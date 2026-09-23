"""Four-way DCLAP audio/text conversion evaluation.

The original FP32 audio/text pair is compared with FP16 audio and dynamic
INT8 text candidates using representation, retrieval, and workstation metrics.
"""

from __future__ import annotations

import argparse
import gc
import hashlib
import json
import platform
import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import onnx
import onnxruntime as ort
import psutil
import requests
from onnxconverter_common.float16 import DEFAULT_OP_BLOCK_LIST, convert_float_to_float16
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoTokenizer

from dclap_reference import (
    audio_model_path,
    load_manifest,
    load_mono_48k,
    segment_audio,
    segment_logmel,
    sha256,
)


ROOT = Path(__file__).resolve().parent
MODEL_DIR = ROOT / "models" / "v1"
TOKENIZER_DIR = MODEL_DIR / "tokenizer"
DATA_DIR = ROOT / "data" / "gtzan"
EVALUATION_DIR = ROOT / "evaluations"
AUDIO_CACHE_DIR = EVALUATION_DIR / "cache"
TEXT_MODEL = MODEL_DIR / "clap_text_model.onnx"
INT8_TEXT_MODEL = MODEL_DIR / "clap_text_model.int8.dynamic.per_channel.onnx"
FP16_AUDIO_MODEL = MODEL_DIR / "model_epoch_36.fp16.onnx"
INT8_AUDIO_MODEL = MODEL_DIR / "model_epoch_36.int8.dynamic.per_channel.onnx"
DATASET_API = "https://huggingface.co/api/datasets/m-a-p/GTZAN/tree/main/genres?recursive=true&expand=false"
DATASET_BASE = "https://huggingface.co/datasets/m-a-p/GTZAN/resolve/main/"
DATASET_LICENSE = "CC-BY-4.0 dataset repository; original audio remains subject to source rights"
GENRES = ("blues", "classical", "country", "disco", "hiphop", "jazz", "metal", "pop", "reggae", "rock")
QUERY_TEMPLATES = ("{genre}", "{genre} music", "a {genre} song")
TOP_K = (1, 5, 10)
MAX_LENGTH = 77


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n", encoding="utf-8")


def model_sha(path: Path) -> str:
    return sha256(path)


def current_rss_mb() -> float:
    return psutil.Process().memory_info().rss / (1024 * 1024)


def session_for(path: Path) -> ort.InferenceSession:
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    options.log_severity_level = 3
    return ort.InferenceSession(str(path), sess_options=options, providers=["CPUExecutionProvider"])


def model_contract(path: Path) -> dict[str, Any]:
    session = session_for(path)
    try:
        artifact_paths = [path]
        external_data = Path(f"{path}.data")
        if external_data.is_file():
            artifact_paths.append(external_data)
        return {
            "path": str(path.relative_to(ROOT)),
            "size_bytes": sum(item.stat().st_size for item in artifact_paths),
            "sha256": model_sha(path),
            "artifacts": [
                {
                    "path": str(item.relative_to(ROOT)),
                    "size_bytes": item.stat().st_size,
                    "sha256": model_sha(item),
                }
                for item in artifact_paths
            ],
            "providers": session.get_providers(),
            "inputs": [{"name": x.name, "type": x.type, "shape": x.shape} for x in session.get_inputs()],
            "outputs": [{"name": x.name, "type": x.type, "shape": x.shape} for x in session.get_outputs()],
        }
    finally:
        del session


def tokenizer_inputs(tokenizer: Any, queries: list[str]) -> dict[str, np.ndarray]:
    encoded = tokenizer(
        queries,
        padding="max_length",
        truncation=True,
        max_length=MAX_LENGTH,
        return_tensors="np",
    )
    return {name: np.asarray(value, dtype=np.int64) for name, value in encoded.items()}


def normalize_rows(values: np.ndarray) -> np.ndarray:
    values = np.asarray(values, dtype=np.float32)
    norms = np.linalg.norm(values, axis=1, keepdims=True)
    return values / np.maximum(norms, 1e-12)


class TextEncoder:
    def __init__(self, model_path: Path):
        self.model_path = model_path
        self.tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_DIR, local_files_only=True, use_fast=True)
        self.session = session_for(model_path)

    def tokenize(self, queries: list[str]) -> dict[str, np.ndarray]:
        return tokenizer_inputs(self.tokenizer, queries)

    def encode_tokens(self, tokens: dict[str, np.ndarray]) -> np.ndarray:
        output = self.session.run(None, tokens)[0]
        return normalize_rows(output)

    def encode(self, queries: list[str]) -> np.ndarray:
        return self.encode_tokens(self.tokenize(queries))

    def close(self) -> None:
        del self.session


class AudioEncoder:
    def __init__(self, model_path: Path):
        self.model_path = model_path
        self.session = session_for(model_path)

    def encode_window(self, mel: np.ndarray) -> np.ndarray:
        output = self.session.run(None, {"mel_spectrogram": mel})[0][0]
        return np.asarray(output, dtype=np.float32)

    def encode_file(self, path: Path) -> np.ndarray:
        try:
            audio = load_mono_48k(path)
        except Exception as error:
            reason = str(error).strip() or "source file is not decodable as audio"
            raise AudioDecodeError(f"{type(error).__name__}: {reason}") from error
        segment_embeddings = []
        for segment in segment_audio(audio):
            mel = segment_logmel(segment)
            segment_embeddings.append(self.encode_window(mel))
        return normalize_rows(np.mean(segment_embeddings, axis=0, keepdims=True))[0]

    def close(self) -> None:
        del self.session


class AudioDecodeError(RuntimeError):
    pass


def dataset_entries() -> list[dict[str, Any]]:
    response = requests.get(DATASET_API, timeout=120)
    response.raise_for_status()
    entries = []
    for item in response.json():
        path = item.get("path", "")
        if item.get("type") == "file" and path.lower().endswith(".wav"):
            genre = path.split("/")[1]
            entries.append({
                "path": path,
                "genre": genre,
                "size_bytes": item.get("size"),
                "sha256": (item.get("lfs") or {}).get("oid"),
                "url": DATASET_BASE + path,
            })
    return sorted(entries, key=lambda item: item["path"])


def select_entries(entries: list[dict[str, Any]], samples_per_genre: int) -> list[dict[str, Any]]:
    selected = []
    for genre in GENRES:
        group = [item for item in entries if item["genre"] == genre]
        if not group:
            raise RuntimeError(f"Dataset has no files for genre {genre}")
        if samples_per_genre >= len(group):
            selected.extend(group)
            continue
        indices = np.linspace(0, len(group) - 1, samples_per_genre).round().astype(int).tolist()
        selected.extend(group[index] for index in indices)
    return selected


def download_one(item: dict[str, Any]) -> dict[str, Any]:
    relative = Path(item["path"]).relative_to("genres")
    destination = DATA_DIR / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_file() and item.get("size_bytes") and destination.stat().st_size == item["size_bytes"]:
        return {**item, "local_path": str(destination.relative_to(ROOT))}

    temporary = destination.with_suffix(destination.suffix + ".part")
    with requests.get(item["url"], stream=True, timeout=120) as response:
        response.raise_for_status()
        with temporary.open("wb") as output:
            for block in response.iter_content(chunk_size=1024 * 1024):
                if block:
                    output.write(block)
    if item.get("size_bytes") and temporary.stat().st_size != item["size_bytes"]:
        raise RuntimeError(f"Size mismatch for {item['path']}")
    if item.get("sha256") and sha256(temporary) != item["sha256"]:
        raise RuntimeError(f"SHA-256 mismatch for {item['path']}")
    temporary.replace(destination)
    return {**item, "local_path": str(destination.relative_to(ROOT))}


def prepare_dataset(samples_per_genre: int) -> Path:
    selected = select_entries(dataset_entries(), samples_per_genre)
    with ThreadPoolExecutor(max_workers=8) as pool:
        downloaded = list(pool.map(download_one, selected))
    manifest = {
        "dataset": "m-a-p/GTZAN",
        "source": DATASET_BASE,
        "license": DATASET_LICENSE,
        "samples_per_genre": samples_per_genre,
        "files": downloaded,
    }
    path = DATA_DIR / "dataset_manifest.json"
    write_json(path, manifest)
    return path


def repair_fp16_sequence_inserts(model: onnx.ModelProto) -> None:
    # ponytail: DCLAP's one Loop carries a float32 sequence; cast its closed-over value back before insertion.
    def visit(graph: onnx.GraphProto) -> None:
        for node in list(graph.node):
            for attribute in node.attribute:
                if attribute.type == onnx.AttributeProto.GRAPH:
                    visit(attribute.g)
                elif attribute.type == onnx.AttributeProto.GRAPHS:
                    for subgraph in attribute.graphs:
                        visit(subgraph)
            if node.op_type != "SequenceInsert":
                continue
            source = node.input[1]
            cast_output = f"{source}_fp32_for_sequence"
            cast = onnx.helper.make_node(
                "Cast",
                [source],
                [cast_output],
                name=cast_output,
                to=onnx.TensorProto.FLOAT,
            )
            index = list(graph.node).index(node)
            graph.node.insert(index, cast)
            node.input[1] = cast_output

    visit(model.graph)


def convert_audio_fp16() -> Path:
    if FP16_AUDIO_MODEL.is_file():
        try:
            model_contract(FP16_AUDIO_MODEL)
        except Exception:
            FP16_AUDIO_MODEL.unlink()
    if not FP16_AUDIO_MODEL.is_file():
        source = audio_model_path(load_manifest())
        model = onnx.load_model(str(source), load_external_data=True)
        converted = convert_float_to_float16(
            model,
            keep_io_types=True,
            op_block_list=list(DEFAULT_OP_BLOCK_LIST) + ["Loop"],
        )
        repair_fp16_sequence_inserts(converted)
        onnx.save_model(converted, str(FP16_AUDIO_MODEL), save_as_external_data=False)
    fp32 = model_contract(audio_model_path(load_manifest()))
    fp16 = model_contract(FP16_AUDIO_MODEL)
    if fp32["inputs"] != fp16["inputs"] or fp32["outputs"] != fp16["outputs"]:
        raise RuntimeError("FP16 audio model contract differs from FP32 model")
    write_json(MODEL_DIR / "audio_model_comparison_manifest.json", {
        "baseline": fp32,
        "candidate": fp16,
        "conversion": {
            "method": "onnxconverter-common float16.convert_float_to_float16",
            "keep_io_types": True,
            "external_data": False,
        },
    })
    return FP16_AUDIO_MODEL


def quantize_text() -> Path:
    if not TEXT_MODEL.is_file():
        raise FileNotFoundError(TEXT_MODEL)
    if not INT8_TEXT_MODEL.is_file():
        quantize_dynamic(
            model_input=str(TEXT_MODEL),
            model_output=str(INT8_TEXT_MODEL),
            per_channel=True,
            reduce_range=False,
            weight_type=QuantType.QInt8,
            extra_options={"MatMulConstBOnly": True},
        )
    fp32 = model_contract(TEXT_MODEL)
    int8 = model_contract(INT8_TEXT_MODEL)
    if fp32["inputs"] != int8["inputs"] or fp32["outputs"] != int8["outputs"]:
        raise RuntimeError("INT8 text model contract differs from FP32 model")
    write_json(MODEL_DIR / "text_model_comparison_manifest.json", {
        "baseline": fp32,
        "candidate": int8,
        "quantization": {
            "method": "ONNX Runtime quantize_dynamic",
            "weight_type": "QInt8",
            "per_channel": True,
            "reduce_range": False,
            "activations": "dynamic",
        },
    })
    return INT8_TEXT_MODEL


def quantize_audio() -> Path:
    if not INT8_AUDIO_MODEL.is_file():
        source = audio_model_path(load_manifest())
        model = onnx.load_model(str(source), load_external_data=True)

        def clear_value_info(graph: onnx.GraphProto) -> None:
            # The released graph contains stale value_info shapes that stop ORT shape inference.
            del graph.value_info[:]
            for node in graph.node:
                for attribute in node.attribute:
                    if attribute.type == onnx.AttributeProto.GRAPH:
                        clear_value_info(attribute.g)
                    elif attribute.type == onnx.AttributeProto.GRAPHS:
                        for subgraph in attribute.graphs:
                            clear_value_info(subgraph)

        clear_value_info(model.graph)
        quantize_dynamic(
            model_input=model,
            model_output=str(INT8_AUDIO_MODEL),
            op_types_to_quantize=["MatMul", "Gemm"],
            per_channel=True,
            reduce_range=False,
            weight_type=QuantType.QInt8,
        )
    fp32 = model_contract(audio_model_path(load_manifest()))
    int8 = model_contract(INT8_AUDIO_MODEL)
    if fp32["inputs"] != int8["inputs"] or fp32["outputs"] != int8["outputs"]:
        raise RuntimeError("INT8 audio model contract differs from FP32 model")
    write_json(MODEL_DIR / "audio_int8_model_comparison_manifest.json", {
        "baseline": fp32,
        "candidate": int8,
        "quantization": {
            "method": "ONNX Runtime quantize_dynamic",
            "operators": ["MatMul", "Gemm"],
            "weight_type": "QInt8",
            "per_channel": True,
            "reduce_range": False,
            "activations": "dynamic",
            "convolution_quantization": "excluded because workstation ORT lacks ConvInteger CPU execution",
        },
    })
    return INT8_AUDIO_MODEL


def load_dataset_manifest() -> list[dict[str, Any]]:
    path = DATA_DIR / "dataset_manifest.json"
    if not path.is_file():
        raise FileNotFoundError(f"Run prepare-dataset first: {path}")
    return json.loads(path.read_text(encoding="utf-8"))["files"]


def audio_cache_key(files: list[dict[str, Any]], model_path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(model_sha(model_path).encode())
    for item in files:
        digest.update(item["path"].encode())
        digest.update(str(item.get("sha256")).encode())
    return digest.hexdigest()[:16]


def load_or_create_audio_cache(files: list[dict[str, Any]], model_path: Path) -> tuple[np.ndarray, list[str], list[str], list[dict[str, Any]]]:
    key = audio_cache_key(files, model_path)
    cache_path = AUDIO_CACHE_DIR / f"audio_embeddings_{key}.npz"
    metadata_path = AUDIO_CACHE_DIR / f"audio_embeddings_{key}.json"
    if cache_path.is_file() and metadata_path.is_file():
        cache = np.load(cache_path, allow_pickle=False)
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        return cache["embeddings"], cache["labels"].tolist(), cache["files"].tolist(), metadata.get("skipped_files", [])

    encoder = AudioEncoder(model_path)
    try:
        embeddings = []
        labels = []
        paths = []
        skipped_files = []
        for index, item in enumerate(files, start=1):
            path = ROOT / item["local_path"]
            try:
                embeddings.append(encoder.encode_file(path))
            except AudioDecodeError as error:
                skipped_files.append({
                    "path": item["path"],
                    "local_path": item["local_path"],
                    "genre": item["genre"],
                    "reason": str(error),
                })
                print(f"skip undecodable audio {index}/{len(files)}: {item['path']}", flush=True)
                continue
            labels.append(item["genre"])
            paths.append(item["path"])
            if index % 25 == 0 or index == len(files):
                print(f"audio {index}/{len(files)}", flush=True)
    finally:
        encoder.close()
    matrix = np.asarray(embeddings, dtype=np.float32)
    AUDIO_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(cache_path, embeddings=matrix, labels=np.asarray(labels), files=np.asarray(paths))
    write_json(metadata_path, {
        "cache_key": key,
        "count": len(paths),
        "model_path": str(model_path.relative_to(ROOT)),
        "model_sha256": model_sha(model_path),
        "skipped_files": skipped_files,
    })
    return matrix, labels, paths, skipped_files


def query_set() -> list[dict[str, str]]:
    return [
        {"query_id": f"{genre}:{index}", "genre": genre, "template": template, "text": template.format(genre=genre)}
        for genre in GENRES
        for index, template in enumerate(QUERY_TEMPLATES)
    ]


def average_precision(ranked_relevance: np.ndarray) -> float:
    total_relevant = int(ranked_relevance.sum())
    if total_relevant == 0:
        return 0.0
    cumulative = np.cumsum(ranked_relevance)
    positions = np.arange(1, len(ranked_relevance) + 1)
    return float(np.sum((cumulative / positions) * ranked_relevance) / total_relevant)


def average_precision_at_k(ranked_relevance: np.ndarray, total_relevant: int, k: int) -> float:
    if total_relevant == 0:
        return 0.0
    ranked_relevance = ranked_relevance[:k]
    cumulative = np.cumsum(ranked_relevance)
    positions = np.arange(1, len(ranked_relevance) + 1)
    return float(np.sum((cumulative / positions) * ranked_relevance) / total_relevant)


def retrieval_metrics(scores: np.ndarray, query_genres: list[str], labels: list[str]) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    rankings = np.argsort(-scores, axis=1, kind="stable")
    per_query = []
    for row, genre in zip(rankings, query_genres):
        relevance = np.asarray([label == genre for label in labels], dtype=bool)
        ranked_relevance = relevance[row]
        total_relevant = int(relevance.sum())
        row_metrics: dict[str, Any] = {
            "genre": genre,
            "average_precision": average_precision(ranked_relevance),
            "average_precision_at_10": average_precision_at_k(ranked_relevance, total_relevant, 10),
        }
        for k in TOP_K:
            hits = int(ranked_relevance[:k].sum())
            row_metrics[f"recall_at_{k}"] = hits / total_relevant
            row_metrics[f"hit_rate_at_{k}"] = float(hits > 0)
        row_metrics["top10"] = [labels[index] for index in row[:10]]
        per_query.append(row_metrics)
    aggregate = {key: float(np.mean([row[key] for row in per_query])) for key in per_query[0] if key.startswith(("recall_", "hit_rate_", "average_"))}
    aggregate["mAP"] = float(np.mean([row["average_precision"] for row in per_query]))
    aggregate["mAP_at_10"] = float(np.mean([row["average_precision_at_10"] for row in per_query]))
    return aggregate, per_query


def percentile(values: list[float]) -> dict[str, float]:
    return {
        "mean": float(statistics.mean(values)),
        "median": float(statistics.median(values)),
        "p95": float(np.percentile(values, 95)),
        "min": float(min(values)),
        "max": float(max(values)),
    }


def measure_text_model(path: Path, tokens: dict[str, np.ndarray], repeats: int) -> dict[str, Any]:
    rss_before = current_rss_mb()
    start = time.perf_counter()
    encoder = TextEncoder(path)
    session_ms = (time.perf_counter() - start) * 1000
    rss_after_session = current_rss_mb()
    single_tokens = {key: value[:1] for key, value in tokens.items()}
    encoder.encode_tokens(single_tokens)
    latencies = []
    for _ in range(repeats):
        start = time.perf_counter_ns()
        encoder.encode_tokens(single_tokens)
        latencies.append((time.perf_counter_ns() - start) / 1_000_000)
    encoder.close()
    gc.collect()
    return {
        "model": model_contract(path),
        "session_creation_ms": session_ms,
        "rss_before_mb": rss_before,
        "rss_after_session_mb": rss_after_session,
        "rss_delta_mb": rss_after_session - rss_before,
        "warm_single_query_latency_ms": percentile(latencies),
        "repeats": repeats,
    }


def measure_audio_model(path: Path, sample_mel: np.ndarray, repeats: int) -> dict[str, Any]:
    rss_before = current_rss_mb()
    start = time.perf_counter()
    encoder = AudioEncoder(path)
    session_ms = (time.perf_counter() - start) * 1000
    rss_after_session = current_rss_mb()
    encoder.encode_window(sample_mel)
    latencies = []
    for _ in range(repeats):
        start = time.perf_counter_ns()
        encoder.encode_window(sample_mel)
        latencies.append((time.perf_counter_ns() - start) / 1_000_000)
    encoder.close()
    gc.collect()
    return {
        "model": model_contract(path),
        "session_creation_ms": session_ms,
        "rss_before_mb": rss_before,
        "rss_after_session_mb": rss_after_session,
        "rss_delta_mb": rss_after_session - rss_before,
        "warm_single_window_latency_ms": percentile(latencies),
        "repeats": repeats,
    }


def distortion_metrics(reference: np.ndarray, candidate: np.ndarray) -> dict[str, float]:
    cosine = np.sum(reference * candidate, axis=1)
    mse = np.mean(np.square(reference - candidate), axis=1)
    l2 = np.linalg.norm(reference - candidate, axis=1)
    return {
        "cosine_similarity_mean": float(np.mean(cosine)),
        "cosine_similarity_median": float(np.median(cosine)),
        "cosine_similarity_p05": float(np.percentile(cosine, 5)),
        "cosine_drop_mean": float(1.0 - np.mean(cosine)),
        "mse_mean": float(np.mean(mse)),
        "mse_median": float(np.median(mse)),
        "mse_p95": float(np.percentile(mse, 95)),
        "l2_mean": float(np.mean(l2)),
        "l2_median": float(np.median(l2)),
        "l2_p95": float(np.percentile(l2, 95)),
    }


def pair_performance_comparison(baseline: dict[str, Any], candidate: dict[str, Any], latency_key: str) -> dict[str, Any]:
    comparison: dict[str, Any] = {}
    for key in ("session_creation_ms", "rss_delta_mb"):
        comparison[key] = {
            "baseline": baseline[key],
            "candidate": candidate[key],
            "relative_change_percent": relative_change(candidate[key], baseline[key]),
        }
    for key in ("mean", "median", "p95"):
        baseline_latency = baseline[latency_key][key]
        candidate_latency = candidate[latency_key][key]
        comparison[f"warm_latency_{key}_ms"] = {
            "baseline": baseline_latency,
            "candidate": candidate_latency,
            "relative_change_percent": relative_change(candidate_latency, baseline_latency),
        }
    baseline_size = baseline["model"]["size_bytes"]
    candidate_size = candidate["model"]["size_bytes"]
    comparison["model_size_bytes"] = {
        "baseline": baseline_size,
        "candidate": candidate_size,
        "relative_change_percent": relative_change(candidate_size, baseline_size),
    }
    return comparison


def relative_change(candidate: float, baseline: float) -> float | None:
    if baseline == 0:
        return None
    return (candidate - baseline) / abs(baseline) * 100.0


def evaluate(run_id: str, repeats: int) -> Path:
    files = load_dataset_manifest()
    fp32_audio_model = audio_model_path(load_manifest())
    fp16_audio_model = convert_audio_fp16()
    quantize_text()
    fp32_audio, labels, audio_paths, skipped_files = load_or_create_audio_cache(files, fp32_audio_model)
    fp16_audio, fp16_labels, fp16_paths, fp16_skipped_files = load_or_create_audio_cache(files, fp16_audio_model)
    skipped_keys = [(item["path"], item["genre"]) for item in skipped_files]
    fp16_skipped_keys = [(item["path"], item["genre"]) for item in fp16_skipped_files]
    if labels != fp16_labels or audio_paths != fp16_paths or skipped_keys != fp16_skipped_keys:
        raise RuntimeError("FP32 and FP16 audio caches contain different corpus rows")
    queries = query_set()
    texts = [query["text"] for query in queries]
    query_genres = [query["genre"] for query in queries]
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_DIR, local_files_only=True, use_fast=True)
    tokens = tokenizer_inputs(tokenizer, texts)

    text_embeddings = {}
    for name, path in (("fp32", TEXT_MODEL), ("int8", INT8_TEXT_MODEL)):
        encoder = TextEncoder(path)
        text_embeddings[name] = encoder.encode_tokens(tokens)
        encoder.close()
        gc.collect()

    audio_embeddings = {"fp32": fp32_audio, "fp16": fp16_audio}
    audio_cosine = np.sum(fp32_audio * fp16_audio, axis=1)
    audio_mse = np.mean(np.square(fp32_audio - fp16_audio), axis=1)
    audio_l2 = np.linalg.norm(fp32_audio - fp16_audio, axis=1)
    text_distortion = distortion_metrics(text_embeddings["fp32"], text_embeddings["int8"])
    audio_distortion = distortion_metrics(fp32_audio, fp16_audio)
    variants = {}
    variant_queries = {}
    audio_control_retrieval = {}
    for audio_name, audio_matrix in audio_embeddings.items():
        audio_control_scores = audio_matrix @ audio_matrix.T
        np.fill_diagonal(audio_control_scores, -np.inf)
        audio_control_retrieval[audio_name], _ = retrieval_metrics(audio_control_scores, labels, labels)
        for text_name, text_matrix in text_embeddings.items():
            variant_name = f"{audio_name}_audio_{text_name}_text"
            variants[variant_name], variant_queries[variant_name] = retrieval_metrics(
                text_matrix @ audio_matrix.T,
                query_genres,
                labels,
            )

    valid_items = {item["path"]: item for item in files}
    sample_path = ROOT / valid_items[audio_paths[0]]["local_path"]
    sample_mel = segment_logmel(segment_audio(load_mono_48k(sample_path))[0])
    performance = {
        "audio_fp32": measure_audio_model(fp32_audio_model, sample_mel, repeats),
        "audio_fp16": measure_audio_model(fp16_audio_model, sample_mel, repeats),
        "text_fp32": measure_text_model(TEXT_MODEL, tokens, repeats),
        "text_int8": measure_text_model(INT8_TEXT_MODEL, tokens, repeats),
    }
    performance_comparison = {
        "audio_fp32_to_fp16": pair_performance_comparison(
            performance["audio_fp32"],
            performance["audio_fp16"],
            "warm_single_window_latency_ms",
        ),
        "text_fp32_to_int8": pair_performance_comparison(
            performance["text_fp32"],
            performance["text_int8"],
            "warm_single_query_latency_ms",
        ),
    }

    result_dir = EVALUATION_DIR / run_id
    result_dir.mkdir(parents=True, exist_ok=True)
    write_json(result_dir / "dataset_manifest.json", json.loads((DATA_DIR / "dataset_manifest.json").read_text(encoding="utf-8")))
    baseline_variant = "fp32_audio_fp32_text"
    previous_candidate_variant = "fp32_audio_int8_text"
    relative_degradation = {
        variant: {
            key: relative_change(metrics[key], variants[baseline_variant][key])
            for key in variants[baseline_variant]
            if isinstance(metrics[key], (int, float))
        }
        for variant, metrics in variants.items()
    }
    isolated_conversion_effects = {
        "audio_fp16_holding_text_fp32": {
            key: relative_change(variants["fp16_audio_fp32_text"][key], variants["fp32_audio_fp32_text"][key])
            for key in variants[baseline_variant]
            if isinstance(variants["fp32_audio_fp32_text"][key], (int, float))
        },
        "audio_fp16_holding_text_int8": {
            key: relative_change(variants["fp16_audio_int8_text"][key], variants["fp32_audio_int8_text"][key])
            for key in variants[baseline_variant]
            if isinstance(variants["fp32_audio_int8_text"][key], (int, float))
        },
        "text_int8_holding_audio_fp32": {
            key: relative_change(variants["fp32_audio_int8_text"][key], variants["fp32_audio_fp32_text"][key])
            for key in variants[baseline_variant]
            if isinstance(variants["fp32_audio_fp32_text"][key], (int, float))
        },
        "text_int8_holding_audio_fp16": {
            key: relative_change(variants["fp16_audio_int8_text"][key], variants["fp16_audio_fp32_text"][key])
            for key in variants[baseline_variant]
            if isinstance(variants["fp16_audio_fp32_text"][key], (int, float))
        },
    }

    per_query = []
    for index, query in enumerate(queries):
        query_row = {variant: metrics[index] for variant, metrics in variant_queries.items()}
        per_query.append({
            **query,
            "text_representation": {
                "cosine": float(np.sum(text_embeddings["fp32"][index] * text_embeddings["int8"][index])),
                "mse": float(np.mean(np.square(text_embeddings["fp32"][index] - text_embeddings["int8"][index]))),
                "l2": float(np.linalg.norm(text_embeddings["fp32"][index] - text_embeddings["int8"][index])),
            },
            "retrieval": query_row,
        })
    with (result_dir / "per_query.jsonl").open("w", encoding="utf-8") as output:
        for row in per_query:
            output.write(json.dumps(row, sort_keys=True) + "\n")

    with (result_dir / "per_audio.jsonl").open("w", encoding="utf-8") as output:
        for index, (path, label) in enumerate(zip(audio_paths, labels)):
            output.write(json.dumps({
                "path": path,
                "genre": label,
                "audio_representation": {
                    "cosine": float(audio_cosine[index]),
                    "mse": float(audio_mse[index]),
                    "l2": float(audio_l2[index]),
                },
            }, sort_keys=True) + "\n")

    write_json(result_dir / "performance.json", performance)
    baseline_retrieval = variants[baseline_variant]
    candidate_retrieval = variants[previous_candidate_variant]
    write_json(result_dir / "summary.json", {
        "run_id": run_id,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "comparison": {
            "baseline": baseline_variant,
            "variants": sorted(variants),
            "audio_models": {
                "fp32": str(fp32_audio_model.relative_to(ROOT)),
                "fp16": str(fp16_audio_model.relative_to(ROOT)),
            },
            "text_models": {
                "fp32": str(TEXT_MODEL.relative_to(ROOT)),
                "int8": str(INT8_TEXT_MODEL.relative_to(ROOT)),
            },
        },
        "dataset": {
            "manifest": str((DATA_DIR / "dataset_manifest.json").relative_to(ROOT)),
            "audio_count": len(labels),
            "excluded_files": skipped_files,
            "genres": sorted(set(labels)),
            "relevance": "same GTZAN genre label",
        },
        "tokenizer": {"path": str(TOKENIZER_DIR.relative_to(ROOT)), "max_length": MAX_LENGTH, "padding": "max_length", "truncation": True},
        "metrics": {
            "audio_representation_preservation_fp32_to_fp16": audio_distortion,
            "text_representation_preservation_fp32_to_int8": text_distortion,
            "retrieval_by_variant": variants,
            "relative_degradation_percent_vs_fp32_audio_fp32_text": relative_degradation,
            "isolated_conversion_effects_percent": isolated_conversion_effects,
            "baseline_retrieval": baseline_retrieval,
            "candidate_retrieval": candidate_retrieval,
            "audio_to_audio_control": audio_control_retrieval,
            "definitions": {
                "recall_at_k": "relevant items retrieved in top K divided by all relevant corpus items",
                "hit_rate_at_k": "fraction of queries with at least one relevant item in top K",
                "mAP": "mean average precision over the full ranked corpus",
                "mAP_at_10": "mean average precision truncated at rank 10",
                "relative_degradation_percent": "(candidate - baseline) / abs(baseline) * 100; negative is lower retrieval quality",
            },
        },
        "performance_file": "performance.json",
        "performance_comparison": performance_comparison,
        "audio_cache_files": {"fp32": audio_paths[:3], "fp16": fp16_paths[:3]},
        "runtime": {"python": sys.version, "platform": platform.platform(), "onnxruntime": ort.__version__, "cpu": platform.processor()},
    })
    return result_dir


def evaluate_int8_audio(run_id: str, repeats: int, previous_run_id: str) -> Path:
    previous_dir = EVALUATION_DIR / previous_run_id
    previous_summary = json.loads((previous_dir / "summary.json").read_text(encoding="utf-8"))
    previous_performance = json.loads((previous_dir / "performance.json").read_text(encoding="utf-8"))
    previous_metrics = previous_summary["metrics"]
    previous_variants = previous_metrics["retrieval_by_variant"]
    baseline_variant = "fp32_audio_fp32_text"
    text_control_variant = "fp32_audio_int8_text"
    if baseline_variant not in previous_variants or text_control_variant not in previous_variants:
        raise RuntimeError(f"Previous comparison is missing {baseline_variant} or {text_control_variant}")

    files = load_dataset_manifest()
    fp32_audio_model = audio_model_path(load_manifest())
    int8_audio_model = quantize_audio()
    quantize_text()
    fp32_audio, labels, audio_paths, skipped_files = load_or_create_audio_cache(files, fp32_audio_model)
    int8_audio, int8_labels, int8_paths, int8_skipped_files = load_or_create_audio_cache(files, int8_audio_model)
    if labels != int8_labels or audio_paths != int8_paths or skipped_files != int8_skipped_files:
        raise RuntimeError("FP32 and INT8 audio caches contain different corpus rows")

    queries = query_set()
    texts = [query["text"] for query in queries]
    query_genres = [query["genre"] for query in queries]
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_DIR, local_files_only=True, use_fast=True)
    tokens = tokenizer_inputs(tokenizer, texts)
    text_encoder = TextEncoder(INT8_TEXT_MODEL)
    try:
        int8_text = text_encoder.encode_tokens(tokens)
    finally:
        text_encoder.close()
    gc.collect()

    variant_name = "int8_audio_int8_text"
    retrieval, per_query_metrics = retrieval_metrics(int8_text @ int8_audio.T, query_genres, labels)
    audio_distortion = distortion_metrics(fp32_audio, int8_audio)
    audio_control_scores = int8_audio @ int8_audio.T
    np.fill_diagonal(audio_control_scores, -np.inf)
    audio_control, _ = retrieval_metrics(audio_control_scores, labels, labels)

    valid_items = {item["path"]: item for item in files}
    sample_path = ROOT / valid_items[audio_paths[0]]["local_path"]
    sample_mel = segment_logmel(segment_audio(load_mono_48k(sample_path))[0])
    performance = {
        "audio_fp32": previous_performance["audio_fp32"],
        "audio_fp16": previous_performance["audio_fp16"],
        "audio_int8": measure_audio_model(int8_audio_model, sample_mel, repeats),
        "text_fp32": previous_performance["text_fp32"],
        "text_int8": previous_performance["text_int8"],
    }
    previous_comparison = previous_summary["performance_comparison"]
    performance_comparison = {
        "audio_fp32_to_fp16": previous_comparison["audio_fp32_to_fp16"],
        "audio_fp32_to_int8": pair_performance_comparison(
            performance["audio_fp32"],
            performance["audio_int8"],
            "warm_single_window_latency_ms",
        ),
        "text_fp32_to_int8": previous_comparison["text_fp32_to_int8"],
    }

    variants = dict(previous_variants)
    variants[variant_name] = retrieval

    def relative_metrics(candidate: dict[str, Any], baseline: dict[str, Any]) -> dict[str, float | None]:
        return {
            key: relative_change(candidate[key], baseline[key])
            for key in baseline
            if isinstance(baseline[key], (int, float)) and isinstance(candidate.get(key), (int, float))
        }

    relative_degradation = dict(previous_metrics["relative_degradation_percent_vs_fp32_audio_fp32_text"])
    relative_degradation[variant_name] = relative_metrics(retrieval, variants[baseline_variant])
    isolated_conversion_effects = dict(previous_metrics["isolated_conversion_effects_percent"])
    isolated_conversion_effects["audio_int8_holding_text_int8"] = relative_metrics(
        retrieval,
        variants[text_control_variant],
    )
    audio_to_audio_control = dict(previous_metrics["audio_to_audio_control"])
    audio_to_audio_control["int8"] = audio_control

    result_dir = EVALUATION_DIR / run_id
    result_dir.mkdir(parents=True, exist_ok=True)
    write_json(result_dir / "dataset_manifest.json", json.loads((DATA_DIR / "dataset_manifest.json").read_text(encoding="utf-8")))
    with (result_dir / "per_query.jsonl").open("w", encoding="utf-8") as output:
        for query, metrics in zip(queries, per_query_metrics):
            output.write(json.dumps({
                **query,
                "retrieval": {variant_name: metrics},
            }, sort_keys=True) + "\n")
    with (result_dir / "per_audio.jsonl").open("w", encoding="utf-8") as output:
        for index, (path, label) in enumerate(zip(audio_paths, labels)):
            output.write(json.dumps({
                "path": path,
                "genre": label,
                "audio_representation": {
                    "cosine": float(np.sum(fp32_audio[index] * int8_audio[index])),
                    "mse": float(np.mean(np.square(fp32_audio[index] - int8_audio[index]))),
                    "l2": float(np.linalg.norm(fp32_audio[index] - int8_audio[index])),
                },
            }, sort_keys=True) + "\n")
    write_json(result_dir / "performance.json", performance)

    audio_models = dict(previous_summary["comparison"]["audio_models"])
    audio_models["int8"] = str(int8_audio_model.relative_to(ROOT))
    write_json(result_dir / "summary.json", {
        "run_id": run_id,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "evaluation_scope": {
            "new_variant": variant_name,
            "dataset": f"full local GTZAN manifest; {len(labels)} valid tracks",
            "android": "not used",
            "previous_results_reused": previous_run_id,
            "other_variants_rerun": False,
        },
        "comparison": {
            "baseline": baseline_variant,
            "variants": sorted(variants),
            "audio_models": audio_models,
            "text_models": previous_summary["comparison"]["text_models"],
        },
        "dataset": {
            "manifest": str((DATA_DIR / "dataset_manifest.json").relative_to(ROOT)),
            "audio_count": len(labels),
            "excluded_files": skipped_files,
            "genres": sorted(set(labels)),
            "relevance": "same GTZAN genre label",
        },
        "tokenizer": previous_summary["tokenizer"],
        "metrics": {
            "audio_representation_preservation_fp32_to_fp16": previous_metrics["audio_representation_preservation_fp32_to_fp16"],
            "audio_representation_preservation_fp32_to_int8": audio_distortion,
            "text_representation_preservation_fp32_to_int8": previous_metrics["text_representation_preservation_fp32_to_int8"],
            "retrieval_by_variant": variants,
            "relative_degradation_percent_vs_fp32_audio_fp32_text": relative_degradation,
            "isolated_conversion_effects_percent": isolated_conversion_effects,
            "baseline_retrieval": variants[baseline_variant],
            "candidate_retrieval": retrieval,
            "audio_to_audio_control": audio_to_audio_control,
            "definitions": previous_metrics["definitions"],
        },
        "performance_file": "performance.json",
        "performance_comparison": performance_comparison,
        "audio_cache_files": {
            "fp32": audio_paths[:3],
            "fp16": previous_summary["audio_cache_files"]["fp16"][:3],
            "int8": int8_paths[:3],
        },
        "reused_results": {
            "summary": str((previous_dir / "summary.json").relative_to(ROOT)),
            "performance": {
                "audio_fp32": True,
                "audio_fp16": True,
                "text_fp32": True,
                "text_int8": True,
            },
        },
        "runtime": {"python": sys.version, "platform": platform.platform(), "onnxruntime": ort.__version__, "cpu": platform.processor()},
    })
    return result_dir


def self_test() -> None:
    scores = np.asarray([[0.9, 0.8, 0.1], [0.2, 0.1, 0.8]])
    metrics, rows = retrieval_metrics(scores, ["a", "b"], ["a", "a", "b"])
    assert metrics["hit_rate_at_1"] == 1.0
    assert len(rows) == 2
    assert abs(relative_change(0.9, 1.0) + 10.0) < 1e-9
    print("self-test passed")


def parser() -> argparse.ArgumentParser:
    command = argparse.ArgumentParser()
    subparsers = command.add_subparsers(dest="command", required=True)
    prepare = subparsers.add_parser("prepare-dataset")
    prepare.add_argument("--samples-per-genre", type=int, default=10)
    subparsers.add_parser("convert-audio-fp16")
    subparsers.add_parser("quantize-text")
    subparsers.add_parser("quantize-audio")
    evaluate_parser = subparsers.add_parser("evaluate")
    evaluate_parser.add_argument("--run-id", default=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
    evaluate_parser.add_argument("--repeats", type=int, default=30)
    int8_audio_parser = subparsers.add_parser("evaluate-int8-audio")
    int8_audio_parser.add_argument("--run-id", default=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-int8-audio"))
    int8_audio_parser.add_argument("--previous-run-id", default="20260921T-workstation-fp16-audio-int8-text")
    int8_audio_parser.add_argument("--repeats", type=int, default=30)
    subparsers.add_parser("self-test")
    return command


def main() -> None:
    args = parser().parse_args()
    if args.command == "prepare-dataset":
        print(prepare_dataset(args.samples_per_genre))
    elif args.command == "convert-audio-fp16":
        print(convert_audio_fp16())
    elif args.command == "quantize-text":
        print(quantize_text())
    elif args.command == "quantize-audio":
        print(quantize_audio())
    elif args.command == "evaluate":
        print(evaluate(args.run_id, args.repeats))
    elif args.command == "evaluate-int8-audio":
        print(evaluate_int8_audio(args.run_id, args.repeats, args.previous_run_id))
    else:
        self_test()


if __name__ == "__main__":
    main()

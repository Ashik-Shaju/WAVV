"""Prepare a small, application-independent Android DCLAP model fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
from pathlib import Path

import numpy as np
from transformers import AutoTokenizer

from dclap_evaluation import GENRES, MAX_LENGTH, QUERY_TEMPLATES
from dclap_reference import ROOT, load_mono_48k, segment_audio, segment_logmel


def write_utf(stream, value: str) -> None:
    data = value.encode("utf-8")
    stream.write(struct.pack(">H", len(data)))
    stream.write(data)


def write_audio(path: Path, records: list[dict[str, object]]) -> None:
    with path.open("wb") as stream:
        stream.write(struct.pack(">II", 0x44434131, len(records)))
        for record in records:
            write_utf(stream, str(record["path"]))
            write_utf(stream, str(record["genre"]))
            windows = record["windows"]
            assert isinstance(windows, list)
            stream.write(struct.pack(">I", len(windows)))
            for window in windows:
                values = np.asarray(window, dtype=">f4")
                stream.write(struct.pack(">I", values.size))
                stream.write(values.tobytes())


def write_text(path: Path, queries: list[dict[str, object]], tokenizer: AutoTokenizer) -> None:
    texts = [str(query["text"]) for query in queries]
    encoded = tokenizer(
        texts,
        padding="max_length",
        truncation=True,
        max_length=MAX_LENGTH,
        return_tensors="np",
    )
    input_ids = np.asarray(encoded["input_ids"], dtype=">i8")
    attention_mask = np.asarray(encoded["attention_mask"], dtype=">i8")
    with path.open("wb") as stream:
        stream.write(struct.pack(">II", 0x44435431, len(queries)))
        for index, query in enumerate(queries):
            write_utf(stream, str(query["genre"]))
            write_utf(stream, texts[index])
            stream.write(input_ids[index].tobytes())
            stream.write(attention_mask[index].tobytes())


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples-per-genre", type=int, default=3)
    parser.add_argument("--output", type=Path, default=ROOT / "evaluations" / "android-device-small")
    args = parser.parse_args()
    if args.samples_per_genre < 1:
        raise SystemExit("--samples-per-genre must be positive")

    records: list[dict[str, object]] = []
    for genre in GENRES:
        candidates = sorted((ROOT / "data" / "gtzan" / genre).glob("*.wav"))
        for path in candidates:
            try:
                windows = [segment_logmel(segment)[0].astype(np.float32) for segment in segment_audio(load_mono_48k(path))]
            except Exception:
                continue
            records.append({
                "path": str(path.relative_to(ROOT)).replace("\\", "/"),
                "genre": genre,
                "windows": windows,
            })
            if sum(item["genre"] == genre for item in records) == args.samples_per_genre:
                break
        if sum(item["genre"] == genre for item in records) != args.samples_per_genre:
            raise SystemExit(f"could not prepare {args.samples_per_genre} samples for {genre}")

    queries = [
        {"query_id": f"{genre}:{index}", "genre": genre, "text": template.format(genre=genre)}
        for genre in GENRES
        for index, template in enumerate(QUERY_TEMPLATES)
    ]
    args.output.mkdir(parents=True, exist_ok=True)
    audio_path = args.output / "audio_fixture.bin"
    text_path = args.output / "text_fixture.bin"
    write_audio(audio_path, records)
    tokenizer = AutoTokenizer.from_pretrained(ROOT / "models" / "v1" / "tokenizer", local_files_only=True, use_fast=True)
    write_text(text_path, queries, tokenizer)
    manifest = {
        "audio_fixture": {"path": audio_path.name, "sha256": sha256(audio_path), "records": len(records)},
        "text_fixture": {"path": text_path.name, "sha256": sha256(text_path), "queries": len(queries), "max_length": MAX_LENGTH},
        "samples_per_genre": args.samples_per_genre,
        "genres": list(GENRES),
        "model_input_contract": {"audio_shape": [1, 1, 128, 1001], "embedding_dimension": 512},
    }
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()

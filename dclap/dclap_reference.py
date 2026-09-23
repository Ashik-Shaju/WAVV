"""DCLAP v1 audio reference harness.

This intentionally follows the preprocessing shown in the DCLAP v1 README.
It is a desktop parity gate, not app code.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import wave
from pathlib import Path
from typing import Any

import librosa
import numpy as np
import onnx
import onnxruntime as ort


ROOT = Path(__file__).resolve().parent
MANIFEST_PATH = ROOT / "manifest.json"
SAMPLE_RATE = 48_000
SEGMENT_SAMPLES = 480_000
SEGMENT_HOP_SAMPLES = 240_000
N_MELS = 128
N_FFT = 2_048
MEL_HOP_SAMPLES = 480


def load_manifest(path: Path = MANIFEST_PATH) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_artifacts(manifest: dict[str, Any]) -> None:
    failures: list[str] = []
    for artifact in manifest["audio"]["files"]:
        path = ROOT / artifact["path"]
        if not path.is_file():
            failures.append(f"missing: {path}")
            continue
        actual_size = path.stat().st_size
        if actual_size != artifact["size_bytes"]:
            failures.append(f"size: {path} expected {artifact['size_bytes']} got {actual_size}")
        actual_hash = sha256(path)
        if artifact["sha256"] == "pending":
            print(f"sha256 {artifact['path']} {actual_hash}")
        elif actual_hash != artifact["sha256"]:
            failures.append(f"sha256: {path} expected {artifact['sha256']} got {actual_hash}")
    if failures:
        raise SystemExit("artifact verification failed:\n" + "\n".join(failures))


def audio_model_path(manifest: dict[str, Any]) -> Path:
    return ROOT / manifest["audio"]["files"][0]["path"]


def session_metadata(model_path: Path) -> dict[str, Any]:
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    model = onnx.load(str(model_path), load_external_data=False)
    return {
        "providers": session.get_providers(),
        "inputs": [
            {"name": value.name, "type": value.type, "shape": value.shape}
            for value in session.get_inputs()
        ],
        "outputs": [
            {"name": value.name, "type": value.type, "shape": value.shape}
            for value in session.get_outputs()
        ],
        "opset": max((opset.version for opset in model.opset_import), default=None),
    }


def load_mono_48k(path: Path) -> np.ndarray:
    audio, _ = librosa.load(path, sr=SAMPLE_RATE, mono=True)
    audio = np.clip(audio, -1.0, 1.0)
    audio = (audio * 32767.0).astype(np.int16)
    return (audio.astype(np.float32) / 32767.0).astype(np.float32)


def segment_audio(audio: np.ndarray) -> list[np.ndarray]:
    total = len(audio)
    if total <= SEGMENT_SAMPLES:
        return [np.pad(audio, (0, SEGMENT_SAMPLES - total))]

    segments = [
        audio[start : start + SEGMENT_SAMPLES]
        for start in range(0, total - SEGMENT_SAMPLES + 1, SEGMENT_HOP_SAMPLES)
    ]
    last_start = len(segments) * SEGMENT_HOP_SAMPLES
    if last_start < total:
        segments.append(audio[-SEGMENT_SAMPLES:])
    return segments


def segment_logmel(segment: np.ndarray) -> np.ndarray:
    mel = librosa.feature.melspectrogram(
        y=segment,
        sr=SAMPLE_RATE,
        n_fft=N_FFT,
        hop_length=MEL_HOP_SAMPLES,
        win_length=N_FFT,
        window="hann",
        center=True,
        pad_mode="reflect",
        power=2.0,
        n_mels=N_MELS,
        fmin=0,
        fmax=14_000,
    )
    log_mel = librosa.power_to_db(mel, ref=1.0, amin=1e-10, top_db=None)
    return log_mel[np.newaxis, np.newaxis, :, :].astype(np.float32)


def encode(path: Path, model_path: Path) -> dict[str, Any]:
    audio = load_mono_48k(path)
    segments = segment_audio(audio)
    session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    embeddings: list[np.ndarray] = []
    mel_checksums: list[str] = []
    mel_shapes: list[list[int]] = []
    for segment in segments:
        mel = segment_logmel(segment)
        mel_shapes.append(list(mel.shape))
        mel_checksums.append(hashlib.sha256(mel.tobytes()).hexdigest())
        output = session.run(None, {"mel_spectrogram": mel})[0]
        embeddings.append(np.asarray(output[0], dtype=np.float32))

    per_segment = np.stack(embeddings)
    average = per_segment.mean(axis=0)
    norm = float(np.linalg.norm(average))
    normalized = average / (norm + 1e-9)
    return {
        "audio": str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path),
        "sample_count_48k": int(len(audio)),
        "segment_count": len(segments),
        "mel_shapes": mel_shapes,
        "mel_sha256": mel_checksums,
        "per_segment_embeddings": per_segment.tolist(),
        "average_embedding": average.tolist(),
        "embedding": normalized.tolist(),
        "embedding_dimension": int(normalized.size),
        "embedding_norm": float(np.linalg.norm(normalized)),
        "finite": bool(np.isfinite(normalized).all()),
    }


def write_pcm_wav(path: Path, samples: np.ndarray, sample_rate: int, channels: int) -> None:
    pcm = np.clip(samples, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype(np.int16)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(channels)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        output.writeframes(pcm.tobytes())


def create_fixtures(directory: Path) -> list[Path]:
    directory.mkdir(parents=True, exist_ok=True)
    fixtures = [
        ("short_mono.wav", 0.5, 1),
        ("normal_mono.wav", 12.0, 1),
        ("exact_10s_mono.wav", 10.0, 1),
        ("multichannel_stereo.wav", 12.5, 2),
    ]
    result: list[Path] = []
    for name, seconds, channels in fixtures:
        count = int(SAMPLE_RATE * seconds)
        time = np.arange(count, dtype=np.float32) / SAMPLE_RATE
        left = 0.35 * np.sin(2.0 * math.pi * 440.0 * time)
        if channels == 1:
            samples = left
        else:
            right = 0.25 * np.sin(2.0 * math.pi * 660.0 * time)
            samples = np.column_stack((left, right))
        path = directory / name
        write_pcm_wav(path, samples, SAMPLE_RATE, channels)
        result.append(path)
    return result


def command_verify(manifest: dict[str, Any]) -> None:
    verify_artifacts(manifest)
    metadata = session_metadata(audio_model_path(manifest))
    print(json.dumps(metadata, indent=2))


def command_fixtures(manifest: dict[str, Any], output: Path) -> None:
    verify_artifacts(manifest)
    fixture_paths = create_fixtures(output)
    results = [encode(path, audio_model_path(manifest)) for path in fixture_paths]
    output_json = output / "reference.json"
    output_json.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(output_json)
    for result in results:
        assert result["embedding_dimension"] == 512
        assert result["finite"]
        assert abs(result["embedding_norm"] - 1.0) < 1e-5


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("verify", help="verify artifacts and print ONNX metadata")
    fixtures = subparsers.add_parser("fixtures", help="generate deterministic fixtures and embeddings")
    fixtures.add_argument("--output", type=Path, default=ROOT / "fixtures")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    manifest = load_manifest()
    if args.command == "verify":
        command_verify(manifest)
    elif args.command == "fixtures":
        command_fixtures(manifest, args.output)


if __name__ == "__main__":
    main()

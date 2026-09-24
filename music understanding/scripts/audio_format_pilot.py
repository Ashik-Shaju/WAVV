from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import warnings
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from statistics import mean, median
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "vendor" / "EfficientAT"
REPORT = ROOT / "reports" / "audio_format_pilot_5track.json"
TRACKS = (
    ("1418664", "track_1418664"),
    ("873816", "track_0873816"),
    ("39104", "track_0039104"),
    ("1061469", "track_1061469"),
    ("196591", "track_0196591"),
)
SAMPLE_SEED = 20260924
SAMPLE_RATE = 32_000
WINDOW_SAMPLES = 10 * SAMPLE_RATE
FORMATS = {"mp32": ".mp3", "ogg": ".ogg", "flac": ".flac"}
EXPECTED_CODECS = {"mp32": "mp3", "ogg": "vorbis", "flac": "flac"}
AGGREGATE_FIELDS = (
    "effective_kbps",
    "mb_per_minute",
    "download_seconds_per_minute",
    "preprocessing_seconds",
    "model_inference_seconds",
    "feature_cosine_vs_flac",
    "feature_relative_l2_vs_flac",
    "frontend_mel_rmse_vs_flac",
)


def fetch_metadata(client_id: str, track_id: str) -> dict[str, object]:
    url = "https://api.jamendo.com/v3.0/tracks/?" + urlencode(
        {"client_id": client_id, "id": track_id, "format": "json", "limit": 1}
    )
    for attempt in range(3):
        with urlopen(url, timeout=30) as response:
            results = json.load(response).get("results", [])
        if len(results) == 1 and str(results[0].get("id")) == track_id:
            return results[0]
        if attempt < 2:
            time.sleep(0.5)
    raise RuntimeError(f"Jamendo did not return the expected track ID {track_id}")


def main() -> None:
    client_id = os.environ.get("JAMENDO_CLIENT_ID")
    if not client_id:
        raise SystemExit("JAMENDO_CLIENT_ID is not set")
    if not REPO.is_dir():
        raise SystemExit("Run scripts/bootstrap_efficientat.py first")

    metadata_by_id = {track_id: fetch_metadata(client_id, track_id) for track_id, _ in TRACKS}
    for track_id, metadata in metadata_by_id.items():
        if not metadata.get("audiodownload_allowed"):
            raise SystemExit(f"Jamendo does not permit downloading track {track_id}")

    os.chdir(REPO)
    sys.path.insert(0, str(REPO))
    import torch
    from models.dymn.model import get_model
    from models.preprocess import AugmentMelSTFT

    torch.set_num_threads(1)
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if not ffmpeg or not ffprobe:
        raise SystemExit("ffmpeg and ffprobe are required for the format pilot")
    startup_start = time.perf_counter()
    frontend = AugmentMelSTFT(
        n_mels=128,
        sr=SAMPLE_RATE,
        win_length=800,
        hopsize=320,
        n_fft=1024,
        freqm=0,
        timem=0,
        fmin=0.0,
        fmax=15_000.0,
        fmin_aug_range=1,
        fmax_aug_range=2_000,
    ).eval()
    with warnings.catch_warnings(), redirect_stdout(StringIO()):
        warnings.simplefilter("ignore")
        model = get_model(
            num_classes=527,
            width_mult=0.4,
            pretrained_name="dymn04_as",
            pretrain_final_temp=1.0,
        ).cpu().eval()
    startup_seconds = time.perf_counter() - startup_start
    warmup_start = time.perf_counter()
    with torch.inference_mode():
        warm_mel = frontend(torch.zeros(1, WINDOW_SAMPLES)).unsqueeze(0)
        model(warm_mel)
    warmup_seconds = time.perf_counter() - warmup_start

    track_results: list[dict[str, object]] = []
    base_format_order = tuple(FORMATS)
    for track_index, (track_id, mtg_track_id) in enumerate(TRACKS):
        metadata = metadata_by_id[track_id]
        duration = float(metadata["duration"])
        window_start = max(0.0, (duration - 10.0) / 2.0)
        format_order = base_format_order[track_index % len(base_format_order):] + base_format_order[:track_index % len(base_format_order)]
        measured: dict[str, dict[str, object]] = {}
        features: dict[str, torch.Tensor] = {}
        mels: dict[str, torch.Tensor] = {}

        for audio_format in format_order:
            suffix = FORMATS[audio_format]
            fd, temp_name = tempfile.mkstemp(suffix=suffix)
            os.close(fd)
            temp_path = Path(temp_name)
            try:
                byte_count = 0
                download_start = time.perf_counter()
                with temp_path.open("wb") as audio_file:
                    download_url = "https://api.jamendo.com/v3.0/tracks/file/?" + urlencode(
                        {
                            "client_id": client_id,
                            "id": track_id,
                            "audioformat": audio_format,
                            "action": "download",
                        }
                    )
                    with urlopen(Request(download_url), timeout=120) as download:
                        content_type = download.headers.get_content_type()
                        while chunk := download.read(256 * 1024):
                            audio_file.write(chunk)
                            byte_count += len(chunk)
                download_seconds = time.perf_counter() - download_start
                if byte_count == 0 or content_type == "application/json":
                    raise RuntimeError(f"{audio_format} returned no audio (content type {content_type})")

                probe_start = time.perf_counter()
                probe = subprocess.run(
                    [
                        ffprobe, "-v", "error", "-select_streams", "a:0",
                        "-show_entries", "stream=codec_name,sample_rate,channels",
                        "-show_entries", "format=duration,bit_rate",
                        "-of", "json", str(temp_path),
                    ],
                    capture_output=True,
                    text=True,
                    check=True,
                )
                probe_seconds = time.perf_counter() - probe_start
                probe_json = json.loads(probe.stdout)
                stream = probe_json["streams"][0]
                if stream["codec_name"] != EXPECTED_CODECS[audio_format]:
                    raise RuntimeError(f"{audio_format} decoded as unexpected codec {stream['codec_name']}")
                duration_seconds = float(probe_json["format"]["duration"])
                if duration_seconds < window_start + 10.0:
                    raise RuntimeError(f"{audio_format} track {track_id} is too short for the shared window")

                decode_start = time.perf_counter()
                decoded = subprocess.run(
                    [
                        ffmpeg, "-v", "error", "-ss", str(window_start), "-i", str(temp_path),
                        "-t", "10", "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE),
                        "-f", "f32le", "pipe:1",
                    ],
                    capture_output=True,
                    check=True,
                ).stdout
                decode_seconds = time.perf_counter() - decode_start
                waveform = torch.frombuffer(bytearray(decoded), dtype=torch.float32).reshape(1, -1)
                if waveform.shape[1] != WINDOW_SAMPLES:
                    raise RuntimeError(f"{audio_format} track {track_id} did not produce a 10-second window")
                window = waveform.contiguous()

                frontend_start = time.perf_counter()
                with torch.inference_mode():
                    mel = frontend(window).unsqueeze(0)
                frontend_seconds = time.perf_counter() - frontend_start

                inference_start = time.perf_counter()
                with torch.inference_mode():
                    _, feature = model(mel)
                inference_seconds = time.perf_counter() - inference_start
                if not torch.isfinite(feature).all() or mel.shape != (1, 1, 128, 1000):
                    raise RuntimeError(f"Unexpected/non-finite output for {audio_format} track {track_id}")

                features[audio_format] = feature.flatten().detach().cpu()
                mels[audio_format] = mel.flatten().detach().cpu()
                measured[audio_format] = {
                    "bytes": byte_count,
                    "size_mb": round(byte_count / 1_000_000, 3),
                    "download_seconds": round(download_seconds, 3),
                    "download_seconds_per_minute": round(download_seconds * 60 / duration_seconds, 3),
                    "response_content_type": content_type,
                    "codec": stream["codec_name"],
                    "source_sample_rate_hz": int(stream["sample_rate"]),
                    "source_channels": int(stream["channels"]),
                    "duration_seconds": round(duration_seconds, 3),
                    "effective_kbps": round(byte_count * 8 / duration_seconds / 1000, 2),
                    "mb_per_minute": round(byte_count / 1_000_000 * 60 / duration_seconds, 3),
                    "ffprobe_seconds": round(probe_seconds, 3),
                    "decode_resample_mono_window_seconds": round(decode_seconds, 3),
                    "frontend_seconds": round(frontend_seconds, 3),
                    "preprocessing_seconds": round(decode_seconds + frontend_seconds, 3),
                    "model_inference_seconds": round(inference_seconds, 3),
                    "end_to_end_seconds": round(
                        download_seconds + probe_seconds + decode_seconds + frontend_seconds + inference_seconds, 3
                    ),
                    "window_start_seconds": round(window_start, 3),
                    "feature_shape": list(feature.shape),
                }
            finally:
                temp_path.unlink(missing_ok=True)
                if audio_format in measured:
                    measured[audio_format]["audio_file_deleted_after_processing"] = not temp_path.exists()

        reference = features["flac"]
        reference_mel = mels["flac"]
        for audio_format in FORMATS:
            if audio_format == "flac":
                measured[audio_format]["feature_cosine_vs_flac"] = 1.0
                measured[audio_format]["feature_relative_l2_vs_flac"] = 0.0
                measured[audio_format]["frontend_mel_rmse_vs_flac"] = 0.0
                continue
            feature = features[audio_format]
            mel = mels[audio_format]
            measured[audio_format]["feature_cosine_vs_flac"] = round(
                torch.nn.functional.cosine_similarity(feature, reference, dim=0).item(), 8
            )
            measured[audio_format]["feature_relative_l2_vs_flac"] = round(
                (
                    torch.linalg.vector_norm(feature - reference)
                    / torch.linalg.vector_norm(reference).clamp_min(1e-12)
                ).item(),
                8,
            )
            measured[audio_format]["frontend_mel_rmse_vs_flac"] = round(
                torch.sqrt(torch.mean((mel - reference_mel) ** 2)).item(), 8
            )

        track_results.append(
            {
                "jamendo_track_id": track_id,
                "mtg_track_id": mtg_track_id,
                "artist_id": metadata.get("artist_id"),
                "artist": metadata.get("artist_name"),
                "title": metadata.get("name"),
                "metadata_duration_seconds": duration,
                "download_allowed": bool(metadata["audiodownload_allowed"]),
                "window_start_seconds": round(window_start, 3),
                "format_download_order": list(format_order),
                "formats": measured,
            }
        )

    aggregate: dict[str, dict[str, dict[str, float]]] = {}
    for audio_format in FORMATS:
        aggregate[audio_format] = {}
        for metric in AGGREGATE_FIELDS:
            values = [float(track["formats"][audio_format][metric]) for track in track_results]
            aggregate[audio_format][metric] = {
                "mean": round(mean(values), 6),
                "median": round(median(values), 6),
                "min": round(min(values), 6),
                "max": round(max(values), 6),
            }

    report = {
        "sample_seed": SAMPLE_SEED,
        "sample_selection": (
            "Five download-allowed tracks from the pinned clean human-annotation set, "
            "selected by seeded shuffle with one track per artist and metadata duration from 90 to 360 seconds."
        ),
        "track_count": len(track_results),
        "model": "pinned DyMN04-AS frozen checkpoint",
        "sample_rate_hz": SAMPLE_RATE,
        "window_seconds": 10,
        "window_policy": "same absolute midpoint window across formats within each track",
        "format_download_order_policy": "rotated across tracks to reduce fixed format-order bias",
        "model_startup_seconds": round(startup_seconds, 3),
        "synthetic_warmup_seconds_excluded_from_per_format_timings": round(warmup_seconds, 3),
        "cpu_threads": torch.get_num_threads(),
        "aggregate_by_format": aggregate,
        "tracks": track_results,
        "interpretation_limit": (
            "five-track codec and representation comparison; not a listening test or downstream "
            "classification-accuracy evaluation"
        ),
    }
    REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"report": str(REPORT), "aggregate_by_format": aggregate}, indent=2))


if __name__ == "__main__":
    main()

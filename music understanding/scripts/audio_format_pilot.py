from __future__ import annotations

import json
import os
import shutil
import sys
import subprocess
import tempfile
import time
import warnings
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "vendor" / "EfficientAT"
REPORT = ROOT / "reports" / "audio_format_pilot.json"
TRACK_ID = "214"
SAMPLE_RATE = 32_000
WINDOW_SAMPLES = 10 * SAMPLE_RATE
FORMATS = {"mp31": ".mp3", "mp32": ".mp3", "ogg": ".ogg", "flac": ".flac"}


def main() -> None:
    client_id = os.environ.get("JAMENDO_CLIENT_ID")
    if not client_id:
        raise SystemExit("JAMENDO_CLIENT_ID is not set")
    if not REPO.is_dir():
        raise SystemExit("Run scripts/bootstrap_efficientat.py first")

    metadata_url = "https://api.jamendo.com/v3.0/tracks/?" + urlencode(
        {"client_id": client_id, "id": TRACK_ID, "format": "json", "limit": 1, "include": "licenses"}
    )
    with urlopen(metadata_url, timeout=30) as response:
        results = json.load(response).get("results", [])
    if len(results) != 1 or str(results[0].get("id")) != TRACK_ID:
        raise SystemExit("Jamendo did not return the expected track ID")
    metadata = results[0]
    if not metadata.get("audiodownload_allowed"):
        raise SystemExit("Jamendo does not permit downloading this track")

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

    measured: dict[str, dict[str, object]] = {}
    features: dict[str, torch.Tensor] = {}
    mels: dict[str, torch.Tensor] = {}
    for audio_format, suffix in FORMATS.items():
        download_start = time.perf_counter()
        byte_count = 0
        fd, temp_name = tempfile.mkstemp(suffix=suffix)
        os.close(fd)
        temp_path = Path(temp_name)
        try:
            with temp_path.open("wb") as audio_file:
                download_url = "https://api.jamendo.com/v3.0/tracks/file/?" + urlencode(
                    {"client_id": client_id, "id": TRACK_ID, "audioformat": audio_format, "action": "download"}
                )
                with urlopen(Request(download_url), timeout=120) as download:
                    content_type = download.headers.get_content_type()
                    while chunk := download.read(256 * 1024):
                        audio_file.write(chunk)
                        byte_count += len(chunk)
            download_seconds = time.perf_counter() - download_start
            if byte_count == 0 or content_type == "application/json":
                raise RuntimeError(f"{audio_format} returned no audio (content type {content_type})")

            probe = subprocess.run(
                [ffprobe, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=codec_name,sample_rate,channels", "-show_entries", "format=duration,bit_rate", "-of", "json", str(temp_path)],
                capture_output=True,
                text=True,
                check=True,
            )
            probe_json = json.loads(probe.stdout)
            stream = probe_json["streams"][0]
            duration_seconds = float(probe_json["format"]["duration"])
            # Use the same absolute segment for each encoding; some encoders
            # trim different amounts of silence/padding at file boundaries.
            start_seconds = max(0.0, (float(metadata["duration"]) - 10.0) / 2.0)

            decode_start = time.perf_counter()
            decoded = subprocess.run(
                [ffmpeg, "-v", "error", "-ss", str(start_seconds), "-i", str(temp_path), "-t", "10", "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "f32le", "pipe:1"],
                capture_output=True,
                check=True,
            ).stdout
            decode_seconds = time.perf_counter() - decode_start
            waveform = torch.frombuffer(bytearray(decoded), dtype=torch.float32).reshape(1, -1)
            if waveform.shape[1] < WINDOW_SAMPLES:
                waveform = torch.nn.functional.pad(waveform, (0, WINDOW_SAMPLES - waveform.shape[1]))
            window = waveform[:, :WINDOW_SAMPLES].contiguous()

            frontend_start = time.perf_counter()
            with torch.inference_mode():
                mel = frontend(window).unsqueeze(0)
            frontend_seconds = time.perf_counter() - frontend_start

            inference_start = time.perf_counter()
            with torch.inference_mode():
                logits, feature = model(mel)
            inference_seconds = time.perf_counter() - inference_start
            if not torch.isfinite(feature).all() or mel.shape != (1, 1, 128, 1000):
                raise RuntimeError(f"Unexpected/non-finite output for {audio_format}")

            features[audio_format] = feature.flatten().detach().cpu()
            mels[audio_format] = mel.flatten().detach().cpu()
            measured[audio_format] = {
                "bytes": byte_count,
                "download_seconds": round(download_seconds, 3),
                "response_content_type": content_type,
                "codec": stream["codec_name"],
                "source_sample_rate_hz": int(stream["sample_rate"]),
                "source_channels": int(stream["channels"]),
                "duration_seconds": round(duration_seconds, 3),
                "effective_kbps": round(byte_count * 8 / duration_seconds / 1000, 2),
                "ffprobe_bitrate_kbps": round(int(probe_json["format"].get("bit_rate", "0")) / 1000, 2),
                "decode_seconds": round(decode_seconds, 3),
                "decode_resample_mono_window_seconds": round(decode_seconds, 3),
                "frontend_seconds": round(frontend_seconds, 3),
                "model_inference_seconds": round(inference_seconds, 3),
                "post_download_processing_seconds": round(decode_seconds + frontend_seconds + inference_seconds, 3),
                "end_to_end_seconds": round(download_seconds + decode_seconds + frontend_seconds + inference_seconds, 3),
                "window_start_seconds": round(start_seconds, 3),
                "feature_shape": list(feature.shape),
                "audio_file_deleted_after_processing": False,
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
        measured[audio_format]["feature_cosine_vs_flac"] = round(torch.nn.functional.cosine_similarity(feature, reference, dim=0).item(), 8)
        measured[audio_format]["feature_relative_l2_vs_flac"] = round((torch.linalg.vector_norm(feature - reference) / torch.linalg.vector_norm(reference).clamp_min(1e-12)).item(), 8)
        measured[audio_format]["frontend_mel_rmse_vs_flac"] = round(torch.sqrt(torch.mean((mel - reference_mel) ** 2)).item(), 8)

    report = {
        "track_id": TRACK_ID,
        "track_duration_metadata_seconds": int(metadata["duration"]),
        "download_allowed": bool(metadata["audiodownload_allowed"]),
        "license_url": metadata.get("license_ccurl"),
        "model": "pinned DyMN04-AS frozen checkpoint",
        "sample_rate_hz": SAMPLE_RATE,
        "window_seconds": 10,
        "model_startup_seconds": round(startup_seconds, 3),
        "synthetic_warmup_seconds_excluded_from_per_format_timings": round(warmup_seconds, 3),
        "cpu_threads": torch.get_num_threads(),
        "interpretation_limit": "single-track file/representation comparison; not a downstream label-accuracy comparison",
        "formats": measured,
    }
    REPORT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

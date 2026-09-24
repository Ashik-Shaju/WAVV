from __future__ import annotations

import hashlib
import io
import json
import math
import os
import subprocess
import sys
import warnings
from contextlib import redirect_stdout
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "vendor" / "EfficientAT"
COMMIT = "a425fdce92572e602a1d5634799bd9f1f2efa806"
SAMPLE_RATE = 32_000
SAMPLES = 10 * SAMPLE_RATE


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def main() -> None:
    if not REPO.is_dir():
        raise SystemExit("Run scripts/bootstrap_efficientat.py first")
    actual_commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=REPO, text=True
    ).strip()
    if actual_commit != COMMIT:
        raise SystemExit(f"EfficientAT source mismatch: expected {COMMIT}, got {actual_commit}")

    os.chdir(REPO)
    sys.path.insert(0, str(REPO))
    import torch
    import torchaudio
    import torchvision
    from models.dymn.model import get_model
    from models.preprocess import AugmentMelSTFT

    torch.set_num_threads(1)
    torch.manual_seed(0)
    time = torch.arange(SAMPLES, dtype=torch.float32) / SAMPLE_RATE
    waveform = (
        0.25 * torch.sin(2 * math.pi * 220 * time)
        + 0.15 * torch.sin(2 * math.pi * 440 * time)
        + 0.08 * torch.sin(2 * math.pi * 880 * time)
    ).unsqueeze(0)
    fixture_pcm = (waveform.clamp(-1, 1) * 32767).to(torch.int16).numpy().tobytes()
    fixture_sha = sha256_bytes(fixture_pcm)

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
    with warnings.catch_warnings(record=True) as observed_warnings:
        warnings.simplefilter("always")
        with redirect_stdout(io.StringIO()):
            model = get_model(
                num_classes=527,
                width_mult=0.4,
                pretrained_name="dymn04_as",
                pretrain_final_temp=1.0,
            ).cpu().eval()
        checkpoint = REPO / "resources" / "dymn04_as.pt"
        if not checkpoint.is_file():
            raise SystemExit(f"Official loader did not cache checkpoint at {checkpoint}")
        with torch.inference_mode():
            mel = frontend(waveform).unsqueeze(0)
            logits_a, feature_a = model(mel)
            logits_b, feature_b = model(mel)
    checkpoint = REPO / "resources" / "dymn04_as.pt"
    checks = {
        "mel_shape": list(mel.shape),
        "logit_shape": list(logits_a.shape),
        "pooled_feature_shape": list(feature_a.shape),
        "finite": bool(torch.isfinite(mel).all() and torch.isfinite(logits_a).all() and torch.isfinite(feature_a).all()),
        "repeatable": bool(
            torch.allclose(logits_a, logits_b, rtol=0.0, atol=1e-7)
            and torch.allclose(feature_a, feature_b, rtol=0.0, atol=1e-7)
        ),
    }
    if checks["mel_shape"] != [1, 1, 128, 1000]:
        raise SystemExit(f"Unexpected official mel shape: {checks['mel_shape']}")
    if (
        checks["logit_shape"] != [1, 527]
        or checks["pooled_feature_shape"] != [1, 384]
        or not checks["finite"]
        or not checks["repeatable"]
    ):
        raise SystemExit(f"Backbone contract check failed: {checks}")

    digest = hashlib.sha256()
    with checkpoint.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    report = {
        "verified_on": date.today().isoformat(),
        "source_commit": actual_commit,
        "source_license": "MIT",
        "runtime_versions": {
            "python": ".".join(map(str, sys.version_info[:3])),
            "torch": torch.__version__,
            "torchvision": torchvision.__version__,
            "torchaudio": torchaudio.__version__,
        },
        "checkpoint": {
            "url": "https://github.com/fschmid56/EfficientAT/releases/download/v0.0.1/dymn04_as.pt",
            "path_local_ignored": str(checkpoint.relative_to(ROOT)),
            "size_bytes": checkpoint.stat().st_size,
            "sha256": digest.hexdigest(),
            "license": "not specified separately upstream; do not redistribute pending confirmation",
        },
        "fixture": {
            "kind": "deterministic 10-second three-tone mono PCM16 generated in memory",
            "sample_rate": SAMPLE_RATE,
            "sample_count": SAMPLES,
            "pcm16_sha256": fixture_sha,
        },
        "preprocessing": {
            "source": "EfficientAT models/preprocess.py AugmentMelSTFT in eval mode",
            "sample_rate_hz": SAMPLE_RATE,
            "channels": 1,
            "preemphasis": [-0.97, 1.0],
            "n_fft": 1024,
            "window_samples": 800,
            "hop_samples": 320,
            "mel_bins": 128,
            "mel_filterbank": "torchaudio.compliance.kaldi.get_mel_banks",
            "f_min_hz": 0,
            "f_max_hz": 15000,
            "power": 2,
            "log_epsilon": 1e-5,
            "normalization": "(ln(mel + 1e-5) + 4.5) / 5",
            "window_seconds": 10,
            "window_policy": "fixed 10-second window for this baseline; full-song aggregation is deferred",
        },
        "outputs": checks,
        "runtime_warnings": sorted({str(item.message) for item in observed_warnings}),
        "interpretation": "527 logits are AudioSet outputs and are not Wavv labels; pooled final feature is the frozen-feature baseline candidate",
    }
    report_path = ROOT / "reports" / "backbone_contract.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

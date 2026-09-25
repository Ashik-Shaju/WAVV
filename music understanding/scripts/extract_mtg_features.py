from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import time
import warnings
from contextlib import redirect_stdout
from datetime import date
from io import StringIO
from pathlib import Path
from statistics import mean
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from prepare_mtg import SPLITS


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "vendor" / "EfficientAT"
MANIFESTS = ROOT / "data" / "manifests"
FEATURES = ROOT / "data" / "features"
TEMP_AUDIO = ROOT / "data" / "temp_audio"
CONTRACT = ROOT / "reports" / "backbone_contract.json"
SOURCE_COMMIT = "a425fdce92572e602a1d5634799bd9f1f2efa806"
SAMPLE_RATE = 32_000
WINDOW_SAMPLES = 10 * SAMPLE_RATE
FEATURE_DIM = 384
SEED = 42
WINDOW_POLICY = "ffmpeg resample to mono 32 kHz, then pad/crop the deterministic midpoint excerpt to exactly 320000 samples"


def manifest_rows() -> dict[str, list[dict[str, object]]]:
    output = {}
    for split in SPLITS:
        path = MANIFESTS / f"mtg_human_{split}.jsonl"
        if not path.is_file():
            raise SystemExit(f"Missing {path}; run prepare_mtg.py manifest first")
        rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
        if any(row.get("split") != split or row.get("status") != "eligible" for row in rows):
            raise SystemExit(f"Invalid or ineligible row in {path}")
        output[split] = rows
    ids = [str(row["track_id"]) for rows in output.values() for row in rows]
    if len(ids) != len(set(ids)):
        raise SystemExit("Manifest contains duplicate track IDs")
    artist_sets = {
        split: {str(row["artist_id"]) for row in rows}
        for split, rows in output.items()
    }
    for index, first in enumerate(SPLITS):
        for second in SPLITS[index + 1 :]:
            if artist_sets[first] & artist_sets[second]:
                raise SystemExit(f"Manifest contains artist leakage between {first} and {second}")
    return output


def manifest_digest(paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in paths:
        digest.update(path.name.encode("utf-8"))
        digest.update(path.read_bytes())
    return digest.hexdigest()


def select_rows(
    rows_by_split: dict[str, list[dict[str, object]]], limit: int | None
) -> dict[str, list[dict[str, object]]]:
    all_rows = sum(map(len, rows_by_split.values()))
    count = all_rows if limit is None else min(limit, all_rows)
    fit_count = round(count * 0.70)
    validation_count = round(count * 0.15)
    counts = {"fit": fit_count, "validation": validation_count, "test": count - fit_count - validation_count}
    selected = {}
    for index, split in enumerate(SPLITS):
        ordered = sorted(rows_by_split[split], key=lambda row: str(row["track_id"]))
        # The seeded order makes limited timing runs prefixes of the full manifest.
        random.Random(SEED + index).shuffle(ordered)
        selected[split] = ordered[: counts[split]]
    return selected


def download_ogg(client_id: str, jamendo_id: str, destination: Path) -> tuple[int, float, str]:
    query = urlencode({
        "client_id": client_id,
        "id": jamendo_id,
        "audioformat": "ogg",
        "action": "download",
    })
    request = Request(
        "https://api.jamendo.com/v3.0/tracks/file/?" + query,
        headers={"User-Agent": "Wavv-Music-Understanding/0.1"},
    )
    started = time.perf_counter()
    try:
        with urlopen(request, timeout=180) as response, destination.open("wb") as audio:
            content_type = response.headers.get_content_type()
            if content_type == "application/json":
                raise RuntimeError("Jamendo returned a JSON response instead of OGG audio")
            byte_count = 0
            while chunk := response.read(256 * 1024):
                audio.write(chunk)
                byte_count += len(chunk)
    except HTTPError as error:
        raise RuntimeError(f"Jamendo rejected OGG download for track {jamendo_id} (HTTP {error.code})") from None
    if byte_count == 0:
        raise RuntimeError("Jamendo returned an empty OGG response")
    return byte_count, time.perf_counter() - started, content_type


def decode_midpoint(path: Path, ffmpeg: str, ffprobe: str) -> tuple[object, float, float]:
    probe = subprocess.run(
        [ffprobe, "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=codec_name",
         "-show_entries", "format=duration", "-of", "json", str(path)],
        capture_output=True,
        text=True,
        check=True,
    )
    media = json.loads(probe.stdout)
    codec = media["streams"][0]["codec_name"]
    duration = float(media["format"]["duration"])
    if codec != "vorbis" or duration < 10.0:
        raise RuntimeError(f"Expected OGG Vorbis >=10s audio; got codec={codec}, duration={duration}")
    window_start = max(0.0, (duration - 10.0) / 2.0)
    decoded = subprocess.run(
        [ffmpeg, "-v", "error", "-ss", str(window_start), "-i", str(path), "-vn",
         "-af", f"aresample={SAMPLE_RATE},apad=whole_len={WINDOW_SAMPLES},atrim=end_sample={WINDOW_SAMPLES}",
         "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "f32le", "pipe:1"],
        capture_output=True,
        check=True,
    ).stdout
    import torch

    if len(decoded) != WINDOW_SAMPLES * 4:
        raise RuntimeError(f"Expected {WINDOW_SAMPLES * 4} decoded bytes; got {len(decoded)}")
    waveform = torch.frombuffer(bytearray(decoded), dtype=torch.float32).reshape(1, WINDOW_SAMPLES).contiguous()
    if not torch.isfinite(waveform).all():
        raise RuntimeError("Decoded waveform contains non-finite values")
    return waveform, duration, window_start


def load_model() -> tuple[object, object]:
    if not REPO.is_dir():
        raise SystemExit("Run scripts/bootstrap_efficientat.py first")
    actual_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip()
    if actual_commit != SOURCE_COMMIT:
        raise SystemExit(f"EfficientAT source mismatch: expected {SOURCE_COMMIT}, got {actual_commit}")
    os.chdir(REPO)
    sys.path.insert(0, str(REPO))
    import torch
    from models.dymn.model import get_model
    from models.preprocess import AugmentMelSTFT

    torch.set_num_threads(1)
    frontend = AugmentMelSTFT(
        n_mels=128, sr=SAMPLE_RATE, win_length=800, hopsize=320, n_fft=1024,
        freqm=0, timem=0, fmin=0.0, fmax=15_000.0, fmin_aug_range=1, fmax_aug_range=2_000,
    ).eval()
    with warnings.catch_warnings(), redirect_stdout(StringIO()):
        warnings.simplefilter("ignore")
        model = get_model(
            num_classes=527, width_mult=0.4, pretrained_name="dymn04_as", pretrain_final_temp=1.0
        ).cpu().eval()
    return frontend, model


def main() -> None:
    parser = argparse.ArgumentParser(description="Download each selected OGG temporarily, extract pooled features, and delete it; never trains.")
    parser.add_argument("--client-id-env", default="JAMENDO_CLIENT_ID")
    parser.add_argument("--limit", type=int, help="Deterministic timing subset across fit/validation/test; omit to finish the full manifest")
    parser.add_argument("--output", type=Path, default=FEATURES / "mtg_dymn04as_4250.sqlite")
    parser.add_argument("--reuse-cache", type=Path, help="Seed a new cache from a compatible, smaller completed feature cache")
    args = parser.parse_args()
    args.output = args.output.resolve()
    if args.reuse_cache is not None:
        args.reuse_cache = args.reuse_cache.resolve()
    if args.limit is not None and args.limit < 1:
        raise SystemExit("--limit must be positive")
    if args.reuse_cache is not None:
        if args.limit is not None:
            raise SystemExit("--reuse-cache applies only to a full-manifest extraction")
        if args.output.resolve() == args.reuse_cache.resolve():
            raise SystemExit("--output and --reuse-cache must be different files")
        if args.output.exists():
            raise SystemExit("--reuse-cache requires a new output cache; resume an existing cache without this option")
    client_id = os.environ.get(args.client_id_env)
    if not client_id:
        raise SystemExit(f"Set {args.client_id_env} for this process; its value is never written to artifacts")
    if shutil.which("ffmpeg") is None or shutil.which("ffprobe") is None:
        raise SystemExit("ffmpeg and ffprobe are required")

    paths = [MANIFESTS / f"mtg_human_{split}.jsonl" for split in SPLITS]
    rows_by_split = manifest_rows()
    chosen = select_rows(rows_by_split, args.limit)
    rows = [row for split in SPLITS for row in chosen[split]]
    if any(row.get("audiodownload_allowed") is not True for row in rows):
        raise SystemExit("Selected manifest includes an ID without an explicit eligible permission snapshot")

    availability_path = ROOT / "reports" / "jamendo_availability.json"
    manifest_report_path = ROOT / "reports" / "mtg_human_manifest.json"
    availability_report = json.loads(availability_path.read_text(encoding="utf-8"))
    manifest_report = json.loads(manifest_report_path.read_text(encoding="utf-8"))
    candidate_path = MANIFESTS / "mtg_human_candidates.jsonl"
    candidate_sha = hashlib.sha256(candidate_path.read_bytes()).hexdigest()
    availability_sha = hashlib.sha256(availability_path.read_bytes()).hexdigest()
    if (
        manifest_report.get("candidate_inventory_sha256") != candidate_sha
        or manifest_report.get("availability_report_sha256") != availability_sha
        or manifest_report.get("availability_audited_on") != availability_report["audited_on"]
        or manifest_report.get("manifest_sha256") != manifest_digest(paths)
    ):
        raise SystemExit("Frozen manifest does not match the current Jamendo availability snapshot; rebuild it")

    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    if contract["source_commit"] != SOURCE_COMMIT or contract["outputs"]["pooled_feature_shape"] != [1, FEATURE_DIM]:
        raise SystemExit("Backbone contract is missing or no longer matches the pinned extractor")
    checkpoint_sha = contract["checkpoint"]["sha256"]
    contract_digest = hashlib.sha256(
        json.dumps(contract["preprocessing"], sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    checkpoint_path = REPO / "resources" / "dymn04_as.pt"
    actual_checkpoint_sha = hashlib.sha256(checkpoint_path.read_bytes()).hexdigest()
    if actual_checkpoint_sha != checkpoint_sha:
        raise SystemExit("Downloaded checkpoint hash differs from the verified backbone contract")
    manifest_sha = manifest_digest(paths)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    TEMP_AUDIO.mkdir(parents=True, exist_ok=True)
    for stale in TEMP_AUDIO.glob("wavv_mu_*.ogg"):
        stale.unlink()

    frontend, model = load_model()
    import torch

    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    connection = sqlite3.connect(args.output)
    connection.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    connection.execute(
        "CREATE TABLE IF NOT EXISTS features (track_id TEXT PRIMARY KEY, split TEXT NOT NULL, "
        "jamendo_track_id TEXT NOT NULL, artist_id TEXT NOT NULL, duration_seconds REAL NOT NULL, "
        "window_start_seconds REAL NOT NULL, feature BLOB NOT NULL)"
    )
    window_policy_sha = hashlib.sha256(WINDOW_POLICY.encode("utf-8")).hexdigest()
    expected_meta = {
        "manifest_sha256": manifest_sha,
        "candidate_inventory_sha256": candidate_sha,
        "availability_report_sha256": availability_sha,
        "checkpoint_sha256": checkpoint_sha,
        "preprocessing_sha256": contract_digest,
        "window_policy_sha256": window_policy_sha,
        "feature_dim": str(FEATURE_DIM),
    }
    for key, value in expected_meta.items():
        prior = connection.execute("SELECT value FROM metadata WHERE key=?", (key,)).fetchone()
        if prior and prior[0] != value:
            raise SystemExit(f"Existing feature cache {key} does not match current source contract")
        connection.execute("INSERT OR IGNORE INTO metadata(key,value) VALUES(?,?)", (key, value))

    if args.reuse_cache is not None:
        if not args.reuse_cache.is_file():
            raise SystemExit(f"Reuse cache does not exist: {args.reuse_cache}")
        old_connection = sqlite3.connect(args.reuse_cache.resolve().as_uri() + "?mode=ro", uri=True)
        try:
            if old_connection.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise SystemExit("Reuse cache failed SQLite integrity_check")
            old_meta = dict(old_connection.execute("SELECT key,value FROM metadata"))
            for key, value in expected_meta.items():
                if key != "manifest_sha256" and old_meta.get(key) != value:
                    raise SystemExit(f"Reuse cache {key} does not match current source contract")
            old_rows = old_connection.execute(
                "SELECT track_id,split,jamendo_track_id,artist_id,duration_seconds,window_start_seconds,feature FROM features"
            ).fetchall()
        finally:
            old_connection.close()

        expected_rows = {
            str(row["track_id"]): (str(row["split"]), str(row["jamendo_track_id"]), str(row["artist_id"]))
            for split_rows in rows_by_split.values()
            for row in split_rows
        }
        for track_id, split, jamendo_id, artist_id, _, _, blob in old_rows:
            if track_id not in expected_rows or expected_rows[track_id] != (split, jamendo_id, artist_id):
                raise SystemExit(f"Reuse cache row does not match the expanded manifest: {track_id}")
            if len(blob) != FEATURE_DIM * 4 or not torch.isfinite(torch.frombuffer(bytearray(blob), dtype=torch.float32)).all():
                raise SystemExit(f"Reuse cache contains an invalid feature vector: {track_id}")
        connection.executemany("INSERT INTO features VALUES(?,?,?,?,?,?,?)", old_rows)
        connection.commit()
        print(f"Reused {len(old_rows)} verified feature rows from {args.reuse_cache}")

    connection.commit()

    start_all = time.perf_counter()
    byte_counts: list[int] = []
    download_times: list[float] = []
    processing_times: list[float] = []
    last_processed_track = None

    def save_checkpoint(state: str) -> None:
        if args.limit is not None:
            return
        manifest_count = sum(map(len, rows_by_split.values()))
        cached_count = connection.execute("SELECT COUNT(*) FROM features").fetchone()[0]
        checkpoint = {
            "checkpoint_date": date.today().isoformat(),
            "state": state,
            "manifest_sha256": manifest_sha,
            "candidate_inventory_sha256": candidate_sha,
            "availability_report_sha256": availability_sha,
            "manifest_rows": manifest_count,
            "cached_rows": cached_count,
            "feature_cache_rows": cached_count,
            "remaining_rows": manifest_count - cached_count,
            "full_manifest_complete": cached_count == manifest_count,
            "cache_path_local_ignored": str(args.output.resolve().relative_to(ROOT)),
            "last_processed_track_id": last_processed_track,
            "temporary_audio_files": len(list(TEMP_AUDIO.glob("wavv_mu_*.ogg"))),
            "source_audio_retained": False,
            "feature_shape_per_track": [FEATURE_DIM],
            "training_started": False,
            "completion_report": "reports/feature_extraction.json",
        }
        checkpoint_path = ROOT / "reports" / "feature_extraction_checkpoint.json"
        report_paths = [checkpoint_path]
        if state != "pretraining_pipeline_complete":
            report_paths.append(ROOT / "reports" / "feature_extraction.json")
        for report_path in report_paths:
            temporary_path = report_path.with_suffix(".json.tmp")
            temporary_path.write_text(json.dumps(checkpoint, indent=2) + "\n", encoding="utf-8")
            temporary_path.replace(report_path)

    try:
        save_checkpoint("feature_extraction_in_progress")
        for index, row in enumerate(rows, start=1):
            track_id = str(row["track_id"])
            exists = connection.execute("SELECT 1 FROM features WHERE track_id=?", (track_id,)).fetchone()
            if exists:
                print(f"Already cached {index}/{len(rows)} {track_id}")
                continue
            fd, temp_name = tempfile.mkstemp(prefix="wavv_mu_", suffix=".ogg", dir=TEMP_AUDIO)
            os.close(fd)
            audio_path = Path(temp_name)
            try:
                track_start = time.perf_counter()
                byte_count, download_seconds, _ = download_ogg(client_id, str(row["jamendo_track_id"]), audio_path)
                waveform, duration, window_start = decode_midpoint(audio_path, ffmpeg, ffprobe)
                with torch.inference_mode():
                    mel = frontend(waveform).unsqueeze(0)
                    _, feature = model(mel)
                    _, repeated = model(mel)
                if mel.shape != (1, 1, 128, 1000) or feature.shape != (1, FEATURE_DIM):
                    raise RuntimeError(f"Unexpected frontend/backbone shapes for {track_id}")
                if not torch.isfinite(feature).all() or not torch.allclose(feature, repeated, rtol=0.0, atol=1e-7):
                    raise RuntimeError(f"Non-finite or non-repeatable feature for {track_id}")
                blob = feature.flatten().detach().cpu().numpy().astype("<f4", copy=False).tobytes()
                if len(blob) != FEATURE_DIM * 4:
                    raise RuntimeError(f"Unexpected feature byte width for {track_id}")
                connection.execute(
                    "INSERT INTO features VALUES(?,?,?,?,?,?,?)",
                    (track_id, str(row["split"]), str(row["jamendo_track_id"]), str(row["artist_id"]),
                     duration, window_start, sqlite3.Binary(blob)),
                )
                connection.commit()
                byte_counts.append(byte_count)
                download_times.append(download_seconds)
                processing_times.append(time.perf_counter() - track_start - download_seconds)
                print(f"Extracted {index}/{len(rows)} {track_id}")
            finally:
                audio_path.unlink(missing_ok=True)
            last_processed_track = track_id
            save_checkpoint("feature_extraction_in_progress")

        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"Feature cache integrity check failed: {integrity}")
        cached = connection.execute(
            "SELECT track_id, split, jamendo_track_id, artist_id, feature FROM features"
        ).fetchall()
        expected_rows = {
            str(row["track_id"]): (
                str(row["split"]), str(row["jamendo_track_id"]), str(row["artist_id"])
            )
            for split_rows in rows_by_split.values()
            for row in split_rows
        }
        cached_ids = {record[0] for record in cached}
        if not cached_ids <= set(expected_rows):
            raise RuntimeError("Feature cache contains IDs outside the frozen manifests")
        for track_id, split, jamendo_id, artist_id, blob in cached:
            if expected_rows[track_id] != (split, jamendo_id, artist_id):
                raise RuntimeError(f"Cached metadata does not match the frozen manifest for {track_id}")
            if len(blob) != FEATURE_DIM * 4 or not torch.isfinite(torch.frombuffer(bytearray(blob), dtype=torch.float32)).all():
                raise RuntimeError(f"Invalid feature vector stored for {track_id}")
        full_ids = {str(row["track_id"]) for split in rows_by_split.values() for row in split}
        complete = cached_ids == full_ids
        remaining = list(TEMP_AUDIO.glob("wavv_mu_*.ogg"))
        if remaining:
            raise RuntimeError(f"Temporary audio files remain: {len(remaining)}")
        report = {
            "completed_on": date.today().isoformat(),
            "model": "frozen DyMN04-AS pooled final feature",
            "source_commit": SOURCE_COMMIT,
            "checkpoint_sha256": checkpoint_sha,
            "manifest_sha256": manifest_sha,
            "candidate_inventory_sha256": candidate_sha,
            "availability_report_sha256": availability_sha,
            "seed": SEED,
            "window": {
                "seconds": 10,
                "policy": WINDOW_POLICY,
                "sample_rate_hz": SAMPLE_RATE,
                "channels": 1,
            },
            "feature_shape_per_track": [FEATURE_DIM],
            "feature_dtype": "little-endian float32",
            "selected_for_this_run": {split: len(chosen[split]) for split in SPLITS},
            "feature_cache_rows": len(cached_ids),
            "manifest_rows": len(full_ids),
            "full_manifest_complete": complete,
            "cached_id_split_artist_mapping_matches_manifest": True,
            "permission_snapshot_audited_on": availability_report["audited_on"],
            "permission_checked_by_per_track_download_endpoint": True,
            "downloaded_ogg_bytes_this_run": sum(byte_counts),
            "mean_download_seconds_per_new_track_this_run": mean(download_times) if download_times else None,
            "mean_decode_and_inference_seconds_per_new_track_this_run": mean(processing_times) if processing_times else None,
            "wall_seconds_this_run": round(time.perf_counter() - start_all, 3),
            "repeatability_checked_for_every_new_feature": True,
            "all_cached_features_finite_and_384d": True,
            "source_audio_retained": False,
            "feature_cache_path": str(args.output.relative_to(ROOT)),
            "feature_cache_size_bytes": args.output.stat().st_size,
            "training_started": False,
        }
        if args.limit is not None:
            report_path = ROOT / "reports" / f"feature_extraction_pilot_{args.limit}.json"
        else:
            report_path = ROOT / "reports" / "feature_extraction.json"
        report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        save_checkpoint("pretraining_pipeline_complete" if complete else "feature_extraction_incomplete")
        print(json.dumps({"report": str(report_path), "feature_cache_rows": len(cached_ids), "full_manifest_complete": complete}, indent=2))
    except BaseException:
        save_checkpoint("feature_extraction_interrupted")
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    main()

from __future__ import annotations

import argparse
import csv
import json
import random
import re
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
METADATA = DATA / "metadata"
REPORTS = ROOT / "reports"
OUTPUT = DATA / "manifests"
SEED = 42
MTG_PARTITIONS = {
    "train": "mtg_split0_train.tsv",
    "validation": "mtg_split0_validation.tsv",
    "test": "mtg_split0_test.tsv",
}
DEAM_PARTITIONS = {
    "development": "metadata_2013.csv",
    "test_2014": "metadata_2014.csv",
    "test_2015": "metadata_2015.csv",
}


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def safe_audio_path(root: Path, relative: str) -> Path:
    candidate = (root / relative).resolve()
    if not candidate.is_relative_to(root.resolve()):
        raise ValueError(f"Audio path escapes its configured root: {relative}")
    return candidate


def normalized_fields(row: dict[str, str | None]) -> dict[str, str]:
    return {
        re.sub(r"[^a-z0-9]", "", (key or "").lower()): value.strip()
        for key, value in row.items()
        if isinstance(value, str)
    }


def build_mtg(audio_root: Path, labels: dict[str, list[str]]) -> dict[str, dict[str, int]]:
    counts = {}
    for split, filename in MTG_PARTITIONS.items():
        output = []
        with (METADATA / filename).open(encoding="utf-8-sig", newline="") as source:
            for row in csv.DictReader(source, delimiter="\t"):
                track_id = row["TRACK_ID"].strip()
                relative = row["PATH"].strip().replace("\\", "/")
                audio = safe_audio_path(audio_root, relative)
                tags = {key: set() for key in labels}
                tag_values = [row.get("TAGS", ""), *(row.get(None) or [])]
                for value in tag_values:
                    for raw_tag in value.split(","):
                        category, sep, name = raw_tag.strip().partition("---")
                        if sep:
                            key = {"genre": "genre", "mood/theme": "mood_theme", "instrument": "instruments"}.get(category)
                            if key and name in labels[key]:
                                tags[key].add(name)
                output.append({
                    "dataset": "mtg_jamendo",
                    "track_id": track_id,
                    "artist_id": row["ARTIST_ID"].strip(),
                    "split": split,
                    "audio_path": str(audio),
                    "audio_exists": audio.is_file(),
                    "duration_seconds": float(row["DURATION"]),
                    "labels": {key: sorted(value) for key, value in tags.items()},
                })
        write_jsonl(OUTPUT / f"mtg_{split}.jsonl", output)
        counts[split] = {"tracks": len(output), "audio_missing": sum(not row["audio_exists"] for row in output)}
    return counts


def build_deam(audio_root: Path) -> dict[str, dict[str, int]]:
    tracks: dict[str, list[dict[str, str]]] = {}
    with zipfile.ZipFile(METADATA / "deam_metadata.zip") as archive:
        for split, filename in DEAM_PARTITIONS.items():
            member = f"metadata/{filename}"
            rows = []
            with archive.open(member) as binary:
                for raw in csv.DictReader((line.decode("utf-8-sig") for line in binary)):
                    row = normalized_fields(raw)
                    track_id = row.get("id") or row.get("songid")
                    if not track_id:
                        raise ValueError(f"Missing DEAM ID in {member}")
                    name = row.get("filename") or f"{track_id}.mp3"
                    rows.append({"track_id": track_id, "filename": name, "title": row.get("songtitle") or row.get("track", ""), "artist": row.get("artist", "")})
            tracks[split] = rows

    development = sorted(tracks.pop("development"), key=lambda row: int(row["track_id"]))
    shuffled = development.copy()
    random.Random(SEED).shuffle(shuffled)
    train_ids = {row["track_id"] for row in shuffled[: round(len(shuffled) * 0.8)]}
    outputs: dict[str, list[dict[str, object]]] = {"train": [], "validation": [], "test_2014": [], "test_2015": []}
    for row in development:
        split = "train" if row["track_id"] in train_ids else "validation"
        audio = safe_audio_path(audio_root, row["filename"])
        outputs[split].append({**row, "dataset": "deam", "split": split, "audio_path": str(audio), "audio_exists": audio.is_file(), "dynamic_annotation_archive": "../metadata/deam_annotations.zip"})
    for official_split, rows in tracks.items():
        split = official_split
        for row in rows:
            audio = safe_audio_path(audio_root, row["filename"])
            outputs[split].append({**row, "dataset": "deam", "split": split, "audio_path": str(audio), "audio_exists": audio.is_file(), "dynamic_annotation_archive": "../metadata/deam_annotations.zip"})
    counts = {}
    for split, rows in outputs.items():
        write_jsonl(OUTPUT / f"deam_{split}.jsonl", rows)
        counts[split] = {"tracks": len(rows), "audio_missing": sum(not row["audio_exists"] for row in rows)}
    return counts


def self_check() -> None:
    assert safe_audio_path(Path("C:/data/audio"), "track/a.mp3").name == "a.mp3"
    try:
        safe_audio_path(Path("C:/data/audio"), "../outside.mp3")
    except ValueError:
        pass
    else:
        raise AssertionError("audio root traversal was not rejected")
    row = normalized_fields({" SongId": " 42 ", "Filename": " \t42.mp3"})
    assert row["songid"] == "42" and row["filename"] == "42.mp3"


def main() -> None:
    parser = argparse.ArgumentParser(description="Create deterministic per-track manifests; does not download audio or train a model.")
    parser.add_argument("--mtg-audio-root", type=Path, default=DATA / "audio" / "mtg_jamendo")
    parser.add_argument("--deam-audio-root", type=Path, default=DATA / "audio" / "deam")
    parser.add_argument("--self-check", action="store_true")
    args = parser.parse_args()
    if args.self_check:
        self_check()
        print("manifest helpers passed")
        return

    audit = json.loads((REPORTS / "data_audit.json").read_text(encoding="utf-8"))
    labels = {name: [item["label"] for item in audit["mtg_jamendo"]["compact_v1_label_map"][key]] for name, key in (("genre", "genre"), ("mood_theme", "mood_theme"), ("instruments", "instruments"))}
    mtg = build_mtg(args.mtg_audio_root, labels)
    deam = build_deam(args.deam_audio_root)
    report = {"seed": SEED, "mtg_audio_root": str(args.mtg_audio_root.resolve()), "deam_audio_root": str(args.deam_audio_root.resolve()), "mtg": mtg, "deam": deam, "manifest_directory": str(OUTPUT.resolve())}
    report["audio_missing"] = sum(split["audio_missing"] for split in (*mtg.values(), *deam.values()))
    (REPORTS / "manifest_build.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

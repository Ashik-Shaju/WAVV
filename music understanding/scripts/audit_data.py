from __future__ import annotations

import csv
import hashlib
import io
import json
import re
import sys
import urllib.request
import zipfile
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data" / "metadata"
REPORTS = ROOT / "reports"
MTG_COMMIT = "cafd8e20c265ed84f1e61f1c875327971f43a62f"
MTG_BASE = f"https://raw.githubusercontent.com/MTG/mtg-jamendo-dataset/{MTG_COMMIT}/"
SOURCES = {
    "mtg_split0_train.tsv": MTG_BASE + "data/splits/split-0/autotagging-train.tsv",
    "mtg_split0_validation.tsv": MTG_BASE + "data/splits/split-0/autotagging-validation.tsv",
    "mtg_split0_test.tsv": MTG_BASE + "data/splits/split-0/autotagging-test.tsv",
    "mtg_split1_train.tsv": MTG_BASE + "data/splits/split-1/autotagging-train.tsv",
    "mtg_split1_test.tsv": MTG_BASE + "data/splits/split-1/autotagging-test.tsv",
    "mtg_human_clean.tsv": MTG_BASE + "derived/music-classification-annotations/music-classification-annotations-clean.tsv",
    "mtg_raw_metadata.tsv": MTG_BASE + "data/raw.meta.tsv",
    "deam_metadata.zip": "https://cvml.unige.ch/databases/DEAM/metadata.zip",
    "deam_annotations.zip": "https://cvml.unige.ch/databases/DEAM/DEAM_Annotations.zip",
}
HUMAN_COUNTS = {
    "genre_dortmund": 612,
    "genre_rosamerica": 573,
    "genre_tzanetakis": 411,
    "genre_electronic": 180,
    "mood_acoustic": 6429,
    "mood_aggressive": 7686,
    "mood_electronic": 7143,
    "mood_happy": 5702,
    "mood_party": 7476,
    "mood_relaxed": 6163,
    "mood_sad": 5678,
    "danceability": 4476,
    "voice_instrumental": 2070,
    "gender": 7533,
    "tonal_atonal": 8756,
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_tsv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file, delimiter="\t")
        if not reader.fieldnames:
            raise ValueError(f"No TSV header: {path}")
        rows: list[dict[str, str]] = []
        for row in reader:
            clean = {str(k).strip(): (v or "").strip() for k, v in row.items() if k is not None and isinstance(v, str)}
            extra = row.get(None)
            if isinstance(extra, list):
                clean["extra_tags"] = "\t".join(value.strip() for value in extra if value)
            rows.append(clean)
        return list(reader.fieldnames), rows


def field(row: dict[str, str], *names: str) -> str:
    wanted = {re.sub(r"[^a-z0-9]", "", name.lower()) for name in names}
    for key, value in row.items():
        if re.sub(r"[^a-z0-9]", "", key.lower()) in wanted:
            return value
    return ""


def track_id(row: dict[str, str]) -> str:
    value = field(row, "track_id", "trackid", "song_id", "songid")
    if not value:
        value = next(iter(row.values()), "")
    return value.strip()


def tags_in(row: dict[str, str]) -> list[str]:
    tags: list[str] = []
    for value in row.values():
        if "---" in value:
            tags.extend(item.strip() for item in re.split(r"[,\t]", value) if "---" in item)
    return tags


def category(tag: str) -> str:
    return tag.split("---", 1)[0].strip().lower()


def label(tag: str) -> str:
    return tag.split("---", 1)[1].strip().lower()


def normalize_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.lower())


def top_labels(counts: Counter[str], limit: int) -> list[dict[str, object]]:
    return [
        {"label": name, "train_tracks": count}
        for name, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:limit]
    ]


def selected_support(counts: Counter[str], selected: list[dict[str, object]]) -> dict[str, int]:
    return {item["label"]: counts.get(item["label"], 0) for item in selected}


def annotation_triplets(row: dict[str, str]) -> list[tuple[str, tuple[str, ...]]]:
    parsed = []
    for value in row.values():
        for item in value.split("\t"):
            if "---" not in item:
                continue
            taxonomy, answers = item.split("---", 1)
            parsed.append((taxonomy.strip().lower(), tuple(answer.strip().lower() for answer in answers.split(","))))
    return parsed


def excluded_annotation_answer(answer: str) -> bool:
    return answer in {"unmatched", "instrumental"}


def fetch() -> None:
    DATA.mkdir(parents=True, exist_ok=True)
    REPORTS.mkdir(parents=True, exist_ok=True)
    lock_path = REPORTS / "source_lock.json"
    prior = json.loads(lock_path.read_text(encoding="utf-8")) if lock_path.exists() else None
    locked = prior.get("files", {}) if prior else {}
    files: dict[str, dict[str, object]] = {}
    for filename, url in SOURCES.items():
        target = DATA / filename
        if not target.exists():
            partial = target.with_suffix(target.suffix + ".part")
            request = urllib.request.Request(url, headers={"User-Agent": "Wavv-Music-Understanding/0.1"})
            with urllib.request.urlopen(request, timeout=60) as response, partial.open("wb") as output:
                while block := response.read(1024 * 1024):
                    output.write(block)
            partial.replace(target)
        actual_hash = sha256(target)
        expected = locked.get(filename, {}).get("sha256") if filename in locked else None
        if expected and actual_hash != expected:
            raise SystemExit(f"Source hash changed for {filename}: expected {expected}, got {actual_hash}")
        files[filename] = {
            "url": url,
            "size_bytes": target.stat().st_size,
            "sha256": actual_hash,
        }
    write_json(lock_path, {
        "fetched_on": date.today().isoformat(),
        "mtg_repository_commit": MTG_COMMIT,
        "files": files,
    })
    print(f"Fetched {len(files)} metadata/annotation sources; no audio downloaded. Hash lock: {lock_path}")


def split_summary(path: Path) -> dict[str, object]:
    headers, rows = parse_tsv(path)
    ids = [track_id(row) for row in rows]
    artists = [field(row, "artist_id", "artistid") for row in rows]
    if not ids or any(not value for value in ids):
        raise ValueError(f"Missing track IDs in {path}; headers={headers}")
    return {
        "track_ids": set(ids),
        "artist_ids": set(a for a in artists if a),
        "rows": len(rows),
        "duplicate_track_rows": len(ids) - len(set(ids)),
    }


def human_summary(path: Path) -> dict[str, object]:
    _, rows = parse_tsv(path)
    ids: set[str] = set()
    counts: Counter[str] = Counter()
    label_counts: dict[str, Counter[str]] = defaultdict(Counter)
    excluded_special_labels: dict[str, Counter[str]] = defaultdict(Counter)
    annotation_issues: Counter[str] = Counter()
    for row in rows:
        tid = track_id(row)
        if tid:
            ids.add(tid)
        for taxonomy, answers in annotation_triplets(row):
            if taxonomy not in HUMAN_COUNTS:
                continue
            if len(answers) != 3:
                annotation_issues["not_three_raters"] += 1
                continue
            if len(set(answers)) != 1:
                annotation_issues["not_unanimous"] += 1
                continue
            answer = answers[0]
            if excluded_annotation_answer(answer):
                excluded_special_labels[taxonomy][answer] += 1
                continue
            label_counts[taxonomy][answer] += 1
    for taxonomy, labels in label_counts.items():
        counts[taxonomy] = sum(labels.values())
    return {
        "track_ids": ids,
        "rows": len(rows),
        "counts": dict(counts),
        "label_counts": {name: dict(labels) for name, labels in label_counts.items()},
        "excluded_special_labels": {name: dict(labels) for name, labels in excluded_special_labels.items()},
        "annotation_issues": dict(annotation_issues),
    }


def deam_summary(metadata_path: Path, annotations_path: Path) -> dict[str, object]:
    metadata_ids: set[str] = set()
    metadata_files: dict[str, dict[str, object]] = {}
    with zipfile.ZipFile(metadata_path) as archive:
        for name in sorted(name for name in archive.namelist() if name.lower().endswith(".csv")):
            with archive.open(name) as stream:
                rows = list(csv.DictReader(io.TextIOWrapper(stream, encoding="utf-8-sig", newline="")))
            if not rows:
                raise ValueError(f"Empty DEAM metadata CSV: {name}")
            id_column = next((key for key in rows[0] if key.strip().lower() in {"id", "song_id"}), None)
            if not id_column:
                raise ValueError(f"No track ID column in DEAM metadata CSV: {name}")
            ids = {row[id_column].strip() for row in rows if row.get(id_column, "").strip()}
            if len(ids) != len(rows) or metadata_ids & ids:
                raise ValueError(f"Duplicate or missing DEAM metadata IDs in {name}")
            metadata_ids.update(ids)
            metadata_files[Path(name).name] = {"tracks": len(rows), "unique_track_ids": len(ids)}

    dimensions = {
        "arousal": "annotations/annotations averaged per song/dynamic (per second annotations)/arousal.csv",
        "valence": "annotations/annotations averaged per song/dynamic (per second annotations)/valence.csv",
    }
    dynamic_annotations = {}
    with zipfile.ZipFile(annotations_path) as archive:
        for dimension, name in dimensions.items():
            with archive.open(name) as stream:
                reader = csv.reader(io.TextIOWrapper(stream, encoding="utf-8-sig", newline=""))
                header = next(reader)
                timestamps = [int(match.group(1)) for column in header if (match := re.fullmatch(r"sample_(\d+)ms", column.strip()))]
                ids = set()
                rows = 0
                for row in reader:
                    if not row or not row[0].strip():
                        continue
                    ids.add(row[0].strip())
                    rows += 1
            if rows != len(ids) or ids != metadata_ids:
                raise ValueError(f"DEAM {dimension} annotation IDs do not match metadata IDs")
            if not timestamps or min(timestamps) != 15_000:
                raise ValueError(f"Unexpected DEAM {dimension} timestamp range")
            dynamic_annotations[dimension] = {
                "tracks": rows,
                "first_timestamp_ms": min(timestamps),
                "sample_interval_ms": min(b - a for a, b in zip(timestamps, timestamps[1:])),
                "sample_count": len(timestamps),
                "track_ids_match_metadata": True,
            }
    return {"metadata_files": metadata_files, "metadata_tracks": len(metadata_ids), "dynamic_annotations": dynamic_annotations}


def source_shape_from_zip(path: Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        return sorted(name for name in archive.namelist() if not name.endswith("/"))


def self_check() -> None:
    rows = [
        {"TRACK_ID": "1", "ARTIST_ID": "a", "TAGS": "genre---rock,mood/theme---happy"},
        {"TRACK_ID": "2", "ARTIST_ID": "b", "TAGS": "genre---jazz"},
    ]
    assert [track_id(row) for row in rows] == ["1", "2"]
    assert tags_in(rows[0]) == ["genre---rock", "mood/theme---happy"]
    assert category("mood/theme---happy") == "mood/theme"
    assert label("genre---rock") == "rock"
    assert annotation_triplets({"ANNOTATIONS": "genre_dortmund---rock,rock,rock", "extra_tags": "voice_instrumental---voice,voice,voice\tmood_happy---happy,not_happy,happy"}) == [
        ("genre_dortmund", ("rock", "rock", "rock")),
        ("voice_instrumental", ("voice", "voice", "voice")),
        ("mood_happy", ("happy", "not_happy", "happy")),
    ]
    assert normalize_name("AC/DC - Rock!") == "acd crock".replace(" ", "")
    assert excluded_annotation_answer("unmatched")
    assert excluded_annotation_answer("instrumental")
    assert not excluded_annotation_answer("female")


def audit() -> None:
    lock = json.loads((REPORTS / "source_lock.json").read_text(encoding="utf-8"))
    for filename, record in lock["files"].items():
        path = DATA / filename
        if not path.exists() or sha256(path) != record["sha256"]:
            raise SystemExit(f"Missing or changed locked source: {filename}")

    names = {
        "train": "mtg_split0_train.tsv",
        "validation": "mtg_split0_validation.tsv",
        "test": "mtg_split0_test.tsv",
        "split1_train": "mtg_split1_train.tsv",
        "split1_test": "mtg_split1_test.tsv",
    }
    splits = {name: split_summary(DATA / filename) for name, filename in names.items()}
    partitions = ["train", "validation", "test"]
    overlaps: dict[str, dict[str, int]] = {}
    artist_overlaps: dict[str, dict[str, int]] = {}
    for i, left in enumerate(partitions):
        for right in partitions[i + 1 :]:
            overlaps[f"{left}_x_{right}"] = {
                "tracks": len(splits[left]["track_ids"] & splits[right]["track_ids"]),
                "artists": len(splits[left]["artist_ids"] & splits[right]["artist_ids"]),
            }
            artist_overlaps[f"{left}_x_{right}"] = {
                "artists": len(splits[left]["artist_ids"] & splits[right]["artist_ids"])
            }
    for name in partitions:
        if splits[name]["duplicate_track_rows"] or not splits[name]["track_ids"]:
            raise SystemExit(f"Invalid split file: {name}: {splits[name]}")
    if any(value["tracks"] for value in overlaps.values()) or any(value["artists"] for value in artist_overlaps.values()):
        raise SystemExit(f"Split-0 track or artist leakage found: {overlaps}")

    human = human_summary(DATA / "mtg_human_clean.tsv")
    human_ids = human["track_ids"]
    human_split_membership = {
        name: len(human_ids & splits[name]["track_ids"])
        for name in partitions
    }
    if human_split_membership["train"] or human_split_membership["validation"]:
        raise SystemExit(f"Human annotation leakage into split-0 fitting/tuning: {human_split_membership}")
    if human_split_membership["test"] != len(human_ids):
        raise SystemExit(f"Human annotations are not all in split-0 test: {human_split_membership}")

    split1_leaks = {
        "split0_test_in_split1_train": len(splits["test"]["track_ids"] & splits["split1_train"]["track_ids"]),
        "split0_test_in_split1_test": len(splits["test"]["track_ids"] & splits["split1_test"]["track_ids"]),
    }
    train_counts: dict[str, Counter[str]] = defaultdict(Counter)
    validation_counts: dict[str, Counter[str]] = defaultdict(Counter)
    test_counts: dict[str, Counter[str]] = defaultdict(Counter)
    for split_name, bucket in (("train", train_counts), ("validation", validation_counts), ("test", test_counts)):
        _, rows = parse_tsv(DATA / names[split_name])
        for row in rows:
            for tag in set(tags_in(row)):
                bucket[category(tag)][label(tag)] += 1

    compact_label_map = {
        "selection_rule": "take the highest-support exact uploader tags from split-0 train only; ties sort alphabetically; no taxonomy crosswalk is inferred",
        "genre": top_labels(train_counts["genre"], 20),
        "mood_theme": top_labels(train_counts["mood/theme"], 12),
        "instruments": top_labels(train_counts["instrument"], 15),
    }

    expected_differences = {
        taxonomy: {"published": expected, "parsed": human["counts"].get(taxonomy, 0)}
        for taxonomy, expected in HUMAN_COUNTS.items()
        if human["counts"].get(taxonomy, 0) != expected
    }
    deam_archives = {
        filename: source_shape_from_zip(DATA / filename)
        for filename in ("deam_metadata.zip", "deam_annotations.zip")
    }
    deam = deam_summary(DATA / "deam_metadata.zip", DATA / "deam_annotations.zip")
    task_matrix = {
        "genre": {
            "training_source": "MTG-Jamendo split-0 train uploader tags (weak, multi-label)",
            "validation_source": "MTG-Jamendo split-0 validation uploader tags",
            "final_weak_label_evaluation": "MTG-Jamendo split-0 test uploader tags",
            "training_tag_support_tracks": dict(sorted(train_counts["genre"].items(), key=lambda x: (-x[1], x[0]))),
            "v1_labels": compact_label_map["genre"],
            "validation_support_for_v1_labels": selected_support(validation_counts["genre"], compact_label_map["genre"]),
            "test_support_for_v1_labels": selected_support(test_counts["genre"], compact_label_map["genre"]),
            "human_consensus_taxonomies": {k: v for k, v in human["counts"].items() if k.startswith("genre_")},
            "human_mapping_status": "separate taxonomy; no crosswalk is inferred",
            "fit_ready": bool(train_counts["genre"]),
            "metric": "mAP, macro/micro F1, per-label support",
        },
        "mood_theme": {
            "training_source": "MTG-Jamendo split-0 train uploader tags (weak, multi-label)",
            "validation_source": "MTG-Jamendo split-0 validation uploader tags",
            "final_weak_label_evaluation": "MTG-Jamendo split-0 test uploader tags",
            "training_tag_support_tracks": dict(sorted(train_counts["mood/theme"].items(), key=lambda x: (-x[1], x[0]))),
            "v1_labels": compact_label_map["mood_theme"],
            "validation_support_for_v1_labels": selected_support(validation_counts["mood/theme"], compact_label_map["mood_theme"]),
            "test_support_for_v1_labels": selected_support(test_counts["mood/theme"], compact_label_map["mood_theme"]),
            "human_consensus_taxonomies": {k: v for k, v in human["counts"].items() if k.startswith("mood_")},
            "human_mapping_status": "keep seven clean binary taxonomies separate; no multi-class mood target is inferred",
            "fit_ready": bool(train_counts["mood/theme"]),
            "metric": "mAP, macro/micro F1, per-label support",
        },
        "instruments": {
            "training_source": "MTG-Jamendo split-0 train uploader instrument tags (weak, multi-label)",
            "validation_source": "MTG-Jamendo split-0 validation instrument tags",
            "final_weak_label_evaluation": "MTG-Jamendo split-0 test instrument tags",
            "training_tag_support_tracks": dict(sorted(train_counts["instrument"].items(), key=lambda x: (-x[1], x[0]))),
            "v1_labels": compact_label_map["instruments"],
            "validation_support_for_v1_labels": selected_support(validation_counts["instrument"], compact_label_map["instruments"]),
            "test_support_for_v1_labels": selected_support(test_counts["instrument"], compact_label_map["instruments"]),
            "human_consensus_taxonomies": {},
            "human_mapping_status": "no human-validated instrument taxonomy in the annotation release",
            "fit_ready": bool(train_counts["instrument"]),
            "metric": "mAP, macro/micro F1, per-label support",
        },
        "voice_instrumental": {
            "training_source": "MTG-Jamendo split-0 instrument uploader tag 'voice' (weak positive; untagged tracks are weak negatives)",
            "validation_source": "MTG-Jamendo split-0 validation instrument tag 'voice'",
            "final_weak_label_evaluation": "MTG-Jamendo split-0 test instrument tag 'voice'",
            "training_positive_tracks": train_counts["instrument"].get("voice", 0),
            "validation_positive_tracks": validation_counts["instrument"].get("voice", 0),
            "test_positive_tracks": test_counts["instrument"].get("voice", 0),
            "human_support_tracks": human["counts"].get("voice_instrumental", 0),
            "fit_ready": bool(train_counts["instrument"].get("voice")),
            "human_label_counts": human["label_counts"].get("voice_instrumental", {}),
            "human_consensus_status": "exact pinned TSV is hash-verified; clean consensus contains voice-only labels after special-answer exclusion, so it cannot supervise a binary vocal/instrumental task",
            "status": "the uploader voice tag is a weak fallback; defer a human binary head unless raw answers support a defensible negative class",
            "metric": "mAP and average precision against held-out weak tags; report human voice-only examples separately",
        },
        "danceability": {
            "training_source": "candidate: exact pinned MTG Music Classification Annotations TSV; dedicated artist split required",
            "evaluation_source": "held-out artist group from the same taxonomy-specific human annotations",
            "human_support_tracks": human["counts"].get("danceability", 0),
            "human_label_counts": human["label_counts"].get("danceability", {}),
            "fit_ready": False,
            "status": "candidate supervised binary task; use TSV-derived counts (the pinned README table differs) and freeze a separate artist-grouped split",
            "metric": "macro F1, balanced accuracy, confusion matrix",
        },
        "gender": {
            "training_source": "candidate: exact pinned MTG Music Classification Annotations TSV; dedicated artist split required",
            "evaluation_source": "held-out artist group from the same taxonomy-specific annotations",
            "human_support_tracks": human["counts"].get("gender", 0),
            "human_label_counts": human["label_counts"].get("gender", {}),
            "fit_ready": False,
            "status": "candidate taxonomy; exclude instrumental answers from the documented male/female target, then confirm class support and product relevance before adding the output",
            "metric": "macro F1, balanced accuracy, confusion matrix",
        },
        "tonal_atonal": {
            "training_source": "candidate: exact pinned MTG Music Classification Annotations TSV; dedicated artist split required",
            "evaluation_source": "held-out artist group from the same taxonomy-specific annotations",
            "human_support_tracks": human["counts"].get("tonal_atonal", 0),
            "human_label_counts": human["label_counts"].get("tonal_atonal", {}),
            "fit_ready": False,
            "status": "candidate taxonomy; confirm class support and product relevance before adding the output",
            "metric": "macro F1, balanced accuracy, confusion matrix",
        },
        "valence_arousal": {
            "training_source": "DEAM 2014 development set only; fixed track-level train/validation split required",
            "official_evaluation": "DEAM 2014 evaluation and 2015 evaluation sets remain untouched",
            "published_partition_tracks": {
                "development": deam["metadata_files"]["metadata_2013.csv"]["tracks"],
                "2014_evaluation": deam["metadata_files"]["metadata_2014.csv"]["tracks"],
                "2015_evaluation": deam["metadata_files"]["metadata_2015.csv"]["tracks"],
            },
            "audio_excerpt_alignment": "45-second excerpt offsets and dynamic-label timestamps must match; dynamic labels exclude first 15 seconds",
            "fit_ready": False,
            "status": "metadata/annotation files are audited; audio rights and duplicate audit remain open",
            "metric": "MAE, RMSE, correlation against constant-mean baseline, per dimension",
        },
    }
    source_lock = json.loads((REPORTS / "source_lock.json").read_text(encoding="utf-8"))
    deam_files = {name: len(paths) for name, paths in deam_archives.items()}
    report = {
        "audited_on": date.today().isoformat(),
        "mtg_jamendo": {
            "repository_commit": MTG_COMMIT,
            "metadata_license": "CC-BY-NC-SA-4.0",
            "usage": "non-commercial research and academic use only per upstream",
            "split0": {
                name: {
                    "tracks": splits[name]["rows"],
                    "artists": len(splits[name]["artist_ids"]),
                    "duplicate_track_rows": splits[name]["duplicate_track_rows"],
                }
                for name in partitions
            },
            "split0_pairwise_overlap": overlaps,
            "human_annotation_tracks": len(human_ids),
            "human_annotation_membership": human_split_membership,
            "human_taxonomy_support_tracks": human["counts"],
            "human_taxonomy_label_counts": human["label_counts"],
            "human_excluded_special_labels": human["excluded_special_labels"],
            "human_annotation_parse_issues": human["annotation_issues"],
            "human_annotation_source_status": "local bytes match the exact pinned upstream clean TSV SHA-256; published README support-count differences are recorded as upstream documentation drift; parsed TSV counts are canonical for manifests",
            "published_vs_parsed_human_support_differences": expected_differences,
            "compact_v1_label_map": compact_label_map,
            "alternate_split_overlap_evidence": split1_leaks,
            "training_only_tag_counts": {
                name: dict(sorted(counter.items(), key=lambda x: (-x[1], x[0])))
                for name, counter in train_counts.items()
            },
        },
        "deam": {
            "manual": "https://cvml.unige.ch/databases/DEAM/manual.pdf",
            "published_tracks": {"2014_development": 744, "2014_evaluation": 1000, "2015_evaluation": 58},
            "metadata_archive_file_counts": {"deam_metadata.zip": deam_files["deam_metadata.zip"], "deam_annotations.zip": deam_files["deam_annotations.zip"]},
            "metadata_partition_track_counts": deam["metadata_files"],
            "dynamic_annotation_alignment": deam["dynamic_annotations"],
            "audio_downloaded": False,
            "license_status": "not verified by this project; user handles review manually and it does not block implementation",
        },
        "tasks": task_matrix,
        "cross_dataset_audio_duplicate_audit": {
            "status": "not complete; no audio downloaded; keep MTG and DEAM task datasets separate until duplicate checks can run",
            "metadata_track_artist_matching": "DEAM metadata partitions and annotation IDs align; MTG-to-DEAM identity joins and exact/near-audio matching remain open",
            "policy": "candidate matches stay grouped; no same recording may cross any train/validation/test boundary",
        },
        "phase_gates": {
            "gate_0": "metadata, split, and frozen-backbone contracts pass; source terms are user-managed and do not block implementation",
            "gate_1": "candidate weak-tag map is derived from split-0 train only; pinned TSV-derived human counts are recorded; human task selection and artist-grouped manifests remain incomplete",
            "training_started": False,
            "blockers": [
                "Select human-label tasks using TSV-derived per-class counts and documented label semantics; gender includes instrumental answers and clean voice labels do not define binary negatives.",
                "Create and audit artist-grouped fit/validation/test manifests for selected human labels and same-track uploader tags.",
                "Audio is not downloaded and frozen-feature extraction is not implemented; these are required before training.",
                "Verify Jamendo API ID matches and download-allowed coverage for the selected artist split; otherwise use a streamed archive fallback and measure its transfer requirements.",
            ],
        },
        "source_hashes": {name: record["sha256"] for name, record in source_lock["files"].items()},
    }
    write_json(REPORTS / "data_audit.json", report)
    print(json.dumps({
        "report": str((REPORTS / "data_audit.json").relative_to(ROOT)),
        "split0_tracks": report["mtg_jamendo"]["split0"],
        "human_annotation_membership": human_split_membership,
        "human_support_mismatches": expected_differences,
        "split1_overlap": split1_leaks,
        "training_tag_classes": {k: len(v) for k, v in report["mtg_jamendo"]["training_only_tag_counts"].items()},
        "deam_archive_files": deam_files,
        "gate_0": "contracts pass; no training performed",
    }, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {"fetch", "audit", "self-check"}:
        raise SystemExit("usage: python scripts/audit_data.py {fetch|audit|self-check}")
    if sys.argv[1] == "fetch":
        fetch()
    elif sys.argv[1] == "audit":
        audit()
    else:
        self_check()
        print("metadata parser self-check passed")

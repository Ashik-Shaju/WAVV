from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import re
import time
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from audit_data import annotation_triplets, field, parse_tsv, tags_in, track_id


ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "data" / "metadata"
REPORTS = ROOT / "reports"
MANIFESTS = ROOT / "data" / "manifests"
SOURCE_LOCK = REPORTS / "source_lock.json"
API_URL = "https://api.jamendo.com/v3.0/tracks/"
MTG_COMMIT = "cafd8e20c265ed84f1e61f1c875327971f43a62f"
SEED = 42
SPLIT_RATIOS = {"fit": 0.70, "validation": 0.15, "test": 0.15}
SPLITS = tuple(SPLIT_RATIOS)
HUMAN_TASKS = (
    "mood_acoustic",
    "mood_aggressive",
    "mood_electronic",
    "mood_happy",
    "mood_party",
    "mood_relaxed",
    "mood_sad",
    "danceability",
    "gender",
    "tonal_atonal",
)
SPECIAL_LABELS = {"unmatched", "instrumental"}
WEAK_TAG_GROUPS = {"genre": 20, "mood/theme": 12, "instrument": 15}
SUPPORT_FLOORS = {
    "fit": {"tracks": 100, "artists": 20},
    "validation": {"tracks": 30, "artists": 10},
    "test": {"tracks": 30, "artists": 10},
}


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as output:
        output.write(json.dumps(value, indent=2, sort_keys=True) + "\n")
    temporary.replace(path)


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    temporary.replace(path)


def load_candidates() -> list[dict[str, object]]:
    _, annotation_rows = parse_tsv(METADATA / "mtg_human_clean.tsv")
    _, test_rows = parse_tsv(METADATA / "mtg_split0_test.tsv")
    _, raw_rows = parse_tsv(METADATA / "mtg_raw_metadata.tsv")

    test_by_id = {track_id(row): row for row in test_rows}
    jamendo_by_id: dict[str, str] = {}
    for row in raw_rows:
        tid = track_id(row)
        url_values = [value for value in row.values() if isinstance(value, str)]
        match = next((
            match
            for value in url_values
            if (match := re.search(r"/track/(\d+)(?:/|$)", value, flags=re.IGNORECASE))
        ), None)
        if tid and match:
            jamendo_by_id[tid] = match.group(1)

    candidates: list[dict[str, object]] = []
    for row in annotation_rows:
        tid = track_id(row)
        artist = field(row, "artist_id")
        if not tid or not artist or tid not in test_by_id or tid not in jamendo_by_id:
            raise ValueError(f"Cannot join annotated track to split-0 test and Jamendo metadata: {tid}")

        labels: dict[str, str] = {}
        for taxonomy, answers in annotation_triplets(row):
            if taxonomy not in HUMAN_TASKS or len(answers) != 3 or len(set(answers)) != 1:
                continue
            answer = answers[0]
            if answer in SPECIAL_LABELS:
                continue
            prior = labels.get(taxonomy)
            if prior is not None and prior != answer:
                raise ValueError(f"Conflicting unanimous answers for {tid}/{taxonomy}")
            labels[taxonomy] = answer

        split_row = test_by_id[tid]
        uploader: dict[str, set[str]] = {name: set() for name in WEAK_TAG_GROUPS}
        for raw_tag in tags_in(split_row):
            category, separator, label = raw_tag.partition("---")
            if separator and category in uploader:
                uploader[category].add(label)

        candidates.append({
            "dataset": "mtg_jamendo",
            "origin_split": "split-0-test",
            "track_id": tid,
            "artist_id": artist,
            "jamendo_track_id": jamendo_by_id[tid],
            "relative_audio_path": field(row, "path"),
            "duration_seconds": float(field(row, "duration")),
            "human_labels": labels,
            "uploader_tags": {name: sorted(values) for name, values in uploader.items()},
        })

    if len(candidates) != len({str(row["track_id"]) for row in candidates}):
        raise ValueError("Duplicate annotated track IDs")
    duplicate_api_ids: dict[str, set[str]] = defaultdict(set)
    for row in candidates:
        duplicate_api_ids[str(row["jamendo_track_id"])].add(str(row["artist_id"]))
    if any(len(artists) > 1 for artists in duplicate_api_ids.values()):
        raise ValueError("A Jamendo recording maps to multiple MTG artists; resolve before splitting")
    return sorted(candidates, key=lambda row: str(row["track_id"]))


def fetch_metadata(client_id: str, jamendo_ids: list[str]) -> dict[str, dict[str, object]]:
    query = urlencode({
        "client_id": client_id,
        "format": "json",
        "limit": min(200, len(jamendo_ids)),
        "id": " ".join(jamendo_ids),
    })
    request = Request(API_URL + "?" + query, headers={"User-Agent": "Wavv-Music-Understanding/0.1"})
    last_error = "unknown"
    for attempt in range(4):
        try:
            with urlopen(request, timeout=45) as response:
                payload = json.load(response)
            header = payload.get("headers", {})
            if header.get("status") != "success" or header.get("code") != 0:
                raise RuntimeError(f"Jamendo API rejected a metadata batch (code={header.get('code')})")
            results = payload.get("results", [])
            metadata = {str(row["id"]): row for row in results if "id" in row}
            unexpected = set(metadata) - set(jamendo_ids)
            if unexpected:
                raise RuntimeError("Jamendo returned IDs outside the requested batch")
            return metadata
        except HTTPError as error:
            last_error = f"HTTP {error.code}"
            if error.code < 500 and error.code != 429:
                raise RuntimeError(f"Jamendo metadata request failed: {last_error}") from None
        except (URLError, TimeoutError) as error:
            last_error = type(error).__name__
        except json.JSONDecodeError:
            last_error = "invalid JSON"
        if attempt < 3:
            time.sleep(0.5 * (2 ** attempt))
    raise RuntimeError(f"Jamendo metadata request failed after retries: {last_error}")


def inventory(client_id: str, batch_size: int) -> None:
    candidates = load_candidates()
    status_by_id: dict[str, dict[str, object]] = {}
    numeric_ids = [str(row["jamendo_track_id"]) for row in candidates]
    for start in range(0, len(numeric_ids), batch_size):
        batch = numeric_ids[start : start + batch_size]
        metadata = fetch_metadata(client_id, batch)
        for jamendo_id in batch:
            row = metadata.get(jamendo_id)
            if row is None:
                status_by_id[jamendo_id] = {"status": "not_returned", "audiodownload_allowed": None}
            elif isinstance(row.get("audiodownload_allowed"), bool):
                status_by_id[jamendo_id] = {
                    "status": "eligible" if row["audiodownload_allowed"] else "not_allowed",
                    "audiodownload_allowed": row["audiodownload_allowed"],
                    "api_duration_seconds": float(row.get("duration", 0) or 0),
                }
            else:
                status_by_id[jamendo_id] = {"status": "permission_unknown", "audiodownload_allowed": None}
        print(f"Checked {min(start + len(batch), len(numeric_ids))}/{len(numeric_ids)} annotated IDs")
        time.sleep(0.1)

    for row in candidates:
        row.update(status_by_id[str(row["jamendo_track_id"])])

    availability = Counter(str(row["status"]) for row in candidates)
    support: dict[str, dict[str, dict[str, int]]] = {}
    for taxonomy in HUMAN_TASKS:
        all_labels = sorted({str(row["human_labels"][taxonomy]) for row in candidates if taxonomy in row["human_labels"]})
        support[taxonomy] = {}
        for label in all_labels:
            matches = [row for row in candidates if row["human_labels"].get(taxonomy) == label]
            support[taxonomy][label] = {
                "annotated_tracks": len(matches),
                "annotated_artists": len({str(row["artist_id"]) for row in matches}),
                "eligible_tracks": sum(row["status"] == "eligible" for row in matches),
                "eligible_artists": len({str(row["artist_id"]) for row in matches if row["status"] == "eligible"}),
            }

    source_lock = json.loads(SOURCE_LOCK.read_text(encoding="utf-8"))
    report = {
        "audited_on": date.today().isoformat(),
        "inventory_source": "Jamendo v3 tracks metadata API; no audio downloaded",
        "mtg_repository_commit": MTG_COMMIT,
        "annotation_sha256": source_lock["files"]["mtg_human_clean.tsv"]["sha256"],
        "annotated_tracks": len(candidates),
        "jamendo_metadata_records": availability["eligible"] + availability["not_allowed"],
        "availability": dict(sorted(availability.items())),
        "human_label_support": support,
        "client_id_recorded": False,
        "audio_downloaded": False,
    }
    write_jsonl(MANIFESTS / "mtg_human_candidates.jsonl", candidates)
    write_json(REPORTS / "jamendo_availability.json", report)
    print(json.dumps({"availability": dict(availability), "report": str(REPORTS / "jamendo_availability.json")}, indent=2))


def artist_group_split(rows: list[dict[str, object]], seed: int) -> dict[str, str]:
    rng = random.Random(seed)
    by_artist: dict[str, list[dict[str, object]]] = defaultdict(list)
    labels_by_artist: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        artist = str(row["artist_id"])
        by_artist[artist].append(row)
        for taxonomy, label in row["human_labels"].items():
            if taxonomy in HUMAN_TASKS:
                labels_by_artist[artist].add(f"{taxonomy}={label}")

    artist_target = {split: len(by_artist) * ratio for split, ratio in SPLIT_RATIOS.items()}
    label_artist_totals = Counter(label for labels in labels_by_artist.values() for label in labels)
    label_target = {
        split: {label: count * SPLIT_RATIOS[split] for label, count in label_artist_totals.items()}
        for split in SPLITS
    }
    assigned_artists = Counter()
    assigned_labels: dict[str, Counter[str]] = {split: Counter() for split in SPLITS}
    assignment: dict[str, str] = {}
    remaining = set(by_artist)

    while remaining:
        remaining_label_counts = Counter(label for artist in remaining for label in labels_by_artist[artist])
        if remaining_label_counts:
            rare_count = min(remaining_label_counts.values())
            rare_labels = sorted(label for label, count in remaining_label_counts.items() if count == rare_count)
            rare_label = rng.choice(rare_labels)
            # Sort before seeded shuffling: set iteration otherwise changes across processes.
            artist_candidates = sorted(artist for artist in remaining if rare_label in labels_by_artist[artist])
            rng.shuffle(artist_candidates)
            artist_candidates.sort(
                key=lambda artist: sum(
                    1 / max(remaining_label_counts[label], 1)
                    for label in sorted(labels_by_artist[artist])
                ),
                reverse=True,
            )
            artist = artist_candidates[0]
        else:
            artist = rng.choice(sorted(remaining))

        order = list(SPLITS)
        rng.shuffle(order)

        def placement_score(split: str) -> float:
            artist_deficit = (artist_target[split] - assigned_artists[split]) / max(artist_target[split], 1)
            label_deficits = [
                (label_target[split][label] - assigned_labels[split][label]) / max(label_target[split][label], 1)
                for label in sorted(labels_by_artist[artist])
            ]
            return artist_deficit + (sum(label_deficits) / len(label_deficits) if label_deficits else 0.0)

        split = max(order, key=placement_score)
        assignment[artist] = split
        assigned_artists[split] += 1
        assigned_labels[split].update(labels_by_artist[artist])
        remaining.remove(artist)

    return assignment


def class_support(rows: list[dict[str, object]], taxonomy: str, label: str) -> dict[str, int]:
    matching = [row for row in rows if row["human_labels"].get(taxonomy) == label]
    return {"tracks": len(matching), "artists": len({str(row["artist_id"]) for row in matching})}


def select_split_rows(
    rows: list[dict[str, object]], split: str, capacity: int, seed: int
) -> tuple[list[dict[str, object]], dict[str, str]]:
    rng = random.Random(seed)
    pool = [row for row in rows if row["split"] == split]
    floor = SUPPORT_FLOORS[split]
    labels_by_taxonomy: dict[str, list[str]] = {
        taxonomy: sorted({str(row["human_labels"][taxonomy]) for row in pool if taxonomy in row["human_labels"]})
        for taxonomy in HUMAN_TASKS
    }
    active_tasks = {
        taxonomy
        for taxonomy, labels in labels_by_taxonomy.items()
        if len(labels) == 2
        and all(
            (support := class_support(pool, taxonomy, label))["tracks"] >= floor["tracks"]
            and support["artists"] >= floor["artists"]
            for label in labels
        )
    }
    selected: list[dict[str, object]] = []
    selected_ids: set[str] = set()
    class_tracks: dict[str, Counter[str]] = {taxonomy: Counter() for taxonomy in HUMAN_TASKS}
    class_artists: dict[str, dict[str, set[str]]] = {taxonomy: defaultdict(set) for taxonomy in HUMAN_TASKS}

    while len(selected) < capacity:
        best_score = 0.0
        best_rows: list[dict[str, object]] = []
        for row in pool:
            tid = str(row["track_id"])
            if tid in selected_ids:
                continue
            score = 0.0
            artist = str(row["artist_id"])
            for taxonomy in active_tasks:
                label = row["human_labels"].get(taxonomy)
                if label is None:
                    continue
                minimum = floor["tracks"]
                if class_tracks[taxonomy][label] < minimum:
                    score += 1.0
                if len(class_artists[taxonomy][label]) < floor["artists"] and artist not in class_artists[taxonomy][label]:
                    score += 2.0
            if score > best_score:
                best_score, best_rows = score, [row]
            elif score == best_score and score > 0:
                best_rows.append(row)
        if best_score <= 0 or not best_rows:
            break
        row = rng.choice(best_rows)
        selected.append(row)
        selected_ids.add(str(row["track_id"]))
        for taxonomy, label in row["human_labels"].items():
            class_tracks[taxonomy][label] += 1
            class_artists[taxonomy][label].add(str(row["artist_id"]))

    if len(selected) < capacity:
        remaining_by_artist: dict[str, list[dict[str, object]]] = defaultdict(list)
        for row in pool:
            if str(row["track_id"]) not in selected_ids:
                remaining_by_artist[str(row["artist_id"])].append(row)
        for artist_rows in remaining_by_artist.values():
            rng.shuffle(artist_rows)
        artists = list(remaining_by_artist)
        rng.shuffle(artists)
        while len(selected) < capacity:
            added = False
            for artist in artists:
                if remaining_by_artist[artist]:
                    row = remaining_by_artist[artist].pop()
                    selected.append(row)
                    selected_ids.add(str(row["track_id"]))
                    added = True
                    if len(selected) == capacity:
                        break
            if not added:
                break

    task_status: dict[str, str] = {}
    for taxonomy, labels in labels_by_taxonomy.items():
        if len(labels) != 2:
            task_status[taxonomy] = "defer: fewer than two observed classes in this partition"
            continue
        failed = []
        for label in labels:
            support = class_support(selected, taxonomy, label)
            if support["tracks"] < floor["tracks"] or support["artists"] < floor["artists"]:
                failed.append(f"{label} {support['tracks']} tracks/{support['artists']} artists")
        task_status[taxonomy] = "retain" if not failed else "defer: " + "; ".join(failed)
    return selected, task_status


def weak_label_map(selected_by_split: dict[str, list[dict[str, object]]]) -> dict[str, list[dict[str, object]]]:
    outputs: dict[str, list[dict[str, object]]] = {}
    for category, limit in WEAK_TAG_GROUPS.items():
        counts: dict[str, dict[str, Counter[str]]] = {
            split: {"tracks": Counter(), "artists": defaultdict(Counter)} for split in SPLITS
        }
        for split, rows in selected_by_split.items():
            for row in rows:
                for label in row["uploader_tags"].get(category, []):
                    counts[split]["tracks"][label] += 1
                    counts[split]["artists"][label][str(row["artist_id"])] += 1
        candidates = sorted(
            (label for label, n in counts["fit"]["tracks"].items() if n >= SUPPORT_FLOORS["fit"]["tracks"]),
            key=lambda label: (-counts["fit"]["tracks"][label], label),
        )
        eligible = []
        for label in candidates:
            support = {
                split: {
                    "tracks": counts[split]["tracks"][label],
                    "artists": len(counts[split]["artists"][label]),
                }
                for split in SPLITS
            }
            if all(
                support[split]["tracks"] >= SUPPORT_FLOORS[split]["tracks"]
                and support[split]["artists"] >= SUPPORT_FLOORS[split]["artists"]
                for split in SPLITS
            ):
                eligible.append({"label": label, "support": support})
            if len(eligible) == limit:
                break
        outputs[category] = eligible
    return outputs


def build_manifest(max_tracks: int, seed: int) -> None:
    candidate_path = MANIFESTS / "mtg_human_candidates.jsonl"
    if not candidate_path.is_file():
        raise SystemExit("Run `prepare_mtg.py inventory` first")
    candidates = [
        json.loads(line)
        for line in candidate_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    eligible = [row for row in candidates if row.get("status") == "eligible"]
    if not eligible:
        raise SystemExit("Jamendo inventory contains no download-eligible tracks")
    availability_path = REPORTS / "jamendo_availability.json"
    if not availability_path.is_file():
        raise SystemExit("Run `prepare_mtg.py inventory` first")
    availability_report = json.loads(availability_path.read_text(encoding="utf-8"))
    candidate_sha = hashlib.sha256(candidate_path.read_bytes()).hexdigest()
    availability_sha = hashlib.sha256(availability_path.read_bytes()).hexdigest()

    assignment = artist_group_split(eligible, seed)
    assigned = [{**row, "split": assignment[str(row["artist_id"])]} for row in eligible]
    total = min(max_tracks, len(assigned))
    fit_count = round(total * SPLIT_RATIOS["fit"])
    validation_count = round(total * SPLIT_RATIOS["validation"])
    capacities = {
        "fit": fit_count,
        "validation": validation_count,
        "test": total - fit_count - validation_count,
    }
    selected_by_split: dict[str, list[dict[str, object]]] = {}
    task_status: dict[str, dict[str, str]] = {}
    for index, split in enumerate(SPLITS):
        selected_by_split[split], task_status[split] = select_split_rows(
            assigned, split, min(capacities[split], sum(row["split"] == split for row in assigned)), seed + index
        )

    included_human_tasks = sorted(
        taxonomy
        for taxonomy in HUMAN_TASKS
        if all(task_status[split].get(taxonomy) == "retain" for split in SPLITS)
    )
    weak_map = weak_label_map(selected_by_split)

    for split in SPLITS:
        write_jsonl(MANIFESTS / f"mtg_human_{split}.jsonl", selected_by_split[split])

    split_manifest_sha256 = {
        split: hashlib.sha256((MANIFESTS / f"mtg_human_{split}.jsonl").read_bytes()).hexdigest()
        for split in SPLITS
    }
    manifest_digest = hashlib.sha256()
    for split in SPLITS:
        path = MANIFESTS / f"mtg_human_{split}.jsonl"
        manifest_digest.update(path.name.encode("utf-8"))
        manifest_digest.update(path.read_bytes())

    artist_sets = {split: {str(row["artist_id"]) for row in selected_by_split[split]} for split in SPLITS}
    track_sets = {split: {str(row["track_id"]) for row in selected_by_split[split]} for split in SPLITS}
    for index, first in enumerate(SPLITS):
        for second in SPLITS[index + 1 :]:
            if artist_sets[first] & artist_sets[second] or track_sets[first] & track_sets[second]:
                raise ValueError(f"Track/artist leakage between {first} and {second}")

    support_report = {}
    for taxonomy in HUMAN_TASKS:
        support_report[taxonomy] = {
            split: {
                label: class_support(selected_by_split[split], taxonomy, label)
                for label in sorted({str(row["human_labels"][taxonomy]) for row in selected_by_split[split] if taxonomy in row["human_labels"]})
            }
            for split in SPLITS
        }

    report = {
        "built_on": date.today().isoformat(),
        "mtg_repository_commit": MTG_COMMIT,
        "annotation_sha256": json.loads(SOURCE_LOCK.read_text(encoding="utf-8"))["files"]["mtg_human_clean.tsv"]["sha256"],
        "availability_audited_on": availability_report["audited_on"],
        "availability_report_sha256": availability_sha,
        "candidate_inventory_sha256": candidate_sha,
        "split_manifest_sha256": split_manifest_sha256,
        "manifest_sha256": manifest_digest.hexdigest(),
        "inventory_eligible_track_count": availability_report["availability"]["eligible"],
        "seed": seed,
        "artist_split_ratios": SPLIT_RATIOS,
        "max_tracks": max_tracks,
        "eligible_candidate_tracks": len(eligible),
        "selected_tracks": {split: len(selected_by_split[split]) for split in SPLITS},
        "selected_artist_groups": {split: len(artist_sets[split]) for split in SPLITS},
        "task_support_floor": SUPPORT_FLOORS,
        "human_candidate_status": task_status,
        "retained_human_tasks": included_human_tasks,
        "human_class_support": support_report,
        "weak_label_map": weak_map,
        "weak_tag_policy": "absence means not tagged by uploader, not confirmed acoustic absence",
        "track_overlap": False,
        "artist_overlap": False,
        "original_split0_test_claim_retired": True,
        "source_audio_downloaded": False,
    }
    write_json(REPORTS / "mtg_human_manifest.json", report)
    print(json.dumps({
        "selected_tracks": report["selected_tracks"],
        "selected_artist_groups": report["selected_artist_groups"],
        "retained_human_tasks": included_human_tasks,
        "weak_label_counts": {name: len(rows) for name, rows in weak_map.items()},
        "report": str(REPORTS / "mtg_human_manifest.json"),
    }, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit Jamendo availability and build a capped artist-grouped MTG annotation manifest; does not download audio or train.")
    subparsers = parser.add_subparsers(dest="command", required=True)
    inventory_parser = subparsers.add_parser("inventory", help="Query Jamendo metadata/download permission for annotated IDs; no audio is downloaded")
    inventory_parser.add_argument("--client-id-env", default="JAMENDO_CLIENT_ID")
    inventory_parser.add_argument("--batch-size", type=int, default=50, choices=range(1, 51))
    manifest_parser = subparsers.add_parser("manifest", help="Create a capped artist-disjoint fit/validation/test manifest")
    manifest_parser.add_argument("--max-tracks", type=int, default=800, help="Initial low-resource cap; 4,250 is only an optional post-validation expansion ceiling")
    manifest_parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()

    if args.command == "inventory":
        client_id = os.environ.get(args.client_id_env)
        if not client_id:
            raise SystemExit(f"Set {args.client_id_env} for this process; its value is never written to reports")
        inventory(client_id, args.batch_size)
    elif args.command == "manifest":
        if args.max_tracks < 1:
            raise SystemExit("--max-tracks must be positive")
        build_manifest(args.max_tracks, args.seed)


if __name__ == "__main__":
    main()

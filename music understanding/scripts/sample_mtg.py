from __future__ import annotations

import argparse
import json
import random
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFESTS = ROOT / "data" / "manifests"
REPORTS = ROOT / "reports"
SEED = 42
SIZES = {"train": 2500, "validation": 750, "test": 1000}


def read_jsonl(path: Path) -> list[dict[str, object]]:
    with path.open(encoding="utf-8") as source:
        return [json.loads(line) for line in source if line.strip()]


def write_jsonl(path: Path, rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def sample_rows(rows: list[dict[str, object]], size: int, seed: int) -> list[dict[str, object]]:
    if not 0 < size <= len(rows):
        raise ValueError(f"Sample size {size} must be between 1 and {len(rows)}")
    return sorted(random.Random(seed).sample(rows, size), key=lambda row: row["track_id"])


def self_check() -> None:
    rows = [{"track_id": str(number)} for number in range(20)]
    first = sample_rows(rows, 7, SEED)
    assert first == sample_rows(rows, 7, SEED)
    assert len(first) == len({row["track_id"] for row in first}) == 7
    try:
        sample_rows(rows, 21, SEED)
    except ValueError:
        pass
    else:
        raise AssertionError("oversized sample was not rejected")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a seeded MTG-Jamendo audio subset without touching test labels during selection.")
    parser.add_argument("--train", type=int, default=SIZES["train"])
    parser.add_argument("--validation", type=int, default=SIZES["validation"])
    parser.add_argument("--test", type=int, default=SIZES["test"])
    parser.add_argument("--seed", type=int, default=SEED)
    parser.add_argument("--self-check", action="store_true")
    args = parser.parse_args()
    if args.self_check:
        self_check()
        print("MTG sampler checks passed")
        return

    counts = {"train": args.train, "validation": args.validation, "test": args.test}
    report: dict[str, object] = {"seed": args.seed, "selection": "uniform track sample within the official split-0 partition; all metadata remains available for audits", "splits": {}}
    for split, size in counts.items():
        source = read_jsonl(MANIFESTS / f"mtg_{split}.jsonl")
        selected = sample_rows(source, size, args.seed)
        write_jsonl(MANIFESTS / f"mtg_v1_{split}.jsonl", selected)
        label_support: dict[str, dict[str, int]] = {}
        for category in ("genre", "mood_theme", "instruments"):
            support: Counter[str] = Counter()
            for row in selected:
                support.update(row["labels"][category])
            label_support[category] = dict(sorted(support.items()))
        report["splits"][split] = {
            "selected_tracks": len(selected),
            "audio_missing": sum(not row["audio_exists"] for row in selected),
            "label_support": label_support,
        }
    REPORTS.mkdir(parents=True, exist_ok=True)
    target = REPORTS / "mtg_subset.json"
    target.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"report": str(target), "seed": args.seed, "selected_tracks": counts, "total_tracks": sum(counts.values())}, indent=2))


if __name__ == "__main__":
    main()

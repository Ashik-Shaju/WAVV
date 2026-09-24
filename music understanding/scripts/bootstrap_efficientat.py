from __future__ import annotations

import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "vendor" / "EfficientAT"
URL = "https://github.com/fschmid56/EfficientAT.git"
COMMIT = "a425fdce92572e602a1d5634799bd9f1f2efa806"


def run(*args: str, cwd: Path | None = None) -> str:
    result = subprocess.run(args, cwd=cwd, check=True, text=True, capture_output=True)
    return result.stdout.strip()


def main() -> None:
    REPO.parent.mkdir(parents=True, exist_ok=True)
    if not REPO.exists():
        run("git", "clone", "--filter=blob:none", "--no-checkout", URL, str(REPO))
    run("git", "fetch", "origin", COMMIT, cwd=REPO)
    run("git", "checkout", "--detach", COMMIT, cwd=REPO)
    actual = run("git", "rev-parse", "HEAD", cwd=REPO)
    if actual != COMMIT:
        raise SystemExit(f"EfficientAT source mismatch: expected {COMMIT}, got {actual}")
    print(f"EfficientAT pinned at {actual}: {REPO}")


if __name__ == "__main__":
    main()

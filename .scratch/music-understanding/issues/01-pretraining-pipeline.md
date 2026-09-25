# Complete the pre-training feature pipeline

Type: task
Status: paused by user on 2026-09-25; 728/800 features are cached locally and audited; 72 remain.

## Scope

Freeze the 800-track artist-disjoint manifest, acquire OGG per track with immediate cleanup, and finish the pinned DyMN04-AS pooled-feature cache. Do not add or run training.

## Acceptance

- API inventory, annotation TSV, checkpoint, preprocessing, and manifest are hash-linked.
- The 800 selected IDs have zero track and artist overlap; eight human-label tasks meet the declared minimum class/artist support.
- The exact fit/validation/test manifests are tracked and their hashes appear in the manifest report. Test metrics are explicitly limited to this coverage-balanced cohort.
- SQLite contains exactly one finite, repeatable 384-value feature for every frozen manifest ID.
- No source audio remains in the project temp folder.
- Training remains gated; no task-quality claim is made until the validation baseline.

## Evidence

See `music understanding/reports/jamendo_availability.json`, `music understanding/reports/mtg_human_manifest.json`, and `music understanding/reports/feature_extraction.json` when full extraction completes.

## Resume checkpoint

Set `JAMENDO_CLIENT_ID` in that PowerShell process; the repository does not store credentials.

`music understanding/reports/feature_extraction_checkpoint.json` records the verified local cache state. Resume with `cd 'C:\Users\Bristo\Wavv\music understanding'` and `.\.venv\Scripts\python.exe scripts\extract_mtg_features.py`; the extractor skips cached track IDs. No training has started.

## Comments

- 2026-09-25: Resumed extraction and paused at the user's request after manifest entry 728 (`track_1164054`). The local cache contains 728 mapped finite 384-D rows, SQLite integrity is `ok`, 72 IDs remain, and the interrupted temporary OGG was deleted. Resume the command above; it skips cached IDs. Training has not started.

# Expand the pre-training cohort to 4,250 tracks

Type: task
Status: in progress on 2026-09-25

## Scope

Use the approved 4,250-track artist-disjoint manifest for the first model fit. Preserve the completed 800-track cohort and its local cache as the exact seed; process only the other 3,450 IDs. Finish all preparation and feature extraction, then stop before training.

The frozen task matrix contains eight human-consensus targets and nine weak uploader-tag targets (five genres and four instruments). Keep weak-label results separate; tag absence means “not tagged,” not confirmed acoustic absence. Gender, tonal/atonal, vocal/instrumental, and DEAM valence/arousal remain deferred.

## Acceptance

- The 4,250 selected rows split into 2,975 fit, 638 validation, and 637 test with zero track/artist overlap.
- Every previous 800 ID retains exactly the same split, artist ID, and Jamendo track ID.
- The existing 800-row SQLite cache is unchanged; a separate 4,250-row cache is seeded with those verified features.
- Only the 3,450 missing IDs are requested from the per-track OGG endpoint. Each downloaded OGG is deleted after processing.
- Every one of the 4,250 cached features is finite, repeatable, and 384-D; every cached mapping matches the frozen manifests; SQLite integrity is `ok`; no temporary OGG remains.
- The manifest task report confirms eight human tasks and nine weak tags meet their declared positive/negative support floors.
- No training entry point is added or run; Gate 2A is the stopping point.

## Evidence

- `music understanding/reports/mtg_human_manifest.json`
- `music understanding/reports/feature_extraction.json`
- `music understanding/reports/feature_extraction_checkpoint.json`
- Historical 800-row evidence uses the `_800` suffixes in `music understanding/data/manifests/` and `music understanding/reports/`.

## Resume

Set `JAMENDO_CLIENT_ID` in the PowerShell process only. Start a new expanded cache once with:

```powershell
cd 'C:\Users\Bristo\Wavv\music understanding'
.\.venv\Scripts\python.exe scripts\extract_mtg_features.py --reuse-cache data\features\mtg_dymn04as_800.sqlite
```

The initial seed-and-download command has already run once; do not repeat `--reuse-cache`. If the process stops before completion, check the checkpoint and ensure no extractor process is still running, then rerun `scripts\extract_mtg_features.py` without `--reuse-cache`. SQLite commits each completed row and the report checkpoint tracks progress. Training remains for a later session.

Current run: hidden Python process PID `11480`, started 2026-09-25 18:25:58 Asia/Colombo. At the 18:35:05 checkpoint it had 853/4,250 rows, including all 800 verified seed rows and 53 new rows. The early live rate suggests about 9.7 hours remain if it holds. Logs are in `%TEMP%\wavv-mu-4250-20260925-182558.out.log` and `%TEMP%\wavv-mu-4250-20260925-182558.err.log`.

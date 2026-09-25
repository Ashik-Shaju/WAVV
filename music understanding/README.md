# Wavv Music Understanding

Pre-training workspace for the separate DyMN04-AS music understanding model. This folder does not train a head or alter the Android app. It completes the model/data contract and audit steps first.

## Current gate

The original 800-track feature extraction is complete and preserved. The user-approved 4,250-track manifest is frozen; its new cache reuses all 800 old features with identical split/artist/Jamendo-ID mapping and is downloading only the remaining 3,450. Gate 2A remains pending until all 4,250 rows pass cache and audio-cleanup checks. Training has not started and no training entry point exists. The cohort supports eight human-consensus tasks plus five genre and four instrument uploader-tag targets; gender and tonal/atonal remain below the support floor. Weak-tag targets and metrics stay separate from human labels, and a missing uploader tag means “not tagged,” not confirmed acoustic absence. The test cohort is coverage-balanced and not prevalence-representative. Raw OGGs and feature caches stay local. See the [implementation plan](../docs/Wavv_Music_Understanding_Implementation_Plan.md), [manifest support report](reports/mtg_human_manifest.json), [extraction report](reports/feature_extraction.json), and [checkpoint](reports/feature_extraction_checkpoint.json).

## Dataset allocation

| Dataset/use | Fitting | Validation/model selection | Final test |
|---|---:|---:|---:|
| MTG-Jamendo split 0 official universe (reference counts only; not this baseline's training corpus) | 32,859 tracks (59.2%) | 11,101 (20.0%) | 11,565 (20.8%) |
| MTG Music Classification Annotations + same-track uploader tags | 2,975 total (560 cached + 2,415 new), from 302 artists | 638 total (120 cached + 518 new), from 65 separate artists | 637 total (120 cached + 517 new), from 65 separate artists; original split-0 test claim is retired; cohort is coverage-balanced, not prevalence-representative |
| Existing MTG official-partition weak-tag sample | 2,500 sampled tracks | 750 sampled tracks | 1,000 sampled tracks; reference artifact only, not used as this plan's fallback |
| DEAM valence/arousal (deferred) | 595 of 744 development tracks (80%) | 149 (20%), track-level seeded split | 1,000 (2014 evaluation) + 58 (2015 evaluation), untouched |

The human-annotation file contains 10,671 unique track IDs, all from split-0 test. The current Jamendo inventory recorded 4,915 allowed, 228 explicitly denied, and 5,528 not returned; a missing record is not treated as a denial. The frozen 4,250-ID manifest retains eight human attribute tasks and nine weak uploader-tag labels. Positive and negative support for each weak label passes the minimum track/artist floors in all three partitions. Gender and tonal/atonal still fail. The original 800 IDs remain unchanged and only 3,450 additional eligible tracks are downloaded. Map IDs through the pinned raw metadata URL, freeze artist groups once, and fetch OGG sequentially. If the per-track file endpoint rejects a frozen ID, stop and rebuild the manifest rather than silently substituting a track. Never combine numbered MTG splits.

The OGG, MP32, and FLAC comparison covers five fixed artist-distinct tracks from the clean human-annotation set. The [five-track report](reports/audio_format_pilot_5track.json) records per-track and aggregate timings, feature/mel similarity, and temporary-file deletion. OGG is selected for the frozen extraction: median size 0.857 MB/minute versus 1.478 for MP32, mean feature cosine to FLAC 0.9962 versus 0.9945, and mean frontend-mel RMSE 0.0826 versus 0.2276; mel RMSE was lower on four of five tracks. This is representation evidence only, not listening or downstream-label evidence. The 4,250-row float feature payload is about 6.23 MiB (1.17 MiB for the preserved 800-row seed). A temporary 10-track run projected 6.57 hours for the remaining 3,450 downloads; the first 53 live new rows took about nine minutes, suggesting about 9.7 hours remain if that early rate holds. Source OGGs are deleted after feature storage. The Jamendo file endpoint enforces `audiodownload_allowed` when each file is requested. [Jamendo track API](https://developer.jamendo.com/v3.0/tracks), [Jamendo file API](https://developer.jamendo.com/v3.0/tracks/file).

Pinned environment: Python 3.12.10, PyTorch 2.14.0 CPU, torchvision 0.29.0 CPU, and torchaudio 2.11.0 CPU. The official frontend produces `[1, 1, 128, 1000]` mel input for the verified ten-second fixture; the model returns 527 AudioSet logits and a `[1, 384]` pooled feature. Only the pooled feature is a Wavv baseline candidate.

## Reproduce the pre-training audit

Use Python 3.12 on the documented i5-1235U/16 GB workstation:

```powershell
cd 'C:\Users\Bristo\Wavv\music understanding'
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe scripts\bootstrap_efficientat.py
.\.venv\Scripts\python.exe scripts\verify_backbone.py
.\.venv\Scripts\python.exe scripts\audit_data.py fetch
.\.venv\Scripts\python.exe scripts\audit_data.py audit
# Set JAMENDO_CLIENT_ID in this PowerShell process; the value is not stored by the scripts.
.\.venv\Scripts\python.exe scripts\prepare_mtg.py inventory
.\.venv\Scripts\python.exe scripts\prepare_mtg.py manifest --max-tracks 4250
.\.venv\Scripts\python.exe scripts\extract_mtg_features.py --limit 9 --output 'C:\Users\Bristo\Wavv\music understanding\data\features\mtg_dymn04as_smoke.sqlite'
.\.venv\Scripts\python.exe scripts\extract_mtg_features.py --reuse-cache data\features\mtg_dymn04as_800.sqlite
```

`verify_backbone.py` runs the pinned official implementation on a generated ten-second fixture. It records the checkpoint digest, preprocessing settings, pooled-feature/logit shapes, finite-value checks, and repeatability in `reports/backbone_contract.json`. The fixture is synthetic and generated in memory.

`audit_data.py fetch` downloads only MTG-Jamendo split/annotation metadata and DEAM metadata/annotation archives into ignored `data/`. It never downloads audio. `audit` checks split disjointness and artist separation, proves the human annotations are confined to split-0 test, verifies DEAM dynamic annotation IDs and time steps, derives a 20-genre/12-mood/15-instrument candidate map from split-0 training counts only, and writes `reports/data_audit.json`.

`build_manifests.py` writes per-track JSONL split manifests to ignored `data/manifests/`. By default it expects audio under `data/audio/mtg_jamendo/` and `data/audio/deam/`; pass `--mtg-audio-root` and `--deam-audio-root` to point at local audio. It reports missing files but does not download audio or train. DEAM development tracks are split 595/149 using fixed seed 42 before any windows are made.

`sample_mtg.py` creates a 2,500/750/1,000 official-partition weak-tag sample; it does not include or group the human annotations and is not a fallback for the custom annotation-ID split. `prepare_mtg.py inventory` checks Jamendo metadata and writes the permission snapshot; `prepare_mtg.py manifest` freezes the artist-disjoint 4,250-track split by default. On a new expansion, pass `--reuse-cache data\features\mtg_dymn04as_800.sqlite` once to verify and seed the new cache; after that, rerun `extract_mtg_features.py` without that option to resume. The extractor stores only little-endian float32 pooled features in SQLite, checkpoints progress, and deletes each temporary OGG. No training should begin before `reports/feature_extraction.json` confirms all 4,250 manifest IDs are present, finite, repeatable, and the temporary-audio folder is empty.

`scripts/audio_format_pilot.py` compares Jamendo MP32, OGG, and FLAC on five allowed tracks and deletes each temporary audio file after processing. To reproduce it, set `JAMENDO_CLIENT_ID` for the process, then run `python scripts/audio_format_pilot.py`; it uses the installed `ffmpeg`/`ffprobe` commands and the pinned local backbone.

## Source notes

- Upstream metadata records MTG-Jamendo's non-commercial research/academic restriction, individual audio-license variation, and no separate DyMN04-AS checkpoint license statement. The user handles terms manually; this workspace does not verify them.
- No model weights, dataset audio, or downloaded source code are tracked in this folder.

## Next implementation stage

The initial 800-row cache is complete, but the approved 4,250-row expansion is still extracting and Gate 2A has not passed for the expanded cohort. The cache is resumable; its checkpoint shows current progress. Stop after all 4,250 features pass Gate 2A. Training and test-set evaluation are for a later session.

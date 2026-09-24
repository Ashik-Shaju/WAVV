# Wavv Music Understanding

Pre-training workspace for the separate DyMN04-AS music understanding model. This folder does not train a head or alter the Android app. It completes the model/data contract and audit steps first.

## Current gate

The 800-track pre-training feature extraction is paused at 193/800; training remains gated and no training entry point exists. The local resumable SQLite cache passes integrity, manifest-alignment, and finite 384-D checks, and no temporary audio remains. The CPU backbone contract, metadata audits, five-track OGG/MP32/FLAC comparison, frozen artist-disjoint manifest, and 9-track extraction smoke check pass. The full metadata-only Jamendo candidate snapshot and exact fit/validation/test ID manifests are tracked and hash-linked; raw audio and the feature cache remain local. MTG is the only first-baseline corpus; DEAM and cross-dataset duplicate checks are deferred. The exact pinned human TSV is hash-verified; its TSV-derived counts are operational, while README differences are recorded as upstream documentation drift. The selected test cohort is support-balanced using labels and is not a prevalence-representative sample of MTG-Jamendo. See the [implementation plan](../docs/Wavv_Music_Understanding_Implementation_Plan.md) and [resume checkpoint](reports/feature_extraction_checkpoint.json).

## Dataset allocation

| Dataset/use | Fitting | Validation/model selection | Final test |
|---|---:|---:|---:|
| MTG-Jamendo split 0 official universe (reference counts only; not this baseline's training corpus) | 32,859 tracks (59.2%) | 11,101 (20.0%) | 11,565 (20.8%) |
| MTG Music Classification Annotations + same-track uploader tags | 560 of the frozen 800 eligible IDs; eight human-label tasks pass minimum class/artist support | 120 IDs from separate artists; support audited | 120 IDs from separate artists; original split-0 test claim is retired; cohort is coverage-balanced, not prevalence-representative |
| Existing MTG official-partition weak-tag sample | 2,500 sampled tracks | 750 sampled tracks | 1,000 sampled tracks; reference artifact only, not used as this plan's fallback |
| DEAM valence/arousal (deferred) | 595 of 744 development tracks (80%) | 149 (20%), track-level seeded split | 1,000 (2014 evaluation) + 58 (2015 evaluation), untouched |

The human-annotation file contains 10,671 unique track IDs, all from split-0 test. The current Jamendo inventory recorded 4,915 allowed, 228 explicitly denied, and 5,528 not returned; a missing record is not treated as a denial. The hash-linked 800-ID manifest retains eight human attribute tasks. Gender and tonal/atonal fail rare-class support, and no uploader-tag label reaches the support floor at this cap. The 4,250 value is an optional expansion ceiling after validation, not the first download target. Map IDs through the pinned raw metadata URL, freeze artist groups once, and fetch OGG sequentially. If the per-track file endpoint rejects a frozen ID, stop and rebuild the manifest rather than silently substituting a track. Never combine numbered MTG splits.

The OGG, MP32, and FLAC comparison covers five fixed artist-distinct tracks from the clean human-annotation set. The [five-track report](reports/audio_format_pilot_5track.json) records per-track and aggregate timings, feature/mel similarity, and temporary-file deletion. OGG is selected for the frozen extraction: median size 0.857 MB/minute versus 1.478 for MP32, mean feature cosine to FLAC 0.9962 versus 0.9945, and mean frontend-mel RMSE 0.0826 versus 0.2276; mel RMSE was lower on four of five tracks. This is representation evidence only, not listening or downstream-label evidence. API coverage and the 800-track manifest are now recorded; downstream task metrics remain untested. The raw feature payload is about 1.17 MiB for 800 examples (6.2 MiB at the optional 4,250 ceiling). The Jamendo file endpoint supports per-track OGG and enforces `audiodownload_allowed` when each file is requested. [Jamendo track API](https://developer.jamendo.com/v3.0/tracks), [Jamendo file API](https://developer.jamendo.com/v3.0/tracks/file).

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
.\.venv\Scripts\python.exe scripts\prepare_mtg.py manifest
.\.venv\Scripts\python.exe scripts\extract_mtg_features.py --limit 9
.\.venv\Scripts\python.exe scripts\extract_mtg_features.py
```

`verify_backbone.py` runs the pinned official implementation on a generated ten-second fixture. It records the checkpoint digest, preprocessing settings, pooled-feature/logit shapes, finite-value checks, and repeatability in `reports/backbone_contract.json`. The fixture is synthetic and generated in memory.

`audit_data.py fetch` downloads only MTG-Jamendo split/annotation metadata and DEAM metadata/annotation archives into ignored `data/`. It never downloads audio. `audit` checks split disjointness and artist separation, proves the human annotations are confined to split-0 test, verifies DEAM dynamic annotation IDs and time steps, derives a 20-genre/12-mood/15-instrument candidate map from split-0 training counts only, and writes `reports/data_audit.json`.

`build_manifests.py` writes per-track JSONL split manifests to ignored `data/manifests/`. By default it expects audio under `data/audio/mtg_jamendo/` and `data/audio/deam/`; pass `--mtg-audio-root` and `--deam-audio-root` to point at local audio. It reports missing files but does not download audio or train. DEAM development tracks are split 595/149 using fixed seed 42 before any windows are made.

`sample_mtg.py` creates a 2,500/750/1,000 official-partition weak-tag sample; it does not include or group the human annotations and is not a fallback for the custom annotation-ID split. `prepare_mtg.py inventory` checks Jamendo metadata and writes the permission snapshot; `prepare_mtg.py manifest` freezes the artist-disjoint split and uses a default 800-track cap. `extract_mtg_features.py --limit 9` runs a deterministic smoke slice; omit `--limit` to resume/complete the full frozen manifest. It stores only little-endian float32 pooled features in a resumable SQLite cache and deletes each temporary OGG. No training should begin before `reports/feature_extraction.json` confirms all manifest IDs are present, finite, repeatable, and the temporary-audio folder is empty.

`scripts/audio_format_pilot.py` compares Jamendo MP32, OGG, and FLAC on five allowed tracks and deletes each temporary audio file after processing. To reproduce it, set `JAMENDO_CLIENT_ID` for the process, then run `python scripts/audio_format_pilot.py`; it uses the installed `ffmpeg`/`ffprobe` commands and the pinned local backbone.

## Source notes

- Upstream metadata records MTG-Jamendo's non-commercial research/academic restriction, individual audio-license variation, and no separate DyMN04-AS checkpoint license statement. The user handles terms manually; this workspace does not verify them.
- No model weights, dataset audio, or downloaded source code are tracked in this folder.

## Next implementation stage

Set `JAMENDO_CLIENT_ID` in the PowerShell process before running the resume command; the repository does not store credentials.

Resume the 800-track pooled-feature cache with `.\.venv\Scripts\python.exe scripts\extract_mtg_features.py`; it skips the 193 cached rows. The [implementation plan](../docs/Wavv_Music_Understanding_Implementation_Plan.md) and reports freeze the permission snapshot, split, and eight supported human-label tasks. After Gate 2A, implement the locked independent binary logistic-head recipe and use validation only to choose regularization and thresholds. Keep test artists locked. Stop before adding or running training; expand the data only after a trained validation baseline shows a specific need.

# Wavv Music Understanding

Pre-training workspace for the separate DyMN04-AS music understanding model. This folder does not train a head or alter the Android app. It completes the model/data contract and audit steps first.

## Current gate

The CPU backbone contract check, metadata audits, and five-track API/format pilot pass; training has not started and there is no training entry point. All temporary pilot audio files were removed after processing; no dataset audio is retained. Cross-dataset duplicate checks need audio and are deferred while MTG-Jamendo and DEAM remain separate by task. The exact pinned human TSV is hash-verified; its TSV-derived counts are operational, while README differences are recorded as upstream documentation drift. Human-label task semantics and artist-grouped manifests remain unfinished.

## Dataset allocation

| Dataset/use | Fitting | Validation/model selection | Final test |
|---|---:|---:|---:|
| MTG-Jamendo split 0 (official metadata universe) | 32,859 tracks (59.2%) | 11,101 (20.0%) | 11,565 (20.8%) |
| MTG Music Classification Annotations + same-track uploader tags | Start with a few-hundred-track timing pilot; target at most 4,250 unique annotated IDs after the label audit | Artist-held-out groups sized for per-taxonomy support | Artist-held-out groups; original split-0 test claim is retired for reused IDs |
| Existing MTG weak-tag sample | 2,500 sampled tracks | 750 sampled tracks | 1,000 sampled tracks; fallback only, not a validated quality sample |
| DEAM valence/arousal | 595 of 744 development tracks (80%) | 149 (20%), track-level seeded split | 1,000 (2014 evaluation) + 58 (2015 evaluation), untouched |

The human-annotation file contains 10,671 unique track IDs, all from split-0 test; taxonomy support counts overlap and the published genre annotations cover only about 1,500 recordings. Use counts parsed from the hash-verified TSV, record README drift, and review label semantics before selecting taxonomies. Build one custom artist-grouped split for selected IDs and reuse their uploader tags for weak tasks, including instruments. Prefer testing Jamendo's per-track download API for selected IDs, fetching one track at a time and deleting it after feature extraction. The API requires a client ID and some tracks may disallow download, so verify ID matches and available-label coverage before freezing the sample. Never combine numbered MTG splits: they are alternative overlapping partitions.

Before the first CPU dataset run, test Jamendo's individual-track API with a few hundred selected IDs. The MP32, OGG, and FLAC comparison covers five artist-distinct tracks from the clean human-annotation set. The [five-track report](reports/audio_format_pilot_5track.json) records per-track and aggregate timings, feature/mel similarity, and temporary-file deletion. OGG is the current choice: its median size is 0.857 MB/minute versus 1.478 MB/minute for MP32, with a higher mean feature cosine to FLAC (0.9962 vs. 0.9945) and lower mean frontend-mel RMSE (0.0826 vs. 0.2276). Decode plus frontend time averaged about 0.072 seconds for either lossy format. This five-track representation comparison is still not evidence of downstream label accuracy; check allowed-download coverage and task metrics before freezing the training input. The feature cache is about 6.2 MiB for 4,250 single-window examples. If per-track API access cannot cover the chosen IDs, stream MTG TAR archives one at a time and extract only selected files; this limits peak disk to an archive plus extracted targets, but transfers the full archive collection. MTG lists the full low-quality collection at 156 GB. [Jamendo track download API](https://developer.jamendo.com/v3.0/tracks/file), [MTG audio options](https://github.com/MTG/mtg-jamendo-dataset#downloading-the-data).

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
```

`verify_backbone.py` runs the pinned official implementation on a generated ten-second fixture. It records the checkpoint digest, preprocessing settings, pooled-feature/logit shapes, finite-value checks, and repeatability in `reports/backbone_contract.json`. The fixture is synthetic and generated in memory.

`audit_data.py fetch` downloads only MTG-Jamendo split/annotation metadata and DEAM metadata/annotation archives into ignored `data/`. It never downloads audio. `audit` checks split disjointness and artist separation, proves the human annotations are confined to split-0 test, verifies DEAM dynamic annotation IDs and time steps, derives a 20-genre/12-mood/15-instrument candidate map from split-0 training counts only, and writes `reports/data_audit.json`.

`build_manifests.py` writes per-track JSONL split manifests to ignored `data/manifests/`. By default it expects audio under `data/audio/mtg_jamendo/` and `data/audio/deam/`; pass `--mtg-audio-root` and `--deam-audio-root` to point at local audio. It reports missing files but does not download audio or train. DEAM development tracks are split 595/149 using fixed seed 42 before any windows are made.

`sample_mtg.py` creates the seeded 2,500/750/1,000 official-partition weak-tag fallback and records per-label support in `reports/mtg_subset.json`; it does not include or group the human annotations. The preferred run in D-043 still needs a selected human-task contract, artist-grouped manifest for the annotated IDs, uploader tags carried on the same tracks, a viable audio acquisition plan, and pooled-feature extraction. Do not train until those are complete.

`scripts/audio_format_pilot.py` compares Jamendo MP32, OGG, and FLAC on five allowed tracks and deletes each temporary audio file after processing. To reproduce it, set `JAMENDO_CLIENT_ID` for the process, then run `python scripts/audio_format_pilot.py`; it uses the installed `ffmpeg`/`ffprobe` commands and the pinned local backbone.

## Source notes

- Upstream metadata records MTG-Jamendo's non-commercial research/academic restriction, individual audio-license variation, and no separate DyMN04-AS checkpoint license statement. The user handles terms manually; this workspace does not verify them.
- No model weights, dataset audio, or downloaded source code are tracked in this folder.

## Next implementation stage

Build reproducible per-track manifests and frozen-feature extraction for the documented tasks. Keep each dataset's partitions isolated and evaluation data untouched. Do not add or run training until the user explicitly asks to cross the training boundary.

# Wavv Music Understanding Implementation Plan

**Status:** Pre-training contract and audits are implemented under [`music understanding/`](../music%20understanding/README.md); training has not started. Source terms are user-managed and do not block implementation.
**Scope:** Train and ship the first structured Music Understanding model. DCLAP remains separate.

This plan turns the locked model contract into a sequence of evidence gates. It does not certify the model in advance: each task advances only when the held-out evaluation and Android measurements support it.

## Authority and decisions

Use this plan with the [Music Understanding and Embedding specification](Wavv_Music_Understanding_and_Embedding_Validated.md), [technical contract](Wavv_technical.md#8-music-understanding-contract), and accepted [D-010/D-043 decisions](Wavv_decisions.md#c-music-understanding-and-embeddings). The model specification owns architecture and output contracts; this document owns implementation order and validation gates. DCLAP's separate plan is [Wavv_DCLAP_Implementation_Research_Plan.md](Wavv_DCLAP_Implementation_Research_Plan.md).

**Dataset decision:** use the exact hash-verified pinned MTG Music Classification Annotations TSV as the source of label values and support counts; its README table differs, and that upstream drift is recorded in the audit. Select only tasks whose parsed labels and semantics support the intended target. The 10,671 unique annotated IDs are all in split-0 test; create a custom artist-grouped split and retire the original test claim for every reused ID. Use uploader tags from those same audio IDs for broader weak-label tasks, including instruments. Never combine numbered MTG partitions. Human annotations do not cover instruments. See [D-043](Wavv_decisions.md#d-043--use-artist-held-out-human-labels-for-covered-tasks) and the [primary-source research note](research/Wavv-Music-Understanding-Implementation-Research.md).

**Device data budget:** begin with a few hundred unique annotated IDs as a timing/storage pilot, then use at most 4,250 as the initial audio target if per-taxonomy artist-held-out support permits. This is a storage starting cap, not a proven quality threshold; grow only fit groups if validation shows a task needs more data. Use uploader tags from those same tracks. Jamendo's per-track download API accepts one track ID, requires a client ID, and reports whether each track permits audio download. Fetch only selected tracks, hold one at a time, extract/cache its pooled feature, then delete the audio. The five-track, artist-distinct comparison of MP32, OGG, and FLAC favors OGG for this frozen-backbone input: it had higher mean feature cosine and lower mel error against FLAC while using about 42% less data per minute than MP32. The result is representation-level only, so verify allowed-download coverage and task metrics on validation before freezing the training format. See the [five-track report](../music%20understanding/reports/audio_format_pilot_5track.json) and [research results](research/Wavv-Music-Understanding-Implementation-Research.md#five-track-follow-up-2026-09-24). If the API cannot cover the needed IDs, MTG's pinned downloader handles complete TAR archives without a track-ID filter; selective archive streaming can reduce peak disk, but not total transfer. MTG lists the full `audio-low` collection at 156 GB. Keep full-quality stereo undownloaded. For DEAM, use 595 fit / 149 validation tracks and keep the 1,000 + 58 evaluation tracks untouched; include its audio only when running valence/arousal.

## Verified pre-training status (2026-09-24)

- EfficientAT is pinned to commit `a425fdce92572e602a1d5634799bd9f1f2efa806`. The official DyMN04-AS checkpoint is 8,086,973 bytes with SHA-256 `49b0e63577021994257b3de2ac5f1686d71554b93c8149f9d2568ad5760b904e`. Checkpoint and dataset terms are user-managed manually; this project has not verified or cleared them.
- The official loader and frontend passed a deterministic CPU smoke check in the project Python 3.12 environment using PyTorch 2.14.0, torchvision 0.29.0, and torchaudio 2.11.0. A 10-second mono 32 kHz window produces `[1, 1, 128, 1000]` mel input, `[1, 527]` AudioSet logits, and a finite, repeatable `[1, 384]` pooled feature. Only the pooled feature is a candidate Wavv input; the 527 logits are not Wavv labels. Details are in [`backbone_contract.json`](../music%20understanding/reports/backbone_contract.json).
- The pinned MTG-Jamendo metadata split has 32,859 train, 11,101 validation, and 11,565 test tracks, with zero track or artist overlap among split-0 partitions. All 10,671 human-annotation track IDs occur in split-0 test and none occur in train or validation. Split 1 overlaps split-0 test and is not an independent holdout.
- DEAM's three metadata partitions contain 744, 1,000, and 58 unique tracks. Both dynamic valence and arousal files cover all 1,802 IDs, begin at 15,000 ms, and advance by 500 ms. Use 595/149 development tracks for fit/validation and keep both evaluation sets untouched. Audio terms are user-managed; cross-dataset audio duplicate checks await audio. Keep datasets separate by task meanwhile.
- The local human-annotation TSV matches the exact pinned upstream file by SHA-256. Its parsed support differs from the pinned README table, which is recorded as upstream documentation drift in [`data_audit.json`](../music%20understanding/reports/data_audit.json); use the exact TSV's parsed labels and counts, while separately reviewing special-label semantics (notably `gender` and `voice_instrumental`).
- The one-track format smoke test and five-track MP32/OGG/FLAC comparison completed. All temporary pilot audio files were deleted, and no dataset audio is retained. Results are in [`audio_format_pilot.json`](../music%20understanding/reports/audio_format_pilot.json) and [`audio_format_pilot_5track.json`](../music%20understanding/reports/audio_format_pilot_5track.json). No training entry point has been added and no training has run.

## V1 boundary

- Use the pinned DyMN04-AS checkpoint as a frozen shared audio backbone. Verify its actual input and intermediate-feature contract before writing a loader; do not use AudioSet's 527 logits as Wavv labels.
- Train small task heads only for targets with an explicit label source and artist-held-out evaluation. Source terms are user-managed. Use masked losses for missing labels.
- For the first desktop baseline, extract one deterministic 10-second window per sampled track. Add multi-window full-song aggregation only after the small baseline passes validation; inference windows must match the verified frontend contract.
- Keep DCLAP embeddings, text retrieval, and Music Understanding profiles separate. Do not fuse them in this implementation.
- Run analysis during background library indexing, cache by source identity and model version, and keep playback/search usable when analysis is pending or fails.
- Do not add OpenMIC, FMA, full-backbone fine-tuning, or a new inference runtime unless a measured gate below requires it.

## Phase 0 — Freeze model and data contracts

Before training:

1. Pin the DyMN/EfficientAT code revision and exact DyMN04-AS checkpoint. The checkpoint hash, size, and loader source are recorded in [`source_manifest.json`](../music%20understanding/source_manifest.json); the user handles terms manually.
2. The pinned `AugmentMelSTFT` uses mono 32 kHz audio, pre-emphasis `[-0.97, 1]`, a 1,024-point FFT, 800-sample window, 320-sample hop, 128 Kaldi mel bands, power mel, `ln(mel + 1e-5)`, and `(mel + 4.5) / 5` normalization. The pinned implementation returns 1,000 frames for a 10-second input because pre-emphasis removes one sample. Do not substitute generic librosa mel settings or DCLAP's 48 kHz preprocessing.
3. The metadata-only source lock pins MTG-Jamendo commit `cafd8e20c265ed84f1e61f1c875327971f43a62f` and records DEAM archive hashes. Split-0 membership and artist separation are verified; numbered MTG splits are alternative partitions. The user handles dataset/checkpoint terms manually; implementation does not claim legal verification.
4. Exact and near-duplicate audio checks across MTG-Jamendo and DEAM are not complete because no corpus audio is retained (the one-track format pilot was deleted). The split metadata has IDs and paths; its separate raw metadata has title/artist strings. Keep the task datasets separate until audio-level duplicate checks can run.
5. The Android acceptance device/runtime is not confirmed. The Vivo I2018/API 33 CPU report in the DCLAP workspace is provisional, not a selected target. Keep the desktop path usable on the documented i5-1235U/16 GB workstation.

**Gate 0:** the backbone, dataset source, and official split contracts are reproducible. Checkpoint/audio terms are user-managed and do not block implementation. The human TSV source is hash-verified; its parsed counts are canonical despite README drift. Confirm the Android target before device evaluation; it does not block the desktop pre-training pipeline.

## Phase 1 — Audit labels and freeze the task matrix

Create one small manifest/table with, for each proposed task, the training source, evaluation source, label type, positive/negative or class counts, missing-label policy, split, and metric. Derive counts from training data only; freeze label mappings and thresholds before inspecting held-out scores.

- **Human-validated properties:** use selected consensus taxonomies from the hash-verified TSV for fitting. Create a custom artist-grouped split for the selected audio IDs and retire split-0 test claims for reused tracks. Support differs sharply by taxonomy; parsed genre consensus support is only 180–621 tracks, so defer any task whose class/artist support cannot pass the split gate. There is no human-validated instrument taxonomy.
- **Genre, mood/theme, instruments:** use uploader tags from the same selected recordings for broader weak-label genre/mood/theme and instrument targets. Freeze the compact label map from fit artists only. Keep metrics from uploader tags separate from human-consensus tasks.
- **Danceability, gender, tonal/atonal, voice/instrumental:** candidate human tasks after reviewing parsed support and label semantics. The current clean TSV retains voice without an instrumental negative class; defer a binary head unless the raw answers establish a defensible mapping. `gender` includes `instrumental` answers in the clean file, so inspect and exclude or explicitly justify that class before task selection.
- **Valence/arousal:** DEAM metadata/annotation track IDs align across all 1,802 tracks, with dynamic annotations starting at 15 seconds every 500 ms. Match exact audio excerpts and offsets, carry annotation uncertainty, and keep official evaluation partitions untouched. The user handles source terms manually. Do not merge DEAM and MTG examples into one training split.
- Keep the DEAM/MTG dataset tasks separate and do not add another dataset before a specific coverage gap is measured.

**Gate 1:** freeze selected human taxonomies and weak-tag mappings from parsed training-only counts, and produce artist-grouped manifests with adequate per-class support. No head proceeds without an auditable fit source and separate artist-held-out validation and test groups.

## Phase 2 — Frozen-feature baseline

Implement the desktop training/reference path in the separate `music understanding/` workspace alongside `dclap/`; keep raw audio and generated artifacts out of ordinary source history, following the repository's existing manifest and evaluation-report pattern.

1. For MTG, select tasks from the hash-verified TSV, then build a custom artist-grouped split from selected annotation IDs. Apply the same split to those recordings' uploader tags so all MTG heads share audio and have no artist leakage. Do not claim reused recordings remain split-0 test examples or borrow another numbered split. For DEAM, use the fixed 595/149 track-level development split and keep official evaluation sets untouched. Group cross-dataset duplicates when audio becomes available.
2. Start with the checkpoint's globally pooled final feature and the same simple linear heads. Probe a documented intermediate feature only if that baseline is inadequate on validation. Select using validation metrics, not test results, and confirm the chosen feature extraction can be reproduced by the full inference path.
3. Keep DyMN04-AS frozen. Extract features in CPU batches of 1–2, cache them, then fit linear heads on the small feature matrices; do not backpropagate through the audio backbone or cache full waveforms/mel arrays. Use multi-label objectives for tag tasks, explicit masks for missing labels, and a regression objective for song-level valence/arousal. Add hidden layers only if linear heads fail validation.
4. Start audio extraction with a few hundred artist-grouped tracks to measure CPU time and storage. Then fit on increasing subsets of train artists while validation/test artists stay fixed. Stop at the smallest subset that passes per-task support and validation gates; otherwise expand only that task's fit data or defer it. The existing 2,500/750/1,000 weak-tag sample remains a fallback, not a validated minimum or quality claim.
5. Record the seed, code revision, configs, dataset manifests, split IDs, label maps, checkpoint, task metrics, training environment, and failure analysis for every run.

**Gate 2:** the selected representation and each retained head beat a prevalence/majority baseline on validation. Tune thresholds and window policy only on validation, then freeze the run. Do not inspect held-out test scores until Phase 3's one final evaluation.

## Phase 3 — Held-out evaluation and song aggregation

Report metrics separately by task and dataset; do not collapse them into a single score.

- Multi-label tags: mAP plus macro/micro F1; report per-class support and performance.
- Binary or exclusive tasks: macro F1, balanced accuracy, and confusion matrix.
- Valence/arousal: MAE, RMSE, and correlation against the constant-mean baseline; report each dimension separately with DEAM's supplied annotation uncertainty.
- Give uncertainty using track-level bootstrap intervals. If artist IDs exist, include an artist-grouped diagnostic interval or split.
- Evaluate human-label heads only on their held-out artist groups. Report these scores separately from weak uploader-tag results and never use test scores to choose labels, layers, checkpoints, thresholds, or pooling.
- Compare mean-probability/mean-regression pooling with one robust alternative on validation. Freeze one simple pooling rule per task, then evaluate stability across song windows on the held-out tracks.

**Gate 3:** a task is eligible for the app only if it beats its simple held-out baseline with uncertainty reported and its output is useful under the target label definition. Otherwise omit that task from the shipped profile; do not tune against the held-out scores or hide a weak task behind aggregate scores.

## Phase 4 — Export and Android indexing

Only after Gate 3:

1. Export the selected frozen backbone plus heads to the smallest supported runtime format, initially ONNX if the model converts cleanly. Keep the verified audio preprocessing contract identical to desktop.
2. Compare exported and reference outputs on a small golden fixture set covering short, normal, long, mono, and multi-channel inputs. Record numerical tolerances and task-metric drift before device work.
3. Add Music Understanding as an independent cached analysis artifact in the existing background indexing flow. Store model/version and source identity with the profile; do not put attributes in DCLAP's vector artifact. Cancellation or failure must not block playback or erase a previous valid profile.
4. On the selected physical target, measure cold/warm load time, per-window latency, whole-song indexing time, peak memory, model size, cancellation/resume, and repeated-run consistency. Compare CPU first; use acceleration only after a measured need.

**Gate 4:** ship only if exported outputs match the desktop reference within the recorded tolerance, retained tasks keep their quality gate, and the target device completes full-song background analysis within the app's agreed indexing and memory budget. Set that budget from the measured target and library size before integration is declared complete.

## Required artifacts

Keep the first deliverables limited to:

- dataset/split manifest and task label map;
- reproducible frozen-feature training and evaluation entry points;
- experiment config, metrics, split IDs, summary, and best checkpoint;
- inference metadata with checkpoint hash, preprocessing, feature layer, label map/version, pooling, runtime format, and output schema;
- golden desktop fixtures and one target-device report.

Do not bundle training datasets or temporary feature caches in the Android app. Do not commit a model artifact until its source license and distribution terms are recorded.

## Next implementation task

Finish the pre-training pipeline: select tasks using exact TSV-derived counts and label semantics, create an auditable artist-grouped manifest for selected annotation IDs, carry uploader tags on those same tracks, validate audio paths, and implement frozen-feature extraction. Keep validation/test artists locked. The implementation intentionally stops before training; do not add or run a training entry point.

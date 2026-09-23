# Wavv Music Understanding Implementation Plan

**Status:** Pre-implementation gates; no Music Understanding model code exists in the app yet.  
**Scope:** Train and ship the first structured Music Understanding model. DCLAP remains separate.

This plan turns the locked model contract into a sequence of evidence gates. It does not certify the model in advance: each task advances only when the held-out evaluation and Android measurements support it.

## Authority and decisions

Use this plan with the [Music Understanding and Embedding specification](Wavv_Music_Understanding_and_Embedding_Validated.md), [technical contract](Wavv_technical.md#8-music-understanding-contract), and accepted [D-010/D-011 decisions](Wavv_decisions.md#c-music-understanding-and-embeddings). The model specification owns architecture and output contracts; this document owns implementation order and validation gates. DCLAP's separate plan is [Wavv_DCLAP_Implementation_Research_Plan.md](Wavv_DCLAP_Implementation_Research_Plan.md).

**Dataset correction:** the human-validated MTG-Jamendo annotations map to split-0 test tracks. The numbered official splits are alternative randomized partitions, not independent folds: a test track in split 0 can appear in another split's training list. Use only split-0 train/validation/test for this baseline, and never combine numbered splits. The human annotations are evaluation-only and do not provide the full Wavv ontology or an instrument taxonomy. Train categorical heads from curated split-0 MTG-Jamendo tags, treating them as weak labels. DEAM remains the source for valence/arousal. See the [primary-source research note](research/Wavv-Music-Understanding-Implementation-Research.md).

## V1 boundary

- Use the pinned DyMN04-AS checkpoint as a frozen shared audio backbone. Verify its actual input and intermediate-feature contract before writing a loader; do not use AudioSet's 527 logits as Wavv labels.
- Train small task heads only for targets with an explicit, licensed training source and a disjoint evaluation source. Use masked losses for missing labels.
- Analyze full songs as checkpoint-compatible windows and aggregate window predictions into a song profile.
- Keep DCLAP embeddings, text retrieval, and Music Understanding profiles separate. Do not fuse them in this implementation.
- Run analysis during background library indexing, cache by source identity and model version, and keep playback/search usable when analysis is pending or fails.
- Do not add OpenMIC, FMA, full-backbone fine-tuning, or a new inference runtime unless a measured gate below requires it.

## Phase 0 — Freeze model and data contracts

Before training:

1. Pin the DyMN/EfficientAT code revision and exact DyMN04-AS checkpoint. Record source URL, revision, checkpoint hash and size, license, required dependencies, and model-card/paper reference.
2. Run the [official DyMN loader](https://raw.githubusercontent.com/fschmid56/EfficientAT/main/inference.py) on a short fixture. Its current defaults are mono 32 kHz, 128-bin log-mel features, 25 ms windows, and 10 ms hops; verify them against the pinned checkpoint and record normalization, window shape, and output tensor shapes. Do not reuse DCLAP's 48 kHz preprocessing or a feature layer from a different EfficientAT model.
3. Build a dataset manifest before downloading audio: dataset release, access date, license/usage terms, track IDs, official split, artist/group ID when available, label files, and audio availability. Confirm MTG-Jamendo split-0 train/validation/test membership and DEAM development/evaluation membership. Treat other numbered MTG-Jamendo splits as alternate partitions; do not mix them into this run. Check that each license permits the intended research/training use; confirm distribution rights separately before bundling any artifact.
4. Check exact and near-duplicate track overlap across MTG-Jamendo and DEAM. Any shared recording must stay in one split across all tasks.
5. Record the physical Android target, OS, runtime, and memory class used for model acceptance. Reuse the DCLAP device only if it is still the app's target. Keep the desktop pipeline usable on the documented i5-1235U/16 GB workstation; an optional GPU may speed training but cannot be a runtime dependency.

**Gate 0:** training may start only when the checkpoint loads reproducibly, its license and all dataset terms are documented, input/output contracts are captured, and the held-out track lists are frozen. No full dataset audio download before this gate.

## Phase 1 — Audit labels and freeze the task matrix

Create one small manifest/table with, for each proposed task, the training source, evaluation source, label type, positive/negative or class counts, missing-label policy, split, and metric. Derive counts from training data only; freeze label mappings and thresholds before inspecting held-out scores.

- **Genre, mood/theme, instruments:** curate a compact mapping from official MTG-Jamendo training tags. Treat uploader tags as weak, potentially multi-label targets. Use the approximate class-count goals in the specification only where training coverage supports them. The human-validated split-0 release is an evaluation set, not a source for head fitting.
- **Human-validated properties:** evaluate only the tasks actually defined in that release. It contains four separate genre taxonomies (411–612 unanimous tracks each), six separate binary mood attributes (5,678–7,686 each), danceability (4,476), and voice/instrumental (2,070). Keep genre taxonomies separate; mood/danceability annotations are binary tasks, not a general multi-class mood ontology. There is no human-validated instrument taxonomy. Do not infer missing labels.
- **Vocal/instrumental and danceability training:** add a head only when the audit finds a documented training label with a defensible mapping. Never train from the held-out human labels. If no suitable training target exists, defer that head and record the gap.
- **Valence/arousal:** use DEAM. Match the exact audio excerpt/track and offset to its annotations, then average aligned ratings to form Wavv's song-level target. Respect the annotation timestamp range (dynamic labels exclude the first 15 seconds) and report the supplied annotation uncertainty. Keep the official evaluation partition untouched; do not attach excerpt labels to an unaligned full recording.
- Use only label sources available under their published terms. Avoid buying/downloading missing audio or adding another dataset before a specific task coverage gap is measured.

**Gate 1:** no head proceeds without a non-empty, auditable training label source and a separate evaluation set. Unsupported tasks are removed from the first model version instead of being filled with proxies or invented labels.

## Phase 2 — Frozen-feature baseline

Implement the desktop training/reference path in a small `music_understanding/` workspace alongside `dclap/`; keep raw audio and generated artifacts out of ordinary source history, following the repository's existing manifest and evaluation-report pattern.

1. Split at track level before creating any windows. Use only MTG-Jamendo split 0: its train partition for fitting, validation partition for model selection, and test partition for final evaluation. Never combine numbered partitions because they overlap. Fit/tune on DEAM's development partition, making a fixed track-level train/validation split within it; keep the official DEAM evaluation partition untouched. Check artist overlap; if an official split is not artist-disjoint, report it and add one artist-disjoint diagnostic split when metadata permits. Group duplicates across datasets so the same recording cannot cross the boundary.
2. Start with the checkpoint's globally pooled final feature and the same simple linear heads. Probe a documented intermediate feature only if that baseline is inadequate on validation. Select using validation metrics, not test results, and confirm the chosen feature extraction can be reproduced by the full inference path.
3. Train a frozen-backbone baseline. Use multi-label objectives for tag tasks, explicit masks for missing labels, and a regression objective for song-level valence/arousal. Start with linear heads; add hidden layers only if the linear baseline fails the validation gate.
4. Use the specification's ~1,000-track subset as a pipeline smoke test only. It cannot support the final model-quality claim.
5. Record the seed, code revision, configs, dataset manifests, split IDs, label maps, checkpoint, task metrics, training environment, and failure analysis for every run.

**Gate 2:** the selected representation and each retained head beat a prevalence/majority baseline on validation. Tune thresholds only on validation. Freeze the complete design before running the untouched MTG split-0 test tags and human-validated labels, plus the DEAM evaluation partition, once.

## Phase 3 — Held-out evaluation and song aggregation

Report metrics separately by task and dataset; do not collapse them into a single score.

- Multi-label tags: mAP plus macro/micro F1; report per-class support and performance.
- Binary or exclusive tasks: macro F1, balanced accuracy, and confusion matrix.
- Valence/arousal: MAE, RMSE, and correlation against the constant-mean baseline; report each dimension separately with DEAM's supplied annotation uncertainty.
- Give uncertainty using track-level bootstrap intervals. If artist IDs exist, include an artist-grouped diagnostic interval or split.
- Evaluate on the human-validated MTG release only for its actual label definitions. Report it separately from noisy-tag MTG results; never use it to choose labels, layers, checkpoints, thresholds, or pooling.
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

## First implementation task

Do Phase 0 only: pin and inspect the DyMN04-AS artifact, audit dataset licenses/splits/label coverage, and produce the frozen input/feature/task manifests. Start training after Gate 0 and Gate 1 pass.

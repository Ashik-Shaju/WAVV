# Wavv Music Understanding Implementation Plan

**Status:** Expansion to the user-approved 4,250-track cohort is in progress. The completed 800-track cache is preserved as the seed; 3,450 additional tracks remain to fetch and process. Gate 2A is not complete for the expanded cohort. Training has not started. Source terms are user-managed.
**Scope:** Train and ship the first structured Music Understanding model. DCLAP remains separate.

This plan turns the locked model contract into a sequence of evidence gates. It does not certify the model in advance: each task advances only when the held-out evaluation and Android measurements support it.

## Authority and decisions

Use this plan with the [Music Understanding and Embedding specification](Wavv_Music_Understanding_and_Embedding_Validated.md), [technical contract](Wavv_technical.md#8-music-understanding-contract), and accepted [D-010/D-043/D-045/D-046 decisions](Wavv_decisions.md#c-music-understanding-and-embeddings). The model specification owns architecture and output contracts; this document owns implementation order and validation gates. DCLAP's separate plan is [Wavv_DCLAP_Implementation_Research_Plan.md](Wavv_DCLAP_Implementation_Research_Plan.md).

**Dataset decision:** use the exact hash-verified pinned MTG Music Classification Annotations TSV for human labels and the same selected tracks' uploader tags for weak labels. Its README support table differs, so the TSV is the operational source. The annotation IDs originate in split-0 test; every reused ID is assigned to a custom artist-group split and loses its original split-0 test claim. The 2026-09-24 Jamendo inventory returned 4,915 allowed IDs, 228 denied IDs, and 5,528 IDs without a returned record; a missing record is not a denial. The 4,250 manifest selects 2,975/638/637 tracks across 302/65/65 artists, preserves every old 800 mapping, retains eight human-label tasks, and adds five genre plus four instrument uploader-tag targets. Positive and negative support for every weak target meets the per-split track/artist floors. Never combine numbered MTG partitions. See [D-043/D-045/D-046](Wavv_decisions.md#c-music-understanding-and-embeddings) and the [primary-source research note](research/Wavv-Music-Understanding-Implementation-Research.md).

**Device data budget:** use the user-approved 4,250-track manifest (2,975 fit, 638 validation, 637 test; 302/65/65 artists). The selector builds a coverage-balanced cohort using labels, including in the test partition; test metrics describe this cohort and do not estimate natural MTG-Jamendo prevalence. The per-class 100-track/20-artist fit and 30-track/10-artist validation/test floors are reporting minimums, not accuracy guarantees. Eight human targets pass; gender and tonal/atonal do not. Five genre and four instrument uploader-tag targets also pass positive and negative floors, but remain weak supervision. On the documented i5-1235U/16 GiB workstation, the current plan reuses 800 cached tracks and fetches only 3,450 more. The ten-track sequential timing benchmark projects about 6.57 hours for those additional downloads and feature extractions; it is a network-sensitive projection, not a device guarantee. OGG is downloaded one at a time and deleted after feature storage. The expanded 384-D float feature payload is about 6.23 MiB; the existing 800-row cache is retained separately and remains unchanged. The five-track codec comparison selected OGG on file size and representation similarity, not downstream-label accuracy. Do not stage MTG's 156 GB archive or add DEAM to this baseline. Revisit DEAM only after defining its static/dynamic valence-arousal target and keeping its evaluation partitions untouched.

## Verified pre-training status (2026-09-24)

- EfficientAT is pinned to commit `a425fdce92572e602a1d5634799bd9f1f2efa806`. The official DyMN04-AS checkpoint is 8,086,973 bytes with SHA-256 `49b0e63577021994257b3de2ac5f1686d71554b93c8149f9d2568ad5760b904e`. Checkpoint and dataset terms are user-managed manually; this project has not verified or cleared them.
- The official loader and frontend passed a deterministic CPU smoke check in the project Python 3.12 environment using PyTorch 2.14.0, torchvision 0.29.0, and torchaudio 2.11.0. A 10-second mono 32 kHz window produces `[1, 1, 128, 1000]` mel input, `[1, 527]` AudioSet logits, and a finite, repeatable `[1, 384]` pooled feature. Only the pooled feature is a candidate Wavv input; the 527 logits are not Wavv labels. Details are in [`backbone_contract.json`](../music%20understanding/reports/backbone_contract.json).
- The pinned MTG-Jamendo metadata split has 32,859 train, 11,101 validation, and 11,565 test tracks, with zero track or artist overlap among split-0 partitions. All 10,671 human-annotation track IDs occur in split-0 test and none occur in train or validation. Split 1 overlaps split-0 test and is not an independent holdout.
- DEAM's three metadata partitions contain 744, 1,000, and 58 unique tracks. Both dynamic valence and arousal files cover all 1,802 IDs, begin at 15,000 ms, and advance by 500 ms. It is deferred from the first model; if added later, use 595/149 development tracks for fit/validation and keep both evaluation sets untouched.
- The local human-annotation TSV matches the exact pinned upstream file by SHA-256. Its parsed support differs from the pinned README table, which is recorded as upstream documentation drift in [`data_audit.json`](../music%20understanding/reports/data_audit.json); use the exact TSV's parsed labels and counts, while separately reviewing special-label semantics (notably `gender` and `voice_instrumental`).
- The five-track MP32/OGG/FLAC comparison completed. All temporary pilot audio files were deleted, and no dataset audio is retained. Results are in [`audio_format_pilot_5track.json`](../music%20understanding/reports/audio_format_pilot_5track.json). No training entry point has been added and no training has run.
- The original 800-track run passed SQLite, manifest-mapping, finite 384-D, repeatability, and temporary-audio checks; its manifests and reports are retained with `_800` filenames. The new 4,250-track manifest is hash-linked, artist-disjoint, preserves the old 800 split/artist/Jamendo-ID mappings exactly, and has nine weak-tag labels with positive and negative class support in every split. The new cache reuses those 800 rows and will fetch only the remaining 3,450. Full expanded extraction is still in progress; do not treat Gate 2A as passed until the final report confirms all 4,250 rows.
- No training entry point has been added and no training has run. This implementation stage stops before training.
- The workstation has 15.7 GiB RAM and 307 GiB free on C:; the first dataset pass still uses per-track OGG with immediate deletion, so this space is headroom rather than a reason to stage the 156 GB archive.

## Initial task contract

These are the initial candidates, based on unanimous, suitable labels parsed from the pinned clean TSV. Counts are track counts and overlap across taxonomies; artist counts in parentheses were counted from the TSV's `ARTIST_ID` field. The final 4,250-ID manifest report gives support by class and artist; only tasks passing every split's floor are retained.

| Candidate task | TSV class counts | Initial decision |
|---|---|---|
| Seven binary moods | acoustic 2,406 / not_acoustic 3,969; aggressive 822 / not_aggressive 6,851; electronic 3,935 / not_electronic 3,155; happy 1,472 / not_happy 4,209; party 711 / not_party 6,757; relaxed 2,296 / not_relaxed 3,810; sad 859 / not_sad 4,787 | Include as human-label candidates; keep each taxonomy separate. |
| Danceability | danceable 1,698 (367 artists) / not_danceable 2,759 (441 artists) | Include as a human-label candidate. |
| Gender | female 1,814 (263 artists) / male 260 (85 artists) | Candidate only; exclude `instrumental` and `unmatched`, and retain only if both classes meet the predeclared artist-held-out support gate. |
| Tonal/atonal | tonal 7,014 (635 artists) / atonal 410 (79 artists) | Candidate only; retain only if both classes meet the same support gate. |
| Four human genre taxonomies | Dortmund 621; electronic 180; Rosamerica 573; Tzanetakis 411. Their smallest classes have only 3–20 tracks. | Defer all four for the first capped model; use separate weak uploader genre tags. Reconsider only after the grouped-support audit. Do not merge taxonomies. |
| Voice/instrumental | voice 2,313; no clean instrumental-negative class | Exclude as a human binary task. The `voice` uploader tag may be used as a separately reported weak target. |
| Instruments | No human-validated instrument labels | Use selected MTG uploader instrument tags as weak labels only. |

Weak-label candidates are genre, mood/theme, and instrument tags attached to the same selected audio IDs. The 4,250 manifest retains five genre labels and four instrument labels whose positive and negative tracks/artists meet the reporting floors in fit, validation, and test; no mood/theme labels pass. A missing uploader tag is an untagged negative for this benchmark, not confirmed acoustic absence. Keep weak-tag scores separate from human-consensus scores. Do not introduce DEAM or another corpus into the first baseline.

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
4. Exact and near-duplicate audio checks across MTG-Jamendo and DEAM are not complete because no corpus audio is retained. DEAM is deferred from the first baseline, so this does not block its MTG-only split; run audio-level duplicate checks before adding DEAM.
5. The Android acceptance device/runtime is not confirmed. The Vivo I2018/API 33 CPU report in the DCLAP workspace is provisional, not a selected target. Keep the desktop path usable on the documented i5-1235U/16 GB workstation.

**Gate 0:** the backbone, dataset source, and official split contracts are reproducible. Checkpoint/audio terms are user-managed and do not block implementation. The human TSV source is hash-verified; its parsed counts are canonical despite README drift. Confirm the Android target before device evaluation; it does not block the desktop pre-training pipeline.

## Phase 1 — Audit labels and freeze the task matrix

The frozen manifest report is the task manifest: it records each retained target's source, class counts, support by tracks/artists, missing-label policy, split IDs, and seed. Keep the seven moods separate. Gender and tonal/atonal remain deferred because their rare classes fail the declared per-class track/artist floors. Freeze labels, support floors, metrics, and the deterministic seed before feature extraction or model fitting.

- **Human-validated properties:** use selected consensus taxonomies from the hash-verified TSV. Split whole artists, approximately 70/15/15 fit/validation/test, with seed 42; the artist grouping takes precedence over exact track ratios. Candidate labels are the seven moods, danceability, gender after special-label exclusion, and tonal/atonal. The gender and tonal/atonal rare classes need the declared per-class support floor. Defer all four human genre taxonomies in the first capped baseline because their multiclass support is sparse, and defer the voice/instrumental binary task because clean consensus has no negative class. Retire the original split-0 test claim for every reused ID. There is no human-validated instrument taxonomy.
- **Genre, mood/theme, instruments:** use uploader tags from the same selected recordings for weak targets. Recompute positive support on fit artists and freeze the label map there. Treat tag absence as an uploader-label negative only for this weak-label benchmark; state clearly that it means “not tagged,” not confirmed acoustic absence. Keep these metrics separate from human-consensus tasks.
- **DEAM valence/arousal:** defer from the first baseline. Before adding it, choose the intended output contract (timestamp-aligned dynamic window scores or whole-song static scores), align audio excerpts/offsets to the corresponding labels, and keep the official evaluation partitions untouched.
- Keep the DEAM/MTG dataset tasks separate and do not add another dataset before a specific coverage gap is measured.

**Gate 1 — passed for the 4,250-track cohort:** the exact TSV-derived human labels and uploader-tag map are frozen with zero artist and track overlap. Eight human tasks and nine weak-tag labels meet the declared split support floors. Gender and tonal/atonal remain deferred. The weak targets are evaluated separately and never presented as human-validated acoustic facts. Do not alter test groups after model results are seen.

## Phase 2 — Frozen-feature baseline

Implement the desktop training/reference path in the separate `music understanding/` workspace alongside `dclap/`; keep raw audio and generated artifacts out of ordinary source history, following the repository's existing manifest and evaluation-report pattern.

1. Join MTG IDs to Jamendo numeric IDs through the pinned raw metadata URL. Freeze the eligible-only 4,250-ID manifest at 2,975/638/637 tracks with zero artist/track overlap. Preserve the original 800 cached examples in the same splits and add only 3,450 new recordings. The report is hash-linked to the exact candidate inventory and API snapshot. At transfer, use the per-track file endpoint as final permission enforcement; if it refuses a frozen ID, stop and refresh/rebuild rather than substitute after the split. Do not claim reused recordings remain split-0 test examples or borrow another numbered split.
2. Start with the checkpoint's globally pooled final feature and the same simple linear heads. Probe a documented intermediate feature only if that baseline is inadequate on validation. Select using validation metrics, not test results, and confirm the chosen feature extraction can be reproduced by the full inference path.
3. Keep DyMN04-AS frozen. Extract one 384-value pooled feature per selected track in evaluation mode, then (after Gate 2A) fit 17 independent linear heads: eight human-consensus tasks and nine uploader-tag tasks. Do not backpropagate through the backbone or cache waveforms/mel arrays. Use only labels present in the frozen task matrix; do not invent voice/instrumental or valence/arousal targets.
4. `extract_mtg_features.py` verifies the hash-linked permission snapshot, validates and copies the existing 800 feature rows into a new 4,250-row cache, then requests an OGG only for missing IDs. It decodes a deterministic 10-second midpoint window to mono 32 kHz and exactly 320,000 samples, writes each finite/repeatable feature to resumable SQLite, and removes each temporary file in `finally`. The previous 800-row cache is left unchanged. The expanded run is still in progress; do not use the existing official-partition 2,500/750/1,000 weak-tag sample as a fallback.
5. Record the seed, code revision, configs, dataset manifests, split IDs, label maps, checkpoint, task metrics, training environment, and failure analysis for every run.

**Gate 2A — pre-training readiness:** all 4,250 features must be finite, 384-dimensional, repeatable, aligned one-to-one with the frozen manifest, integrity-checked in SQLite, and extracted with zero source audio retained. The original 800 rows are already audited and are being reused; Gate 2A remains pending until the other 3,450 are complete and `feature_extraction.json` records `full_manifest_complete: true`. Training has not started. This stage stops here.

### Locked first-fit recipe (not yet run)

- Train 17 independent binary logistic heads, one scalar logit for each retained target: eight human-consensus tasks and nine uploader-tag labels. Keep the backbone in evaluation mode and frozen. For human tasks, use only rows carrying that consensus label. For uploader tags, presence is positive and absence means only “not tagged.”
- Standardize features with per-dimension mean and standard deviation computed from the fit partition only; save these values with the heads. Use unweighted binary cross-entropy with logits plus L2 weight penalty, leaving the bias unpenalized. Fit deterministic full-batch CPU LBFGS models on `lambda ∈ {1e-4, 1e-3, 1e-2, 1e-1}`.
- Select `lambda` per task by validation AUROC; break ties toward stronger regularization. Then select the binary decision threshold on validation to maximize macro-F1, breaking ties toward the threshold nearest 0.5. Compare the selected head with an always-majority validation baseline. Do not tune on test.
- Lock the chosen weights, scalers, thresholds, taxonomy/label map, code/config versions, split-manifest hashes, backbone/checkpoint hashes, and validation report before one final test evaluation. Report per-class support by both tracks and artists, macro-F1, balanced accuracy, AUROC, confusion matrix, and artist-cluster bootstrap intervals. Because the test cohort is coverage-balanced by label, do not report its class proportions or micro/accuracy metrics as estimates of natural MTG prevalence.
- The heads contain 6,545 trainable scalars in total (384 weights plus one bias across 17 tasks); the raw 4,250-row float feature matrix is about 6.23 MiB. This is CPU-feasible on the measured workstation. Training remains unstarted until Gate 2A passes.

**Gate 2B — baseline quality (after training begins):** each retained head must beat its prevalence/majority baseline on validation. Tune thresholds and window policy only on validation, then freeze the run. Do not inspect held-out test scores until Phase 3's one final evaluation.

## Phase 3 — Held-out evaluation and song aggregation

Report metrics separately by task and dataset; do not collapse them into a single score.

- Multi-label tags: mAP plus macro/micro F1; report per-class support and performance.
- Binary or exclusive tasks: macro F1, balanced accuracy, and confusion matrix.
- Valence/arousal: MAE, RMSE, and correlation against the constant-mean baseline; report each dimension separately with DEAM's supplied annotation uncertainty.
- Give uncertainty using artist-cluster bootstrap intervals because tracks from one artist are correlated; a track-level bootstrap can be shown only as a secondary diagnostic.
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

The original 800-track cache is complete, and the approved 4,250-track manifest and task support are frozen. The separate cache is reusing the existing 800 rows while the remaining 3,450 are downloaded and processed. Training has not started; wait for Gate 2A's full 4,250-row audit, then stop for the later training session. Keep test artists locked and use validation only for model selection. Do not expand beyond 4,250 without a new evidence-based decision.

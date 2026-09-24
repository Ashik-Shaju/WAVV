# Wavv — Master Development Plan
**Status:** Corrected execution plan  
**Project:** Wavv — Offline-first AI music player  
**Exhibition:** 16 October 2026  
**Platform:** Android only  
**UI/application:** Kotlin + Jetpack Compose  
**Android stack:** Kotlin/Android  
**Cloud:** AWS Amplify + Amazon Cognito + Amazon S3 (optional account/cloud features)  
**Target device:** iQOO Z5, Snapdragon 778G, 8 GB RAM, Android 13  
**Development machine:** Intel i5-1235U, 16 GB RAM  
**Developers:** Ashik and Bristo

## 0. Purpose and authority

This is the master execution plan.

- `decisions.md` = durable architecture and scope.
- `technical.md` = interfaces and runtime contracts.
- model specifications = authoritative model-specific implementation details.

The project has approximately one month before the 16 October 2026 exhibition. Therefore the plan deliberately prioritizes a stable end-to-end demo over optional research branches.

---

# 1. Exhibition objective

Deliver one reliable Android application that can:

1. scan and play a local music library;
2. work when metadata is missing or incomplete;
3. analyze songs in the background;
4. generate DCLAP semantic embeddings;
5. provide structured Music Understanding attributes;
6. identify the dominant sung language for the six target languages when confidence is sufficient;
7. perform natural-language semantic search;
8. recommend the next song using content + behavior;
9. demonstrate UHQ enhancement for MP3/AAC through background processing and caching;
10. run the core path offline;
11. optionally sign in and demonstrate Wavv cloud backup/restore;
12. upload a selected song to Wavv Cloud and demonstrate controlled sharing/download.

---

# 2. Hard scope constraints

Must remain true:

- Android only.
- Kotlin + Jetpack Compose; React Native is not part of V1.
- No mandatory backend/cloud dependency for core playback or AI.
- Account/login remains optional.
- No mandatory internet.
- AWS V1 uses a minimal managed stack (Amplify + Cognito + S3); additional services require a concrete need.
- No WebRTC/P2P/torrent/TURN/STUN infrastructure in the exhibition path.
- Cloud upload is explicit and user-selected; never silently upload the whole local library.
- No continuous full-model inference during playback.
- No large on-device audio-language foundation model.
- No separate full model for every music attribute.
- No neural next-song model in V1.
- No spatial-audio work in the exhibition path.
- No research branch may block the P0 demo.
- Models must be tested on the iQOO Z5 before freeze.

---

# 3. Final model inventory

| Component | V1 decision | Status |
|---|---|---|
| UHQ-MP3 | required | train/custom |
| UHQ-AAC | required | train/custom |
| UHQ-Opus | optional | stretch |
| Music Understanding | DyMN04-AS + Wavv heads | train/adapt |
| Audio semantic embedding | DCLAP audio encoder | pretrained |
| Text retrieval | native DCLAP-compatible CLAP text tower | baseline |
| Alternative text encoders | projection/alignment experiment | optional |
| Singing-LID | compact CNN | train/custom |
| iALS | implicit-feedback recommender | train/local |
| Hybrid ranker | configurable weighted ranking | non-neural V1 |

---

# 4. Core data strategy

## 4.1 UHQ

Primary clean reference:
- MUSDB18-HQ `mixture.wav`

Additional clean music:
- only license-checked/validated sources when needed

Synthetic degradation:
- MP3: 128/160/192/256/320 kbps
- AAC: 96/128/192/256/320 kbps
- Opus: 64/96/128/160/192 kbps, optional

Do not use MUSDB18-HQ stems for the core UHQ training path.

## 4.2 Music Understanding

Primary:
- artist-grouped subset of MTG Music Classification Annotations for the taxonomies they cover, after reconciling the pinned TSV; reused split-0 test IDs lose the original test claim
- MTG uploader tags from the same selected tracks for broader weak-label tasks, including instruments

The numbered MTG-Jamendo partitions overlap and are alternative randomized splits; do not combine them.

Emotion:
- DEAM for valence/arousal

Do not add OpenMIC/FMA unless a measured label-coverage problem requires it.

## 4.3 Singing-LID

Core:
1. Wavv permitted modern-song corpus.
2. Indian Regional Music Dataset.
3. GTSinger.

Do not make DALI/Slingua core dependencies.

## 4.4 Recommendation behavior

Core:
1. 30Music.
2. MSSD.
3. Yambda-50M.
4. Wavv-native interaction log.

Optional:
- Deezer cold-start dataset for targeted research.

Do not use Yambda-5B as a development dependency.

---

# 5. V1 architecture

```text
LOCAL AUDIO
   │
   ├───────────────► DCLAP audio ─────────► 512-D embedding
   │
   ├───────────────► Music Understanding ─► structured profile
   │
   └───────────────► Singing-LID ─────────► language + confidence
   │
   └───────────────► DSP ─────────────────► BPM/key/loudness/duration
   │
   ▼
SongProfile + vector index
   │
   ├──► semantic search
   ├──► similar songs
   ├──► filtering/playlist generation
   └──► hybrid recommendation
                ▲
                │
        iALS + current session
```

UHQ is a separate background preprocessing path that produces a cached enhanced file.

---

# 6. Execution order

The order below is deliberate. Do not start with the hardest training problem.

## Phase 0 — repository and contracts
**Goal:** no architecture ambiguity.

Implement/verify:
- repository structure
- model configuration files
- `SongProfile` schema
- model bundle metadata
- canonical behavioral event schema
- dataset manifest format
- background-job interfaces

Exit:
- code compiles/tests;
- contracts are stable.

## Phase 1 — local library + playback
**Goal:** a working app before AI.

Implement:
- file discovery
- stable song IDs
- metadata fallback
- playback/queue
- basic UI
- local DB
- background scheduler skeleton

Exit:
- local library reliably scans and plays.

## Phase 2 — DCLAP indexing
**Goal:** establish the semantic retrieval foundation.

Implement:
- exact DCLAP checkpoint integration
- exact preprocessing verification
- FP32 audio inference
- 512-D normalization
- vector persistence
- brute-force cosine retrieval

Do not start quantization yet.

Exit:
- deterministic sample embeddings;
- text/audio similarity path works;
- search can retrieve by a known test query.

## Phase 3 — Music Understanding baseline
**Goal:** create structured song attributes.

Implement:
- DyMN04-AS frozen backbone
- verified intermediate feature extraction
- compact Wavv-specific heads
- MTG-Jamendo label mappings
- DEAM valence/arousal head
- window-level inference
- song-level aggregation
- profile cache

Follow the [Music Understanding implementation plan](Wavv_Music_Understanding_Implementation_Plan.md) for artifact pinning, label/split gates, and held-out evaluation. Human annotations from split-0 test may be used only after reconciliation and an artist-grouped split; reused tracks lose their original split-0 test claim.

Exit:
- metrics are recorded for each task;
- profile is usable by the app.

## Phase 4 — Singing-LID baseline
**Goal:** six-language audio classification.

Implement:
- manifests
- artist/song-disjoint split
- 16 kHz mono preprocessing
- 80-bin log-mel frontend
- 10-second crops
- vocal/activity filtering
- compact CNN
- balanced sampling
- song-level aggregation
- validation-based Unknown threshold

Exit:
- per-language F1/confusion matrix;
- Unknown behavior measured.

Only then consider teacher/distillation or TCN.

## Phase 5 — Recommendation foundation
**Goal:** local behavioral personalization.

Implement:
- canonical Wavv event schema
- 30Music adapter
- MSSD adapter
- Yambda-50M adapter
- behavioral normalization
- interaction-strength configuration
- iALS baseline at 32/64 latent dimensions
- popularity/content-only baselines
- time-aware evaluation

Exit:
- iALS and baselines are reproducibly comparable.

## Phase 6 — semantic text projection experiments
**Goal:** decide whether V1 needs an alternative text encoder.

Start with the native DCLAP/CLAP text tower as baseline.

Only when the baseline is measured:
- benchmark MiniLM/e5/GTE candidates;
- train 2-layer projection into DCLAP 512-D space;
- compare Recall@1/5/10, MRR, RAM, size, latency.

Exit:
- either keep native text;
- or record an explicit new decision for the selected projected encoder.

This phase is optional for the exhibition unless native text performance/deployment is inadequate.

## Phase 7 — Cloud account, backup/restore, and Wavv Cloud
**Goal:** add a real AWS component without making core playback/cloud dependent.

Implement in the smallest useful order:
1. configure AWS Amplify Android integration;
2. configure Cognito email-based account flow;
3. define a small versioned Wavv backup payload for selected persistent state;
4. upload/download backup objects using authenticated S3 access;
5. restore backup after sign-in/reinstall;
6. add explicit user-selected song upload to Wavv Cloud;
7. list/download/play cloud songs;
8. add controlled file sharing using the simplest supported AWS mechanism (prefer time-limited share access);
9. test quota, failure, retry and offline fallback behaviour.

Do **not** add API Gateway/DynamoDB/Lambda unless the concrete sharing or backup requirement cannot be implemented cleanly with Amplify + Cognito + S3.

Exit:
- a user can run Wavv anonymously;
- a signed-in user can back up and restore selected app data;
- a selected cloud-uploaded song survives uninstall/reinstall and can be downloaded again;
- cloud access failures do not break local playback.

## Phase 8 — UHQ training
**Goal:** produce the exhibition-critical enhancement.

Order:
1. dataset generation smoke test;
2. MP3 baseline;
3. AAC baseline;
4. evaluation;
5. background cache integration;
6. optional quantization/export optimization;
7. optional Opus only if time remains.

Core UHQ model:
- compact residual time-frequency network;
- bounded frequency correction;
- DSP reconstruction.

Exit:
- measurable improvement versus compressed baseline;
- no obvious artifact gate failures;
- cached playback works.

## Phase 9 — hybrid ranking
**Goal:** combine the model outputs.

Candidate sources:
- DCLAP similarity
- semantic query candidates
- Music Understanding filters
- language match
- iALS score
- recent-session context
- novelty/diversity
- repetition penalty

Do not build a neural reranker.

Exit:
- similar songs;
- natural-language search;
- personalized next-song ranking;
- automatic playlist generation all use the same ranking infrastructure.

## Phase 10 — Android optimization
**Goal:** make the system exhibition-safe.

Measure on iQOO Z5:
- cold/warm model load
- per-window latency
- whole-song indexing time
- peak RAM
- storage
- playback stability
- thermal behavior
- UHQ background throughput
- search responsiveness

Optimize in this order:
1. lazy loading
2. cache reuse
3. model release after task
4. ONNX/INT8 where validated
5. smaller models only when profiling proves necessary
6. accelerator experiments only where supported and beneficial

## Phase 11 — freeze and exhibition build
**Goal:** stop research drift.

Freeze:
- model versions
- label mappings
- SongProfile schema
- vector format
- UHQ models
- ranking formula
- Android runtime configuration

After freeze:
- bug fixes
- crash fixes
- UI polish
- demo reliability
- low-risk performance fixes

No new architecture branches after freeze.

---

# 7. P0 / P1 / P2 scope

## P0 — must work

- local scan/playback
- offline operation
- missing-metadata fallback
- DCLAP embedding/index
- Music Understanding profile
- six-language Singing-LID
- semantic search
- similar songs
- basic iALS personalization
- hybrid next-song recommendation
- MP3/AAC UHQ cached enhancement
- polished demo flow

## P1 — desirable

- automatic playlist generation
- better cold-start ranking
- online AcoustID/MusicBrainz enrichment
- stronger text-query parsing
- better recommendation explanations

## P2 — stretch

- Opus UHQ
- multilingual native-language text search via projected encoder
- teacher/distillation Singing-LID
- TCN Singing-LID
- learned ranking weights
- deeper session modeling
- broader language coverage

P2 cannot delay P0.

---

# 8. Dataset preparation rules

For every dataset:

```text
source
version/access_date
license/usage notes
subset
file/track count
duration
split
label/event mapping
preprocessing
checksum where practical
```

Before training:
1. verify access;
2. verify decode;
3. verify labels/events;
4. remove duplicates where practical;
5. create manifest;
6. create leak-safe splits;
7. only then generate derived crops/features.

Do not repeatedly re-encode the same dataset manually.

---

# 9. Leakage rules

## UHQ
Split by source song before crop creation. Preserve MUSDB18-HQ held-out test tracks.

## Music Understanding
Keep track-level separation and preserve official split definitions where applicable.

## Singing-LID
Split by song and artist where feasible before window creation.

## Recommendation
Use time-aware splits. Never train on future interactions relative to the evaluation period.

Do not merge user identities across 30Music/MSSD/Yambda.

---

# 10. Team allocation

## Ashik — app/integration lead

- Kotlin + Jetpack Compose UI
- Figma-to-Compose implementation
- playback
- MediaStore/file discovery
- local database
- background workers
- model runtime integration
- vector search integration
- recommendation orchestration
- AWS Amplify/Cognito/S3 integration
- Android profiling

## Bristo — ML/data lead

- dataset acquisition/validation
- preprocessing
- model training
- evaluation
- model export/quantization
- embedding generation
- experiment tracking
- cloud-data schema review where needed

Both developers review:
- model I/O contracts
- database schema
- model versioning
- release build

No model is considered integrated until both the ML output and app-side contract are tested.

---

# 11. Compute policy

## i5-1235U / 16 GB

Use for:
- preprocessing
- manifests
- feature caching
- small smoke tests
- DCLAP/MU/LID inference debugging
- iALS
- evaluation
- export
- Android builds/testing

## Free/limited GPU

Use for:
- UHQ full training
- Music Understanding training
- Singing-LID training
- text projection experiments
- larger controlled experiments

Do not make GPU availability a runtime dependency.

---

# 12. Model-specific acceptance gates

## UHQ
Required:
- held-out improvement over compressed baseline
- no major ringing/warbling/clipping
- stereo image preserved
- playable cached output
- acceptable background runtime

## Music Understanding
Required:
- task-specific metrics
- stable multi-window aggregation
- acceptable song-level profile quality
- model export
- Android benchmark

## DCLAP
Required:
- sane text/music similarity
- sane music/music similarity
- stable 512-D vectors
- measured model size/RAM/latency

## Singing-LID
Required:
- six mandatory languages evaluated
- per-language metrics
- artist/song-disjoint test
- calibration/Unknown behavior
- Android inference benchmark

## iALS/hybrid
Required:
- beats popularity meaningfully on offline evaluation
- hybrid adds value on personalized cases
- cold-start path remains usable
- local training/update remains practical

---

# 13. Exhibition demo sequence

Use a deterministic, rehearsed flow:

1. Open Wavv offline.
2. Show local songs, including incomplete metadata.
3. Trigger or display background analysis.
4. Search: `happy Malayalam songs`.
5. Show language-aware semantic results.
6. Open a song and inspect AI attributes.
7. Play with UHQ toggle using an already cached enhancement.
8. Skip/favorite/replay a small sequence.
9. Request next song and show personalization.
10. Open an automatically generated playlist.
11. Toggle airplane mode and repeat core search/play/recommendation flow.
12. Optional connected demo: sign in, show backup/cloud library, upload a permitted demo song, and download/share it.

The core demo must not depend on network availability or live cloud inference. The cloud/account portion is demonstrated as a separate optional connected segment and must fail gracefully when the network is unavailable.

---

# 14. Risk control

Highest-risk items:
1. UHQ perceptual quality.
2. Android inference/memory when multiple models are involved.
3. Singing-LID accuracy across six languages.
4. Dataset access/processing time.
5. recommendation quality with sparse local user history.

Mitigation:
- build baselines early;
- use caching;
- keep fallback behavior;
- freeze scope;
- never make optional research a dependency.

---

# 15. Final execution priority for the remaining schedule

### Priority 1
App shell + local playback + indexing contracts.

### Priority 2
DCLAP + Music Understanding + Singing-LID inference paths.

### Priority 3
Behavioral event schema + iALS baseline + hybrid ranking.

### Priority 4
UHQ MP3/AAC training + cache integration.

### Priority 5
Android profiling + quantization only where validated.

### Priority 6
UI polish + exhibition rehearsal + reliability fixes.

Optional research branches are pursued only after the corresponding P0 path is stable.

---

# 16. Definition of done

A component is done only when:

- source/data version is recorded;
- preprocessing is reproducible;
- model configuration is recorded;
- metrics are saved;
- baseline comparison exists where applicable;
- exported artifact works;
- artifact size is known;
- target-device latency/RAM is measured;
- failure cases are documented;
- cache/index behavior is tested;
- app-side contract is implemented;
- decision/status is recorded.

A trained checkpoint sitting on disk is not a finished component.

---

# 17. Coding-agent rules

Before changing architecture, an AI coding agent must read:
- `decisions.md`
- `technical.md`
- the relevant model-specific specification

The agent must:
1. inspect the actual repository before creating abstractions;
2. inspect exact model/checkpoint interfaces before integration;
3. prefer small testable changes;
4. add tests for schema and model-boundary changes;
5. never silently substitute a checkpoint/dataset/runtime;
6. record major experimental results;
7. preserve offline functionality;
8. avoid large rewrites late in the schedule.

When a model specification conflicts with an older project-level sentence, the newer corrected decision/model specification controls and the project-level file must be updated.

---

# 18. Source-of-truth map

| Topic | Authoritative source |
|---|---|
| Durable architecture/scope | `decisions.md` |
| App/runtime interfaces | `technical.md` |
| UHQ architecture/training | UHQ model specification |
| Music Understanding/DCLAP integration | Music Understanding + embedding specification |
| DCLAP quantization/text architecture | DCLAP specification |
| Singing-LID architecture/datasets | Singing-LID specification |
| Recommendation behavioral datasets | Recommendation/behavioral specification |
| Execution order | `plan.md` |
| Cloud computing architecture | `cloud_computing.md` + cloud decisions in `decisions.md` |

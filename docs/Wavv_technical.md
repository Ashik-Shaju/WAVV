# Wavv — Technical Specification and ML Contracts
**Status:** Corrected active engineering specification  
**Related:** `decisions.md`, `plan.md`  
**Platform:** Android  
**UI/application:** Kotlin + Jetpack Compose  
**Platform/runtime:** Kotlin/Android  
**Cloud integration:** AWS Amplify + Amazon Cognito + Amazon S3 (optional)

## 0. Contract hierarchy

This document defines application interfaces and runtime contracts.

- Model-specific documents define model internals.
- This file defines how those models connect to the Android app.
- `decisions.md` defines durable architecture decisions.
- `plan.md` defines execution order.

An implementation must not invent tensor shapes, preprocessing parameters, checkpoints, labels, or runtime assumptions that are not present in the selected model specification/checkpoint.

---

# 1. System architecture

```text
Jetpack Compose UI
      │
      ▼
Kotlin / Android application layer
      ├── MediaStore/file discovery
      ├── playback
      ├── background analysis scheduler
      ├── local database
      ├── model runtime bridge
      ├── vector search
      └── recommendation engine
               │
     ┌─────────┼─────────┐
     │         │         │
     ▼         ▼         ▼
   DCLAP       MU       Singing-LID
     │         │         │
     ▼         ▼         ▼
 embeddings  profile  language/confidence
     │         │         │
     └─────────┼─────────┘
               ▼
          SongProfile cache
               │
        retrieval/ranking
```

Jetpack Compose owns presentation. Kotlin/Android owns audio I/O, background work, local inference, indexing, recommendation computation, local storage, and cloud integration. Avoid unnecessary serialization/bridge layers for large audio or embedding data.

Do not repeatedly move raw audio or embedding matrices through the JS bridge.

---

# 2. Song lifecycle

For each discovered file:

```text
DISCOVERED
   ↓
METADATA_READY
   ↓
ANALYSIS_PENDING
   ├── DCLAP
   ├── Music Understanding
   ├── Singing-LID
   └── optional UHQ
   ↓
READY
```

Partial completion is valid. A song remains playable even if analysis is incomplete.

The analysis scheduler should support retryable and permanent failure states.

Recommended states:

```text
DISCOVERED
ANALYSIS_PENDING
PARTIALLY_READY
READY
FAILED_RETRYABLE
FAILED_PERMANENT
```

---

# 3. Stable song identity

Generate `song_id` from stable file identity, not title/artist.

Store:
- URI/path reference
- file size
- modification time
- duration
- actual codec/container information
- sample rate/channels when available
- metadata
- analysis/model versions

When file identity or relevant model version changes, invalidate only the affected derived artifacts.

---

# 4. SongProfile contract

```json
{
  "schema_version": 1,
  "song_id": "stable-id",
  "uri": "content://...",
  "file": {
    "size_bytes": 0,
    "modified_at": 0,
    "codec": "mp3",
    "container": "mp3",
    "sample_rate": 44100,
    "channels": 2,
    "duration_ms": 0
  },
  "metadata": {
    "title": null,
    "artist": null,
    "album": null,
    "genre": null,
    "source": "embedded|filename|online|unknown"
  },
  "semantic_embedding": {
    "model_id": "dclap-version",
    "dimension": 512,
    "dtype": "float32",
    "normalized": true,
    "vector_ref": "local-vector-index-id"
  },
  "music_attributes": {
    "genre": [],
    "mood": [],
    "instruments": [],
    "voice_instrumental": null,
    "danceability": null,
    "emotion": {
      "valence": null,
      "arousal": null
    },
    "model_id": "mu-v1"
  },
  "language": {
    "label": "Malayalam",
    "confidence": 0.0,
    "is_abstained": false,
    "model_id": "slid-v1"
  },
  "dsp": {
    "bpm": null,
    "key": null,
    "loudness": null,
    "duration_ms": 0
  },
  "analysis": {
    "status": "ready",
    "model_bundle_version": "...",
    "analyzed_at": 0
  }
}
```

Arrays may be empty. Numeric outputs may be null when the relevant task is unavailable or not yet analyzed.

---

# 5. DCLAP contract

## 5.1 V1 architecture

Use the exact selected DCLAP implementation:

```text
DCLAP audio branch
    = distilled pretrained audio student

DCLAP text branch
    = compatible original CLAP text tower
```

Both map into the compatible 512-D retrieval space.

Do not describe the selected DCLAP checkpoint as having a separately distilled text student.

## 5.2 Audio input

Use the exact preprocessing required by the selected DCLAP checkpoint/repository.

Do not hard-code:
- sample rate
- hop size
- window size
- tensor names
- normalization
- chunking

until verified from the actual implementation.

## 5.3 Output

```text
float32[512]
→ L2 normalize
→ persist as the initial song vector
```

INT8 encoder experiments are allowed, but stored vectors remain FP32 initially.

## 5.4 Runtime

ONNX Runtime is the deployment baseline for the selected DCLAP ONNX models.

Run during background indexing. Do not encode full songs for every search query.

---

# 6. DCLAP quantization contract

Benchmark these configurations:

```text
A: FP32 audio + FP32 text
B: INT8 audio + FP32 text
C: FP32 audio + INT8 text
D: INT8 audio + INT8 text
```

Measure:
- model size
- RAM
- cold start
- audio encoding latency
- query encoding latency
- cosine drift
- text-to-music Recall@K
- music-to-music retrieval quality

INT8 may be accepted branch-by-branch. Do not silently quantize and ship.

---

# 7. Text retrieval contract

### V1

```text
UTF-8 query
   ↓
native compatible DCLAP/CLAP text encoder
   ↓
512-D embedding
   ↓
L2 normalize
   ↓
cosine similarity against cached DCLAP audio vectors
```

### Alternative text encoders

MiniLM, multilingual-e5-small and GTE multilingual are **experimental alternatives**, not direct replacements.

Any alternative requires:

```text
candidate text encoder
      ↓
trained projection/alignment
      ↓
512-D DCLAP-compatible space
```

A replacement encoder cannot be compared directly with DCLAP vectors merely because its output dimension is 512 or can be resized.

---

# 8. Music Understanding contract

## 8.1 V1 model

Use **DyMN04-AS**.

Initial policy:
- frozen pretrained backbone;
- Wavv-specific heads on the verified 384-value pooled feature;
- verify the exact feature layer against the actual model implementation.

Do not use the AudioSet 527-class logits as Wavv metadata.

## 8.2 Tasks

Supported V1 task families:

```text
genre
mood/theme
instruments
voice/instrumental
danceability
valence
arousal
```

Train only tasks with valid labels. Missing task labels must be masked rather than forcing every track to contribute to every head.

The first low-resource fit uses eight independent one-logit binary logistic heads. Fit each head only on rows with that task's consensus label; fit feature standardization on fit rows only. Tune regularization and the decision threshold on validation, and do not inspect test scores until the model is locked.

The first low-resource baseline is narrower than the eventual profile: use the eight human-annotation tasks retained by the frozen 800-track manifest in the [implementation plan](Wavv_Music_Understanding_Implementation_Plan.md). Gender, tonal/atonal, uploader-tag heads, vocal/instrumental, instruments, and DEAM valence/arousal are deferred until their support/target contracts pass the same evidence gates. Empty or deferred fields remain unavailable; do not synthesize predictions for them.

## 8.3 Data

Primary:
- artist-grouped Music Classification Annotations from split-0 test tracks for the taxonomies they cover; using any annotated ID for fitting/tuning retires its original split-0 test claim
- MTG uploader tags on the same selected IDs for broader weak-label tasks, including instruments; keep human and weak-label scores separate
- numbered MTG partitions are alternate overlapping splits and must not be combined

Emotion:
- DEAM for valence/arousal

Do not automatically add OpenMIC or FMA. Add them only after a measured coverage/quality gap.

## 8.4 Input/windowing

The selected DyMN implementation defines its input contract. Use fixed-size analysis windows and aggregate them to a song-level profile.

Do not pass a multi-minute song as one giant tensor.

Reference:
```text
full song
  ↓
backbone-compatible windows
  ↓
per-window predictions/features
  ↓
robust aggregation
  ↓
song profile
```

The exact window and hop must come from the selected checkpoint contract, not from a generic assumption.

## 8.5 Output

```json
{
  "genre": [{"label": "rock", "score": 0.82}],
  "mood": [{"label": "happy", "score": 0.79}],
  "instruments": [{"label": "electric_guitar", "score": 0.91}],
  "voice_instrumental": {"vocal": 0.97},
  "danceability": 0.71,
  "emotion": {"valence": 0.63, "arousal": 0.81},
  "model_version": "mu-v1"
}
```

Labels and mappings are configuration artifacts derived from verified training labels.

---

# 9. Singing-LID contract

## 9.1 V1 architecture

```text
10 s mono audio
   ↓
16 kHz
   ↓
80-bin log-mel
   ↓
compact CNN
   ↓
6-class probabilities
   ↓
validation-calibrated abstention
```

Preferred size: ~1–3M parameters. Practical ceiling <5M.

A small TCN is experimental only.

## 9.2 Production labels

```text
English
Hindi
Malayalam
Tamil
Telugu
Kannada
```

`Unknown` is an abstention outcome.

Do not add a mandatory learned `Other` class in V1.

## 9.3 Dataset

Core:
1. Wavv permitted modern-song corpus — primary.
2. Indian Regional Music Dataset — Indian-language anchor.
3. GTSinger — singing diversity + English.

DALI/Slingua are optional research/diagnostic sources, not core dependencies.

## 9.4 Training

Baseline:
- from scratch compact CNN;
- weighted cross entropy;
- language-balanced sampling;
- artist/song-disjoint split;
- 10-second vocal-focused crops;
- conservative augmentation after the baseline.

Teacher/distillation is fallback only.

## 9.5 Inference

Use:
```text
10 s window
5 s stride
50% overlap
```

Filter clearly non-vocal windows where the training pipeline supports it.

Aggregate valid window probabilities into a song-level probability.

Apply a threshold learned on validation only:

```text
max_probability >= threshold → language
otherwise → Unknown
```

Return:

```json
{
  "label": "Malayalam",
  "confidence": 0.91,
  "is_abstained": false,
  "model_id": "slid-v1"
}
```

---

# 10. UHQ contract

## 10.1 Core models

Required:
- UHQ-MP3
- UHQ-AAC

Optional:
- UHQ-Opus

## 10.2 Reference and degradation

Primary clean source:
- MUSDB18-HQ `mixture.wav` only

Do not use the MUSDB18-HQ stems for the core UHQ training path.

Synthetic degradation:

```text
MP3: 128/160/192/256/320 kbps
AAC:  96/128/192/256/320 kbps
Opus: 64/96/128/160/192 kbps [optional]
```

Clean and compressed segments must remain time-aligned.

## 10.3 Model

Input:
```text
compressed audio STFT log-magnitude
```

Output:
```text
bounded adaptive frequency correction
```

Reconstruction:
```text
apply correction
→ inverse STFT / DSP
→ enhanced audio
```

Do not build a neural phase model, waveform generator, transformer, diffusion model, or unrestricted EQ booster in V1.

## 10.4 Runtime

```text
detect actual codec
→ choose codec-specific model
→ check cache
→ background enhancement if needed
→ play cached output
```

Do not infer codec solely from filename extension. Never overwrite the original file.

---

# 11. DSP metadata contract

Use deterministic or existing-library processing for:
- BPM/tempo
- key
- loudness
- duration

Do not add a separate neural model merely to compute these values unless a later requirement explicitly justifies it.

---

# 12. Behavioral event contract

Canonical local event:

```text
user_id
song_id
timestamp
session_id
event_type
listen_seconds
completion_ratio
skip_position_seconds
selection_source
playlist_id
recommendation_id
```

Supported/extendable event types:

```text
play
complete
skip
replay
favorite
unfavorite
playlist_add
playlist_remove
search
search_select
manual_select
pause
resume
seek_forward
seek_backward
recommendation_impression
recommendation_select
```

Do not require events that the first UI does not produce.

---

# 13. Behavioral dataset adapters

Core adapters:

```text
30Music
MSSD
Yambda-50M
```

Optional research adapter:
```text
Deezer cold-start
```

Each adapter maps real source fields into the canonical Wavv event schema.

Never invent missing source events.

Do not merge datasets as if their user IDs, item IDs, and event semantics were shared.

---

# 14. Preference mapping / iALS contract

Raw events first pass through:

```text
raw event
→ normalized behavior
→ interaction strength r_ui
→ confidence c_ui
→ iALS
```

Long-term preference candidates:
- favorite/like
- playlist add
- high completion
- replay/repeated listening
- repeated manual selection

Primarily contextual:
- pause
- seek
- current session
- time of day
- context switch
- queue state

iALS parameters and event weights must be configuration, not scattered constants.

Start with modest latent dimension such as 32 or 64.

---

# 15. Recommendation contract

Candidate sources:
1. DCLAP nearest neighbours.
2. semantic-query candidates.
3. attribute-filtered local songs.
4. iALS personalized candidates.
5. recent-session candidates.

Ranking signals:
```text
semantic_similarity
attribute_match
language_match
personalization_score
session_context
novelty/diversity
repetition_penalty
```

Initial implementation is a configurable weighted ranker.

APIs:

```text
searchMusic(query) -> SearchResult[]
similarSongs(songId) -> Song[]
recommendNextSong(context) -> RankedSong[]
createPlaylist(request) -> Playlist
```

No neural reranker is required for V1.

---

# 16. Cold-start contract

## New user
Use:
- selected songs/artists
- content/audio similarity
- language
- mood/style
- current session

Increase iALS contribution as interaction history accumulates.

## New song
Use:
- DCLAP embedding
- Music Understanding profile
- Singing-LID result
- DSP metadata

Do not fabricate an iALS vector for a completely new item.

---

# 17. Local storage

Recommended entities:

```text
Song
SongProfile
VectorIndexMetadata
ListeningEvent
Playlist
PlaylistItem
ModelBundle
AnalysisJob
```

Store embeddings outside the main JSON/SQLite song row where practical.

Use a vector index reference:

```text
SongProfile → vector_ref → vector store
```

Phase 1 vector search:
- normalized FP32 512-D
- brute-force cosine

Benchmark:
- 500
- 1,000
- 5,000
- 10,000 songs

Only then consider ANN or vector compression.

---

# 18. Background scheduling

Requirements:
- resumable
- retryable
- playback-safe
- thermally aware
- prioritizes current/recent/visible songs
- does not require network
- exposes progress for large libraries

Suggested priority:

```text
1. currently requested
2. recently played
3. visible/priority library
4. remaining library
```

Models should be lazy-loaded where memory pressure exists.

---

# 19. Export and runtime contract

Preferred deployment:
- ONNX where supported by the selected model
- ONNX Runtime CPU baseline

Optional:
- NNAPI/QNN/other accelerators after profiling

Every model artifact records:

```text
model_id
version
checksum
input contract
output contract
preprocessing version
quantization type
file size
CPU latency
peak RAM
accelerator latency when tested
```

Do not assume an accelerator is faster.

---

# 20. Cloud computing and account runtime

## 20.1 Design goal

Cloud functionality is optional and must never block local playback or core AI features.

V1 preferred stack:

```text
Wavv Android (Kotlin + Compose)
        │
        ▼
AWS Amplify Android
     /       \
 Cognito      S3
  account   cloud files
```

Do not add API Gateway, Lambda, DynamoDB, EC2, WebRTC, TURN, STUN, or other infrastructure unless a concrete V1 requirement cannot be satisfied without it.

## 20.2 Account

Amazon Cognito handles optional account registration, sign-in, verification/recovery and authenticated identity. The user can continue without an account.

Recommended V1 login:
- email + password
- verification as required by the configured Cognito flow

## 20.3 Backup/restore

Back up a versioned, compact Wavv state payload containing selected durable user data:
- playlists
- favorites
- listening history/recently played
- settings/preferences
- recommendation-related user state
- selected persistent analysis metadata if stable song identity permits it

Do not back up disposable caches, temporary UHQ outputs, temporary tensors or logs.

Recommended flow:

```text
Local DB
   ↓
Create versioned backup payload
   ↓
Authenticated S3 object
   ↓
Restore after reinstall/sign-in
   ↓
Validate schema/version
   ↓
Merge or restore into local DB
```

Backup operations must be retryable and must not delete local data when cloud sync fails.

## 20.4 Wavv Cloud Music

A local song is uploaded only after explicit user action. The upload stores the actual audio file in S3 and records local metadata needed to display/manage it.

Cloud songs can be listed and downloaded later. They should not replace the local file automatically.

## 20.5 Sharing

V1 cloud sharing is whole-file sharing through S3, not peer-to-peer. A cloud-uploaded file can be shared using a controlled access mechanism. Time-limited S3 presigned URLs are a suitable primitive when a recipient should receive temporary access.

For the first implementation, prefer the simplest sharing flow that can be securely integrated with Cognito/Amplify. If recipient-specific authorization requires a small backend function, add only that single function; do not introduce a broad microservice architecture.

Do not implement in V1:
- WebRTC data channels
- custom signaling servers
- STUN/TURN infrastructure
- BitTorrent/DHT
- multi-peer chunk transfer

## 20.6 Cloud/local boundaries

Local-only data:
- raw local files that were not uploaded
- temporary ML data
- UHQ intermediate/cache artifacts
- temporary audio buffers

Cloud-backed data:
- account identity
- backup payload
- user-selected cloud music
- explicit shared-file objects

## 20.7 Failure behaviour

If AWS/network access fails:
- local playback continues;
- local playlists/favorites/history remain available;
- cached AI features remain available;
- cloud items are marked unavailable until connectivity returns;
- backup/upload jobs can retry later.

# 21. Offline/online boundary

## Must work offline
- playback
- scanning
- cached analysis
- DCLAP retrieval
- Music Understanding
- Singing-LID
- semantic search
- recommendation
- iALS updates/retrieval
- cached UHQ playback

## Optional online
- AcoustID
- MusicBrainz
- artwork/metadata enrichment

Online failures must degrade gracefully.

---

# 22. Evaluation contracts

## UHQ
Compare:
- compressed baseline
- enhanced output
- optional DSP baseline
- clean reference where appropriate

Measure:
- multi-resolution STFT loss/distance
- spectral convergence
- waveform error
- clipping/peak safety
- loudness consistency
- listening/artifact inspection
- runtime/model size

## Music Understanding
- multi-label: mAP, macro F1, micro F1
- multi-class: accuracy, macro F1, confusion matrix
- regression: MAE, RMSE, correlation where meaningful

## Singing-LID
- song-level macro F1
- per-language precision/recall/F1
- balanced accuracy
- confusion matrix
- abstention/selective accuracy
- calibration

## DCLAP
- text→music Recall@1/5/10
- music→music retrieval
- latency/RAM/model size
- cosine drift for quantized variants

## Recommendation
- Recall@5/10/20
- Precision@5/10/20
- NDCG@5/10/20
- MAP@5/10/20
- coverage
- novelty
- temporal leakage checks

Compare against:
- popularity
- content-only
- iALS
- hybrid

---

# 23. Experiment and artifact tracking

Every experiment stores:

```text
experiment_id
model_id
base_checkpoint
code_version
config
random_seed
dataset_manifest
split
metrics
hardware
training_time
exported_artifact
failure_analysis
notes
```

Use JSON/YAML/CSV when a full experiment platform is unnecessary.

---

# 24. Model loading and memory policy

Do not load every model simultaneously on the Android device.

Preferred pattern:

```text
indexing task
→ load required model
→ process batch
→ persist results
→ release model resources
→ next task
```

Keep small always-loaded components only when profiling demonstrates the benefit.

---

# 25. Non-goals

Do not implement in the core exhibition path:
- mandatory cloud recommendation
- LLM-based playlist generation
- continuous full-model inference during playback
- CLaMP 3/MuQ-MuLan-style heavyweight runtime
- spatial audio
- mandatory source separation
- automatic lyric generation
- neural next-song generation model

---

# 26. Stable module boundaries

Suggested modules:

```text
models/
  dclap/
  music_understanding/
  singing_lid/
  uhq/

retrieval/
  text/
  vector/
  ranking/

recommendation/
  events/
  adapters/
  ials/
  hybrid/

pipeline/
  indexing/
  background/

app/
  native/
  ui/
```

Exact repository organization may differ, but the logical boundaries must remain.

---

# 27. Implementation rule

When the repository/checkpoint provides an exact contract, follow it.

The coding agent must:
1. inspect the selected model/repository;
2. verify input/output tensors;
3. run a small inference test;
4. record preprocessing;
5. then implement integration.

Never infer a tensor shape, checkpoint name, or model runtime solely from a generic architecture description.

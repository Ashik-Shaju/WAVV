# Wavv — Architecture Decisions and Change Log
**Status:** Corrected and aligned with current model specifications  
**Project:** Wavv — Offline-first Android music player  
**Exhibition:** 16 October 2026  
**Developers:** Ashik and Bristo

## Document authority

This file records durable architecture and scope decisions.

Authority order:
1. A newer `D-###` decision in this file overrides an older decision when explicitly marked `Superseded`.
2. Model-specific specifications define the implementation details of their model.
3. `technical.md` defines application interfaces and runtime contracts.
4. `plan.md` defines execution order and priorities.

An AI coding agent must not silently change an accepted architecture. Experimental alternatives must remain isolated behind configuration until evaluated.

---

# A. Product and platform

## D-001 — Android-only product
**Status:** Accepted

Wavv targets Android only for the current college project.

**Reason:** keeps the scope feasible for the 16 October 2026 exhibition and permits focused Android optimization.

**Consequence:** do not build Windows/iOS support into the core architecture.

## D-002 — Kotlin + Jetpack Compose is the V1 UI stack
**Status:** Accepted

Wavv uses Kotlin as the primary application language and Jetpack Compose for Android UI. Figma is the design source; the implementation is hand-mapped into Compose rather than depending on React Native.

Kotlin/Android owns presentation, playback, background processing, model execution, storage/indexing, networking, cloud integration, and recommendation orchestration.

**Reason:** Wavv is Android-only and already depends heavily on native Android capabilities. Removing the JavaScript/native bridge reduces integration and debugging complexity.

## D-003 — Offline-first architecture
**Status:** Accepted

Core playback and AI functionality must work without internet access and without an AWS account.

Internet/cloud functionality is optional enrichment and account-backed persistence.

## D-004 — AWS is an optional V1 cloud layer, not a runtime requirement
**Status:** Accepted

Wavv includes a real but deliberately minimal AWS cloud component for: (1) optional account authentication, (2) backup/restore of selected Wavv state, (3) user-selected cloud music storage, and (4) controlled sharing of cloud-stored files.

The core app must remain fully usable offline. Cloud inference, cloud recommendation, P2P/WebRTC sharing, TURN/STUN infrastructure, and BitTorrent-style protocols are **not** V1 dependencies.

AWS is integrated with the Android app using current AWS Amplify Android libraries, with Amazon Cognito for authentication and Amazon S3 for object storage. Additional AWS services are added only when a concrete requirement exists.

---

# B. ML scope

## D-005 — ML inventory is component-based
**Status:** Accepted

Wavv has the following ML/data components:

1. **UHQ perceptual enhancement** — train/custom.
2. **Music Understanding** — efficient pretrained backbone + Wavv-specific heads.
3. **DCLAP audio semantic embedding** — pretrained.
4. **DCLAP-compatible text retrieval** — native CLAP text baseline; optional trained projection for multilingual alternatives.
5. **Singing-LID** — compact custom classifier.
6. **iALS recommendation** — local implicit-feedback recommender.
7. **Deterministic DSP metadata** — BPM, key, loudness, duration where available.

These are separate responsibilities. No component may silently replace another.

## D-006 — UHQ is perceptual enhancement, not audio super-resolution
**Status:** Accepted

UHQ improves perceived quality of lossy-compressed music. It does not claim exact recovery of discarded codec information or neural waveform super-resolution.

## D-007 — UHQ core scope is MP3 + AAC
**Status:** Accepted

**Required exhibition models:**
- UHQ-MP3
- UHQ-AAC

**Optional/stretch:**
- UHQ-Opus

Each codec has its own model weights and degradation matrix.

## D-008 — UHQ uses synthetic codec degradation and paired clean references
**Status:** Accepted

Training uses aligned clean/degraded pairs. MUSDB18-HQ `mixture.wav` is the primary controlled clean source; additional clean music is optional only when properly verified and useful.

The model predicts a bounded frequency-domain correction and leaves waveform reconstruction to DSP.

## D-009 — UHQ runs as background preprocessing + cache
**Status:** Accepted

The exhibition build does not require live neural UHQ processing. Enhanced files are generated in the background, cached, and played without overwriting the original.

---

# C. Music Understanding and embeddings

## D-010 — Music Understanding uses one efficient multi-task backbone
**Status:** Accepted

V1 uses **DyMN04-AS** as the primary backbone.

Initial policy:
- freeze the backbone;
- train Wavv-specific heads;
- use one shared backbone for genre, mood, instruments, vocal/instrumental, danceability and valence/arousal where labels support them.

Selective upper-layer fine-tuning is an experiment only after the frozen baseline.

## D-011 — MTG-Jamendo is the primary categorical Music Understanding source
**Status:** Superseded by D-043

Original rule: train categorical heads from curated split-0 training tags and reserve the human-validated annotations for evaluation only.

DEAM is the primary auxiliary source for continuous valence/arousal.

## D-012 — DCLAP is the dedicated audio semantic embedding
**Status:** Accepted

Use the selected AudioMuse-AI-DCLAP checkpoint as the V1 semantic audio embedding baseline.

Do not retrain DCLAP initially.

The stored song representation is the DCLAP audio embedding, not a DyMN intermediate feature.

## D-013 — DCLAP and Music Understanding remain separate
**Status:** Accepted

DCLAP:
- dense audio/text-aligned retrieval representation
- music-to-music similarity
- text-to-music retrieval

Music Understanding:
- interpretable attributes
- structured filtering
- ranking features
- UI explanations

Do not fuse DCLAP and DyMN vectors in V1.

## D-014 — Native DCLAP text path is the V1 text baseline
**Status:** Accepted

Use the compatible original CLAP text tower supplied by the selected DCLAP implementation as the baseline query encoder.

The baseline produces a DCLAP-compatible 512-D embedding.

Do not replace it with MiniLM/E5/GTE without a trained projection/alignment stage.

## D-015 — Alternative text encoders + projection are experimental
**Status:** Experimental

Potential alternatives:
- all-MiniLM-L6-v2
- multilingual-e5-small
- GTE multilingual

If tested, each alternative must use a trained projection/alignment into the DCLAP 512-D space and must be evaluated against the native text baseline.

This is **not required for V1**.

## D-016 — INT8 DCLAP quantization is optimization, not a new embedding space
**Status:** Accepted

Quantize audio and/or text ONNX models only after measuring:
- latency
- RAM
- model size
- cosine drift
- retrieval quality

Stored song embeddings remain FP32 initially.

## D-017 — Brute-force 512-D cosine search first
**Status:** Accepted

Start with normalized 512-D vectors and brute-force cosine similarity. Introduce ANN infrastructure only if target-device profiling proves it necessary.

---

# D. Singing Language Identification

## D-018 — Singing-LID is a compact supporting model
**Status:** Accepted

Singing-LID is a background indexing model, not a fourth giant foundation model.

## D-019 — CNN-only is the V1 Singing-LID architecture
**Status:** Accepted

Start with a compact CNN on 80-bin log-mel spectrograms.

Target:
- 16 kHz mono
- 10-second input
- 80 mel bins
- ~1–3M parameters preferred
- <5M practical ceiling

A small TCN is only an ablation if CNN-only temporal modeling is inadequate.

## D-020 — Singing-LID core classes
**Status:** Accepted

Production classes:
- English
- Hindi
- Malayalam
- Tamil
- Telugu
- Kannada

`Unknown` is an abstention outcome, not a learned mandatory class.

The former `Other` production class is removed from V1.

## D-021 — Singing-LID core datasets
**Status:** Accepted

V1 core stack:
1. Wavv permitted modern-song corpus — primary deployment-domain source.
2. Indian Regional Music Dataset — Indian-language anchor.
3. GTSinger — singing diversity + English coverage.

DALI/Slingua are not core V1 dependencies.

## D-022 — Singing-LID teacher/distillation is fallback only
**Status:** Experimental

A larger teacher, WavLM-class model, or phonotactic CRNN/CTC system may be evaluated only if the compact CNN fails Wavv acceptance gates after data/augmentation improvements.

Teacher inference is never a deployment dependency.

## D-023 — Singing-LID must use artist/song-disjoint evaluation
**Status:** Accepted

Never randomly split windows across train/validation/test. Split by song, and by artist where the dataset allows it, before creating crops.

## D-024 — Singing-LID uses song-level aggregation
**Status:** Accepted

Inference uses 10-second windows with 5-second stride, then aggregates valid window probabilities into a song-level prediction.

Confidence thresholds are calibrated on validation data only.

---

# E. Recommendation and behavioral data

## D-025 — iALS is the core collaborative model
**Status:** Accepted

Use implicit-feedback ALS/iALS for long-term user-item preference.

iALS does not perform audio understanding, language detection, semantic search, or short-term session reasoning.

## D-026 — Approved behavioral dataset stack
**Status:** Accepted

Core public foundation:
- **30Music** — listening, positive preference, playlist/session context.
- **MSSD** — skip/pause/seek/session/playback-context behavior.
- **Yambda-50M** — listen, played ratio, like/dislike/reversal, temporal and organic/recommendation context.

**Wavv-native event log** is mandatory for signals unavailable in those datasets.

Optional:
- Deezer cold-start dataset for targeted research only.

Do not make Yambda-5B a development dependency.

## D-027 — Do not fabricate missing behavioral signals
**Status:** Accepted

Dataset adapters must preserve real source semantics. Missing Wavv events such as `search_select`, `manual_select`, or local playlist actions are collected by Wavv itself.

## D-028 — Long-term preference and session context are separate
**Status:** Accepted

Long-term signals feed iALS after configurable event-to-preference mapping.

Short-term signals such as recent session, time of day, context switch, pause and seek primarily feed the ranking/session layer.

Do not force every event into iALS.

## D-029 — Recommendation is hybrid
**Status:** Accepted

Final ranking combines, as applicable:
- DCLAP/content similarity
- Music Understanding attributes
- Singing-LID language match
- iALS personalization
- current-session context
- diversity/novelty
- repetition penalties

## D-030 — No neural next-song model initially
**Status:** Accepted

Do not add a transformer/RNN/neural reranker until the hybrid baseline is measured and a concrete failure mode justifies it.

---

# F. Cloud computing and account

## D-031 — Wavv Account is optional and primarily supports backup/restore
**Status:** Accepted

A user may use Wavv without creating an account. An authenticated account allows selected persistent Wavv state to be backed up to AWS and restored after app deletion/reinstallation or migration to another device.

Backup scope includes, as applicable:
- playlists
- favorites
- listening history
- recently played state
- settings/preferences
- recommendation-related user state
- selected persistent song-analysis metadata when a stable song identity is available

Temporary caches, intermediate UHQ files, ML tensors, logs, and other disposable processing artifacts are not backed up.

## D-032 — User-selected Cloud Music Library uses Amazon S3
**Status:** Accepted

Local music remains local by default. The user can explicitly upload selected music files to Wavv Cloud. Cloud files remain associated with the user's account and can be downloaded/restored after reinstall or on another device.

S3 is the storage layer for these objects. Cloud storage is not required for local playback.

## D-033 — Cloud sharing is file-based, not P2P
**Status:** Accepted

V1 sharing uses AWS-hosted cloud objects rather than WebRTC/P2P or torrent-style distribution. A user can opt to share a cloud-uploaded file using controlled access.

Initial V1 sharing modes:
- private: only the owner
- shared/link: owner generates a controlled share access for another Wavv user or recipient
- public/discoverable: optional stretch mode only if a simple and safe catalog is implemented

Do not build WebRTC, STUN/TURN, signaling infrastructure, DHT, swarm management, piece scheduling, or multi-peer transfers in the core exhibition path.

## D-034 — AWS configuration must remain minimal
**Status:** Accepted

The preferred V1 AWS stack is:
- AWS Amplify Android integration
- Amazon Cognito for authentication
- Amazon S3 for cloud files and backup objects

A small server-side function may be introduced only if required to create controlled share access or perform another clearly justified backend operation. API Gateway, DynamoDB, EC2, Lambda, or other services are **not automatic requirements**.

Use AWS managed services and Amplify abstractions wherever they reduce configuration and security risk. Do not build custom cloud infrastructure merely to demonstrate AWS.

## D-035 — No raw local-library auto-upload
**Status:** Accepted

Wavv must never silently upload a user's entire local music library. Cloud upload is an explicit user action for selected files.

## D-036 — Cloud access must be authenticated/controlled
**Status:** Accepted

Cloud objects remain protected by AWS access controls. Time-limited sharing mechanisms such as S3 presigned URLs may be used when a file needs to be shared without exposing long-lived AWS credentials.

The app must not embed permanent AWS access keys.

## D-037 — Cloud is additive; no cloud-only model path in V1
**Status:** Accepted

DCLAP, Music Understanding, Singing-LID, UHQ, semantic retrieval and iALS remain local/on-device or locally cached as specified. AWS is not used as a mandatory inference backend or recommender backend in V1.

## G. Data, deployment, and engineering

## D-038 — Background indexing + caching
**Status:** Accepted

DCLAP, Music Understanding, Singing-LID and UHQ run during library/background analysis as applicable. Cached results are reused until the source file or relevant model version changes.

## D-039 — Android deployment is measured, not assumed
**Status:** Accepted

Model export should prefer ONNX where practical. CPU is the baseline. NNAPI/QNN/other acceleration is optional and must be benchmarked on the target device.

## D-040 — Dataset provenance is mandatory
**Status:** Accepted

Every dataset manifest records:
- source
- version/access date
- license/usage notes
- subset
- preprocessing
- split definition
- label mapping
- checksums where practical

## D-041 — Every deployed model needs an operational contract
**Status:** Accepted

A model is not complete after training. Required:
- reproducible preprocessing
- saved metrics
- model/version metadata
- exported artifact
- size
- Android latency/RAM measurement
- known failure cases
- stable input/output contract

## D-042 — Model-specific specifications are authoritative for model internals
**Status:** Accepted

The detailed model documents define datasets, preprocessing, training stages, architecture boundaries, evaluation, export and acceptance gates.

Project-level files must not contradict them.

The currently reviewed specifications are:
- Singing-LID specification
- Music Understanding + embedding integration specification
- UHQ enhancement specification
- DCLAP quantization/text architecture specification
- recommendation/behavioral dataset specification

## D-043 — Use artist-held-out human labels for covered tasks
**Status:** Accepted

For the CPU-constrained Music Understanding baseline, use the exact hash-verified pinned MTG annotation TSV and record its support-count differences from the README as upstream documentation drift. Select only taxonomies with clear label semantics and usable class/artist support. Build a track- and artist-disjoint fit/validation/test split from the annotated split-0 test IDs; tracks used for fitting or tuning are no longer part of the original split-0 test claim. Do not combine numbered MTG partitions.

Prefer human consensus labels for covered tasks. Reuse the same selected audio IDs' MTG uploader tags for broader weak-tag tasks, including instruments, so one audio download serves both. Start with a few-hundred-track audio/CPU timing pilot. Then grow the training partition only as needed, with validation-only data scaling until the smallest subset passing the per-task support and validation gates is reached; lock final test artists throughout. Do not claim a fixed sample cap yields validated quality. Use mono `audio-low` and the frozen DyMN04-AS feature path first; do not fine-tune the backbone.

**Reproducible comparison:** with the same track/artist split, audio encoding, deterministic 10-second window policy, frozen pooled features, and linear-head baseline, record per-taxonomy metrics and support at successive training-set sizes. Keep human and uploader-tag scores separate because their label definitions differ. Compare each task with its prevalence/majority baseline; expand only where validation or class support fails. Freeze the TSV-derived label contract and artist-grouped manifests before downloading beyond the timing pilot.

---

# H. Superseded decisions

## S-006 — React Native UI stack
**Status:** Superseded by D-002

React Native is removed from the V1 architecture. Wavv is implemented with Kotlin + Jetpack Compose.

## S-007 — No runtime cloud/account layer
**Status:** Superseded by D-004 and D-031–D-037

The app remains offline-first, but AWS is now a real optional V1 component for authentication, backup/restore, user-selected cloud music storage, and controlled sharing.

## S-008 — P2P/WebRTC sharing as V1
**Status:** Rejected for V1

The earlier idea of WebRTC/P2P/torrent-like music sharing is explicitly removed from the exhibition architecture to prevent unnecessary networking and AWS infrastructure complexity.


## S-001 — Former Singing-LID “Other” class
**Status:** Superseded by D-020

V1 no longer learns a mandatory `Other` production class. It uses six mandatory languages plus validation-calibrated `Unknown` abstention.

## S-002 — Former “teacher/student by default” Singing-LID
**Status:** Superseded by D-019 and D-022

CNN-only is the default. Teacher/distillation is a fallback experiment.

## S-003 — Former MP3-only UHQ core
**Status:** Superseded by D-007

MP3 and AAC are the required exhibition models. Opus is optional/stretch.

## S-004 — Former default multilingual text projection
**Status:** Superseded by D-014 and D-015

Native DCLAP-compatible CLAP text is the V1 baseline. Alternative multilingual encoders require trained alignment and remain experimental.

## S-005 — Former Last.fm/MSD recommendation development stack
**Status:** Superseded by D-026

The approved core is 30Music + MSSD + Yambda-50M + Wavv-native events.

---

# I. Coding-agent change rules

An AI coding agent may change an experimental choice only if it:

1. verifies the alternative against the actual repository/checkpoint;
2. runs or defines a reproducible comparison;
3. updates this file with a new decision ID;
4. updates `technical.md` and/or `plan.md` when contracts or roadmap change.

Never silently replace a model, checkpoint, dataset, label taxonomy, runtime, or loss.

When uncertain, preserve the current interface and isolate the experiment behind a configuration switch.

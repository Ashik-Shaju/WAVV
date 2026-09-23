# Wavv DCLAP Embedding Implementation Plan

**Date:** 2026-09-21  
**Status:** Research complete; Phases 0–2 implemented; workstation FP32/INT8 evaluation complete; Android device gate pending
**Scope:** Original DCLAP audio plus its compatible CLAP text tower, with dynamic per-channel INT8 text as the Android candidate

## Decision

Implement the released **AudioMuse-AI-DCLAP v1 audio model** and its compatible CLAP text tower through ONNX Runtime. Store normalized FP32 audio vectors and use the measured dynamic per-channel INT8 text model for Android text queries after the device feasibility gate passes.

Do not begin with quantization, ANN search, Music Understanding fusion, multilingual projection, or a replacement text encoder. Those are separate experiments and would make the first integration harder to validate.

The first useful vertical slice is:

```text
local Song URI
    -> Android PCM decoder
    -> 48 kHz mono 10-second windows
    -> DCLAP log-mel preprocessing
    -> DCLAP audio ONNX
    -> average window vectors + L2 normalize
    -> cached float32[512]
    -> brute-force cosine / music-to-music search
```

Text-to-music search remains part of the intended V1 architecture and now has a workstation reference/evaluation. It must still pass a separate model-size, memory, and latency gate on the target device.

## What the repository already decided

These are existing project decisions, not new architecture:

- DCLAP is the dedicated semantic audio embedding; Music Understanding remains a separate structured-output system. See [`Wavv_decisions.md`](Wavv_decisions.md#d-012--dclap-is-the-dedicated-audio-semantic-embedding) and [`Wavv_Music_Understanding_and_Embedding_Validated.md`](Wavv_Music_Understanding_and_Embedding_Validated.md).
- The baseline embedding is normalized FP32, 512-D; brute-force cosine comes before ANN. See [`Wavv_technical.md`](Wavv_technical.md#53-output) and [`Wavv_decisions.md`](Wavv_decisions.md#d-017--brute-force-512-d-cosine-search-first).
- Analysis belongs in background indexing and cached results are invalidated by source/model changes. See [`Wavv_technical.md`](Wavv_technical.md#2-song-lifecycle) and [`Wavv_plan.md`](Wavv_plan.md#12-model-specific-acceptance-gates).

Current implementation facts:

- [`app/build.gradle.kts`](../app/build.gradle.kts) pins ONNX Runtime Android and targets `minSdk = 26`.
- [`LibraryIndexing.kt`](../app/src/main/java/com/wavv/app/LibraryIndexing.kt) already owns WorkManager-based library indexing.
- [`WavvDatabase.kt`](../app/src/main/java/com/wavv/app/WavvDatabase.kt) stores embeddings as a separate Room artifact and keeps analysis state in `AnalysisJobEntity`.
- [`MainActivity.kt`](../app/src/main/java/com/wavv/app/MainActivity.kt) combines metadata search with DCLAP cosine results.
- The app contains the original FP32 audio model plus external data, the dynamic INT8 text model, and matching tokenizer assets under `app/src/main/assets/dclap/v1`; the model installer verifies their hashes before session creation.

## Evidence and implications

### 1. Pin DCLAP v1, not the moving `main` branch

The author-maintained DCLAP repository describes the released model as a distilled LAION CLAP **audio** tower, while the original LAION CLAP text tower is reused. The v1 release contains `model_epoch_36.onnx` and its required external-data file; it states that the audio output remains in a 512-D space. The release reports validation cosine and retrieval results, but those are maintainer-reported and are not an independent quality guarantee.

Sources: [DCLAP README](https://github.com/NeptuneHub/AudioMuse-AI-DCLAP), [v1 release](https://github.com/NeptuneHub/AudioMuse-AI-DCLAP/releases/tag/v1), [DCLAP configuration](https://raw.githubusercontent.com/NeptuneHub/AudioMuse-AI-DCLAP/main/student_clap/config.yaml).

**Plan:** record the exact release URL, commit/tag, file names, SHA-256 hashes, embedding dimension, and preprocessing values in a small model manifest. Never reconstruct the model from the current training code or silently update it.

### 2. The audio input contract is precise

The v1 reference uses mono 48 kHz audio, 10-second windows, 50% overlap, zero-padding for short audio, a final tail window, 128 mel bins, `n_fft = 2048`, STFT hop `480`, Hann window, `fmin = 0`, and `fmax = 14000`. Audio is clipped to `[-1, 1]`, converted to int16 and back to float32, then converted to log-mel. The ONNX input is `mel_spectrogram`; the reference output is a 512-D L2-normalized embedding.

Source: [DCLAP v1 README](https://raw.githubusercontent.com/NeptuneHub/AudioMuse-AI-DCLAP/main/README.md#L22-L32) and [reference code](https://raw.githubusercontent.com/NeptuneHub/AudioMuse-AI-DCLAP/main/README.md#L34-L118).

**Plan:** implement these values as model metadata/configuration, not scattered constants. Add a parity test against the reference implementation before connecting Room or UI.

### 3. Android runtime is viable, but must be measured

ONNX Runtime's mobile guidance names `onnxruntime-android` for Android and recommends starting with the full package, CPU for quantized models, and XNNPACK for non-quantized models. It explicitly requires measuring model size, binary size, latency, and power; NNAPI performance is device/model-specific.

Sources: [ONNX Runtime mobile deployment](https://onnxruntime.ai/docs/tutorials/mobile/), [ONNX Runtime Android build guidance](https://onnxruntime.ai/docs/build/android.html).

**Plan:** add a pinned `onnxruntime-android` dependency only after the model smoke test is defined. Start with CPU/XNNPACK; do not build a custom reduced runtime until the full package works and binary size is actually a problem.

### 4. Native Android media APIs are enough for PCM extraction

Android's `MediaExtractor` selects an audio track and reads encoded samples. `MediaCodec` decodes compressed audio to raw PCM frames, where samples are 16-bit signed integers or float PCM. This covers the app's existing `content://` and file-based media sources without introducing a new audio-decoding dependency.

Sources: [MediaExtractor API](https://developer.android.com/reference/android/media/MediaExtractor#readSampleData(java.nio.ByteBuffer,%20int)), [MediaCodec raw audio documentation](https://developer.android.com/reference/android/media/MediaCodec#RawAudioBuffers).

**Plan:** use `MediaExtractor` + `MediaCodec` in a cancellable `AudioPcmDecoder`. Resample and mono-fold in the preprocessing layer, then feed fixed windows to DCLAP. Keep decoding and DSP independent of ONNX Runtime so each can be tested separately.

### 5. Background execution matches the existing architecture

WorkManager supports long-running work, including local ML inference that can exceed ten minutes, with foreground notification support. It also supports storage, charging, battery, and idle constraints.

Sources: [WorkManager long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running), [WorkManager constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#work-constraints).

**Plan:** keep metadata discovery and DCLAP analysis as separately resumable jobs. A song remains playable when its embedding is pending or failed. Use the existing `AnalysisJobEntity` state pattern and add progress/cancellation handling at the song/window boundary.

### 6. Model delivery is the largest deployment risk

The DCLAP audio model is described as roughly 7M parameters and the AudioMuse-AI deployment source describes the audio artifact as about 20 MB plus external data. The same deployment source describes the paired CLAP text ONNX as roughly 478 MB; ONNX Runtime also requires an on-device model to fit storage and memory.

Sources: [DCLAP v1 release](https://github.com/NeptuneHub/AudioMuse-AI-DCLAP/releases/tag/v1), [AudioMuse-AI model download stage](https://raw.githubusercontent.com/NeptuneHub/AudioMuse-AI/main/Dockerfile#L127-L213), [ONNX Runtime mobile requirements](https://onnxruntime.ai/docs/tutorials/mobile/#obtain-a-model).

**Plan:**

1. Bundle the measured FP32 audio model and INT8 text model as separate identifiable offline assets.
2. Install both assets atomically into an app-private model directory before creating ORT sessions.
3. Keep text-session creation lazy because the text model is much larger than the audio model.
4. Do not substitute MiniLM, E5, GTE, or another text model. Equal dimensionality is not alignment.

### 7. Licensing must be checked before shipping artifacts

The DCLAP repository is AGPL-3.0. The maintainer's license notice separately identifies the DCLAP audio weights and the LAION CLAP text head; the upstream LAION repository is CC0. This is a distribution/compliance issue, not an implementation detail.

Sources: [DCLAP license](https://github.com/NeptuneHub/AudioMuse-AI-DCLAP/blob/main/LICENSE), [AudioMuse-AI license notice](https://raw.githubusercontent.com/NeptuneHub/AudioMuse-AI/main/LICENSE), [LAION CLAP license](https://github.com/LAION-AI/CLAP/blob/main/LICENSE).

**Plan:** before packaging model files, record the exact artifact licenses and notices for DCLAP, the paired text model, ONNX Runtime, tokenizer files, EfficientAT/EdgeNeXt-derived components, and model-training data. Obtain project-owner/legal approval for AGPL redistribution and source-notice obligations.

## Implementation phases

### Phase 0 — artifact and parity gate

No Android feature work yet.

1. Download DCLAP release v1 artifacts into a staging directory outside source control.
2. Generate a manifest with URLs, hashes, sizes, license references, ORT model input/output names, types, shapes, and opset.
3. Run the official/reference preprocessing on one short clip, one normal song, one mono file, and one multichannel file.
4. Build a small reference fixture: mel tensor, segment count, per-segment embedding, averaged embedding, final norm, and text/audio similarity where the text model is available.
5. Verify finite values, shape `(512,)`, norm approximately `1`, and deterministic repeat output.

**Gate:** do not touch Room or UI until the artifact loads and the reference output is reproducible.

### Phase 1 — Android audio embedding slice

Minimal app surface:

- `DclapModelInstaller`: copies/validates the FP32 audio plus external-data files and the INT8 text file into `filesDir`, then verifies hashes.
- `AudioPcmDecoder`: `content://` URI to cancellable PCM stream.
- `DclapPreprocessor`: mono/resample/window/log-mel conversion.
- `DclapAudioEncoder`: owns one ORT environment/session and exposes `encodeSong(uri): FloatArray`.

Keep the public contract small:

```kotlin
suspend fun encodeSong(uri: Uri): DclapEmbedding

data class DclapEmbedding(
    val values: FloatArray, // exactly 512, finite, L2-normalized
    val modelId: String,
)
```

Do not expose ORT tensors or raw audio buffers to Compose. Close the session/model resources and cancel cleanly between songs.

**Gate:** an instrumentation test on the target Android device completes one known clip, matches the fixture within an established parity tolerance, and reports cold/warm latency and peak memory.

### Phase 2 — cache and resumable indexing

Current implementation: Room v4 contains a separate `song_embeddings` artifact, the DCLAP worker skips valid source/model cache hits, records per-song failures, and is scheduled after metadata indexing. Text search loads the INT8 session lazily; a full-library indexing run remains a product-data test.

Add a separate embedding artifact, not columns on `songs`:

```text
song_embeddings
  song_id             primary key
  model_id
  source_size_bytes
  source_modified_at
  dimension           512
  dtype               float32
  normalized          true
  vector              2048-byte FP32 blob
  updated_at
```

Wire a DCLAP analysis job after metadata discovery:

1. Read the current `Song`.
2. Skip when the same `song_id`, source identity, and `model_id` already have a valid vector.
3. Decode and embed one song.
4. Write the vector atomically with completed status.
5. On cancellation, leave the job resumable and never publish a partial vector.
6. On decode/model failure, record a bounded error and continue with the next song.

Keep the existing metadata indexing behavior intact. Do not make playback depend on successful analysis.

### Phase 3 — brute-force retrieval

Implement the smallest vector store that fits the current app: load normalized FP32 vectors, compute dot products, sort descending, and map results back to `Song` records. Keep the vector artifact outside the main song row.

Benchmark at 500, 1,000, 5,000, and 10,000 songs, as already required by [`Wavv_technical.md`](Wavv_technical.md#17-local-storage). Add ANN or compressed vectors only if those measurements show a real device problem.

First user-visible result should be music-to-music similarity from an indexed song. It validates the complete audio-side path without depending on the 478 MB text model.

### Phase 4 — text query gate

Only after Phase 1–3 are stable:

1. The paired FP32 `clap_text_model.onnx` and matching tokenizer are staged in `dclap`; `dclap_evaluation.py quantize-text` produces the dynamic per-channel INT8 candidate.
2. Reproduce the exact tokenizer contract: byte-level Roberta vocabulary/merges, `input_ids` and `attention_mask`, `int64`, padding/truncation to 77 tokens.
3. The Android text encoder and asset installer are implemented against the INT8 candidate.
4. The workstation comparison is stored in [`dclap/evaluations/20260921T-workstation-full-gtzan`](../dclap/evaluations/20260921T-workstation-full-gtzan), covering cosine, MSE/L2, Recall@K, mAP, relative degradation, model size, session creation, RSS, and warm latency.
5. Run the device-only feasibility test: install/storage cost, model-copy time, session creation, peak RAM, warm query latency, repeated-query stability, and power.
6. Validate text-to-music ranking on-device against the stored desktop reference.

**Gate:** ship text-to-music on-device only if the model can be delivered and loaded reliably on the exhibition device. Otherwise, keep the audio embedding/index implementation and record text search as blocked by the model-delivery decision; do not silently change encoders.

## Acceptance checks

### Correctness

- Audio model and external data load from the same directory.
- Every published vector is exactly 512 FP32 values.
- All values are finite and final L2 norm is within the test tolerance of `1.0`.
- Windowing handles short clips, exact 10-second clips, long clips, and the tail window.
- A corrupt/unsupported file fails one analysis job without crashing the library or deleting a previous valid vector.
- Re-running the same source/model version is a cache hit and does not re-embed.
- Updating a file or model version invalidates only its stale embedding.

### Performance

Record, on the real exhibition device:

- APK/install and model storage size
- model copy and ORT session creation time
- cold and warm per-window latency
- full-song indexing time for representative durations
- peak Java/native memory
- battery impact for a small batch
- vector search latency at 500/1k/5k/10k songs

Do not invent an accuracy threshold before the FP32 reference exists. Establish parity first, then set a practical tolerance from the measured reference and retrieval behavior.

### Product behavior

- Metadata search continues to work before analysis completes.
- Music-to-music search reports when a song is not yet analyzed.
- Background analysis is cancellable, retryable, and resumable.
- The offline demo does not require live cloud inference.

## Explicitly deferred

- INT8 audio quantization; text INT8 is workstation-validated, but Android acceptance remains device-gated.
- ANN/vector compression; first benchmark brute-force search.
- Any text encoder other than the paired CLAP text tower.
- Multilingual text projection/alignment.
- DCLAP + Music Understanding vector fusion.
- Full Music Understanding integration; it remains a separate pipeline.
- Replacing native Android media decoding with a new decoder dependency.

## First implementation ticket

1. [x] Lock the v1 artifact manifest and license notes.
2. [x] Add a desktop/reference parity harness and fixtures in [`dclap`](../dclap/).
3. [x] Add the audio ORT Android smoke test and resumable embedding indexer.
4. [x] Add the compatible tokenizer and INT8 text ORT path; verify the workstation comparison.
5. Measure the target device.
6. Only if the text gate passes, wire text query retrieval/UI.

This order gives Wavv a working, testable embedding path before it takes on the text model's deployment risk.

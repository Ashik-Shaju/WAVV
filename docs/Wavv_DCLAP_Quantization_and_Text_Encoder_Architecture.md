# Wavv — Corrected DCLAP Quantization and Text-Encoder Architecture Specification

**Document type:** AI coding-agent implementation specification  
**Project:** Wavv  
**Audience:** ChatGPT Codex / coding agents / developers Ashik & Bristo  
**Priority:** Architecture and deployment guidance for semantic music retrieval  
**Status:** Corrected and validated baseline  
**Target hardware:** Intel Core i5-1235U CPU, 16 GB RAM  
**Exhibition target:** 16 October 2026

---

## 1. Purpose

This document fixes the earlier ambiguity around DCLAP text encoding, distillation, and quantization in Wavv.

Wavv uses DCLAP as the **semantic music embedding model** for text-to-music and music-to-music retrieval. The goal of this document is to define exactly what may be quantized, what must remain embedding-compatible, and what must **not** be assumed to already exist as a pretrained model.

The key rule is:

> **Do not replace the DCLAP text encoder with an arbitrary small/quantized text encoder unless a trained projection/alignment model has been built and validated.**

For the first implementation, prefer **post-training quantization of the existing DCLAP ONNX models** over introducing a new text-student training pipeline.

---

## 2. Corrected DCLAP Architecture

The selected AudioMuse-AI-DCLAP implementation is a dual-encoder retrieval system with two separate model branches:

```text
                    DCLAP
                      |
          +-----------+-----------+
          |                       |
          v                       v
   Audio encoder             Text encoder
   distilled student         original CLAP text tower
          |                       |
          v                       v
       512-D                    512-D
       vector                   vector
          |                       |
          +-----------+-----------+
                      |
              similarity search
```

### 2.1 Audio branch

- DCLAP distills the **audio branch** of CLAP into a much smaller student model.
- The released DCLAP audio encoder is approximately 7M parameters in the referenced implementation.
- The audio branch outputs a 512-dimensional normalized embedding.
- This embedding is the vector Wavv should store for each indexed song.

### 2.2 Text branch

- The referenced DCLAP implementation **does not provide a separately distilled text student**.
- It reuses the original LAION CLAP text encoder exported for inference.
- The text encoder outputs a 512-dimensional embedding compatible with DCLAP audio embeddings.
- Therefore the text side is larger than the distilled audio side.

### 2.3 Important correction

Do **not** document or implement DCLAP as:

```text
DCLAP = distilled audio + distilled text
```

That is incorrect for the selected pretrained implementation.

The correct description is:

```text
DCLAP = distilled audio encoder + original CLAP text encoder
```

---

## 3. Runtime

The selected DCLAP implementation is distributed as ONNX models and is intended to run through **ONNX Runtime**.

The architecture should therefore be implemented around:

```text
ONNX model(s)
      |
      v
ONNX Runtime
      |
      v
CPU inference on Wavv target hardware
```

Do not introduce a different runtime unless there is a measured and justified deployment benefit.

The exact execution-provider configuration must be detected and benchmarked on the actual development machine. CPU execution is the baseline because Wavv must remain practical on the i5-1235U.

---

## 4. Quantization: What Is Allowed

### 4.1 Quantize the DCLAP audio model

This is a valid optimization target.

```text
Official DCLAP audio ONNX
        |
        v
INT8 quantization experiment
        |
        v
Quantized ONNX audio encoder
        |
        v
512-D embedding
```

However, do not assume that INT8 preserves retrieval quality. It must be benchmarked.

### 4.2 Quantize the DCLAP text model

This is also a valid optimization target.

```text
Official DCLAP/CLAP text ONNX
        |
        v
INT8 quantization experiment
        |
        v
Quantized text encoder
        |
        v
512-D query embedding
```

The main reason to consider this is memory and CPU efficiency during interactive semantic search.

### 4.3 Do not initially quantize the stored embedding database

The initial Wavv implementation should keep stored 512-D song vectors in FP32 unless a later benchmark proves that lower-precision vector storage is safe.

Recommended pipeline:

```text
INT8 encoder
      |
      v
FP32 or FP16 512-D embedding
      |
      v
L2 normalization
      |
      v
stored/retrieval vector
```

The encoder's internal precision and the retrieval-vector storage precision are separate decisions.

---

## 5. Quantization Validation Protocol

Never replace the baseline model before measuring quality.

Create the following four evaluation configurations:

### A. Baseline

```text
FP32 audio + FP32 text
```

This is the reference accuracy and latency baseline.

### B. Audio-only quantized

```text
INT8 audio + FP32 text
```

Measure whether song embeddings change enough to hurt retrieval.

### C. Text-only quantized

```text
FP32 audio + INT8 text
```

Measure whether query embeddings change enough to hurt text-to-music retrieval.

### D. Both quantized

```text
INT8 audio + INT8 text
```

Only accept this configuration if retrieval quality and latency remain within the project acceptance thresholds.

For every configuration record:

- model size on disk
- RAM usage
- cold-start time
- inference latency per audio segment
- full-song indexing throughput
- query embedding latency
- cosine-similarity drift relative to FP32
- text-to-music Recall@K
- music-to-music retrieval quality

---

## 6. Do Not Replace DCLAP Text With an Arbitrary Small Model

The following is **not valid by itself**:

```text
English query
   -> MiniLM / E5 / BGE / other small encoder
   -> 384/768-D vector
   -> compare directly with DCLAP 512-D audio vectors
```

The new text model is not automatically aligned to the DCLAP audio space.

A replacement text model requires a compatibility/alignment stage, for example:

```text
small text encoder
      |
      v
trained projection / alignment network
      |
      v
512-D DCLAP-compatible space
      |
      v
DCLAP audio vectors
```

This additional training is a separate project and must not be silently introduced into the V1 implementation.

---

## 7. No Existing Ready-Made “Both-Distilled DCLAP” Assumption

The selected DCLAP project should be treated as **audio-distilled only**.

Do not claim that an available DCLAP checkpoint already contains:

```text
small distilled audio student
+
small distilled CLAP text student
```

There are related research directions involving multilingual encoders, projections, and cross-modal distillation, but they are not a drop-in replacement for the selected DCLAP checkpoint that can simply be downloaded and substituted without validation.

Therefore:

> **For Wavv V1, use the official DCLAP audio student and its compatible CLAP text encoder, then optimize them through measured quantization.**

---

## 8. Multilingual Text Is a Separate Decision

The native DCLAP text encoder is primarily English-oriented.

A multilingual DCLAP-derived approach can use:

```text
multilingual text encoder
        |
        v
trained projection
        |
        v
512-D DCLAP-compatible space
```

A known multilingual DCLAP project uses a multilingual GTE encoder followed by a small projection into the DCLAP 512-D space. This **is alignment/projection**, not text distillation of the original CLAP tower.

Wavv users currently search in English.

Therefore:

- Do not add a multilingual text encoder to V1 unless testing shows a real need.
- Do not add projection training merely because a smaller text encoder exists.
- Keep the native DCLAP text path as the baseline for English queries.
- Revisit multilingual projection only as a later feature if Wavv needs Malayalam, Tamil, Hindi, etc. query support.

---

## 9. Wavv Semantic Retrieval Architecture

### Song indexing

```text
Song audio
    |
    +----> DCLAP audio encoder
    |          |
    |          v
    |       512-D music embedding
    |
    +----> Music Understanding model
               |
               v
        structured music profile
        (genre, mood, instruments,
         vocals, danceability,
         valence/arousal, etc.)
```

Store at least:

```text
song_id
DCLAP 512-D embedding
music_profile
DSP metadata
other application metadata
```

### Text-to-music search

```text
User English query
        |
        v
DCLAP text encoder
        |
        v
512-D query embedding
        |
        v
similarity search against cached DCLAP song embeddings
        |
        v
candidate songs
        |
        v
optional Music Understanding attribute reranking/filtering
```

The full DCLAP audio model does **not** need to run again for every search. Song audio embeddings should be precomputed and cached during indexing.

---

## 10. Relationship With Wavv Music Understanding Model

DCLAP and the Music Understanding model must remain separate components.

### DCLAP

Purpose:

- dense semantic music representation
- text-to-music retrieval
- music-to-music retrieval
- nearest-neighbor similarity
- cross-modal audio/text alignment

### Music Understanding

Purpose:

- interpretable attributes
- genre
- mood
- instruments
- vocal/instrumental classification
- danceability
- valence/arousal
- structured filtering and reranking

Both models process the same song and will naturally learn some overlapping information. That is not functional duplication.

Do not replace DCLAP with Music Understanding features.

Do not fuse their vectors in V1.

Use:

```text
DCLAP = retrieval representation
Music Understanding = structured interpretation
```

A future fusion/reranking experiment may be added only after the basic system works and is benchmarked.

---

## 11. Implementation Requirements for the Coding Agent

### Required behavior

1. Locate and inspect the exact DCLAP model files used by the repository before implementing inference.
2. Do not hard-code tensor names, dimensions, sample rates, hop sizes, tokenizer limits, or preprocessing values without checking the actual model/export code.
3. Implement the original FP32 ONNX path first and make it the accuracy reference.
4. Add INT8 quantization as an **optional experimental path**, not as an immediate replacement.
5. Produce deterministic 512-D output vectors with the same normalization convention used by the baseline DCLAP implementation.
6. Cache song embeddings so interactive search does not repeatedly run the audio encoder.
7. Keep DCLAP embedding code isolated from Music Understanding code.
8. Keep model files, inference code, and vector storage independently replaceable.

### Suggested module boundaries

```text
models/
  dclap_embedding/
    audio/
    text/
    quantized/
    preprocessing/
    inference/
    benchmarks/

music_understanding/
  inference/
  profile/

retrieval/
  vector_store/
  similarity/
  reranking/
```

### Recommended runtime API

```python
encode_song(audio) -> np.ndarray  # shape: (512,)
encode_query(text) -> np.ndarray  # shape: (512,)
```

Both functions must document:

- input format
- preprocessing
- output dtype
- output shape
- normalization
- model version

---

## 12. Quantization Acceptance Criteria

The quantized model must not be accepted merely because it is smaller.

Minimum checks:

```text
Model size reduction        ✓
RAM reduction               ✓
CPU latency improvement     ✓
Embedding shape preserved   ✓
No NaN/Inf                  ✓
Cosine drift measured       ✓
Retrieval quality measured  ✓
```

Preferred acceptance logic:

```text
if memory/latency improves
AND retrieval degradation is small
AND no functional regressions:
    accept INT8
else:
    retain FP32 for that branch
```

Do not invent an arbitrary accuracy threshold before establishing the FP32 baseline. First measure the baseline and then define a practical tolerance from observed retrieval performance.

---

## 13. Recommended Wavv V1 Decision

**Lock the following architecture unless benchmarks show a problem:**

```text
DCLAP audio:
    distilled pretrained audio student
    -> ONNX Runtime
    -> FP32 baseline
    -> optional INT8 optimized build
    -> 512-D embedding

DCLAP text:
    original compatible CLAP text encoder
    -> ONNX Runtime
    -> FP32 baseline
    -> optional INT8 optimized build
    -> 512-D embedding

Stored song vectors:
    FP32 initially

Music Understanding:
    separate model and structured profile

Multilingual text:
    not part of V1 unless explicitly added later

Custom text distillation:
    deferred; only build if profiling shows the native text tower is a material deployment bottleneck
```

---

## 14. What the Coding Agent Must NOT Do

Do not:

- assume DCLAP already has a distilled text encoder
- replace the CLAP text encoder with MiniLM/E5/BGE/etc. without alignment training
- compare arbitrary text embeddings directly with DCLAP audio embeddings
- quantize and silently ship without retrieval benchmarking
- change the DCLAP 512-D space in V1
- use Music Understanding embeddings as a replacement for DCLAP embeddings
- re-run full-song DCLAP audio inference on every user search
- add multilingual projection training to V1 without an explicit requirement

---

## 15. External Validation Basis

This specification is based on verification of the selected DCLAP implementation and related DCLAP work.

Relevant evidence includes:

- **AudioMuse-AI-DCLAP (NeptuneHub):** DCLAP distills the audio branch and retains/reuses the CLAP text tower; the two branches operate in a common 512-D embedding space.
- **tinyCLAP:** also focuses on compact audio inference while using a separate text encoder rather than providing a fully distilled text+audio DCLAP replacement.
- **AudioMuse-AI-DCLAP-Multilingual:** demonstrates a multilingual text encoder plus a learned projection into the DCLAP 512-D space; this is a projection/alignment approach, not a distilled CLAP text student.

These references establish the architecture choices above. Exact implementation details must still be verified from the specific model checkpoint/repository selected by the Wavv team before coding.

---

## 16. Final Implementation Principle

The practical Wavv strategy is:

```text
KEEP THE DCLAP EMBEDDING SPACE STABLE.

OPTIMIZE THE EXISTING ENCODERS FIRST.

QUANTIZE ONLY AFTER MEASUREMENT.

ONLY TRAIN A NEW TEXT STUDENT IF PROFILING JUSTIFIES IT.
```

This minimizes engineering risk, preserves compatibility with the existing DCLAP audio index, and keeps the implementation feasible for the Wavv exhibition timeline.

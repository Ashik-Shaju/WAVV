# Wavv Singing Language Identification (Singing-LID) — Codex Implementation Specification

**Status:** Corrected implementation spec  
**Project:** Wavv  
**Target:** College exhibition — 16 October 2026  
**Model role:** Supporting audio-analysis model used during background library indexing  
**Primary deployment:** Android  
**Development hardware:** Intel Core i5-1235U, 16 GB RAM  
**Primary training acceleration:** Free/limited Colab or Kaggle GPU when needed

---

## 1. Objective

Build **one compact Singing Language Identification model** that identifies the dominant sung language of a song from its audio.

Mandatory deployment languages:

```text
English
Hindi
Malayalam
Tamil
Telugu
Kannada
```

The model must not depend on:

- filename
- album metadata
- artist name
- lyrics availability
- text language models

Those signals may be used elsewhere in Wavv, but Singing-LID must make its decision from audio.

### Output

The model returns:

```json
{
  "language": "Malayalam",
  "confidence": 0.92,
  "model_version": "slid-v1"
}
```

For low-confidence or unsupported audio:

```json
{
  "language": "Unknown",
  "confidence": 0.31,
  "model_version": "slid-v1"
}
```

`Unknown` is an **abstention decision**, not a mandatory learned catch-all class.

Do not create a large generic `Other` class in v1 unless a later experiment demonstrates that a curated additional-language class materially improves open-set rejection.

---

# 2. Final Architecture Decision

## 2.1 Start with CNN-only

The production candidate should begin as a **compact CNN-only classifier**.

Do **not** require a CNN + TCN architecture.

Reason:

- CNNs can learn local time-frequency patterns directly from the log-mel spectrogram.
- A CNN-only model is simpler to train.
- It is smaller and easier to quantize.
- It is easier to debug on the available hardware.
- It minimizes Android inference cost.
- Adding a TCN is only justified by an ablation showing that CNN-only temporal modeling is insufficient.

### Baseline architecture

```text
10 s mono audio
       │
       ▼
shared STFT preprocessing
       │
       ▼
80-bin log-mel spectrogram
       │
       ▼
lightweight CNN blocks
       │
       ▼
frequency/time compression
       │
       ▼
global or temporal-aware pooling
       │
       ▼
128-D embedding
       │
       ▼
dropout
       │
       ▼
6-class classifier
       │
       ▼
calibrated probabilities
```

Target model size:

```text
Preferred: ~1–3M parameters
Hard practical target: <5M parameters
```

Do not increase model size unless validation results justify it.

---

# 3. Optional Second-Stage Model

A **small TCN may be tested only as an ablation**:

```text
log-mel
  ↓
CNN feature extractor
  ↓
TCN
  ↓
pooling
  ↓
classifier
```

The TCN is accepted only if it gives a meaningful improvement in language-level metrics without an unacceptable increase in:

- parameter count
- inference time
- RAM
- training complexity

Do not make CNN+TCN the default merely for architectural complexity.

---

# 4. Why Not Start With a Large Teacher?

Do **not** make WavLM/Wav2Vec2/distillation a required first implementation.

The first implementation is intentionally **from scratch** because:

1. The task is narrow: six-language Singing-LID.
2. We need an efficient Android model.
3. The project deadline favors a simpler training pipeline.
4. A pretrained teacher increases engineering and training complexity.
5. We need to establish whether a compact singing-specific model is already sufficient.

A teacher/distillation experiment is a fallback only if the compact CNN fails the acceptance gates.

---

# 5. Audio Frontend

## 5.1 Important distinction

**STFT log spectrogram and log-mel spectrogram are not the same representation.**

The pipeline is:

```text
raw audio
   ↓
STFT
   ↓
magnitude/power spectrogram
   ├──► log-STFT/log-spectrogram
   │
   └──► mel filterbank
            ↓
         log-mel
```

Singing-LID uses **log-mel**, not the UHQ model's log-STFT representation.

## 5.2 Shared STFT

Wavv may share the underlying STFT computation between models where frontend parameters permit it.

However:

- do not feed the UHQ log-STFT directly to Singing-LID
- do not assume different models use identical STFT parameters
- make frontend configuration explicit

## 5.3 Initial input configuration

Use:

```text
mono audio
16 kHz sample rate
10-second input window
80 mel bins
log-mel representation
```

STFT parameters such as `n_fft`, `hop_length`, `win_length`, `f_min`, `f_max`, and normalization must be configurable rather than hard-coded in multiple places.

Exact STFT values are an experiment/configuration decision and must be recorded with each training run.

---

# 6. Windowing

## Training

Use **10-second crops**.

Do not generate every possible overlapping 10-second crop from every song for training; this can massively oversample long songs and cause dataset imbalance.

Instead:

1. identify usable vocal regions
2. sample 10-second crops from those regions
3. limit the number of training crops per song
4. balance languages and artists

## Inference

Use:

```text
window = 10 seconds
stride = 5 seconds
overlap = 50%
```

Example:

```text
0–10 s
5–15 s
10–20 s
15–25 s
...
```

This gives more phonetic context while still providing multiple observations across the song.

---

# 7. Vocal / Instrumental Handling

Do not assume every 10-second section of a song represents its language.

Songs contain:

- instrumental intros
- instrumental interludes
- outros
- spoken sections
- humming
- non-lexical vocals
- backing vocals
- multilingual sections

At minimum, the dataset pipeline must reject clearly non-vocal windows.

A lightweight vocal/activity filter should be used before creating the final Singing-LID training set.

A future version may use a dedicated vocal detector, but v1 does not require another model unless experiments show that simple filtering is insufficient.

The reference Singing-LID research also explicitly used vocal extraction and fixed-length segments, supporting the importance of vocal-focused input. [Renault et al., 2021, uploaded paper.]

---

# 8. Song-Level Prediction

Singing-LID is a song-level feature, even though inference operates on windows.

For a song:

```text
song
  ↓
10 s / 5 s stride windows
  ↓
CNN predictions
  ↓
quality/confidence filtering
  ↓
song-level aggregation
  ↓
language + confidence
```

Recommended initial aggregation:

```text
song_probability(language)
    = weighted mean of valid window probabilities
```

Weights should be based on window quality/confidence, not merely window count.

Do not allow a large number of low-quality instrumental windows to dominate the result.

---

# 9. Confidence and Unknown

The classifier always produces probabilities over the six production languages.

Then apply a calibrated abstention rule:

```text
max_probability >= threshold
        ↓
return predicted language

max_probability < threshold
        ↓
return Unknown
```

Potential later improvements:

- temperature scaling
- confidence margin
- entropy threshold
- validation-derived per-language thresholds

Thresholds must be learned only from validation data and frozen before final test evaluation.

Do not tune the threshold on the test set.

---

# 10. Core Dataset Strategy

The project intentionally uses a **minimal three-source dataset stack**.

## Dataset 1 — Wavv permitted modern-song corpus

### Role: PRIMARY

This is the most important dataset for the final model because it matches Wavv's actual deployment domain.

Target languages:

```text
English
Hindi
Malayalam
Tamil
Telugu
Kannada
```

Use known-language modern songs and construct a controlled dataset with:

```text
song_id
artist_id
language
source
audio_path
duration
quality/vocal metadata
```

The goal is not maximum raw hours. The goal is:

- sufficient artist diversity
- sufficient song diversity
- balanced six-language coverage
- contemporary music-domain coverage

This source should be the dominant data source for the final model.

---

## Dataset 2 — Indian Regional Music Dataset

### Role: INDIAN-LANGUAGE ANCHOR

Zenodo 5825830 contains approximately:

- 340 recordings
- 68 artists
- 17 languages
- 29.3 hours

and includes:

```text
Hindi
Malayalam
Tamil
Telugu
Kannada
```

This is valuable because it directly supports the five Indian target languages.

### Important limitation

The public release is based on **precomputed mel-spectrogram features**, not simply raw audio files matching Wavv's raw-audio frontend.

Therefore:

- use it as supplementary Indian-language supervision/validation
- do not let it define the production raw-audio preprocessing pipeline
- do not claim that its features are identical to Wavv's log-mel configuration

If raw audio can legitimately be obtained from the original source, it may be processed through Wavv's own frontend; otherwise keep the feature-level usage isolated and explicitly documented.

---

## Dataset 3 — GTSinger

### Role: GENERAL SINGING DIVERSITY + ENGLISH

GTSinger provides roughly 80.59 hours of singing, 20 professional singers and nine languages, including English.

It is useful for:

- English coverage
- singer diversity
- professional singing diversity
- acoustic/style robustness
- reducing overfitting to Wavv's own song collection

It does **not** solve the Indian-language coverage problem and must not be treated as a substitute for the Indian-language corpus.

---

# 11. Dataset Sources Deliberately Excluded From Core Training

Do not add datasets merely to increase dataset count.

The following are **not required for v1 core Singing-LID training**:

```text
DALI
Slingua
Acappella
SingStyle111
NHSS
Music4All
MIR-1K
MUSDB18
CCMixter
speech-only Indian-language datasets
```

They may be used for a later diagnostic or research experiment, but they are intentionally excluded from the core pipeline to reduce:

- preprocessing complexity
- balancing complexity
- duplicated data
- experiment count
- debugging burden
- training time

The uploaded Singing-LID paper uses DALI as a research benchmark and demonstrates that a stronger phonotactic architecture can work well, but that does not make DALI mandatory for Wavv's compact production model.

---

# 12. Dataset Balancing

Do not balance only by number of windows.

Balance at multiple levels:

```text
language
artist
song
```

Preferred training sampler:

```text
language-balanced
    +
artist-aware
    +
song-aware crop limits
```

Example failure:

```text
English: 500 hours
Malayalam: 20 hours
Tamil: 20 hours
...
```

A naive sampler would cause English to dominate.

Use weighted/balanced sampling so that all six production languages contribute meaningful training signal.

Do not artificially duplicate data many times just to make class counts equal. Prefer weighted sampling and controlled crop generation.

---

# 13. Anti-Leakage Requirements

This is mandatory.

Never split individual windows randomly when they come from the same song.

Correct order:

```text
1. group by artist/song
2. create train/validation/test groups
3. only then create windows
```

Preferred split:

```text
TRAIN artists
      ≠
VALIDATION artists
      ≠
TEST artists
```

At minimum:

- the same song must never cross splits
- adjacent windows from one song must never cross splits
- artist leakage should be prevented where the dataset has enough artists

The uploaded Singing-LID study explicitly used artist-aware splitting, reinforcing this requirement.

---

# 14. Multilingual Songs

V1 is a **single-label dominant-language classifier**.

Do not build a multi-label architecture initially.

For multilingual songs:

1. determine whether one language is dominant
2. aggregate singing windows
3. use confidence/ambiguity to produce `Unknown` when the evidence is insufficient

A future version can support multi-label language tags, but it is not required for the exhibition model.

---

# 15. Training Plan

## Phase A — Minimal CNN proof of concept

Use:

```text
Indian Regional
+
a small controlled Wavv subset
```

Goal:

- validate preprocessing
- validate labels
- validate CNN architecture
- verify six-class training
- verify no leakage

Do not optimize everything at once.

---

## Phase B — Full three-source training

Combine:

```text
Wavv modern songs
+
Indian Regional
+
GTSinger
```

Use language-balanced sampling.

---

## Phase C — Augmentation

Use realistic music/audio augmentation:

- gain variation
- mild additive noise
- mild EQ
- reverb
- codec/compression simulation
- time masking
- frequency masking

Do not use extreme pitch/time transformations by default because they can distort language-relevant singing characteristics.

Augmentation must be applied only after the baseline is working.

---

## Phase D — Calibration

After the model is finalized:

1. freeze the classifier
2. calibrate on validation data
3. determine Unknown threshold
4. freeze thresholds
5. evaluate once on the untouched test set

---

# 16. Loss

Start with:

```text
weighted cross entropy
```

where weights compensate for language imbalance.

Do not introduce distillation loss, CTC loss, or multi-task phoneme loss in v1.

---

# 17. Optional Teacher / Distillation Fallback

Only run this experiment if CNN-only fails acceptance gates.

Candidate:

```text
strong pretrained audio/speech teacher
                ↓
       teacher probabilities/features
                ↓
        compact CNN student
                ↓
             INT8
```

Possible objective:

```text
L = supervised cross entropy
    + α × distillation KL
```

Temperature and `α` must be configurable.

The teacher is **not a deployment dependency**.

---

# 18. Optional Phonotactic Fallback

If CNN-only remains weak after dataset and augmentation improvements, investigate a more structured architecture inspired by the uploaded Renault et al. Singing-LID paper:

```text
audio
 ↓
CRNN acoustic model
 ↓
CTC phoneme posteriorgram
 ↓
language classifier
```

The paper reports that the phonotactic approaches substantially outperformed a naive end-to-end baseline on its DALI closed-set benchmark, with its joint system reaching about 91.7% balanced accuracy. However, those results are for a different dataset, language set, and architecture and must **not** be treated as a Wavv performance expectation.

This is a fallback research path, not the v1 deployment architecture.

---

# 19. Metrics

Every experiment must report:

```text
segment accuracy
song-level accuracy
macro F1
per-language precision
per-language recall
per-language F1
balanced accuracy
confusion matrix
Unknown/abstention rate
selective accuracy after abstention
```

For Wavv, prioritize:

1. song-level macro F1
2. per-language F1
3. balanced accuracy
4. confidence/Unknown behavior

Raw accuracy alone is insufficient.

---

# 20. Required Evaluation

The final test report must explicitly include:

```text
English
Hindi
Malayalam
Tamil
Telugu
Kannada
```

The report must include a per-language confusion matrix.

Do not claim additional language support merely because a dataset contains additional languages.

A language is production-supported only when it passes Wavv's own test criteria.

---

# 21. Validation of Real-World Robustness

Create a separate held-out test set containing:

- artists unseen during training
- songs unseen during training
- different genres where possible
- different recording quality
- different accompaniment density
- modern commercial-style music
- quieter and louder vocal mixes

This set is more important for Wavv than achieving a strong score on a single public dataset.

---

# 22. Hardware Constraints

## Development CPU

Intel i5-1235U + 16 GB RAM:

Suitable for:

- feature extraction
- dataset preparation
- small CNN experiments
- evaluation
- quantization
- model export
- CPU inference benchmarking

Not suitable for large-scale teacher training.

## Free GPU

Use Colab/Kaggle GPU for:

- full training
- augmentation-heavy training
- hyperparameter experiments
- optional teacher/distillation experiments

Use cached features where appropriate to avoid repeatedly computing expensive preprocessing.

---

# 23. Android Deployment

The model must be exported to a mobile-friendly runtime.

Preferred pipeline:

```text
PyTorch training
      ↓
ONNX export
      ↓
INT8 quantization
      ↓
Android runtime
```

The final runtime can be changed if the rest of Wavv's Android stack makes another deployment format more practical.

The production model must be benchmarked for:

```text
model size
RAM
10-second window inference latency
full-song indexing latency
CPU usage
battery impact
```

Singing-LID runs during **background/library indexing**, not continuously during playback.

---

# 24. Wavv Integration

During song indexing:

```text
raw song
   │
   ├──► UHQ analysis
   │
   ├──► Music Understanding
   │
   └──► Singing-LID
             │
             ▼
       song language profile
```

Store:

```json
{
  "language": "Malayalam",
  "confidence": 0.92,
  "model_version": "slid-v1"
}
```

Use the cached result for:

- language-aware search
- playlist filtering
- automatic categorization
- recommendation/ranking features
- explanation UI

Do not run Singing-LID again on every search query if the song has already been indexed.

---

# 25. Repository Layout

Recommended:

```text
models/
  language_id/
    README.md
    config/
    src/
    checkpoints/
    export/

data/
  language_id/
    manifests/
    processed/
    splits/

experiments/
  language_id/
    <run_name>/
      config.yaml
      metrics.json
      summary.md
      confusion_matrix.png
```

Final deployment artifacts:

```text
models/language_id/
  model.onnx
  labels.json
  thresholds.json
  metadata.json
```

---

# 26. Required Metadata

Every training sample should retain:

```text
song_id
artist_id
language
dataset/source
split
start_time
duration
vocal_quality if available
```

Every model artifact should retain:

```text
model_version
sample_rate
STFT configuration
mel configuration
window_seconds
stride_seconds
label list
normalization
training dataset versions
validation metrics
quantization type
```

---

# 27. Experiment Order

Implement experiments in this exact order:

```text
1. Dataset manifests
2. Artist/song-disjoint splitting
3. Raw audio validation
4. STFT implementation
5. Log-mel frontend
6. 10-second crop generation
7. Vocal/activity filtering
8. Compact CNN baseline
9. Language-balanced sampling
10. Baseline evaluation
11. Data augmentation
12. Full three-source training
13. Confidence calibration
14. Unknown threshold
15. Android/ONNX export
16. INT8 quantization
17. Mobile benchmark
18. Optional TCN ablation
19. Optional teacher/distillation
20. Optional phonotactic fallback
```

Do not skip directly to teacher/distillation.

---

# 28. Acceptance Gates

The model is ready for integration only when all are true:

### Data integrity

- no song leakage
- no artist leakage where feasible
- label provenance is recorded
- language balance is controlled

### ML quality

- all six mandatory languages evaluated
- acceptable macro F1
- acceptable per-language F1
- confusion matrix reviewed
- song-level aggregation improves or remains stable relative to segment-level inference
- Unknown threshold reduces confidently wrong predictions

### Deployment

- model size is acceptable
- INT8 export works
- Android inference is stable
- background indexing latency is acceptable

Do not define a universal accuracy number before establishing a held-out Wavv test set. Performance targets must be based on actual measured difficulty.

---

# 29. What Codex Should Not Do

Do **not**:

- automatically add more datasets
- replace log-mel with log-STFT without an experiment
- add TCN automatically
- add WavLM/Wav2Vec2 automatically
- train one model per language
- split windows randomly across train/test
- create labels from filenames without verification
- treat instrumental audio as confidently labeled singing
- force every song into one language
- tune thresholds on the test set
- claim support for languages not evaluated by Wavv
- optimize for benchmark accuracy at the expense of Android runtime

---

# 30. Final V1 Specification

```text
TASK
Singing Language Identification

INPUT
10-second mono audio

WINDOWING
10-second window
5-second stride
50% overlap at inference

FRONTEND
STFT → mel filterbank → log-mel
80 mel bins
16 kHz initial target

MODEL
Compact CNN
~1–3M preferred
<5M practical ceiling

CLASSES
English
Hindi
Malayalam
Tamil
Telugu
Kannada

UNKNOWN
Confidence-based abstention
Not a mandatory learned class

DATASETS
1. Wavv modern-song corpus — primary
2. Indian Regional Music Dataset — Indian-language anchor
3. GTSinger — singing diversity + English

TRAINING
From scratch
Weighted cross entropy
Language-balanced sampling
Artist/song-disjoint split

AGGREGATION
Confidence/quality-weighted song-level probability averaging

DEPLOYMENT
INT8
Android
Background indexing

FALLBACKS
1. CNN + small TCN ablation
2. teacher/distillation
3. phonotactic CRNN + CTC approach
```

---

## 31. Source/Verification Notes

The following decisions are grounded in the project files and the reviewed research material:

- The uploaded Wavv specification establishes Singing-LID as a separate supporting model, the need for artist/recording-aware splitting, song-level aggregation, Unknown/abstention behavior, and mobile constraints. [Project file: `04_singing_language_id.md`]
- The uploaded Renault et al. (2021) paper demonstrates that Singing-LID is distinct from speech LID and that phonotactic/CTC structure can materially improve singing-language recognition over a naive end-to-end baseline. Its reported 91.7% balanced accuracy applies to its own DALI five-language experiment and must not be transferred to Wavv as an expected result.
- The Indian Regional Music Dataset record reports 340 recordings, 68 artists, 17 languages and 29.3 hours, with the required Indian languages represented.
- GTSinger is retained because it provides substantial high-quality singing diversity and English coverage.
- The architecture choice of CNN-only first, followed by optional TCN ablation, is a Wavv engineering decision based on the project's device/deadline constraints; it is not claimed as a published requirement.

**Implementation principle:** prefer the smallest model and smallest dataset stack that reliably solves Wavv's six-language singing-language task on a leakage-free, artist-disjoint real-world evaluation set.

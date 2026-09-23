# Wavv Model Spec — UHQ Perceptual Audio Enhancement (Coding-Agent Specification)

**Status:** Core trainable model family
**Owner:** ML workstream
**Primary goal:** Improve the perceived quality of lossy-compressed music by learning a small, codec-specific, adaptive frequency-domain correction. This is **not** neural audio super-resolution and must not be implemented as full waveform generation.

## 1. Objective

Wavv UHQ should make compressed music sound more natural and detailed while preserving the original musical content, timing, pitch, stereo layout, and overall character.

The system must learn from paired examples:

```text
clean lossless reference
        ↓
codec compression
        ↓
compressed audio
        ↓
learned frequency-domain correction
        ↓
DSP reconstruction
        ↓
enhanced audio
```

The model is **not** expected to recover every piece of information removed by lossy compression. It should learn a plausible perceptual correction that improves the compressed signal without introducing obvious artifacts.

Do not describe this as:
- audio super-resolution
- lossless recovery
- recovery of all discarded codec information
- neural waveform regeneration

## 2. Model family

Use separate lightweight codec-specific models because MP3, AAC, and Opus have different compression behaviour.

### Core models

1. **UHQ-MP3** — required
2. **UHQ-AAC** — required
3. **UHQ-Opus** — optional/stretch only

The MP3 and AAC models are the main exhibition path. Opus must not become a dependency for the exhibition.

All models use the same general architecture and training strategy, but are trained independently with codec-specific data and weights.

## 3. File/container detection

Do **not** infer codec solely from filename extension.

The preprocessing and application layer must inspect the actual audio codec using media metadata.

Examples:

- MP3 model: actual MP3 audio, commonly `.mp3`
- AAC model: actual AAC audio, commonly stored in MP4-family containers such as `.m4a` / `.mp4a`
- Opus model: actual Opus audio, commonly stored in `.opus`, Ogg, or WebM containers

Containers and codecs are different concepts. For example, `.m4a` is a container and may contain AAC; `.ogg` or `.webm` may contain Opus but the application must verify the actual codec.

## 4. Clean/reference dataset

### Primary clean source: MUSDB18-HQ

Use MUSDB18-HQ as the primary controlled reference dataset.

For this UHQ project, use **only `mixture.wav` from each track**.

Do not use these MUSDB18-HQ stems for UHQ training:

```text
mixture.wav  → USE
 drums.wav   → DO NOT USE
 bass.wav    → DO NOT USE
 other.wav   → DO NOT USE
 vocals.wav  → DO NOT USE
```

The reason is that UHQ enhancement targets complete music playback, not source separation. The complete stereo mixture is the correct reference signal for this task.

MUSDB18-HQ provides 100 training tracks and 50 test tracks. Keep the designated test tracks held out for final evaluation.

### Additional lossless sources

Additional clean music may be added only when it is properly license-checked and useful for genre/style diversity.

Lossless WAV and FLAC sources can be mixed safely in the reference corpus, provided they are validated and normalized through the same preprocessing pipeline.

The file extension itself is not the important property; the reference should be genuinely lossless or otherwise verified as an appropriate clean source.

Before using a reference file, validate:

- sample rate
- channel count
- bit depth where available
- duration
- clipping/invalid samples
- decode success
- licensing/usage rights

Standardize the internal training representation to a consistent sample rate and channel layout. The initial target is **44.1 kHz stereo** to align with the primary MUSDB18-HQ reference set.

Do not silently upsample low-quality sources and treat them as high-resolution references.

## 5. Synthetic degradation strategy

Synthetic degradation is mandatory for the controlled UHQ training dataset.

Each clean reference is encoded separately for each supported codec and bitrate.

### MP3 — required

```text
128 kbps
160 kbps
192 kbps
256 kbps
320 kbps
```

### AAC — required

```text
96 kbps
128 kbps
192 kbps
256 kbps
320 kbps
```

### Opus — optional/stretch

```text
64 kbps
96 kbps
128 kbps
160 kbps
192 kbps
```

Do not force the same bitrate list across codecs. Equal nominal bitrate across different codecs does **not** mean equal perceptual degradation.

The 15 conditions above are the fixed project scope when Opus is enabled:

```text
MP3 : 128 / 160 / 192 / 256 / 320
AAC :  96 / 128 / 192 / 256 / 320
Opus:  64 /  96 / 128 / 160 / 192   [optional]
```

Do not add arbitrary extra bitrate conditions to the core dataset merely to increase dataset size.

## 6. Why the dataset is synthetic

The clean source and compressed version must be from the **same original audio segment**.

This creates an exact supervised relationship:

```text
clean reference segment
        ↕
corresponding compressed segment
```

The model therefore learns codec-induced changes rather than differences between unrelated songs.

## 7. Automated dataset-generation script

Create a deterministic preprocessing script, preferably:

```text
scripts/generate_uhq_dataset.py
```

The script must support:

- WAV and FLAC clean references
- source validation
- codec selection
- bitrate selection according to the codec matrix above
- reproducible encoding settings
- decoding compressed audio to PCM for analysis
- aligned segmentation
- STFT/log-magnitude generation
- dataset quality checks
- manifest creation
- optional analysis reports

### Required pipeline

```text
clean WAV / FLAC
      ↓
validate reference
      ↓
standardize to 44.1 kHz stereo
      ↓
encode MP3/AAC/(Opus optional)
      ↓
decode compressed file to PCM
      ↓
verify duration/channel/sample alignment
      ↓
create aligned fixed-duration crops
      ↓
calculate required STFT representation
      ↓
run quality checks
      ↓
write manifest
```

The script may additionally produce analysis outputs that describe how compression affected the audio, but those analysis-only features should not automatically become model inputs.

## 8. Dataset must remain lean

The training corpus will be relatively small. The goal is **controlled signal quality, not maximum file count or feature count**.

Keep only information that materially helps training, reconstruction, evaluation, or reproducibility.

Do not introduce unrelated augmentation such as:

- background noise
- random EQ curves
- random distortion
- arbitrary loudness changes
- unrelated synthetic artifacts
- extra codec families outside project scope

These can cause the model to learn to fix problems that are not caused by codec compression.

## 9. Training sample structure

Train on many short, aligned examples rather than loading complete songs into the model.

Recommended initial crop length:

```text
5–10 seconds
```

Each sample should conceptually contain:

```text
compressed segment      → neural model input
clean reference        → training target/reference
codec                  → metadata
bitrate                → metadata
```

The exact same source interval must be used for clean and compressed samples.

The entire dataset does not need to fit in RAM or VRAM. Use lazy loading or indexed datasets and mini-batches.

## 10. Dataset split

**Split by source song before creating training crops.**

Never allow different crops from the same song to appear in both training and validation/test sets.

Recommended structure:

```text
source songs
   ├── training songs
   │      └── training crops
   ├── validation songs
   │      └── validation crops
   └── held-out test songs
          └── final-evaluation crops
```

For MUSDB18-HQ, preserve the designated test tracks for final evaluation.

Do not train or tune the final model on those held-out test tracks.

## 11. Core UHQ model concept

The UHQ model should **not reconstruct the entire waveform**.

Instead it predicts a **bounded, adaptive, frequency-domain correction** for the compressed signal.

Core idea:

```text
compressed audio
      ↓
STFT
      ↓
log-magnitude
      ↓
codec-specific UHQ model
      ↓
bounded frequency correction / residual
      ↓
apply correction to compressed spectral magnitude
      ↓
DSP inverse-STFT reconstruction
      ↓
enhanced audio
```

The compressed signal remains the base signal. The model learns a targeted correction.

## 12. Model input/output

### Primary neural input

Use:

**compressed audio STFT log-magnitude**

This is the core model representation.

Do not make the initial model depend on a large collection of parallel inputs.

### Supporting data

The dataset pipeline should retain the corresponding:

- compressed PCM/waveform
- clean/reference PCM/waveform
- clean/reference STFT representation when needed for loss calculation
- codec metadata
- bitrate metadata

These are training/evaluation resources and are not automatically separate neural inputs.

### Codec and bitrate handling

The application selects the model using actual codec metadata:

```text
MP3 → UHQ-MP3
AAC → UHQ-AAC
Opus → UHQ-Opus (if enabled)
```

Do **not** require codec ID as a model input in the first version.

Do **not** require bitrate as a model input in the first version.

The model should learn from the spectral degradation itself while the dataset is balanced across the selected bitrate conditions.

## 13. Model output

The neural network outputs:

**a bounded, adaptive frequency-domain enhancement correction/residual.**

Conceptually:

```text
compressed log-magnitude
          +
predicted correction
          ↓
enhanced magnitude
```

The correction must be constrained so that the network cannot freely apply extreme spectral changes.

The model is not allowed to invent an unrestricted EQ curve or aggressively boost high frequencies.

## 14. Model architecture

Use a lightweight time-frequency residual network.

Recommended first architecture:

```text
STFT log-magnitude
        ↓
small convolutional stem
        ↓
4–8 lightweight residual blocks
        ↓
adaptive frequency-correction head
        ↓
bounded residual/correction
```

Start with a small channel width appropriate for eventual device deployment.

Do not start with:

- Transformer
- diffusion model
- large audio foundation model
- neural vocoder
- large generative waveform model
- neural super-resolution pipeline

The first model should be straightforward enough to smoke-test on the project CPU and train seriously on a free/low-cost GPU.

## 15. Frequency-correction behaviour

The correction must be **frequency-dependent and content-adaptive**.

It must learn:

- which frequency regions were perceptually degraded
- how much each region should change
- when a change should be near zero
- how the correction changes with musical content
- how to preserve unaffected regions

Do not implement a fixed treble boost or a universal EQ curve.

### Bounded correction

Use a bounded activation, scale, clipping-safe residual parameterization, or equivalent method so corrections remain controlled.

The intended behaviour is:

```text
mild degradation → small correction
stronger degradation → larger but still controlled correction
little useful degradation → near-zero correction
```

This is especially important for high-quality conditions such as MP3/AAC 320 kbps.

## 16. Phase and reconstruction

Do not create a separate neural phase-reconstruction model in the first version.

Use an appropriate DSP inverse-STFT pipeline and retain/use the compressed signal's phase information where appropriate.

The neural network should learn the frequency correction. Standard DSP should perform spectral-to-waveform reconstruction.

This separation is intentional because it:

- reduces training difficulty
- reduces model size
- reduces data requirements
- keeps runtime practical
- avoids turning UHQ into full neural audio generation

## 17. Loss function

Start with:

```text
L = w1 * spectral_loss
  + w2 * multi_resolution_STFT_loss
  + w3 * waveform_loss
  + w4 * artifact_penalty
```

### Spectral loss

Encourage the corrected spectrum to move toward the clean reference.

### Multi-resolution STFT loss

Compare enhanced and clean audio at multiple STFT resolutions to preserve both short-term detail and broader spectral structure.

### Waveform loss

Use waveform-domain error as an additional constraint on the reconstructed signal.

### Artifact penalty

Discourage excessive or unstable spectral changes.

The first implementation must remain simple. Do not introduce a large pretrained perceptual or audio-language model into the UHQ training loop unless controlled experiments show the lightweight baseline is insufficient.

## 18. Mel spectrogram policy

Mel spectrograms are **not required model inputs**.

They may be generated for:

- codec-compression analysis
- visualizations
- debugging
- exhibition demonstrations
- optional future controlled experiments

The core training representation remains STFT/log-magnitude because it preserves finer frequency information relevant to codec-induced degradation.

Do not increase the training dataset size and complexity by storing Mel features for every sample unless later evidence shows a clear benefit.

## 19. Training workflow

### Phase A — dataset smoke test

Use a very small number of songs first:

- ~10–20 source songs
- short crops
- 1–3 epochs
- one codec model at a time
- CPU allowed

Verify:

- encoding
- decoding
- alignment
- STFT pipeline
- model forward pass
- loss calculation
- training convergence
- reconstruction
- checkpoint save/load
- export

### Phase B — full training

Train the MP3 and AAC models independently using the full eligible clean training set and their corresponding degradation conditions.

Optional Opus training may begin only after the MP3/AAC pipeline is stable.

Preferred serious-training hardware:

- Google Colab GPU
- Kaggle GPU
- AWS or another GPU service when available

Cloud inference is not required.

## 20. Training scripts

Prefer a clean separation between dataset generation and model training.

Suggested structure:

```text
scripts/
  generate_uhq_dataset.py
  train_uhq_mp3.py
  train_uhq_aac.py
  train_uhq_opus.py       # optional
  evaluate_uhq.py
  export_uhq.py
```

Shared model code should be reusable where practical, for example:

```text
src/uhq/
  dataset.py
  features.py
  model.py
  losses.py
  reconstruction.py
  metrics.py
  config.py
```

Do not duplicate the entire architecture three times simply because the weights are different.

## 21. Evaluation

For each model, compare:

1. clean reference
2. compressed input
3. UHQ-enhanced output
4. optional conventional DSP baseline

Evaluate every selected bitrate separately.

### Required measurements

- multi-resolution STFT distance
- spectral convergence
- waveform error metrics
- peak/clipping safety
- loudness consistency checks
- artifact inspection
- fixed listening-set observations
- runtime
- model size
- output/cache size

Results must be reported separately for each codec.

Do not combine MP3 and AAC into one aggregate score that hides codec-specific behaviour.

## 22. Acceptance criteria

A codec model is accepted only when it provides a meaningful improvement over its corresponding compressed baseline without obvious artifacts.

Required checks:

- measurable objective improvement on held-out samples where appropriate
- no obvious ringing
- no obvious warbling
- no obvious clipping
- no major stereo-image damage
- no major pitch/tempo changes
- playable Android output
- acceptable background-processing runtime
- practical model size
- safe caching behaviour

If a model fails the listening/perceptual gate, do not solve the problem by immediately increasing model size. First inspect the correction range, loss weighting, reconstruction path, training data, and dataset quality.

## 23. Runtime/application flow

UHQ should be implemented as background enhancement with caching.

```text
User selects/plays file
        ↓
Detect actual codec
        ↓
Select matching UHQ model
        ↓
Check cache
        ↓
Cached?
 ┌──────┴──────┐
Yes           No
 ↓             ↓
play       background enhancement
               ↓
             cache
               ↓
             play
```

The original user file must never be overwritten.

Live neural enhancement is **not** an exhibition requirement. Background processing and cached results are the reliable exhibition path.

## 24. Export and deployment

Preferred flow:

```text
PyTorch training
      ↓
checkpoint selection
      ↓
ONNX export
      ↓
numerical parity test
      ↓
Android/device runtime
```

FP16 and/or INT8 optimization should be attempted only after numerical parity with the original model is verified.

## 25. Dataset manifest

Use a reproducible manifest such as:

```csv
id,source_path,degraded_path,target_path,codec,bitrate,split,start_sec,duration_sec,sample_rate,channels
```

Optional useful metadata may include encoder version/settings and dataset manifest version.

Do not store generated audio, checkpoints, or large derived datasets in git.

## 26. Required artifact structure

```text
experiments/uhq/
  mp3/<run>/
    config.yaml
    metrics.json
    summary.md
    checkpoints/best.pt

  aac/<run>/
    config.yaml
    metrics.json
    summary.md
    checkpoints/best.pt

  opus/<run>/                # optional
    config.yaml
    metrics.json
    summary.md
    checkpoints/best.pt

models/uhq/
  mp3/
    uhq_mp3.onnx
    metadata.json

  aac/
    uhq_aac.onnx
    metadata.json

  opus/                       # optional
    uhq_opus.onnx
    metadata.json
```

Each `metadata.json` must contain at minimum:

- model version
- codec
- supported bitrates
- sample rate
- STFT parameters
- normalization assumptions
- training dataset manifest ID
- model checksum

## 27. Core vs optional scope

### Core exhibition scope

- UHQ-MP3
- UHQ-AAC
- MP3 bitrates: 128/160/192/256/320 kbps
- AAC bitrates: 96/128/192/256/320 kbps
- lossless/reference WAV or FLAC preprocessing
- MUSDB18-HQ `mixture.wav` as the primary reference source
- controlled synthetic codec degradation
- STFT/log-magnitude model input
- bounded adaptive frequency correction
- DSP reconstruction
- objective + listening evaluation
- background processing and caching
- Android-compatible output

### Optional/stretch scope

- UHQ-Opus
- Opus bitrates: 64/96/128/160/192 kbps
- additional license-checked clean music for diversity
- deeper artifact/perceptual analysis
- further runtime quantization/optimization

Optional work must not delay or destabilize the MP3/AAC exhibition path.

## 28. Coding-agent implementation rules

The coding agent must:

1. Keep dataset generation deterministic and reproducible.
2. Keep clean/compressed pairs time-aligned.
3. Split train/validation/test by source song, never by crop.
4. Keep MP3, AAC, and optional Opus datasets logically separate.
5. Never infer codec solely from filename extension.
6. Never use MUSDB18-HQ `drums.wav`, `bass.wav`, `other.wav`, or `vocals.wav` for the core UHQ training path.
7. Never train on the held-out final test set.
8. Avoid unnecessary feature storage and augmentation.
9. Preserve the original compressed audio as the base signal; predict a correction rather than regenerate the whole waveform.
10. Keep the correction bounded and conservative.
11. Keep model architecture lightweight until objective/perceptual evidence justifies a change.
12. Log configuration, dataset manifest version, metrics, and checkpoint information for every experiment.
13. Make failures explicit rather than silently resampling, clipping, or substituting data.
14. Never overwrite user audio files.

## 29. Final design summary

```text
LOSSLESS CLEAN MUSIC
(WAV / FLAC)
        │
        ├───────────── primary reference: MUSDB18-HQ mixture.wav
        │
        ▼
CONTROLLED CODEC DEGRADATION
        │
        ├── MP3: 128 / 160 / 192 / 256 / 320
        │
        ├── AAC:  96 / 128 / 192 / 256 / 320
        │
        └── Opus: 64 / 96 / 128 / 160 / 192 [optional]
        │
        ▼
ALIGNED SEGMENTS
        │
        ▼
COMPRESSED STFT / LOG-MAGNITUDE
        │
        ▼
CODEC-SPECIFIC LIGHTWEIGHT MODEL
        │
        ▼
BOUNDED ADAPTIVE FREQUENCY CORRECTION
        │
        ▼
APPLY CORRECTION TO COMPRESSED SPECTRAL MAGNITUDE
        │
        ▼
DSP / INVERSE STFT
        │
        ▼
ENHANCED AUDIO
        │
        ▼
BACKGROUND CACHE → ANDROID PLAYBACK
```

The primary engineering goal is a **small, controlled, codec-specific perceptual enhancement system** that can be trained and evaluated reliably with the project's limited dataset and available compute, while remaining feasible for the Wavv exhibition.

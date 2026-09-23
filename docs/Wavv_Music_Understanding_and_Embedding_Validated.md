# Wavv Music Understanding + Embedding Integration Specification
## Coding-Agent Specification — Validated V1

**Project:** Wavv  
**Purpose:** Production-oriented implementation specification for the AI coding agent (Codex-class agent)  
**Exhibition:** 16 October 2026  
**Hardware target:** Intel Core i5-1235U CPU, 16 GB RAM  
**Primary developers:** Ashik and Bristo

---

# 1. Important Architecture Correction

This document supersedes the earlier Music Understanding wording that blurred together:

1. the **dedicated music embedding model (DCLAP)**,
2. the **text embedding side**, and
3. the **Music Understanding model (DyMN04-AS + Wavv task heads)**.

These are separate components and MUST remain logically separate in the implementation.

## Final responsibilities

### DCLAP
Creates the **audio/music embedding** used for semantic music retrieval.

### Text embedding
Creates the **text/query embedding** used for text-to-music retrieval.

### Music Understanding
Creates **structured musical understanding** such as genre, mood, instruments, vocal/instrumental, danceability, valence and arousal.

The Music Understanding model may later contribute additional structured signals to retrieval/ranking, but it must **not replace DCLAP's dedicated audio embedding** unless an explicit experiment proves that this is better.

---

# 2. Validated High-Level Architecture

```text
                              WAVV
                                │
                     ┌──────────┴──────────┐
                     │                     │
                     ▼                     ▼
               MUSIC INDEXING          USER QUERY
                     │                     │
             ┌───────┴───────┐             ▼
             │               │       Text Embedding
             ▼               ▼             │
           DCLAP       Music Understanding │
          Audio Tower    DyMN04-AS         │
             │               │             │
             ▼               ▼             │
       Music Embedding   Structured        │
                           Profile          │
             │               │              │
             └───────┬───────┘              │
                     │                      │
                     ▼                      ▼
               Search Index          Text Embedding
                     │                      │
                     └──────────┬───────────┘
                                ▼
                     Semantic Retrieval / Ranking
```

The core principle is:

> **DCLAP provides the dense cross-modal audio representation; Music Understanding provides explicit interpretable musical attributes.**

Do not create a second independent “Wavv music embedding” from DyMN unless a later experiment explicitly evaluates and justifies it.

---

# 3. Why DCLAP and Music Understanding Are Both Needed

The two systems solve different problems.

## DCLAP

DCLAP is an audio/text-aligned embedding system.

Its audio branch converts music into a dense vector suitable for:

- music-to-music similarity
- text-to-music retrieval
- semantic search
- nearest-neighbour retrieval
- similarity ranking

The underlying CLAP design explicitly produces representations for both audio and text in a shared space and supports audio-text similarity/retrieval.

For the DCLAP implementation used by Wavv, preserve the actual checkpoint/model definition and embedding dimension from the selected repository/checkpoint. Do not hard-code dimensions unless confirmed from the downloaded model.

## Music Understanding

Music Understanding answers questions such as:

- Is this rock?
- Is it energetic?
- Which instruments are present?
- Is it vocal or instrumental?
- How positive/negative is the emotion?
- How activated/energetic is the emotion?
- How danceable is it?

These outputs are explicit structured information and are useful for:

- filtering
- ranking
- recommendations
- playlist rules
- UI explanations
- metadata display

---

# 4. Critical Decision About “Using Music Understanding for the Embedding”

Do **not** assume that metadata must be embedded before it can be used in semantic search.

DCLAP already obtains a learned representation directly from the audio waveform and is designed to align audio and text representations.

Therefore:

```text
Song audio ──► DCLAP audio encoder ──► music embedding
Text query ──► DCLAP/text-compatible text encoder ──► text embedding
```

is the primary semantic-search path.

Music Understanding is an additional signal:

```text
Song audio ──► DyMN04-AS + heads ──► structured profile
```

The structured profile can be used for filtering/reranking.

### Optional future fusion

A later experiment may test:

```text
DCLAP embedding
       +
Music Understanding feature/profile
       ↓
fused retrieval/ranking representation
```

But this is **NOT V1**.

Reason:
- DCLAP already contains broad audio semantics.
- The benefit of adding a second learned representation has not yet been measured.
- Fusion adds complexity, storage and indexing cost.
- We should establish the simplest working baseline first.

---

# 5. Music Understanding Model

## 5.1 Backbone

Primary backbone:

**DyMN04-AS**

Use the pretrained EfficientAT/DyMN checkpoint.

Known reference characteristics:

- approximately 1.97M parameters
- approximately 0.12B MACs per 10-second input
- AudioSet mAP approximately 45.0

Larger DyMN variants exist, but V1 uses DyMN04-AS because the project prioritizes mobile/laptop feasibility.

## 5.2 Backbone training policy

### Initial V1

**Freeze the DyMN04-AS backbone.**

Train only Wavv-specific task heads.

```text
Pretrained DyMN04-AS
        │
      FROZEN
        │
        ▼
Wavv intermediate representation
        │
        ├── Genre head
        ├── Mood head
        ├── Instrument head
        ├── Vocal/Instrumental head
        ├── Danceability head
        └── Valence/Arousal head
```

### Optional later fine-tuning

Only after frozen-backbone evaluation:

1. unfreeze upper layers
2. use a smaller learning rate
3. compare against frozen baseline
4. keep fine-tuning only if it provides a meaningful validated gain

Do not start with full end-to-end fine-tuning.

---

# 6. Do Not Use AudioSet Logits as Wavv Metadata

DyMN04-AS is pretrained for AudioSet.

Its original 527-class AudioSet classification output is **not** Wavv's music-understanding output.

Do not treat classes such as:

- train
- door
- railway
- speech

as Wavv music categories.

Instead, extract a suitable intermediate representation from the DyMN model and train Wavv-specific heads on top of it.

The exact feature layer must be verified against the actual DyMN implementation before coding the extractor.

---

# 7. Evidence for Using Intermediate Representations

The supplied low-complexity audio-embedding research supports the general strategy of:

```text
pretrained audio representation
       ↓
lightweight task-specific heads
```

The paper reports that AudioSet logits generalize poorly compared with intermediate representations, while mid-level representations generalize better. It also reports that adding low-level/pitch information improves music-related tasks.

The paper additionally demonstrates scene embeddings by processing 10-second frames and averaging their extracted embeddings.

Important limitation:

**This evidence was obtained with EfficientAT MobileNet models, not specifically with DyMN04-AS.**

Therefore the coding agent must benchmark candidate intermediate DyMN features rather than assuming the exact feature-selection formula from that paper.

---

# 8. V1 Music Understanding Outputs

The production profile should expose structured outputs approximately as:

```json
{
  "genre": [
    {"label": "rock", "score": 0.82}
  ],
  "mood": [
    {"label": "happy", "score": 0.79}
  ],
  "instruments": [
    {"label": "electric_guitar", "score": 0.91}
  ],
  "voice_instrumental": {
    "vocal": 0.97
  },
  "danceability": 0.71,
  "emotion": {
    "valence": 0.63,
    "arousal": 0.81
  },
  "model_version": "mu-v1"
}
```

Do not force labels that are unsupported by training annotations.

---

# 9. Primary Training Datasets

## 9.1 MTG-Jamendo

Use the official MTG-Jamendo training data as the primary source of categorical/music-attribute training targets. Treat the original user-provided tags as weak labels: curate mappings and report their coverage and noise rather than treating every tag as a clean class.

The dataset contains more than 55,000 tracks and a large tag vocabulary spanning:

- genre
- instruments
- mood/theme

The official processed dataset contains 55,525 tracks and multiple subsets/taxonomies.

Do not blindly train on every original uploader tag as a single flat ontology.

## 9.2 Human-validated MTG-Jamendo classification annotations

Reserve the released human-validated Music Classification Annotations for independent evaluation. The released annotations cover MTG-Jamendo split-0 test tracks, so using them to fit a head would spend the held-out benchmark. The numbered official MTG-Jamendo splits are alternative randomized partitions, not independent folds; a split-0 test track can be in another split's training list. Use split 0 consistently for the categorical baseline and never combine numbered partitions. The clean release has four genre taxonomies with 411–612 unanimous examples each, six separate binary mood attributes with 5,678–7,686 examples each, 4,476 danceability labels, and 2,070 voice/instrumental labels. It does not supply a human-validated instrument taxonomy.

Use these annotations only for the task definitions and tracks they actually cover. Keep all split-0 test tracks out of fitting and tuning, and do not report this set as an independent evaluation after using any of its labels during model selection. Confirm dataset version, track IDs, label definitions, and split membership in the dataset manifest before training.

---

# 10. DEAM

DEAM is the primary source for continuous emotional targets:

- valence
- arousal

The supplied DEAM manual states:

- more than 1,800 songs/excerpts
- 45-second excerpts plus full songs
- continuous valence/arousal annotations
- dynamic annotations resampled to 2 Hz
- averaged annotations and standard deviations
- continuous dynamic values in the -1 to +1 range
- dynamic arousal annotations are generally higher quality than dynamic valence
- 2015 evaluation-set valence is particularly reliable

Use DEAM to train/adapt the emotion head.

Do not build a completely separate large emotion network for V1.

---

# 11. Final Label Scope

Keep V1 compact and useful.

## Genre

Use a curated broad genre taxonomy.

Target:
- approximately 10–20 classes

Do not use all possible genre tags as separate classes.

## Mood

Use a compact, human-understandable taxonomy.

Target:
- approximately 8–12 classes

Use validated labels where possible.

Examples include:
- happy
- sad
- relaxed
- aggressive
- party
- electronic

Final classes must be selected according to actual annotation coverage and balance.

## Instruments

Use a selected list of sufficiently represented instruments.

Do not build an enormous ontology.

The exact instrument list must be generated from verified label counts.

OpenMIC is NOT a required V1 dependency.

Only add OpenMIC later if testing proves MTG instrument coverage/quality inadequate.

## Vocal/instrumental

Use the validated MTG-derived voice/instrumental annotation where available.

## Danceability

Use the available validated annotation and select the simplest appropriate target representation.

## Emotion

Predict continuous:

- valence
- arousal

---

# 12. DSP Features

Do not add neural models unnecessarily for deterministic audio features.

Calculate or extract:

- BPM / tempo
- key
- loudness
- duration

These are complementary to ML outputs.

They should be stored alongside the Music Understanding profile.

---

# 13. Entire-Song Analysis

Wavv analyzes complete songs during indexing.

Do NOT pass a 3–5 minute song as one giant tensor into DyMN04-AS.

Instead:

```text
Full song
   │
   ├── Window 1 ──► DyMN04-AS
   ├── Window 2 ──► DyMN04-AS
   ├── Window 3 ──► DyMN04-AS
   ├── ...
   └── Window N ──► DyMN04-AS
                         │
                         ▼
                 aggregate predictions
                         │
                         ▼
                  song-level profile
```

The exact window size/stride must follow the backbone input contract and be experimentally selected.

The paper supporting efficient audio embeddings uses 10-second frames for scene-level embeddings, but this should be treated as a reference point rather than an unchangeable Wavv requirement.

---

# 14. Whole-Song Aggregation

Initial V1 strategy:

- process multiple windows
- predict each window
- aggregate with simple robust pooling

For classification:

- average probabilities/logits
- optionally use median when appropriate

For regression:

- mean or median

For embeddings:

- DCLAP audio embeddings from multiple song windows can be aggregated into a song-level embedding if required by the DCLAP implementation.

Do not confuse:
- DCLAP song embedding
- DyMN intermediate representation
- Music Understanding prediction profile

They are different artifacts.

---

# 15. DCLAP Indexing

The semantic retrieval index should store the DCLAP-derived music embedding for each song.

Recommended conceptual record:

```json
{
  "track_id": "...",
  "dclap_embedding": "...",
  "music_understanding": {
    "genre": [],
    "mood": [],
    "instruments": [],
    "voice_instrumental": {},
    "danceability": 0.0,
    "emotion": {
      "valence": 0.0,
      "arousal": 0.0
    }
  },
  "dsp": {
    "bpm": 0.0,
    "key": "...",
    "loudness": 0.0,
    "duration": 0.0
  },
  "model_versions": {
    "dclap": "...",
    "music_understanding": "mu-v1"
  }
}
```

Exact vector serialization and ANN index format depend on the application implementation.

---

# 16. Text-to-Music Search

Primary path:

```text
User query
   ↓
Text encoder compatible with DCLAP space
   ↓
Text embedding
   ↓
Similarity against DCLAP song embeddings
   ↓
Candidate songs
   ↓
Optional Music Understanding filters/reranking
   ↓
Final results
```

Example:

```text
"energetic rock songs with electric guitar"
```

DCLAP handles broad semantic matching.

Music Understanding can provide explicit constraints/ranking signals:

```text
genre ≈ rock
arousal = high
instrument contains electric guitar
```

This is stronger than relying on only one mechanism.

---

# 17. Music-to-Music Search

For:

> “Find songs similar to this song”

Use:

```text
Reference song
   ↓
DCLAP audio embedding
   ↓
nearest-neighbour search
   ↓
candidate songs
   ↓
optional Music Understanding reranking
```

Music Understanding should not be required for the first retrieval stage.

---

# 18. Search Fusion Is Optional

A simple V1 retrieval system may use:

```text
DCLAP similarity
```

A later ranking stage can use:

```text
final_score =
    w1 * semantic_similarity
  + w2 * attribute_match
  + w3 * optional_application_signals
```

The weights must be configurable and learned/tuned from experiments, not hard-coded without evaluation.

---

# 19. Dataset Leakage Rules

The coding agent MUST:

- keep track-level separation between train/validation/test
- prevent windows from the same track appearing in different splits
- preserve official MTG-Jamendo splits where appropriate
- prevent duplicate/near-duplicate leakage where possible
- keep DEAM evaluation data separate from training
- never evaluate on samples whose labels were used to train the same head

---

# 20. Training Strategy

## Stage 0 — Pipeline smoke test

Use a small subset:

- approximately 1,000 tracks

Verify:

- loaders
- feature extraction
- labels
- losses
- metrics
- output schema
- checkpointing

## Stage 1 — Frozen backbone

- freeze DyMN04-AS
- train Wavv task heads
- evaluate all tasks

## Stage 2 — Improve data/labels

- check imbalance
- remove unusable labels
- refine taxonomy
- improve aggregation

## Stage 3 — Optional selective fine-tuning

Only if the frozen baseline is inadequate.

## Stage 4 — Deployment optimization

- ONNX/export path
- quantization where useful
- CPU inference benchmark
- Android integration
- background indexing/cache

---

# 21. DCLAP vs Music Understanding Compute

These components have separate runtime costs.

A song indexing pass conceptually performs:

```text
Audio
 │
 ├──► DCLAP audio embedding extraction
 │
 └──► DyMN04-AS multi-task understanding
```

Do not claim a single runtime number until both are benchmarked on the target hardware.

For Music Understanding, use:

- per-window latency
- full-song analysis time
- peak RAM
- number of windows

For DCLAP, separately measure:

- per-window/audio-file encoding time
- full-song embedding time
- peak RAM
- embedding storage size

Then measure combined indexing time.

---

# 22. Caching

Music analysis should primarily happen during **library indexing/background analysis**.

Do not run Music Understanding continuously during playback.

Cache:

- DCLAP song embedding
- Music Understanding profile
- DSP features
- model versions
- source audio identity/hash if needed

When a song has already been analyzed with the same model versions, reuse the cached results.

---

# 23. Hardware Constraints

Target:

**Intel i5-1235U + 16 GB RAM**

Development should work without requiring a dedicated GPU.

Optional GPU:

- Google Colab free GPU
- Kaggle free GPU
- AWS only as an optional service where needed

Use GPUs primarily for training, not as a hidden runtime dependency for the Android application.

---

# 24. Storage Rules

Do not download the full 500+ GB MTG-Jamendo audio collection unnecessarily.

Prefer:

- selected subsets
- lower-size variants where suitable
- precomputed mel features when compatible with the training pipeline
- cached embeddings

Keep raw data and generated training artifacts separate.

---

# 25. Model/Code Separation

Recommended project structure:

```text
models/
├── dclap/
│   ├── inference/
│   ├── config/
│   └── README.md
│
└── music_understanding/
    ├── data/
    ├── models/
    ├── training/
    ├── inference/
    ├── evaluation/
    └── README.md

search/
├── text_embedding/
├── vector_index/
└── ranking/

pipeline/
└── library_indexing/
```

The exact project structure may be adapted to the repository, but DCLAP and Music Understanding must remain separate modules.

---

# 26. Required Music Understanding Deliverables

```text
models/music_understanding/
    model.onnx
    labels.json
    metadata.json

experiments/music_understanding/<run>/
    config.yaml
    metrics.json
    summary.md
    checkpoints/
```

The model metadata must record:

- backbone version/checkpoint
- feature layer used
- taxonomy version
- dataset versions
- normalization/preprocessing
- model version

---

# 27. Required DCLAP Deliverables

```text
models/dclap/
    model/
    config/
    metadata.json
```

The index must record:

- DCLAP checkpoint/version
- embedding dimension
- preprocessing
- normalization
- song-level aggregation method

Do not assume an embedding dimension from memory. Read it from the actual selected checkpoint/config.

---

# 28. Acceptance Gates

## Music Understanding

Must demonstrate:

- useful genre performance
- useful mood performance
- useful instrument performance
- usable vocal/instrumental classification
- measurable valence/arousal prediction
- stable predictions across multiple crops
- acceptable full-song indexing time
- acceptable memory usage

## DCLAP

Must demonstrate:

- sensible music-to-music similarity
- useful text-to-music retrieval
- acceptable embedding extraction time
- stable song-level embeddings

## Combined Search

Must demonstrate:

- text query returns semantically relevant music
- music-to-music retrieval is meaningful
- structured attributes can improve filtering/ranking
- cached indexing avoids repeated expensive inference

---

# 29. Things the Coding Agent Must NOT Do

Do NOT:

1. Replace DCLAP with DyMN without an explicit experiment.
2. Treat the DyMN AudioSet 527-class logits as Wavv labels.
3. Automatically fine-tune the complete DyMN backbone.
4. Download the entire MTG-Jamendo audio collection without necessity.
5. Use every MTG-Jamendo tag as an unstructured label space.
6. Add OpenMIC/FMA automatically without a demonstrated need.
7. Create a separate neural model for BPM/key/loudness unless a later requirement explicitly demands it.
8. Run Music Understanding continuously during playback.
9. Recompute song embeddings if the cached model/version is unchanged.
10. Mix DCLAP embeddings and DyMN representations into one vector without an explicit experiment and evaluation.

---

# 30. Key Technical Clarification

There are three different concepts:

### A. DCLAP audio embedding
Dense learned vector representing the audio in the DCLAP audio-text space.

### B. DyMN intermediate representation
Internal feature representation used by the Music Understanding network to predict Wavv-specific attributes.

### C. Music Understanding profile
Human-readable/structured predictions such as genre, mood, instruments and emotion.

These must not be given the same name in code or documentation.

Recommended naming:

```python
dclap_embedding
mu_feature
music_profile
text_embedding
```

---

# 31. Final V1 Architecture — LOCKED

```text
                           WAVV LIBRARY INDEXING

                                Full Song
                                   │
                      ┌────────────┴────────────┐
                      │                         │
                      ▼                         ▼
                    DCLAP                  DyMN04-AS
                      │                    (frozen first)
                      │                         │
                      ▼                         ▼
              DCLAP Music Embedding       Wavv Heads
                      │                         │
                      │              ┌──────────┼───────────┐
                      │              ▼          ▼           ▼
                      │            Genre       Mood     Instruments
                      │              │          │           │
                      │              ├──────────┼───────────┤
                      │              ▼          ▼           ▼
                      │        Voice/Instr. Danceability Emotion
                      │                                      │
                      │                         Valence + Arousal
                      │
                      └──────────────┬───────────────────────┘
                                     │
                                     ▼
                              Cached song index
                                     │
                    ┌────────────────┴────────────────┐
                    │                                 │
                    ▼                                 ▼
              Vector retrieval                 Attribute filtering/
              and similarity                   ranking/recommendation


                           TEXT SEARCH

                     User natural-language query
                                │
                                ▼
                      Text embedding encoder
                                │
                                ▼
                     DCLAP-compatible space
                                │
                                ▼
                      DCLAP song embeddings
                                │
                                ▼
                        Candidate retrieval
                                │
                                ▼
                   Optional Music Understanding
                       attribute reranking
```

---

# 32. Final Decisions

| Component | V1 decision |
|---|---|
| Dedicated music embedding | **DCLAP** |
| Text embedding | **DCLAP-compatible text side / existing Wavv text embedding design** |
| Music Understanding backbone | **DyMN04-AS** |
| DyMN backbone training | **Frozen initially** |
| DyMN AudioSet logits | **Not used as Wavv outputs** |
| Genre | Curated MTG-Jamendo training tags; four human-validated split-0 taxonomies held out for evaluation |
| Mood | Curated MTG-Jamendo training tags; evaluate the six matching human-validated binary attributes separately |
| Instruments | MTG-Jamendo uploader tags as weak labels; no human-validated instrument taxonomy in this release |
| Vocal/instrumental | Train only from an explicitly mapped training label; 2,070 human-validated examples for final evaluation |
| Danceability | Train only from an explicitly mapped training label; 4,476 human-validated examples for final evaluation |
| Valence | DEAM |
| Arousal | DEAM |
| BPM | DSP |
| Key | DSP |
| Loudness | DSP |
| Extra OpenMIC | **Only if measured need appears** |
| Extra FMA | **Only if measured need appears** |
| DCLAP + DyMN fusion | **Not V1; experiment later** |
| Full-song analysis | **Yes, using windows + aggregation** |
| Continuous inference during playback | **No** |
| Main runtime point | **Library indexing/background analysis** |
| Cache results | **Yes** |

---

# 33. Source/Validation Notes

This specification is based on the currently available Wavv project materials and the following evidence.

### MTG-Jamendo labels and splits

The official [dataset documentation](https://github.com/MTG/mtg-jamendo-dataset), [split generator](https://github.com/MTG/mtg-jamendo-dataset/blob/master/scripts/data_split.py), and [human-annotation description](https://github.com/MTG/mtg-jamendo-dataset/blob/master/derived/music-classification-annotations/README.md) show that the human-validated labels are for split-0 test tracks and that numbered splits are alternative randomized partitions. These files overlap, so split-0 test IDs must not be drawn into training from another numbered split. The clean human labels cover selected genre, binary mood, danceability, and voice/instrumental tasks; they do not provide human-validated instrument labels. See the [implementation research note](research/Wavv-Music-Understanding-Implementation-Research.md) for support counts and example overlap evidence.

### Project Music Understanding document

The existing project specification defines Music Understanding as a multi-task model for genre, mood, instruments, tags and emotion, and places it in the library-analysis/search/recommendation workflow. It also specifies windowed full-song analysis, lightweight heads and caching. This new document preserves those useful elements while separating Music Understanding from DCLAP. 

### DyMN paper

The supplied DyMN paper establishes DyMN as an efficient pretrained audio model and reports downstream transfer experiments including musical instrument recognition. The paper also explains that DyMN is trained for 10-second AudioSet tagging and is designed to be computationally efficient. 

### Low-Complexity Audio Embedding Extractors paper

The supplied paper explicitly evaluates pretrained efficient audio networks as general-purpose embedding extractors, shows that intermediate features transfer better than AudioSet logits in broad downstream evaluation, and uses averaged 10-second frame embeddings for scene-level representations. These findings support testing intermediate DyMN representations, but they do not prove that the exact MobileNet feature combination is optimal for DyMN04-AS. 

### DEAM manual

The supplied DEAM manual confirms the valence/arousal dataset structure and quality notes used by this document.

### DCLAP / CLAP external verification

The CLAP ecosystem provides aligned audio and text representations for retrieval, and the AudioMuse-AI-DCLAP project describes a distilled CLAP audio tower retaining the CLAP embedding space for efficient text-to-music search. Use the exact Wavv-selected DCLAP repository/checkpoint as the implementation source of truth. Do not hard-code dimensions or preprocessing from a different CLAP variant.

---

# 34. Implementation Rule

When a choice is not explicitly locked above, the coding agent must:

1. inspect the actual selected model/repository,
2. avoid assuming tensor dimensions or preprocessing,
3. implement the smallest testable version,
4. benchmark it,
5. record the result,
6. only then optimize or expand the architecture.

Do not silently introduce a new model, dataset, embedding fusion method, or training strategy.

---

# 35. Current Status

**Architecture:** Locked for V1  
**Primary semantic audio embedding:** DCLAP  
**Primary text-to-audio alignment:** DCLAP-compatible text embedding space  
**Music Understanding:** DyMN04-AS + Wavv-specific heads  
**Categorical training data:** Curated MTG-Jamendo training tags  
**Human-validated MTG-Jamendo annotations:** held-out split-0 evaluation only  
**Primary emotion data:** DEAM  
**DSP metadata:** BPM, key, loudness, duration  
**Embedding fusion:** Deferred until measured  
**OpenMIC:** Optional contingency  
**FMA:** Optional contingency  
**Fine-tuning:** Optional after frozen baseline  
**Deployment mode:** Background/library indexing with caching  
**Immediate implementation priority:** Verify model interfaces and build separate DCLAP and Music Understanding pipelines before integration

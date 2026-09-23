# Wavv — Corrected Recommendation / Behavioral Dataset Specification
## For AI Coding Agents (Codex / ChatGPT Coding Agents)

**Project:** Wavv  
**Application:** Offline/local music player  
**Exhibition target:** 16 October 2026  
**Primary developers:** Ashik and Bristo  
**Hardware constraint:** Intel i5-1235U CPU, 16 GB RAM  
**Design principle:** Practical, CPU-friendly, local-first, and implementable before the exhibition.

---

# 1. Purpose

This document defines the corrected behavioral-data and recommendation architecture for Wavv.

Wavv is an **offline music player**. It is NOT a cloud music-streaming service and should not be designed as if it owns a Spotify-scale online recommendation pipeline.

The recommendation system has two distinct responsibilities:

1. Learn **long-term user preference** from user-item interactions.
2. Combine that preference with Wavv's music/content understanding and current-session signals.

The main collaborative filtering model is **iALS (implicit Alternating Least Squares)**.

Do not make iALS responsible for music understanding, language detection, mood classification, semantic search, or every short-term playback event.

---

# 2. Core Architecture

Use this high-level architecture:

```text
                    LOCAL MUSIC LIBRARY
                            |
             +--------------+--------------+
             |                             |
      Audio / Content Analysis       User Interaction Log
             |                             |
             |                  play / completion / skip
             |                  replay / like / playlist
             |                  search / manual selection
             |                  pause / seek / context
             |                             |
             v                             v
       Song representations        Behavioral Adapter
             |                             |
             |                      Interaction Mapper
             |                             |
             |                             v
             |                           iALS
             |                             |
             +--------------+--------------+
                            |
                            v
                     Hybrid Ranker
                            |
                            v
                  Recommended / Ranked Songs
```

---

# 3. Responsibility of iALS

iALS answers:

> "Which songs/items does this user tend to prefer based on their historical behavior?"

iALS should NOT directly be treated as the model for:

- language identification
- mood detection
- genre/style classification
- audio similarity
- semantic similarity
- natural-language search
- current-session sequence modeling
- every playback-control action

The recommendation system should expose an interface similar to:

```text
ials_score(user_id, song_id)
content_score(reference_song, candidate_song)
mood_score(context, candidate_song)
language_score(context, candidate_song)
novelty_score(candidate_song)
repetition_penalty(candidate_song)
```

A later hybrid ranker combines these signals.

---

# 4. The Critical Behavioral Principle

Do NOT assume that every public dataset contains every Wavv event.

Public datasets are used to establish a behavioral training and evaluation foundation.

Wavv itself will eventually collect additional events that are specific to an **offline local player**.

Therefore:

```text
Public datasets
    |
    +--> signals directly represented
    |
    +--> signals that can be derived safely
    |
    +--> signals not available publicly
                 |
                 v
          Wavv-native events
```

Never invent missing events in a public dataset.

Never claim that a signal was learned from a dataset when the dataset does not actually contain that signal.

---

# 5. Approved Core Dataset Stack

## 5.1 30Music — positive preference / playlist foundation

Use 30Music for:

- listening events
- timestamps
- sessions
- loved tracks
- user playlists
- playlist-related listening context

The 30Music paper describes a dataset combining implicit listening events with explicit preferences and playlists. It contains millions of play events, sessions, loved-track preferences, and user-created playlists.

### Role in Wavv

30Music is primarily used to learn:

- positive preference patterns
- playlist/love behavior
- repeated listening/session behavior
- relationships between listening and explicit positive organization

### Important limitation

Do NOT claim 30Music provides a complete skip/pause/seek event stream.

Its scrobbling mechanism does not represent every playback control action, and short skips are not directly equivalent to explicit skip logs.

---

## 5.2 MSSD — playback/session behavior

Use MSSD for:

- skip behavior
- not-skipped behavior
- pause behavior
- seek forward/back behavior
- context switching
- shuffle/context metadata
- temporal/session behavior
- playback start/end reasons

Relevant fields include signals such as:

```text
skip_1
skip_2
skip_3
not_skipped
context_switch
no_pause
short_pause
long_pause
num_seekfwd
num_seekbk
shuffle
hour_of_day
date
context_type
reason_start
reason_end
```

### Role in Wavv

MSSD should provide the behavioral foundation for understanding:

- early/late abandonment
- completion-like behavior
- interruptions
- seeking behavior
- session context
- playback-mode effects

### Important limitation

MSSD should NOT be represented as a complete source of Wavv's local-player semantics.

For example, it does not automatically provide Wavv's exact:

```text
search -> local song selection
manual library selection
playlist creation
favorite button semantics
```

Those remain Wavv-native where not explicitly observed.

---

## 5.3 Yambda-50M — explicit feedback + large behavioral foundation

Use **Yambda-50M**, NOT Yambda-5B, for the practical local development pipeline.

The supplied Yambda paper describes:

- Yambda-5B: 4.79 billion user-item interactions
- 1 million users
- 9.39 million tracks
- listening events
- likes
- dislikes
- unlikes
- undislikes
- timestamps
- played percentage
- track duration
- `is_organic`
- audio embeddings

The released smaller variants include Yambda-500M and Yambda-50M.

Yambda-50M contains approximately:

```text
10,000 users
934,057 items
46,467,212 listens
881,456 likes
107,776 dislikes
```

and the paper specifies separate event files plus a unified multi-event Parquet file.

### Role in Wavv

Yambda is particularly valuable for:

- explicit like
- explicit dislike
- preference reversal
- played percentage
- track duration
- organic vs recommendation-driven interactions
- large-scale implicit listening behavior
- temporal recommendation evaluation

### Important limitation

Yambda's documented interaction types are:

```text
listen
like
dislike
unlike
undislike
```

Do NOT claim that it directly contains:

- pause
- seek
- playlist_add
- manual local-library selection
- Wavv search-to-select events

Those are supplied by MSSD, 30Music, or Wavv itself depending on the signal.

### Hardware rule

Do NOT download or process the full 4.79B-event Yambda-5B release on the normal project machine.

Start with Yambda-50M.

If a larger experiment is needed, use Yambda-500M selectively and only after measuring storage/RAM/processing cost.

---

# 6. Dataset Coverage Matrix

The following is the target interpretation:

| Wavv behavioral signal | 30Music | MSSD | Yambda | Initial source/handling |
|---|---|---|---|---|
| Play/listen | Yes | Yes | Yes | iALS |
| Listen duration / completion | Partial/derived | Yes | Yes | iALS |
| Early/late skip | Limited | Yes | Derivable | iALS / behavioral mapping |
| Replay / repeated listening | Derivable | Derivable | Derivable | iALS |
| Favorite/love | Yes | No | Yes (like) | iALS |
| Playlist add / playlist presence | Yes | Playlist snapshots/context | No direct event | iALS/auxiliary |
| Dislike | Limited | No | Yes | iALS |
| Unlike / reversal | No | No | Yes | preference-state handling |
| Pause | No | Yes | No | session/context |
| Seek forward/back | No | Yes | No | session/context |
| Context switch | No | Yes | No | session/context |
| Shuffle | No | Yes | No | session/context |
| Time/session context | Yes/partial | Yes | Yes timestamps | context/ranking |
| Organic vs recommended | No | Context-specific | Yes | ranking/evaluation |
| Search -> select | Not sufficient | Not sufficient | Not direct | Wavv-native |
| Manual library selection | No | No | No | Wavv-native |
| Recommendation impression | No | Limited | Organic flag only, not Wavv-native | Wavv-native |
| Playlist creation | Partial | No | No | Wavv-native |
| Local file browsing | No | No | No | Wavv-native |

The purpose of this table is to prevent false assumptions during implementation.

---

# 7. Wavv-Native Event Schema

The application MUST define its own event schema instead of depending on public datasets.

Recommended minimum schema:

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

Recommended event types:

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

Do not require all of these events to exist in the first application build if the UI does not expose them yet.

The schema should still be extensible.

---

# 8. Event-to-Preference Mapping

The raw events should NOT be sent directly to iALS.

Create a dedicated layer:

```text
Raw event
   |
   v
Event normalization
   |
   v
Behavior interpretation
   |
   v
Interaction strength r_ui
   |
   v
Confidence c_ui
   |
   v
iALS
```

Use a configurable mapping.

Conceptually:

```text
explicit positive:
    favorite
    playlist_add
    like

strong consumption:
    high completion
    replay
    repeated listening

weak positive:
    normal play
    medium completion

negative:
    explicit dislike
    very early skip
    repeated skip

context-only:
    pause
    seek
    time-of-day
    context switch
```

Do NOT hard-code arbitrary values such as:

```text
favorite = 10
play = 2
skip = -5
```

unless those values are being treated as an experiment configuration.

Instead:

1. define candidate weights;
2. train/evaluate offline;
3. compare metrics;
4. retain the simplest configuration that performs well.

---

# 9. Important Difference Between Preference and Context

Some behavioral events represent long-term preference.

Examples:

```text
favorite
like
playlist_add
repeated listening
high completion
repeated manual selection
```

These may contribute to the iALS user-item preference signal.

Other events are primarily short-term/contextual:

```text
pause
seek
current queue
time of day
context switch
recently played
```

These should usually feed the session/ranking layer instead of directly changing the long-term iALS preference matrix.

Do not force every behavioral feature into iALS.

---

# 10. Handling Publicly Missing Signals

If a signal exists in Wavv but is absent from the public training datasets:

### Do not fabricate data.

Instead:

```text
signal available in dataset?
        |
      yes
        |
        v
learn/calibrate from public data

        no
        |
        v
implement Wavv-native event
        |
        v
collect real Wavv interactions
        |
        v
retrain/recalibrate later
```

Example:

```text
search_select
```

If public data does not provide a suitable chronological search->selection event, Wavv should still record it.

Initially it may be used as:

- a conservative positive intent signal;
- a hybrid-ranker feature;
- or an interaction-mapping feature.

Later, after enough Wavv data accumulates, test empirically whether it improves long-term preference learning.

---

# 11. Cold-Start Behavior

## New user

A new user has little or no iALS history.

Use:

- user-selected songs/artists
- content/audio similarity
- language
- mood/style
- manual selections
- current session behavior

Then gradually rely more on iALS as interactions accumulate.

## New song

A brand-new local song may not have an iALS item vector.

Do NOT force a fabricated iALS representation.

Use the content/audio side:

```text
DCLAP/music embedding
language
mood/style
other extracted features
```

to rank it.

During later iALS retraining/fold-in, the new song can acquire a collaborative representation once sufficient interaction information exists.

---

# 12. Offline-Player Specific Recommendation Logic

Wavv does not have continuous server-side streaming recommendations.

Therefore:

- store behavioral events locally;
- update lightweight user/session state locally;
- periodically retrain or refresh iALS;
- avoid retraining after every playback event;
- use current-session signals for immediate adaptation;
- keep the complete basic recommendation path functional without cloud services.

A practical architecture is:

```text
Playback event
    |
    +--> local event log
    |
    +--> update session state immediately
    |
    +--> accumulate preference evidence
                 |
                 v
         periodic iALS update/retrain
```

---

# 13. Evaluation

Use a time-aware evaluation protocol.

Recommended structure:

```text
past interactions
       |
       v
   training
       |
       v
    gap
       |
       v
  validation
       |
       v
     test
```

Do not randomly mix future interactions into training.

Primary metrics:

```text
Recall@K
Precision@K
NDCG@K
MAP@K
Coverage@K
Novelty
```

Recommended K values:

```text
5
10
20
```

Also compare against:

1. popularity baseline;
2. content-only baseline;
3. iALS;
4. hybrid recommender.

Acceptance goal:

- iALS should beat a meaningful popularity baseline;
- hybrid should outperform content-only in personalized cases;
- cold-start should remain usable without iALS history;
- local inference/update should remain practical on the target CPU.

---

# 14. Yambda Evaluation Notes

The supplied Yambda paper is especially useful because it explicitly benchmarks iALS and uses a global temporal split.

The paper reports a modified temporal protocol:

```text
Train: 300 days
Gap: 30 minutes
Test: 1 day
```

and freezes model/user state during the test period.

This is a useful reference for our own evaluation design, but Wavv does NOT have to reproduce the exact Yambda protocol.

Yambda also evaluates both:

- implicit Listen+ feedback;
- explicit Like feedback.

The paper reports iALS results at Yambda-50M, Yambda-500M, and Yambda-5B scales.

Use this as evidence that iALS is a valid baseline for this type of music interaction data, not as proof that iALS is the best final Wavv model.

---

# 15. Dataset Processing Rules

Use:

- Parquet where possible;
- chunked/streamed processing;
- sparse matrices;
- integer IDs;
- dictionary/index mappings;
- local caching;
- incremental preprocessing.

Avoid:

- dense user-item matrices;
- loading all events into RAM at once;
- unnecessary dataframe duplication;
- full Yambda-5B processing on the development machine;
- giant merged datasets when a dataset-specific adapter is sufficient.

Each dataset should have a dedicated adapter:

```text
datasets/
    30music_adapter
    mssd_adapter
    yambda_adapter
```

Each adapter should output the same canonical Wavv schema.

---

# 16. Do NOT Blindly Merge Dataset Events

Do not concatenate 30Music + MSSD + Yambda as if all events came from one population.

They are from different services, users, item IDs, logging policies, and observation processes.

Instead:

```text
30Music ------\
MSSD ----------> Canonical behavioral schema
Yambda -------/
```

Use each dataset primarily for the behavioral signal types it actually observes.

For combined experiments, clearly document:

- source dataset;
- event semantics;
- mapping;
- normalization;
- confidence transformation.

Do not pretend cross-dataset user identity exists.

---

# 17. Auxiliary Dataset Policy

Do NOT add more datasets simply because they contain the word "Spotify", "music", or "recommendation".

An additional dataset should be added only if it fills a clearly identified Wavv gap.

The Deezer cold-start dataset is useful as an **auxiliary research/feature reference** because it includes features derived from favorites, skips, streamed items, banned items, and searches.

It should NOT automatically become another core iALS training dataset.

Other large Last.fm/Deezer/Spotify/Kaggle datasets may be used later for robustness checks, but they are not required for the first implementation unless a specific uncovered signal justifies them.

---

# 18. Recommended Final Dataset Plan

## Core

### 30Music
Purpose:
- positive preference
- love
- playlist signals
- sessions
- listening history

### MSSD
Purpose:
- skip
- pause
- seek
- context switch
- playback/session context

### Yambda-50M
Purpose:
- listen
- played ratio
- like
- dislike
- unlike
- undislike
- organic vs recommendation-driven behavior

## Auxiliary

### Deezer cold-start dataset
Purpose:
- research/validation around search/favorite/skip/cold-start features

## Not required initially

Do not add further large datasets unless a specific Wavv requirement is identified.

---

# 19. Implementation Order

The coding agent should implement in this order:

## Phase 1 — Canonical event schema

Create:

```text
WavvEvent
```

with validation and serialization.

## Phase 2 — Dataset adapters

Implement:

```text
30Music adapter
MSSD adapter
Yambda-50M adapter
```

Each adapter must output canonical Wavv events.

## Phase 3 — Behavioral normalization

Implement:

```text
event normalization
completion calculation
skip classification
replay derivation
explicit positive/negative states
organic/recommendation context
```

## Phase 4 — Interaction-strength mapper

Implement configurable mappings from raw/normalized events to:

```text
r_ui
c_ui
```

Keep mapping configuration outside the code where practical.

## Phase 5 — iALS baseline

Implement:

- sparse matrix preparation;
- train/validation/test split;
- iALS training;
- item recommendation;
- user/item factor persistence;
- evaluation.

Start with modest latent dimensions such as 32 or 64.

Do NOT optimize for maximum scale initially.

## Phase 6 — Baselines

Implement:

```text
Popularity
Content-only
iALS
```

Then compare.

## Phase 7 — Hybrid ranker

Combine:

```text
iALS score
content/audio score
language
mood/style
session/context
novelty
repetition penalty
```

## Phase 8 — Wavv-native event logging

Add application events not adequately represented in public datasets.

## Phase 9 — Recalibration using Wavv events

Once enough Wavv interaction data exists:

- measure predictive value of Wavv-native signals;
- recalibrate event weights;
- retrain/fold-in iALS;
- compare against the initial public-data configuration.

---

# 20. Anti-Patterns — Do Not Implement

Do NOT:

- claim a dataset contains an event it does not contain;
- invent missing behavioral events;
- treat every play equally;
- equate every skip with explicit dislike;
- put every playback-control event directly into iALS;
- use iALS for language/mood/audio similarity;
- require cloud services for basic recommendation;
- use a dense user-item matrix;
- process the full Yambda-5B dataset on the normal development machine;
- randomly split time-dependent interactions when temporal leakage matters;
- merge users across unrelated datasets;
- add large datasets without a specific research/engineering reason.

---

# 21. Key Design Principle for AI Coding Agents

When implementing anything related to recommendation behavior, ask:

> "Is this a long-term user preference signal, a short-term context signal, a song-content signal, or an application-specific interaction?"

Then route it accordingly.

```text
Long-term preference
        -> iALS

Current session / context
        -> session/ranking layer

Song/audio/content characteristics
        -> content models

Wavv-specific events
        -> Wavv-native event log
        -> later calibration/retraining
```

This separation is mandatory for keeping Wavv understandable, testable, CPU-friendly, and realistic for an offline music player.

---

# 22. Final Decision

**Approved behavioral dataset stack:**

```text
30Music
    +
MSSD
    +
Yambda-50M
    +
Wavv-native interaction log
```

Optional:

```text
Deezer cold-start dataset
```

Do not expand the dataset list unless a concrete Wavv behavioral gap remains.

The objective is not to find one dataset containing every imaginable user action.

The objective is to build a system where:

1. public datasets provide enough diverse evidence to train and validate the initial behavioral models;
2. Wavv records missing offline-player interactions itself;
3. iALS learns long-term collaborative preference;
4. other models handle music/content understanding;
5. the hybrid ranker handles context and final recommendation;
6. everything remains practical on the target CPU/local hardware.

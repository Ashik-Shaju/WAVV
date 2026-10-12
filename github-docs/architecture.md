# Architecture

Wavv has four main runtime paths: selected media becomes a local catalog; local analysis enriches that catalog; Compose screens observe catalog and listening state; and a Media3 service owns playback independently of the activity.

```text
Android picker / MediaStore
          │
          ▼
LibrarySourceStore ──► LibraryIndexWorker ──► MediaLibraryRepository
                                                    │
                                                    ▼
                                                Room catalog
                                                    │
                       ┌────────────────────────────┴──────────────────────┐
                       ▼                                                   ▼
                LibraryViewModel                                  Analysis worker
             StateFlow to Compose screens                       embeddings + tags
                       │                                                   │
                       └──────── search / recommendations ◄───────────────┘

Compose controls ──► PlaybackController ──► MediaController / MediaSession
                                                  │
                                                  ▼
                                      PlaybackService + ExoPlayer
                                                  │
                                                  ▼
                                  Android codecs and audio routes
                                                  │
                                                  └── listening events ──► Room
```

## UI and application state

`MainActivity` sets up edge-to-edge Compose, creates the `LibraryViewModel`, connects a `PlaybackController`, and selects one of four main screens: Home, Search, Library, and You. Screen composables render state and call actions; they do not own the catalog or the player.

`LibraryViewModel` exposes library and discovery state through `StateFlow`. It coordinates source selection, WorkManager jobs, Room reads and writes, search cancellation, and local recommendation updates. This keeps indexing and search work out of composable functions.

## Catalog pipeline

`LibrarySourceStore` records the chosen all-device, file, or folder source and the persisted document access granted by Android. `LibraryIndexWorker` invokes `MediaLibraryRepository`, which reads Android's media catalog or walks the selected document tree. It extracts supported file metadata and artwork, then `LibraryStore.replaceSongs` replaces the catalog in a Room transaction while carrying user state forward for matching source URIs.

Indexing uses unique WorkManager work and reports progress. The worker requires storage not to be low; it does not require network connectivity. The selected audio remains at its original URI.

## Local persistence

`WavvDatabase` is the source of truth for catalog tracks, per-track favorite and play state, playlists and their ordered items, listening events, analysis-job status, semantic embeddings, and music-tag results. Room migrations and exported schemas are maintained with the database code. App preferences separately retain the chosen library source and document URIs.

Analysis outputs are separate from source metadata: model estimates do not overwrite tags embedded in a file. See [Library and metadata](library-and-metadata.md) and [On-device analysis](on-device-analysis.md).

## Search and recommendations

Search first parses supported metadata constraints from the user's query, then combines local text matching with available audio-to-text similarity. Personalization combines local listening events, favorites, file genres and artists, available model tags, and audio embeddings. The calculation runs on the current installation's catalog only; it does not call a ranking service.

An absent field remains absent. Similarity is relevance, not a verified genre, mood, or language. For the exact user-visible behavior and boundaries, see [Search and personalization](search-and-personalization.md).

## Playback

The UI sends queue and transport actions through `PlaybackController`, a Media3 `MediaController` client. `PlaybackService` owns ExoPlayer and its `MediaSession`; this separates playback lifetime from the screen that initiated it. The service records playback feedback after player state confirms it. Android's codecs and audio-routing stack determine which formats and routes work on a particular device.

See [Playback](playback.md) for queue behavior and device-dependent support. These responsibilities describe source code, not a claim of universal device validation.

When the user enables UHQ AAC, `PlaybackService` queues a separate `UHQEnhancementWorker` for supported AAC. The source keeps playing while the worker writes a cached PCM WAV; the service switches to that file at the current position after success and falls back to the source on failure. This playback path does not add catalog tags or embeddings.

# Wavv context

## Product

Wavv is a local-first Android music player. Users choose which device files or folders to index; the app plays those files, keeps library and listening state on-device, and performs available music analysis locally. The current scope is not an account-backed or cloud-streaming service.

## Current implementation map

- **UI:** Kotlin and Jetpack Compose. `MainActivity.kt` owns navigation and player surfaces; Home, Search, Library, and profile screens are split into their corresponding screen files.
- **Library:** `LibraryIndexing.kt`, `MediaLibraryRepository.kt`, and `LibraryViewModel.kt` handle selected sources, media metadata, indexing, and library state.
- **Playback:** `PlaybackService.kt` owns the Media3 session and background playback; `PlaybackController.kt` connects the UI to it.
- **UHQ playback:** an opt-in, experimental AAC enhancement is present in this checkout. MP3 enhancement is not integrated; local MP3 model experiments remain research artifacts. The AAC model's source-data rights, perceptual quality, device behavior, and redistribution rights are not cleared.
- **Persistence:** `WavvDatabase.kt` is the Room source of truth for songs, favorites, playlists, listening events, analysis jobs, semantic embeddings, and Music Understanding results. Its current schema version is 9; file metadata remains separate from persisted model annotations.
- **Audio metadata:** `MediaLibraryRepository.kt` prefers indexed MediaStore values and fills gaps from supported embedded metadata keys for device and SAF sources. The Room catalog retains creator, release/track, technical, compilation, and recording-date fields; availability varies by file/container/provider. Search, album grouping/order, and playback metadata consume the relevant fields.
- **Local discovery:** `OfflineDiscovery.kt` ranks local candidates from metadata, thresholded Music Understanding tags, DCLAP audio similarity, explicit favorites, observed listening, and successful user queue additions. Inferred genres are additive to file genres and contribute using their model score; genre tags are not counted again as generic tags. Model scores are not calibrated listener-preference probabilities. Intentional selections carry more weight than automatic autoplay transitions. Long-term taste and current-session context are separate; daypart context is used only after repeated evidence across distinct days.
- **Audio analysis:** a WorkManager job runs the DCLAP semantic model and the separate Music Understanding tagger when their runtime assets are available.

## Domain terms

- **Catalog track:** a media item discovered through a source the user selected.
- **File metadata:** values read from a file or Android media provider. Missing values remain unknown.
- **Music tag:** a model-produced estimate with a model/version identity; it is not a file tag or a guaranteed fact.
- **DCLAP similarity:** a relevance score between audio and text/audio embeddings, not a verified genre, mood, or language label.
- **Dynamic mix:** a temporary queue derived from the current local catalog and listening signals; it does not replace a user-created playlist.
- **For You sort:** an explicit recommendation order for Songs, Liked Songs, Artists, and Albums. It does not silently replace other library sorts or user-created playlist order.

## Boundaries

- Keep user media, analysis results, and listening history local. Do not add sample tracks, fabricated metadata, or automatic edits to user playlists.
- Refresh Home/Autoplay from local feedback; Search uses a bounded personal tie-breaker and taste-orders genre cards/results; Library offers explicit For You ordering for tracks, artists, and albums when recommendations are available. Exclude items already queued or dismissed for the session, and preserve a listener's manual Autoplay reorder.
- Search for a language only from explicit recognized file metadata; an absent or unrecognized language stays unknown.
- DCLAP and Music Understanding remain separate systems with different outputs.
- AWS is future scope and is not part of the app implementation. Keep UHQ experimental until its source data, model rights, perceptual quality, and target-device behavior are cleared.
- Review the committed third-party notices in [github-docs/redistribution.md](github-docs/redistribution.md); a notice applies only to the components it identifies.
- Treat source code and executed tests as implementation evidence. Dated research and `.scratch/` plans are not proof that a feature has shipped or passed runtime validation.

## Build and verification

Build and test commands are maintained in [README.md](README.md). JVM tests do not establish Android-runtime, device-performance, or visual-parity results; report those only when the corresponding emulator/device test was actually run.

# Wavv context

## Product

Wavv is a local-first Android music player. Users choose which device files or folders to index; the app plays those files, keeps library and listening state on-device, and performs available music analysis locally. The current scope is not an account-backed or cloud-streaming service.

## Current implementation map

- **UI:** Kotlin and Jetpack Compose. `MainActivity.kt` owns navigation and player surfaces; Home, Search, Library, and profile screens are split into their corresponding screen files.
- **Library:** `LibraryIndexing.kt`, `MediaLibraryRepository.kt`, and `LibraryViewModel.kt` handle selected sources, media metadata, indexing, and library state.
- **Playback:** `PlaybackService.kt` owns the Media3 session and background playback; `PlaybackController.kt` connects the UI to it.
- **UHQ playback:** experimental enhancement is not included in the mainline app build; redistribution and quality validation remain unresolved.
- **Persistence:** `WavvDatabase.kt` is the Room source of truth for songs, favorites, playlists, listening events, analysis jobs, semantic embeddings, and Music Understanding results. Its current schema version is 8.
- **Local discovery:** `OfflineDiscovery.kt` combines local metadata, available model outputs, and observed local listening behavior. Missing or uncertain metadata stays unknown.
- **Audio analysis:** a WorkManager job runs the DCLAP semantic model and the separate Music Understanding tagger when their runtime assets are available.

## Domain terms

- **Catalog track:** a media item discovered through a source the user selected.
- **File metadata:** values read from a file or Android media provider. Missing values remain unknown.
- **Music tag:** a model-produced estimate with a model/version identity; it is not a file tag or a guaranteed fact.
- **DCLAP similarity:** a relevance score between audio and text/audio embeddings, not a verified genre, mood, or language label.
- **Dynamic mix:** a temporary queue derived from the current local catalog and listening signals; it does not replace a user-created playlist.

## Boundaries

- Keep user media, analysis results, and listening history local. Do not add sample tracks, fabricated metadata, or automatic edits to user playlists.
- Search for a language only from explicit recognized file metadata; an absent or unrecognized language stays unknown.
- DCLAP and Music Understanding remain separate systems with different outputs.
- AWS is future scope and is not part of the app implementation. The experimental UHQ enhancement is outside the current mainline app scope.
- Treat source code and executed tests as implementation evidence. Dated research and `.scratch/` plans are not proof that a feature has shipped or passed runtime validation.

## Build and verification

Build and test commands are maintained in [README.md](README.md). JVM tests do not establish Android-runtime, device-performance, or visual-parity results; report those only when the corresponding emulator/device test was actually run.

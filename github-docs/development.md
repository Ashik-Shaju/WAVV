# Development and testing

Wavv is an Android application written in Kotlin with Jetpack Compose. Build prerequisites and canonical commands are in the root [README](../README.md); do not maintain a second copy of those commands here. Android and Kotlin dependency versions are kept in `gradle/libs.versions.toml`.

## Code map

| Area | Main files | Responsibility |
| --- | --- | --- |
| Activity, navigation, player surfaces | `MainActivity.kt` | Permission/source launchers, tab and player state, UI-to-domain actions |
| Screens | `HomeScreen.kt`, `SearchScreen.kt`, `LibraryScreen.kt`, `ProfileScreen.kt` | Compose presentation and user interactions |
| Catalog and source indexing | `LibraryIndexing.kt`, `MediaLibraryRepository.kt`, `LibraryViewModel.kt` | Persisted source grants, metadata scan, worker lifecycle, screen state |
| Persistence | `WavvDatabase.kt` | Room entities, DAOs, migrations, and catalog operations |
| Playback | `PlaybackController.kt`, `PlaybackService.kt` | Media3 controller, queue operations, service-owned ExoPlayer and session |
| Search and personalization | `OfflineDiscovery.kt`, `SongSearch.kt` | Query parsing, local ranking, mixes, observed-playback reducer |
| Model inference | `Dclap.kt`, `DclapIndexing.kt`, `MusicUnderstanding.kt`, `MusicUnderstandingRuntime.kt` | On-device embeddings, tag estimates, caching and worker progress |
| Embedded file content | `EmbeddedLanguageMetadata.kt`, `Lyrics.kt` | Supported language fields and lyrics read from selected files |

## Test levels

Run the commands in the root README for these checks:

| Check | What it can establish | What it cannot establish alone |
| --- | --- | --- |
| `:app:assembleDebug` | Kotlin/Compose compilation and debug packaging | Permission flow, playback, device codecs, output routes, or device performance |
| `:app:testDebugUnitTest` | Deterministic JVM logic such as query parsing, ranking, queue rules, metadata parsing, and reducers | Android framework behavior, Room on-device behavior, actual audio, or visual parity |
| `:app:connectedDebugAndroidTest` | Instrumented paths, including bundled inference, Room persistence, and selected UI rendering tests | Compatibility with every device, codec, audio route, or Android release |

Relevant local test classes include `OfflineDiscoveryTest`, `PlaybackQueueTest`, `PlaybackFeedbackReducerTest`, `LibraryBackendTest`, `LyricsTest`, and `DclapTest`. Instrumentation tests live under `app/src/androidTest/`; their presence is not evidence that they passed. Run them on an emulator or device and report the exact result before making a runtime claim.

## Practical change boundaries

- Treat source-code behavior and tests as the current contract. Historical notes and mockups are not proof that a feature shipped.
- Keep metadata read from files separate from estimates produced by local analysis.
- Keep file access behind the source grants selected through Android's picker or media permission.
- A Compose preview can check layout, but it cannot validate Media3 playback, Android permission prompts, routing, or real file metadata.
- The release variant currently uses the debug signing configuration. It is suitable for local install testing, not public production distribution.

For known build, indexing, search, and playback symptoms, see [Troubleshooting](troubleshooting.md).

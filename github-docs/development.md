# Development and testing

Wavv is an Android application written in Kotlin with Jetpack Compose. Use the prerequisites and commands in the root [README](../README.md); this page does not keep a second copy of them. Android and Kotlin dependency versions are in <code>gradle/libs.versions.toml</code>.

## Code map

| Area | Main files | Responsibility |
| --- | --- | --- |
| Activity, navigation, player surfaces | <code>MainActivity.kt</code> | Permission and source launchers, screen state, player UI, UI actions |
| Screens | <code>HomeScreen.kt</code>, <code>SearchScreen.kt</code>, <code>LibraryScreen.kt</code>, <code>ProfileScreen.kt</code> | Compose presentation and user interactions |
| Catalog and source indexing | <code>LibraryIndexing.kt</code>, <code>MediaLibraryRepository.kt</code>, <code>LibraryViewModel.kt</code> | Source grants, metadata scans, background indexing, screen state |
| Persistence | <code>WavvDatabase.kt</code> | Room entities, DAOs, migrations, catalog operations |
| Playback | <code>PlaybackController.kt</code>, <code>PlaybackService.kt</code> | Media3 controller, queue, playback service, and session |
| Playback effects | <code>AudioEqualizer.kt</code>, <code>BitPerfectPlayback.kt</code>, <code>PlaybackCrossfade.kt</code>, <code>UHQEnhancement.kt</code> | Equalizer, output mode, crossfade, optional AAC processing |
| Search and personalization | <code>OfflineDiscovery.kt</code>, <code>SongSearch.kt</code> | Query parsing, local ranking, mixes, playback feedback |
| Metadata and local analysis | <code>AudioMetadata.kt</code>, <code>Dclap.kt</code>, <code>DclapIndexing.kt</code>, <code>MusicUnderstanding.kt</code> | File metadata, on-device search and tag analysis |
| Lyrics | <code>EmbeddedLanguageMetadata.kt</code>, <code>Lyrics.kt</code> | Supported file language fields and embedded, imported, or local lyrics |

## Test levels

Run the commands in the root README.

| Check | What it covers | What it does not establish alone |
| --- | --- | --- |
| <code>:app:assembleDebug</code> | Kotlin/Compose compilation and debug packaging | Runtime permission flows, playback, device codecs, routes, or performance |
| <code>:app:testDebugUnitTest</code> | Deterministic logic such as search ranking, metadata and lyrics parsing, queue rules, playback effects, and persistence helpers | Android framework behavior, playback on a device, or rendered UI parity |
| <code>:app:connectedDebugAndroidTest</code> | Device-side analysis, Room persistence, session effects, and AAC processing | Compatibility with every device, codec, output route, or Android release |

The instrumentation suite includes <code>UHQEnhancementEndToEndTest</code>, which creates a short AAC input and checks the enhancement output. Its optional library case needs audio access and a supported AAC track in <code>Music/</code>. <code>DclapEndToEndTest</code> and the Music Understanding instrumentation tests exercise on-device analysis. Test files being present does not mean the checks have passed; record the actual command result before making a runtime claim.

The repository has unit tests for screen-content decisions, but no end-to-end test that drives the full app UI. It also has no test of the complete <code>PlaybackService</code> flow that switches playback to and from a cached AAC enhancement.

## Change boundaries

- Keep file metadata distinct from analysis results.
- Keep file access behind the Android permissions or document grants selected by the user.
- A Compose preview or unit test cannot validate device playback, Android permission prompts, audio routing, or real file metadata.
- Release builds enable code and resource shrinking. Without the private release keystore, local `assembleRelease` output is unsigned. See the root README for signed build instructions.

For build, indexing, search, and playback symptoms, see [Troubleshooting](troubleshooting.md).

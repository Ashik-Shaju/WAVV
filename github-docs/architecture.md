# Architecture

Wavv keeps music access, catalog data, and playback inside the Android app. The main path is:

```text
Chosen audio source → library indexing → local catalog → app screens
                                            ↘ local discovery
App controls → playback controller → media playback service → Android audio output
```

## Library

Users can select audio across the device, individual files, or one or more folders. Android grants access to the chosen sources; Wavv persists those read grants so it can refresh the catalog later. `LibraryIndexWorker` schedules indexing, and `MediaLibraryRepository` reads available file metadata and artwork. The original audio stays at its source; the app stores catalog records and source references.

## Local storage and discovery

Room stores the catalog, favorites, playlists, listening events, and available analysis results. `OfflineDiscovery` ranks catalog items from available metadata, local analysis, and listening signals. Missing file metadata remains unknown rather than being filled with guessed values.

## Playback

Compose screens send playback actions through `PlaybackController`. `PlaybackService` owns the Media3 player and media session, so playback is not tied to a screen's lifetime. Android and Media3 handle decoding and route selection; supported formats and output behavior can vary with the device and its installed codecs.

These are source-level responsibilities, not a claim that every device route or format has been runtime-tested.

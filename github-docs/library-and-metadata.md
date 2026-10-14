# Library and metadata

The library is a catalog of references to user-selected audio. It is not a second copy of the user's music folder.

## Source selection and indexing

`LibrarySourceStore` retains the chosen source. All-device selection uses Android's `MediaStore` audio catalog and the runtime media permission. File and folder selection use Android's Storage Access Framework; Wavv retains the read grant so it can reopen the URI later. Folder scans walk the selected tree recursively. Users can add and remove folders from the You screen.

`LibraryIndexWorker` runs a unique, one-time WorkManager job. It asks `MediaLibraryRepository` to read the current source, reports progress, and replaces catalog rows in a Room transaction. Work is constrained to run when storage is not low, uses exponential retry for I/O failures, and does not require a network connection. If access fails, reselect the source and retry.

For all-device scans, Wavv asks Android for audio entries marked as music. File and folder scans accept documents with an audio MIME type or a recognized audio-file suffix. Discovery of a file does not guarantee that Android can decode it; actual playback support is described in [Playback](playback.md).

## Metadata fields

Wavv reads available title, artist, album, duration, and MIME/container information. Optional catalog fields include genres, album artist, composer, year and recording date, track/disc numbers and track count, author, writer, compilation status, bitrate, and sample rate. Android media-provider values are preferred where exposed, with supported embedded metadata filling gaps. Availability varies by file, container, Android version, and document provider. Missing genres, language, or model tags are not fabricated.

Language metadata is checked lazily when a language search is requested. Wavv recognizes explicit media-track language and supported embedded language fields. Unknown and unrecognized values remain unknown.

## Artwork and lyrics

Artwork comes from an embedded picture or Android's album-art provider. Embedded images above the configured size limit or images Android cannot decode may be unavailable. Wavv keeps a deduplicated, resized app-private display copy and prunes artwork no longer referenced by the indexed catalog. It does not substitute unrelated album art for a track that has none.

Lyrics are read from supported embedded metadata when the player opens the lyrics panel. Timed lyric lines can be synchronized to playback position. There is no online lyrics lookup.

## Identity and user state

Android media entries use their provider IDs; selected document entries receive a stable ID derived from their URI. When a source is rescanned, `LibraryStore.replaceSongs` replaces the catalog transactionally and restores favorite and last-played state for matching URIs. Analysis results are associated with the track and the source file's size and modified time; changed sources are eligible for re-analysis.

The Room catalog, playlists, listening events, and analysis outputs are described in [Architecture](architecture.md). Selected URIs and Android grants are described in [Privacy](privacy.md).

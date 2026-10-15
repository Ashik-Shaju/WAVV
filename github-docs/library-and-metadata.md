# Library and metadata

The library is a catalog of references to audio selected by the user. Wavv does not create a second copy of the music collection.

## Source selection and indexing

Wavv can read Android's indexed music collection, selected individual audio documents, or one or more selected folders. The all-device source uses Android's media permission. File and folder sources use Android's Storage Access Framework; Wavv retains the granted read access so it can reopen the selected URIs. Folder scans include subfolders.

<code>LibraryIndexWorker</code> runs a unique, one-time WorkManager job. It asks <code>MediaLibraryRepository</code> to read the current source, reports progress, and replaces catalog rows in a Room transaction. If access fails, select the source again and retry.

For all-device scans, Wavv asks Android for audio entries marked as music. File and folder scans accept documents with an audio MIME type or a recognized audio-file suffix. Finding a file does not guarantee that Android can decode it; see [Playback](playback.md).

## Metadata fields

Wavv reads available title, artist, album, duration, and MIME/container information. Other available fields include genre, album artist, composer, year and recording date, track and disc numbers, track count, author, writer, compilation status, bitrate, and sample rate. Android media-provider values are preferred where exposed, with supported embedded metadata filling gaps.

Availability varies by file, container, Android version, and document provider. Missing values from the file or media provider remain unknown; inferred tags, when available, are separate local analysis results described in [On-device analysis](on-device-analysis.md). Language metadata is checked when a language search needs it and only recognized values qualify.

## Artwork and lyrics

Artwork can come from an embedded picture or Android's album-art provider. Large or unreadable images may be unavailable. Wavv keeps a resized app-private display copy and removes copies that are no longer referenced by the catalog. It does not substitute unrelated cover art.

The player reads supported embedded lyrics. A user can import a .ttml, .lrc, or plain-text lyrics document. The optional **Automatically check for lyrics** setting looks for a matching .ttml or .lrc document when a track starts without embedded lyrics. It searches selected music folders; with the all-device library, it checks Downloads only when Android grants whole-storage access. When both formats match, TTML is preferred. Wavv does not download lyrics.

Timed lyrics can be synchronized to playback position. Lyrics are not indexed by Search.

## Track identity and user state

Android media entries use provider IDs; selected document entries receive a stable ID derived from their URI. When a source is rescanned, the catalog is replaced transactionally and favorite and last-played state is restored for matching source URIs. Local analysis results are reused only while their source-file identity remains current.

Selected URIs and Android grants are described in [Privacy](privacy.md).

# User guide

Wavv plays audio already available on your Android device. It does not provide a streaming catalog. Start by choosing which local audio Wavv may index.

## Choose a music source

On first launch, choose one of these sources:

| Choice | What it does | Access needed |
| --- | --- | --- |
| All music on this device | Reads Android's indexed music collection. Android may omit audio it does not classify as music. | Wavv's audio-library permission |
| Individual audio files | Lets you select one or more audio documents. | Read access granted by Android's file picker |
| A specific folder | Scans audio files in the selected folder and its subfolders. | A persistent folder read grant |

Indexing fills the library from the selected source; it does not move or rewrite the audio. Wavv can refresh the selection when loading the library. In **You → Music Folders**, add or remove folders or switch to scanning all device music. If Android revokes a file or folder grant, choose that source again.

## Browse the app

- **Home** shows library-driven shelves, such as recent tracks and available mixes. A mix appears only when local listening or favorite signals are sufficient; a new or sparse library may have no recommendations yet.
- **Search** matches titles and metadata and can use local audio-text similarity for descriptive queries. Category cards are built from actual genre tags and tracks with artwork. If your files lack those tags or art, the app does not invent them.
- **Library** organizes songs, albums, artists, favorites, and user-created playlists. Playlist changes require an explicit action.
- **You** shows local library counts, analysis progress, the selected source, notification and listening-history controls, Equalizer settings, and the optional UHQ AAC setting. The Theme row reports the current dark appearance; it is not a theme switch. Storage Used estimates the total size of the indexed audio files, not Wavv's app storage.

## Search honestly

Try a descriptive request such as `calm piano`, a metadata request such as `songs by <artist>`, or a language filter such as `songs in English`. Search uses the words and tags available in your own library. Descriptive similarity is a ranking signal, not proof that a track has a particular mood or genre.

Language filtering is stricter: Wavv matches recognized language metadata explicitly present in the file or media track. If no recognized language is present, the language stays unknown and the track will not match that language filter. Lyrics are not searched.

## Play and manage a queue

Tap a track to start playback. The mini-player opens the full player; use its transport buttons and seek bar to control playback. The queue can show upcoming tracks, play a track next, add tracks, reorder or remove upcoming tracks, shuffle upcoming items, and repeat the current item or queue. Repeat behavior is controlled by the player UI.

The output panel lists compatible Android audio routes currently reported to Wavv. The operating system, device, selected route, and installed decoder determine actual format and output support. See [Playback](playback.md) for the boundary.

UHQ AAC is an experimental, opt-in setting in **You** and is off by default. For AAC tracks Android can decode in the supported form, Wavv keeps the original playing while it prepares a cached enhanced copy, then may switch to that copy. Other formats and processing failures keep using the original. Turning UHQ off returns playback to the original; cached copies can remain until Android clears them or the cache needs space. Wavv never changes the source file.

## Artwork, metadata, and lyrics

Wavv uses metadata and artwork it can read from the selected file or Android's media provider. Missing titles use a filename fallback; missing artist or album values remain marked unknown. The player can show embedded lyrics when the file uses a supported lyrics field. Wavv does not fetch artwork, lyrics, genres, or language from a network service.

## Local personalization

Favorites, confirmed plays, completions, repeats, skips, and playlist actions can influence later local recommendations. An early skip is treated as weak context, not as a definitive dislike. History is stored on-device and pruned after one year. Clearing listening history keeps favorites and playlists; see [Privacy](privacy.md).

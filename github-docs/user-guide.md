# User guide

Wavv plays audio already available on your Android device. It does not provide a streaming catalog. Start by choosing which local audio Wavv may index.

## Choose a music source

On first launch, choose a source:

| Choice | What it does | Access needed |
| --- | --- | --- |
| All music on this device | Reads Android's indexed music collection. Android may omit audio it does not classify as music. | Wavv's audio-library permission |
| Individual audio files | Lets you select audio documents. | Read access granted by Android's file picker |
| One or more folders | Scans selected folders and their subfolders. | Persistent folder read grants |

Indexing adds references to your library; it does not move or rewrite the audio. In **You → Music Folders**, add or remove folders or switch to all device music. If Android revokes access, select the source again.

## Browse the app

- **Home** shows shelves such as recently played or recently added tracks and available mixes. A mix may be absent when the library or listening history is sparse.
- **Search** matches available titles and metadata, and can use local audio analysis for descriptive queries. Categories use genres from file metadata or local analysis and require readable artwork.
- **Library** organizes songs, albums, artists, favorites, and playlists. When recommendations are available, Songs, Liked Songs, Artists, and Albums can use the **For You** sort.
- **You** shows library counts, source and analysis status, notifications, listening history, Music Folders, Standby, and playback settings. **Storage Used** estimates the size of indexed audio files, not Wavv's app storage.

## Search

Try a descriptive request such as <code>calm piano</code>, an artist query such as <code>songs by &lt;artist&gt;</code>, or a language filter such as <code>songs in English</code>. Results depend on information present in your library. Descriptive matching is a ranking signal, not proof that a track has a particular mood or genre.

Language filters match only recognized language metadata present in a file or media track. Missing language remains unknown. Search does not search lyrics.

## Playback and settings

Tap a track to start playback. The mini-player opens the full player. Use the transport controls and seek bar to control playback. The queue supports play next, add to queue, reorder or remove upcoming tracks, shuffle, repeat, and AutoPlay suggestions.

The full player may show a source-quality label. Tap it for technical details Android can read from the file. The label does not certify mastering or audible quality. The output panel lists routes Android currently reports to Wavv; actual support depends on Android, the device, route, and decoder.

In **You**, **Equalizer** provides a ten-band EQ, session gain, presets, and saved profiles. **Crossfade** blends eligible queued tracks and is off when set to zero. **UHQ AAC** is an experimental, opt-in enhancement that is off by default. For supported AAC, Wavv keeps playing the source while it prepares a cached copy; unsupported input or processing failure leaves playback on the original. Wavv does not modify the source file.

**Bit-perfect mode** requests Android's bit-perfect mixer mode for supported USB output and PCM formats on Android 14 or newer. It may not activate for the current route or track. While active, it pauses EQ, UHQ processing, and crossfade; use the USB device's hardware volume control.

## Artwork, metadata, and lyrics

Wavv uses metadata and artwork it can read from a selected file or Android's media provider. Missing titles use a filename fallback; missing artist and album values remain unknown.

The player can read embedded lyrics, import a <code>.ttml</code>, <code>.lrc</code>, or plain-text file, or use **Automatically check for lyrics**. Automatic checking is off by default. When enabled, Wavv checks for a matching lyric file when a track starts without embedded lyrics. It searches selected music folders, or Downloads for the all-device source only when Android grants whole-storage access. TTML is preferred to LRC when both match. Wavv does not download lyrics.

## Local personalization

Favorites and listening activity can influence local recommendations. Listening history stays on-device for up to one year. **Clear listening history** removes listening events and recently played markers, while preserving favorites and playlists. See [Privacy](privacy.md).

# Architecture

Wavv has four connected runtime paths: Android-selected media becomes a local catalog; background work adds local analysis results; Compose screens display library and listening state; and a Media3 service owns playback independently of the activity.

~~~text
Android picker / MediaStore
          |
          v
LibrarySourceStore -> LibraryIndexWorker -> MediaLibraryRepository
                                              |
                                              v
                                           Room catalog
                                              |
                      +-----------------------+----------------------+
                      v                                              v
                LibraryViewModel                              Analysis worker
               StateFlow to UI                          local search and tag results
                      |                                              |
                      +----------- search / suggestions <-----------+

Compose controls -> PlaybackController -> MediaController / MediaSession
                                              |
                                              v
                                    PlaybackService + ExoPlayer
                                              |
                                              v
                                  Android codecs and audio routes
                                              |
                                              +---- listening events -> Room
~~~

## UI and application state

<code>MainActivity</code> hosts the Compose app and connects the library and playback state. The main surfaces are Home, Search, Library, and You. Screen composables render state and send actions to the app's state holders and playback controller.

<code>LibraryViewModel</code> exposes catalog and discovery state through <code>StateFlow</code>. It coordinates source selection, background work, Room reads and writes, search, and local recommendation updates.

## Catalog pipeline

<code>LibrarySourceStore</code> retains the chosen all-device, file, or folder source and Android's persisted document grants. <code>LibraryIndexWorker</code> asks <code>MediaLibraryRepository</code> to read the source, extracts supported metadata and artwork, and replaces catalog rows in a Room transaction while carrying user state forward for matching source URIs.

The selected audio remains at its original URI. Indexing uses unique WorkManager work, reports progress, and does not require network connectivity.

## Local persistence and analysis

<code>WavvDatabase</code> stores catalog tracks, favorites and play state, playlists, listening events, analysis status, and local analysis results. Room migrations and exported schemas are maintained with the database code. App preferences retain the chosen library source and other user settings.

Analysis results remain separate from metadata read from the file. They do not overwrite embedded tags. See [Library and metadata](library-and-metadata.md) and [On-device analysis](on-device-analysis.md).

## Search and recommendations

Search and recommendations use the current library, available local analysis results, and local listening activity. They run on the device and do not call a ranking service. Missing information remains unknown.

See [Search and personalization](search-and-personalization.md) for user-visible behavior and [Privacy](privacy.md) for locally stored information.

## Playback

The UI sends queue and transport actions through <code>PlaybackController</code>, a Media3 <code>MediaController</code> client. <code>PlaybackService</code> owns ExoPlayer and its <code>MediaSession</code>, keeping playback separate from the screen that started it. Android's codecs and audio-routing stack determine which formats and routes work on a particular device.

Playback options such as equalizer, crossfade, optional AAC enhancement, and USB bit-perfect mixer mode are coordinated by the service. Their support depends on the active track, Android version, and output route; see [Playback](playback.md).

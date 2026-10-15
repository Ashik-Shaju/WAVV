# Privacy and local data

Wavv reads audio sources selected on the device and stores its library and playback state locally. The Android manifest does not declare the <code>INTERNET</code> permission. The app has no account, cloud library, or sync flow.

## What Wavv reads and stores

| Information | Why it is used | Where it is kept |
| --- | --- | --- |
| Selected library source and document URIs | Refresh the chosen library and retain Android-granted access | App preferences and Android's persisted URI grants |
| File metadata and source URI | Display, search, and play selected tracks | Room catalog |
| Favorites and playlists | Preserve explicit library choices | Room database |
| Playback events and recent-track state | Show history and personalize discovery | Room database |
| Analysis status and results | Support local discovery and avoid unnecessary reprocessing | Room database |
| Lyrics source choices and selected lyrics document URIs | Read embedded, imported, or discovered lyrics | App preferences and Android-granted document access |
| Embedded artwork copied for display | Show artwork supplied by a selected file or Android media provider | App-private <code>filesDir/embedded-artwork</code> |
| Installed analysis assets | Run local analysis | App-private files directory |
| Temporary AAC processing audio and enhanced copies | Optional playback enhancement when enabled | App cache; enhanced copies are limited to 1 GiB total and may be evicted |

Wavv does not copy the original selected audio into a separate collection or edit its embedded tags. Removing a source grant does not delete the source audio.

When **Automatically check for lyrics** is enabled, matching <code>.ttml</code> or <code>.lrc</code> files are searched only in selected music folders. With the all-device library source, Wavv checks Downloads only when Android grants whole-storage access. Lyrics imported through the player use the selected document grant. Wavv does not fetch lyrics from an online service.

## Listening history

Playback activity is used locally for recent tracks and recommendations. Listening events older than one year are pruned. **Clear listening history** removes listening events and recently played markers; favorites and playlists remain.

## Android permissions and backup

The all-device library requires Android's audio-library permission (<code>READ_MEDIA_AUDIO</code> on current Android versions and <code>READ_EXTERNAL_STORAGE</code> on older versions). Selecting files or folders uses Android's system document picker and a persistent read grant.

The committed backup rules exclude cloud backup and allow device-to-device transfer of the Room database, app preferences, and cached embedded artwork. Other app-private files and caches are excluded. Device-transfer availability and restore timing depend on Android and the device manufacturer. The selected music files remain in their original locations.

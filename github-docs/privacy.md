# Privacy and local data

Wavv is designed around files the user selects and data kept by the app on the device. The current manifest declares audio-library, foreground media-playback, and notification permissions, but it does not declare Android's `INTERNET` permission. The current app has no account, cloud library, or sync flow.

## What Wavv reads and stores

| Information | Why it is used | Where it is kept |
| --- | --- | --- |
| Selected source and document URIs | Refresh the chosen library and retain Android-granted access | App preferences and Android's persisted URI grants |
| File metadata and source URI | Display, search, and play selected tracks | Room catalog |
| Favorite and playlist changes | Preserve explicit library choices | Room database |
| Playback events and recently played state | Update local history and personalize discovery | Room database |
| Analysis status, embeddings, and tag estimates | Avoid unnecessary inference and support local discovery | Room database |
| Embedded artwork copied for display | Show artwork when a selected file or Android media provider supplies it | App-private `filesDir/embedded-artwork` |
| Installed model files | Run bundled analysis after first use | App-private files directory |

Wavv does not copy the original selected audio into a separate music collection or edit its embedded tags. Removing a source grant does not delete the source audio. A later library scan may stop showing a file that is no longer in the selected source.

## Listening history controls

Playback activity is used locally for recent tracks and recommendations. The app removes events older than one year when history is pruned. The You screen also offers **Clear listening history**; this removes listening events and recently played markers, while favorites and playlists remain.

## Android permissions and backup

Selecting all device music requires Android's audio-library permission (`READ_MEDIA_AUDIO` on current Android versions and `READ_EXTERNAL_STORAGE` on older versions). Selecting individual files or folders uses Android's system document picker and a persistable read grant instead.

The manifest sets `android:allowBackup="true"`. Android backup and device-transfer behavior is controlled by the platform and device configuration; do not treat app-local storage as a guarantee that data can never leave one device. Review the manifest and actual data flows before adding network, analytics, account, or backup-policy features.

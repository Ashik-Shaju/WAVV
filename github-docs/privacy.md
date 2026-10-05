# Privacy and local data

Wavv reads only audio sources the user selects or grants access to. Folder and file selections use Android's document picker; whole-device library access uses Android's media permission.

The app stores catalog metadata, source references, playlists, favorites, listening history, and available analysis results in app-local storage. It may cache embedded artwork locally. It does not copy the selected audio into a Wavv-managed music library.

The current Android manifest does not declare the `INTERNET` permission, and the app has no account or cloud-sync flow. The manifest does allow Android backup (`allowBackup="true"`), so platform backup and device-transfer behavior is a separate boundary from Wavv's own network behavior.

Re-check the manifest and data flows before adding networking, analytics, account features, or backup changes; those would change this description.

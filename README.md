# Wavv

Wavv is an Android music player for audio you choose from your device. It builds a local library, plays through Android's media stack, and provides local search and recommendations. It does not provide a streaming catalog, account, or cloud library.

## Features

- Index music from Android's device library, selected files, or selected folders.
- Browse songs, albums, artists, favorites, and playlists.
- Search local metadata and use on-device analysis for descriptive searches and music tags.
- Manage playback queues, shuffle, repeat, and autoplay.
- Use embedded or imported lyrics, with optional discovery of matching local <code>.ttml</code> and <code>.lrc</code> files.
- Configure a ten-band equalizer, crossfade, optional AAC enhancement, and USB bit-perfect mixer mode where Android supports it.

All library and listening data stay on the device. Wavv does not sync a library or listening profile to a service.

## Build

### Requirements

- Git and Git LFS
- JDK 17
- Android 8.0 or newer to run the app
- Android SDK Platform 37
- Network access for the first Gradle dependency download

Clone the repository and fetch its Git LFS assets before building. The Windows commands are:

~~~powershell
git lfs install
git clone https://github.com/Ashik-Shaju/WAVV.git
Set-Location WAVV
git lfs pull
.\gradlew.bat :app:assembleDebug
~~~

On macOS or Linux, run:

~~~sh
git lfs install
git clone https://github.com/Ashik-Shaju/WAVV.git
cd WAVV
git lfs pull
./gradlew :app:assembleDebug
~~~

If Gradle cannot find the Android SDK, open the project in Android Studio or set <code>ANDROID_HOME</code> or <code>ANDROID_SDK_ROOT</code>. The local <code>local.properties</code> file is not tracked.

The debug APK is written to <code>app/build/outputs/apk/debug/app-debug.apk</code>. Install it on a connected Android device with:

~~~powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

The release build currently uses the debug signing configuration. It is for local development and is not a production-signed release.

## Tests

On Windows, run JVM unit tests with <code>.\gradlew.bat :app:testDebugUnitTest</code> and instrumentation tests with <code>.\gradlew.bat :app:connectedDebugAndroidTest</code>. On macOS or Linux, use <code>./gradlew</code> with the same task names. Instrumentation tests need a connected emulator or device; some cases also require audio-library access and a supported local AAC file. See [Development and testing](github-docs/development.md) for what these checks cover and their limits.

## Documentation

Start at the [documentation index](github-docs/README.md) for user instructions, architecture, playback, privacy, troubleshooting, and redistribution notices.

## License

This repository does not currently contain a root-level <code>LICENSE</code> file for Wavv's own source. See [Redistribution and notices](github-docs/redistribution.md) for the committed notices and assets whose terms need checking.

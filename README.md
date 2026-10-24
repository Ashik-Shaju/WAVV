<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Wavv logo" width="96"/>

# Wavv

**A private, offline-first music player for Android — with on-device intelligence.**

Wavv plays the audio you already own. It builds a local library, plays through
Android's media stack, and adds on-device semantic search, tagging, and audio
enhancement — no streaming catalog, no account, no cloud library.

[![Platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%20%E2%86%92%2037-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Gradle](https://img.shields.io/badge/build-Gradle_Kotlin_DSL-02303A?logo=gradle&logoColor=white)](https://gradle.org)

</div>

---

> [!IMPORTANT]
> All library and listening data stay on the device. Wavv does not sync a
> library or listening profile to any service.

## ✨ Features

| | |
|---|---|
| 🎵 **Library** | Index music from Android's device library, selected files, or selected folders. Browse songs, albums, artists, favorites, and playlists. |
| 🔍 **Smart search** | Search local metadata, with on-device analysis powering descriptive ("semantic") searches and automatic music tags. |
| ⏯️ **Playback** | Manage playback queues, shuffle, repeat, and autoplay through Android's media stack. |
| 📝 **Lyrics** | Embedded or imported lyrics, with optional discovery of matching local `.ttml` and `.lrc` files. |
| 🎚️ **Audio controls** | Ten-band equalizer, crossfade, optional AAC enhancement, and USB bit-perfect mixer mode where Android supports it. |

## 🧱 Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose · Material 3 |
| Playback | Media3 / ExoPlayer · MediaSession |
| Data | Room · WorkManager |
| On-device ML | ONNX Runtime |

## 🚀 Getting started

### Requirements

- Git and Git LFS
- JDK 17
- Android SDK Platform 37
- Android 8.0 or newer to run the app
- Network access for the first Gradle dependency download

### Clone and build (debug)

**Windows**

```powershell
git lfs install
git clone https://github.com/Ashik-Shaju/WAVV.git
Set-Location WAVV
git lfs pull
.\gradlew.bat :app:assembleDebug
```

**macOS / Linux**

```sh
git lfs install
git clone https://github.com/Ashik-Shaju/WAVV.git
cd WAVV
git lfs pull
./gradlew :app:assembleDebug
```

> [!TIP]
> If Gradle cannot find the Android SDK, open the project in Android Studio or
> set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`). The local `local.properties`
> file is not tracked.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 🔐 Release build

1. Place a private keystore at `app/keystore/wavv-release.jks`.
2. Set the signing environment variables:

   | Variable | Purpose |
   |---|---|
   | `WAVV_KEYSTORE_PASSWORD` | Keystore password |
   | `WAVV_KEY_ALIAS` | Key alias |
   | `WAVV_KEY_PASSWORD` | Key password |

3. Run `.\gradlew.bat :app:assembleRelease` (Windows) or `./gradlew :app:assembleRelease` (macOS/Linux).

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`. Without a keystore, Gradle creates an unsigned APK for local inspection only. Never commit the keystore or its passwords; keep the same key for future updates. See [Release notes](RELEASE_NOTES.md) for the current summary.

## 🧪 Testing

```sh
./gradlew :app:testDebugUnitTest          # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumentation tests
```

On Windows, use `.\gradlew.bat` with the same task names. Instrumentation
tests need a connected emulator or device; some cases also require
audio-library access and a supported local AAC file. See
[Development and testing](github-docs/development.md) for what these checks
cover and their limits.

## 📚 Documentation

Start at the [documentation index](github-docs/README.md):

- [Architecture](github-docs/architecture.md)
- [User guide](github-docs/user-guide.md)
- [Playback](github-docs/playback.md)
- [Library and metadata](github-docs/library-and-metadata.md)
- [Search and personalization](github-docs/search-and-personalization.md)
- [On-device analysis](github-docs/on-device-analysis.md)
- [Privacy](github-docs/privacy.md)
- [Troubleshooting](github-docs/troubleshooting.md)
- [Redistribution and notices](github-docs/redistribution.md)

## 🗂️ Project structure

```
WAVV/
├── app/                    # Android application module (single-module project)
│   └── src/main/assets/    # Bundled ONNX models (DCLAP, music understanding, UHQ)
├── github-docs/            # User, architecture, and privacy documentation
├── gradle/                 # Version catalog and wrapper configuration
└── CONTEXT.md              # Single-context domain documentation root
```

## 🤝 Contributing

Bug reports and feature requests are welcome through
[GitHub Issues](https://github.com/Ashik-Shaju/WAVV/issues). Please search
existing issues before opening a new one, and include your Android version,
device, and reproduction steps for bugs.

## 📄 License

This repository does not currently contain a root-level `LICENSE` file for
Wavv's own source. See [Redistribution and notices](github-docs/redistribution.md)
for the committed notices and assets whose terms need checking.

---

<div align="center">

**Wavv** — your music, on your device, understood on-device.

</div>

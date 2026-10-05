# Wavv

Wavv is an offline-first Android music player built with Kotlin, Jetpack Compose, Room, and Media3. Start with the [public documentation](github-docs/README.md).

## Build on Windows

Install Git LFS, JDK 17 or newer, and Android SDK Platform 37. The first build needs network access to resolve Gradle and Android dependencies.

```powershell
git lfs install
git clone https://github.com/Ashik-Shaju/WAVV.git
Set-Location WAVV
git lfs pull
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

If Gradle cannot find the Android SDK, open the project in Android Studio or set `ANDROID_HOME`/`ANDROID_SDK_ROOT`. The local `local.properties` file is intentionally not tracked.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`; install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Bundled DCLAP and Music Understanding models use Git LFS, so run `git lfs pull` before building a fully functional APK.

## Tests

Run unit tests with `.\gradlew.bat :app:testDebugUnitTest`. Run Android instrumentation tests on a connected emulator or device with `.\gradlew.bat :app:connectedDebugAndroidTest`. The DCLAP device-library smoke test is skipped unless audio-library access is granted and at least one local audio file is available.

## Redistribution

See [redistribution and notices](github-docs/redistribution.md) before sharing source or distributing a build.

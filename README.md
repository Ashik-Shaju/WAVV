# Wavv

Wavv is an Android music player built with Kotlin, Jetpack Compose, Room, and Media3.

## Build on Windows

Install Git LFS and an Android SDK that includes API 37. The Gradle wrapper downloads the project's configured Gradle and JDK when needed; the first build also needs network access to resolve dependencies.

```powershell
git lfs install
git clone https://github.com/Ashik-Shaju/WAVV.git
Set-Location WAVV
git lfs pull
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

If Gradle cannot find the Android SDK, open the project in Android Studio or set `ANDROID_HOME`/`ANDROID_SDK_ROOT`. The local `local.properties` file is intentionally not tracked.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The app's bundled model assets use Git LFS; `git lfs pull` is required for semantic search. Room generates its schema snapshot under `app/schemas/` during the build.

## Third-party notices

Review `app/src/main/assets/dclap/v1/THIRD_PARTY_NOTICES.txt` and `app/src/main/assets/licenses/Nunito-OFL.txt` before redistributing a build. The release variant currently uses debug signing for local testing and is not configured for production distribution.

There is currently no root-level license for Wavv's own source code.

# Troubleshooting

## The build cannot find Android SDK files

Install the Android SDK Platform listed in the root README, open the project in Android Studio, or set <code>ANDROID_HOME</code> or <code>ANDROID_SDK_ROOT</code>. <code>local.properties</code> is intentionally not tracked.

## Local analysis does not run

Install Git LFS and fetch the repository assets with <code>git lfs pull</code>. A checkout that contains only LFS pointer files may still compile, but local analysis needs the actual assets. Review the full Gradle or runtime error to distinguish missing files from a Kotlin or Android dependency issue.

## No tracks appear

Confirm that the selected source contains audio and Android still grants access. The all-device option uses Android's indexed music collection; files Android does not classify as music may be easier to reach by selecting them or their folder directly. Use **You → Music Folders** to change the source, then retry after any access error.

## A genre category or cover is missing

Categories need genre information from file metadata or completed local analysis, plus readable artwork for at least one matching track. Check the file's tags, Android media provider, and analysis progress. Wavv does not infer genres from cover art or substitute unrelated artwork.

## A descriptive or language search returns no result

Descriptive search may need local analysis results; basic title and metadata matching can still work when analysis is unavailable. Language filtering requires a recognized language value in the file or media track. Search does not inspect lyrics.

Check analysis progress on the **You** screen and retry after restoring source access.

## A file is listed but will not play

Finding a file does not guarantee that the device can decode its container and codec. Check that Android can still read the source URI and try a format supported by the device. Reconnect an audio device or choose a route in Android settings if it is not available in Wavv.

## USB bit-perfect mode is not active

The setting requires Android 14 or newer, a compatible USB audio output, and a PCM format that the output advertises. Check the current status shown under **You → Bit-perfect mode**. Unsupported routes or formats continue with normal playback.

## UHQ AAC does not use the enhanced copy

UHQ is off by default and supports only AAC that Android exposes in the supported decoder format. Enable **You → UHQ AAC** and allow processing to finish. If the input is unsupported or processing fails, Wavv keeps using the original; the source file is not changed.

## Lyrics are unavailable

Check for supported embedded lyrics, or import a <code>.ttml</code>, <code>.lrc</code>, or plain-text lyric file from the player. **Automatically check for lyrics** is off by default. When enabled, Wavv looks for matching <code>.ttml</code> or <code>.lrc</code> files in selected music folders; for the all-device library it searches Downloads only when Android grants whole-storage access.

## What to include in a bug report

Include the Wavv version, Android version and device model, the exact task or screen action, and the relevant error text. Do not attach private audio, local project folders, or access tokens. State whether you reproduced the issue in a JVM test, emulator, or physical device.

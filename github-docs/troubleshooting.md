# Troubleshooting

## The build cannot find Android SDK files

Install the Android SDK platform specified in the root README, open the project in Android Studio, or set `ANDROID_HOME`/`ANDROID_SDK_ROOT`. `local.properties` is intentionally local and should not be committed.

## The build reports a missing or invalid model asset

Install Git LFS and pull the repository's LFS-managed files before building. A source checkout without the actual model files cannot package or run the bundled analysis. Review the full Gradle error to distinguish a missing asset from an ONNX Runtime or Kotlin compile failure.

## No tracks appear

Confirm the selected source contains audio and that Android still grants access. The all-device option uses Android's indexed music collection; recordings or files excluded by Android may be easier to access by selecting their folder or individual files. For a folder, select the folder itself and wait for its descendants to finish indexing. Use **You → Music Folders** to change the source, then retry if Wavv reports an access error.

## A genre category or cover is missing

Categories are made from actual genre metadata and require readable artwork for at least one track in that genre. Wavv does not add synthetic tags or substitute unrelated album art. Check the file's embedded tags or the device media provider.

## A descriptive or language search returns no result

Descriptive matching needs a readable local catalog and current embeddings for semantic ordering; if analysis failed, basic metadata matching may still work. A language filter is stricter and accepts only recognized language metadata explicitly found in the file or media track. A track with no such value remains unknown and is excluded from a language-specific result.

Check analysis status on the You screen and retry after fixing source access. Search does not inspect lyrics.

## A file is listed but will not play

Catalog recognition does not certify that the device has a decoder for the container and codec. Try a format supported by the Android device, check that the source URI remains readable, and review the playback error. Bluetooth and external routes are reported by Android; reconnect the device or choose the route in Android settings if it is not available in Wavv.

## Lyrics are unavailable

Wavv reads supported lyrics embedded in the selected file. It does not download lyrics. A file without a readable lyrics field will show no lyrics.

## What to attach to a useful bug report

Include the Wavv version, Android version and device model, the exact Gradle task or screen/action, and the relevant error text. Do not attach private audio, model files, access tokens, or unrelated local project folders. State whether the failure was reproduced in a JVM test, an emulator, or a physical device.

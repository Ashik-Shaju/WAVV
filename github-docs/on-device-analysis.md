# On-device analysis

Wavv can analyze audio in the selected local library to support descriptive search and music tags. The results stay on the device and do not change the original audio or its embedded metadata.

## When it runs

After the library is refreshed, Wavv schedules analysis as background work. The **You** screen shows progress and allows a retry after a failed or cancelled run. Analysis does not require network connectivity.

The ONNX assets are tracked with Git LFS. Run <code>git lfs pull</code> before building and using local analysis. A checkout without the actual LFS files may still compile, but analysis cannot run.

## What it provides

Descriptive search can use local audio analysis to rank tracks against the words in a query. Separately, music-tag analysis can add inferred tags to the local catalog. These results are estimates, not verified facts about a track, and they do not replace metadata read from the file.

If analysis is unavailable or incomplete, Wavv can still search available titles and file metadata. Missing information remains unknown; the app does not write inferred values into the original file.

## Limits

A source may not be readable or decodable on a particular device. Analysis may also fail if required assets are missing or processing fails. Speed, memory use, and decoder support vary by device; the presence of a test or bundled file does not establish performance on every device.

UHQ AAC is a separate playback feature, not part of catalog analysis. See [Playback](playback.md) for its behavior.

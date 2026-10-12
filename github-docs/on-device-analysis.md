# On-device analysis

Wavv bundles two distinct analysis paths. One makes audio available for descriptive text search; the other estimates structured music tags. Neither changes the original file, and neither result is guaranteed truth.

## When analysis runs

After the library catalog is refreshed, `DclapIndexScheduler` queues a unique WorkManager job. The worker reports separate progress phases and processes the local catalog. It requires storage not to be low, retries I/O failures, and does not require network connectivity. The You screen shows progress and offers retry after a failed or cancelled run.

The app copies versioned model assets into its private files directory when needed and verifies the model files against their bundled hashes. Large model assets are tracked with Git LFS; run `git lfs pull` to fetch the actual model files. A checkout containing only LFS pointer text may still compile, but model integrity checks will fail when analysis tries to install those assets. A missing asset or inaccessible source can leave analysis incomplete without changing the selected audio.

## Audio-text similarity

The bundled audio encoder turns each indexed track into a normalized 512-value embedding. The implementation decodes audio in overlapping windows and combines the window outputs into one track vector. At search time, the bundled text encoder embeds the remaining descriptive query and compares it with current track vectors. These scores order semantic matches; they are not genre, language, or mood labels.

The audio model uses FP32 weights and the text model uses INT8 weights. This path is configured with ONNX Runtime's CPU provider. Embeddings are stored in Room with a model identity and source-file fingerprint; a result is reused only while those values remain current.

## Structured music tags

The separate music-understanding path decodes the track, computes local audio features, and applies per-label probability heads. Windows are aggregated across the full track and labels are kept only when they meet their configured thresholds. The resulting values are estimates with confidence thresholds, not tags written back to the file.

This runtime tries ONNX Runtime's XNNPACK execution provider and falls back to CPU if provider setup fails. Results are stored separately in Room with model identity and source-file size/modified time. DCLAP embeddings and music tags are distinct outputs and are not interchangeable.

## Failure and validation limits

Analysis can fail when Android cannot read or decode a source, a required model asset is unavailable, or inference fails. WorkManager reports the failure and the app can retry; search can still use text and file metadata when semantic analysis is unavailable. Device-specific speed, memory use, inference behavior, and decoder compatibility require instrumented tests on that device. The presence of those tests or bundled models alone does not prove runtime success.

Check the model-specific `LICENSE-*` and `THIRD_PARTY_NOTICES.txt` beside each asset before distributing a build. See [Redistribution](redistribution.md).

The optional UHQ AAC playback enhancement is separate from these catalog-analysis paths. It does not produce search embeddings or music tags; see [Playback](playback.md) for its supported input and cache behavior.

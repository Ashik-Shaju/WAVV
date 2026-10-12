# Playback

Wavv delegates audio decoding and device output to Android and Media3. Its app code owns the queue and controls, but it does not ship a separate decoder for every audio format.

## Playback components

`PlaybackController` connects the Compose UI to a Media3 `MediaController`. `PlaybackService` owns ExoPlayer and a Media3 `MediaSession`, so the player is not owned by a screen composable. The service publishes session state and receives transport actions while it is alive. The service stops playback when Android reports that the task was removed.

ExoPlayer uses the platform renderer factory with decoder fallback enabled, configures music audio attributes and audio focus, and pauses when Android reports that audio output has become noisy. Android's installed codecs and Media3's support for a container/codec combination determine whether a particular file plays. An unsupported format is reported as a playback error; discovering a file is not the same as decoding it.

## Queue behavior

- Starting a track sets a queue with that track first, available personalized suggestions next, and remaining catalog tracks after them. Starting a smart mix uses that mix's tracks as the queue.
- **Play next** inserts a track immediately after the current track. **Add to queue** appends it. The current track cannot be removed from the active queue.
- Upcoming tracks can be moved or removed. Shuffle rearranges upcoming tracks without changing the current item or the already-played prefix. Turning shuffle off restores the managed queue order.
- Previous restarts the current track after it has played for more than three seconds; otherwise it goes to the previous queue item when one exists.
- Repeat cycles through off, repeat queue, and repeat one.
- Listening events are written after player state confirms playback, not merely when a play request is issued.

## Experimental AAC enhancement

The **UHQ AAC** setting in **You** is off by default. When enabled, the service processes AAC that Android reports with the supported AAC decoder type. Playback starts from the source; after background processing succeeds, Wavv switches to a cached PCM WAV at the same position. Unsupported formats or processing failures continue from the source. Turning the setting off returns the current item to its source, and the source file is never modified.

Enhanced files live in the app cache, which is limited to 1 GiB for UHQ audio and may be evicted by Wavv or Android. The player’s UHQ badge is not a bit-perfect, high-resolution, or listening-quality certification. Android decoding, model quality, and performance vary by device; UHQ AAC has not been validated across devices.

## Audio output routes

The output panel uses Android `MediaRouter` to display enabled routes matching its live-audio selector. Bluetooth, wired, USB, built-in speaker, and external routes appear only when the system exposes a compatible route. Android and the route provider control device selection and routing behavior. Wavv cannot guarantee support for every accessory or audio format.

The current player UI displays an **UHQ** badge, but the playback path does not verify bit-perfect output or a device's high-resolution capability. Treat that badge as unverified; it is not a certification or an output-quality measurement.

For model inference and its separate execution providers, see [On-device analysis](on-device-analysis.md); those are not playback decoders.

# Playback

Wavv delegates audio decoding and device output to Android and Media3. Its app code owns the queue and controls, but it does not ship a separate decoder for every audio format.

## Playback components

`PlaybackController` connects the Compose UI to a Media3 `MediaController`. `PlaybackService` owns ExoPlayer and a Media3 `MediaSession`, so the player is not owned by a screen composable. The service publishes session state and receives transport actions while it is alive. The service stops playback when Android reports that the task was removed.

ExoPlayer uses the platform renderer factory with decoder fallback enabled, configures music audio attributes and audio focus, and pauses when Android reports that audio output has become noisy. Android's installed codecs and Media3's support for a container/codec combination determine whether a particular file plays. An unsupported format is reported as a playback error; discovering a file is not the same as decoding it.

## Queue behavior

- Starting a track directly leaves the upcoming queue empty. Starting from a playlist, mix, album, artist, genre, or other collection puts the remaining tracks from that source in the queue and shows the source name. The current track is displayed separately from upcoming tracks.
- AutoPlay is on by default and uses the app's local personalized recommendations. It shows up to ten suggestions, separate from the queue. Suggestions can be reordered, played next, or appended to the queue. Turning AutoPlay off hides its suggestions and prevents it from adding a song when the queue ends; turning it back on restores the same remaining suggestion order. If the queue ends while AutoPlay is on, its first suggestion is appended and played.
- **Play next** inserts a track immediately after the current track. **Add to queue** appends it after the existing session queue. A suggestion promoted to the queue is removed from AutoPlay.
- Upcoming queue tracks can be moved or removed; AutoPlay suggestions can only be reordered within AutoPlay. Shuffle changes only the upcoming queue, leaves AutoPlay untouched, and reuses the previous shuffle order when enabled again. Turning shuffle off restores the managed queue order. The player retains the session timeline as songs play, with no fixed queue length.
- Previous restarts the current track after it has played for more than three seconds; otherwise it goes to the previous queue item when one exists.
- Repeat cycles through off, repeat queue, and repeat one.
- Listening events are written after player state confirms playback, not merely when a play request is issued.

## Crossfade

Crossfade is off by default and can be configured in **You** for up to 12 seconds. When the current and next queue items are eligible, the service prepares the next track and blends both players with an equal-power fade. It uses an additional decoder while enabled; UHQ-enhanced tracks and ineligible transitions use normal playback instead.

## Audio quality details

The full player may show a source-quality label such as **Standard**, **High Quality**, **Lossless**, or **Hi-Res Lossless**. Tap it to view the available codec, bit depth, sample rate, bitrate, and channel details; an estimated bitrate is marked. These labels use metadata exposed by Android and the selected file. They do not verify recording provenance, mastering, audible quality, or bit-perfect output.

## Experimental AAC enhancement

The **UHQ AAC** setting in **You** is off by default. When enabled, the service processes AAC that Android reports with the supported AAC decoder type. Playback starts from the source; after background processing succeeds, Wavv switches to a cached PCM WAV at the same position. Unsupported formats or processing failures continue from the source. Turning the setting off returns the current item to its source, and the source file is never modified.

Enhanced files live in the app cache, which is limited to 1 GiB for UHQ audio and may be evicted by Wavv or Android. The player’s UHQ badge is not a bit-perfect, high-resolution, or listening-quality certification. Android decoding, model quality, and performance vary by device; UHQ AAC has not been validated across devices.

## Audio output routes

The output panel uses Android `MediaRouter` to display enabled routes matching its live-audio selector. Bluetooth, wired, USB, built-in speaker, and external routes appear only when the system exposes a compatible route. Android and the route provider control device selection and routing behavior. Wavv cannot guarantee support for every accessory or audio format.

The current player UI displays an **UHQ** badge, but the playback path does not verify bit-perfect output or a device's high-resolution capability. Treat that badge as unverified; it is not a certification or an output-quality measurement.

For model inference and its separate execution providers, see [On-device analysis](on-device-analysis.md); those are not playback decoders.

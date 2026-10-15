# Playback

Wavv uses Android and Media3 for audio decoding and device output. The app owns playback controls and queue behavior, but does not ship a separate decoder for every format.

## Playback components

<code>PlaybackController</code> connects the Compose UI to a Media3 <code>MediaController</code>. <code>PlaybackService</code> owns ExoPlayer and a Media3 <code>MediaSession</code>, so playback is not owned by a screen composable. The service publishes playback state and receives transport actions while it is alive.

Android's codecs and Media3's support for a container and codec combination determine whether a particular file plays. Finding a file does not guarantee that the device can decode it. An unsupported format is reported as a playback error.

## Queue behavior

- Starting a track directly leaves the upcoming queue empty. Starting from a collection queues the remaining tracks from that source.
- AutoPlay is on by default and keeps a separate list of up to ten suggestions. Suggestions can be reordered, played next, or added to the queue. If the queue ends while AutoPlay is on, its first suggestion is added and played.
- **Play next** inserts a track after the current one. **Add to queue** appends it after the session queue. A suggestion moved to the queue is removed from AutoPlay.
- Upcoming queue tracks can be moved or removed. Shuffle affects the upcoming queue and leaves AutoPlay suggestions separate.
- Previous restarts the current track after it has played for more than three seconds; otherwise it moves to the previous queue item when one exists.
- Repeat cycles through off, repeat queue, and repeat one.
- Listening events are recorded after player state confirms playback, not merely when a play request is issued.

## Crossfade

Crossfade is off by default and can be set in **You** for up to 12 seconds. For eligible transitions, the service prepares the next track and blends the two players. Crossfade uses an additional decoder. It is bypassed while USB bit-perfect mode is active and for transitions that need the AAC enhancement path; the saved duration is retained.

## USB bit-perfect mode

**Bit-perfect mode** is an opt-in request to Android's bit-perfect mixer mode. It requires Android 14 or newer, a connected compatible USB output, and an active PCM format that the output advertises. Wavv shows the current status in **You**, since an enabled setting may not be active for the current route or track.

While active, Android bypasses framework mixing and volume scaling. Wavv pauses equalizer, UHQ processing, and crossfade, so use the USB device's hardware volume control. Other routes continue with normal playback. The setting and status do not certify the entire output signal chain or audible quality.

## Audio quality details

The full player may show a source-quality label such as **Standard**, **High Quality**, **Lossless**, or **Hi-Res Lossless**. Tap it to view codec, bit depth, sample rate, bitrate, and channel details that Android can read; estimated bitrate is marked. These labels do not verify mastering, audible quality, or bit-perfect output.

## Experimental AAC enhancement

The **UHQ AAC** setting in **You** is off by default. When enabled, the service processes AAC that Android reports with the supported decoder type. Playback starts from the source; after background processing succeeds, Wavv switches to a cached PCM WAV at the current position. Unsupported formats or processing failures continue from the source. Turning the setting off returns the current item to its source, and the source file is never modified.

Enhanced files use the app cache, which is limited to 1 GiB for UHQ audio and may be evicted by Wavv or Android. The UHQ indicator is not a bit-perfect or high-resolution certification. Android decoding and performance vary by device; UHQ has not been validated across devices.

## Audio output routes

The output panel lists enabled routes currently reported to Wavv, including compatible Bluetooth, wired, USB, built-in, or external routes. Android and the route provider control the available devices and routing behavior. Wavv cannot guarantee support for every accessory or audio format.

For local library analysis, see [On-device analysis](on-device-analysis.md). Those features are separate from playback decoding.

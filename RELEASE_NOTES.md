# Wavv 0.3.0 — APK Size & Performance Optimization

Major package size reduction and build optimizations:

- **Targeted Architecture:** Release builds now exclusively target 64-bit ARM (`arm64-v8a`), dropping unneeded 32-bit and emulator binaries to reduce download size by ~97 MB.
- **Unified On-Device ML Assets:** Merged DCLAP audio model tensors into a self-contained ONNX model, eliminating separate external data file requirements and automatically cleaning up legacy model data upon upgrade.
- **DEX & Memory Optimization:** Pruned `material-icons-extended` in favor of core Compose vectors, significantly reducing DEX method count and multidex footprint.
- **Resource & Graphic Compression:** Converted large background assets to WebP at 100% quality and regenerated launcher icon mipmaps at precise density resolutions, saving ~7 MB with zero loss in visual clarity.
- **Locale Filtering:** Filtered unused third-party framework translations from release packaging.

# Wavv 0.2.0 — Random Playback & Release Improvements

- Double-tap the Wavv logo on Home to play a random local track.
- Personalized mix titles now pick up an accent color from their artwork.
- Build an unsigned release APK without signing credentials, or a signed APK when all signing values and the keystore are available.

# Wavv 0.1.0 — Initial Release

Initial release of Wavv, a private, on-device music player.

- Browse and search music stored on your device.
- Build playlists and manage your playback queue.
- Use local music analysis, equalizer, crossfade, and optional AAC enhancement.

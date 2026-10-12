# Wavv documentation

Wavv is a local-first Android music player. It indexes audio the user selects, stores library state on the device, plays through Android's media stack, and runs its bundled music-analysis features locally. The current app has no sign-in, cloud library, or streaming catalog.

## Start here

| If you are… | Read |
| --- | --- |
| Using Wavv | [User guide](user-guide.md) |
| Building or testing the app | [Development](development.md) |
| Understanding how parts fit together | [Architecture](architecture.md) |
| Working on local files and metadata | [Library and metadata](library-and-metadata.md) |
| Changing audio controls or routing | [Playback](playback.md) |
| Changing search or recommendations | [Search and personalization](search-and-personalization.md) |
| Changing on-device analysis | [On-device analysis](on-device-analysis.md) |
| Reviewing local storage and permissions | [Privacy and local data](privacy.md) |
| Diagnosing a problem | [Troubleshooting](troubleshooting.md) |
| Sharing source or an app build | [Redistribution and notices](redistribution.md) |
| Checking model and dataset terms | [Model and data provenance](model-and-data-provenance.md) |

The root [README](../README.md) is the single source for build prerequisites and commands. Dependency versions live in `gradle/libs.versions.toml`; the source and tests are the authority for current behavior.

## Scope and evidence

These guides describe the implementation in this repository. A documented feature is not automatically proven on every Android version, codec, or output device. See [Development](development.md) for what local and device tests establish, and [Redistribution](redistribution.md) for source and bundled-component notice requirements.

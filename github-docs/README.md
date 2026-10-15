# Wavv documentation

Wavv indexes audio selected on an Android device, keeps its library and listening state locally, and plays through Android's media stack. It has no sign-in, cloud library, or streaming catalog.

## Start here

| If you are… | Read |
| --- | --- |
| Using Wavv | [User guide](user-guide.md) |
| Building or testing the app | [Development and testing](development.md) |
| Understanding the app structure | [Architecture](architecture.md) |
| Working with local files and metadata | [Library and metadata](library-and-metadata.md) |
| Changing playback controls or output | [Playback](playback.md) |
| Working on search or recommendations | [Search and personalization](search-and-personalization.md) |
| Working on local analysis | [On-device analysis](on-device-analysis.md) |
| Reviewing stored information and permissions | [Privacy and local data](privacy.md) |
| Diagnosing a problem | [Troubleshooting](troubleshooting.md) |
| Reviewing license notices before sharing a build | [Redistribution and notices](redistribution.md) |

The root [README](../README.md) contains the build prerequisites and commands. Dependency versions are in <code>gradle/libs.versions.toml</code>. The committed source and tests are the authority for current behavior.

## Scope and evidence

These guides describe the implementation in the committed repository. A feature description is not proof that it works on every Android version, codec, or output device. Tests listed in the development guide are present in source; their presence alone does not mean they have passed. Check [Development and testing](development.md) for test boundaries and [Redistribution](redistribution.md) for the notices stored with committed assets.

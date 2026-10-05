# Redistribution and notices

This repository currently has no root `LICENSE` file for Wavv's own source. Do not assume the source is available for reuse under an open-source license until the project owner adds one.

The app also bundles third-party code, models, tokenizers, and other assets. Their terms can differ from the app source and from one another. Review the notices shipped with each component before redistributing a build:

- [Audio-search notices](../app/src/main/assets/dclap/v1/THIRD_PARTY_NOTICES.txt)
- [Music-analysis notices](../app/src/main/assets/music-understanding/v1/THIRD_PARTY_NOTICES.txt)
- [Nunito font license](../app/src/main/assets/licenses/Nunito-OFL.txt)

The presence of a notice file is not, by itself, proof that every bundled artifact may be redistributed. Keep required notices with the corresponding components and verify the actual files and terms for the intended release.

The current release build uses Android's debug signing configuration for local testing. It is not a production-signed release; configure and protect a production signing key before publishing an APK.

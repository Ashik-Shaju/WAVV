# Redistribution and notices

This repository has no root `LICENSE` file for Wavv's own source. Do not assume the app source is available for reuse under an open-source license.

The app contains third-party models and other assets with separate terms. Check the exact component [audio-search notices](../app/src/main/assets/dclap/v1/THIRD_PARTY_NOTICES.txt), [music-analysis notices](../app/src/main/assets/music-understanding/v1/THIRD_PARTY_NOTICES.txt), and [font license](../app/src/main/assets/licenses/Nunito-OFL.txt) before redistributing a build. A notice for reference code, a tokenizer, or a font does not establish rights to related model weights or training data.

New assets may be considered when a clear permissive license covers the exact files and intended use, and required notices can be kept with them. MIT, Apache-2.0, BSD, ISC, CC0, and OFL are examples for code and assets. For datasets, check audio, metadata, and annotations separately. Do not infer rights from a repository's presence in Git or from an upstream statement about only part of a model or dataset. See [model and data provenance](model-and-data-provenance.md) for the permissively licensed components documented for this repository. This page is an engineering guide, not a legal determination.

The current app build is for local testing and uses Android's debug signing configuration. It is not a production-signed release; configure and protect a production signing key before publishing an APK.

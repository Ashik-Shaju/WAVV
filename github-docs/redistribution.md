# Redistribution and notices

This repository does not currently contain a root-level <code>LICENSE</code> file for Wavv's own source. Do not assume that the app source is available for reuse under an open-source license.

## Committed third-party notices

The committed notices explicitly identify these permissively licensed components:

| Component | Terms recorded in the repository | Scope |
| --- | --- | --- |
| LAION CLAP text head | [CC0 1.0](../app/src/main/assets/dclap/v1/LICENSE-CLAP-CC0-1.0.txt) | The text-head files named by the DCLAP notice |
| RoBERTa tokenizer files | [MIT](../app/src/main/assets/dclap/v1/LICENSE-Roberta-MIT.txt) | The tokenizer vocabulary and merges named by the DCLAP notice |
| EfficientAT reference implementation | [MIT](../app/src/main/assets/music-understanding/v1/LICENSE-EfficientAT.txt) | The reference implementation only, as stated by its notice |
| Nunito font | [SIL Open Font License 1.1](../app/src/main/assets/licenses/Nunito-OFL.txt) | The bundled font files |

These terms do not apply automatically to adjacent assets or Wavv's own source. Full notices for the named asset groups are committed beside the assets they identify:

| Committed asset area | Notice or license file |
| --- | --- |
| Local search assets | [DCLAP third-party notices](../app/src/main/assets/dclap/v1/THIRD_PARTY_NOTICES.txt), with the component license texts in the same directory |
| Music-tag analysis assets | [Music Understanding third-party notices](../app/src/main/assets/music-understanding/v1/THIRD_PARTY_NOTICES.txt) and [EfficientAT license](../app/src/main/assets/music-understanding/v1/LICENSE-EfficientAT.txt) |
| Nunito font | [SIL Open Font License 1.1](../app/src/main/assets/licenses/Nunito-OFL.txt) |

The notices name separate components and their scope. A permissive license for one component, such as MIT or CC0, does not automatically apply to adjacent files. Review the exact notice and license files for every asset included in a build, and keep required notices with redistributed copies. The <code>.gitattributes</code> file identifies ONNX files as Git LFS content; fetch those files before packaging a build.

The tracked tree does not include a license or notice beside <code>app/src/main/assets/uhq/aac-v1/uhq_aac.onnx</code> or <code>app/src/main/res/font/galaxie_copernicus_book.ttf</code>. Check the terms for those files before redistributing a build that contains them.

For new committed assets, use terms that clearly cover the exact files and intended use, and keep the required notices with them. Permissive licenses such as MIT, Apache-2.0, BSD, ISC, CC0, and OFL can be documented when the committed notice identifies the covered component. This page records repository files; it is not a legal determination.

## Build signing

The current Gradle release configuration uses the debug signing configuration. Build and install the debug APK for local testing; configure and protect a production signing key before publishing an APK.

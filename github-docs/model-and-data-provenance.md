# Model and data provenance

This page lists only bundled components with documented permissive terms. It is not a complete inventory of Wavv's models or training datasets, and entries for code or tokenizers do not grant rights to related model weights or source media.

## Permissively licensed components

| Component | Documented terms | Scope |
| --- | --- | --- |
| LAION-CLAP text head | CC0 1.0 | The bundled notice applies to the text-head files it identifies. It does not establish rights to the complete audio-search model or its training corpus. |
| RoBERTa tokenizer | MIT | The bundled notice applies to the tokenizer component, not other model weights. |
| EfficientAT reference implementation | MIT | The bundled notice identifies the reference code license. It does not assign a separate license to pretrained or Wavv model weights. |
| Nunito font | SIL Open Font License 1.1 | Keep the [font license](../app/src/main/assets/licenses/Nunito-OFL.txt) with the font asset. |

See the [audio-search notice](../app/src/main/assets/dclap/v1/THIRD_PARTY_NOTICES.txt) and [music-analysis notice](../app/src/main/assets/music-understanding/v1/THIRD_PARTY_NOTICES.txt) for the exact component boundaries and required notices.

## Dataset scope

No complete training dataset is listed here as permissively licensed. A dataset's metadata or annotation license does not automatically cover its audio, and an implementation license does not cover model weights trained from that dataset. Check the license and intended use for each exact audio file, metadata set, annotation set, checkpoint, and export before redistribution.

This page does not clear the complete model or dataset stack for release. It records only the components above whose notices name permissive terms; other lineage remains outside this public inventory.

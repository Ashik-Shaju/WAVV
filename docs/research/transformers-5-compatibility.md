# Transformers 5 compatibility review

Date: 2026-09-22

## Decision

Keep `transformers==4.57.1` in the production DCLAP environment. Do not upgrade the shared environment to 5.17.0 yet. If a future Hugging Face model specifically requires Transformers 5, use a separate experiment environment and promote it only after tokenizer, embedding, retrieval, and export parity checks.

## What the current project actually uses

- `dclap/dclap_evaluation.py` and `dclap/prepare_device_fixture.py` use only `AutoTokenizer.from_pretrained(..., local_files_only=True, use_fast=True)` and the tokenizer call for the DCLAP 77-token contract.
- The DCLAP audio and text inference models are ONNX models run by ONNX Runtime. Transformers is not the Android runtime.
- DCLAP text token IDs are part of the model contract. A tokenizer/backend or special-token change can change the text embedding even when the ONNX graph is unchanged.

## Planned-model impact

| Planned model | Transformers 5 benefit | Risk / decision |
|---|---|---|
| Native DCLAP text tower | None for inference; current API is sufficient | Keep 4.57.1 and preserve tokenizer parity |
| MiniLM/e5/GTE text projection experiments | Possible benefit from newer model/tokenizer support | Isolate per experiment; retrain the projection with the exact tokenizer/version |
| DyMN04-AS Music Understanding | None in the current EfficientAT/DyMN plan | Keep outside Transformers |
| Singing-LID compact CNN | None | Keep outside Transformers; a WavLM/Wav2Vec2 teacher is only a later fallback |
| UHQ residual/time-frequency model | None; the plan explicitly avoids a Transformer foundation model | Keep outside Transformers |
| iALS and hybrid ranker | None | Keep outside Transformers |

## Evidence from primary sources

Hugging Face describes Transformers v5 as a major API/inference evolution and documents tokenizer backend changes, including a decoupled tokenizer architecture and multiple backend types. That is useful for new HF model families, but it increases the need to pin and verify tokenization for a fixed embedding model:

- [Transformers v5 announcement](https://huggingface.co/blog/transformers-v5)
- [Tokenizer architecture and v5 changes](https://huggingface.co/blog/tokenizers)
- [Transformers v5.17 custom tokenizer documentation](https://huggingface.co/docs/transformers/v5.17.0/custom_tokenizers)
- [Fast tokenizer backend selection](https://huggingface.co/docs/transformers/main//fast_tokenizers)

## Required gate before any future upgrade

For every candidate model, compare the old and new environments using the same fixture:

1. exact `input_ids`, `attention_mask`, sequence length, padding, truncation, and special-token behavior;
2. FP32 embedding cosine similarity and MSE/L2;
3. Recall@K and mAP against the current retrieval baseline;
4. ONNX export and ONNX Runtime smoke tests where conversion is needed;
5. Android tokenizer parity for the final deployed text model.

The upgrade is acceptable only when these checks pass for the candidate model. A Transformers 5 upgrade does not improve the current Android DCLAP runtime by itself.

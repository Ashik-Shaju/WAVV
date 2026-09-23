# Wavv Music Understanding Implementation Research

Date: 2026-09-23

## Decision

Keep DyMN04-AS as the frozen-backbone **candidate baseline**, with Music Understanding separate from DCLAP. The checkpoint size and AudioSet score support a feasibility choice, not validated Wavv-task accuracy. Before training, correct the label/split plan and lock an artifact, preprocessing, and evaluation manifest.

## Headline finding: the human-label set leaks across numbered splits

MTG's human-validated Music Classification Annotations are annotations for **split-0 test**, not a separate training corpus. MTG's split generator repeatedly shuffles artist IDs to create each numbered split; those split directories are alternative partitions, not mutually exclusive outer folds. I verified `track_0000214` occurs in split-0 test and split-1 train, and `track_0003112` occurs in test for both split-0 and split-1. See the [annotation description](https://github.com/MTG/mtg-jamendo-dataset/blob/master/derived/music-classification-annotations/README.md), [split generator](https://github.com/MTG/mtg-jamendo-dataset/blob/master/scripts/data_split.py), [split-0 train](https://raw.githubusercontent.com/MTG/mtg-jamendo-dataset/master/data/splits/split-0/autotagging-train.tsv), [split-0 validation](https://raw.githubusercontent.com/MTG/mtg-jamendo-dataset/master/data/splits/split-0/autotagging-validation.tsv), [split-0 test](https://raw.githubusercontent.com/MTG/mtg-jamendo-dataset/master/data/splits/split-0/autotagging-test.tsv), [split-1 train](https://raw.githubusercontent.com/MTG/mtg-jamendo-dataset/master/data/splits/split-1/autotagging-train.tsv), and [split-1 test](https://raw.githubusercontent.com/MTG/mtg-jamendo-dataset/master/data/splits/split-1/autotagging-test.tsv).

**Safe protocol:** reserve the human-annotation track IDs as a locked final evaluation set. Fit and tune using split-0 train and validation only; exclude every human-annotation ID from every training/tuning manifest, even if another numbered split assigns it to train or validation. Audit track IDs, artist IDs, and exact/near-duplicate audio across datasets before fitting. Do not treat another numbered split as an independent holdout after training on these examples. Preserve the split-0 human evaluation for one final report.

## Exact task coverage and label limits

The clean human annotations contain unanimous, suitable labels for four genre taxonomies (only **411–612 tracks each**), six separate binary mood attributes (**5,678–7,686 each**), danceability (**4,476**), and voice/instrumental (**2,070**). The released taxonomies are single-label binary or multiclass tasks. There is **no human-validated instrument taxonomy** in this annotation release. Therefore:

- Use the main MTG-Jamendo uploader tags for genre, mood/theme, and instruments as weak multi-label training targets; the processed dataset has 87 genre, 40 instrument, and 56 mood/theme tags. Select a compact vocabulary using counts from training data only, and document label mappings.
- Use the clean human labels only for the tasks they actually cover and only as held-out evaluation in the baseline. Genre support is small; report per-class counts and uncertainty. Do not relabel the separate binary mood attributes as one mutually exclusive mood classifier.
- Vocal/instrumental is covered by the human labels. Instrument presence is not; characterize instrument results as noisy-tag supervision unless Wavv obtains independent instrument annotations.

The [MTG dataset README](https://github.com/MTG/mtg-jamendo-dataset) describes the uploader-provided tags and official train/validation/test manifests. The human-label README provides the exact class lists and support counts above.

## Model and input claims

The official [EfficientAT README](https://github.com/fschmid56/EfficientAT/blob/main/README.md) reports DyMN04-AS at 1.97M parameters, 0.12B MACs, and 45.0 mAP **on AudioSet**. That mAP is not evidence of Wavv genre, instrument, mood, or emotion quality. The [official DyMN implementation](https://raw.githubusercontent.com/fschmid56/EfficientAT/main/models/dymn/model.py) returns AudioSet logits plus a globally pooled final feature and can return intermediate feature maps. The [official inference code](https://raw.githubusercontent.com/fschmid56/EfficientAT/main/inference.py) defaults to mono 32 kHz, 128 mel bins, 800-sample (25 ms) windows, and 320-sample (10 ms) hops; AudioSet examples are 10 seconds. Pin the exact checkpoint/release, hash, preprocessing, feature tensor, and window policy in the model manifest.

The [Low-Complexity Audio Embedding Extractors paper](https://eurasip.org/Proceedings/Eusipco/Eusipco2023/pdfs/0000451.pdf) supports the general frozen-representation-plus-lightweight-head approach and 10-second scene pooling, but its reported feature-selection results are for **MobileNet**, not DyMN04-AS. Treat layer choice and song-window aggregation as hypotheses: compare the checkpoint's pooled feature first, and test an intermediate feature only if the first baseline is inadequate.

## Emotion data and distribution constraints

The [DEAM manual](https://cvml.unige.ch/databases/DEAM/manual.pdf) documents separate development and evaluation sets, 45-second excerpts plus full songs, 2 Hz dynamic labels in [-1, 1] with the first 15 seconds excluded, and uncertainty estimates. It says 2015 dynamic labels are more reliable, especially valence on the 2015 evaluation set. Use development data for fitting and keep evaluation labels untouched; align excerpt offsets and label timestamps, and carry the supplied standard deviations into evaluation. Since DEAM includes Jamendo recordings and MTG-Jamendo is Jamendo-sourced, deduplicate recordings across the two datasets before any split.

MTG metadata is CC BY-NC-SA 4.0 and the dataset is limited to non-commercial research/academic use; DEAM is also non-commercial. Check training-data and checkpoint/weight terms before distributing a trained model or bundling it in Wavv. See the [MTG license/use terms](https://github.com/MTG/mtg-jamendo-dataset#license) and [DEAM terms](https://cvml.unige.ch/databases/DEAM/manual.pdf).

## Minimal validation gates

1. **Artifact gate:** pin source revision/checkpoint and hash; record terms, runtime, input contract, output/features, and a fixed audio fixture. Verify finite outputs, expected shapes, and deterministic feature extraction against the official implementation.
2. **Data gate:** publish a task-to-label map and counts; freeze track/artist/audio-duplicate manifests; prove zero training/tuning overlap with split-0 human-evaluation IDs. Use only train for fitting and validation for taxonomy, thresholds, and hyperparameters.
3. **Frozen-head gate:** train the smallest suitable heads on a small pipeline slice, then run the frozen baseline. Compare with a majority/prior baseline. Report per-task metrics and support: multi-label MTG tags with mAP and per-label precision/recall; single-label human genre with macro-F1/confusion; binary human tasks with AP/F1; DEAM regression with MAE/RMSE/correlation. Select thresholds only on validation.
4. **Final evaluation gate:** run the locked human labels once for covered tasks, official held-out MTG test tags for weakly labelled tasks, and untouched DEAM evaluation data for emotion. Only then decide whether to expand training, selectively fine-tune, choose window aggregation, or export for device inference. Measure full-song latency and peak memory on target hardware separately from accuracy.

Current documentation's architecture separation, frozen-first policy, compact label intent, and windowed song analysis remain sound. The required correction is to make the annotation split and actual per-task coverage explicit before the first training run; no public source establishes Wavv-task accuracy for this proposed model.

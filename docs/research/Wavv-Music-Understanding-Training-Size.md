# Wavv Music-Understanding Training-Size Decision

Date: 2026-09-25

## Recommendation

The 800-track cap is the smallest checked cap that retained all eight planned heads under the declared support floors: 775 failed the mood-party floor. Those floors make evaluation reportable; they do not establish accuracy or an optimum. For a one-fit model intended to be the final version within this eight-task scope, use the documented 4,250-track expansion ceiling if an approximately 7-hour incremental extraction is acceptable and the current features can be safely migrated. The 800 current IDs were verified to retain the same split and artist metadata in the deterministic 4,250 manifest, though the cache migration is not implemented. Without migration, budget about 8 hours to extract all 4,250 rows. If that delay is unacceptable, the existing 800 rows can support a quick first fit, but its result is not validated as a strong final model until its locked artist-held-out metrics are measured.

This recommendation applies only to the current narrow output: seven binary mood/acoustic attributes and danceability. The selected labels do not cover genre, instruments, vocals, captions, or song-level temporal understanding. MTG says its three-annotator consensus annotations are suitable for classifier training, but annotator agreement varies by taxonomy and some constructs remain ambiguous. More rows improve support and evaluation precision; they do not turn these eight labels into a complete model or guarantee product-quality accuracy. [Pinned MTG annotation documentation](https://github.com/MTG/mtg-jamendo-dataset/blob/cafd8e20c265ed84f1e61f1c875327971f43a62f/derived/music-classification-annotations/README.md)

## Why 800 was selected

The frozen manifest draws 800 artist-disjoint tracks from 4,915 tracks marked download-allowed in the dated Jamendo inventory. It retains eight tasks that meet the local minimum track/artist reporting floors, with fit/validation/test split sizes of 560/120/120. The support sweep tried smaller caps; 775 failed `mood_party`, while 800 passed. This is the reason for 800, not a result showing that 800 is sufficient or optimal. No learning-curve experiment or Wavv-task result exists.

The 800 examples are not 800 labels for every head. Missing taxonomy labels are omitted per task:

| Task | Fit labeled tracks (class counts) | Validation labeled tracks (class counts) | Test labeled tracks (class counts) |
| --- | ---: | ---: | ---: |
| Danceability | 306 (139/167) | 76 (39/37) | 80 (41/39) |
| Mood acoustic | 386 (159/227) | 84 (33/51) | 85 (35/50) |
| Mood aggressive | 431 (114/317) | 100 (37/63) | 100 (30/70) |
| Mood electronic | 424 (222/202) | 99 (57/42) | 92 (48/44) |
| Mood happy | 386 (126/260) | 92 (31/61) | 91 (30/61) |
| Mood party | 428 (108/320) | 86 (30/56) | 90 (30/60) |
| Mood relaxed | 404 (152/252) | 94 (37/57) | 93 (33/60) |
| Mood sad | 380 (113/267) | 97 (31/66) | 89 (30/59) |

Counts are from the frozen local manifest; within each cell, class counts are in the order recorded in `mtg_human_manifest.json`. The set contains 251/47/41 fit/validation/test artists. Thus the training sample per head is only about 0.8–1.1 examples per 384 input dimensions, before accounting for artist correlation. L2 regularization and a frozen pretrained representation make this a sensible baseline, but do not establish that the learned mapping will generalize well. Test support is only 30–70 examples per class, so per-task metrics and artist-cluster intervals will be noisy.

The labels are unanimous human annotations on the selected records, not a full-track, population-representative sample. The official annotation page reports agreement rates from 44% to 71% for the selected mood/danceability taxonomies among the annotated set. The local test cohort is support-balanced, so its class proportions do not estimate MTG prevalence. [Pinned MTG annotation documentation](https://github.com/MTG/mtg-jamendo-dataset/blob/cafd8e20c265ed84f1e61f1c875327971f43a62f/derived/music-classification-annotations/README.md), [local frozen manifest](../../music%20understanding/reports/mtg_human_manifest.json)

## CPU training cost versus a larger cohort

The planned learner fits eight independent binary logistic heads over cached 384-value features. The selected heads contain 3,080 scalars total; lambda selection uses four values per task. The frozen DyMN04-AS backbone is not updated. Its 1.97M parameters and 0.12B MACs are published inference/model figures, not the amount of work for fitting these heads, and its AudioSet score is not Wavv-task evidence. [EfficientAT model table](https://github.com/fschmid56/EfficientAT/blob/main/README.md#pre-trained-models), [local locked recipe](../../docs/Wavv_Music_Understanding_Implementation_Plan.md)

For a temporary CPU timing run, I read the validated 800-row cache and used class-balanced samples of 10 and 20 labeled fit rows per task. Each timed grid fit all eight heads across the four planned lambda values, using BCE-with-logits, weight-only L2, and LBFGS (`max_iter=20`, `max_eval=25`), with one CPU thread. After a warm-up, 12 repeated grids averaged 0.295 seconds at 10 rows and 0.310 seconds at 20 rows. Scaling against the actual 306–431 labeled fit rows per task estimates roughly 5–12 seconds for the 800-row optimizer grid. That estimate excludes Python startup, cache reading, validation scoring, and model-file writing; allow tens of seconds for the complete head-training command. No final weights were saved and full training has not started.

The 4,250-row matrix is about 5.3 times larger than the current 800-row matrix. If task label counts and optimizer convergence scale similarly, the same eight-head grid should still take under a minute; that is an extrapolation, not a measured full-cohort run. The raw feature matrix is only about 6.2 MiB, and the matrices and optimizer state are small for an i5-1235U with 16 GB RAM. [PyTorch LBFGS documentation](https://docs.pytorch.org/docs/stable/generated/torch.optim.LBFGS.html)

Extraction dominates. A fresh 10-track sequential benchmark using additional IDs from the deterministic 4,250 manifest took 68.6 seconds, or 6.85 seconds per track: 6.56 seconds downloading and 0.29 seconds decoding/inference on average. The one-time model load took 1.55 seconds. This projects to about 1.52 hours for 800 tracks, 8.09 hours for all 4,250 from scratch, or 6.57 additional hours for 3,450 tracks if the existing features are migrated. The sample averaged 3.42 MB per audio file, projecting about 2.74 GB and 14.54 GB transferred, respectively; each source audio file was deleted immediately after processing. The earlier 72-track batch averaged 7.27 seconds per track, showing expected network variation. The current cache is bound to its complete manifest digest, and the extractor rejects it after the manifest changes. A temporary expansion reproduced all 800 existing IDs with identical split and artist metadata, so migration can reuse them after a new-digest cache migration is implemented and validated. [Local extraction report](../../music%20understanding/reports/feature_extraction.json), [extractor cache checks](../../music%20understanding/scripts/extract_mtg_features.py)

The extracted feature cache is small and source OGGs are deleted after each track, but a larger run costs several hours of network/audio processing unless migration is added and verified. Choose the cap before training so the one fit and one held-out evaluation correspond to the intended final cohort. Do not silently tune the 4,250 manifest after looking at test results.

## Decision limit

No Wavv-task training, validation result, or learning curve exists yet. The larger cap is the stronger one-shot data choice, not a validated accuracy guarantee. After the one fit, keep a task only if validation beats its majority baseline and the locked test evaluation supports it; a weak task should be omitted rather than called final. If the desired product means broad music understanding rather than these eight attributes, the present label set is insufficient regardless of whether it uses 800 or 4,250 tracks.

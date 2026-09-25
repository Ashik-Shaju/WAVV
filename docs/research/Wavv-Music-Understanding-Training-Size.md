# Wavv Music-Understanding Training-Size Decision

Date: 2026-09-25

## Recommendation

The original 800-track cap was the smallest checked size that retained all eight human-consensus tasks under the declared support floors: 775 failed the mood-party floor. The user has since approved 4,250 tracks for the one planned fit. The frozen 4,250 manifest contains eight human-consensus tasks plus nine uploader-tag targets (five genres and four instruments) whose positive and negative track/artist support passes the same per-split floors. The prior 800 IDs retain identical split, artist, and Jamendo mappings. Cache seeding is now implemented and validated; therefore only 3,450 tracks need downloading. The 800-support decision below is historical pilot evidence, not the adopted final cap.

This remains a bounded first model, not a broad music-language or temporal-understanding model. The eight human targets use unanimous annotation rows; the nine uploader targets use tag presence/absence and are weaker evidence. An absent uploader tag means “not tagged,” not confirmed acoustic absence. Neither this cohort nor its support counts guarantee model quality. [Pinned MTG annotation documentation](https://github.com/MTG/mtg-jamendo-dataset/blob/cafd8e20c265ed84f1e61f1c875327971f43a62f/derived/music-classification-annotations/README.md)

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

## CPU training cost for the approved cohort

The planned learner fits 17 independent binary logistic heads over cached 384-value features: eight human-consensus and nine uploader-tag tasks. The heads contain 6,545 trainable scalars; lambda selection uses four values per task. The frozen DyMN04-AS backbone is not updated. Its 1.97M parameters and 0.12B MACs are published inference/model figures, not the amount of work for fitting these heads, and its AudioSet score is not Wavv-task evidence. [EfficientAT model table](https://github.com/fschmid56/EfficientAT/blob/main/README.md#pre-trained-models), [local locked recipe](../../docs/Wavv_Music_Understanding_Implementation_Plan.md)

For a temporary CPU timing run, I read the validated 800-row cache and used class-balanced samples of 10 and 20 labeled fit rows per task. Each timed grid fit all eight heads across the four planned lambda values, using BCE-with-logits, weight-only L2, and LBFGS (`max_iter=20`, `max_eval=25`), with one CPU thread. After a warm-up, 12 repeated grids averaged 0.295 seconds at 10 rows and 0.310 seconds at 20 rows. Scaling against the actual 306–431 labeled fit rows per task estimates roughly 5–12 seconds for the 800-row optimizer grid. That estimate excludes Python startup, cache reading, validation scoring, and model-file writing; allow tens of seconds for the complete head-training command. No final weights were saved and full training has not started.

The 4,250-row matrix is about 5.3 times larger than the 800-row matrix, and it has 17 heads instead of eight. Extrapolating from the 10/20-row benchmark, head fitting should take roughly one to two minutes on the measured i5-1235U; that is not a full-cohort measurement. The raw feature matrix is about 6.23 MiB, and the matrices and optimizer state are small for a 16 GB workstation. [PyTorch LBFGS documentation](https://docs.pytorch.org/docs/stable/generated/torch.optim.LBFGS.html)

Extraction dominates. A fresh 10-track sequential benchmark using additional IDs from the deterministic 4,250 manifest took 68.6 seconds, or 6.85 seconds per track: 6.56 seconds downloading and 0.29 seconds decoding/inference on average. The one-time model load took 1.55 seconds. This projects to 6.57 additional hours for 3,450 tracks, or 8.09 hours for all 4,250 from scratch. The sample averaged 3.42 MB per audio file, projecting about 11.8 GB transferred for the 3,450 new tracks; audio is deleted after each feature, so it is not retained on disk. In the live run, the first 53 new rows completed in about nine minutes, or roughly 10 seconds per track; this early rate projects about 9.7 hours remaining for the 3,397 tracks left at that checkpoint. The earlier 72-track batch averaged 7.27 seconds per track, showing network variation. The cache-seeding option validated all 800 reused rows' source contract, split, Jamendo ID, artist, and finite 384-D payload, then wrote them into a separate cache with the new manifest digest. [Local extraction report](../../music%20understanding/reports/feature_extraction.json), [extractor cache checks](../../music%20understanding/scripts/extract_mtg_features.py)

The extracted feature cache is small and source OGGs are deleted after each track. The larger run costs several hours of network/audio processing, but the old 800 rows are now reused safely. The 4,250 cap is frozen before training; do not tune the manifest after looking at test results.

## Decision limit

No Wavv-task training, validation result, or learning curve exists yet. The 4,250-track choice and support floors are a one-fit data plan, not validated accuracy. After training, keep a task only if it beats its validation baseline and the locked test evaluation supports it; report uploader-tag results separately and omit weak tasks that fail. The present labels still do not cover captions, vocals, or temporal music understanding.

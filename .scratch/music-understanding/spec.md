# Music Understanding: constrained first baseline

Status: paused by user on 2026-09-25; 728/800 cached features are audited and 72 remain.

## Goal

Prepare a reproducible CPU-only frozen-feature dataset pipeline for the Wavv Music Understanding model, stopping before training. Keep this model separate from DCLAP.

## Locked baseline

- Backbone: pinned DyMN04-AS, frozen; cache only its verified 384-value pooled feature.
- Data: exact hash-verified MTG Music Classification Annotations IDs, joined to split-0 metadata and current Jamendo OGG permission inventory.
- Initial manifest: 800 IDs split by artist into fit/validation/test, approximately 70/15/15; no track or artist overlap.
- Evaluation caveat: the capped cohorts are selected to meet class/artist support floors using labels, including in the test partition. Test scores describe this coverage-balanced cohort, not natural MTG-Jamendo prevalence.
- Targets: the eight human-label tasks retained by the manifest support gate. Gender, tonal/atonal, weak uploader-tag tasks, vocal/instrumental, instruments, and DEAM valence/arousal are deferred.
- Audio: one deterministic 10-second midpoint window, mono 32 kHz, exact 320,000 samples; download one OGG at a time and delete it after feature storage.
- Source terms: handled manually by the user.

## Readiness gates

1. Pin and hash sources, checkpoint, annotation TSV, permission inventory, and the tracked split manifests.
2. Verify no track/artist overlap and minimum per-class reporting support.
3. Complete exactly one finite, repeatable 384-value feature per frozen manifest track; SQLite passes integrity check and temporary audio is absent.
4. Stop before training. Only expand the 800-track cap after validation demonstrates a specific support or quality need.

See `docs/Wavv_Music_Understanding_Implementation_Plan.md` for evidence, metrics, later training gates, and the eventual broader model scope.

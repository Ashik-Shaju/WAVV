# Music Understanding: constrained first baseline

Status: approved expansion to 4,250 tracks is in progress. The earlier 800-track cache is complete and will be reused; training has not started.

## Goal

Prepare a reproducible CPU-only frozen-feature dataset pipeline for the Wavv Music Understanding model, stopping before training. Use 4,250 selected tracks, keep this model separate from DCLAP, and reuse the completed 800-track seed.

## Locked baseline

- Backbone: pinned DyMN04-AS, frozen; cache only its verified 384-value pooled feature.
- Data: exact hash-verified MTG Music Classification Annotations IDs, joined to split-0 metadata and current Jamendo OGG permission inventory.
- Final pre-training manifest: 4,250 eligible IDs split by artist into fit/validation/test (2,975/638/637), with zero track or artist overlap. The 800 already-extracted IDs remain in their exact prior splits; acquire only the other 3,450.
- Evaluation caveat: the capped cohorts are selected to meet class/artist support floors using labels, including in the test partition. Test scores describe this coverage-balanced cohort, not natural MTG-Jamendo prevalence.
- Targets: eight human-consensus tasks and the nine uploader-tag labels that pass positive and negative support floors (five genres and four instruments). Keep uploader-tag results separate and describe an absent uploader tag as “not tagged,” not as confirmed acoustic absence. Gender, tonal/atonal, vocal/instrumental, and DEAM valence/arousal remain deferred.
- Audio: one deterministic 10-second midpoint window, mono 32 kHz, exact 320,000 samples; download one OGG at a time and delete it after feature storage.
- Source terms: handled manually by the user.

## Readiness gates

1. Pin and hash sources, checkpoint, annotation TSV, permission inventory, and the tracked split manifests.
2. Verify no track/artist overlap and minimum per-class reporting support.
3. Complete exactly one finite, repeatable 384-value feature per frozen manifest track; SQLite passes integrity check and temporary audio is absent.
4. Complete the 4,250-row feature cache, reuse the existing 800 rows, and stop before training.

See `docs/Wavv_Music_Understanding_Implementation_Plan.md` for evidence, metrics, later training gates, and the eventual broader model scope.

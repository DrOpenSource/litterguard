# ADR-0003: Object detector model, and the project license

- **Status:** Accepted
- **Date:** 2026-11-21 (proposed) · 2026-11-21 (accepted, after owner decision D1 = open source)

## Context
Ultralytics YOLO (v8/11/26) has the best tooling and mobile export path, but its code and trained weights are AGPL-3.0. The owner decided that LitterGuard is **open source**. See [REPO_VALIDATION.md §3](../research/REPO_VALIDATION.md).

## Decision
1. **LitterGuard is licensed AGPL-3.0** (`LICENSE`), with a section 7 additional permission for linking Google ML Kit / Play services (`LICENSE-EXCEPTIONS.md`, pending owner review).
2. **Detector family: Ultralytics YOLO.**
   - Phase 2 (walking skeleton): stock COCO **YOLO11n**, INT8, raw head `[1, 84, N]` + our NMS.
   - Phase 6: fine-tuned **YOLO11n vs YOLO26n**, chosen by benchmark on the Moto G54. The candidates are INT8 raw head and w8a32 NMS-free `[1, 300, 6]`; we pick on latency and recall of small, distant objects.
   - Input size (320 / 416 / 640) is decided by the P0.7 device spike, together with ROI-crop inference (ARCHITECTURE §4).
3. **Plate detector:** a YOLO11n plate model trained on Indian plate datasets (AGPL is fine). Using `open-image-models` instead requires the weight license to be verified first.
4. Every model ships with a `models.json` entry (source, license, sha256, tensor shapes) and golden tensor-shape tests.

## Consequences
- The whole app, the training configs and the scripts are published under AGPL-3.0.
- All dependencies must be AGPL-compatible. Proprietary Google libraries are covered only by the section 7 permission.
- RF-DETR (Apache-2.0, compatible) stays an optional benchmark candidate, not a requirement.

## Alternatives considered
- **Proprietary + RF-DETR / EfficientDet-Lite:** not needed given D1.
- **Ultralytics Enterprise license:** not needed given D1.

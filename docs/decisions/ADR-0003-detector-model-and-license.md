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
   - Input size (320 / 416 / 640) is decided by the P0.8 device benchmark, together with ROI-crop inference (ARCHITECTURE §4). **Decided: 416, CPU INT8 with 4 threads; GPU FP32 is the alternate.** See the addendum below.
3. **Plate detector:** a YOLO11n plate model trained on Indian plate datasets (AGPL is fine). Using `open-image-models` instead requires the weight license to be verified first.
4. Every model ships with a `models.json` entry (source, license, sha256, tensor shapes) and golden tensor-shape tests.

## Consequences
- The whole app, the training configs and the scripts are published under AGPL-3.0.
- All dependencies must be AGPL-compatible. Proprietary Google libraries are covered only by the section 7 permission.
- RF-DETR (Apache-2.0, compatible) stays an optional benchmark candidate, not a requirement.

## Alternatives considered
- **Proprietary + RF-DETR / EfficientDet-Lite:** not needed given D1.
- **Ultralytics Enterprise license:** not needed given D1.

## Addendum 2026-11-21: input size and accelerator (P0.8 device benchmark)

**Measurement setup**
- Moto G54 (Android 15), LiteRT 2.4.0 CompiledModel, `DetectorBenchmarkTest`.
- Stock YOLO11n, Ultralytics 8.4.216 `litert` export (FP32 NCHW in, `[1, 84, N]` out). The INT8 variant is static-quantised with float I/O and calibrated on coco8.
- Each timed run covers write + run + read-back: 5 warm-up runs, then 40 timed runs.
- The GPU was strict (`disableAutoFallbackToCpu`). Logcat shows **all nodes on the GPU** (`355/355` FP32, `384–385/385` INT8, one `LITERT_CL` partition).

**Results** (p50 / p90 in ms):

| Model | CPU 2 threads | CPU 4 threads | GPU (OpenCL) |
| --- | --- | --- | --- |
| FP32 320 | 59.7 / 84.3 | 41.9 / 50.1 | 44.3 / 62.4 |
| FP32 416 | 119.5 / 196.4 | 90.1 / 106.7 | **58.2 / 61.0** |
| FP32 640 | 295.4 / 418.4 | 188.4 / 198.4 | 110.1 / 113.2 |
| INT8 320 | 30.5 / 35.9 | **15.2 / 24.9** | 47.8 / 55.2 |
| INT8 416 | 47.9 / 55.7 | **32.4 / 42.1** | 55.4 / 62.3 |
| INT8 640 | 116.1 / 151.4 | 93.7 / 107.4 | 103.8 / 108.0 |

**Other observations**
- `Accelerator.NPU` fails with "Failed to compile model" for every variant. The MediaTek APU is not usable with the bundled runtime.
- A GPU first load takes 0.5–2.7 s (shader compile).
- INT8 brings no speed-up on the GPU, because the GPU dequantises.
- 4 CPU threads always beat 2.

**Decision**
1. **Input size 416.** Detection runs on the ROI crop (ARCHITECTURE §4), so 416 px over a crop gives distant people more pixels than 640 over the full frame. 640 costs 3× the latency for little gain once the crop is applied. 320 is kept as the thermal fallback size.
2. **Primary accelerator: CPU, INT8, 4 threads.** p90 is 42 ms, about 13% of the 333 ms frame budget at 3 FPS. There's no GPU warm-up or driver risk, and the GPU stays free.
3. **Alternate: GPU, FP32, 416.** p90 is 61 ms with very low jitter. Switch to it if **either** of these holds:
   - (a) INT8 loses more than 2 mAP points (or more than 3 points of small-object recall) against FP32 on our validation set (P6);
   - (b) the J5 thermal soak shows the CPU path reaching SEVERE more often than GPU FP32.

   `models.json` carries both variants, and the adapter's fallback order is CPU-INT8 → GPU-FP32 → CPU-FP32.
4. Never ship INT8 for the GPU path, and don't pursue the NPU for the MVP.

**Caveats**
- These are short runs on a cool phone (battery at 32 °C, on 500 mA USB). Sustained latency under heat comes from J5.
- INT8 accuracy is unverified until P2.2/P6 calibrate on our own viewpoint frames.

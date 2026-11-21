# Reference Repository Validation

Checked on **2026-11-21** against live GitHub metadata (`gh api`), raw source files, and web search.
Re-check before relying on a repo: stars, licenses and folder layouts change.

**Verdicts:**
- **Adopt**: use directly.
- **Reference**: read and learn from it; reimplement in our code.
- **Avoid**: do not copy code or weights from it.

## 1. Repositories named in the v1.0 PRD

| Repo | Stars · last push · license | Verdict | Findings |
| --- | --- | --- | --- |
| [surendramaran/YOLO](https://github.com/surendramaran/YOLO) | 212 · 2026-05 · **CC BY 4.0** (on code) | **Reference** | The PRD points to `YOLOv11-Object-Detector-Android-Tflite`, but that folder now holds **only a README** linking back to the v8/v9 projects. The v8 project uses the **legacy** `org.tensorflow:tensorflow-lite:2.16.1` `Interpreter` + Support Library, CameraX `1.4.0-beta02`, compileSdk 34, and XML views. Useful as a reference for the `[1, 4+C, 8400]` output parsing, NMS and the overlay. Do not copy it in wholesale: we target the LiteRT CompiledModel API and Compose. Any code we adapt must carry CC BY 4.0 attribution (Surendra Maran). |
| [google-ai-edge/litert-samples](https://github.com/google-ai-edge/litert-samples) | 499 · active · Apache-2.0 | **Adopt** | `samples/litert/` exists but has **no object-detection sample** (classification, segmentation, OCR verification, etc.). The bigger value is **`skills/`**: agent playbooks for `compiled-model-app-scaffolding`, `litert-compiled-model-migration`, `accuracy-safe-quantization` and `on-device-verification`. Point the coding agent at these. |
| [googlesamples/mlkit](https://github.com/googlesamples/mlkit) | 4.3k · active · Apache-2.0 | **Adopt** | `android/vision-quickstart` is present and current. Use the `text-recognition` (bundled) path with `InputImage.fromBitmap(plateCrop, 0)`, as the PRD says. |
| [ultralytics/ultralytics](https://github.com/ultralytics/ultralytics) | 63.6k · active · **AGPL-3.0** | **Adopt, if the license fits** (see §3) | Current lines are YOLO26/YOLO27 as well as YOLO11/v8. **The export command in the v1.0 PRD needs fixing:** (a) Ultralytics now recommends `format=litert`; (b) YOLO26's NMS-free head outputs `(N, 300, 6)`, not `[1, 4+C, 8400]`, and it is **only available for FP32 / w8a32 exports, not full INT8**; (c) the LiteRT export is documented as **NCHW float input**, unlike the old NHWC `tflite` export. Our parser must therefore be export-specific and **verified against the real tensor shapes** of each exported file. **Trained weights are AGPL-3.0 by default.** |
| [pedropro/TACO](https://github.com/pedropro/TACO) | 781 · 2026-10 · MIT (toolkit) | **Reference** | The data is mostly close-up hand-held litter photos, a **domain mismatch** with a fixed camera 5–25 m away. It is useful for training an optional litter-classifier booster, not as the trigger (PRD §6.1). The README doesn't state the image licenses, so check them per image (Flickr-sourced) before redistributing. |
| [jeremy-rico/litter-detection](https://github.com/jeremy-rico/litter-detection) | 19 · **2023-03** · **no license** | **Avoid** | It is stale, and with no license we have no right to copy the code. The useful parts (TACO→YOLO conversion, collapsing classes into one) are about 50 lines; we write our own in `ml/`. |
| [sayakpaul/Vehicle-Number-Plate-Detection](https://github.com/sayakpaul/Vehicle-Number-Plate-Detection) | 42 · **2019-09** · MIT | **Avoid** | TensorFlow 1 Object Detection API era; not a useful export path in 2026. Use the datasets in §2.3 instead. |
| [Muhammad-Zeerak-Khan/ALPR-using-YOLOv8](https://github.com/Muhammad-Zeerak-Khan/Automatic-License-Plate-Recognition-using-YOLOv8) | 482 · 2024-10 · MIT (repo) | **Reference** | A desktop Python pipeline (SORT tracking + EasyOCR + `license_plate_detector.pt`). The two-stage vehicle→plate idea is right. However, the bundled `.pt` was trained with Ultralytics (AGPL by Ultralytics' terms) on a dataset of unclear provenance that is **not Indian-specific**. Don't ship those weights. |

## 2. Better-suited or additional resources

### 2.1 Agent tooling (high value, low effort)

| Repo | Why |
| --- | --- |
| [android/skills](https://github.com/android/skills) · 10.7k · Apache-2.0 | Official Android agent skills. Directly relevant: `camera/camerax`, `device-ai/litert/litert-runtime`, `build-system/agp/agp-9-upgrade`, `testing/testing-setup`, `security/android-permissions-security`, `performance/anr-audit-and-fix`, `jetpack-compose/jetpack-compose-performance`, `system/edge-to-edge`. **✅ Done (2026-11-21):** `camerax` and `litert-runtime` (plus two LiteRT skills from litert-samples) were reviewed and vendored into `.claude/skills/`, pinned to a commit (see `THIRD_PARTY_NOTICES.md`). |
| [google-ai-edge/litert-samples/skills](https://github.com/google-ai-edge/litert-samples/tree/main/skills) | Model conversion → quantisation → on-device verification playbooks (for the Phase 2 model work). |

### 2.2 Models

| Repo | License | Role for us |
| --- | --- | --- |
| [roboflow/rf-detr](https://github.com/roboflow/rf-detr) · 10.1k | **Apache-2.0** (Nano–Large) | **License-safe detector alternative** if the app is not AGPL. RF-DETR Nano is reported at 48.4 COCO AP at 384 px. **Unverified for us:** its conversion to LiteRT and its mobile latency (it is a DETR transformer). Needs a 1-day spike: ONNX → LiteRT, then benchmark on the reference device. D-FINE and LW-DETR are also Apache-2.0. |
| [google-ai-edge/mediapipe-samples · object_detection/android](https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/object_detection) | Apache-2.0 | The **lowest-effort license-safe baseline** (EfficientDet-Lite via MediaPipe Tasks). Lower accuracy on small or distant objects; a good fallback and a sanity baseline. |
| [ankandrew/fast-plate-ocr](https://github.com/ankandrew/fast-plate-ocr) · 817 · MIT · active | MIT | **Plate-specific OCR**: tiny CCT/MobileViT models (sub-ms on desktop) that export to **TFLite**, with a fixed-slot output that suits plates better than general OCR. **Caveat:** India does **not** appear in the regions list of the global model's plate config we inspected, so expect to fine-tune it on Indian plates. Plan: run ML Kit first, then A/B this model on plate crops collected in the field (Phase 3). |
| [ankandrew/open-image-models](https://github.com/ankandrew/open-image-models) · 128 · MIT | MIT (repo); **weight provenance to verify** | Ready-made YOLOv9 **plate detectors** (ONNX, 256–640 px, end-to-end). A fast way to get the P1 plate detector without training. Verify the weights' license (upstream YOLOv9 code is GPL-3.0) before shipping; ONNX → LiteRT conversion is needed. |

### 2.3 Datasets

| Dataset | Fit |
| --- | --- |
| [MIVIA-IWDD (WACV 2026 workshop, IWDD contest)](https://mivia.unisa.it/iwddcontest2026/) | **The closest public match to our problem**: 400 fixed-surveillance-camera clips (200 dumping, 200 not) with the dumping moment annotated. Use it to build **scenario-replay fixtures** for the state machine and to measure detection speed. Check access and license terms. Paper: [Bouwmans et al., WACVW 2026](https://openaccess.thecvf.com/content/WACV2026W/WasteVision/html/Bouwmans_Illegal_waste_dumping_detection_WACVW_2026_paper.html). |
| [Indian Licence Plate Dataset in the wild (arXiv 2111.06054)](https://arxiv.org/pdf/2111.06054) | 16k images, 21k plates, 4-point annotations. For plate detector and OCR fine-tuning. |
| [IEEE DataPort: Indian LPR (YOLO + OCR)](https://ieee-dataport.org/documents/indian-license-plate-recognition-dataset-yolo-ocr) | YOLO-format detection plus a character set built from degraded plates. |
| [Mendeley: Indian Number Plate Dataset (IURS/NPDS)](https://data.mendeley.com/datasets/sxrnr7hwtk/1) | COCO-format Indian plates. |
| [Roboflow: litter-detect](https://universe.roboflow.com/ian-carlo-perilla-2dq1i/litter-detect) | CC BY 4.0; includes `GarbageBag` / `PlasticBag` classes, closer to dumped sacks than TACO is. |
| **Our own field data** (PRD FR-6.4 export) | Ultimately the most valuable set: our camera, our sites, our lighting. |

## 3. The licensing decision (blocks the Phase 2 model choice)

Ultralytics states that models trained with its code are AGPL-3.0 by default. Its staff have said in public forums that free use requires open-sourcing the entire solution under AGPL-3.0; otherwise you need an Enterprise license. *(Not legal advice.)*

| If LitterGuard is… | Detector path |
| --- | --- |
| **Open source under AGPL-3.0** | Ultralytics YOLO11n / YOLO26n is fine. Best tooling, fastest path. |
| **Proprietary, or might become so** | RF-DETR Nano or D-FINE (Apache-2.0); fallback EfficientDet-Lite (MediaPipe). Or buy an Ultralytics Enterprise license. |

Either way the `ObjectDetector` port keeps this a contained adapter change. See [ADR-0003](../decisions/ADR-0003-detector-model-and-license.md).

**✅ Decided 2026-11-21: open source under AGPL-3.0, so we take the Ultralytics path** (ADR-0003). RF-DETR stays an optional benchmark candidate.

## 4. Corrections to carry into the code

1. **Exporter-specific parsers, verified by tests.** Golden tests load each shipped `.tflite`, run one fixture image, and assert the input/output tensor shapes and layout (NHWC vs NCHW, `[1,4+C,8400]` vs `[1,300,6]`).
2. **CompiledModel API, not `Interpreter`.** Follow the litert-samples `litert-compiled-model-migration` skill when adapting reference code.
3. **INT8 calibration** needs representative data from *our* camera viewpoint (`data=` at export). Calibrating on COCO alone costs accuracy on distant, small objects.

# Learnings

> T3 memory: non-obvious facts, each with evidence. Format and tags are in [MEMORY_ARCHITECTURE.md §3](../agent/MEMORY_ARCHITECTURE.md). Grep by tag before touching an area.

### [ml][litert] Ultralytics YOLO26 LiteRT export: output shape depends on the quantisation mode
- **Observed:** 2026-11-21, Ultralytics docs (end-to-end detection and LiteRT export guides).
- **Evidence:** NMS-free head → `(N, 300, 6)`, only for FP32/w8a32. Raw head → `(N, 4+C, 8400)`. The LiteRT export is documented as NCHW float input, unlike the older NHWC `tflite` export.
- **Resolution:** the parser is chosen per model via `models.json`; golden shape tests are mandatory (BEST_PRACTICES §6).
- **Status:** active. Verify on the first exported file.

### [ml] surendramaran/YOLO "YOLOv11" Android folder is README-only
- **Observed:** 2026-11-21, GitHub tree.
- **Evidence:** the folder holds only a README pointing at the v8/v9 projects; those use legacy `tensorflow-lite:2.16.1` Interpreter APIs and CameraX 1.4.0-beta02.
- **Resolution:** use it as a reference only; implement on the LiteRT CompiledModel API.
- **Status:** active

### [ocr] fast-plate-ocr global model does not list India
- **Observed:** 2026-11-21, `cct_s_v2_global_plate_config.yaml` `plate_regions`.
- **Resolution:** ML Kit first; fast-plate-ocr only after fine-tuning on Indian plates (Phase 3 A/B).
- **Status:** active

### [fgs] Camera foreground service cannot start from BOOT_COMPLETED (Android 15+)
- **Observed:** 2026-11-21, platform restriction (to be confirmed on the reference devices).
- **Resolution:** after a reboot, show a "Tap to resume" notification + operational Telegram alert (FR-8.5).
- **Status:** active — needs device verification

### [build] Current AndroidX needs compileSdk 37.2 (a minor API level)
- **Observed:** 2026-11-21. With compileSdk 36, AAR metadata checks failed for 19 deps (OkHttp 5.5.1 needs 37; Compose UI 1.13.0 needs 37.1+).
- **Resolution:** `compileSdk = 37` + `compileSdkMinor = 2` (AGP 9 `CommonExtension.compileSdkMinor`), via the catalog. SDK package: `platforms;android-37.2`. targetSdk stays 36.
- **Status:** active

### [litert] GPU silently runs on the CPU without the litert-gpu artifact
- **Observed:** 2026-11-21, vendored official skills (`android-litert-runtime`, `litert-compiled-model-app-scaffolding`).
- **Evidence:** the skills state that with `litert` alone, a GPU+CPU accelerator set runs on the CPU and only logs "GPU accelerator could not be loaded and registered".
- **Resolution:** `:core:ml` declares `libs.litert.gpu` (ADR-0004). Always log the accelerator actually used.
- **Status:** active

### [litert] Official skills disagree on model thread confinement
- **Observed:** 2026-11-21. `litert-runtime` says a single-thread executor; `compiled-model-app-scaffolding` says `Dispatchers.IO.limitedParallelism(1)`.
- **Resolution:** a dedicated single-thread executor per model satisfies both (ADR-0004).
- **Status:** active

### [device:moto-g54] Spec facts that drive design (from public sources; verify on the unit)
- **Observed:** 2026-11-21, GSMArena / Wikipedia / Beebom.
- **Evidence:** Dimensity 7020 (2x A78 + 6x A55), IMG BXM-8-256 GPU, 50 MP f/1.8 OIS main camera, no telephoto, shipped with Android 13 (last major update reportedly 15).
- **Resolution:** benchmark 2 vs 4 CPU threads and GPU (P0.8). Plate range depends on the 50 MP capture mode (docs/devices/moto-g54.md).
- **Status:** active, needs device verification

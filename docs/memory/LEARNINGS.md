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
- **Status:** superseded by the measured entries below (camera, thermal). Big cores confirmed as cpu6–7 @ 2.2 GHz.

### [device:moto-g54][camera] Apps get only 12.6 MP from the "50 MP" camera; HFOV is 61°, not 80°
- **Observed:** 2026-11-21, `adb shell dumpsys media.camera` on the owner's unit (Android 15).
- **Evidence:** camera 0 active array 4096×3072. No `ULTRA_HIGH_RESOLUTION_SENSOR` capability, no `*MaximumResolution` configs. Largest JPEG/YUV is 4096×3072. Focal 4.27 mm / sensor width 5.00 mm gives HFOV ≈ 61°. No `availableStreamUseCases`. OIS is available. Zoom 1–8× is digital.
- **Resolution:** plate captures use 4096×3072 stills. The narrower FOV makes this ~87 px across a car plate at 20 m, so the OCR range is about 22 m for cars and 12 m for two-wheelers (moto-g54.md). Don't build a 50 MP code path, and don't set `StreamUseCase`.
- **Status:** active

### [device:moto-g54][thermal] Thermal status jumps NONE → SEVERE; LIGHT/MODERATE thresholds are NaN
- **Observed:** 2026-11-21, `adb shell dumpsys thermalservice`.
- **Evidence:** the CPU/GPU/NPU hot thresholds are `[NaN, NaN, NaN, 85, 90, 100, 117]` °C. Skin is `[…, 80, 85, 90, 95]`. Headroom thresholds are `[NaN×4, 1.17, 1.33, 1.5]`.
- **Resolution:** FR-8.4 now drives the early FPS tiers from `getThermalHeadroom(10)`, falling back to status-only if it returns NaN. The headroom value itself is still to be confirmed from the app (P2).
- **Status:** active

### [device:moto-g54][power] Laptop USB supplies only 500 mA
- **Observed:** 2026-11-21, `dumpsys battery` while connected to the dev PC (`Max charging current: 500000`).
- **Resolution:** long thermal and soak runs (J5) must use the wall charger. Battery drain during dev sessions over a PC USB port is expected.
- **Status:** active

### [device:moto-g54][litert] Detector benchmark: CPU INT8 (4 threads) is fastest; GPU is fully resident; NPU unusable
- **Observed:** 2026-11-21, `DetectorBenchmarkTest` on the owner's G54, LiteRT 2.4.0, stock YOLO11n.
- **Evidence (p50):**
  - 416 px: INT8 CPU-4T 32 ms; FP32 GPU 58 ms; INT8 GPU 55 ms.
  - 640 px: FP32 GPU 110 ms; INT8 CPU-4T 94 ms.
  - The GPU runs all nodes (`355/355`, `385/385` LITERT_CL, one partition); its first load takes 0.5–2.7 s.
  - CPU-2T is always slower than 4T.
  - `Accelerator.NPU` gives "Failed to compile model".
- **Resolution:** ADR-0003 addendum: 416 px, CPU-INT8-4T primary, GPU-FP32 alternate, no NPU. INT8 on the GPU is pointless.
- **Status:** active. Revisit after the J5 thermal soak and the P6 INT8 accuracy check.

### [litert] litert-api's manifest adds FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC permissions
- **Observed:** 2026-11-21, `litert-api-2.4.0.aar` AndroidManifest (used for AI Pack downloads).
- **Resolution:** our app uses a `camera` FGS only. In P2.12, strip the extra one with `tools:node="remove"` on `FOREGROUND_SERVICE_DATA_SYNC`, so Play's FGS declaration stays truthful. Verify in the merged manifest.
- **Status:** active

### [ml] Ultralytics LiteRT export: Linux/macOS only, and litert-torch must be preinstalled
- **Observed:** 2026-11-21, Ultralytics 8.4.216. `format="tflite"` is deprecated and now redirects to `format="litert"` (litert-torch). The output is FP32 NCHW `[1,3,S,S]`. The quantised file is `<stem>_int8.tflite` (static INT8, float I/O, float head); there is no FP16 variant.
- **Evidence:**
  - On Windows: `AssertionError: LiteRT export only supported on Linux x86 and macOS`.
  - In CI, when Ultralytics auto-installed `litert-torch 0.10.0` mid-export, it downgraded torch 2.15 to 2.14.1 inside a running process, and the import failed with `duplicate registrations for aten.miopen_batch_norm.default`.
- **Resolution:** export in CI (`ml-export-bench.yml`). Install `ml/requirements-export.txt` (ultralytics + litert-torch + ai-edge-quantizer pinned) in one pip call, before exporting.
- **Status:** active

### [tooling] Auto-mode permission blocks pushing downloaded binaries to the phone
- **Observed:** 2026-11-21. Pushing and running the prebuilt `android_aarch64_benchmark_model` (TF nightly bucket) over adb was denied by the agent permission classifier.
- **Resolution:** measure model latency with our own instrumented benchmark built from pinned Maven LiteRT (`:core:ml` androidTest). It is also closer to production, because it uses the CompiledModel API rather than the legacy delegate. Alternatively, the owner can allow the action explicitly.
- **Status:** active

### [telegram] Group chat-ID auto-detection can't rely on an old `/start`
- **Observed:** 2026-11-22, Telegram Bot API docs (`getUpdates`; bot privacy mode FAQ), while closing P0.10.
- **Evidence:** undelivered updates are kept for at most 24 hours, so the `/start` sent during Task B will be gone when the app is built. In privacy mode (the default) a bot in a group only gets commands addressed to it (`/start@LitterGuardBot`), replies to it, service messages, and a plain `/start` only if it was the last bot to post there. A `my_chat_member` update is always sent when the bot is added to a chat.
- **Resolution:** P2.10 detects the group from a fresh action during in-app setup: open an "Add to group" deep link (`t.me/<bot>?startgroup=<nonce>`), then match the `my_chat_member` update or `/start@<bot> <nonce>`. Read the bot username from `getMe`; don't hard-code it. Don't call `deleteWebhook`/set webhooks.
- **Status:** active. Verify when P2.10 is built.

### [tooling] Git Bash rewrites `/sdcard/...` paths passed to adb
- **Observed:** 2026-11-22. `adb pull /sdcard/Music/...` failed with "failed to stat remote object 'C:/Program Files/Git/sdcard/...'".
- **Resolution:** prefix with `MSYS_NO_PATHCONV=1` (or use the PowerShell tool) for any adb command that takes a device path.
- **Status:** active

### [audio] Linear loudnorm can't reach −14 LUFS on raw phone speech under a −1.5 dBTP cap
- **Observed:** 2026-11-22, Moto Recorder AAC files (44.1 kHz stereo, ~−16 LUFS, peaks +0.4 dBTP, about 1–1.5 s of leading digital silence).
- **Evidence:** two-pass `loudnorm` with `linear=true` stopped at about −15.4 LUFS, because the true-peak cap limits the gain. With 3:1 compression first it reached −14.8 LUFS.
- **Resolution:** `ml/scripts/prepare_audio_clips.py` trims silence, then high-pass → mono → compressor → two-pass loudnorm → 48 kHz. ffmpeg comes from the `imageio-ffmpeg` wheel (no system install needed on Windows). Trimming the leading silence alone saves about 1 s of H2 siren latency.
- **Status:** active


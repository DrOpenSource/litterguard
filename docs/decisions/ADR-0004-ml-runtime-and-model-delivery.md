# ADR-0004: Bundled LiteRT runtime, thread-confined models, and build-time model download

- **Status:** Accepted
- **Date:** 2026-11-21

## Context
- The official `litert-runtime` skill (vendored in `.claude/skills/`) defaults to the Google Play services runtime. It also warns that `Accelerator.GPU` **silently runs on the CPU** unless `litert-gpu` is added next to `litert`.
- The official skills disagree on threading. One requires a single-thread executor; the other uses `Dispatchers.IO.limitedParallelism(1)`. Both require that create, run and close are serialised and happen off the main thread.
- The `compiled-model-app-scaffolding` skill says "weights are never committed" and downloads them at build time. Our earlier plan used Git LFS, which has bandwidth quotas on GitHub and forces every contributor to install LFS.

## Decision
1. **Bundled runtime:** `com.google.ai.edge.litert:litert` **and** `litert-gpu` (same version). We don't use Play services LiteRT. Reasons: fewer proprietary dependencies under AGPL, it works on de-Googled devices, and it keeps the NPU option open.
2. **Thread confinement:** each model is owned by a **dedicated single-thread executor** (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`). This satisfies both skills; creation, warm-up, inference and close all run there. One shared `Environment` per process.
3. **Accelerator policy:** each model's `models.json` entry declares an ordered accelerator list. The adapter tries each entry in order and logs the accelerator *actually* used into every incident; GPU entries are requested strictly, with `disableAutoFallbackToCpu()`. For the detector on the G54, the order is CPU-INT8 (4 threads) → GPU-FP32 → CPU-FP32. That order is decided by measurement in ADR-0003's 2026-11-21 addendum, replacing the earlier "GPU first" assumption.
4. **Model delivery:**
   - `.tflite` files are **not committed**. They are attached to GitHub Releases.
   - A Gradle task in `:core:ml` downloads them into `build/generated/models/` (registered as an assets source) and **verifies sha256 against `core/ml/models.json`**. A mismatch fails the build.
   - `androidResources { noCompress += "tflite" }`.
   - Local override: `-PlitterguardModelsDir=<dir>` for models still in development.

## Consequences
- The repo stays small, there's no LFS, and a contributor needs only `./gradlew`.
- CI needs network access to the Release assets (cacheable).
- `.gitattributes` drops the LFS rule; `*.tflite` is git-ignored.

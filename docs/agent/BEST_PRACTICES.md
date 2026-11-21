# Engineering Best Practices

The standards every contributor (human or agent) follows in this repo. `AGENTS.md` holds the non-negotiable rules; this guide explains **how** to write code that meets them. Where this guide and the tooling (ktlint, detekt, Lint) disagree, the tooling wins: fix the guide.

---

## 1. Kotlin

- **Immutability by default:** use `val`, `data class` with `val`s, and read-only `List`/`Map` in APIs. Mutable state stays private and confined to one owner.
- **Model states as types:** use `sealed interface` for states, effects and results. Do not encode states as booleans or nullable flags.
  ```kotlin
  sealed interface DumpingState {
      data object Idle : DumpingState
      data class ActorPresent(val since: Instant, val tracks: Set<TrackId>) : DumpingState
      data class ObjectLeftPending(val candidate: Blob, val since: Instant) : DumpingState
      data class Cooldown(val until: Instant, val incidentId: IncidentId) : DumpingState
  }
  ```
- **Exhaustive `when`** over sealed types, with no `else ->` branch. That way the compiler tells you every place a new state must be handled.
- **Use value classes for IDs and units** (`@JvmInline value class TrackId(val v: Int)`, `Normalized`, `Millis`). This prevents argument mix-ups in geometry code.
- **Inject time and randomness.** Never call `System.currentTimeMillis()`, `Instant.now()` or `Random` inside domain code. Use the `Clock` port instead.
- **No `!!`.** Use `requireNotNull(x) { "why" }` at boundaries, or handle the null case.
- **Don't use exceptions for control flow.** Domain functions return results; adapters convert platform exceptions into typed failures at the boundary.
- **Public API surface:** use `internal` by default in modules, and only make public what another module actually needs.
- **Naming:** use domain words from the AGENTS.md glossary (`actor`, `baseline`, `dwell`). Avoid `data`, `info`, `manager`, `helper`.

## 2. Coroutines and Flow

- **Structured concurrency only.** Never use `GlobalScope`. Every coroutine is owned by a scope with a lifecycle (`viewModelScope`, the service session scope, or WorkManager).
- **Inject dispatchers** (`@IoDispatcher`, `@DefaultDispatcher`) so tests can swap them for `StandardTestDispatcher`.
- **Pipeline confinement:** the state machine runs on `Dispatchers.Default.limitedParallelism(1)`. Do not add locks to domain code. If you feel you need one, the design is wrong.
- **Never swallow `CancellationException`.** When catching broadly at an adapter boundary:
  ```kotlin
  try { detector.detect(frame) }
  catch (e: CancellationException) { throw e }
  catch (e: IOException) { Timber.w(e, "detector IO"); emptyList() }
  ```
- **Choose the right Flow:**
  - `StateFlow` for current state (configuration, pipeline status).
  - `SharedFlow(extraBufferCapacity, DROP_OLDEST)` for events.
  - Never use an unbounded buffer for camera frames.
- **UI collection:** use `collectAsStateWithLifecycle()` and nothing else.
- **Supervise side effects:** run effects in a `SupervisorJob` child scope, so a failed Telegram call can't cancel the camera pipeline.

## 3. Domain logic (`:core:domain`)

- **Write a pure function wherever you can.** The state machine is `(state, observation, config, now) → (state', effects)`. It has no I/O, no Android types, and no hidden clock.
- **Effects are data.** The machine emits `Effect.PlayDeterrent`, `Effect.CommitIncident(...)`, and so on. The orchestrator executes them, and tests assert on the list it emits.
- **One config object.** `DetectionConfig` holds every tunable, with KDoc describing the units and the PRD §6.4 reference. Presets (Low/Normal/High) are factory functions on it.
- **Geometry conventions:**
  - Normalised `[0,1]` coordinates live in the domain. Pixel coordinates only exist inside adapters.
  - The origin is top-left and the y-axis points down.
  - Polygons are counter-clockwise.
  - Point-in-polygon must handle points on edges deterministically (an edge point counts as inside).
- **Image data in the domain:** `LumaPlane` (a `ByteArray` with width, height and row stride) and `ImageBuffer`. These are pooled and never retained after the call returns, unless the frame is explicitly copied into the evidence buffer.

## 4. Jetpack Compose and UI

- **Unidirectional data flow:**
  - The ViewModel exposes a single `StateFlow<UiState>` and accepts `onEvent(UiEvent)`.
  - Composables are stateless and take `state` plus lambdas.
  - Write stateful wrappers only at the screen level.
- **Every composable takes `modifier: Modifier = Modifier`** as its first optional parameter, applied to its root. The Compose ktlint rules enforce this.
- **Previews:** every screen and shared component has a `@Preview` driven by fake state. No preview should touch real repositories.
- **Performance:**
  - Hoist and remember expensive objects.
  - Read fast-changing state (such as overlay boxes at 3 FPS) in the draw or layout phase, using `Modifier.drawWithContent` or lambda modifiers, not in composition.
  - Use stable/immutable UI models (`@Immutable` data classes, `ImmutableList` or `List` of data classes).
- **Kiosk screen:** a pure black background and minimal recomposition. It must not keep an animation running.
- **Strings:** every user-visible string lives in `strings.xml`, never hard-coded, so Kannada and Hindi can be added later. Use `stringResource()`.
- **Accessibility:** touch targets ≥ 48 dp, `contentDescription` on icons, and colour is never the only signal of state.
- **Theme:** colours and type come from `:core:ui` theme tokens only. No raw hex in features.

## 5. CameraX

- **Bind once per session** with `ProcessCameraProvider` to a lifecycle owner: the `LifecycleService` for monitoring, or the screen for setup preview.
- **ImageAnalysis:**
  - `STRATEGY_KEEP_ONLY_LATEST`, `OUTPUT_IMAGE_FORMAT_RGBA_8888` (avoids manual YUV conversion), and target resolution set via `ResolutionSelector`.
  - Throttle in the analyzer by timestamp. Dropped frames must still call `close()` immediately.
  - Run the analyzer on a dedicated single-thread executor, never the main thread.
- **Always** `imageProxy.use { ... }` or `try/finally { close() }`. A single leak stalls the stream once the buffer queue fills.
- **Rotation:** apply `imageInfo.rotationDegrees` once, in the adapter. The domain only sees upright frames.
- **ImageCapture** for evidence: `CAPTURE_MODE_MINIMIZE_LATENCY`, capture to memory, encode a JPEG off the main thread. Rate-limit captures (FR-5.2) so the analysis stream isn't starved.
- **Torch:** use `cameraControl.enableTorch()` on the bound camera. Never open a second camera session for the torch.
- **Errors:** observe `cameraInfo.cameraState`, and rebind with backoff on `ERROR_*` states (FR-8.5).

## 6. On-device ML (LiteRT and ML Kit)

- **Use the CompiledModel API** with accelerator preference GPU (or NPU) → CPU. Log the accelerator actually used. Don't use the legacy `Interpreter` in new code. Declare `litert-gpu` next to `litert`; without it, GPU requests silently run on the CPU.
- **Model lifecycle:** load once per monitoring session, run one warm-up inference, reuse input/output `TensorBuffer`s, and close the buffers before the model at session end. Never allocate per frame. Every call happens on the model's **dedicated single-thread executor** (ADR-0004).
- **Benchmark honestly:** time `run()` *plus* the output readback; `run()` alone only enqueues work on the GPU.
- **Pre-processing must match the export exactly:** input size, letterboxing, normalisation, NHWC or NCHW layout, and quantisation params from the tensor. Keep it as a documented function with a unit test on a fixture.
- **Post-processing lives in testable code:** decode `[1, 4+C, 8400]` (or `[1, 300, 6]`) → boxes → class-wise NMS. Write it as plain Kotlin over `FloatArray` so it can be unit-tested with golden tensors.
- **Golden tests per model:** load the asset, check input/output shapes and dtypes against `models.json`, and run one fixture image with expected detections within a tolerance.
- **ML Kit OCR:** feed only the plate crop, and reuse one `TextRecognizer` client. Its `Task` callbacks are converted with `await()` (kotlinx-coroutines-play-services) or `suspendCancellableCoroutine`.
- **Measure, don't guess.** Every inference path logs a latency histogram (p50/p90) in debug builds. A performance claim in a PR needs numbers from a named device.

## 7. Android platform: services, power, thermal

- **Foreground service type `camera`:**
  - Start it from a visible activity (Android 14+), and call `startForeground()` within the allowed window.
  - Show an ongoing notification with a Stop action.
  - Declare `FOREGROUND_SERVICE_CAMERA`.
- **Wake locks** are partial, have a tag, have a timeout, and are released in `finally` and `onDestroy`.
- **Thermal:** react through `addThermalStatusListener`, and poll `getThermalHeadroom(10)` every 30 s on API 30+. Policy changes go through the `PipelineConfig` StateFlow, never through scattered checks.
- **Permissions:**
  - Request in context, with a rationale.
  - Handle "denied" and "don't ask again" by linking to Settings.
  - Never request background location, storage or microphone (PRD §9).
- **Audio:** request `AudioFocus` (`USAGE_ALARM` or `USAGE_ASSISTANCE_SONIFICATION`), release it after playback, and respect quiet hours.

## 8. Persistence

- **Room:**
  - DAOs return `Flow` for observation and use `suspend` for writes.
  - Write in transactions when an incident and its evidence rows change together.
  - Keep schema JSON exported and committed. Every version bump comes with a `Migration` and a `MigrationTestHelper` test.
- **Write order:** files first, then the DB row, then enqueue delivery. A crash between steps must leave a recoverable state; the purge worker cleans up orphan files.
- **DataStore:** one `Preferences` DataStore for `SiteConfig`. Validate on read, and fall back to defaults rather than crashing.
- **Secrets:** encrypt with Tink AEAD under an Android Keystore master key. Never log or export secrets, and never include them in crash reports.

## 9. Networking (Telegram and webhook)

- **One OkHttp client**, with timeouts set (connect 10 s, read 30 s, write 60 s for media).
- **Delivery only through WorkManager:** a unique work name per incident, a network constraint, and exponential backoff. Honour Telegram's `429 retry_after`.
- **Idempotency:** the incident ID is in the caption and stored with the delivery result. Retries must not create duplicate group messages after a confirmed success.
- **Sanitise logs:** never log URLs that contain the bot token (`/bot<token>/`). Redact them in an OkHttp interceptor.

## 10. Error handling and logging

- **Timber only**, with no `println` or `Log.*` calls. Use structured messages: `Timber.i("state %s -> %s", from, to)`.
- **Log levels:**
  - `e`: needs a human.
  - `w`: degraded but recovering.
  - `i`: state transitions and lifecycle.
  - `d`: per-frame detail, stripped from release builds.
- **Privacy in logs:** no plate numbers at `i` or above in release builds, no tokens, and no image bytes.
- **Fail soft at the edges, fail loud in the core.** Adapters degrade (no OCR → "unreadable"). Domain invariants use `check()`/`require()` and crash in debug builds.

## 11. Testing

- **Default test stack:** JUnit Jupiter, Truth, Turbine (for Flow), `kotlinx-coroutines-test`, MockK (only where a fake is impractical).
- **Prefer fakes to mocks** for ports (`FakeClock`, `FakeObjectDetector`, `InMemoryIncidentRepository`). They live in each module's `testFixtures` or in a shared test source set.
- **Name tests by behaviour:** `` `person passing through with bag does not trigger` ``.
- **Make tests table-driven** for plate sanitising, geometry and thermal policy.
- **State machine:** cover every `(state, input)` pair in the transition table. Add a **scenario-replay** test for each field incident, both true and false.
- **Determinism:** no `Thread.sleep`, no real time, no network in unit tests. Use `runTest` with virtual time.
- **Coverage expectation:** `:core:domain` aims for ≥ 90% line coverage of decision code. Adapters get contract tests rather than coverage targets.
- **Instrumented tests** are kept small: model loading, camera binding smoke test, Room migrations, and the ROI drawing UI.

## 12. Performance and thermal budget

| Budget | Target |
| --- | --- |
| Motion gate per frame | ≤ 2 ms |
| Detector per frame (reference device) | ≤ 60 ms |
| Whole pipeline per frame (no incident) | ≤ 120 ms |
| Allocations per frame in steady state | ~0 (pooled buffers) |
| Main thread | Never does image work |

Profile with Android Studio Profiler or Perfetto before optimising. Attach numbers to the PR.

## 13. Git workflow

- **Branches:** `main` is always green. Use `feat/<slug>`, `fix/<slug>`, `chore/<slug>`, `docs/<slug>`.
- **Conventional Commits** that reference PRD IDs: `feat(domain): baseline diff with illumination guard (FR-2.6)`.
- **Commits are small and atomic.** A refactor commit is separate from a behaviour commit.
- **PR description:** what and why, PRD IDs, how it was tested (commands plus device), screenshots for UI, and a "needs device verification" list.
- **Never commit:** secrets, `local.properties`, field-test media, or generated build output.

## 14. Dependencies

- Add a dependency only when it removes significant code or risk. State the license (it must be compatible with the project license; see ADR-0003), its size impact, and its maintenance status.
- Versions live in the catalog. Upgrade in dedicated PRs, one ecosystem at a time (AndroidX, Kotlin, ML), and run the full gate plus an on-device smoke test.

## 15. Agent-specific practices

- **Plan, then make small diffs.** For anything over about 100 lines, state the plan first (files, interfaces, tests), then implement it in steps that each build.
- **Don't add speculative abstraction.** Implement the PRD requirement in front of you. Ports exist where ARCHITECTURE.md lists them; don't invent new layers.
- **Don't invent APIs.** If you are unsure a CameraX, LiteRT or ML Kit API exists in the pinned version, check the docs or source, or the official skills, before using it.
- **Verify, then claim.** "Tests pass" means you ran them and saw them pass. Quote the command and the result.
- **Leave the campsite clean.** Remove dead code, debug logs and TODOs you created. If a TODO must stay, use `TODO(FR-x.y): ...` with the PRD reference.
- **Update memory** per [MEMORY_ARCHITECTURE.md](MEMORY_ARCHITECTURE.md) before ending the session.

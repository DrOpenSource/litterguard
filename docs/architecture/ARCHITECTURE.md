# LitterGuard — Architecture

Status: baseline for Phase 0. Change it through an ADR (`docs/decisions/`), never silently.

## 1. Principles

1. **Decisions are pure; effects are adapters.** Everything that decides *whether dumping happened* is pure Kotlin in `:core:domain`, deterministic, and testable on the JVM with recorded or synthetic inputs. Android APIs (CameraX, LiteRT, ML Kit, audio, network) sit behind interfaces ("ports") and are implemented in `:core:*` Android modules.
2. **One direction of dependency.** `model ← domain ← infrastructure ← features ← app`. Nothing points back up.
3. **Swappable models.** Detector, plate detector and OCR are ports. Changing YOLO for RF-DETR, or ML Kit for fast-plate-ocr, is an adapter change plus a config flag; the domain does not change.
4. **Throttled by design.** The pipeline consumes at most `analysis_fps` frames per second. Back-pressure means dropping frames, never queueing them.
5. **Offline first.** Every incident is persisted before any network call. Network delivery is an eventually-consistent side effect.

## 2. Module map

```
:app                      Application, MainActivity, NavHost, Hilt root, WorkManager config
│
├── :feature:setup        Onboarding, permissions, ROI drawing, siren test, settings
├── :feature:monitor      Start/stop, kiosk screen, debug overlay
├── :feature:incidents    Incident list/detail, review, export
│        (features depend on :core:domain, :core:model, :core:ui only, never on each other)
│
├── :core:ui              Theme, shared Compose components (RoiCanvas, KioskStatus)
├── :core:monitoring      MonitoringService (FGS), PipelineOrchestrator, ThermalWatchdog, WakeLock
├── :core:camera          CameraX binding, throttled frames → ImageBuffer, ImageCapture, torch
├── :core:ml              LiteRT ObjectDetector / PlateDetector, ML Kit TextRecognizer
├── :core:data            Room (incidents, evidence, delivery), DataStore (SiteConfig), EvidenceStore
├── :core:alerts          SirenPlayer, TorchPulser, TelegramClient, DeliveryWorker, SecretStore
│
├── :core:domain  (JVM)   State machine, baseline diff, tracker, ROI geometry, plate sanitiser, ports
└── :core:model   (JVM)   Value types: BoundingBox, Detection, Polygon, ImageBuffer, LumaPlane, Incident…
```

### Dependency rules (enforced in review; Gradle makes most violations impossible)

| Module | May depend on | Must not |
| --- | --- | --- |
| `:core:model` | nothing (Kotlin stdlib) | Android, coroutines |
| `:core:domain` | `:core:model`, coroutines-core | Android, any `:core:*` Android module |
| `:core:<infra>` | `:core:domain` (+ its own libraries) | another infra module, features |
| `:core:monitoring` | `:core:domain` only; gets infra through ports injected by Hilt | concrete infra classes |
| `:feature:*` | `:core:domain`, `:core:model`, `:core:ui` | other features, infra modules |
| `:app` | everything | business logic of its own |

Hilt `@Binds` modules live in each infra module (`:core:ml` binds `ObjectDetector` to `LiteRtObjectDetector`), so `:core:monitoring` and the features only ever see interfaces.

## 3. Ports (defined in `:core:domain`)

| Port | Implemented in | Purpose |
| --- | --- | --- |
| `FrameSource` | `:core:camera` | `Flow<AnalysisFrame>` at the throttled rate (`ImageBuffer` + `LumaPlane` + timestamp + rotation) |
| `EvidenceCamera` | `:core:camera` | `suspend captureHighRes(): ImageBuffer`; `pulseTorch()` |
| `ObjectDetector` | `:core:ml` | `detect(ImageBuffer): List<Detection>` (actors + animals) |
| `PlateDetector` | `:core:ml` | `detect(vehicleCrop): List<BoundingBox>` |
| `TextRecognizer` | `:core:ml` | `suspend recognize(crop): List<TextLine>` |
| `Deterrent` | `:core:alerts` | `suspend play(clip: DeterrentClip)` |
| `AlertSink` | `:core:alerts` | `enqueue(incidentId)`; delivery is WorkManager's job |
| `IncidentRepository` | `:core:data` | CRUD + `Flow` queries for incidents and evidence |
| `SiteConfigRepository` | `:core:data` | `Flow<SiteConfig>`, update ROI and settings |
| `EvidenceStore` | `:core:data` | write/read/purge evidence images (app-private files) |
| `ThermalMonitor` | `:core:monitoring` | `Flow<ThermalLevel>` |
| `Clock` | `:core:monitoring` (system impl) | Injected time source, so tests are deterministic |
| `ObjectDetectorFactory` | `:core:ml` | `open(): ObjectDetector`, one per monitoring session, closed at its end |
| `MonitoringController` | `:core:monitoring` | Start and stop watching, plus a `StateFlow` of what the pipeline sees (used by the screens) |
| `AlertChannelSetup` | `:core:alerts` | Guided Telegram setup; the token goes in and never comes back out |

`ImageBuffer` is a pooled, platform-neutral ARGB buffer (`width`, `height`, `IntArray`). `ImageProxy` is only touched in `:core:camera`. `Bitmap` conversion happens only at the edges that need it: `:core:ui` (display, ADR-0005), `:core:data` (JPEG encoding) and instrumented tests. This one choice is what lets the whole decision pipeline run in JVM tests.

## 4. Runtime pipeline

```
CameraX ImageAnalysis (1280×720, KEEP_ONLY_LATEST)
   │  analyzer thread: drop frames until (now - lastAccepted) ≥ 1/analysis_fps
   ▼
:core:camera → AnalysisFrame(ImageBuffer, LumaPlane, ts)        [buffers from a pool of 4]
   ▼
PipelineOrchestrator (:core:monitoring) — single-threaded coroutine (Default.limitedParallelism(1))
   1. MotionGate.score(luma, roi)                    ← domain, ~1 ms
   2. if motion || state != IDLE: ObjectDetector.detect(roiCrop(frame))  ← :core:ml, own thread
   3. Tracker.update(detections)                     ← domain
   4. BaselineModel.update/diff(luma, roi, actors)   ← domain
   5. DumpingStateMachine.onObservation(obs) → List<Effect>     ← domain, pure
   6. EvidenceBuffer.offer(frame, tracks)            ← domain policy, buffers owned by orchestrator
   ▼
Effect executor (IO dispatcher, supervised; one effect's failure never kills the pipeline)
   • PlayDeterrent        → Deterrent.play()
   • CaptureHighRes       → EvidenceCamera.captureHighRes()
   • CommitIncident       → EvidenceStore + IncidentRepository (persist first)
   • RunPlatePipeline     → PlateDetector → TextRecognizer → PlateSanitizer (domain)
   • EnqueueAlert         → AlertSink.enqueue()
   • AbsorbIntoBaseline   → BaselineModel
```

### ROI-crop inference

The detector runs on the **bounding rectangle of the ROI** (plus a 10% margin so arriving actors are caught, and `roi_crop_headroom` above it: the ROI lies on the ground and people stand up from it), letterboxed to the model input. It does not run on the full frame. Two gains: compute drops roughly in proportion to the ROI share of the frame, and distant, small objects get more input pixels. Boxes are mapped back to normalised frame coordinates in the adapter.

### Threading and lifecycle rules

- The state machine is called from **one** coroutine only. No locks in the domain.
- ML models are created once per monitoring session and closed in `onDestroy`. They are never re-created per frame.
- Each model is confined to its **own dedicated single-thread executor** (ADR-0004); the orchestrator reaches it through `withContext(modelDispatcher)`. LiteRT create/run/close must never hop threads.
- Every `ImageProxy` is closed in `finally`. Pooled buffers are returned on every path, including failures.
- Thermal policy changes `analysis_fps` through a `StateFlow<PipelineConfig>`; the throttle reads it each frame.
- `MonitoringService` owns the session scope; cancelling the scope stops camera, ML and effects cleanly.

## 5. Persistence

- **Room** (`litterguard.db`): `incident`, `evidence_file`, `delivery_attempt`. Exported schemas live in `core/data/schemas/` and are committed; every schema change ships a tested `Migration`.
- **DataStore (Preferences)**: `SiteConfig` (ROI as a normalised JSON point list, thresholds, prefs).
- **Tink AEAD keyset** (Android Keystore master key): encrypts the Telegram bot token.
- **Files**: `filesDir/evidence/<incidentId>/{scene,actor_N,plate,highres_N}.jpg`, JPEG quality 85, with EXIF stripped of location. Site coordinates live only in incident metadata.
- **Purge**: a periodic `RetentionWorker` (daily) applies FR-6.5.

## 6. Alert delivery

```
CommitIncident ──▶ Room (deliveryState = PENDING)
                └▶ WorkManager.enqueueUniqueWork("deliver-<incidentId>", KEEP, DeliveryWorker)
DeliveryWorker: load incident + evidence → TelegramClient.sendMediaGroup(...)
   success → deliveryState = DELIVERED (+ telegram message id)
   retryable (IOException, 429 respecting retry_after, 5xx) → Result.retry() (exponential backoff)
   permanent (401/403/400) → deliveryState = FAILED + operator-visible error
```

Telegram review buttons (P1): a `TelegramUpdatesPoller` long-polls `getUpdates` with an offset while monitoring is active and maps `callback_query` to review labels.

## 7. Testing strategy (pyramid)

| Layer | Where | What |
| --- | --- | --- |
| Unit (most tests) | `:core:domain`, `:core:model` (JUnit Jupiter, Truth) | geometry, tracker, baseline diff on synthetic luma planes, state machine transition tables, plate sanitiser (table-driven) |
| **Scenario replay** | `:core:domain` test fixtures | Recorded field sequences (`observations.jsonl` = per-frame detections + downscaled luma) replayed through the full decision pipeline. Assert: an incident at the expected time, or none. **Every field false positive or false negative becomes a fixture.** |
| Adapter | infra modules (JUnit Jupiter + MockWebServer; instrumented where needed) | Telegram client contract, Room DAOs + migrations, LiteRT output parsing against a golden tensor |
| Instrumented | `androidTest` | Model loads and runs on device; camera binding smoke test; Compose UI for ROI drawing |
| Field | `docs/field-tests/` | Journeys J1–J7 (PRD §11) |

## 8. Model artefacts

- `.tflite` models are **not committed**. They are published as GitHub Release assets and downloaded at build time into `:core:ml` generated assets, verified against `core/ml/models.json` (name, version, url, sha256, input/output tensor shapes, classes, license, source). See ADR-0004.
- The app checks sha256 at load time and logs model versions into each incident.
- Training, export and evaluation live in `ml/` (Python) and never in the Android build.

# LitterGuard: Phase-wise Implementation Plan (Android MVP)

| Field | Value |
| --- | --- |
| Version | 1.0 · 2026-11-21 |
| Scope | Production-grade MVP that passes PRD journeys J1–J7 on the **Moto G54**, then a 7-day pilot |
| Inputs | [PRD v1.1](../product/PRD.md) · [Architecture](../architecture/ARCHITECTURE.md) · [ADRs](../decisions/) · [Device profile](../devices/moto-g54.md) |

## How to use this plan

- **Task IDs** are `P<phase>.<n>`. Reference them in branch names (`feat/p2.3-litert-detector`), commits (`feat(ml): LiteRT detector (P2.3, FR-2.3)`), and `STATUS.md`.
- **Tick a box only when its "Done when" criterion has been verified**: the command was run, or the field test was recorded. Partial work stays unticked and is described in `STATUS.md`.
- **Tags:**
  - `[human]` needs the owner to do something physical. The owner is non-technical, so each such task points to a plain step-by-step task in [OWNER_GUIDE.md](../owner/OWNER_GUIDE.md). The agent does every technical part.
  - `[ml]` is Python work in `ml/`.
  - Untagged tasks are app code an agent can do.
- **Size:**
  - **S** ≤ ½ day
  - **M** ≈ 1–2 days
  - **L** ≥ 3 days (agent-assisted, one developer)
- **Ordering:** `after:` lists hard dependencies. Everything else in a phase can run in parallel.
- **Every phase ends with a review task (`Px.R`):** prune LEARNINGS, close STATUS questions, check ADR statuses, add a PRD changelog line.

## Progress dashboard

Update the **Done** column when ticking boxes.

| Phase | Goal | Done / Total | Status | Exit gate |
| --- | --- | --- | --- | --- |
| [P0](#phase-0--foundation) | Foundation, decisions, device baseline | 7 / 12 | 🟡 In progress | CI green; G54 benchmark decides model size and accelerator |
| [P1](#phase-1--domain-core-jvm-no-device) | All decision logic, pure Kotlin, replay-tested | 3 / 15 | 🟡 In progress | Synthetic J2/J3 scenarios pass; ≥ 90% domain coverage |
| [P2](#phase-2--walking-skeleton-on-the-moto-g54-slice-1) | End-to-end loop on the phone (slice 1) | 0 / 15 | ⚪ | Foot-drop → siren < 8 s → Telegram photo; J1 < 3 min |
| [P3](#phase-3--evidence-and-anpr) | Evidence buffer, high-res capture, plates | 0 / 12 | ⚪ | H3 measured (≥ 70% human-readable plates in good light) |
| [P4](#phase-4--reliability-and-device-hardening) | Thermal, kiosk, recovery, endurance | 0 / 14 | ⚪ | J5, J6, J7 pass on the G54 |
| [P5](#phase-5--review-export-and-field-data-loop) | Human review, export, replay fixtures from the field | 0 / 7 | ⚪ | J4 passes; ≥ 10 real-site replay fixtures |
| [P6](#phase-6--custom-models-and-tuning) | Fine-tuned models, plate OCR A/B, tuning | 0 / 10 | ⚪ | Custom detector beats stock on field validation set |
| [P7](#phase-7--field-validation-and-mvp-release) | Full journeys at the site, pilot, v1.0 release | 0 / 6 | ⚪ | PRD §12 exit criteria met → tag `v1.0.0` |
| **Total** | | **10 / 91** | | |

## Tracks and critical path

```
Human track   P0.5 P0.6 P0.8(device) P0.9(audio) P0.10(bot) P0.11(site) ─▶ P2.14 ─▶ P3.11 ─▶ P4.13 ─▶ P5.6 ─▶ P7
App track     P1 (all JVM) ──────────────▶ P2 ─────────▶ P3 ─────────▶ P4 ─────────▶ P5 ─────────────────▶ P7.3/P7.5
ML track                 P2.2 (stock YOLO11n) ─▶ P3.4 (plate model, long pole) ─▶ P6 (needs P5 field data)
```

**Critical path:** P0.8 (G54 benchmark) → P2.2/P2.3 → P2.14 → P3.4 (plate model) → P3.11 → P4.13 → P5 → P6.3/6.4 → P7.4.
Start **P3.4 during Phase 2**; it only depends on public datasets.

**Rough calendar:** P0–P2 in about 2.5 weeks, P3–P4 about 2 weeks, P5–P6 about 2.5 weeks, P7 about 2–3 weeks, so **≈ 9–10 weeks** to the MVP exit. This is a planning estimate; re-baseline at each phase review.

---

## Phase 0 — Foundation

**Goal:** a repo, decisions and device knowledge solid enough that Phase 1 and 2 never stall.

- [x] **P0.1** Repo scaffold: 12 Gradle modules, convention plugins, version catalog, Spotless + ktlint + detekt + Lint. · S
  - Done when: `./gradlew spotlessCheck detekt test lintDebug assembleDebug -PwarningsAsErrors=true` is green. ✔ verified 2026-11-21
- [x] **P0.2** Specification set: PRD v1.1, ARCHITECTURE, ADR-0001/0002, BEST_PRACTICES, MEMORY_ARCHITECTURE, AGENTS.md. · M
- [x] **P0.3** Decisions D1–D3 recorded: AGPL-3.0 `LICENSE`, ML Kit section 7 exception (draft), `THIRD_PARTY_NOTICES.md`, ADR-0003/0004 accepted. · S
- [x] **P0.4** Official agent skills reviewed and vendored into `.claude/skills/` (CameraX, LiteRT runtime, CompiledModel scaffolding, on-device verification), pinned to a commit. · S
- [x] **P0.5** License exception: standard FSF wording kept, copyright line "© 2026 LitterGuard contributors" in the README. Owner informed. Revisit with a lawyer before any partnership or commercial deployment. · S
- [x] **P0.6** The owner created the public repo [DrOpenSource/litterguard](https://github.com/DrOpenSource/litterguard); the agent pushed `main`. Branch protection (CI required) is set after the first green CI run (P0.7). · S
- [x] **P0.7** CI: a GitHub Actions workflow runs the full gate with `-PwarningsAsErrors=true` on PRs and `main`. It uses Gradle and Android SDK caches, installs `platforms;android-37.2`, and uploads build reports when a run fails. Once a run is green, `main` requires the `gate` check. · S · after: P0.6
  - Done when: a PR shows a green required check. ✔ first run green 2026-11-21 ([run](https://github.com/DrOpenSource/litterguard/actions/runs/44905003545)); `main` is protected (PR + `gate` required, linear history, no force-push).
- [ ] **P0.8** Moto G54 baseline. **Owner:** OWNER_GUIDE **Task A** (About-phone screenshot, switch on USB debugging, plug in). **Agent, over adb:** the full checklist in [moto-g54.md](../devices/moto-g54.md), including `benchmark_model` for stock YOLO11n INT8 at 320/416/640 on CPU (2 and 4 threads) and on GPU, the camera high-res-mode probe, and `getThermalHeadroom` behaviour. · M
  - Done when: results are in LEARNINGS (`[device:moto-g54]`) and the **detector input size + accelerator** decision is appended to ADR-0003.
- [ ] **P0.9** `[human]` Record the deterrent audio (OWNER_GUIDE **Task C**; the agent copies the files over adb and normalises the loudness):
  - a warning clip (EN then Kannada, ≤ 6 s);
  - a pre-warning clip (for later A/B);
  - a short test tone.
  - Format: 48 kHz, loudness-normalised (around −14 LUFS); your own recording or a CC0 licence. Long lead time. · S
- [ ] **P0.10** `[human]` Create a Telegram bot and a private test group (OWNER_GUIDE **Task B**). The owner keeps the token private and types it into the app later; the chat ID is auto-detected by the app (P2.10). · S
- [ ] **P0.11** `[human]` Site reconnaissance (OWNER_GUIDE **Task D**): mount point, distance, lighting at dawn, dusk and night, power, Wi-Fi, usual dumping times. The agent copies the photos to `field-data/` (git-ignored) over adb. · S
- [ ] **P0.R** Phase review. · S

## Phase 1 — Domain core (JVM, no device)

**Goal:** every *decision* in the product is implemented and proven in pure Kotlin before any camera code exists (ADR-0001/0002).
**Exit:** all P1.13 scenarios pass in `./gradlew :core:domain:test`; domain line coverage ≥ 90%; the full gate is green.

- [x] **P1.1** Value types in `:core:model`. Covers `FR-2.4`. · S
  - Types: `NormalizedPoint`, `BoundingBox` (IoU, area, bottom-centre), `Polygon`, `ObjectCategory` (PERSON, VEHICLE, ANIMAL, OTHER), `Detection`, `TrackId`, `IncidentId`, `MonotonicTime`, `ImageBuffer`, `LumaPlane`. (`PlateRead` moves to P1.9; `Incident` and `EvidenceRef` move to P1.7, next to the logic that defines them.)
  - Done when: unit tests for IoU, area and bottom-centre pass. ✔ 17 tests, 2026-11-21
- [x] **P1.2** `DetectionConfig` with every PRD §6.4 parameter (units documented in KDoc), Low/Normal/High presets, and range validation. · S · after: P1.1 ✔ 8 tests, including "every preset meets the 8 s J2 latency budget"
- [x] **P1.3** ROI geometry. Covers `FR-1.2 FR-1.3 FR-2.4`. · S · after: P1.1
  - Scope: point-in-polygon (edge counts as inside), self-intersection check, polygon bounds, normalised↔pixel mapping with rotation, and the ROI-crop rectangle with margin.
  - Done when: table-driven tests pass, covering concave, degenerate and edge cases. ✔ 36 tests, 2026-11-21
- [ ] **P1.4** `MotionGate`: downscale the ROI luma, per-pixel diff threshold, changed-fraction score. Covers `FR-2.2`. · S · after: P1.3
- [ ] **P1.5** `Tracker`. Covers `FR-2.5`. · M · after: P1.1
  - Scope: IoU association with a centroid fallback, enter/exit hysteresis, in-ROI test by bottom-centre, a per-track static-duration counter, and class smoothing.
  - Done when: tests cover crossing tracks, occlusion gaps of 1–2 frames, and parked vehicles.
- [ ] **P1.6** `BaselineModel`. Covers `FR-2.6`, PRD §6.2. · L · after: P1.3
  - Scope: rolling-median refresh, freeze/unfreeze, brightness normalisation, gradient + luma diff, morphology, connected components, min area, actor-overlap exclusion, illumination abort, and absorbing an object into the baseline.
  - Done when: synthetic luma tests pass for a new blob, a global brightness change, a shadow edge, and camera shake.
- [ ] **P1.7** `DumpingStateMachine`: pure `(state, observation, config, now) → (state', effects)`. Covers `FR-3.1–3.5`. · M · after: P1.5, P1.6
  - Scope: all PRD §6.3 transitions, animal exclusion, `UNATTRIBUTED_OBJECT`, `INCONCLUSIVE`, merging within cooldown, the parked-actor timeout, and the pre-warning flag (**default off**, D2).
  - Done when: a transition-table test covers every (state, input) pair.
- [ ] **P1.8** `EvidencePolicy`. Covers `FR-5.1 FR-5.2`. · S · after: P1.5
  - Scope: Laplacian-variance sharpness on luma, top-K scoring (area × sharpness × centrality), speculative-capture decisions (≤ 2 per track), and the 2-minute expiry.
- [ ] **P1.9** `PlateSanitizer`. Covers `FR-5.5 FR-5.6 FR-5.7`. · S
  - Scope: normalise, merge two-line plates, positional confusion correction, the standard and BH regexes, a score, and best-of-K selection.
  - Done when: a table-driven test with ≥ 40 cases (real-world formats, OCR noise) passes.
- [ ] **P1.10** Ports (ARCHITECTURE §3), plus fakes in `testFixtures`: `FakeClock`, `FakeObjectDetector`, `InMemoryIncidentRepository`, and so on. · S · after: P1.1
- [ ] **P1.11** `DecisionPipeline` facade: one `Observation` in → motion → tracker → baseline → state machine → evidence policy → effects out. Pure and deterministic. · S · after: P1.4–P1.8
- [ ] **P1.12** Scenario replay harness. · M · after: P1.11
  - Scope: a JSONL observation format (timestamp, detections, luma reference), a synthetic scene generator (luma planes with moving boxes and dropped blobs), and a runner with an assertion DSL (`expectIncident(at = 12.3.s ± 1.s)`, `expectNoAlert()`).
- [ ] **P1.13** Synthetic acceptance scenarios. · M · after: P1.12
  - J2 cases: on foot, scooter, car.
  - J3 cases: walk through carrying a bag, stop briefly with a bag, dog, cow, cloud/lighting change, return for the bag, a parked car leaving, someone lingering in the ROI.
  - Done when: all of them behave per the PRD, and the modelled trigger latency is ≤ 6.8 s.
- [ ] **P1.14** Kover coverage report and threshold (≥ 90% lines for `:core:domain`) wired into the gate. · S
- [ ] **P1.R** Phase review. · S

## Phase 2 — Walking skeleton on the Moto G54 (Slice 1)

**Goal:** the whole loop runs on the real phone with the stock COCO detector: camera → detect → decide → siren → persist → Telegram.
**Exit:** on the G54, a foot-drop gives a siren < 8 s after the actor leaves and a Telegram photo; J1 setup < 3 min; zero false sirens in a 10-minute walk-through.

- [ ] **P2.1** Model delivery (ADR-0004). · M
  - Scope: the `core/ml/models.json` schema; a Gradle task that downloads models, checks sha256 (and fails the build on mismatch), and supports the `-PlitterguardModelsDir` override; `noCompress "tflite"`; `abiFilters "arm64-v8a"`.
  - Done when: a clean checkout builds with the model fetched and verified.
- [ ] **P2.2** `[ml]` Export stock YOLO11n (COCO) to LiteRT INT8 at the P0.8 input size. Calibrate on viewpoint frames from P0.11 (or a COCO subset until those exist), publish as a Release asset, and add the `models.json` entry with real tensor shapes. · S · after: P0.8
- [ ] **P2.3** `LiteRtObjectDetector`. Covers `FR-2.3 FR-2.7`. · M · after: P2.1, P2.2, P1.10
  - Scope: a dedicated thread, a shared `Environment`, GPU → CPU fallback with the actual accelerator logged, warm-up, letterbox preprocessing per `models.json` layout, a `[1, 84, N]` decoder plus class-wise NMS as pure JVM-tested functions, and the COCO → `ObjectCategory` mapping.
  - Done when: an instrumented golden test (shapes + fixture image) passes on the G54.
- [ ] **P2.4** CameraX `FrameSource`. Covers `FR-2.1`. · M · after: P1.10
  - Scope: RGBA_8888 at 1280×720 via `ResolutionSelector`, `KEEP_ONLY_LATEST`, a timestamp throttle driven by `PipelineConfig`, rotation, pooled `ImageBuffer` + `LumaPlane`, and a `close()` on every path.
  - Done when: a 10-minute run on the G54 shows a stable FPS and no frame leaks (buffer pool counters).
- [ ] **P2.5** ROI-crop inference: crop rectangle plus margin, letterbox, map boxes back to frame coordinates. · S · after: P2.3, P1.3
- [ ] **P2.6** `PipelineOrchestrator` + supervised `EffectExecutor` in `:core:monitoring`, with per-stage latency metrics. · M · after: P1.11, P2.4
- [ ] **P2.7** Persistence v1. Covers `FR-6.1 FR-1.3`. · M · after: P1.10
  - Room: `incident`, `evidence_file`, `delivery_attempt`, with the schema exported and DAO tests.
  - DataStore: `SiteConfig`, with the ROI as JSON.
  - `EvidenceStore`: JPEG at quality 85, EXIF location stripped.
- [ ] **P2.8** Deterrence v1. Covers `FR-4.1 FR-4.2 FR-1.4`. · S · after: P0.9
  - Scope: bundled clips, `AudioFocus` (`USAGE_ALARM`), maximum volume and then restore, route detection (Bluetooth vs speaker), and the **Test Siren** button.
- [ ] **P2.9** Telegram v1. Covers `FR-7.1 FR-7.2 FR-1.5`. · M · after: P2.7
  - Scope:
    - `SecretStore` (Tink AEAD, Keystore).
    - `TelegramClient`: `sendMessage`, `sendPhoto`, `sendMediaGroup`, with a token-redacting interceptor.
    - `DeliveryWorker`: unique work, backoff, respects `429 retry_after`, distinguishes permanent failures.
    - MockWebServer contract tests and a **Send test alert** button.
- [ ] **P2.10** Setup feature. Covers `FR-1.1–1.5`. · M · after: P2.7, P1.3
  - Scope: onboarding and permission requests (Camera, Notifications, one-time Location); the ROI editor on the `camera-compose` viewfinder (drag, undo, clear, self-intersection guard); and settings. Settings are designed for a non-technical operator: paste the bot token, then **the app finds the group chat automatically** (`getUpdates` after a `/start` in the group), with no chat-ID typing.
- [ ] **P2.11** Monitor feature: start/stop and a debug overlay showing boxes, track IDs, state, FPS, accelerator and motion score. The overlay draws in the draw phase. · M · after: P2.6
- [ ] **P2.12** `MonitoringService` v1: a `camera`-type foreground service, an ongoing notification with a Stop action, the session scope, and the manifest permissions. Covers `FR-8.1`. · S · after: P2.6
- [ ] **P2.13** App shell: navigation (setup → monitor → incidents), Hilt WorkManager configuration, and the `:core:ui` theme. · S
- [ ] **P2.14** `[human]` Field smoke test on the G54: time J1, run 5 foot-drop trials and a 10-minute J3 walk-through, and record the results in `docs/field-tests/`. Latency comes from the logs. · S · after: P2.1–P2.13
- [ ] **P2.R** Phase review. · S

## Phase 3 — Evidence and ANPR

**Goal:** incidents carry usable evidence: the actor or vehicle frames, a high-res still, and a plate read.
**Exit:** H3 is measured and recorded, with ≥ 70% human-readable plates for vehicle incidents in good light at the chosen mount (or a documented gap and mitigation).

- [ ] **P3.1** Evidence buffer wiring: copy top-K frames per vehicle track out of the pool, enforce a memory cap, and discard them on expiry. Covers `FR-5.1`. · S · after: P2.6, P1.8
- [ ] **P3.2** Speculative high-res `ImageCapture`. Covers `FR-5.2 FR-5.3`. · M · after: P3.1
  - Scope: probe for the highest-resolution mode the G54 exposes (50 MP if available); `MINIMIZE_LATENCY`; at most 2 captures per track; rate-limited so analysis stays ≥ 2 FPS; plus a scene capture on confirmation.
- [ ] **P3.3** Zoom: a CameraX linear-zoom slider in setup, persisted, with the ROI drawn after zoom. Covers `FR-1.7`. · S · after: P2.10
- [ ] **P3.4** `[ml]` Plate detector. · L
  - Scope: assemble Indian plate data (IEEE DataPort LPR, Indian plates in the wild, Mendeley IURS; record licences in `ml/configs/SOURCES.md`); train single-class YOLO11n-plate; export LiteRT INT8; write an mAP report in `ml/eval/`.
  - **Start during Phase 2.**
- [ ] **P3.5** `LiteRtPlateDetector` on vehicle crops (reusing the P2.3 infrastructure), with golden tests. · S · after: P3.4
- [ ] **P3.6** Plate-crop processing: deskew, upscale to the OCR minimum, contrast normalisation, two-line detection. · M · after: P3.5
- [ ] **P3.7** ML Kit `TextRecognizer` adapter: bundled Latin model, one client, coroutine `await`, lines ordered top to bottom. · S
- [ ] **P3.8** Plate effect pipeline: buffered frames and captures → plate detection → OCR → `PlateSanitizer` best-of-K, persisting the raw text and confidence. Covers `FR-5.4–5.7`. · S · after: P3.2, P3.6, P3.7, P1.9
- [ ] **P3.9** Alert v2: `sendMediaGroup` (scene + vehicle + plate crop) with the PRD caption format. · S · after: P3.8, P2.9
- [ ] **P3.10** Incidents feature v1: a list, and a detail view with the evidence images and the plate (parsed and raw). Covers `FR-6.2`. · M · after: P2.7
- [ ] **P3.11** `[human]` Field test: 10 scooter and 10 car trials at about 10, 15 and 20 m in daylight. Measure H3 (human-readable %, OCR exact-match %) and tune zoom and mount. · M · after: P3.1–P3.10
- [ ] **P3.R** Phase review. · S

## Phase 4 — Reliability and device hardening

**Goal:** the phone can be left alone for hours, and every failure is either recovered or reported.
**Exit:** J5 (3 h endurance), J6 (offline) and J7 (recovery) pass on the G54.

- [ ] **P4.1** `ThermalWatchdog`. Covers `FR-8.4`. · M
  - Scope: a status listener plus headroom polling (NaN-safe) feeding a policy that sets `PipelineConfig` to 3 / 2 / 0.5 FPS or pauses ML. The torch is disabled at SEVERE and above. The policy itself is unit-tested in the domain.
- [ ] **P4.2** `StreamUseCase` hint (API 33+) for long sessions, A/B-tested on the G54 for 30 minutes and recorded in LEARNINGS. · S · after: P4.1
- [ ] **P4.3** Kiosk mode: black UI, minimum brightness, keep-screen-on, tap to peek at status. Covers `FR-8.2`. · S
- [ ] **P4.4** Wake lock: partial, tagged, renewed on a timer, released on every path. Covers `FR-8.3`. · S
- [ ] **P4.5** Camera recovery: observe `cameraState`, rebind with backoff, alert the operator after N failures. Covers `FR-8.5`. · M
- [ ] **P4.6** Process death and reboot: a persisted "monitoring intended" flag, a resume notification, and an operational Telegram alert. The `BOOT_COMPLETED` receiver only notifies. Covers `FR-8.5`. · S
- [ ] **P4.7** Battery-optimisation exemption flow, with Motorola-specific steps verified on the G54. Covers `FR-8.6`. · S
- [ ] **P4.8** Quiet hours: cap the volume or switch to torch only. Covers `FR-4.4`. · S
- [ ] **P4.9** Operational alerts: start/stop, thermal SEVERE, camera error, reboot, low storage. Covers `FR-7.4`. · S · after: P2.9
- [ ] **P4.10** `RetentionWorker` (images after 30 days, metadata after 180), orphan-file cleanup, and a low-storage warning. Covers `FR-6.5`. · S
- [ ] **P4.11** Observability: a rolling Timber file log (10 MB cap), debug log export, and p50/p90 latency per stage in the overlay. · S
- [ ] **P4.12** LeakCanary in debug builds, plus a heap sample logged every 10 minutes. · S
- [ ] **P4.13** `[human]` On the G54: J5 (3 hours on a charger, indoors), J6 (Wi-Fi off, 2 incidents, Wi-Fi on), J7 (kill the process; reboot). Record the reports. · M · after: P4.1–P4.12
- [ ] **P4.R** Phase review. · S

## Phase 5 — Review, export and field-data loop

**Goal:** humans can label incidents, and real-site data flows back into tests and training.
**Exit:** J4 passes; ≥ 10 real-site replay fixtures are in the test suite.

- [ ] **P5.1** In-app review (Confirm / False alarm), plus filters by label and kind. Covers `FR-6.2`. · S
- [ ] **P5.2** Telegram inline review buttons and a `getUpdates` long-poll (offset persisted) while monitoring. Covers `FR-6.3`. · M
- [ ] **P5.3** Export: a zip with images, `incidents.json` and YOLO-format labels, shared via the share sheet, with the schema documented. Covers `FR-6.4`. · M
- [ ] **P5.4** Debug "record session" mode: writes observation JSONL and downscaled luma frames to app-private storage. Local only; auto-deleted after 7 days. · M
- [ ] **P5.5** Turn the first field recordings into replay fixtures in `core/domain/src/test/resources/scenarios/`, at a downscale where no plates or faces are readable. · M · after: P5.4
- [ ] **P5.6** `[human]` J4 field test, and label the first week of incidents. · S
- [ ] **P5.R** Phase review. · S

## Phase 6 — Custom models and tuning

**Goal:** models and thresholds tuned to *our* viewpoint, and ML Kit made optional.
**Exit:** the shipped custom detector beats stock on the field validation set (higher recall at an equal false-alarm rate), golden tests are green, and presets are tuned.

- [ ] **P6.1** `[ml]` Workspace: a pinned `requirements.txt` (Ultralytics version), `SOURCES.md` listing the licence of every dataset and model, a scripts skeleton, and one CLI entry point. · S
- [ ] **P6.2** `[ml]` Dataset assembly. · L · after: P5.3
  - Contents: a COCO subset (person, vehicles, dog, cow) plus Indian traffic classes (auto-rickshaw, goods carrier; decision D4) plus labelled field exports.
  - Split by site and day, never at random by frame.
- [ ] **P6.3** `[ml]` Fine-tune YOLO11n and YOLO26n at the chosen size, calibrate INT8 on field frames, and export to LiteRT. · L · after: P6.2
- [ ] **P6.4** Benchmark on the G54 and record the pick in an ADR-0003 addendum and a `models.json` bump. · M · after: P6.3
  - Compare: INT8 raw head vs w8a32 NMS-free; CPU with 2 and 4 threads vs GPU.
  - Measure: recall on small and distant objects in the field validation set.
- [ ] **P6.5** Litter confidence booster: either a small garbage-bag/litter classifier (trained on litter-detect + TACO crops) or a `litter` detector class. It adds confidence but never gates the trigger. Measure the false-alarm change on the fixtures. · M
- [ ] **P6.6** fast-plate-ocr. · L · after: P3.11
  - Fine-tune on Indian plates (including two-line), export TFLite, and A/B it against ML Kit on field plate crops through a `PlateOcr` adapter.
  - Adopt it if exact match improves by 10 percentage points or more. Either way, add a `foss` build flavour without ML Kit.
- [ ] **P6.7** `[ml]` MIVIA-IWDD clips, if the licence permits: convert them to replay inputs and measure detection rate and time-to-detect against ground truth. · M
- [ ] **P6.8** Threshold tuning: sweep `DetectionConfig` over the field fixtures, set the presets, and update PRD §6.4. · M · after: P5.5
- [ ] **P6.9** Low-light evaluation: CameraX Low Light Boost (AE mode) on the G54 if supported; night trials; document the operating envelope. This addresses the top PRD risk. · M
- [ ] **P6.R** Phase review. · S

## Phase 7 — Field validation and MVP release

**Goal:** prove the hypotheses H1–H4 at a real site and ship v1.0.0.
**Exit:** PRD §12 criteria are met, and `v1.0.0` is tagged with its source, APK and models.

- [ ] **P7.1** `[human]` Site go-live prerequisites: signage ("CCTV surveillance in operation"), an RWA briefing, and Telegram group governance (members, retention notice). · S
- [ ] **P7.2** `[human]` A full J1–J7 run on the G54 at the real site, with reports in `docs/field-tests/`. · M · after: P6
- [ ] **P7.3** Fix-and-retest loop: every failure becomes a replay fixture plus a fix plus a retest. · M · after: P7.2
- [ ] **P7.4** `[human]` A 7-day pilot with a daily label review, tracking the PRD §12 metrics (false alarms < 1/day, true incidents). · L (calendar) · after: P7.3
- [ ] **P7.5** Release engineering. · M
  - Scope: a release keystore kept outside git (procedure documented), versioning, a `CHANGELOG.md`, and a GitHub Release with the APK, model assets and source (AGPL).
  - Done when: an R8 release build is verified on the G54.
- [ ] **P7.6** MVP exit report: a verdict on each of H1–H4 with evidence, plus v2 recommendations (outdoor camera, edge box, night IR, multiple sites). · S · after: P7.4

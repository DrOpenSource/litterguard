# LitterGuard — Product Requirements Document (Android MVP)

| Field | Value |
| --- | --- |
| Version | 1.1 (supersedes the v1.0 draft) |
| Status | Draft v1.1: D1–D3 decided 2026-11-21; the remaining open decisions are in §14 |
| Last updated | 2026-11-21 |
| Scope | Single-Android-phone prototype that validates detection, deterrence and evidence capture |
| Related | [ARCHITECTURE.md](../architecture/ARCHITECTURE.md) · [REPO_VALIDATION.md](../research/REPO_VALIDATION.md) · [ADRs](../decisions/) |

---

## 1. Problem

People dump waste illegally and repeatedly on open plots and outside gated residential societies in Bangalore. They come on foot, by two-wheeler, or by car, usually at times when nobody is watching. RWAs and residents have no cheap way to:

1. **deter** a dumper while the dumping is happening,
2. **prove** it happened (a timestamped photo, ideally with a vehicle registration), and
3. **report** it fast enough for anyone to act.

CCTV with a human watching it is expensive. Recorded CCTV only gets reviewed after the fact, if at all.

## 2. Goal and hypothesis

**MVP goal:** Using one ordinary Android phone, prove that we can reliably detect the act of dumping, deter it on the spot, and produce usable evidence. We do this before spending on outdoor cameras or edge hardware.

**Hypotheses to validate:**

| # | Hypothesis | Measured by |
| --- | --- | --- |
| H1 | On-device CV can tell a real dumping event from passers-by, traffic and animals | J2 detection rate ≥ 80%, J3 zero false sirens |
| H2 | An automated audio warning is fast enough to matter | Trigger latency < 8 s after the actor leaves |
| H3 | A phone camera at balcony distance captures evidence that is readable by eye, and partly by OCR | ≥ 70% of vehicle incidents have a human-readable plate; OCR exact-match ≥ 50% on those |
| H4 | A mid-range phone survives continuous duty indoors | 3 h run: no crash, no thermal shutdown, ≥ 2 FPS |

## 3. Scope

### 3.1 In scope (MVP)

- Drawing a polygon ROI ("Dump Zone") and saving it.
- On-device detection of people, vehicles and newly left objects inside the ROI.
- A dumping state machine that confirms an *abandoned* object (see §6).
- Instant deterrence: a bilingual audio warning (English + Kannada) and an optional torch pulse.
- Evidence capture: a scene snapshot, frames of the actor/vehicle, a plate crop, and OCR text.
- A local incident log (Room) with human review (Confirm / False alarm).
- Alert delivery to a Telegram group, with a generic webhook (e.g. Supabase Edge Function) as an alternative.
- Device safeguards: foreground service, dimmed kiosk mode, thermal watchdog, recovery after a crash or reboot.
- Export of labelled incidents for later model fine-tuning.

### 3.2 Out of scope (v1)

- Facial recognition or identification of people. It is excluded for DPDP Act privacy risk and poor reliability in low light, and this is a hard constraint, not a backlog item.
- Weatherproof 24/7 outdoor deployment; IR / night vision.
- Direct BBMP / government API integration.
- Cloud video streaming or continuous recording. Only incident frames are stored.
- Multiple cameras or sites per device; a cloud dashboard.
- iOS.

### 3.3 Deployment assumptions

- The phone is mounted **indoors** (balcony, window or security cabin) and overlooks the dumping spot, which is **lit or in daylight**.
- The phone is on mains power and paired with a Bluetooth speaker. If no speaker is connected, audio goes to the phone speaker.
- Wi-Fi is usually available. The app must queue alerts when it is not.
- The site location is fixed: GPS is captured **once during setup**, not for every incident. This avoids background-location permission and poor indoor GPS fixes.
- Distance to the ROI is roughly 5–25 m.

## 4. Users

| Persona | Needs | Main surface |
| --- | --- | --- |
| **Operator**: RWA volunteer or security guard | Set up in under 3 minutes, trust that it is running, review incidents | App (setup, kiosk, incident log) |
| **Alert recipient**: RWA members | Get a clear photo and plate fast, in a group chat they already use | Telegram group |
| **Developer / ML engineer** | Tune thresholds, inspect debug overlays, export labelled data | Debug overlay, export, logs |

## 5. Functional requirements

Priority: **P0** = required for the MVP field test · **P1** = should have in the MVP · **P2** = later.

### 5.1 Setup and calibration

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-1.1 | First-run onboarding asks for Camera, Notifications and (one-time, foreground) Location permissions, explaining why each is needed. The app works in degraded mode without Location; the site then has no coordinates. | P0 |
| FR-1.2 | The operator taps 4–12 points on the live preview to define the ROI polygon. Points can be dragged, undone and cleared. The polygon must not self-intersect. | P0 |
| FR-1.3 | ROI coordinates are stored **normalised (0–1) to the analysis frame** so they survive changes of resolution or rotation, and they persist across restarts (DataStore). | P0 |
| FR-1.4 | "Test Siren" plays the deterrent clip through the current audio route and shows which route it is (Bluetooth or phone speaker). | P0 |
| FR-1.5 | Settings: site name, Telegram bot token (stored encrypted), with the **group chat detected automatically** after the operator sends `/start` in the group (no typing of chat IDs), "Send test alert", language order of the audio, quiet hours. | P0 |
| FR-1.6 | A sensitivity preset (Low / Normal / High) maps to the parameter table in §6.4; per-parameter overrides are available in Developer options. | P1 |
| FR-1.7 | A camera-zoom slider (CameraX linear zoom) so a distant ROI fills more of the frame. | P1 |

### 5.2 Ingestion and detection pipeline

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-2.1 | Sample ImageAnalysis frames at **3 FPS** by default (configurable 0.5–5), using `STRATEGY_KEEP_ONLY_LATEST` with timestamp gating. Never process at the sensor frame rate. | P0 |
| FR-2.2 | Stage 0, the motion gate: a cheap luma frame-diff on a downscaled (~160×120) grayscale crop of the ROI. ML runs only when motion is above threshold **or** the state machine is not IDLE. | P0 |
| FR-2.3 | Stage 1, actor detection: an INT8 LiteRT detector finds `person`, `bicycle`, `motorcycle`, `car`, `truck`, `auto-rickshaw`*, and animals (`dog`, `cow`). Animals are tracked so they can be **excluded** as actors. | P0 |
| FR-2.4 | An actor counts as "in ROI" when the bottom-centre of its box (its ground contact point) lies inside the polygon. | P0 |
| FR-2.5 | A lightweight IoU/centroid tracker (ByteTrack-style, no re-identification) gives actors stable track IDs across frames. | P0 |
| FR-2.6 | Stage 2, abandoned-object detection is **class-agnostic** and based on a baseline diff (§6.2). An optional litter classifier raises confidence but **does not gate** the trigger in the MVP. | P0 |
| FR-2.7 | Hardware acceleration via the LiteRT CompiledModel API: GPU (or NPU where available) with automatic CPU fallback. The chosen accelerator is logged. | P1 |

\* `auto-rickshaw` is not a COCO class. Until we have a custom model it maps to `car`/`truck`/`motorcycle`; see §14.

### 5.3 Dumping decision

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-3.1 | The state machine in §6 confirms dumping only when **an actor was present, a new object persists after the actor leaves, and it stays static ≥ 5 s**. | P0 |
| FR-3.2 | A new object with no attributable actor (e.g. one that appears after an animal visit) is logged as `UNATTRIBUTED_OBJECT`. No siren and no alert, but it is visible for review. | P0 |
| FR-3.3 | A **60 s cooldown** follows each confirmed alert. Further detections within the cooldown are merged into the same incident. | P0 |
| FR-3.4 | A confirmed object is absorbed into the baseline so it does not trigger again. | P0 |
| FR-3.5 | Optional pre-warning: if a new static object appears **while** an actor is still in the ROI, play a short "This area is under camera surveillance" clip. Off by default (see §14). | P1 |

### 5.4 Deterrence

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-4.1 | On confirmation, play the warning clip (English, then Kannada) through the active route at the configured volume, starting within 500 ms of confirmation. Example text: *"Illegal dumping detected. Your vehicle is being recorded."* | P0 |
| FR-4.2 | Audio focus is requested and volume is restored afterwards. The clip is bundled (offline), not TTS. | P0 |
| FR-4.3 | Torch pulses 3 times, optionally. The torch is shared with the camera, so this uses `CameraControl.enableTorch` on the bound camera; the pulse must not interrupt analysis. | P1 |
| FR-4.4 | Quiet hours (default 22:00–06:00): siren volume is capped or replaced by torch-only. This respects residential noise limits under the Noise Pollution (Regulation & Control) Rules, 2000. | P1 |

### 5.5 Evidence and plate capture (ANPR)

> **Correction to v1.0.** v1.0 captured the full-resolution frame *after* the trigger. The trigger fires only after the vehicle has **left** the ROI and 5 s have passed, so that frame cannot contain the plate. Evidence must be gathered **while the actor is present** and committed when dumping is confirmed. See [ADR-0002](../decisions/ADR-0002-abandoned-object-detection-and-evidence-buffer.md).

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-5.1 | **Pre-event evidence buffer.** While any actor is in the ROI, keep the top-K (K = 3) analysis frames per vehicle track, scored by box area × sharpness (variance of Laplacian) × how central the box is. | P0 |
| FR-5.2 | **Speculative high-res capture.** When a vehicle track has been stable for ≥ 2 frames, trigger a CameraX `ImageCapture` (minimise-latency mode), at most 2 per track. Discard it if no incident follows within 2 min. | P0 |
| FR-5.3 | On confirmation, capture one high-res **scene** frame showing the dumped object. | P0 |
| FR-5.4 | Plate pipeline: vehicle crop → plate detector (`.tflite`) → plate crop (with deskew and upscale if smaller than 2× the OCR minimum) → ML Kit Text Recognition v2 (bundled Latin, offline). Only the **plate crop** goes to OCR. | P0 (detector P1; until it lands, OCR runs on the lower third of the vehicle crop) |
| FR-5.5 | Two-line plates, common on Indian two-wheelers: join OCR lines top to bottom before validating. | P0 |
| FR-5.6 | Plate normalisation and validation: uppercase; strip non-alphanumerics; apply position-aware confusion correction (letter positions `0→O 1→I 8→B 5→S 2→Z`, digit positions `O→0 I→1 B→8 S→5 Z→2 G→6 D→0`); validate against **both** patterns:<br>• standard `^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}$` (e.g. `KA05MN1234`, `DL3CAB1234`)<br>• Bharat series `^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$` (e.g. `22BH1234AA`)<br>Always keep the raw OCR text and a confidence score; a failed validation never discards evidence. | P0 |
| FR-5.7 | Choose the best plate read across all buffered frames of the track, by validation and then confidence. | P1 |

### 5.6 Logging, review and export

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-6.1 | Each incident is saved locally: ID (UUID), site, timestamps (actor entered, object left, confirmed), actor types, object box, evidence files, plate (parsed, raw, confidence), delivery status, pipeline and model versions, FPS and thermal state at the time. | P0 |
| FR-6.2 | Incident log screen: list plus detail view with the evidence images and **Confirm dumping / False alarm** buttons. | P0 |
| FR-6.3 | Telegram inline buttons for review (Confirm / False alarm), received by `getUpdates` long-polling from the device. | P1 |
| FR-6.4 | Export labelled incidents as a zip (images + JSON metadata + YOLO-format labels) via the Android share sheet, for fine-tuning. | P1 |
| FR-6.5 | Retention: evidence images are auto-deleted after **30 days** (configurable); incident metadata is kept for 180 days. | P0 |

### 5.7 Alerting

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-7.1 | When dumping is confirmed, queue a Telegram `sendMediaGroup` (scene + vehicle + plate crop) with a caption: site, local time, plate (or "unreadable"), confidence, incident ID. | P0 |
| FR-7.2 | Delivery runs through WorkManager (network-connected constraint, exponential backoff), keyed on the incident ID so retries are idempotent. Alerts survive app restarts and offline periods. | P0 |
| FR-7.3 | A generic HTTPS webhook (JSON + multipart images) is the alternative sink, for a Supabase Edge Function. | P2 |
| FR-7.4 | Operational alerts to the same chat: monitoring started/stopped, thermal SEVERE, camera error, device rebooted. | P1 |

### 5.8 Stability and device safeguards

| ID | Requirement | Pri |
| --- | --- | --- |
| FR-8.1 | Monitoring runs inside a **foreground service** of type `camera`, started from the foreground UI (Android 14+ rule), with a persistent notification and a Stop action. | P0 |
| FR-8.2 | **Kiosk mode:** while monitoring, a black screen with a minimal status dot, minimum brightness, `FLAG_KEEP_SCREEN_ON`, and touch to wake the status. Screen-on kiosk is the primary mode because some OEMs throttle camera access with the screen off. | P0 |
| FR-8.3 | A partial wake lock is held only while monitoring, released on stop, and renewed with a timeout (never held indefinitely). | P0 |
| FR-8.4 | **Thermal watchdog** with `PowerManager.addThermalStatusListener` and `getThermalHeadroom()`:<br>• NONE/LIGHT → 3 FPS<br>• MODERATE → 2 FPS<br>• SEVERE → 0.5 FPS + operator alert<br>• CRITICAL or worse → ML paused, motion gate only, operator alert | P0 |
| FR-8.5 | Recovery: if the camera disconnects or errors, rebind with backoff. After a crash or reboot, post a "Tap to resume monitoring" notification and an operational Telegram alert. (Android 15+ does not allow a camera foreground service to start from `BOOT_COMPLETED`.) | P1 |
| FR-8.6 | Prompt the operator to exempt the app from battery optimisation, with OEM-specific instructions (Xiaomi, Samsung, Oppo/Vivo/Realme). | P1 |

## 6. Dumping detection logic (normative)

### 6.1 Why the v1.0 rule had to change

v1.0 required a `trash_bag/litter` **detector class** to appear. That is fragile here:

- **TACO** is mostly close-up, hand-held photos of single litter items. That is a different domain from a phone looking down at a plot from 15 m.
- Dumped waste in Bangalore is varied: sacks, cartons, construction debris, food waste in plastic covers. No single class covers it.
- A class-gated trigger turns every missed detection into a missed incident.

The MVP therefore detects **"something new that was left behind by an actor"** without depending on its class. The litter model is a *confidence booster* and a future filter, not the gate.

### 6.2 Baseline diff (abandoned-object detector)

1. **Baseline B.** While the ROI is quiet (state IDLE, low motion), refresh a grayscale ROI baseline every 30 s, keeping a short rolling median to absorb slow lighting drift.
2. **Freeze.** When an actor enters, freeze B (the "before" picture).
3. **Compare.** After every actor has left and motion has settled (≥ 1 s), compute a **gradient-plus-luma** difference between B and the current frame. The gradient term suppresses shadows and soft lighting changes. Normalise for global brightness first.
4. **Blobs.** Threshold, apply morphological open/close, take connected components inside the ROI, and discard blobs below `object_min_area` or overlapping any live actor/animal box.
5. **Persistence.** A candidate must keep IoU ≥ 0.6 with its first appearance for `dwell_time`.
6. **Abort guards.** If the diff covers > 35% of the ROI, treat it as an illumination change or camera shake: re-baseline and log `INCONCLUSIVE`.

This also covers the parked-vehicle case without special code. B is frozen before the car arrives and compared after it leaves, so the car itself never becomes a "new object".

### 6.3 State machine

```
            actor enters ROI (≥2 frames)
   IDLE ───────────────────────────────▶ ACTOR_PRESENT ◀───────────┐
    ▲  ▲                                   │  (evidence buffer on) │ actor re-enters
    │  │ all actors gone, no new object    │                       │
    │  └───────────────────────────────────┤ all actors gone       │
    │                                      ▼ + new blob found      │
    │  object removed / blob gone     OBJECT_LEFT_PENDING ─────────┘
    ├──────────────────────────────────────┤
    │                                      │ blob static ≥ dwell_time
    │                                      ▼
    │      cooldown elapsed            CONFIRMED ──▶ deterrence + evidence commit + alert
    └──────────────────────────────── COOLDOWN ◀──┘ (baseline absorbs object)
```

- **ACTOR_PRESENT:** freeze the baseline; buffer evidence (FR-5.1/5.2). An actor that has been static longer than `parked_actor_timeout` stays tracked but stops consuming high-res captures.
- **Animals only** never move the machine to ACTOR_PRESENT. A blob that appears after an animal-only visit becomes `UNATTRIBUTED_OBJECT` (FR-3.2).
- **A person carrying a bag through the ROI** produces no persistent blob after they leave, so the machine returns to IDLE. This is exactly the J3 false-positive case.
- **Inside COOLDOWN** the detector keeps running; new confirmations are merged into the current incident (FR-3.3).

### 6.4 Default parameters

All of these live in one `DetectionConfig` data class; nothing is hard-coded elsewhere.

| Parameter | Default | Notes |
| --- | --- | --- |
| `analysis_fps` | 3.0 | Lowered by the thermal policy |
| `analysis_resolution` | 1280×720 | Downscaled internally for the motion gate and baseline |
| `motion_luma_threshold` | 25 / 255 | Per-pixel, on the 160×120 ROI crop |
| `motion_min_fraction` | 0.5% of ROI | |
| `actor_min_confidence` | 0.45 | Per class; tuned in the field |
| `actor_enter_frames` / `actor_exit_frames` | 2 / 3 | Hysteresis against flicker |
| `settle_time` | 1.0 s | After the last actor leaves |
| `object_min_area` | 0.15% of frame | Should scale with ROI distance (P2) |
| `dwell_time` | 5.0 s | From v1.0 |
| `object_stability_iou` | 0.6 | |
| `illumination_abort_fraction` | 35% of ROI | |
| `cooldown` | 60 s | From v1.0 |
| `baseline_refresh` | 30 s | IDLE only |
| `parked_actor_timeout` | 180 s | |
| `evidence_frames_per_track` / `speculative_captures_per_track` | 3 / 2 | |

### 6.5 Latency budget (J2: < 8 s)

The person leaves → settle 1.0 s → dwell 5.0 s → the confirming frame arrives (≤ 0.33 s at 3 FPS) → audio starts (≤ 0.5 s). That totals **about 6.8 s**, leaving roughly 1.2 s of headroom. Alert delivery is measured separately: P90 under 15 s on Wi-Fi.

### 6.6 Plate-resolution sanity check

A plate is about 0.5 m wide. At 20 m with a typical 75° main camera, the horizontal field of view is about 30 m:

- A 1280-px analysis frame puts about **21 px** on the plate, which is too small for OCR.
- A 4000-px ImageCapture puts about **65 px** on it, which is marginal.
- With 2× zoom, about **130 px**, which is usable.

Hence FR-5.2 (high-res speculative capture) and FR-1.7 (zoom) are what make H3 achievable.

## 7. Non-functional requirements

| Area | Requirement |
| --- | --- |
| Performance | Stage-1 detector ≤ 60 ms/frame on the reference device, the **Moto G54** (Dimensity 7020; see [moto-g54.md](../devices/moto-g54.md)). End-to-end pipeline ≥ 2 FPS sustained. |
| Thermal | 3 h continuous on charger: no thermal shutdown; `THERMAL_STATUS_SEVERE` for < 10% of runtime. |
| Memory | Steady-state heap < 300 MB; no growth trend over 3 h (bitmaps pooled, buffers recycled). |
| Reliability | Zero ANRs; the camera pipeline recovers from disconnects without operator action; queued alerts are never lost. |
| Storage | ≤ 5 MB per incident; evidence purge per FR-6.5; warn when free space < 1 GB. |
| Security | Bot token encrypted at rest (Android Keystore-backed AEAD via Tink); never logged; no secrets in the APK or git. `allowBackup=false`. HTTPS only. |
| Privacy | See §9. No face recognition, no continuous recording, data minimisation. |
| Localisation | UI in English; audio in English + Kannada. Strings externalised for Kannada/Hindi UI later. |
| Accessibility | Setup flow touch targets ≥ 48 dp; contrast meets WCAG AA; works one-handed. |
| Observability | On-device rolling log (Timber file tree, capped at 10 MB); a debug overlay showing boxes, track IDs, state, FPS, thermal status and accelerator. |
| Compatibility | minSdk 29 (Android 10), so repurposed older phones work; targetSdk 36; compileSdk 37.2 (required by current AndroidX). |

## 8. Data model (summary)

Full schema lives in code (`:core:data`); this table states the intent.

| Entity | Key fields |
| --- | --- |
| `SiteConfig` (DataStore) | name, lat/lng (one-time), ROI polygon (normalised), sensitivity, zoom, quiet hours, audio prefs, Telegram chat ID; token stored separately (encrypted) |
| `Incident` (Room) | id, siteName, actorEnteredAt, objectLeftAt, confirmedAt, kind (`CONFIRMED_DUMPING`, `UNATTRIBUTED_OBJECT`, `INCONCLUSIVE`), actorClasses, objectBox, plateText, plateRaw, plateConfidence, deliveryState, reviewLabel (`NONE`, `CONFIRMED`, `FALSE_ALARM`), reviewedAt, pipelineVersion, detectorModelVersion, fpsAtEvent, thermalAtEvent |
| `EvidenceFile` (Room) | id, incidentId, kind (`SCENE`, `ACTOR`, `PLATE_CROP`, `HIGH_RES`), relativePath, width, height, capturedAt, sharpness |
| `DeliveryAttempt` (Room) | incidentId, sink (`TELEGRAM`, `WEBHOOK`), attempt, status, httpCode, at |

Evidence files go in app-private storage (`filesDir/evidence/<incidentId>/`). Nothing is written to shared media.

## 9. Privacy, legal and permissions

> Not legal advice. Have a lawyer review this before any deployment beyond a controlled test.

- **Personal data.** Under the Digital Personal Data Protection Act, 2023, images of people and vehicle registration numbers can be personal data. The MVP minimises it: only incident frames are kept, there is no continuous recording, processing is on-device, sharing is limited to a closed Telegram group, and retention is limited (FR-6.5).
- **No facial recognition**, ever, in this product line without a fresh legal review.
- **Notice.** Put up a physical "CCTV surveillance in operation" sign at the site. It also deters.
- **Noise.** Quiet hours (FR-4.4) respect residential night limits.
- **Group hygiene.** Only RWA-authorised members in the alert group. The bot token is rotatable from Settings.
- **Android permissions (final list):** `CAMERA`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION` (one-time at setup, foreground only), `BLUETOOTH_CONNECT` (to show the route name on API 31+), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (prototype sideload only; review Play policy before any store release). **Not requested:** background location, storage, microphone.

## 10. Tech stack (versions validated 2026-11-21)

| Layer | Choice | Version | Notes |
| --- | --- | --- | --- |
| Language / UI | Kotlin + Jetpack Compose (Material 3) | Kotlin 2.4.21, Compose BOM 2026.11.01 | AGP 9 built-in Kotlin |
| Build | Gradle + AGP, convention plugins, version catalog | Gradle 9.9.2, AGP 9.5.2 | `build-logic/` |
| Camera | CameraX `ImageAnalysis` + `ImageCapture` + `camera-compose` | 1.7.0 | |
| ML runtime | LiteRT CompiledModel API, bundled `litert` + `litert-gpu` (GPU → CPU fallback) | 2.4.0 | ADR-0004; not the legacy `Interpreter` |
| Detector | Ultralytics YOLO11n INT8 (stock COCO, then fine-tuned); YOLO26n candidate | — | ADR-0003; behind an `ObjectDetector` port |
| Plate OCR | ML Kit Text Recognition v2, bundled Latin | 16.0.1 | fast-plate-ocr is the A/B candidate |
| Persistence | Room + DataStore + app-private files | Room 2.8.5, DataStore 1.3.0 | |
| Background work | WorkManager (+ Hilt workers) | 2.12.0 | Alert delivery, retention purge |
| DI | Hilt | 2.60.1 | KSP 2.3.14 |
| Network | OkHttp (direct Telegram Bot API calls) | 5.5.1 | No Retrofit needed for 3 endpoints |
| Security | Tink (Keystore-backed AEAD) | 1.24.0 | `security-crypto` is deprecated |
| Quality | Spotless + ktlint (+ Compose rules), detekt, Android Lint | 8.12.0 / 1.8.0 / 1.23.8 | |
| Tests | JUnit Jupiter 6, Truth, Turbine, MockK, MockWebServer; AndroidX Test | — | |
| Training (offline) | Ultralytics (AGPL-3.0) in `ml/` | — | Training configs are part of the Corresponding Source |

## 11. User journeys and acceptance criteria

Each journey is a field-test script. Record results in `docs/field-tests/` (template there).

### J1. Site setup and calibration (operator)

Mount the phone → open the app → grant permissions → draw the ROI over the dumping patch → pair the speaker → **Test Siren** → enter Telegram details → **Send test alert** → **Start Monitoring**.

- ✅ Done in **< 3 min** by a first-time operator who has the Telegram details ready.
- ✅ The ROI and settings survive a force-stop and restart.
- ✅ The test alert arrives in the group.

### J2. True positive (on foot, two-wheeler, car)

A volunteer enters the ROI, drops a garbage bag, and leaves.

- ✅ **≥ 80% detection** across ≥ 20 trials (mixed foot / scooter / car) in daylight or good light.
- ✅ The siren starts **< 8 s** after the actor leaves the ROI (log timestamps).
- ✅ The Telegram alert has the scene, the actor/vehicle frame, the time, and a plate (or "unreadable").
- ✅ For vehicles, the plate is **human-readable in ≥ 70%** of incidents, and the OCR exact match is **≥ 50%** of those (H3).

### J3. False-positive rejection

During a 30-minute window: volunteers walk through carrying bags without dropping them, vehicles pass and stop briefly, a dog or cow wanders through, clouds change the light, someone leaves and comes back for their bag.

- ✅ **Zero** sirens and **zero** Telegram dumping alerts.
- ✅ Debug overlay boxes are allowed; `UNATTRIBUTED_OBJECT` / `INCONCLUSIVE` entries are allowed and reviewed.

### J4. Evidence review and feedback

The operator reviews incidents in the app (and in Telegram, P1) and marks them Confirm or False alarm.

- ✅ The review label persists and appears in the export.
- ✅ The export zip opens on a laptop with images and labels intact.

### J5. Thermal and endurance

Run continuously for **3 hours** on a charger, indoors.

- ✅ No crash, ANR or OS kill. No thermal shutdown.
- ✅ Average FPS **≥ 2**. Thermal transitions are logged and the operator is alerted on SEVERE.
- ✅ No upward memory trend (compare heap at start and end).

### J6. Offline resilience (new)

Turn Wi-Fi off, trigger 2 incidents, turn Wi-Fi back on.

- ✅ Both alerts are delivered within 2 min of reconnecting, in order, with no duplicates.

### J7. Recovery (new)

Kill the app process during monitoring, and separately reboot the phone.

- ✅ The operator gets a resume prompt (notification + Telegram operational alert). Monitoring resumes with the same ROI after one tap.

## 12. Success metrics (MVP exit criteria)

The MVP passes when J1–J7 all pass on the **Moto G54** reference device (a second, older phone is recommended but optional) at **one** real site, plus a 7-day pilot at that site with:

- ≥ 1 confirmed true incident, or a documented zero-dumping week;
- a false-alarm rate (operator-labelled) under 1 per day;
- the operator still willing to keep it running (qualitative).

## 13. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| **Dumping happens at night/early morning, outside "lit" conditions** | High | High | Make site lighting a deployment prerequisite; log low-light frames; evaluate CameraX low-light / Night Mode options in Phase 4; outdoor IR is a post-MVP hardware decision |
| Plates too small or blurry at distance | High | Medium | High-res speculative capture, zoom, best-of-K frames; human-readable photo is the fallback |
| Baseline diff tricked by shadows, rain, swaying plants | Medium | High | Gradient-based diff, illumination abort, ROI drawn to exclude foliage, sensitivity presets, field tuning |
| OEM background killing (Xiaomi, Oppo, Vivo…) | High | High | Screen-on kiosk, foreground service, battery-optimisation exemption, recovery prompt (FR-8.5) |
| Thermal throttling in Bangalore summer (35 °C+ ambient on a balcony) | Medium | High | Motion gating, FPS policy, GPU/NPU delegate, no direct sun on the phone, a cheap phone fan |
| Linking proprietary ML Kit into an AGPL-3.0 app | Low | Medium | Section 7 additional permission (`LICENSE-EXCEPTIONS.md`); fast-plate-ocr path makes ML Kit optional (P6.6) |
| Bystander privacy complaint | Low | High | §9 controls, signage, retention, no faces |

## 14. Open decisions

| # | Question | Decision / default |
| --- | --- | --- |
| D1 | Licensing of the app | ✅ **Decided 2026-11-21: open source, AGPL-3.0** (ADR-0003), with an ML Kit linking exception (draft) |
| D2 | Pre-warning clip while the actor is present (FR-3.5) | ✅ **Decided 2026-11-21: off for the false-alarm test (J3).** It may be A/B-tested in the pilot later |
| D3 | Reference test device | ✅ **Decided 2026-11-21: Moto G54 5G** ([profile](../devices/moto-g54.md)) |
| D4 | Custom vehicle classes (auto-rickshaw, goods carrier) in v1 or v2? | v2; map to COCO classes in v1 |
| D5 | Telegram only, or Supabase from day one? | Telegram only (P0); webhook P2 |
| D6 | Final applicationId / package (currently `app.litterguard`) | Change before the first external release |

## 15. Changes from v1.0

1. **Abandoned-object detection is class-agnostic** (baseline diff), not gated on a `litter` class (§6.1, ADR-0002).
2. **Evidence is buffered before the event**, because the v1.0 post-trigger capture could not contain the plate (§5.5).
3. The **Bharat (BH) series** plate pattern, two-line plates, and position-aware OCR confusion correction were added (FR-5.5/5.6).
4. **Animals are tracked and excluded as actors**, and new objects without an actor are logged, not alerted (FR-3.2).
5. **One-time site GPS** instead of per-incident location, which avoids background-location permission.
6. **Quiet hours** for the siren (noise rules).
7. **Thermal policy** extended with `getThermalHeadroom` and a CRITICAL tier.
8. New journeys **J6 (offline)** and **J7 (recovery)**.
9. **Privacy section** (DPDP Act), a retention policy, and an explicit permission list.
10. The **detector license** is flagged as a decision (Ultralytics weights are AGPL-3.0).
11. Every dependency version is pinned and validated; LiteRT **CompiledModel** API instead of the legacy Interpreter.
12. Decisions D1–D3 recorded: AGPL-3.0 open source, pre-warning off for J3, Moto G54 as the reference device. Model weights are downloaded at build time, not committed (ADR-0004).

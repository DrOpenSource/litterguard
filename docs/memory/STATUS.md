# Project Status

> T1 working memory. Overwrite it to stay current; keep it under ~80 lines. See [MEMORY_ARCHITECTURE.md](../agent/MEMORY_ARCHITECTURE.md).

## Now
Phase: **2 (walking skeleton), code complete, never yet run on a phone** · Branch: `feat/p2.1-p2.5-detector-camera` · Repo: [DrOpenSource/litterguard](https://github.com/DrOpenSource/litterguard) · Updated: 2026-11-26
- **Goal:** a demo for the owner's friend, who visits on **2026-11-27** (the friend also picks the site: OWNER_GUIDE Task D).
- **The owner is non-technical.** Tasks only via [OWNER_GUIDE.md](../owner/OWNER_GUIDE.md), one at a time (AGENTS.md §7).
- The decision core is merged (PR #6: P1.4–P1.7, P1.11, P1.13).
- This branch adds, after #6: removal detection, crop headroom, the scenery rule, the LiteRT detector, CameraX frames, Room/DataStore/evidence files, siren, Telegram, the foreground-service session and all screens (parts of P2.1–P2.13).
- **Local gate green** 2026-11-26: `spotlessCheck detekt test lintDebug assembleDebug -PwarningsAsErrors=true -PlitterguardModelsDir=ml/weights/litert`, 231 tests.
- **CI will fail on this branch** until the `models-v1` release exists (see below). No Phase 2 box is ticked: none has been verified on the device.

## Next (resume here)
1. Phone connected → run `DetectorGoldenTest` (`:core:ml:connectedDebugAndroidTest`, needs `-PlitterguardModelsDir`). If INT8 misses people, put `fp32`/GPU first in `models.json` and its test.
2. `:app:installDebug`, then drive the whole loop over adb: permissions, zone, siren, live boxes, a bag drop, the switch to another app while watching.
3. Owner: OWNER_GUIDE **Task E** (set up), **Task F** (rehearse and demo), then **Task D** at the site, including the new "can the app see a person from there" step.
4. After the demo: finish P2 gaps (see below), then P2.14.

## Blocked: needs the owner's approval
- Publishing the two stock model files as a GitHub **pre-release `models-v1`** (P2.2; URLs and sha256 are already in `core/ml/models.json`). The agent's permission check denied it on 2026-11-26; don't work around it.

## P2 gaps (known, not blocking the demo)
- P2.6 per-stage latency metrics (only detector ms and confirm delay are logged). P2.7 `delivery_attempt` table and DAO tests (adding the table needs a migration). P2.10 onboarding and one-time location. P2.11 overlay has no track IDs or accelerator line beyond the model description.
- P1.8, P1.10 (shared fakes), P1.12 (JSONL format), P1.9, P1.14 still open.

## Local dev-machine state (not in git)
- `ml/weights/litert/`: the two model files for `-PlitterguardModelsDir` (sha256 matches `models.json`).
- `core/ml/src/androidTest/assets/bench/`: benchmark models plus `bus.jpg` for the golden test.
- `ml/.venv`: Python 3.12 with Ultralytics 8.4.216. It can't export LiteRT on Windows; use `ml-export-bench`.
- The G54 is authorised for adb on this PC ("Always allow").

## Open questions (need a human)
- A parked vehicle **inside** the zone keeps "someone is in the zone" forever, so dumping next to it is never judged (PRD only stops captures after `parked_actor_timeout`). For now: draw the zone clear of parking.
- An object thrown into the zone by someone who never steps in is not caught (only animal-only visits become `UNATTRIBUTED_OBJECT`).
- Licensing: `litert-api` pulls the proprietary `com.google.android.play:ai-delivery` → `asset-delivery`, which LICENSE-EXCEPTIONS does not cover. Settle before any public APK release.
- D4: custom vehicle classes in v1 or v2? Default: v2. · D5: Telegram only? Default: yes. · D6: final applicationId.

## Assumptions made (revisit)
- Screens draw on analysis frames, not a viewfinder (ADR-0005, Proposed). The app is landscape-only, and the camera follows landscape rotations only.
- Added beyond the old PRD, with tunables in §6.4: occlusion waiting, removal detection, `roi_crop_headroom` 0.40, `scenery_after` 10 s.
- One suppressed lint: `disableAutoFallbackToCpu()` is marked tests-only by LiteRT but ADR-0004 needs strict GPU.
- Package root `app.litterguard`, minSdk 29. INT8 accuracy is unproven (calibrated on coco8).
- WorkManager adds `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED`; the latter isn't in PRD §9's list yet.

## Needs device verification
- **Everything in Phase 2.** Especially: model load and output decoding, INT8 vs FP32 accuracy, camera frames upright and aligned with the zone, switching apps while watching, siren loudness and routing, Keystore token storage, Telegram delivery, latency from leaving to siren (< 8 s).
- Removal and shadow handling on real ground (synthetic scenes only).
- `getThermalHeadroom()` and the 30-minute camera soak (P2.4/P2.6); BOOT_COMPLETED and Motorola battery behaviour (P4.6/P4.7).

## Recent sessions
- 2026-11-26 — Decision core merged (#6); Phase 2 skeleton written and gate green locally; review and scenarios found and fixed five demo-breaking bugs; owner guide Tasks E and F added.
- 2026-11-22 — Owner finished Tasks B (bot) and C (audio); Task D requested.
- 2026-11-21 — G54 baseline and benchmark (PR #2); GitHub + CI; P1.1–P1.3 (PR #1); decisions D1–D3.

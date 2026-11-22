# Project Status

> T1 working memory. Overwrite it to stay current; keep it under ~80 lines. See [MEMORY_ARCHITECTURE.md](../agent/MEMORY_ARCHITECTURE.md).

## Now
Phase: **0 (10/12) and 1 (3/15) in parallel** · Repo: [DrOpenSource/litterguard](https://github.com/DrOpenSource/litterguard) · Updated: 2026-11-22
- `main` is pushed and protected: PR plus a green `gate` check required, linear history, no force-push. CI takes ~4 min.
- **The owner is non-technical.** Give them tasks only via [OWNER_GUIDE.md](../owner/OWNER_GUIDE.md), one at a time (AGENTS.md §7).
- **Task A is done.** The G54 is connected over USB to the dev PC, and adb works.
- **Task B is done (P0.10).** Bot "LitterGuard" = @LitterGuardBot, group "LitterGuard Test". The owner holds the token; never ask for it. P2.10 must detect the group from a fresh action at setup (LEARNINGS `[telegram]`).
- **Task C is done (P0.9). Audio is English only (D7).** The clips are in `core/alerts/src/main/res/raw/` (`warning_en`, `notice_en`, `test_tone`), built by `ml/scripts/prepare_audio_clips.py`. The raw recordings are in git-ignored `field-data/audio/raw/`. The owner agreed to publish their voice.
- **P0.8 is done.** Findings: [moto-g54.md](../devices/moto-g54.md) and the ADR-0003 addendum.
  - Android 15; apps get at most 12.6 MP (no 50 MP); HFOV ≈ 61°.
  - The thermal HAL jumps NONE → SEVERE, so FR-8.4 now uses headroom tiers.
  - Detector: **416 px, CPU INT8 with 4 threads (p50 32 ms)**; GPU FP32 is the alternate (58 ms, fully resident); the NPU is unusable.
- Benchmark tooling: `DetectorBenchmarkTest` (`:core:ml` androidTest) runs on models from the `ml-export-bench` workflow artifact. Ultralytics LiteRT export does not run on Windows.
- Done in Phase 1: P1.1–P1.3 (61 tests).
- Decided: D1 = AGPL-3.0, D2 = pre-warning off for J3, D3 = Moto G54. ADR-0003/0004 Accepted.
- Known lint warnings (expected): `OldTargetApi` (targetSdk 36 is deliberate), `MissingApplicationIcon` (placeholder). `UnusedResources` ×3 for the audio clips until P2.8 plays them.

## Next (resume here)
1. Owner: **Task D** (site visit) was requested on 2026-11-22. When they're done, copy the photos to `field-data/site/` over adb (never into git) and record the answers in P0.11.
2. Agent track: P1.4 MotionGate, P1.5 Tracker, P1.10 Ports + fakes (parallel; none started yet, no open branches), then P1.6 BaselineModel. One PR per slice; CI `gate` must pass before squash-merging.
3. P2.2 can now proceed: the input size is decided (416).

## Local dev-machine state (not in git)
- `ml/.venv`: Python 3.12 with Ultralytics 8.4.216 + torch CPU, plus `imageio-ffmpeg` (ffmpeg for the audio script). It can't export LiteRT on Windows; use the `ml-export-bench` workflow.
- `core/ml/src/androidTest/assets/bench/`: 6 stock YOLO11n benchmark models (git-ignored). The CI artifact expires 7 days after 2026-11-21; re-run the workflow to get them again.
- The G54 is authorised for adb on this PC ("Always allow").

## Open questions (need a human)
- D4: custom vehicle classes (auto-rickshaw, goods carrier) in v1 or v2? Default: v2 (P6.2).
- D5: Telegram only, or Supabase as well? Default: Telegram only.
- D6: final applicationId (currently `app.litterguard`)? Change it before the first public release.

## Assumptions made (revisit)
- Debug APK is ~118 MB (LiteRT + ML Kit native libs for all ABIs). P2.1 restricts `abiFilters` to `arm64-v8a`.
- Package/namespace root is `app.litterguard`.
- minSdk 29, so repurposed older phones still work (ADR-0001).
- Thermal headroom cut-offs 0.80 / 1.0 (FR-8.4) are first guesses; tune them from the P2.6 soak.
- INT8 accuracy is assumed acceptable until P6; if it isn't, switch to GPU FP32 (ADR-0003 addendum).

## Needs device verification
- How the 11 s warning clip sounds through the G54 speaker and a BT speaker at full volume (P2.8 Test Siren / Task E).
- `getThermalHeadroom()` value and trend from inside the app (P2.6); 30-minute camera soak (P2.4).
- Camera foreground service blocked from BOOT_COMPLETED on Android 15 (P4.6).
- Motorola battery-optimisation path and kiosk survival (P4.7).

## Recent sessions
- 2026-11-22 — Owner finished Task B (Telegram bot, P0.10) and Task C (English-only audio, P0.9, D7); Task D requested.
- 2026-11-21 — Owner connected the G54; P0.8 baseline + detector benchmark done (PR #2); FR-8.4 thermal tiers revised.
- 2026-11-21 — Pushed to GitHub, CI + branch protection; owner guide; P1.1–P1.3 implemented (PR #1).
- 2026-11-21 — Decisions D1–D3 applied; AGPL LICENSE + exception draft; ADR-0003/0004; skills vendored; G54 profile; implementation plan written.

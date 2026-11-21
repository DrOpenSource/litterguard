# Project Status

> T1 working memory. Overwrite it to stay current; keep it under ~80 lines. See [MEMORY_ARCHITECTURE.md](../agent/MEMORY_ARCHITECTURE.md).

## Now
Phase: **0 (8/12) and 1 (3/15) in parallel** · Repo: [DrOpenSource/litterguard](https://github.com/DrOpenSource/litterguard) · Updated: 2026-11-21
- `main` is pushed and protected: PR plus a green `gate` check required, linear history, no force-push. CI takes ~4 min.
- **The owner is non-technical.** Give them tasks only via [OWNER_GUIDE.md](../owner/OWNER_GUIDE.md), one at a time (AGENTS.md §7).
- **Task A is done.** The G54 is connected over USB to the dev PC, and adb works.
- **P0.8 is done.** Findings: [moto-g54.md](../devices/moto-g54.md) and the ADR-0003 addendum.
  - Android 15; apps get at most 12.6 MP (no 50 MP); HFOV ≈ 61°.
  - The thermal HAL jumps NONE → SEVERE, so FR-8.4 now uses headroom tiers.
  - Detector: **416 px, CPU INT8 with 4 threads (p50 32 ms)**; GPU FP32 is the alternate (58 ms, fully resident); the NPU is unusable.
- Benchmark tooling: `DetectorBenchmarkTest` (`:core:ml` androidTest) runs on models from the `ml-export-bench` workflow artifact. Ultralytics LiteRT export does not run on Windows.
- Done in Phase 1: P1.1–P1.3 (61 tests).
- Decided: D1 = AGPL-3.0, D2 = pre-warning off for J3, D3 = Moto G54. ADR-0003/0004 Accepted.
- Known lint warnings (expected): `OldTargetApi` (targetSdk 36 is deliberate), `MissingApplicationIcon` (placeholder).

## Next
1. Owner: **Task B** (Telegram bot) next, then Task C (voice) and Task D (site visit). Ask for one at a time.
2. Agent track: P1.4 MotionGate, P1.5 Tracker, P1.10 Ports + fakes (parallel), then P1.6 BaselineModel.
3. P2.2 can now proceed: the input size is decided (416).

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
- `getThermalHeadroom()` value and trend from inside the app (P2.6); 30-minute camera soak (P2.4).
- Camera foreground service blocked from BOOT_COMPLETED on Android 15 (P4.6).
- Motorola battery-optimisation path and kiosk survival (P4.7).

## Recent sessions
- 2026-11-21 — Owner connected the G54; P0.8 baseline + detector benchmark done (PR #2); FR-8.4 thermal tiers revised.
- 2026-11-21 — Pushed to GitHub, CI + branch protection; owner guide; P1.1–P1.3 implemented (PR #1).
- 2026-11-21 — Decisions D1–D3 applied; AGPL LICENSE + exception draft; ADR-0003/0004; skills vendored; G54 profile; implementation plan written.

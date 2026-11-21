# Project Status

> T1 working memory. Overwrite it to stay current; keep it under ~80 lines. See [MEMORY_ARCHITECTURE.md](../agent/MEMORY_ARCHITECTURE.md).

## Now
Phase: **0 (7/12) and 1 (3/15) in parallel** · Repo: [DrOpenSource/litterguard](https://github.com/DrOpenSource/litterguard) · Updated: 2026-11-21
- `main` is pushed and protected: PR plus a green `gate` check required, linear history, no force-push. CI takes ~4 min.
- **The owner is non-technical.** Give them tasks only via [OWNER_GUIDE.md](../owner/OWNER_GUIDE.md), one at a time (AGENTS.md §7).
- Done in Phase 1: P1.1 value types, P1.2 `DetectionConfig` + presets, P1.3 ROI geometry, validation and frame mapping (61 tests).
- Decided: D1 = AGPL-3.0, D2 = pre-warning off for J3, D3 = Moto G54. ADR-0003/0004 Accepted.
- Known lint warnings (expected): `OldTargetApi` (targetSdk 36 is deliberate), `MissingApplicationIcon` (placeholder).

## Next
1. Owner: **Task A** (connect the G54) is requested first; then Task B (Telegram bot), Task C (voice), Task D (site visit).
2. Once Task A is done, the agent runs P0.8 over adb and copies any recordings and photos into `field-data/`.
3. Agent track: P1.4 MotionGate, P1.5 Tracker, P1.10 Ports + fakes (parallel), then P1.6 BaselineModel.

## Open questions (need a human)
- D4: custom vehicle classes (auto-rickshaw, goods carrier) in v1 or v2? Default: v2 (P6.2).
- D5: Telegram only, or Supabase as well? Default: Telegram only.
- D6: final applicationId (currently `app.litterguard`)? Change it before the first public release.

## Assumptions made (revisit)
- Debug APK is ~118 MB (LiteRT + ML Kit native libs for all ABIs). P2.1 restricts `abiFilters` to `arm64-v8a`.
- Package/namespace root is `app.litterguard`.
- minSdk 29, so repurposed older phones still work (ADR-0001).

## Needs device verification
- Whether LiteRT's GPU path works on the G54's IMG BXM-8-256 GPU, or CPU INT8 is faster (P0.8).
- Whether CameraX exposes a 50 MP capture mode on the G54 (P0.8, P3.2); plate OCR range depends on it.
- `getThermalHeadroom()` on MediaTek: a number or NaN (P0.8).
- Camera foreground service blocked from BOOT_COMPLETED on the G54's Android version (P4.6).

## Recent sessions
- 2026-11-21 — Pushed to GitHub, CI + branch protection; owner guide; P1.1–P1.3 implemented (PR #1).
- 2026-11-21 — Decisions D1–D3 applied; AGPL LICENSE + exception draft; ADR-0003/0004; skills vendored; G54 profile; implementation plan written.
- 2026-11-21 — Repo scaffolded; PRD v1.1 + architecture + ADRs + agent guides written; external repos validated.

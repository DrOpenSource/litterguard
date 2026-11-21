# Project Status

> T1 working memory. Overwrite it to stay current; keep it under ~80 lines. See [MEMORY_ARCHITECTURE.md](../agent/MEMORY_ARCHITECTURE.md).

## Now
Phase: **0 — Foundation** (4/12 tasks) · Branch: `main` (not pushed yet) · Updated: 2026-11-21
- Plan: [IMPLEMENTATION_PLAN.md](../plan/IMPLEMENTATION_PLAN.md), with 91 tasks over 8 phases. P0.1–P0.4 are done.
- Decided: D1 = open source, AGPL-3.0 (ADR-0003 Accepted); D2 = pre-warning off for J3; D3 = Moto G54 reference device.
- ADR-0004 Accepted: bundled LiteRT (`litert` + `litert-gpu`), model confined to its own thread, models downloaded at build time (no LFS).
- Official skills vendored in `.claude/skills/` (CameraX, LiteRT runtime, CompiledModel scaffolding, on-device verification).
- Full gate green 2026-11-21: `spotlessCheck detekt test lintDebug assembleDebug -PwarningsAsErrors=true`.
- Known lint warnings (expected): `OldTargetApi` (targetSdk 36 is deliberate), `MissingApplicationIcon` (placeholder).

## Next
1. Human track: P0.5 (license exception review), P0.6 (GitHub repo), P0.8 (G54 baseline spike), P0.9 (audio clips), P0.10 (Telegram bot), P0.11 (site recon).
2. Agent track: start **Phase 1** (pure JVM; needs no device): P1.1 → P1.2/P1.3/P1.5/P1.10 in parallel.
3. Once P0.6 is done: P0.7 CI.

## Open questions (need a human)
- D4: custom vehicle classes (auto-rickshaw, goods carrier) in v1 or v2? Default: v2 (P6.2).
- D5: Telegram only, or Supabase as well? Default: Telegram only.
- D6: final applicationId (currently `app.litterguard`)? Change it before the first public release.
- LICENSE-EXCEPTIONS.md wording and copyright line (P0.5).

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
- 2026-11-21 — Decisions D1–D3 applied; AGPL LICENSE + exception draft; ADR-0003/0004; skills vendored; G54 profile; implementation plan written.
- 2026-11-21 — Repo scaffolded; PRD v1.1 + architecture + ADRs + agent guides written; external repos validated.

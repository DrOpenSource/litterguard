# AGENTS.md — Operating manual for coding agents

LitterGuard is a native Android (Kotlin + Compose) app. One phone watches a dumping spot, detects illegal waste dumping on-device, plays an audio deterrent, captures evidence (photo + licence plate), and alerts a Telegram group. Read this file fully before every task. It overrides habits and defaults.

| Need | Read |
| --- | --- |
| What to build and why | [docs/product/PRD.md](docs/product/PRD.md) (requirement IDs `FR-x.y`, journeys `J1–J7`) |
| How the code is structured | [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) |
| How to write code here | [docs/agent/BEST_PRACTICES.md](docs/agent/BEST_PRACTICES.md) |
| Where project memory lives, and when to update it | [docs/agent/MEMORY_ARCHITECTURE.md](docs/agent/MEMORY_ARCHITECTURE.md) |
| Why things are the way they are | [docs/decisions/](docs/decisions/) (ADRs) |
| Which external repos and models to trust | [docs/research/REPO_VALIDATION.md](docs/research/REPO_VALIDATION.md) |
| **What to work on next** | [docs/plan/IMPLEMENTATION_PLAN.md](docs/plan/IMPLEMENTATION_PLAN.md) (task IDs `P<phase>.<n>`) |
| The test device | [docs/devices/moto-g54.md](docs/devices/moto-g54.md) |
| What the (non-technical) owner does, step by step | [docs/owner/OWNER_GUIDE.md](docs/owner/OWNER_GUIDE.md) |

## 1. Session protocol (mandatory)

**Start of session**
1. Read [docs/memory/STATUS.md](docs/memory/STATUS.md): current phase, work in flight, next steps, open questions. Then find your task in [IMPLEMENTATION_PLAN.md](docs/plan/IMPLEMENTATION_PLAN.md) and check its dependencies are done.
2. Skim [docs/memory/LEARNINGS.md](docs/memory/LEARNINGS.md) for entries tagged with the area you are about to touch.
3. Read the ADRs and PRD sections that cover your task. If the task contradicts an *Accepted* ADR, stop and say so; don't work around it.

**End of session** (or before handing back to the user)
1. Tick the finished task checkboxes in `IMPLEMENTATION_PLAN.md`; a box is ticked only when its "done when" criterion was verified. Update `STATUS.md`: what changed, what is next, new open questions. Keep it short; it is a snapshot, not a diary.
2. Add a `LEARNINGS.md` entry for any non-obvious fact you discovered: device quirks, library bugs, failed approaches.
3. If you made an architectural choice, write an ADR from [ADR-0000-template.md](docs/decisions/ADR-0000-template.md).

## 2. Commands

Run from the repo root. Use `./gradlew` in bash, or `.\gradlew.bat` in PowerShell. JDK 17 is required.

| Task | Command |
| --- | --- |
| Build debug APK | `./gradlew assembleDebug` |
| All JVM unit tests | `./gradlew test` |
| One module's tests | `./gradlew :core:domain:test` |
| Format (auto-fix) | `./gradlew spotlessApply` |
| Format check | `./gradlew spotlessCheck` |
| Static analysis | `./gradlew detekt` |
| Android Lint | `./gradlew lintDebug` |
| **Full local gate (run before saying "done")** | `./gradlew spotlessCheck detekt test lintDebug assembleDebug` |
| Strict compile (CI mode) | add `-PwarningsAsErrors=true` |
| Install on device | `./gradlew :app:installDebug` |
| Instrumented tests (device) | `./gradlew connectedDebugAndroidTest` |

## 3. Repository map

```
app/                 Application shell: navigation, Hilt root. No business logic.
feature/{setup,monitor,incidents}/   Compose screens + ViewModels
core/model/          (JVM) value types
core/domain/         (JVM) ALL decision logic + ports (interfaces). The heart of the product.
core/{camera,ml,data,alerts,monitoring,ui}/   Android adapters implementing domain ports
build-logic/         Gradle convention plugins (litterguard.*)
gradle/libs.versions.toml   The only place versions are declared
config/detekt/       Static-analysis rules
ml/                  Python training/export workspace (not part of the Android build)
docs/                PRD, architecture, ADRs, agent guides, memory, field tests
```

## 4. Hard rules (never break these)

1. **No business logic in Android modules.** If it decides something (thresholds, state transitions, geometry, plate validation), it goes in `:core:domain` with unit tests. Android modules translate and execute; they don't decide.
2. **Respect the dependency direction:** `model ← domain ← core infra ← feature ← app`. Features never depend on each other or on infra modules. Infra modules never depend on each other.
3. **No version strings in module build files.** Add libraries to `gradle/libs.versions.toml`, and justify every new dependency in your summary (why, license, size). Prefer the platform or an existing dependency.
4. **No magic numbers for tunables.** Detection thresholds live in `DetectionConfig` (domain). If you change a default, also update PRD §6.4.
5. **Never commit secrets or personal data.** That covers bot tokens, keystores, `local.properties`, and field-test images or videos (plates and people are personal data). `.gitignore` covers the usual paths; check `git status` before committing.
6. **No facial recognition, face matching, or person re-identification**, in any form. This is a product and legal constraint (PRD §3.2, §9).
7. **Room schema changes ship with a migration and a migration test.** Exported schemas in `core/data/schemas/` are committed.
8. **Models:** `.tflite` files are **never committed**. They are downloaded at build time and sha256-verified against `core/ml/models.json` (source, license, input and output shapes), each with a golden shape test (ADR-0004). Never ship weights whose license is unknown or AGPL-incompatible.
9. **Close what you open:** every `ImageProxy`, model instance, wake lock, and `MediaPlayer` must be released on every path. Leaks show up as thermal or ANR failures hours later (J5).
10. **Don't weaken a test to make it pass.** If a test is wrong, say why in the commit message.
11. **Don't commit or push unless the user asks.** Never force-push `main`.
12. **License hygiene:** the project is AGPL-3.0. New dependencies must be AGPL-compatible; proprietary Google ML Kit / Play services are allowed only under `LICENSE-EXCEPTIONS.md`. Code adapted from elsewhere must be credited in `THIRD_PARTY_NOTICES.md`.

## 5. Definition of done

A task is done only when all of these hold:
- [ ] The full local gate passes (`spotlessCheck detekt test lintDebug assembleDebug`). Report the actual result; never assume it.
- [ ] New domain logic has unit tests, including edge cases. State-machine changes get a transition-table test, plus a scenario-replay fixture when possible.
- [ ] The PRD requirement ID(s) you implemented appear in the commit message or PR, e.g. `feat(domain): dwell timer for abandoned objects (FR-3.1)`.
- [ ] Docs are updated if behaviour, parameters, or architecture changed.
- [ ] `docs/memory/STATUS.md` is updated.
- [ ] If you could not verify something on a device (camera, thermal, audio routing), you say so explicitly and list it under "Needs device verification" in STATUS.md.

## 6. Working style

- **Small vertical slices.** Each change should be reviewable in about 10 minutes. Split refactors from behaviour changes.
- **Read before writing.** Check how similar code is already done in the repo and follow that pattern.
- **Run it.** Prefer running the build and tests over reasoning about whether they would pass.
- **Ask when the PRD is ambiguous** and the choice is user-visible or hard to reverse. Otherwise pick the conservative option, then record it in STATUS.md under "Assumptions made".
- **Platform guidance:** before touching CameraX or LiteRT code, read the vendored official skills in [.claude/skills/](.claude/skills/): `android-camerax`, `android-litert-runtime`, `litert-compiled-model-app-scaffolding`, `litert-on-device-verification`. Where they conflict with an Accepted ADR, the ADR wins (for example, ADR-0004 picks the bundled runtime, not Play services). For other topics (permissions, testing, ANRs, Compose performance), see [android/skills](https://github.com/android/skills). APIs move quickly; your training data may be stale.

## 7. Working with the project owner (mandatory)

The owner is **non-technical** and wants to be hand-held through development.

- **Never** ask the owner to run commands, benchmarks, or builds, read logs or code, or review PRs. Agents do all of the technical work: `adb` for the phone, `gh` for GitHub, Gradle for builds.
- When something physical is needed (tapping a phone setting, plugging in, recording audio, taking photos, a field test), point the owner to a task in [docs/owner/OWNER_GUIDE.md](docs/owner/OWNER_GUIDE.md). If no task fits yet, add one first, with numbered steps, the exact on-screen words, why it's needed, and what "done" looks like.
- Ask for **one task at a time**. Prefer screenshots to technical values.
- Explain any choice in one plain sentence and recommend a default. Don't hand the owner technical option lists.
- Never ask the owner to paste secrets (such as the Telegram bot token) into chat. They type secrets into the app on the phone.
- Field-test instructions (journeys J1–J7) are written as plain scripts in OWNER_GUIDE before the test is due.

## 8. Glossary

| Term | Meaning |
| --- | --- |
| **ROI / Dump Zone** | The operator-drawn polygon. Activity outside it is ignored. |
| **Actor** | A person or vehicle track inside the ROI (judged by the bottom-centre of its box). Animals are *not* actors. |
| **Baseline** | Grayscale snapshot of the empty ROI, frozen when an actor enters. |
| **Blob / candidate** | A region that differs from the baseline after actors leave. |
| **Dwell** | How long a candidate must stay static (default 5 s) before dumping is confirmed. |
| **Incident** | The persisted record of a confirmed (or unattributed/inconclusive) event, with its evidence. |
| **Evidence buffer** | The best frames and high-res captures gathered *while* an actor is present (ADR-0002). |
| **Effect** | An instruction emitted by the pure state machine (play siren, capture, commit, alert) and executed by adapters. |

# LitterGuard

On-device illegal-dumping detection for a single Android phone. It watches a "Dump Zone" you draw on the camera preview. When someone leaves waste behind, it plays a loud bilingual warning, captures evidence (scene, vehicle, licence plate), and alerts your RWA's Telegram group. Everything runs offline on the phone; there is no cloud video and no facial recognition.

> **Status:** Phase 0, foundation. The repo, build system and specification are in place; product code starts in Phase 1. See the [implementation plan](docs/plan/IMPLEMENTATION_PLAN.md) and [current status](docs/memory/STATUS.md).

## Quick start

Requirements: JDK 17, Android SDK platform 37.2 + build-tools 37 (`sdkmanager "platforms;android-37.2"`), and a device on Android 10 or later.

```bash
./gradlew assembleDebug          # build
./gradlew test                   # JVM unit tests
./gradlew spotlessApply          # format
./gradlew spotlessCheck detekt test lintDebug assembleDebug   # full local gate
./gradlew :app:installDebug      # install on a connected device
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

## Documentation

| Doc | For |
| --- | --- |
| [PRD](docs/product/PRD.md) | What we're building, requirements, acceptance journeys |
| [Architecture](docs/architecture/ARCHITECTURE.md) | Modules, ports, pipeline, testing strategy |
| [Decisions (ADRs)](docs/decisions/) | Why key choices were made |
| [Repo & model validation](docs/research/REPO_VALIDATION.md) | Which external repos, models and datasets to use, and their licenses |
| [AGENTS.md](AGENTS.md) | Operating manual for AI coding agents (and a good onboarding read for humans) |
| [Best practices](docs/agent/BEST_PRACTICES.md) | Coding standards |
| [Memory architecture](docs/agent/MEMORY_ARCHITECTURE.md) | How project knowledge is kept across sessions |

## License

© 2026 LitterGuard contributors. Licensed under [GNU AGPL-3.0](LICENSE), with an [additional permission](LICENSE-EXCEPTIONS.md) for linking Google ML Kit. Model weights trained with Ultralytics are also AGPL-3.0. See [ADR-0003](docs/decisions/ADR-0003-detector-model-and-license.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

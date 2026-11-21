# ADR-0001: Native Android, a modular Gradle build, and a pure-Kotlin domain

- **Status:** Accepted
- **Date:** 2026-11-21

## Context
The MVP has to run a camera → ML → decision → effects loop continuously on one phone, under thermal limits, and stay testable while AI agents and humans change it in parallel (PRD §2, §5.8).

## Decision
- **Kotlin + Jetpack Compose, native Android.** No cross-platform layer between us and CameraX, LiteRT, ML Kit, or the thermal and foreground-service APIs.
- **A multi-module Gradle build** with convention plugins (`build-logic/`) and a single version catalog (`gradle/libs.versions.toml`).
- **`:core:model` and `:core:domain` are pure Kotlin/JVM modules.** All dumping-decision logic lives there, behind ports (ARCHITECTURE.md §3).
- **SDK levels:** minSdk 29 (Android 10: thermal status API, scoped storage, and repurposed older phones still work); `getThermalHeadroom` is gated on API 30; targetSdk 36; compileSdk 37.2 (current AndroidX and OkHttp require it; compile and target SDK are independent).
- **Hilt** for DI, **Room + DataStore** for persistence, **WorkManager** for delivery.

## Consequences
- The decision pipeline is unit- and replay-testable on the JVM in seconds, without a device.
- Module boundaries limit the blast radius of an agent's change, and the build enforces the dependency direction.
- There is more upfront build configuration, absorbed by the convention plugins.
- A future iOS or edge port would reuse the domain logic only via Kotlin Multiplatform (not planned).

## Alternatives considered
- **Flutter + the Ultralytics YOLO plugin:** fastest demo, but an extra bridge for the camera/thermal/foreground-service work, and the plugin is AGPL-3.0.
- **Single-module app:** faster to start, but logic tends to grow tangled with Android code and becomes untestable.

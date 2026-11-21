@AGENTS.md

## Claude Code specifics

- Project memory that the whole team shares lives **in the repo** (`docs/memory/`), per [MEMORY_ARCHITECTURE.md](docs/agent/MEMORY_ARCHITECTURE.md). Your per-user auto-memory is for personal preferences only. Never put project facts there that teammates or other agents need.
- On Windows, the Bash tool runs Git Bash: use `./gradlew`. In the PowerShell tool use `.\gradlew.bat`.
- Long Gradle runs (a first build or `connectedDebugAndroidTest`) should run in the background.

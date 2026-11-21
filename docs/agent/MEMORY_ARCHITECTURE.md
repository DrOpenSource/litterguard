# Agent Memory Architecture

Coding agents start every session with no memory. Humans forget why things were done. This document defines **where project knowledge lives, who writes it, when, and how it is kept small enough to be read**.

Design goals:
1. **Shared and versioned.** Memory lives in git next to the code, so every agent, tool and teammate sees the same truth, and history shows how it changed.
2. **Tiered by volatility.** Rules rarely change; status changes every session. They live in different files with different update rules.
3. **Cheap to load.** An agent should get oriented by reading under about 300 lines.
4. **Evidence over opinion.** Learnings cite what was observed (device, version, log line), not hunches.

## 1. Tiers

| Tier | File(s) | Holds | Changes | Load rule |
| --- | --- | --- | --- | --- |
| **T0 Constitution** | `AGENTS.md` (+ `CLAUDE.md` import) | Hard rules, commands, definition of done, glossary | Rarely; by human decision | Auto-loaded every session |
| **T1 Working state** | `docs/memory/STATUS.md` | Current phase, work in flight, next steps, open questions, assumptions, items needing device verification | **Every session** (overwrite to stay current) | Read at session start |
| **T2 Decisions** | `docs/decisions/ADR-*.md` | Why the architecture is the way it is | Append-only; an Accepted ADR is changed only by a superseding ADR | Read the ADRs relevant to the task |
| **T3 Learnings** | `docs/memory/LEARNINGS.md` | Non-obvious facts: device quirks, library bugs, tuning results, failed approaches | Append, curated (see §4) | Grep by tag before touching an area |
| **T4 Specification** | `docs/product/PRD.md`, `docs/architecture/ARCHITECTURE.md` | What to build, and the target structure | When the spec changes | Read the sections relevant to the task |
| **T5 Evidence** | `docs/field-tests/*.md`, scenario fixtures in `core/domain/src/test/resources/scenarios/` | Measured outcomes of journeys J1–J7; replayable sequences | Append per test run | Read when tuning or debugging detection |
| **Code as memory** | Code, tests, KDoc, commit messages | The ground truth of behaviour | Every change | Always trust over prose when they disagree, then fix the prose |

### What is *not* project memory

- **Per-user tool memory** (e.g. Claude Code's `~/.claude/projects/.../memory/`, Cursor memories) is only for personal working preferences ("prefers terse summaries"). Never keep project facts there: other agents and teammates cannot see them.
- **Chat transcripts.** Anything that matters gets distilled into T1–T3 before the session ends.

## 2. Session read/write protocol

```
SESSION START                                SESSION END
─────────────                                ───────────
1. AGENTS.md (auto)                          1. Rewrite STATUS.md "Now" / "Next" / "Recent sessions"
2. STATUS.md  ─── what's in flight?          2. Append LEARNINGS.md for anything non-obvious
3. grep LEARNINGS.md for [area] tags         3. New ADR if an architectural choice was made
4. Relevant ADRs + PRD sections              4. Field-test results → docs/field-tests/
5. Then: read the code you will touch        5. Mention memory updates in your final summary
```

**Triggers to write memory mid-session (don't wait for the end):**
- You lost more than 15 minutes to something surprising → a LEARNINGS entry.
- You chose between two reasonable designs → an ADR, or at least a STATUS "Assumptions made" line.
- The user makes a product decision in chat → update the PRD (and close the item in §14 Open decisions).

## 3. File formats

### STATUS.md (T1): a snapshot, never more than about 80 lines

```markdown
## Now
Phase: <n — name>  ·  Branch: <branch>  ·  Updated: <YYYY-MM-DD>
- <what is in progress, with PRD IDs>

## Next
1. <ordered next steps>

## Open questions (need a human)
- <question> — context / options / default if no answer

## Assumptions made (revisit)
- <assumption> — why — where in code

## Needs device verification
- <behaviour> — how to verify

## Recent sessions (keep the last 5; delete older)
- <YYYY-MM-DD> — <one line: what changed>
```

### LEARNINGS.md (T3): one entry per fact

```markdown
### [camera][xiaomi] ImageAnalysis stops when screen turns off on MIUI 14
- **Observed:** 2026-12-03, Redmi Note 12, CameraX 1.7.0 — analyzer callbacks stop ~30 s after screen-off.
- **Evidence:** logcat `CameraStateMachine: CLOSED (ERROR_CAMERA_DISABLED)`.
- **Resolution:** kiosk keeps screen on (FR-8.2). Don't rely on screen-off capture.
- **Status:** active
```

Tags (lowercase, in brackets): `[camera] [ml] [litert] [ocr] [domain] [thermal] [fgs] [audio] [telegram] [room] [build] [device:<model>] [oem:<brand>] [field]`.

### ADRs (T2)

Use [ADR-0000-template.md](../decisions/ADR-0000-template.md). Number sequentially. Status is one of: Proposed → Accepted → Superseded/Deprecated.

## 4. Hygiene (keeping memory useful)

- **STATUS.md is overwritten, not appended.** History lives in git and the 5-line "Recent sessions" list.
- **LEARNINGS.md is curated.** When an entry is fixed upstream or no longer applies, set `Status: obsolete (<reason>)`. Prune obsolete entries during each phase-boundary review. Merge duplicates.
- **One fact, one place.** If something is a rule, it goes in AGENTS.md; a decision, an ADR; a parameter, PRD §6.4 plus `DetectionConfig`. Elsewhere, link; don't copy.
- **Code beats prose.** If memory and code disagree, verify against the code and the tests, then fix the memory in the same change.
- **No secrets and no personal data in memory files.** No tokens, no plate numbers from real incidents, no names of residents.
- **Phase boundary review** (at the end of each implementation phase): prune LEARNINGS, close resolved STATUS questions, check that the ADR statuses match reality, update the PRD changelog.

## 5. Conflict resolution order

When sources disagree: **user's latest explicit instruction → AGENTS.md hard rules → Accepted ADRs → PRD → ARCHITECTURE → STATUS/LEARNINGS → code comments.** If a higher-priority source looks wrong, raise it; don't silently follow the lower one.

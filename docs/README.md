# Docs index

```
docs/
├── product/PRD.md                    What & why — requirement IDs FR-x.y, journeys J1–J7
├── architecture/ARCHITECTURE.md      Modules, ports, pipeline, persistence, testing
├── decisions/                        ADRs (append-only; supersede, don't edit Accepted ones)
├── research/REPO_VALIDATION.md       External repos/models/datasets: verdicts + licenses
├── agent/
│   ├── BEST_PRACTICES.md             How to write code here
│   └── MEMORY_ARCHITECTURE.md        Where knowledge lives and when to update it
├── memory/
│   ├── STATUS.md                     T1: current state (overwrite each session)
│   └── LEARNINGS.md                  T3: non-obvious facts with evidence (curated)
├── plan/IMPLEMENTATION_PLAN.md       Phases, trackable tasks (P<phase>.<n>), exit criteria
├── devices/moto-g54.md               Reference device profile + baseline checklist
├── owner/OWNER_GUIDE.md              Plain step-by-step tasks for the (non-technical) owner
└── field-tests/TEMPLATE.md           Journey results (copy per test run)
```

The operating rules for agents are in [/AGENTS.md](../AGENTS.md), at the repo root so tools load it automatically.

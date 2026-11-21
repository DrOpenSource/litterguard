# Field Test — <Journey J#> — <YYYY-MM-DD>

> Copy to `docs/field-tests/<YYYY-MM-DD>-J<#>-<site>.md`. **No raw images or plate numbers in this file**; use incident IDs. Raw media stays in `field-data/` (git-ignored).

| Field | Value |
| --- | --- |
| Build | versionName / git SHA |
| Device | model · Android version · SoC |
| Site | name · distance to ROI (m) · mount height (m) |
| Conditions | time window · light (daylight / dusk / street-lit) · weather · ambient °C |
| Config | sensitivity preset · overrides · analysis_fps · zoom |
| Detector / OCR | model name + version from `models.json` |

## Trials

| # | Scenario | Expected | Actual | Latency (s) | Plate readable (eye / OCR) | Incident ID | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | foot, drop bag, leave | alert | | | n/a | | |
| 2 | scooter, drop bag, leave | alert + plate | | | | | |

## Metrics

| Metric | Target (PRD) | Result | Pass? |
| --- | --- | --- | --- |
| Detection rate | ≥ 80% | | |
| False sirens (30 min) | 0 | | |
| Trigger latency p90 | < 8 s | | |
| Avg FPS / thermal max | ≥ 2 / < SEVERE | | |

## Findings → actions
- <finding> → <LEARNINGS entry / fixture added / ticket>

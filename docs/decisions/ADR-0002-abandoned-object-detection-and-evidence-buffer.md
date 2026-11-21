# ADR-0002: Class-agnostic abandoned-object detection and a pre-event evidence buffer

- **Status:** Accepted
- **Date:** 2026-11-21

## Context
The v1.0 PRD triggered on a new `trash/litter` **detector box** appearing, and captured the full-resolution evidence frame **after** the trigger. Two problems:
1. Litter datasets (TACO) don't match our viewpoint, and dumped waste is too varied for one class. A class-gated trigger means missed incidents (PRD §6.1).
2. The trigger needs the actor to have **left** the ROI, plus 5 s of dwell. A post-trigger capture therefore cannot contain the vehicle or its plate.

## Decision
1. **Detect "a new static thing left behind by an actor" without depending on its class.** Freeze a grayscale ROI baseline when an actor enters. After all actors leave and motion settles, diff the current frame against the baseline (gradient + luma, brightness-normalised), extract blobs, and require persistence for `dwell_time`. Illumination and shake guards abort to `INCONCLUSIVE`. A litter classifier may *raise confidence*, but it does not gate the trigger in v1.
2. **Gather evidence while the actor is present.** Keep the top-K sharpest/largest analysis frames per vehicle track, plus up to 2 speculative high-res `ImageCapture` shots per track. Commit them on confirmation, and discard them after 2 min with no incident.
3. Animals are tracked but never count as actors. A new object that appears with no human/vehicle actor is logged as `UNATTRIBUTED_OBJECT`, with no siren.

## Consequences
- The trigger is robust to unknown waste types, and parked vehicles don't need special handling.
- Plate evidence becomes achievable (PRD §6.6 resolution analysis).
- New failure modes: shadows, rain and swaying foliage. These are mitigated by the gradient diff, illumination abort, ROI design and field tuning, and covered by scenario-replay tests.
- A small cost in extra captures and memory while an actor is present (bounded: K=3 frames + 2 captures per track).

## Alternatives considered
- **A litter class as the trigger (v1.0):** too dependent on a model we don't have yet.
- **A video action-recognition model (as in the IWDD contest entries):** better accuracy ceiling, but too heavy for phone thermals at MVP stage. Revisit for edge hardware.
- **Continuous video ring buffer with post-hoc extraction:** heavier on storage, power and privacy. A bounded frame buffer meets the need.

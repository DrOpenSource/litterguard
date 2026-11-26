# ADR-0005: The zone editor and live view draw on analysis frames, not a camera viewfinder

- **Status:** Proposed
- **Date:** 2026-11-26
- **Deciders:** coding agent (conservative default while building the walking skeleton; revisit at the P2 review)

## Context
The plan (P2.10) put the ROI editor on the `camera-compose` viewfinder. A `Preview` stream and the `ImageAnalysis` stream can differ in aspect ratio, crop and rotation. FR-1.3 stores the ROI **normalised to the analysis frame**, so a viewfinder would need a coordinate transform that only a device can prove right. Features also may not depend on `:core:camera` (ARCHITECTURE §2), while a viewfinder needs a CameraX `SurfaceRequest`.

## Decision
The setup and monitor screens show the **analysis frames themselves**, downscaled 2×, through the `FrameSource` port. Setup asks for 5 FPS; the live view shows each processed frame (3 FPS).

- A corner tapped on the picture is already in pipeline coordinates, so there is no transform to get wrong.
- Overlay boxes line up with exactly the frame they were computed from.
- `:feature:setup` no longer depends on `camera-compose`.

## Consequences
- The picture is choppy (3 to 5 FPS) compared with a viewfinder. That is acceptable for a fixed, mounted phone.
- `:core:ui` converts `ImageBuffer` to a `Bitmap` for display, and `:core:data` does so to encode JPEGs. ARCHITECTURE §3 is updated to say so.
- If operators find setup hard to use, add a viewfinder later behind a port that also returns the preview-to-analysis transform.

## Alternatives considered
- **`camera-compose` viewfinder with a coordinate transform:** smoother, but more code, a new port carrying Android types, and a class of silent misalignment bugs.
- **Letting features depend on `:core:camera`:** breaks the dependency rule.

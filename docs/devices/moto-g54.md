# Reference device: Motorola Moto G54 5G

Owner decision D3 (2026-11-21): this is the **primary test device**. All performance and thermal targets are measured here.

| Spec (public sources; verify on the unit) | Value | Why it matters |
| --- | --- | --- |
| SoC | MediaTek Dimensity 7020 (6 nm): 2× Cortex-A78 @ 2.2 GHz + 6× A55 @ 2.0 GHz | Only **2 big cores**, so benchmark XNNPACK with 2 vs 4 threads |
| GPU | IMG BXM-8-256 | LiteRT GPU support on Imagination GPUs is unproven, so **benchmark it; don't assume**. CPU INT8 may win |
| NPU | MediaTek APU | LiteRT NPU support for this SoC is unconfirmed. Treat it as unavailable until tested |
| Main camera | 50 MP, f/1.8, PDAF, **OIS** | OIS helps sharpness. Check whether CameraX exposes full 50 MP or only 12.5 MP binned output |
| Other cameras | 8 MP ultrawide; **no telephoto** | Zoom is digital, so plate resolution depends on the high-res capture mode (PRD §6.6) |
| OS | Shipped with Android 13; last major update reportedly Android 15 | Android 14+ camera foreground-service rules apply; Android 15 BOOT_COMPLETED restriction likely |
| Battery | 5000–6000 mAh (sources differ) | Irrelevant on mains power; charge-limit behaviour matters for a 24/7 run |

## Plate-resolution estimate on this device (PRD §6.6)

The main camera's horizontal field of view is about 80°, so at 20 m the frame is roughly 34 m wide. A 0.5 m plate then gets:

| Capture | Pixels across the plate |
| --- | --- |
| 1280-px analysis frame | ~19 px |
| 4080-px binned still (12.5 MP) | ~60 px |
| 8160-px full-res still (50 MP), if exposed | ~120 px |
| 2× digital zoom crop of a 50 MP still | ~120 px |

OCR needs roughly 80 px or more. So **the 50 MP capture mode, or a closer mount, decides whether OCR works beyond about 12 m.** P0.7 measures this.

## Baseline checklist (task P0.7)

- [ ] Record the exact model, RAM, Android version and security patch (`adb shell getprop ro.build.fingerprint`)
- [ ] Turn on Developer options + USB debugging; `adb devices` works from the dev machine
- [ ] Camera capabilities: CameraX supported resolutions for ImageAnalysis and ImageCapture; whether a high-resolution (50 MP) capture mode is available; zoom ratio range; `StreamUseCase` support; torch available
- [ ] Thermal: `adb shell dumpsys thermalservice`; whether `getThermalHeadroom()` returns a number or NaN
- [ ] `benchmark_model` for stock YOLO11n INT8 at 320/416/640 on CPU (2 and 4 threads) and on GPU: p50/p90 latency, peak memory
- [ ] 30-minute idle camera run at 3 FPS analysis: device temperature and thermal status trend
- [ ] Battery/background: Motorola battery-optimisation settings path; whether the app survives 1 h with the screen dimmed (kiosk)
- [ ] Record the results in `docs/memory/LEARNINGS.md` with tag `[device:moto-g54]`

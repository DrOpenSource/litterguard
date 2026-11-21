# Reference device: Motorola Moto G54 5G

Owner decision D3 (2026-11-21): this is the **primary test device**. All performance and thermal targets are measured here.

| Spec (public sources; verify on the unit) | Value | Why it matters |
| --- | --- | --- |
| SoC | MediaTek Dimensity 7020 (6 nm): 2× Cortex-A78 @ 2.2 GHz + 6× A55 @ 2.0 GHz | Only **2 big cores**, so benchmark XNNPACK with 2 vs 4 threads |
| GPU | IMG BXM-8-256 | LiteRT GPU support on Imagination GPUs is unproven, so **benchmark it; don't assume**. CPU INT8 may win |
| NPU | MediaTek APU | LiteRT NPU support for this SoC is unconfirmed. Treat it as unavailable until tested |
| Main camera | 50 MP quad-Bayer, f/1.8, PDAF, **OIS** | **Measured: apps get only the 12.6 MP (4096×3072) binned output**, HFOV ≈ 61°. See the checklist below |
| Other cameras | 8 MP ultrawide; **no telephoto** | Zoom is digital, so plate resolution is capped by the 4096-px still (PRD §6.6) |
| OS | Shipped with Android 13; **measured: Android 15 (SDK 35)**, patch 2026-09-01 | Android 14+ camera foreground-service rules apply; the Android 15 BOOT_COMPLETED restriction **applies** |
| Battery | 5000–6000 mAh (sources differ) | Irrelevant on mains power; charge-limit behaviour matters for a 24/7 run |
| **Owner's unit (About-phone screenshot, 2026-11-21)** | 12 GB RAM, 256 GB storage (160 GB used), rear 50 MP + 8 MP, front 16 MP, 2400×1080 display | 12 GB RAM leaves plenty of headroom for two models plus the evidence buffer. Build number is under About phone → Device identifiers |

## Plate-resolution estimate on this device (PRD §6.6)

*Corrected with measured optics, 2026-11-21:* focal length 4.27 mm on a 5.00 mm-wide sensor gives a horizontal FOV of **≈ 61°**. The original public-spec guess was 80°. The frame is **1.17 × distance** wide, so 23.4 m wide at 20 m. **Apps cannot get 50 MP**; the largest still is 4096×3072.

| Capture | Car plate (0.50 m) at 20 m | Two-wheeler plate (0.285 m) at 10 m |
| --- | --- | --- |
| 1280-px analysis frame | ~27 px | ~31 px |
| **4096-px still (12.6 MP, the maximum)** | **~87 px** | **~100 px** |
| Digital zoom | adds no detail; it only crops the same 4096-px readout | — |

OCR needs roughly **80 px or more**. Maximum plate-readable distance with 12.6 MP stills:
- car plates: about **22 m**;
- two-wheeler plates: about **12 m**.

Mount the phone within those distances of the vehicle stopping point (OWNER_GUIDE Task D records the distance). These figures are geometry only; motion blur and night noise will reduce them. P3 and P4 measure the real numbers.

## Baseline checklist (task P0.8)

- [x] Record the exact model, RAM, Android version and security patch. **Measured 2026-11-21 over adb:**
  - `motorola/cancunf_g_sysq/cancunf:15/V1TDS35H.83-20-5-14/24971a-330658:user/release-keys`
  - Android **15** (SDK 35), security patch 2026-09-01, vendor partition Android 12
  - SoC `MT6855V/AZA` (Dimensity 7020). cpu0–5 run at 2.0 GHz (A55) and **cpu6–7 at 2.2 GHz (A78)**
  - MemTotal 11.9 GB; about 5.6 GB available while idle
- [x] Turn on Developer options + USB debugging; `adb devices` works (the device serial is deliberately not recorded in this public repo).
  - First attempt: Windows showed "Unknown USB Device (Device Descriptor Request Failed)" (VID_0000). That is a USB-level failure, caused by the cable or the port.
  - Second attempt: the phone enumerated as MTP only (PID `2E82`), meaning USB debugging was off.
  - With debugging on, it enumerates as PID `2E81`/`2E76` (with the ADB interface). The Windows ADB driver needed no manual install.
- [x] Camera capabilities (`dumpsys media.camera`). There are 3 devices:

  | ID | Facing | Active array | Focal / sensor width | HFOV | Level | Notes |
  | --- | --- | --- | --- | --- | --- | --- |
  | 0 | back (main) | **4096×3072 (12.6 MP)** | 4.27 mm / 5.00 mm | **≈ 61°** | LEVEL_3 | OIS available, flash, zoom 1–8× (digital), RAW, JPEG max 4096×3072 |
  | 1 | front | 2320×1740 | 3.27 / 4.66 | ≈ 71° | LEVEL_3 | |
  | 2 | back (ultrawide) | 3264×2448 (8 MP) | 1.66 / 3.65 | ≈ 95° | FULL | |

  - **No 50 MP mode for apps.** Apps only get the 4×-binned 12.6 MP output. The `ULTRA_HIGH_RESOLUTION_SENSOR` capability is absent and no `*MaximumResolution` stream configs are listed.
  - `availableStreamUseCases` is not advertised, so don't rely on `StreamUseCase`.
  - The earlier 80° HFOV assumption was wrong: the main camera is about 61°. See the corrected plate estimate above.
- [x] Thermal (`dumpsys thermalservice`): ThermalHAL 2.0 is connected.
  - CPU, GPU and NPU **throttling thresholds start at SEVERE: 85 / 90 / 100 / 117 °C** for SEVERE / CRITICAL / EMERGENCY / SHUTDOWN. LIGHT and MODERATE are **NaN**. Skin is 80 / 85 / 90 / 95 °C; battery is 50 / 55 / 59 / 90 °C.
  - The headroom thresholds are `[NaN×4, 1.17, 1.33, 1.5]`, so headroom 1.0 means SEVERE.
  - **Consequence: the status listener jumps from NONE straight to SEVERE, so a status-only policy would never use the 2 FPS tier.** FR-8.4 now uses `getThermalHeadroom()` for the early tiers.
  - Idle on USB: CPU 44 °C, skin 39 °C, battery 33 °C.
  - We can't read `getThermalHeadroom()` from the shell. It is checked from the app in P2.
- Power: the laptop USB port supplies only **500 mA** (`Max charging current: 500000`). Field runs need the wall charger.
- [ ] `benchmark_model` for stock YOLO11n INT8 at 320/416/640 on CPU (2 and 4 threads) and on GPU: p50/p90 latency, peak memory
- [ ] 30-minute idle camera run at 3 FPS analysis: device temperature and thermal status trend
- [ ] Battery/background: Motorola battery-optimisation settings path; whether the app survives 1 h with the screen dimmed (kiosk)
- [ ] Record the results in `docs/memory/LEARNINGS.md` with tag `[device:moto-g54]`

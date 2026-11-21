"""Export stock YOLO11n to LiteRT (.tflite) at several input sizes for the P0.8 device benchmark.

Usage (from the repo root; Linux x86-64 or macOS only, because Ultralytics' LiteRT exporter refuses Windows):
    python -I ml/scripts/export_bench_models.py
On Windows dev machines this runs in CI instead: `.github/workflows/ml-export-bench.yml`.

Uses the Ultralytics `litert` exporter (litert-torch). Input is FP32 NCHW `[1, 3, imgsz, imgsz]`, output FP32.
Writes FP32 and static-INT8 (float I/O) variants as `yolo11n_<variant>_<imgsz>.tflite` into
`core/ml/src/androidTest/assets/bench/`, where `DetectorBenchmarkTest` picks them up. These files are
git-ignored and must never be committed (AGENTS.md hard rule 8). Stock COCO weights: AGPL-3.0 (ADR-0003).
INT8 is calibrated on Ultralytics' tiny `coco8` set, which is fine for latency and NOT representative of
accuracy; real calibration uses our own viewpoint data (ml/README.md).
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
WEIGHTS_DIR = REPO / "ml" / "weights"
OUT_DIR = REPO / "core" / "ml" / "src" / "androidTest" / "assets" / "bench"
IMAGE_SIZES = (320, 416, 640)
VARIANTS = {"fp32": None, "int8": 8}  # label -> Ultralytics `quantize` value


def main() -> int:
    from ultralytics import YOLO  # imported late so --help style failures are cheap

    WEIGHTS_DIR.mkdir(parents=True, exist_ok=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    weights = WEIGHTS_DIR / "yolo11n.pt"

    for size in IMAGE_SIZES:
        for label, quantize in VARIANTS.items():
            model = YOLO(str(weights) if weights.exists() else "yolo11n.pt")
            extra = {"quantize": quantize, "data": "coco8.yaml"} if quantize else {}
            src = Path(model.export(format="litert", imgsz=size, **extra))
            dst = OUT_DIR / f"yolo11n_{label}_{size}.tflite"
            shutil.move(src, dst)
            print(f"{dst.relative_to(REPO)}  {dst.stat().st_size / 1e6:.1f} MB")
    return 0


if __name__ == "__main__":
    sys.exit(main())

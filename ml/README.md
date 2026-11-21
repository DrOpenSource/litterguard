# ml/ — model training and export workspace

Python workspace for training, evaluating and exporting the on-device models. It is **not** part of the Android build.

```
ml/
├── configs/      dataset YAMLs + training/export configs (tracked)
├── scripts/      dataset conversion (TACO → single 'litter' class, plate sets), export, evaluation
├── eval/         evaluation reports (tracked, small)
├── datasets/     downloaded / converted data   (git-ignored)
├── runs/         training runs                 (git-ignored)
└── weights/      .pt / .onnx checkpoints       (git-ignored)
```

Rules:
- **License first.** Record the source and license of every dataset and base model in `configs/SOURCES.md` before using it (see `docs/research/REPO_VALIDATION.md`, ADR-0003).
- **Publish the shipped artifact** as a GitHub Release asset, and add a `core/ml/models.json` entry with url, sha256, input/output tensor shapes, classes, license and source. The Android build downloads and verifies it (ADR-0004). Never commit weights.
- **Calibrate INT8 on our own viewpoint data**, not only on COCO.
- Run scripts with the venv interpreter (`ml/.venv`), and run any third-party code with `python -I`.
- Framework: **Ultralytics** (AGPL-3.0, ADR-0003). Pin exact versions in `ml/requirements.txt` (task P6.1). Training configs are part of the Corresponding Source, so commit them.

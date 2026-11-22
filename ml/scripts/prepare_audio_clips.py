"""Turn the owner's phone recordings into deterrent clips for the app (P0.9, FR-4.1, FR-4.2, FR-3.5).

Usage (from the repo root, with the ml venv; works on Windows):
    pip install -r ml/requirements-audio.txt
    python -I ml/scripts/prepare_audio_clips.py --out core/alerts/src/main/res/raw

The raw phone recordings are read from `field-data/audio/raw/` (git-ignored); pass `--raw` to change that.
Output names are Android resource names (`warning_en`, `notice_en`, `test_tone`), so they can go straight into
`res/raw`. The speaker (the project owner) agreed on 2026-11-22 to publish their recordings with the code.

For each recording this:
  1. trims leading and trailing silence (leading silence adds straight to the H2 siren latency),
  2. removes rumble below 80 Hz, downmixes to mono (the phone speaker and most BT speakers are mono), and
     applies gentle 3:1 compression,
  3. resamples to 48 kHz and loudness-normalises to -14 LUFS integrated, -1.5 dBTP true peak (two-pass EBU R128),
  4. adds a 10 ms fade-in and a 60 ms fade-out to avoid clicks, then writes 16-bit PCM WAV.
It also synthesises `test_tone.wav` (a short two-note chime) for the Test Siren button.

A recorded voice is personal data: re-recordings by anyone else need that speaker's consent before the output
is committed (AGENTS.md hard rule 5). Without `--out`, output goes to the git-ignored `field-data/audio/clips/`.
ffmpeg comes from the `imageio-ffmpeg` wheel (BSD-2-Clause wrapper around an LGPL/GPL ffmpeg build). It is a
dev-machine tool only and is not shipped in the app.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# Recording file name on the phone (case-insensitive stem) -> clip name used by the app.
CLIPS = {
    "litterwarning": "warning_en",
    "litternotice": "notice_en",
}
TARGET_LUFS = -14.0
TARGET_TP = -1.5
TARGET_LRA = 11.0
SAMPLE_RATE = 48_000
SILENCE_DB = -45  # anything quieter than this at the start/end counts as silence
TONE_SECONDS = 0.9


def ffmpeg_exe() -> str:
    import imageio_ffmpeg

    return imageio_ffmpeg.get_ffmpeg_exe()


def run(args: list[str]) -> str:
    proc = subprocess.run(args, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr)
        raise SystemExit(f"ffmpeg failed: {' '.join(args[:6])} ...")
    return proc.stderr


def pre_chain() -> str:
    trim = f"silenceremove=start_periods=1:start_threshold={SILENCE_DB}dB:start_silence=0.02"
    # Trim the tail by reversing, trimming the (new) start, and reversing back.
    # Gentle compression evens out syllables so the clip reaches the loudness target under the true-peak cap,
    # and keeps quieter words intelligible outdoors.
    compress = "acompressor=threshold=-20dB:ratio=3:attack=5:release=100:makeup=2"
    return f"{trim},areverse,{trim},areverse,highpass=f=80,pan=mono|c0=0.5*c0+0.5*c1,{compress}"


def measure(ff: str, src: list[str], chain: str) -> dict[str, str]:
    stderr = run(
        [ff, "-hide_banner", "-nostats", *src, "-af",
         f"{chain},loudnorm=I={TARGET_LUFS}:TP={TARGET_TP}:LRA={TARGET_LRA}:print_format=json",
         "-f", "null", "-"],
    )
    return json.loads(re.findall(r"\{[^{}]*\}", stderr)[-1])


def normalise(ff: str, src: list[str], chain: str, dest: Path) -> None:
    m = measure(ff, src, chain)
    loudnorm = (
        f"loudnorm=I={TARGET_LUFS}:TP={TARGET_TP}:LRA={TARGET_LRA}"
        f":measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
        f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true"
    )
    # loudnorm upsamples internally to 192 kHz, so resample after it.
    tmp = dest.with_suffix(".tmp.wav")
    run([ff, "-hide_banner", "-nostats", "-y", *src, "-af", f"{chain},{loudnorm},aresample={SAMPLE_RATE}",
         "-ac", "1", "-c:a", "pcm_s16le", str(tmp)])
    duration = probe_duration(ff, tmp)
    fades = f"afade=t=in:d=0.01,afade=t=out:st={max(duration - 0.06, 0):.3f}:d=0.06"
    run([ff, "-hide_banner", "-nostats", "-y", "-i", str(tmp), "-af", fades, "-c:a", "pcm_s16le", str(dest)])
    tmp.unlink()


def probe_duration(ff: str, path: Path) -> float:
    stderr = run([ff, "-hide_banner", "-i", str(path), "-f", "null", "-"])
    h, mnt, s = re.search(r"Duration: (\d+):(\d+):([\d.]+)", stderr).groups()
    return int(h) * 3600 + int(mnt) * 60 + float(s)


def report(ff: str, path: Path) -> str:
    m = measure(ff, ["-i", str(path)], "anull")
    return (f"{path.name}: {probe_duration(ff, path):.2f} s, {m['input_i']} LUFS, "
            f"{m['input_tp']} dBTP")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--raw", type=Path, default=REPO / "field-data" / "audio" / "raw")
    parser.add_argument("--out", type=Path, default=REPO / "field-data" / "audio" / "clips")
    args = parser.parse_args()
    ff = ffmpeg_exe()
    args.out.mkdir(parents=True, exist_ok=True)

    found = {p.stem.lower(): p for p in args.raw.iterdir() if p.is_file()}
    missing = [stem for stem in CLIPS if stem not in found]
    if missing:
        raise SystemExit(f"missing recordings in {args.raw}: {missing}")

    outputs = []
    for stem, name in CLIPS.items():
        dest = args.out / f"{name}.wav"
        normalise(ff, ["-i", str(found[stem])], pre_chain(), dest)
        outputs.append(dest)

    # Test tone: E5 then A5, each with a soft attack and decay. Synthetic, so it carries no personal data.
    tone = args.out / "test_tone.wav"
    half = TONE_SECONDS / 2
    src = ["-f", "lavfi", "-i",
           f"aevalsrc='0.5*sin(2*PI*if(lt(t,{half}),659.25,880)*t)*exp(-3*mod(t,{half}))':"
           f"s={SAMPLE_RATE}:d={TONE_SECONDS}"]
    normalise(ff, src, "anull", tone)
    outputs.append(tone)

    for path in outputs:
        print(report(ff, path))
    return 0


if __name__ == "__main__":
    sys.exit(main())

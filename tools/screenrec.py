#!/usr/bin/env python3
"""Record the TV's screen while remote keys are pressed, and optionally turn the clip into an animated WebP.

Set TV_HOST as for tv.py. Usage:
    screenrec.py <out.mp4> <seconds> [keys.txt]
        [--webp <out.webp>] [--crop W:H:X:Y] [--start S] [--length S] [--fps N] [--width PX]
    screenrec.py --help

keys.txt holds one TV shell line per line, run once the recording has started, through a second connection:
    input keyevent DPAD_RIGHT
    sleep 2
Without it, the clip records whatever happens on screen.

--webp converts with ffmpeg (from PATH, or from the imageio-ffmpeg package): --crop cuts out the part to show
(in 1280x720 pixels, the recording's size), --start and --length trim it, --fps (default 15) and --width
(default: the crop's own width) keep the file small. The WebP loops forever, which GitHub shows like a GIF.

Before a clip goes anywhere public, crop out what the person watches (posters, titles, artwork),
Wi-Fi names, accounts and device names, as the repo's privacy rules ask (AGENTS.md, "Hard rules").
"""
import os
import shutil
import subprocess
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
TV = [sys.executable, os.path.join(HERE, "tv.py")]
REMOTE = "/sdcard/screenrec.mp4"


def tv_batch(lines):
    return subprocess.run(TV + ["--batch"], input="\n".join(lines) + "\n", text=True,
                          capture_output=True, check=False)


def ffmpeg():
    found = shutil.which("ffmpeg")
    if found:
        return found
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        sys.exit("screenrec.py: --webp needs ffmpeg on PATH, or `pip install imageio-ffmpeg`")


def to_webp(mp4, webp, crop=None, start=None, length=None, fps=15, width=None):
    filters = [f"fps={fps}"]
    if crop:
        filters.append(f"crop={crop}")
    if width:
        filters.append(f"scale={width}:-2:flags=lanczos")
    cmd = [ffmpeg(), "-v", "error", "-y"]
    if start:
        cmd += ["-ss", str(start)]
    if length:
        cmd += ["-t", str(length)]
    cmd += ["-i", mp4, "-an", "-vf", ",".join(filters), "-c:v", "libwebp", "-loop", "0",
            "-quality", "75", "-compression_level", "6", "-preset", "picture", webp]
    subprocess.run(cmd, check=True)
    print(f"webp {webp} ({os.path.getsize(webp)} bytes)")


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__.strip())
        return 0 if argv else 2
    opts, pos, i = {}, [], 0
    while i < len(argv):
        if argv[i].startswith("--"):
            if i + 1 >= len(argv):
                sys.exit(f"screenrec.py: {argv[i]} needs a value")
            opts[argv[i][2:]] = argv[i + 1]
            i += 2
        else:
            pos.append(argv[i])
            i += 1
    if len(pos) not in (2, 3) or set(opts) - {"webp", "crop", "start", "length", "fps", "width"}:
        print(__doc__.strip())
        return 2
    out, seconds = pos[0], int(pos[1])
    keys = open(pos[2]).read().splitlines() if len(pos) == 3 else []
    if not os.environ.get("TV_HOST") and not os.environ.get("TV_ADB_BINARY"):
        sys.exit("screenrec.py: set TV_HOST to the TV's IP")

    # screenrecord blocks until its time limit, so it runs on one connection while the keys go over another.
    rec = threading.Thread(target=tv_batch, args=([
        f"screenrecord --size 1280x720 --bit-rate 12000000 --time-limit {seconds} {REMOTE}"],))
    rec.start()
    time.sleep(1.5)
    if keys:
        tv_batch(keys)
    rec.join()
    pulled = subprocess.run(TV + ["--pull", REMOTE, out], check=False).returncode
    tv_batch([f"rm -f {REMOTE}"])
    if pulled:
        return 1
    if "webp" in opts:
        to_webp(out, opts["webp"], opts.get("crop"), opts.get("start"), opts.get("length"),
                int(opts.get("fps", 15)), opts.get("width"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

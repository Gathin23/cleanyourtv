#!/usr/bin/env python3
"""Fallback file transfer: send a file to the TV as base64 chunks through plain shell commands.

Usage: b64push.py <local file> <remote path>

The file is assembled under a temporary name next to the target and checked with md5sum; only a
matching copy is moved over the target, so a failed or corrupted transfer leaves the old file alone.
"""
import base64
import hashlib
import os
import re
import sys

CHUNK = 48 * 1024  # raw bytes per shell command (base64 grows it by 4/3); capped per TV in main()
# adbd before Android 7 aborts the connection on an ADB "open" payload over 4096 bytes, which
# Android 5.x TVs announce as their max data size. Newer adbd takes the full 48 KB.

# The remote path is pasted into the TV's shell: absolute, plain characters, no "..".
REMOTE_RE = re.compile(r"/[A-Za-z0-9._/-]+")
USAGE = "usage: b64push.py <local file> <remote path>\n"


def safe_remote(path):
    return bool(REMOTE_RE.fullmatch(path)) and ".." not in path and not path.endswith("/")


def q(path):
    """Single-quoted for the TV's shell. safe_remote() already rules out a quote inside."""
    return "'" + path + "'"


def main():
    args = sys.argv[1:]
    if len(args) != 2:
        sys.stderr.write(USAGE)
        return 2
    local, remote = args
    if not safe_remote(remote):
        sys.stderr.write(f"refusing remote path {remote!r}: use an absolute path of letters, digits, "
                         "'.', '_', '-' and '/', without '..'\n")
        return 2
    with open(local, "rb") as f:
        data = f.read()
    want = hashlib.md5(data).hexdigest()

    # Imported only now: tv.py exits at import time when TV_HOST is unset, which would hide the
    # usage message above.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tv  # noqa: E402

    dev = tv.connect()
    b64 = remote + ".b64.part"
    part = remote + ".part"
    try:
        chunk = min(CHUNK, dev.max_chunk_size * 3 // 4 - 64)   # base64 of the chunk plus the echo must fit one payload
        chunk -= chunk % 3   # a multiple of 3, or each chunk ends in '=' padding mid-stream and base64 -d corrupts it
        # Only our own temporary names are cleared; the target stays until the copy is verified.
        # `: >` creates the base64 file even for an empty upload, so `base64 -d` has an input.
        dev.shell(f"rm -f {q(b64)} {q(part)} && : > {q(b64)}")
        n = (len(data) + chunk - 1) // chunk
        for i in range(n):
            piece = base64.b64encode(data[i * chunk:(i + 1) * chunk]).decode()
            out = dev.shell(f"echo '{piece}' >> {q(b64)} && echo ok{i}", read_timeout_s=60.0)
            if f"ok{i}" not in out:
                print(f"chunk {i} failed: {out}")
                dev.shell(f"rm -f {q(b64)} {q(part)}")
                return 1
            print(f"\rchunk {i + 1}/{n}", end="", flush=True)
        if n:
            print()
        out = dev.shell(f"base64 -d < {q(b64)} > {q(part)} && md5sum {q(part)}; rm -f {q(b64)}",
                        read_timeout_s=120.0)
        got = out.split()[0] if out.strip() else ""
        ok = got == want
        print("md5 remote:", got, "local:", want, "MATCH" if ok else "MISMATCH")
        if not ok:
            dev.shell(f"rm -f {q(part)}")
            print(f"{remote} was left as it was.", file=sys.stderr)
            return 1
        out = dev.shell(f"mv -f {q(part)} {q(remote)} && echo moved", read_timeout_s=60.0)
        if "moved" not in out:
            dev.shell(f"rm -f {q(part)}")
            print(f"could not move the verified copy over {remote}: {out.strip()}", file=sys.stderr)
            return 1
        return 0
    finally:
        # Exceptions (including interrupted transfers) must also clear staging. A disconnected TV
        # may refuse cleanup; report that without hiding the original failure or deleting the target.
        try:
            dev.shell(f"rm -f {q(b64)} {q(part)}")
        except Exception as error:
            print(f"could not remove temporary upload files: {error}", file=sys.stderr)
        finally:
            dev.close()


if __name__ == "__main__":
    sys.exit(main())

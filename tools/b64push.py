#!/usr/bin/env python3
"""Fallback file transfer: send a file to the TV as base64 chunks through plain shell commands.

Usage: b64push.py <local file> <remote path>

The file is assembled under a temporary name next to the target and checked with md5sum; only a
matching copy is moved over the target, so a failed or corrupted transfer leaves the old file alone.
Where the TV refuses that move (some firmware refuses rename(2) in /data/local/tmp, #1588), the
verified file is copied over the target and checked again. That copy is not atomic.

Exit status: 0 the copy is on the TV, 1 a transfer failed (the target is as it was, unless that
copy failed or was interrupted, which the message says), 2 nothing was sent: the arguments were
refused, or the connection failed in one of the ways tv.py explains (a refused port, a Wireless
debugging port, a handshake that timed out, a TV that cannot be reached). Why it stopped goes to
stderr, which the scripts that throw the progress on stdout away still show.
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
    # Read the file here, before the TV is opened: a path typed from the wrong folder, or a build
    # that left no APK behind, is an argument that is wrong rather than a transfer that failed, and
    # exit 1 would look to the caller like a push that started. The OSError covers all three ways in
    # - no such file, a directory, a file in the middle of the path - with the reason the OS gave.
    try:
        with open(local, "rb") as f:
            data = f.read()
    except OSError as error:
        sys.stderr.write(f"cannot read {local!r}: {error.strerror or error}. Nothing was sent to the TV.\n")
        return 2
    want = hashlib.md5(data).hexdigest()

    # Imported only now: tv.py exits at import time when TV_HOST is unset, which would hide the
    # usage message above.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import tv  # noqa: E402

    # A refused port or a TV that cannot be reached gets tv.py's own explanation, not a traceback, and
    # exits 2 as tv.py does: nothing was sent. An error tv.py has nothing to add to keeps its traceback.
    try:
        dev = tv.connect()
    except Exception as error:
        why = tv.connect_error(error)
        if why is None:
            raise
        print(f"{why}\nNothing was sent to the TV.", file=sys.stderr)
        return 2
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
                if i:
                    print(flush=True)   # end the progress line, so the reason starts a line of its own
                print(f"chunk {i} failed: {out.strip() or 'no output'}", file=sys.stderr)
                dev.shell(f"rm -f {q(b64)} {q(part)}")
                print(f"{remote} was left as it was.", file=sys.stderr)
                return 1
            print(f"\rchunk {i + 1}/{n}", end="", flush=True)
        if n:
            print(flush=True)
        out = dev.shell(f"base64 -d < {q(b64)} > {q(part)} && md5sum {q(part)}; rm -f {q(b64)}",
                        read_timeout_s=120.0)
        got = out.split()[0] if out.strip() else ""
        ok = got == want
        # A match stays on stdout, where the photo and news screensaver scripts look for "md5".
        print("md5 remote:", got, "local:", want, "MATCH" if ok else "MISMATCH",
              file=sys.stdout if ok else sys.stderr)
        if not ok:
            dev.shell(f"rm -f {q(part)}")
            print(f"{remote} was left as it was.", file=sys.stderr)
            return 1
        out = dev.shell(f"mv -f {q(part)} {q(remote)} && echo moved", read_timeout_s=60.0)
        if "moved" not in out:
            # SELinux on a TCL ak30a5 (20250610) refuses rename(2) in /data/local/tmp while cp works
            # there (#1588). Copy the verified file over the target instead and check that copy too.
            # Unlike the move it is not atomic: a copy that stops part-way can leave the target
            # incomplete.
            refused = out.strip()
            try:
                out = dev.shell(f"cp -f {q(part)} {q(remote)} && md5sum {q(remote)}", read_timeout_s=120.0)
            except BaseException:
                # A connection that drops, a read that times out or Ctrl-C while cp is writing can
                # leave the target cut short. Say so, then let the transport error through.
                print(f"could not move the verified copy over {remote}: {refused}", file=sys.stderr)
                print(f"copying it over instead was interrupted. {remote} may be incomplete.",
                      file=sys.stderr)
                raise
            got = out.split()[0] if out.strip() else ""
            if got != want:
                dev.shell(f"rm -f {q(part)}")
                print(f"could not move the verified copy over {remote}: {refused}", file=sys.stderr)
                print(f"copying it over instead did not give a copy with md5 {want}: {out.strip()}. "
                      f"{remote} may be incomplete.", file=sys.stderr)
                return 1
            print(f"the move was refused ({refused}), so the file was copied: md5 {got} MATCH")
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

#!/usr/bin/env python3
"""Minimal ADB-over-TCP client for an Android TV using adb_shell (no adb binary needed).

Set TV_HOST to the TV's IP (or IP:port). Usage:
    tv.py --probe                      connect and print model / Android version
    tv.py <shell command...>           run one shell command
    tv.py --screenshot <local.png> [KEY ...] [--wait SECONDS]
                                       capture the screen on the TV, then pull it; press remote keys first
    tv.py --batch < commands.txt       one command per line, same connection
    tv.py --push <local> <remote>      small files only (see b64push.py for larger ones)
    tv.py --pull <remote> <local>
    tv.py --reboot                     return once the reboot request is accepted; the TV may still be restarting
"""
import os
import re
import sys
import time

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

# Windows consoles often use an old code page (cp1251, cp1252 and others) that cannot print every character
# in dumpsys output. Python then stops with an error in the middle. disable.ps1 reads this output to see if
# setupwraith owns the Wi-Fi network, so print a replacement character instead of stopping.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(errors="replace")
    except (AttributeError, ValueError):
        pass

def split_host_port(endpoint):
    """Split host:port or [ipv6]:port into (host, port_or_none)."""
    if endpoint.startswith("["):
        end = endpoint.find("]")
        if end != -1:
            host = endpoint[1:end]
            rest = endpoint[end + 1:]
            if rest.startswith(":"):
                return host, rest[1:]
            return host, None
    elif endpoint.count(":") == 1:
        host, port = endpoint.split(":", 1)
        return host, port
    return endpoint, None


_raw_host = os.environ.get("TV_HOST")
if not _raw_host:
    sys.exit("Set TV_HOST to your TV's IP address, e.g.  TV_HOST=192.168.1.50 tools/tv.py --probe\n"
             "(find it under Settings → Network, or look for the TV in `arp -a`)")
HOST, _endpoint_port = split_host_port(_raw_host)
_port_str = _endpoint_port or os.environ.get("TV_PORT") or "5555"
try:
    PORT = int(_port_str)
    if not (1 <= PORT <= 65535):
        raise ValueError
except ValueError:
    sys.exit(f"not a port number: {_port_str!r}")
KEYDIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adbkey")
KEY = os.path.join(KEYDIR, "adbkey")
# Reuse the stock adb key when the TV already trusts it (platform-tools / Android Studio users), so the
# "Allow USB debugging?" prompt doesn't come back a second time for this client. From #105.
ADB_KEY = os.path.join(os.path.expanduser("~"), ".android", "adbkey")
if not os.path.exists(KEY) and os.path.exists(ADB_KEY) and os.path.exists(ADB_KEY + ".pub"):
    KEY = ADB_KEY


def restrict(path):
    """Keep a private key to this account. Reported, never fatal: the key still works either way.
    Windows has no POSIX mode bits and ignores this."""
    try:
        os.chmod(path, 0o600)
    except OSError as e:
        print(f"warning: could not restrict permissions on {path}: {e}", file=sys.stderr)


def get_signer():
    # This key is an ADB credential: anyone who can read it gets a shell on every TV that has
    # accepted it, with no "Allow USB debugging?" prompt. adb_shell's keygen writes it with a plain
    # open(), so it lands at 0666 & ~umask, which is 0644 under the usual umask: readable by every
    # other account on the machine. Google's own adb keeps ~/.android/adbkey at 0600, so match that,
    # on every run, so a key written by an earlier version of this script is repaired too.
    os.makedirs(KEYDIR, mode=0o700, exist_ok=True)
    if not os.path.exists(KEY):
        keygen(KEY)
    if KEY != ADB_KEY:   # the stock key belongs to the user's own adb; don't touch its permissions
        restrict(KEY)
    with open(KEY) as f:
        priv = f.read()
    with open(KEY + ".pub") as f:
        pub = f.read()
    return PythonRSASigner(pub, priv)


def connect(timeout=120):
    dev = AdbDeviceTcp(HOST, PORT, default_transport_timeout_s=float(timeout))
    dev.connect(rsa_keys=[get_signer()], transport_timeout_s=float(timeout), auth_timeout_s=timeout)
    return dev


USAGE = """usage:
    tv.py --probe
    tv.py --screenshot <local.png> [KEY ...] [--wait SECONDS]
    tv.py --push <local> <remote>
    tv.py --pull <remote> <local>
    tv.py --batch < commands.txt
    tv.py --reboot
    tv.py <shell command...>
"""
# Arguments each known flag takes, the flag included. The shell branch takes anything, so a flag
# with the wrong count listed here is a typo, not a command for the TV.
# --screenshot takes its file and then any number of keys, so it is checked by screenshot_args instead.
ARITY = {"--probe": 1, "--push": 3, "--pull": 3, "--batch": 1, "--reboot": 1}

# screencap writes the PNG on the TV and we pull the file afterwards: adb_shell's shell() decodes
# what comes back as text, which corrupts an image, and `exec-out screencap -p` returns a broken
# PNG on some MediaTek sets (docs/TROUBLESHOOTING.md). /data/local/tmp is writable from shell.
SHOT_REMOTE = "/data/local/tmp/cleanyourtv-shot.png"
PNG = b"\x89PNG\r\n\x1a\n"
# Key names go into a shell command on the TV, so only plain key names and numeric keycodes get through.
KEY_NAME = re.compile(r"^[A-Z0-9_]+$")
KEY_GAP_S = 0.6   # between two keys, so the UI has finished reacting to the first


def check_arity(args):
    """Reject a known flag with the wrong argument count before connecting.

    Without this, `--screenshot` with a destination missing falls through to the shell branch and
    the TV tries to run `--screenshot` as a command."""
    if args and args[0] in ARITY and len(args) != ARITY[args[0]]:
        sys.stderr.write(USAGE)
        sys.exit(2)
    if args and args[0] == "--screenshot":
        screenshot_args(args[1:])


def screenshot_args(rest):
    """`<local.png> [KEY ...] [--wait SECONDS]` into (local, keys, wait), or a usage error before connecting.

    KEY is an Android key name without the KEYCODE_ prefix (HOME, DPAD_RIGHT), a full name (KEYCODE_BACK)
    or a numeric keycode (83). The screenshot is taken --wait seconds after the last key (default 1)."""
    local, keys, wait = None, [], 1.0
    i = 0
    while i < len(rest):
        arg = rest[i]
        if arg == "--wait" and i + 1 < len(rest):
            try:
                wait = float(rest[i + 1])
            except ValueError:
                wait = -1
            if wait < 0:
                usage_error(f"--wait needs a number of seconds, not {rest[i + 1]!r}")
            i += 2
            continue
        if local is None and not arg.startswith("--"):
            local = arg
        elif KEY_NAME.match(arg):
            keys.append(arg)
        else:
            usage_error(f"not a key name: {arg!r} (use names such as HOME or DPAD_RIGHT, in capitals, "
                        "or a numeric keycode)")
        i += 1
    if local is None:
        usage_error("--screenshot needs the file to save to")
    return local, keys, wait


def usage_error(message):
    sys.stderr.write(f"{message}\n{USAGE}")
    sys.exit(2)


def keyevent(name):
    """The `input keyevent` argument for a key name or keycode that already matched KEY_NAME."""
    return name if name.isdigit() or name.startswith("KEYCODE_") else "KEYCODE_" + name


def screenshot(dev, local, keys=(), wait=1.0):
    """Press `keys`, then capture the TV screen into `local`. Returns 0, or 1 when the capture failed or
    came back empty."""
    # Agents pass paths like shots/01-home.png before that folder exists, and the pull then fails
    # after the capture has already run on the TV (#364). Create it first.
    os.makedirs(os.path.dirname(os.path.abspath(local)), exist_ok=True)
    for i, key in enumerate(keys):
        if i:
            time.sleep(KEY_GAP_S)
        dev.shell(f"input keyevent {keyevent(key)}")
    if keys:
        time.sleep(wait)
    # The exit status tells a capture that failed (no file to pull) from one that came back empty: with
    # Netflix in front a TCL C755 exits 1 and leaves a 0-byte file, which is the empty case below.
    said = dev.shell(f"rm -f {SHOT_REMOTE}; screencap -p {SHOT_REMOTE}; echo rc=$?", read_timeout_s=120.0)
    rc = next((int(line[3:]) for line in said.splitlines() if re.fullmatch(r"rc=-?\d+", line.strip())), None)
    if rc not in (None, 0, 1):
        other = " ".join(line.strip() for line in said.splitlines() if line.strip() and not line.startswith("rc="))
        dev.shell(f"rm -f {SHOT_REMOTE}", read_timeout_s=60.0)
        print(f"screencap failed on the TV (exit status {rc}): {other or 'no output'}", file=sys.stderr)
        return 1
    dev.pull(SHOT_REMOTE, local, transport_timeout_s=300.0, read_timeout_s=300.0)
    dev.shell(f"rm -f {SHOT_REMOTE}", read_timeout_s=60.0)   # leave nothing behind on the TV
    size = os.path.getsize(local) if os.path.exists(local) else 0
    if size == 0:
        # 0 bytes means the input, not the connection: Live TV and HDMI captures come back empty on
        # most sets, and PROMPT.md tells the agent to look at the screen rather than the ADB link.
        print(f"captured 0 bytes into {local}: Live TV and HDMI inputs come back empty on most TVs, as do "
              "some streaming apps (Netflix), so check what is on screen before the ADB connection "
              "(see docs/TROUBLESHOOTING.md)", file=sys.stderr)
        return 1
    with open(local, "rb") as f:
        if f.read(len(PNG)) != PNG:
            print(f"{local} is not a PNG: the pull from the TV came back damaged; try again", file=sys.stderr)
            return 1
    print(f"screenshot {local} ({size} bytes)")
    return 0


def main():
    args = sys.argv[1:]
    check_arity(args)
    dev = connect()
    if args == ["--reboot"]:
        # Native reboot waits for ADB's OKAY, not the shell's CLSE that can be lost as adbd stops (#995).
        try:
            dev.reboot()
        finally:
            dev.close()
        print("Reboot request accepted; wait for the TV to restart before reconnecting.")
        return 0
    status = 0
    if not args or args == ["--probe"]:
        for p in ("ro.product.model", "ro.product.device", "ro.build.version.release",
                  "ro.build.version.sdk", "ro.build.display.id", "ro.product.manufacturer",
                  "ro.adb.secure", "service.adb.tcp.port"):
            print(f"{p} = {dev.shell(f'getprop {p}').strip()}")
    elif args[0] == "--screenshot":
        local, keys, wait = screenshot_args(args[1:])
        status = screenshot(dev, local, keys, wait)
    elif args[0] == "--push" and len(args) == 3:
        dev.push(args[1], args[2], transport_timeout_s=300.0, read_timeout_s=300.0)
        print(f"pushed {args[1]} -> {args[2]}")
    elif args[0] == "--pull" and len(args) == 3:
        dev.pull(args[1], args[2], transport_timeout_s=300.0, read_timeout_s=300.0)
        print(f"pulled {args[1]} -> {args[2]}")
    elif args[0] == "--batch":
        # one shell command per stdin line, each run separately on the same connection.
        # utf-8-sig drops the BOM Windows PowerShell may prepend to piped stdin.
        sys.stdin.reconfigure(encoding="utf-8-sig")
        for line in sys.stdin:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            out = dev.shell(line, read_timeout_s=120.0)
            sys.stdout.write(f"$ {line}\n{out}")
            if out and not out.endswith("\n"):
                sys.stdout.write("\n")
            sys.stdout.flush()
    else:
        out = dev.shell(" ".join(args), read_timeout_s=120.0)
        sys.stdout.write(out)
    dev.close()
    return status


if __name__ == "__main__":
    sys.exit(main())

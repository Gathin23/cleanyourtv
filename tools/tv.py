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
    tv.py --help                       this text; needs neither the venv nor a TV

Exit status: 0 the command ran (the TV's own answer is in its output), 1 the screenshot came back
empty or failed, 2 it was refused before anything ran on the TV - a flag it does not have, a local
path that cannot be used, a port it could not open.

Set TV_ADB_BINARY to the Google adb executable to use an already connected, paired transport
(including encrypted Wireless debugging). See tools/native-adb.md.
"""
import errno
import os
import re
import subprocess
import sys
import time

# -h/--help before the adb_shell imports and before TV_HOST is read: the usage is the first thing a
# reader types at a script they do not know, and it needs neither the venv nor a TV. The TV_HOST check
# below exits at import time, so a flag handled after it could never print anything but that error.
if sys.argv[1:2] and sys.argv[1] in ("-h", "--help"):
    print((__doc__ or "").strip())
    sys.exit(0)

# The docs type `tools/tv.py ...`, which starts the first python3 on PATH, and that one seldom has
# adb_shell: the README installs it into tools/venv only. Run that way, tv.py starts again under the
# venv's Python, or says how to make the venv, instead of ending in a ModuleNotFoundError traceback.
# Only when run as a script: recovery.py, privacy.py, bench.py and the tests import this file and
# handle a missing client themselves.
try:
    from adb_shell.adb_device import AdbDeviceTcp
    from adb_shell.auth.keygen import keygen
    from adb_shell.auth.sign_pythonrsa import PythonRSASigner
except ImportError as e:
    if __name__ != "__main__":
        raise
    _dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "venv")
    _venv = next((p for p in (os.path.join(_dir, "bin", "python"), os.path.join(_dir, "Scripts", "python.exe"))
                  if os.path.isfile(p)), None)
    # Once only, marked in the environment: a venv that lacks adb_shell as well gets the message below
    # rather than another run of itself.
    if _venv and not os.environ.get("CLEANYOURTV_TV_PY_VENV"):
        os.environ["CLEANYOURTV_TV_PY_VENV"] = "1"
        _args = [_venv, "-I", os.path.abspath(__file__), *sys.argv[1:]]
        if os.name == "nt":
            # No exec on Windows: os.execv starts a second process, and the shell takes its prompt back.
            import subprocess
            sys.exit(subprocess.call(_args))
        os.execv(_venv, _args)
    _make = (r"python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt"
             if os.name == "nt" else
             "python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt")
    sys.exit(f"tools/tv.py needs the packages in tools/requirements.txt ({e}).\nrun: {_make}")

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
# "Allow USB debugging?" prompt doesn't come back a second time for this client. From #105. While
# tools/adbkey has no key, the stock key is the only one; once it has one, connect() offers both.
ADB_KEY = os.path.join(os.path.expanduser("~"), ".android", "adbkey")
if not os.path.exists(KEY) and os.path.exists(ADB_KEY) and os.path.exists(ADB_KEY + ".pub"):
    KEY = ADB_KEY


def restrict(path, mode=0o600):
    """Keep a private key, or its folder, to this account. Reported, never fatal: the key still works
    either way. Windows has no POSIX mode bits and ignores this."""
    try:
        os.chmod(path, mode)
    except OSError as e:
        print(f"warning: could not restrict permissions on {path}: {e}", file=sys.stderr)


def get_signer():
    # This key is an ADB credential: anyone who can read it gets a shell on every TV that has
    # accepted it, with no "Allow USB debugging?" prompt. adb_shell's keygen writes it with a plain
    # open(), so it lands at 0666 & ~umask, which is 0644 under the usual umask: readable by every
    # other account on the machine. Google's own adb keeps ~/.android/adbkey at 0600, so match that,
    # on every run, so a key written by an earlier version of this script is repaired too.
    # makedirs leaves a folder that already exists at its old mode (0755 from a tv.py older than
    # #491), so the folder is set to 0700 on every run as well.
    os.makedirs(KEYDIR, mode=0o700, exist_ok=True)
    restrict(KEYDIR, 0o700)
    if not os.path.exists(KEY):
        # Google's adb opens a new key under umask(077), so it is 0600 from the moment it exists, not
        # only after the chmod below. adb_shell writes the .pub in the same call, so that is 0600 as
        # well; only this script reads it.
        old_umask = os.umask(0o077)
        try:
            keygen(KEY)
        finally:
            os.umask(old_umask)
    if KEY != ADB_KEY:   # the stock key belongs to the user's own adb; don't touch its permissions
        restrict(KEY)
    with open(KEY) as f:
        priv = f.read()
    with open(KEY + ".pub") as f:
        pub = f.read()
    return PythonRSASigner(pub, priv)


def stock_signer():
    """The stock ~/.android/adbkey, to offer after tools/adbkey's key, or None.

    A tools/adbkey made before the TV approved the stock adb key kept a set that trusts that key
    `unauthorized` until the folder was deleted (#1526). adb_shell signs the TV's token with each key in
    turn, as the stock adb does with its keys, and sends the first key's public half (the "Allow USB
    debugging?" prompt) only when the TV accepts none. So a second key adds no prompt, and the prompt
    still asks for tools/adbkey's key. The stock key is only read, never chmod-ed, and one that cannot
    be read or parsed is left out rather than stopping a connection tools/adbkey's key can make."""
    if KEY == ADB_KEY or not (os.path.exists(ADB_KEY) and os.path.exists(ADB_KEY + ".pub")):
        return None
    try:
        with open(ADB_KEY) as f:
            priv = f.read()
        with open(ADB_KEY + ".pub") as f:
            pub = f.read()
        return PythonRSASigner(pub, priv)
    except Exception:   # unreadable (a `sudo adb` run can leave it root's), or not a key adb_shell parses
        return None


class NativeAdbError(RuntimeError):
    """The selected Google ADB transport is unavailable or an operation failed."""


class NativeAdbDevice:
    """The subset of adb_shell used by the tools, over an existing Google ADB transport.

    Every command selects the same explicit endpoint. Never select the first connected TV,
    change adbd's mode, or disconnect a transport another program may be using.
    """

    # b64push sizes its shell commands from this payload bound. A small value also
    # keeps adb's command-line arguments below Windows' process argument limit.
    max_chunk_size = 4096

    def __init__(self, binary, host, port, timeout):
        self.binary = binary
        self.serial = f"[{host}]:{port}" if ":" in host else f"{host}:{port}"
        self.timeout = float(timeout)
        if self._run(["get-state"]).strip() != "device":
            raise NativeAdbError(f"{self.serial} is not an authorized ADB transport; pair and connect it first.")

    def _run(self, args, timeout=None):
        limit = self.timeout if timeout is None else float(timeout)
        try:
            result = subprocess.run([self.binary, "-s", self.serial, *args],
                                    capture_output=True, text=True, errors="replace", timeout=limit)
        except subprocess.TimeoutExpired as e:
            raise NativeAdbError(f"Google ADB {args[0]} on {self.serial} timed out after {limit:g}s.") from e
        except OSError as e:
            raise NativeAdbError(f"Could not run Google ADB {self.binary!r}: {e}") from e
        if result.returncode:
            detail = result.stderr.strip() or result.stdout.strip() or f"exit {result.returncode}"
            raise NativeAdbError(f"Google ADB {args[0]} on {self.serial}: {detail}")
        return result.stdout

    def shell(self, command, read_timeout_s=None, **kwargs):
        # -T avoids a PTY; -x matches adb_shell's shell service: remote stderr is in the
        # returned text and a remote exit code is not mistaken for a transport failure.
        # Guards still see remote Error/Failure lines. No local shell interprets command.
        return self._run(["shell", "-T", "-x", command], read_timeout_s)

    def push(self, local, remote, transport_timeout_s=None, read_timeout_s=None, **kwargs):
        self._run(["push", os.fspath(local), os.fspath(remote)],
                  read_timeout_s if read_timeout_s is not None else transport_timeout_s)

    def pull(self, remote, local, transport_timeout_s=None, read_timeout_s=None, **kwargs):
        self._run(["pull", os.fspath(remote), os.fspath(local)],
                  read_timeout_s if read_timeout_s is not None else transport_timeout_s)

    def reboot(self):
        self._run(["reboot"])

    def close(self):
        pass  # Each subprocess is closed; leave the shared ADB server and transport alone.


def connect(timeout=120):
    binary = os.environ.get("TV_ADB_BINARY")
    if binary:
        return NativeAdbDevice(binary, HOST, PORT, timeout)
    dev = AdbDeviceTcp(HOST, PORT, default_transport_timeout_s=float(timeout))
    keys = [get_signer()]   # first, so the prompt asks for the same key as before
    stock = stock_signer()
    if stock is not None:
        keys.append(stock)
    dev.connect(rsa_keys=keys, transport_timeout_s=float(timeout), auth_timeout_s=timeout)
    return dev


def connect_error(e):
    """What to print when connect() raised `e`, or None for an error this has nothing to add to.

    A refused port and a Wireless debugging port used to end tv.py with a traceback, which says nothing
    about the port to use instead (#1578). connect() itself still raises: check.py (through
    recovery.connect()) and privacy.py catch its errors with wording and exit codes of their own, and
    recovery.py --save still lets them through (a follow-up)."""
    if isinstance(e, NativeAdbError):
        return str(e)
    # Imported here rather than with the client above, so the stub adb_shell in the tests (adb_device
    # and auth only) still loads this file.
    from adb_shell.exceptions import AdbTimeoutError, InvalidCommandError, TcpTimeoutException
    target = f"{HOST}:{PORT}"
    # The way to port 5555 on a TV whose only network ADB is Wireless debugging (docs/TROUBLESHOOTING.md).
    pair = ("pair the TV once with the stock adb (`adb pair`, then `adb connect` to the port its Wireless "
            "debugging screen shows) and run `adb tcpip 5555`")
    reboot = ("Port 5555 usually closes at the next reboot; `adb connect` and `adb tcpip 5555` open it "
              "again, with no new pairing.")
    see = "See docs/TROUBLESHOOTING.md#connecting."
    if isinstance(e, InvalidCommandError) and "STLS" in str(e):
        # Android 11+ answers the handshake on its Wireless debugging port with STLS, a request for TLS.
        # adb_shell 0.4.4 (tools/requirements.txt) has no TLS, so no TV_HOST or TV_PORT gets past it.
        return (f"{target} is a Wireless debugging port: it asks for TLS, which tv.py cannot use.\n"
                f"To use port 5555 instead, {pair}, then set TV_HOST to the TV's IP alone and leave TV_PORT "
                f"unset. {reboot} {see}")
    if isinstance(e, ConnectionRefusedError):
        return (f"{target} refused the connection: nothing listens for ADB there, so network ADB is off "
                f"or on another port.\nIf the TV has Wireless debugging, {pair}. {reboot} A firmware that "
                f"runs adbd on a port of its own takes TV_HOST=<tv ip>:<port>.\nNot an Android TV (Samsung, LG, "
                f"Roku)? There's no ADB to connect to; see https://cleanyour.tv/not-android. {see}")
    if isinstance(e, (TcpTimeoutException, AdbTimeoutError)):
        return (f"{target} took the connection, but ADB did not finish the handshake "
                f"({type(e).__name__}: {e}).\nIf the TV shows \"Allow USB debugging?\", accept it and run "
                f"this again; otherwise check that TV_HOST is the TV's address. {see}")
    # Only a socket error means the TV was not reached. get_signer() runs inside connect() too, before any
    # socket opens, and its errors (adbkey.pub missing, a key or folder a `sudo` run left owned by root)
    # carry the file's name, which no socket error does: they keep the traceback, which names the file. A
    # connection reset or broken in the handshake keeps its traceback too, as the port was reached.
    if isinstance(e, OSError) and e.filename is None and not isinstance(e, ConnectionError):
        # macOS Local Network privacy denies a process the local subnet and the kernel reports that as
        # EHOSTUNREACH, the same errno as a TV that is switched off, so "is the TV on?" sends people
        # rebuilding a network that was never broken. The permission belongs to the app that runs this
        # script (Terminal, iTerm2, VS Code), not to python or adb: switching ADB client does not avoid it,
        # and a denial sticks once it happens. ARP and mDNS keep answering while it is denied, so the TV
        # stays discoverable and only unicast fails, which is what makes it read as a network fault. The
        # default gateway kept answering throughout, so it is the one address that tells the two apart.
        if sys.platform == "darwin" and e.errno == errno.EHOSTUNREACH:
            return (f"could not reach {target} ({e}). Is the TV on and on this network?\n"
                    f"On macOS this is also what Local Network privacy looks like. Allow the app that runs "
                    f"tv.py (Terminal, iTerm2, VS Code) under System Settings > Privacy & Security > Local "
                    f"Network, then quit and reopen it; granting it to python or adb does nothing. To tell "
                    f"the two apart, try the default gateway: if it answers while every other address on "
                    f"the subnet gives this error, it is the permission and not the TV. {see}")
        return f"could not reach {target} ({e}). Is the TV on and on this network? {see}"
    return None


USAGE = """usage:
    tv.py --probe
    tv.py --screenshot <local.png> [KEY ...] [--wait SECONDS]
    tv.py --push <local> <remote>
    tv.py --pull <remote> <local>
    tv.py --batch < commands.txt
    tv.py --reboot
    tv.py --help
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
    the TV tries to run `--screenshot` as a command. The same goes for a flag this script does not
    have at all: `--proob` used to be sent to the TV, whose shell answered "inaccessible or not
    found" while tv.py exited 0, so a mistyped flag looked like a command that ran."""
    if args and args[0] in ARITY and len(args) != ARITY[args[0]]:
        sys.stderr.write(USAGE)
        sys.exit(2)
    if args and args[0] == "--screenshot":
        screenshot_args(args[1:])
    elif args and args[0] in ("--push", "--pull"):
        transfer_args(args)
    elif args and args[0].startswith("-") and args[0] not in ARITY:
        usage_error(f"not a flag of tv.py: {args[0]!r}")


def transfer_args(args):
    """Judge the two paths of --push/--pull before the TV is opened.

    adb_shell opens the local path itself, so a path typed from the wrong folder was a
    FileNotFoundError traceback that only started once the connection had been made: exit 1, the code
    this script otherwise returns for a screenshot that came back empty, and not a word saying the run
    had sent nothing. --pull writes to the name it is given, so a folder sitting there was
    IsADirectoryError and a name in a folder that does not exist FileNotFoundError, both after the TV
    had already been asked for the file - tools/b64push.py's own local path has been answered this way
    since #1633. All of these are wrong before anything can be sent, so they are answered here, in the
    words b64push.py uses, and the TV is never opened for them.

    The two directions do not share one rule: a folder is a valid --push argument (adb_shell sends the
    files inside it), while --pull needs a file name inside a folder that is there.
    """
    if len(args) != 3:      # check_arity has already answered the count
        return
    push = args[0] == "--push"
    local, remote = (args[1], args[2]) if push else (args[2], args[1])
    if push:
        if not remote:
            usage_error("--push needs the path on the TV to write to")
        if not local:
            usage_error("--push needs the local file to send")
        if os.path.isdir(local):
            return          # adb_shell pushes the files inside it
        try:
            # Opened, not read: adb_shell opens it the same way, and a 500 KB APK is not loaded to
            # find out that it is not there.
            with open(local, "rb"):
                pass
        except OSError as e:
            path_error(f"cannot read {local}: {e.strerror or e}. Nothing was sent to the TV.")
        return
    if not remote:
        usage_error("--pull needs the path on the TV to read")
    if not local:
        usage_error("--pull needs the local file to save to")
    folder = os.path.dirname(os.path.abspath(local))
    if not os.path.isdir(folder):
        path_error(f"there is no folder {folder} for {local}: create it, or pull into a folder that is "
                   f"there. Nothing was pulled.")
    if os.path.isdir(local):
        path_error(f"{local} is a folder, and --pull writes the file to the name it is given: give a "
                   f"file name. Nothing was pulled.")
    if os.path.exists(local) and not os.access(local, os.W_OK):
        path_error(f"cannot write {local}: permission denied. Nothing was pulled.")


def screenshot_target(local):
    """Judge the file --screenshot saves to, and make the folder it needs, before the TV is opened.

    Every page here hands out `--screenshot shots/01.png` - PROMPT.md, AGENTS.md, CONTRIBUTING.md - and
    the folder that does not exist yet was fixed for them in #364. Two other shapes a destination takes
    were judged nowhere: a folder of that name (`mkdir shots` and then `--screenshot shots`, or a name
    typed with its trailing separator) reached adb_shell's pull, which writes to the name it is given,
    as an IsADirectoryError *after* the TV had run `screencap` - exit 1, the code this client keeps for a
    capture that came back empty, so the run read as a TV with nothing on screen; no PNG; and the
    capture left on the TV, because the cleanup after the pull never ran. A path through a file was the
    same shape one step earlier, out of os.makedirs, and `--screenshot ""` - what
    `--screenshot "$SHOT"` sends when SHOT was never set - was FileNotFoundError: ''. --push and --pull
    have been judged this way since #2039; this is the flag the pages hand out most.

    A folder that is not there is still made rather than refused: that is the one shape the command is
    expected to fix for itself.
    """
    if not local:
        usage_error("--screenshot needs the file to save to")
    if os.path.isdir(local):
        path_error(f"{local} is a folder, and --screenshot saves the PNG to a file: give a file name, such "
                   f"as {os.path.join(local, '01-home.png')}. Nothing was captured.")
    folder = os.path.dirname(os.path.abspath(local))
    if os.path.exists(folder) and not os.path.isdir(folder):
        path_error(f"{folder} is a file, not a folder: --screenshot cannot save {local} inside it. "
                   f"Nothing was captured.")
    try:
        os.makedirs(folder, exist_ok=True)
    except OSError as e:
        path_error(f"cannot create the folder {folder} for {local}: {e.strerror or e}. Nothing was captured.")
    if not os.access(folder, os.W_OK):
        path_error(f"cannot write {local}: {folder} is not writable. Nothing was captured.")
    if os.path.exists(local) and not os.access(local, os.W_OK):
        path_error(f"cannot write {local}: permission denied. Nothing was captured.")


def screenshot_args(rest):
    """`<local.png> [KEY ...] [--wait SECONDS]` into (local, keys, wait), or a usage error before connecting.

    KEY is an Android key name without the KEYCODE_ prefix (HOME, DPAD_RIGHT), a full name (KEYCODE_BACK)
    or a numeric keycode (83). The screenshot is taken --wait seconds after the last key (default 1). With no
    keys, --wait still pauses before the capture, so a screen that is still loading can settle; without it,
    no keys means no pause."""
    local, keys, wait = None, [], None
    i = 0
    while i < len(rest):
        arg = rest[i]
        if arg == "--wait":
            if i + 1 == len(rest):
                usage_error("--wait needs a number of seconds")
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
    screenshot_target(local)
    if wait is None:
        wait = 1.0 if keys else 0.0
    return local, keys, wait


def usage_error(message):
    sys.stderr.write(f"{message}\n{USAGE}")
    sys.exit(2)


def path_error(message):
    """Refuse a path that cannot be used, before anything is sent. Exit 2, no usage block.

    The message already says which path and why; the usage block would only repeat two lines the
    caller did type correctly."""
    sys.stderr.write(f"{message}\n")
    sys.exit(2)


def keyevent(name):
    """The `input keyevent` argument for a key name or keycode that already matched KEY_NAME."""
    return name if name.isdigit() or name.startswith("KEYCODE_") else "KEYCODE_" + name


def screenshot(dev, local, keys=(), wait=0.0):
    """Press `keys`, then capture the TV screen into `local`. Returns 0, or 1 when the capture failed or
    came back empty.

    `local` and the folder it needs are judged - and the folder made - by screenshot_target() before the
    TV is opened: agents pass shots/01-home.png before that folder exists (#364), and a destination that
    cannot be written has to be answered before a capture is run for it."""
    for i, key in enumerate(keys):
        if i:
            time.sleep(KEY_GAP_S)
        dev.shell(f"input keyevent {keyevent(key)}")
    if wait:
        time.sleep(wait)
    # The exit status tells a capture that failed (no file to pull) from one that came back empty: with
    # Netflix in front a TCL C755 exits 1 and leaves a 0-byte file, which is the empty case below.
    said = dev.shell(f"rm -f {SHOT_REMOTE}; screencap -p {SHOT_REMOTE}; echo rc=$?", read_timeout_s=120.0)
    rc = next((int(line.strip()[3:]) for line in said.splitlines() if re.fullmatch(r"rc=-?\d+", line.strip())), None)
    if rc not in (None, 0, 1):
        other = " ".join(line.strip() for line in said.splitlines() if line.strip() and not line.strip().startswith("rc="))
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
    try:
        dev = connect()
    except Exception as e:
        why = connect_error(e)
        if why is None:
            raise
        print(why, file=sys.stderr)
        return 2
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

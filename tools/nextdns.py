#!/usr/bin/env python3
"""Point the TV's Private DNS at a NextDNS profile, check it, and put it back.

    nextdns.py <profile id> [--name <device name>]
                                    send every DNS lookup the TV makes to that NextDNS profile
    nextdns.py --status             print the TV's Private DNS mode and host
    nextdns.py --off                put back what the TV had before the first run

The profile id is the six characters NextDNS shows under Setup (`abc123`). `--name` labels the TV in
the profile's Logs and Analytics (`--name "Living room"` shows as "Living room").

Why Private DNS and not the DNS fields under the Wi-Fi settings: a static IP with only DNS 1 filled in
gets 8.8.8.8 as DNS 2 on Fire OS, and plain DNS to NextDNS's addresses only carries the profile when
the IP is linked. Private DNS (DNS over TLS, Android 9 and newer; Fire OS 7 and 8) carries the profile
in the host name and, in `hostname` mode, never falls back to another resolver. Android TV has no
Settings page for it, so ADB is the way in, as docs/faq.md says.

What the profile then blocks is set on my.nextdns.io, not here: the blocklists, and the YouTube ad
option NextDNS was rolling out as a beta in November 2026, apply to the TV as soon as its lookups go
to the profile. The same caveats as AdGuard's resolver apply: the resolver sees every lookup the TV
makes, Private DNS goes around a Pi-hole or AdGuard Home on the router, and the TV reports "No
internet" while the resolver cannot be reached or its clock is wrong (`auto_time` off, or a box with
no battery-backed clock after a power cut). `--off` is the undo for all of those.

The first run records the TV's own `private_dns_mode` and `private_dns_specifier` in
~/.cleanyourtv/private-dns-<tv>.json before it changes them; later runs keep that first record, so
`--off` always returns to the TV's own values. With no record, `--off` sets `opportunistic`, the undo
docs/faq.md gives.

Exit status: 0 done (or the status printed), 1 it reached the TV and then failed (the TV is older than
Android 9, the values did not stick, or the network did not validate with NextDNS), 2 the run never got
as far as the TV: an argument was refused, `tools/tv.py` could not be loaded, `TV_HOST` is not set, or
the TV could not be reached. Why it stopped goes to stderr.
"""
import json
import os
import re
import sys
import time

USAGE = ("usage: nextdns.py <profile id> [--name <device name>]\n"
         "       nextdns.py --status\n"
         "       nextdns.py --off\n")

# NextDNS profile ids are six lowercase letters and digits.
PROFILE_RE = re.compile(r"[a-z0-9]{6}")
# A device name ends up in a DNS label: letters, digits, hyphens and spaces (NextDNS reads a double
# hyphen as a space). Nothing else, since the value is also passed through the TV's shell.
NAME_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9 -]*")
PRIVATE_DNS_SDK = 28            # Android 9, where Private DNS arrived
KEYS = ("private_dns_mode", "private_dns_specifier")
VALIDATE_SECONDS = 20


def usage_error(message):
    sys.stderr.write(f"{message}\n{USAGE}")
    raise SystemExit(2)


def parse_args(args):
    """('on', host) / ('status', None) / ('off', None), judged before TV_HOST is read."""
    if args in (["--status"], ["--off"]):
        return args[0][2:], None
    if not args or args[0].startswith("-"):
        usage_error("give a NextDNS profile id, --status or --off")
    profile = args[0].strip().lower()
    if not PROFILE_RE.fullmatch(profile):
        usage_error(f"not a NextDNS profile id: {args[0]!r} (six letters and digits, as under Setup)")
    rest = args[1:]
    name = None
    if rest:
        if len(rest) != 2 or rest[0] != "--name":
            usage_error("only --name <device name> can follow the profile id")
        name = rest[1].strip()
        if not NAME_RE.fullmatch(name):
            usage_error(f"device name {rest[1]!r}: use letters, digits, spaces and hyphens only")
    return "on", hostname(profile, name)


def hostname(profile, name=None):
    """The DNS-over-TLS host for a profile, with the device name NextDNS reads from the first label."""
    if not name:
        return f"{profile}.dns.nextdns.io"
    label = re.sub(r" +", "--", name)
    label = f"{label}-{profile}"
    if len(label) > 63:
        usage_error("device name too long: keep it under 50 characters")
    return f"{label}.dns.nextdns.io"


def shell(dev, command):
    return (dev.shell(command, read_timeout_s=60.0) or "").strip()


def connect():
    # Imported here so --help and a refused argument need neither the venv nor a TV.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        import tv
    except SystemExit as exit_request:
        # tv.py exits at import time with no TV_HOST or a bad TV_PORT; that is a run that never got
        # as far as the TV, which this script answers with 2, as privacy.py and recovery.py do.
        if not exit_request.code:
            raise
        if isinstance(exit_request.code, str):
            sys.stderr.write(exit_request.code + "\n")
        sys.stderr.write("Nothing was asked of the TV.\n")
        raise SystemExit(2)
    except ImportError as e:
        sys.stderr.write(f"cannot load tools/tv.py ({e}).\n"
                         "run: python3 -m venv tools/venv && "
                         "tools/venv/bin/pip install -r tools/requirements.txt\n"
                         "Nothing was asked of the TV.\n")
        raise SystemExit(2)
    try:
        return tv.connect()
    except Exception as e:
        sys.stderr.write(f"cannot reach the TV at {os.environ.get('TV_HOST', 'TV_HOST')}: {e}\n"
                         "Check TV_HOST and that ADB debugging is on (tools/tv.py --probe).\n"
                         "Nothing was asked of the TV.\n")
        raise SystemExit(2)


def read_values(dev):
    values = {}
    for key in KEYS:
        value = shell(dev, f"settings get global {key}")
        values[key] = None if value in ("", "null") else value
    return values


def record_path():
    host = re.sub(r"[^A-Za-z0-9.-]", "_", os.environ.get("TV_HOST", "tv"))
    return os.path.join(os.path.expanduser("~"), ".cleanyourtv", f"private-dns-{host}.json")


def save_record(values):
    """Keep the TV's own values from the first run; a second run must not overwrite them with ours."""
    path = record_path()
    if os.path.exists(path):
        return path, False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(values, f, indent=2)
        f.write("\n")
    return path, True


def put(dev, key, value):
    if value is None:
        shell(dev, f"settings delete global {key}")
    else:
        shell(dev, f"settings put global {key} {value}")


def validated(dev):
    """True once the default network is VALIDATED again, polling for up to VALIDATE_SECONDS."""
    deadline = time.monotonic() + VALIDATE_SECONDS
    while True:
        caps = shell(dev, "dumpsys connectivity | grep -m 3 -E 'NetworkAgentInfo|Capabilities'")
        if "VALIDATED" in caps:
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(2)


def print_status(values):
    mode = values["private_dns_mode"] or "not set (the TV's default, opportunistic)"
    print(f"private_dns_mode:      {mode}")
    print(f"private_dns_specifier: {values['private_dns_specifier'] or 'not set'}")
    host = values["private_dns_specifier"] or ""
    if values["private_dns_mode"] == "hostname" and host.endswith(".dns.nextdns.io"):
        print("Every DNS lookup goes to NextDNS, with no fallback to another resolver.")
    elif values["private_dns_mode"] == "hostname":
        print("Every DNS lookup goes to that host, which is not NextDNS.")
    else:
        print("Lookups use the network's DNS, so a static 8.8.8.8 or the router's resolver still applies.")


def turn_on(dev, host):
    sdk = shell(dev, "getprop ro.build.version.sdk")
    if not sdk.isdigit() or int(sdk) < PRIVATE_DNS_SDK:
        sys.stderr.write(
            f"this TV reports SDK {sdk or 'unknown'}; Private DNS needs Android 9 (SDK 28) or newer, "
            "and Fire OS 6 is Android 7.\n"
            "On such a TV set a static IP under the Wi-Fi advanced settings with BOTH DNS fields set to "
            "the NextDNS addresses from your profile's Setup page, and turn on Linked IP there.\n"
            "Nothing was changed.\n")
        raise SystemExit(1)
    before = read_values(dev)
    path, fresh = save_record(before)
    print(f"{'Recorded' if fresh else 'Kept the earlier record of'} the TV's own values in {path}")
    put(dev, "private_dns_specifier", host)
    put(dev, "private_dns_mode", "hostname")
    after = read_values(dev)
    if after["private_dns_mode"] != "hostname" or after["private_dns_specifier"] != host:
        sys.stderr.write("the TV did not keep the new values:\n")
        print_status(after)
        sys.stderr.write("Undo with: tools/venv/bin/python -I tools/nextdns.py --off\n")
        raise SystemExit(1)
    print(f"Private DNS: {host} (mode hostname)")
    if shell(dev, "settings get global auto_time") == "0":
        print("Warning: auto_time is off. If the clock drifts, DNS over TLS rejects the certificate and "
              "the TV shows \"No internet\"; `settings put global auto_time 1` avoids that.")
    if not validated(dev):
        sys.stderr.write(
            f"the network did not validate within {VALIDATE_SECONDS} s, so the TV may show \"No internet\".\n"
            "Check the profile id, then undo with: tools/venv/bin/python -I tools/nextdns.py --off\n")
        raise SystemExit(1)
    print("The network validated through NextDNS. Open an app on the TV, then look for its lookups "
          "under Logs on my.nextdns.io.")


def turn_off(dev):
    path = record_path()
    if os.path.exists(path):
        with open(path) as f:
            before = json.load(f)
        for key in ("private_dns_mode", "private_dns_specifier"):
            put(dev, key, before.get(key))
        os.remove(path)
        print(f"Put back the TV's own Private DNS values from {path}")
    else:
        put(dev, "private_dns_mode", "opportunistic")
        put(dev, "private_dns_specifier", None)
        print("No record of the TV's own values, so Private DNS is back to opportunistic, the default.")
    print_status(read_values(dev))


def main(args):
    if args[:1] in (["-h"], ["--help"]):
        print((__doc__ or "").strip())
        return 0
    action, host = parse_args(args)
    dev = connect()
    try:
        if action == "status":
            print_status(read_values(dev))
        elif action == "off":
            turn_off(dev)
        else:
            turn_on(dev, host)
    finally:
        close = getattr(dev, "close", None)
        if close:
            close()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

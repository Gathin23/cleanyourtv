#!/usr/bin/env python3
"""Restart Home after a theme push and verify its previously enabled key helpers (#994).

Used by theme.sh and theme.ps1. No theme files or app configuration are changed here.

Exit status: 0 Home is up and the key helpers it had are bound again; 1 the TV ran the restart and it failed
(a command it refused, or a helper that did not stay bound); 2 nothing was asked of the TV - tools/venv is
missing, TV_HOST is unset, or the TV did not answer. The three shapes in 2 are the ones tv.py words, so they
name the port, the pair-once step and the "not an Android TV" way out, as bench.py, trace.py and manage.py do.
"""
import os
import re
import shlex
import sys
import time

PACKAGE = "com.gathin.tvhome"
SERVICES = "enabled_accessibility_services"
FLAG = "accessibility_enabled"
ACTION = "android.accessibilityservice.AccessibilityService"


def shell(dev, command):
    # adb_shell's shell transport does not return the remote exit code. Require a
    # final marker, so a failed settings/start command cannot look like success.
    output = dev.shell(command + "; rc=$?; printf '\\n__CYT_THEME_RC__%s\\n' \"$rc\"",
                       read_timeout_s=15)
    result = re.search(r"\n__CYT_THEME_RC__(\d+)\s*$", output)
    if not result or result[1] != "0":
        raise RuntimeError("TV command failed or returned an incomplete response")
    text = output[:result.start()].strip()
    if re.search(r"(?mi)^\s*(Error:|Permission Denial:|Security exception:)", text):
        raise RuntimeError("TV refused the command")
    return text


def components(value):
    if value in ("", "null"):
        return {}
    found = {}
    for item in value.split(":"):
        if not re.fullmatch(r"[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+", item):
            raise RuntimeError("Could not read the accessibility service list")
        package, name = item.split("/", 1)
        full = package + "/" + (package + name if name.startswith(".") else name)
        found.setdefault(full, item)
    return found


def bound_services(output, user):
    """Read completed system-server bindings, by component, without localized labels.

    Accessibility's own dump uses Service[label=...] on Android 8/11/14. Activity
    Manager instead identifies the component, current-user instance, received
    binder and system-server client. A started/running service alone is not bound.
    """
    found = set()
    for block in re.split(r"(?m)^\s*\* ServiceRecord\{", output)[1:]:
        header = re.match(r"[^\n]*?\bu(\d+) ([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)\}", block)
        if not header or int(header[1]) != user:
            continue
        if not re.search(r"(?m)^\s*app=ProcessRecord\{", block):
            continue
        for binding in re.split(r"(?m)^\s*\* IntentBindRecord\{", block)[1:]:
            # All Connections is a different section; do not borrow a client from it.
            binding = binding.split("All Connections:", 1)[0]
            intent = re.search(r"(?m)^\s*intent=\{([^}\n]*)}", binding)
            component = re.search(r"\bcmp=([A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+)", intent[1]) if intent else None
            action = re.search(r"\bact=(\S+)", intent[1]) if intent else None
            if (component and components(component[1]).keys() == components(header[2]).keys()
                    and (not action or action[1] == ACTION)
                    and re.search(r"(?m)^\s*binder=(?!null(?:\s|$))\S+", binding)
                    and re.search(r"\brequested=true received=true hasBound=true\b", binding)
                    and re.search(r"Client AppBindRecord\{[^\n]*ProcessRecord\{[^}\n]*:system/1000}", binding)):
                found.update(components(header[2]))
                break
    return found


def settings_command(user, services, flag):
    value = shlex.quote(":".join(services.values()))
    return (f"settings --user {user} put secure {SERVICES} {value} && "
            f"settings --user {user} put secure {FLAG} {flag}")


def cycle(dev, user, saved):
    # Keep the off interval and restoration on the TV. A host disconnect during
    # a host-side sleep must not strand every accessibility service switched off.
    restore = settings_command(user, saved, 1)
    off = settings_command(user, {}, 0)
    shell(dev, f"cyt_restore() {{ {restore}; }}; trap cyt_restore EXIT; "
               f"trap 'cyt_restore; exit 1' HUP INT TERM; {off} && sleep 2; "
               "cyt_rc=$?; cyt_restore; cyt_restore_rc=$?; trap - EXIT HUP INT TERM; "
               '[ "$cyt_rc" -eq 0 ] && [ "$cyt_restore_rc" -eq 0 ]')


def restart(dev, pause=time.sleep):
    user_text = shell(dev, "am get-current-user")
    if not user_text.isdecimal():
        raise RuntimeError("Could not identify the current Android user")
    user = int(user_text)
    get = lambda key: shell(dev, f"settings --user {user} get secure {key}")
    saved = components(get(SERVICES))
    enabled = get(FLAG)
    if enabled not in ("0", "1", "null"):
        raise RuntimeError("Could not read the accessibility enabled flag")
    helpers = {component for component in saved if component.split("/", 1)[0] == PACKAGE}
    home = shell(dev, "cmd package resolve-activity --brief --user " + str(user)
                 + " -a android.intent.action.MAIN -c android.intent.category.HOME")
    def write(services, flag):
        shell(dev, settings_command(user, services, flag))

    try:
        shell(dev, f"am force-stop --user {user} {PACKAGE}")
        if home.splitlines()[-1:] and home.splitlines()[-1].startswith(PACKAGE + "/"):
            start = "-a android.intent.action.MAIN -c android.intent.category.HOME"
        else:
            start = "-n " + PACKAGE + "/.MainActivity"
        shell(dev, f"am start --user {user} {start}")
        if not helpers:
            return

        if enabled != "1":
            # An intentionally disabled accessibility subsystem stays disabled.
            write(saved, 0)
            return

        pause(1)  # Android removes force-stopped services asynchronously.
        for attempt in range(3):
            # Keep the original list even if Android has now dropped our helpers. Also
            # retain any newly observed service belonging to another app.
            saved.update(components(get(SERVICES)))
            write(saved, 1)
            pause(1)  # Xiaomi needs a real list change before the clear (#726).
            cycle(dev, user, saved)  # TCL G05 needs a two-second off interval (#994).
            stable = 0
            for _ in range(7):
                pause(1)
                current = components(get(SERVICES))
                saved.update(current)
                flag = get(FLAG)
                bound = bound_services(shell(dev, f"dumpsys activity services {PACKAGE}"), user)
                stable = stable + 1 if saved.keys() <= current.keys() and flag == "1" and helpers <= bound else 0
                # Three readings span two seconds; a first successful bind alone does
                # not catch the later removal reported on Chromecast (#994).
                if stable == 3:
                    return
        # Leave the original list restored even when binding could not be verified.
        write(saved, 1)
        raise RuntimeError("Home restarted, but key helpers did not stay bound; check Accessibility settings")
    except BaseException:
        # A failed start/probe or Ctrl-C must not leave the force-stopped helpers
        # off the list. If the transport is gone this fails explicitly as well.
        if helpers:
            write(saved, 1 if enabled == "1" else 0)
        raise


def main():
    # Both callers use Python -I, which excludes the script directory from sys.path.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    dev = None
    try:
        # The three shapes that never ask the TV anything are answered here, before connect(): a checkout
        # without tools/venv (no adb_shell), a shell with no TV_HOST (tv.py exits at import), and a TV that
        # does not answer. Each used to end in "Theme restart failed: ..." with exit 1 - the status this
        # script keeps for a restart it sent and that failed - so a set that was merely asleep read as a
        # theme that was pushed and would not restart. bench.py (#2559), trace.py (#2683) and manage.py
        # (#2692) answer the same shapes with 2 and say what they did not do.
        try:
            import tv as tv_client
            connect, connect_error = tv_client.connect, tv_client.connect_error
        except ImportError as error:
            print(f"cannot load tools/tv.py ({error}): run this with tools/venv/bin/python, as "
                  "tools/theme.sh does. Nothing was restarted.", file=sys.stderr)
            return 2
        except SystemExit as exit_request:
            # tv.py exits at import time with its own "Set TV_HOST to your TV's IP address" text, which only
            # the interpreter would print for that exit: written here, so catching it does not hide it. A
            # module that exits 0 answered the call itself (its --help), and that status stands.
            if not exit_request.code:
                raise
            if isinstance(exit_request.code, str):
                print(exit_request.code, file=sys.stderr)
            print("Nothing was restarted: Home was not asked to start again.", file=sys.stderr)
            return 2
        try:
            dev = connect()
        except Exception as error:
            # A refused port, a Wireless debugging port that asks for TLS, a TV behind a network that does
            # not route: tv.py words those, and they carry the pair-once advice and the "not an Android TV"
            # way out that "Connection refused" alone does not. An error it has nothing to add to (an ADB
            # key it cannot read) keeps this script's own line and status.
            why = connect_error(error)
            if why is None:
                raise
            print(f"{why}\nNothing was restarted: Home was not asked to start again.", file=sys.stderr)
            return 2
        restart(dev)
    except Exception as error:
        print(f"Theme restart failed: {error}", file=sys.stderr)
        return 1
    finally:
        if dev is not None:
            dev.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""What this TV runs for voice, ACR and telemetry, with the evidence for each name (#444).

    privacy.py                      audit the connected TV and print the report
    privacy.py --write <file>       write the candidates as a debloat list for tools/disable.sh
    privacy.py --write <file> --include-low
                                    also the packages that only matched by name

`--include-low` is read by the list `--write` writes and by nothing else, so on its own it is refused
with this usage rather than accepted and ignored: the report prints the name matches either way, and a
run that asked for them read as if it had got them.

The honest part is the evidence, not the category. On firmware where most packages run under
`android.uid.system`, about fifty of them "have" RECORD_AUDIO and none of them record anything;
a per-package permission scan would call all fifty listeners. So this audit says which names the
firmware itself nominated, which ones are known components with a known job, which ones only matched
by name, and which ones have microphone access in the returned AppOps history. It never claims a
package is listening.

Disabling is left to `tools/disable.sh`, so the undo path is the one every list already uses: the
record written before the first change, replayed by `tools/restore.sh`.
"""
import os
import re
import sys

# Names from the TV end up inside commands run by its shell, so nothing that could carry shell syntax
# goes through. Same rule as tools/recovery.py.
PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")

USAGE = ("usage: privacy.py                       audit the connected TV\n"
         "       privacy.py --write <file> [--include-low]\n"
         "                                    write the candidates as a debloat list\n")

# What we know about specific packages, from reports in examples/ and docs/ rather than from the name.
# `category` drives the section in the report; `note` is what the owner reads before disabling it.
CATALOGUE = {
    "com.google.android.katniss": (
        "voice",
        "Google Assistant and voice search; the remote's mic button and voice search inside apps go "
        "dead without it",
    ),
    "com.google.android.speech.pumpkin": (
        "voice",
        "offline hotword model, unused once the assistant above is off",
    ),
    "org.droidtv.amazonalexa": (
        "voice",
        "Amazon Alexa on Philips sets; the microphone is in the remote, not the panel",
    ),
    "tv.inscape.tvclient.inscapeacr": (
        "acr",
        "Inscape ACR client: identifies what is on screen, HDMI included, from the picture, and needs "
        "only INTERNET, not RECORD_AUDIO",
    ),
    "com.skyworth.aisense": (
        "acr",
        "Coocaa/Skyworth ACR, used to target the ads pushed to the set",
    ),
    "tv.samba.ssm.ui": (
        "acr",
        "Samba TV ACR on TCL sets: identifies what is on screen to target ads; shipped enabled on the "
        "Netherlands 43P69K (examples/tcl-g10-google-tv) and a US 55Q651G",
    ),
    "org.droidtv.candeebug": (
        "telemetry",
        "\"DAC\" analytics on Philips sets",
    ),
    "org.droidtv.gel": (
        "telemetry",
        "\"GEL\" analytics; it runs inside the candeebug process, so both go together",
    ),
    "org.droidtv.usagelogger": (
        "telemetry",
        "boot stats, remote keypress counts and panel telemetry",
    ),
    "org.droidtv.tv.frameworklogger": (
        "telemetry",
        "framework and reboot logging",
    ),
    "com.tcl.logkit": (
        "telemetry",
        "TCL log capture and upload; disabled in most TCL lists in examples/",
    ),
    "com.google.android.feedback": (
        "telemetry",
        "feedback reports to Google",
    ),
    "com.android.tv.feedbackconsent": (
        "telemetry",
        "Android TV's consent screen for feedback reports: once the owner agrees, it collects the bug "
        "report and system logs for the app that asked; disabled with com.google.android.feedback in "
        "about one in five lists in examples/",
    ),
    "com.google.android.tv.remotecontrol.logging": (
        "telemetry",
        "remote usage logging, as the Chromecast HD and 4K and Google TV Streamer lists call it; "
        "a boot receiver with Bluetooth permissions and no INTERNET permission of its own",
    ),
    "com.google.android.apps.tv.netoscope": (
        "telemetry",
        "Google TV network diagnostics: it can read dumpsys (DUMP) and holds INTERNET and Wi-Fi "
        "permissions; the Chromecast with Google TV 4K lists disable it",
    ),
    "com.google.android.partnersetup": (
        "telemetry",
        "Google Partner Setup: RLZ partner-attribution pings, with a ping receiver that also runs "
        "at boot; disabled in about a third of the lists in examples/",
    ),
}

# A package found only by its name is reported apart and never written into the list by default:
# `com.example.voicecontrol` may be the panel's own audio routing, and nothing here can tell. The
# stem matches at the start of a name segment, which catches `voicecontrol` and `micd` while leaving
# `vending` or `playground` alone; the probe cap keeps the noise bounded.
NAME_HINT = re.compile(
    r"(?:^|\.)(acr|analytics|assistant|alexa|hotword|inscape|listen|microphone|privacy|speech|"
    r"telemetry|tracker|tracking|voice)",
    re.IGNORECASE,
)

VOICE_SETTINGS = ("voice_interaction_service", "voice_recognition_service", "assistant")
TITLES = (
    ("voice", "Voice assistants and wake word"),
    ("acr", "ACR (identifies what is on screen, no microphone needed)"),
    ("telemetry", "Telemetry and analytics"),
    ("unknown", "Matched by name only, nothing else known"),
)
# `dumpsys package` is slow on old firmware; a TV with fifty name matches is not worth ten minutes.
MAX_PROBES = 25
SYSTEM_UID = 1000
# The package's own UID line in `dumpsys package`: `userId=` up to Android 13, `appId=` from 14. It is
# anchored to the start of a line, so an install permission that differs for a second user, which
# ends with ", userId=10", is never read as the package's UID.
UID_RE = re.compile(r"^\s*(?:userId|appId)=(\d+)", re.M)
# Only the package's own "requested permissions" block counts, as in tools/check.py: the same dump
# prints its shared user's permissions, and on a system-UID package those include RECORD_AUDIO.
REQUESTED_RE = re.compile(r"requested permissions:(.*?)"
                          r"(?:\n\s*(?:install|runtime) permissions:|\n\s*User \d+:|$)", re.S)
# Android 5.x prints no "requested permissions" block, only the package's own "grantedPermissions:"
# at the end of its section. A package in a shared UID has none there: on 5.x its grants are the
# shared user's, printed under "Shared users:", so probe() reads no block past that header.
GRANTED_RE = re.compile(r"\n {4}grantedPermissions:\n((?: {6}\S.*(?:\n|$))*)")


def shell(dev, command):
    return (dev.shell(command, read_timeout_s=120.0) or "").strip()


def connect():
    # Imported here so the module can be imported (tests, --help) without adb_shell installed.
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        import tv
    except ImportError as e:
        raise SystemExit(f"cannot load tools/tv.py ({e}).\nrun: python3 -m venv tools/venv && "
                         "tools/venv/bin/pip install -r tools/requirements.txt")
    try:
        return tv.connect()
    except Exception as e:
        raise SystemExit(f"cannot reach the TV at {os.environ.get('TV_HOST', 'TV_HOST')}: {e}\n"
                         "Check TV_HOST and that USB debugging is still on (tools/tv.py --probe).")


def query_packages(dev):
    # User 0's packages, read the way tools/recovery.py reads them and for the same reason: without
    # --user, Android 14's pm merges the answers of every user on the TV, so a package that only a
    # second user has disabled would be reported as disabled here, and --write would leave it out of
    # the list as already off. The plain queries are only the fallback for a pm that answers
    # --user 0 with no package lines at all, never for an empty -d.
    scope = " --user 0"
    answer = shell(dev, "pm list packages --user 0")
    if not any(line.startswith("package:") for line in answer.splitlines()):
        scope = ""
        answer = shell(dev, "pm list packages")
    installed = []
    for line in answer.splitlines():
        if not line.startswith("package:"):
            continue
        name = line.split(":", 1)[1].strip()
        if PACKAGE_RE.fullmatch(name) and name not in installed:
            installed.append(name)
    if not installed:
        raise SystemExit("the TV returned no packages, so there is nothing to audit. Check the "
                         "connection (tools/tv.py --probe) and try again.")
    disabled = {line.split(":", 1)[1].strip()
                for line in shell(dev, f"pm list packages -d{scope}").splitlines()
                if line.startswith("package:")}
    return installed, disabled


def voice_setting_owners(dev):
    """package name -> the secure settings that name it (the firmware's own pick for the assistant)."""
    owners = {}
    for key in VOICE_SETTINGS:
        value = shell(dev, f"settings get secure {key}")
        if not value or value == "null" or "/" not in value:
            continue
        package = value.split("/", 1)[0].strip()
        if PACKAGE_RE.fullmatch(package):
            owners.setdefault(package, []).append(key)
    return owners


def probe(dev, name):
    """What the package declares about the microphone, and its returned AppOps history."""
    dump = shell(dev, f"dumpsys package {name}")
    uid = None
    declared = None
    if dump:
        found = UID_RE.search(dump)
        uid = int(found.group(1)) if found else None
        own = dump.split("\nShared users:", 1)[0]
        block = REQUESTED_RE.search(own) or GRANTED_RE.search(own)
        declared = bool(block and "android.permission.RECORD_AUDIO" in block.group(1))
    ops = shell(dev, f"appops get {name} RECORD_AUDIO")
    return {"uid": uid, "declared": declared, "appops": ops}


def used_microphone(ops):
    """True for recorded access, False for no history, None for unknown output."""
    if not ops or "error" in ops.lower() or "exception" in ops.lower():
        return None
    lines = [line.strip() for line in ops.splitlines()]
    for line in lines:
        if not line.startswith("RECORD_AUDIO: "):
            continue
        # A mode (including a UID override) or rejectTime is not evidence of access.
        # AppOps prints time only for a recorded access, using TimeUtils.formatDuration.
        if line.endswith(" (running)") or re.search(
                r"; time=(?:0|[+-](?:\d+[dhms])*\d+ms) ago(?:;| \(running\)|$)", line):
            return True
    return False if "No operations." in lines else None


def classify(name, owners, probed):
    """(category, note, evidence lines) for one package."""
    category, note = CATALOGUE.get(name, ("unknown", "matched by name only"))
    evidence = []
    if name in owners:
        # The firmware nominated it, which outranks both the catalogue and the name.
        category = "voice" if category == "unknown" else category
        if name not in CATALOGUE:
            note = "the TV's own speech service"
        evidence.append(f"named by the TV: {', '.join(owners[name])}")

    uid = probed.get("uid")
    if uid == SYSTEM_UID:
        # About fifty packages on such firmware show RECORD_AUDIO granted because they share the
        # system UID; listing that per package is how a permission scan ends up crying wolf.
        evidence.append("shares the system UID (uid 1000): the permissions it shows are the system's, "
                        "not evidence that it records")
    elif probed.get("declared"):
        evidence.append("declares RECORD_AUDIO")

    used = used_microphone(probed.get("appops", ""))
    if used is False:
        evidence.append("appops: no RECORD_AUDIO history returned")
    elif used is True:
        evidence.append("appops: recorded RECORD_AUDIO access in the returned history "
                        "(read in full with `appops get <package> RECORD_AUDIO`)")
    else:
        evidence.append("appops: RECORD_AUDIO access is unknown from this output")
    return category, note, evidence


def audit(dev):
    installed, disabled = query_packages(dev)
    owners = voice_setting_owners(dev)

    candidates = [name for name in installed if name in CATALOGUE or name in owners]
    hinted = [name for name in installed if name not in candidates and NAME_HINT.search(name)]
    probes = (candidates + hinted)[:MAX_PROBES]

    entries = []
    for name in probes:
        category, note, evidence = classify(name, owners, probe(dev, name))
        entries.append({
            "name": name,
            "category": category,
            "state": "disabled" if name in disabled else "enabled",
            "note": note,
            "evidence": evidence,
        })
    return {
        "installed": installed,
        "disabled": disabled,
        "entries": entries,
        "skipped": max(0, len(candidates) + len(hinted) - len(probes)),
    }


def sections(entries):
    for category, title in TITLES:
        group = [e for e in entries if e["category"] == category]
        if group:
            yield title, group


def print_report(result):
    installed = len(result["installed"])
    enabled = installed - len(result["disabled"])
    print(f"Audit of the TV at {os.environ.get('TV_HOST', 'TV_HOST')}: {installed} packages, "
          f"{enabled} enabled.\n")
    for title, group in sections(result["entries"]):
        print(title)
        for entry in group:
            print(f"  {entry['state']:<9} {entry['name']}")
            print(f"            {entry['note']}")
            for line in entry["evidence"]:
                print(f"            {line}")
        print()
    if not result["entries"]:
        print("No voice assistant, ACR client or telemetry package was found by name on this TV.")
        print("That is not a clean bill of health: it only means nothing here recognised the names.")
        print()
    if result["skipped"]:
        print(f"{result['skipped']} more name matches were not probed (the audit stops at "
              f"{MAX_PROBES}); check them by hand with `dumpsys package <name>`.")
        print()
    print("What to do with it")
    print("  TV_HOST=<tv ip> tools/venv/bin/python -I tools/privacy.py --write privacy.txt   "
          "# the voice, ACR and telemetry rows above")
    print("  TV_HOST=<tv ip> tools/disable.sh privacy.txt   # applies it, records the undo first")
    print("  TV_HOST=<tv ip> tools/restore.sh privacy.txt   # undo")
    print()
    print("What this audit cannot tell you: whether audio ever leaves the TV. `appops` shows the op "
          "was touched, not what was done with the stream, and on many sets the microphone is in the "
          "remote rather than the panel. Nothing on this list is proven to be recording, and nothing "
          "here proves it is not.")


def list_lines(result, include_low, name):
    """The debloat list for the candidates the audit actually identified, as the file `name`."""
    keep = [e for e in result["entries"]
            if e["category"] != "unknown" or include_low]
    if not keep:
        raise SystemExit("nothing identified to write: only name matches were found. Run the audit "
                         "again to read them, or pass --include-low to write those too.")
    # Already off: writing it would only make restore.sh re-enable what the owner disabled on
    # purpose. Dropped before the check, so a TV with every candidate already off gets no file: a
    # list of headings alone is one tools/disable.sh refuses, not "wrote 0 packages".
    keep = [e for e in keep if e["state"] != "disabled"]
    if not keep:
        raise SystemExit("nothing to write: every package this list would name is already disabled "
                         "on the TV. Run the audit again to read them.")
    # The file outlives this run and may be shared (docs/workflows/tv-list.md), so the header uses
    # the placeholder every list uses rather than the TV's address.
    lines = [
        "# Privacy audit written by tools/privacy.py.",
        "# Voice, ACR and telemetry candidates this TV answered for. Everything is DISABLED, not",
        "# uninstalled, and the undo is the one every list uses:",
        f"#   TV_HOST=<tv ip> tools/disable.sh {name}",
        f"#   TV_HOST=<tv ip> tools/restore.sh {name}",
        "#",
        "# Each line says what the package is and why it is here. Nothing below is proven to record",
        "# anything: the audit reports what the firmware and the packages declare.",
    ]
    if include_low and result["skipped"]:
        # The report counts the name matches the probe cap left out; a list of name matches that
        # does not would look complete.
        lines += [
            "#",
            f"# {result['skipped']} more name matches were not probed (the audit stops at "
            f"{MAX_PROBES}) and are not in this list;",
            "# check them by hand with `dumpsys package <name>`.",
        ]
    for title, group in sections(keep):
        lines.append("")
        lines.append(f"# {title}")
        for entry in group:
            why = entry["note"]
            if entry["evidence"]:
                why += f" ({entry['evidence'][0]})"
            lines.append(f"{entry['name']:<45} # {why}")
    return lines


def target_problem(path):
    """Why --write cannot write the file `path`, or None."""
    if os.path.isdir(path):
        # The argument is a folder: `--write lists/`, or `--write` with a name that happens to be a
        # directory. Saying it "already exists" answers a question the owner did not ask, and letting
        # it through is an IsADirectoryError after the whole audit.
        return (f"{path} is a folder, and --write takes a file name to create, "
                f"e.g. {os.path.join(path, 'privacy.txt')}.")
    if os.path.exists(path):
        # A second audit of the same TV may legitimately differ, but silently replacing a list the
        # owner is about to apply is how a hand edit disappears.
        return f"{path} already exists; move it aside or choose another name."
    folder = os.path.dirname(path) or "."
    if not os.path.isdir(folder):
        return f"there is no folder {folder} for {path}; create it or choose another name."
    return None


def write_list(path, result, include_low):
    # main() checked before the audit too; this is for a file or folder that changed while it ran.
    problem = target_problem(path)
    if problem:
        raise SystemExit(problem)
    # The header names the file as it was typed, like the commands printed below; an absolute path
    # is cut to the file name, which keeps the owner's home folder out of the list.
    lines = list_lines(result, include_low, os.path.basename(path) if os.path.isabs(path) else path)
    text = "\n".join(lines) + "\n"
    try:
        with open(path, "w") as f:
            f.write(text)
    except OSError as error:
        # The audit is two shell calls for each package it probed, slow on old firmware: a file the
        # folder would not take should not cost the owner a second run of it. What was to be written
        # goes to stdout, so it can be saved by hand.
        sys.stdout.write(text)
        raise SystemExit(f"the audit finished, but {path} could not be written: "
                         f"{error.strerror or error}\n"
                         "the list is printed above: copy it into a file you can write.")
    names = sum(1 for line in lines if line and not line.startswith("#"))
    print(f"wrote {names} packages to {path}")
    if include_low and result["skipped"]:
        print(f"{result['skipped']} more name matches were not probed (the audit stops at "
              f"{MAX_PROBES}), so the list leaves them out; check them by hand with "
              "`dumpsys package <name>`.")
    print(f"apply:   TV_HOST={os.environ.get('TV_HOST', '<tv ip>')} tools/disable.sh {path}")
    print(f"undo:    TV_HOST={os.environ.get('TV_HOST', '<tv ip>')} tools/restore.sh {path}")
    print("read it first: the comments say what each one takes with it.")


def main():
    args = sys.argv[1:]
    include_low = "--include-low" in args
    args = [a for a in args if a != "--include-low"]
    # --include-low is only read by the list --write makes (list_lines), so `privacy.py --include-low`
    # was accepted and changed nothing: the report prints the name matches whether or not it was given,
    # and the run read as if the ones the owner asked for had been folded in. The usage line below has
    # always put the flag after --write; now the run says the same thing instead of ignoring it.
    if include_low and not args:
        sys.stderr.write(USAGE)
        sys.stderr.write("--include-low is read by the list --write writes: pass it with --write, "
                         "e.g. privacy.py --write privacy.txt --include-low\n")
        return 2
    if args and (len(args) != 2 or args[0] != "--write" or not args[1]):
        # An empty name is what `--write "$OUT"` sends when OUT was never set - the same mistake as
        # leaving the name out, and os.path checks read "" as "a file in the folder I am in", so it
        # used to come back as a FileNotFoundError after the whole audit.
        sys.stderr.write(USAGE)
        return 2

    if args:
        # Checked before connecting: the audit sends two commands for every package it probes,
        # and `dumpsys package` is slow on old firmware, all for a file that could not be written.
        problem = target_problem(args[1])
        if problem:
            raise SystemExit(f"{problem} Nothing was asked of the TV.")

    dev = connect()
    try:
        result = audit(dev)
    finally:
        dev.close()

    if args:
        write_list(args[1], result, include_low)
    else:
        print_report(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())

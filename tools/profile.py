#!/usr/bin/env python3
"""A household profile: capture one unit, check any unit against it, converge another unit to it.

    TV_HOST=<master ip>  profile.py capture <profile dir>      write apps.txt, debloat.txt, profile.json
    TV_HOST=<ip>         profile.py check <profile dir>         report how this unit differs
    TV_HOST=<target ip>  profile.py sync <profile dir> --source <master ip> [--apply]

The master is the unit the person has already set up. Its third-party packages become the profile's
installable list and its disabled packages become its debloat list, in the format tools/disable.sh
and tools/restore.sh already take. Sync only ever adds: it installs what the master has, and mirrors
the enabled/disabled state of the packages the profile names. Nothing is uninstalled, and a package
the profile does not name is reported and left alone.

Exit status, the three codes tools/check.py uses: 0 the unit matches, or the sync ran; 1 drift, or a
dry run that has work to do; 2 the run could not check at all (no TV, no profile, tv.py missing).
"""
import json
import os
import re
import subprocess
import sys
import tempfile
import time

if sys.argv[1:2] and sys.argv[1] in ("-h", "--help"):
    print((__doc__ or "").strip())
    sys.exit(0)

TOOLS = os.path.dirname(os.path.abspath(__file__))
APPS = "apps.txt"
DEBLOAT = "debloat.txt"
HEADER = "profile.json"
REMOTE_DIR = "/data/local/tmp"
PROPS = ("ro.product.model", "ro.product.device", "ro.build.version.sdk")


class Transport(Exception):
    """The TV could not be read or written: exit 2, not drift."""


def python():
    """The interpreter tools/tv.py is run with: the venv when there is one, as the shell scripts do."""
    venv = os.path.join(TOOLS, "venv", "bin", "python")
    if not os.path.exists(venv):
        venv = os.path.join(TOOLS, "venv", "Scripts", "python.exe")
    return venv if os.path.exists(venv) else sys.executable


def tv_cli(host, *args):
    """Run tools/tv.py, against `host` when one is named, and return its stdout."""
    env = dict(os.environ)
    if host:
        env["TV_HOST"] = host
    proc = subprocess.run([python(), "-I", os.path.join(TOOLS, "tv.py"), *args],
                          env=env, capture_output=True, text=True)
    if proc.returncode != 0:
        detail = (proc.stderr or proc.stdout).strip().splitlines()
        raise Transport(detail[-1] if detail else f"tv.py exited {proc.returncode}")
    return proc.stdout


def packages(text):
    return sorted(line.split(":", 1)[1].strip() for line in text.splitlines()
                  if line.startswith("package:"))


def read_tv(host=None):
    """What the unit has, what of it is third-party, what is disabled, and what it is."""
    return {
        "installed": packages(tv_cli(host, "pm list packages")),
        "third_party": packages(tv_cli(host, "pm list packages -3")),
        "disabled": packages(tv_cli(host, "pm list packages -d")),
        "props": {key: tv_cli(host, f"getprop {key}").strip() for key in PROPS},
    }


def read_list(path):
    if not os.path.exists(path):
        raise Transport(f"no {os.path.basename(path)} in {os.path.dirname(path)}")
    with open(path, encoding="utf-8") as f:
        return sorted({line.split()[0] for line in f.read().splitlines()
                       if line.strip() and not line.lstrip().startswith("#")})


def read_profile(directory):
    if not os.path.isdir(directory):
        raise Transport(f"no profile directory at {directory}")
    return read_list(os.path.join(directory, APPS)), read_list(os.path.join(directory, DEBLOAT))


def write_list(path, what, model, names):
    """The list format tools/disable.sh reads: one package per line, comments allowed."""
    header = (f"# {what} captured from {model or 'an Android TV'} on {time.strftime('%Y-%m-%d')}; "
              "tools/profile.py reads this file")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join([header, *names]) + "\n")


def capture(directory):
    tv = read_tv()
    model = tv["props"]["ro.product.model"]
    os.makedirs(directory, exist_ok=True)
    write_list(os.path.join(directory, APPS), "third-party apps", model, tv["third_party"])
    write_list(os.path.join(directory, DEBLOAT), "disabled packages", model, tv["disabled"])
    with open(os.path.join(directory, HEADER), "w", encoding="utf-8") as f:
        json.dump({
            "model": model,
            "device": tv["props"]["ro.product.device"],
            "sdk": tv["props"]["ro.build.version.sdk"],
            "captured": time.strftime("%Y-%m-%d"),
            "apps": len(tv["third_party"]),
            "disabled": len(tv["disabled"]),
        }, f, indent=2, sort_keys=True)
        f.write("\n")
    print(f"captured {directory}: {len(tv['third_party'])} third-party apps, "
          f"{len(tv['disabled'])} disabled packages")
    return 0


def drift(profile, tv):
    """[(label, packages)] for each way this unit differs from the profile."""
    apps, debloat = profile
    installed, disabled = set(tv["installed"]), set(tv["disabled"])
    return [(label, names) for label, names in (
        ("missing", [p for p in apps if p not in installed]),
        ("not in the profile", [p for p in tv["third_party"] if p not in apps]),
        ("should be disabled", [p for p in debloat if p in installed and p not in disabled]),
        ("disabled but the profile installs it", [p for p in apps if p in disabled]),
    ) if names]


def report(sections):
    for label, names in sections:
        print(f"{label} ({len(names)}): {', '.join(names)}")


def check(directory):
    profile = read_profile(directory)
    sections = drift(profile, read_tv())
    if not sections:
        print(f"the TV matches the profile: {len(profile[0])} apps, {len(profile[1])} disabled")
        return 0
    report(sections)
    print("drift: run profile.py sync with this unit as TV_HOST and the master as --source")
    return 1


def plan(profile, source, target):
    """What has to happen on the target. Only the packages the profile names, and nothing destructive."""
    apps, debloat = profile
    source_installed, source_disabled = set(source["installed"]), set(source["disabled"])
    target_installed, target_disabled = set(target["installed"]), set(target["disabled"])
    return (
        [p for p in apps if p in source_installed and p not in target_installed],
        [p for p in debloat if p in target_installed and p not in target_disabled],
        [p for p in apps if p in source_installed and p not in source_disabled and p in target_disabled],
    )


def apk_paths(host, pkg):
    return [line.split(":", 1)[1].strip() for line in tv_cli(host, f"pm path {pkg}").splitlines()
            if line.startswith("package:")]


def transfer(host, pkg, workdir):
    """Copy a package off the master and install it on TV_HOST. None means "install by hand"."""
    paths = apk_paths(host, pkg)
    if not paths:
        # installed on the master but its APK is not readable from the shell (a stub, or a package
        # whose files are not world-readable): nothing to copy.
        return None
    try:
        if len(paths) > 1:
            return install_split(host, pkg, paths, workdir)
        local = os.path.join(workdir, os.path.basename(paths[0]))
        tv_cli(host, "--pull", paths[0], local)
        remote = f"{REMOTE_DIR}/{pkg}.apk"
        tv_cli(None, "--push", local, remote)
        out = tv_cli(None, f"pm install -r {remote}")
        tv_cli(None, f"rm -f {remote}")
    except Transport as e:
        # One APK that cannot travel leaves the package manual; it must not stop the rest of the sync.
        print(f"could not transfer {pkg}: {e}", file=sys.stderr)
        return None
    return "Success" in out


def install_split(host, pkg, paths, workdir):
    """Install a split package in one session: install-create, one install-write per APK, install-commit."""
    files = []
    for number, path in enumerate(paths, 1):
        local = os.path.join(workdir, f"{number}-{os.path.basename(path)}")
        tv_cli(host, "--pull", path, local)
        files.append(local)
    created = tv_cli(None, "pm install-create -r")
    session = re.search(r"\[(\d+)\]", created)
    if not session:
        return None
    session_id = session.group(1)
    try:
        for number, local in enumerate(files, 1):
            remote = f"{REMOTE_DIR}/{pkg}-{number}.apk"
            tv_cli(None, "--push", local, remote)
            tv_cli(None, f"pm install-write -S {os.path.getsize(local)} {session_id} {number}.apk {remote}")
            tv_cli(None, f"rm -f {remote}")
        out = tv_cli(None, f"pm install-commit {session_id}")
    except Transport:
        tv_cli(None, f"pm install-abandon {session_id}")
        raise
    if "Success" not in out:
        # A commit the TV refuses leaves the session open, and Android caps how many can be open.
        tv_cli(None, f"pm install-abandon {session_id}")
        return None
    return True


def sync(directory, source_host, apply):
    profile = read_profile(directory)
    source, target = read_tv(source_host), read_tv()
    installable, disable, enable = plan(profile, source, target)
    only_here = [p for p in target["third_party"] if p not in profile[0]]
    if only_here:
        print(f"only on this unit ({len(only_here)}): {', '.join(only_here)} (left alone)")
    sections = [(label, names) for label, names in (
        ("install from the master", installable),
        ("disable", disable),
        ("enable", enable),
    ) if names]
    if not sections:
        print("nothing to change: the target already matches the master")
        return 0
    report(sections)
    if not apply:
        print("dry run: add --apply to change this unit")
        return 1
    workdir = tempfile.mkdtemp(prefix="cleanyourtv-profile-")
    manual = [pkg for pkg in installable if transfer(source_host, pkg, workdir) is None]
    if manual:
        print(f"install by hand ({len(manual)}): {', '.join(manual)} "
              f"(its APK is not readable on the master; files land in {workdir})")
    for pkg in disable:
        tv_cli(None, f"pm disable-user --user 0 {pkg}")
    for pkg in enable:
        tv_cli(None, f"pm enable --user 0 {pkg}")
    if disable:
        print("undo, one command per line on this unit: "
              + "; ".join(f"pm enable --user 0 {pkg}" for pkg in disable))
    print("the target now matches the master for every package the profile names")
    return 0


def main(argv):
    if len(argv) < 2 or argv[0] not in ("capture", "check", "sync"):
        print((__doc__ or "").strip(), file=sys.stderr)
        return 2
    command, directory, rest = argv[0], argv[1], argv[2:]
    try:
        if command == "capture":
            return capture(directory)
        if command == "check":
            return check(directory)
        if "--source" not in rest:
            print("sync needs --source <master ip>: TV_HOST is the unit that changes", file=sys.stderr)
            return 2
        return sync(directory, rest[rest.index("--source") + 1], "--apply" in rest)
    except Transport as e:
        print(f"could not read: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

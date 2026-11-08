#!/usr/bin/env python3
"""elppA setup: explicit install, content permission, Home selection and restore. Never debloats."""
import argparse
import datetime
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

PACKAGE = "io.github.sanskarixd.elppa"
COMPONENT = PACKAGE + "/com.gathin.tvhome.ElppaHomeActivity"
HOME_ARGS = ["-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"]
ROOT = Path(__file__).resolve().parent.parent
COMPONENT_RE = re.compile(r"^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$")


def parse_component(output):
    for line in reversed(output.strip().splitlines()):
        if COMPONENT_RE.fullmatch(line.strip()):
            return line.strip()
    raise RuntimeError("TV did not return a Home activity; choose a default on the TV first.")


def adb(args, *command):
    result = subprocess.run([args.adb, "-s", args.serial, *command], capture_output=True, text=True, timeout=120)
    output = result.stdout.strip()
    if result.returncode or re.search(r"(?im)^(error:|failure\s*\[|exception|security exception:|java\.lang\.SecurityException)", output):
        raise RuntimeError((result.stderr.strip() or output or "ADB command failed")[:1200])
    return output


def shell(args, *command):
    return adb(args, "shell", *command)


def home(args):
    return parse_component(shell(args, "cmd", "package", "resolve-activity", "--brief", *HOME_ARGS))


def start(args):
    result = shell(args, "am", "start", "-W", "-n", COMPONENT)
    if "Status: ok" not in result or "Error:" in result:
        raise RuntimeError("Launcher did not start; Home was not changed.\n" + result)
    return result


def backup(args):
    owner = home(args)
    if owner.startswith("android/"):
        raise RuntimeError("Choose the original launcher as the default Home on the TV first, then retry.")
    if owner.startswith(PACKAGE + "/"):
        raise RuntimeError("elppA is already Home. Keep the previous recovery file; no new recovery record was written.")
    folder = ROOT / "backup" / ("elppa-" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d-%H%M%S-%f"))
    folder.mkdir(parents=True, mode=0o700)
    for filename, command in {
        "packages.txt": ["pm", "list", "packages"], "disabled.txt": ["pm", "list", "packages", "-d"],
        "properties.txt": ["getprop"], "system.txt": ["settings", "list", "system"],
        "secure.txt": ["settings", "list", "secure"], "global.txt": ["settings", "list", "global"],
    }.items():
        path = folder / filename
        path.write_text(shell(args, *command), encoding="utf-8"); path.chmod(0o600)
    record = folder / "restore.json"
    record.write_text(json.dumps({"version": 1, "serial": args.serial, "home": owner}, indent=2), encoding="utf-8")
    record.chmod(0o600)
    return record, owner


def restore(args, record):
    data = json.loads(Path(record).read_text(encoding="utf-8"))
    owner = data.get("home", "")
    if data.get("serial") != args.serial:
        raise RuntimeError("Recovery file belongs to a different ADB endpoint. Verify the TV before using its record.")
    if not COMPONENT_RE.fullmatch(owner) or owner.startswith(PACKAGE + "/") or owner.startswith("android/"):
        raise RuntimeError("Recovery file does not name a valid original launcher.")
    shell(args, "cmd", "package", "set-home-activity", "--user", "0", owner)
    if home(args) != owner:
        raise RuntimeError("Original Home was not selected. Open the TV's default-app settings; no packages were disabled.")
    shell(args, "input", "keyevent", "KEYCODE_HOME")
    print("Original Home selected. Press the physical Home button to verify it.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb") or "adb", help="Path to Google's adb / adb.exe")
    parser.add_argument("--serial", required=True, help="Exactly the authorised endpoint shown by adb devices")
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("probe")
    install = sub.add_parser("install"); install.add_argument("--apk", type=Path, required=True)
    sub.add_parser("open"); sub.add_parser("grant-content"); sub.add_parser("make-home")
    restore_parser = sub.add_parser("restore"); restore_parser.add_argument("--record", type=Path, required=True)
    args = parser.parse_args()
    try:
        if adb(args, "get-state") != "device": raise RuntimeError("Authorise the computer on the TV first.")
        if args.action == "probe":
            for label, prop in [("Model", "ro.product.model"), ("Android", "ro.build.version.release"), ("API", "ro.build.version.sdk"), ("Build", "ro.build.display.id")]:
                print(label + ": " + shell(args, "getprop", prop))
            print("Home: " + home(args))
        elif args.action == "install":
            if not args.apk.is_file(): raise RuntimeError("APK file does not exist.")
            if int(shell(args, "getprop", "ro.build.version.sdk")) < 26: raise RuntimeError("Android 8 / API 26 is required.")
            owner = home(args); print(adb(args, "install", "-r", str(args.apk.resolve())))
            print(start(args)); print("Installed as an app. Previous Home: " + owner + ". No Home assignment or package disabling was requested.")
        elif args.action == "open": print(start(args))
        elif args.action == "grant-content":
            shell(args, "pm", "grant", PACKAGE, "android.permission.READ_TV_LISTINGS")
            print("TV-listings grant requested. Open elppA > Settings > Content diagnostics to verify access.")
        elif args.action == "make-home":
            record, owner = backup(args); print("Recovery record: " + str(record)); start(args)
            try:
                shell(args, "cmd", "package", "set-home-activity", "--user", "0", COMPONENT)
                if home(args) != COMPONENT: raise RuntimeError("Firmware did not select elppA as Home.")
                shell(args, "input", "keyevent", "KEYCODE_HOME")
            except Exception:
                restore(args, record); raise
            print("Home resolves to elppA. Original launcher remains enabled. Test the physical remote, phone, voice, inputs and reboot.")
        elif args.action == "restore": restore(args, args.record)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        parser.exit(1, str(error) + "\n")


if __name__ == "__main__": main()

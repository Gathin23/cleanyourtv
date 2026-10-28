"""tools/privacy.py on Android 14's `dumpsys package`, where the package's UID line is `appId=`.

Android 14 renamed that line from `userId=` to `appId=`, and privacy.py only looked for `userId=`. On a
Chromecast with Google TV 4K (Google TV 14) it found no UID at all, and where 14 prints ", userId=10"
for a second user it would take that 10, so it never said that a package shares the system UID. It also
took RECORD_AUDIO anywhere in the dump as the package's own request, and the dump of a package in a
shared UID ends with that shared user's permissions. A system package that never asks for the
microphone therefore read "declares RECORD_AUDIO", in the report and in the list --write leaves behind,
and so did any package whose shared UID holds the permission.

Android 5.x prints no "requested permissions" block at all, only the package's own
"grantedPermissions:", so the 5.1 dumps here check that an app with its own UID on such a TV still
declares RECORD_AUDIO and that a shared UID's grant, printed under "Shared users:", still does not count.

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is simulated.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import itertools
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_privacy_audit import FAKE_TV  # noqa: E402

from tools import privacy  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
NAME = "com.example.voicecontrol"


def dump(field, uid, requested, shared=None, name=NAME):
    """`dumpsys package <name>` in the shape AOSP prints it, trimmed to the lines the audit reads.

    `field` is "userId" (Android 13 and older) or "appId" (14 and later). With `shared`, the package
    is in that shared UID: its runtime permissions are printed only under "Shared users:", and an
    install permission that differs for a second user (id 10) ends with ", userId=10", as AOSP's
    Settings.java prints it."""
    lines = ["Packages:", f"  Package [{name}] (8fe837):", f"    {field}={uid}"]
    if shared:
        lines.append(f"    sharedUser=SharedUserSetting{{652eaa4 {shared}/{uid}}}")
    lines += [f"    pkg=Package{{1d2e3f4 {name}}}", "    requested permissions:"]
    lines += [f"      {permission}" for permission in requested]
    lines.append("    install permissions:")
    lines += [f"      {permission}: granted=true" for permission in requested
              if permission != "android.permission.RECORD_AUDIO"]
    lines.append("    User 0: ceDataInode=258587 installed=true hidden=false suspended=false "
                 "stopped=false notLaunched=false enabled=0 instant=false virtual=false")
    if not shared:
        lines.append("      runtime permissions:")
        lines += [f"        {permission}: granted=true" for permission in requested
                  if permission == "android.permission.RECORD_AUDIO"]
    else:
        lines += ["", "Shared users:", f"  SharedUser [{shared}] (652eaa4):", f"    {field}={uid}",
                  "    Packages", f"      PackageSetting{{8fe837 {name}/{uid}}}",
                  "    install permissions:",
                  "      android.permission.INTERNET: granted=true",
                  "      android.permission.MANAGE_USERS: granted=false, userId=10",
                  "    User 0: ",
                  "      runtime permissions:",
                  "        android.permission.RECORD_AUDIO: granted=true, flags=[ GRANTED_BY_DEFAULT]"]
    return "\n".join(lines) + "\n"


def dump_5(uid, granted, shared=None, name=NAME):
    """The same dump on Android 5.x, in the shape lollipop-mr1-release's Settings.java prints it.

    There is no "requested permissions" block: the package's own grants are the last lines of its
    section, under "grantedPermissions:". With `shared`, the package has no grants of its own, and
    `granted` is printed only under "Shared users:", as the shared user's."""
    lines = ["Packages:", f"  Package [{name}] (3a1b2c4):", f"    userId={uid} gids=[3003]"]
    if shared:
        lines.append(f"    sharedUser=SharedUserSetting{{652eaa4 {shared}/{uid}}}")
    lines += [f"    pkg=Package{{1d2e3f4 {name}}}",
              "    permissionsFixed=true haveGids=true installStatus=1",
              "    User 0:  installed=true hidden=false stopped=false notLaunched=false enabled=0"]
    if shared:
        lines += ["", "Shared users:", f"  SharedUser [{shared}] (652eaa4):",
                  f"    userId={uid} gids=[3003]"]
    lines.append("    grantedPermissions:")
    lines += [f"      {permission}" for permission in granted]
    return "\n".join(lines) + "\n"


INTERNET = ["android.permission.INTERNET"]
MICROPHONE = ["android.permission.INTERNET", "android.permission.RECORD_AUDIO"]
SYSTEM_14 = dump("appId", 1000, INTERNET, shared="android.uid.system")
VENDOR_SHARED_13 = dump("userId", 10057, INTERNET, shared="com.example.uid.shared")
VENDOR_SHARED_14 = dump("appId", 10057, INTERNET, shared="com.example.uid.shared")
OWN_MICROPHONE_14 = dump("appId", 10044, MICROPHONE)
VENDOR_SHARED_5 = dump_5(10057, MICROPHONE, shared="com.example.uid.shared")
OWN_MICROPHONE_5 = dump_5(10044, MICROPHONE)


class Device:
    def __init__(self, answer):
        self.answer = answer

    def shell(self, command, read_timeout_s=None):
        return self.answer if command.startswith("dumpsys package ") else "No operations."


def entry(out, name):
    """The note and evidence lines printed under one package in the report."""
    after = out.split(f" {name}\n", 1)[1].splitlines()
    return "\n".join(itertools.takewhile(lambda line: line.startswith(" " * 12), after))


class PrivacyAndroid14UidTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", tools / "privacy.py")
        (tools / "tv.py").write_text(FAKE_TV)
        self.script = tools / "privacy.py"
        self.state_path = self.base / "tv-state.json"

    def tv(self, dumps):
        self.state_path.write_text(json.dumps({
            "installed": sorted(dumps),
            "disabled": [],
            "voice": {},
            "dumps": dumps,
            "appops": {},
        }))

    def run_script(self, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        result = subprocess.run([sys.executable, str(self.script), *args], cwd=self.repo, env=env,
                                capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=120)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    # What probe() reads

    def test_the_uid_is_read_from_the_appid_line(self):
        for answer, uid in ((SYSTEM_14, 1000), (VENDOR_SHARED_14, 10057), (OWN_MICROPHONE_14, 10044),
                            (VENDOR_SHARED_13, 10057)):
            with self.subTest(uid=uid, android_14="appId=" in answer):
                self.assertEqual(privacy.probe(Device(answer), NAME)["uid"], uid)

    def test_only_the_package_s_own_request_is_a_declaration(self):
        for answer, declared in ((SYSTEM_14, False), (VENDOR_SHARED_13, False),
                                 (VENDOR_SHARED_14, False), (OWN_MICROPHONE_14, True),
                                 (VENDOR_SHARED_5, False), (OWN_MICROPHONE_5, True)):
            with self.subTest(declared=declared, dump=answer.splitlines()[2].strip()):
                self.assertIs(privacy.probe(Device(answer), NAME)["declared"], declared)

    # The report

    def test_a_system_package_on_android_14_shares_the_system_uid(self):
        self.tv({NAME: SYSTEM_14})
        lines = entry(self.run_script(), NAME)
        self.assertIn("shares the system UID (uid 1000)", lines)
        self.assertNotIn("declares RECORD_AUDIO", lines)

    def test_a_shared_uid_s_grant_is_not_the_package_s_request(self):
        for answer in (VENDOR_SHARED_13, VENDOR_SHARED_14, VENDOR_SHARED_5):
            with self.subTest(dump=answer.splitlines()[2].strip()):
                self.tv({NAME: answer})
                lines = entry(self.run_script(), NAME)
                self.assertNotIn("declares RECORD_AUDIO", lines)
                self.assertNotIn("system UID", lines)

    def test_an_app_that_asks_for_the_microphone_still_declares_it(self):
        # On 5.x the package's own grantedPermissions stand in for the request it does not print.
        for answer in (OWN_MICROPHONE_14, OWN_MICROPHONE_5):
            with self.subTest(dump=answer.splitlines()[2].strip()):
                self.tv({NAME: answer})
                lines = entry(self.run_script(), NAME)
                self.assertIn("declares RECORD_AUDIO", lines)
                self.assertNotIn("system UID", lines)

    # The list it writes

    def test_the_written_list_does_not_claim_the_microphone(self):
        # A catalogue package, so --write keeps it without --include-low; the first evidence line is
        # the one that goes into the file.
        self.tv({"org.droidtv.usagelogger": dump("appId", 1000, INTERNET, shared="android.uid.system",
                                                 name="org.droidtv.usagelogger")})
        target = self.base / "privacy.txt"
        self.run_script("--write", str(target))
        line = next(line for line in target.read_text().splitlines()
                    if line.startswith("org.droidtv.usagelogger"))
        self.assertIn("(shares the system UID (uid 1000)", line)
        self.assertNotIn("declares RECORD_AUDIO", line)


if __name__ == "__main__":
    unittest.main()

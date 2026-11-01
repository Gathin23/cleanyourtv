"""The input-driver guard reads each `dumpsys package` the way the TV prints it (#2398, #1753).

tests/test_tvinput_guard.py answers the guard's lookup with a ready list of names. Here the lookup
itself runs: the loop disable.sh and disable.ps1 send to the TV is handed to a local `sh`, with
`dumpsys` and `getprop` on PATH answering what a TV of a given Android version prints.

Android 6.0 (SDK 23) and later print a "requested permissions:" block whenever a package asks for
anything, so a readable dump without one is a package that asks for nothing: it cannot be an input
driver, and the guard lets it through. On a Xiaomi Mi TV 4A (MiTV-MOOQ1) and an NVIDIA SHIELD TV
2019, both Android 11, the guard used to keep such packages on as unreadable (#2398). Lollipop prints
neither block for packages that do ask, so below SDK 23 the same dump still keeps the package on
(#1753). A TV whose watchdog has switched every dump off says so, and the run says to reboot first.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it.
"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_home_guard as home_guard  # noqa: E402

BEFORE = "com.example.before"
AFTER = "com.example.after"
# Asks for no permission at all: on the SHIELD, a single DummyReceiver and nothing else.
NO_PERMS = "android.autoinstalls.config.nvidia"
MEDIATEK = "com.mediatek.tvinput"
HUNG = "Service dumps disabled due to hung system process.\n"

# The guard's loop is the one command that runs `dumpsys package` for every listed name. Run it in a
# local shell, with this test's dumpsys and getprop first on PATH.
RUN_LOOP = r'''

# Added by tests/test_tvinput_guard_dumps.py: the input-driver loop runs in a real shell.
home_guard_answer = answer


def answer(command):
    if "for p in " in command and "dumpsys package" in command:
        import subprocess
        env = dict(os.environ, PATH=os.environ["FAKE_BIN"] + os.pathsep + os.environ["PATH"])
        return subprocess.run(["sh", "-c", command], env=env, capture_output=True,
                              text=True).stdout
    return home_guard_answer(command)
'''
MAIN = '\nif __name__ == "__main__":'

# The TV's dumpsys: one file per package, or ALL for an answer every package gets.
DUMPSYS = '''#!/bin/sh
if [ -f "$FAKE_DUMPS/ALL" ]; then cat "$FAKE_DUMPS/ALL"
elif [ -f "$FAKE_DUMPS/$2" ]; then cat "$FAKE_DUMPS/$2"
else echo "Unable to find package: $2"; fi
'''
GETPROP = '''#!/bin/sh
[ -f "$FAKE_DUMPS/sdk" ] && cat "$FAKE_DUMPS/sdk"
true
'''


def android11(name, requested=()):
    """`dumpsys package <name>` on Android 11; no requested block when the package asks for nothing."""
    lines = ["Receiver Resolver Table:", "  Non-Data Actions:",
             "      android.autoinstalls.config.action.PLAY_AUTO_INSTALL:",
             f"        5d1c0a2 {name}/.DummyReceiver", "",
             # Not the package's own section, though it carries the name in brackets.
             "Key Set Manager:", f"  [{name}]", "      Signing KeySets: 3", "",
             "Packages:", f"  Package [{name}] (8e7f1b4):", "    userId=10037",
             "    codePath=/system/app/PlayAutoInstallConfig"]
    if requested:
        lines += ["    requested permissions:"] + [f"      {p}" for p in requested]
        lines += ["    install permissions:"] + [f"      {p}: granted=true" for p in requested]
    lines += ["    User 0: ceDataInode=0 installed=true hidden=false enabled=0", "      gids=[3003]",
              "      runtime permissions:"]
    return "\n".join(lines) + "\n"


def lollipop(name):
    """A Lollipop dump of a package whose permissions it does not print in either form."""
    return "\n".join(["Packages:", f"  Package [{name}] (2f4b9c1):", "    userId=10052",
                      "    codePath=/system/app/Example.apk"]) + "\n"


class TvInputGuardDumpsTest(unittest.TestCase):
    home_guard_setup = home_guard.HomeGuardTest.setUp
    write_list = home_guard.HomeGuardTest.write_list
    tv = home_guard.HomeGuardTest.tv
    disable_with = home_guard.HomeGuardTest.disable
    entries = home_guard.HomeGuardTest.entries
    output = home_guard.HomeGuardTest.output

    def setUp(self):
        self.home_guard_setup()
        self.assertIn(MAIN, home_guard.FAKE_TV, "the Home guard test's fake changed shape")
        (self.tools / "tv.py").write_text(home_guard.FAKE_TV.replace(MAIN, RUN_LOOP + MAIN, 1))
        self.bin = self.base / "bin"
        self.dumps = self.base / "dumps"
        self.bin.mkdir()
        self.dumps.mkdir()
        for name, text in (("dumpsys", DUMPSYS), ("getprop", GETPROP)):
            (self.bin / name).write_text(text)
            (self.bin / name).chmod(0o755)
        self.write_list([BEFORE, NO_PERMS, AFTER])

    def dumps_on(self, sdk, dumps):
        """What this TV's getprop and dumpsys answer; sdk None is a TV that reports no SDK."""
        for f in self.dumps.iterdir():
            f.unlink()
        if sdk is not None:
            (self.dumps / "sdk").write_text(f"{sdk}\n")
        for name, text in dumps.items():
            (self.dumps / name).write_text(text)
        self.tv(installed=[BEFORE, NO_PERMS, MEDIATEK, AFTER, home_guard.STOCK])

    def disable(self, entry):
        result, sent = self.disable_with(entry, {"FAKE_BIN": str(self.bin),
                                                 "FAKE_DUMPS": str(self.dumps)})
        self.assertEqual(result.returncode, 0, self.output(result))
        return " ".join(self.output(result).split()), sent

    def test_a_package_that_asks_for_nothing_passes_on_android_6_and_later(self):
        for entry in self.entries():
            for sdk in (23, 30):
                with self.subTest(entry=entry, sdk=sdk):
                    self.dumps_on(sdk, {BEFORE: android11(BEFORE, ["android.permission.INTERNET"]),
                                        NO_PERMS: android11(NO_PERMS),
                                        AFTER: android11(AFTER, ["android.permission.INTERNET"])})
                    said, sent = self.disable(entry)
                    self.assertIn(f"pm disable-user --user 0 {NO_PERMS}", sent)
                    self.assertNotIn("Skipping", said)

    def test_an_input_driver_is_still_skipped_on_android_11(self):
        self.write_list([BEFORE, NO_PERMS, MEDIATEK])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.dumps_on(30, {BEFORE: android11(BEFORE, ["android.permission.INTERNET"]),
                                   NO_PERMS: android11(NO_PERMS),
                                   MEDIATEK: android11(MEDIATEK, [
                                       "android.permission.TV_INPUT_HARDWARE"])})
                said, sent = self.disable(entry)
                self.assertNotIn(f"pm disable-user --user 0 {MEDIATEK}", sent)
                self.assertIn(f"pm disable-user --user 0 {NO_PERMS}", sent)
                self.assertIn(f"Skipping {MEDIATEK}: it asks for android.permission.TV_INPUT_HARDWARE",
                              said)

    def test_the_same_dump_stays_unchecked_on_lollipop(self):
        # #1753: Lollipop prints neither block for a package that does ask, so no block proves nothing.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.dumps_on(22, {NO_PERMS: lollipop(NO_PERMS)})
                said, sent = self.disable(entry)
                self.assertNotIn(f"pm disable-user --user 0 {NO_PERMS}", sent)
                self.assertIn(f"Skipping {NO_PERMS}: the TV's dumpsys shows neither", said)
                self.assertIn(f"pm disable-user --user 0 {BEFORE}", sent)

    def test_a_tv_that_reports_no_sdk_keeps_the_package_on(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.dumps_on(None, {NO_PERMS: android11(NO_PERMS)})
                said, sent = self.disable(entry)
                self.assertNotIn(f"pm disable-user --user 0 {NO_PERMS}", sent)
                self.assertIn(f"Skipping {NO_PERMS}: the TV's dumpsys shows neither", said)

    def test_a_dump_without_the_package_section_stays_unchecked(self):
        # Readable means the package's own section is there; anything else is not an answer.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.dumps_on(30, {NO_PERMS: f"Key Set Manager:\n  [{NO_PERMS}]\n"})
                said, sent = self.disable(entry)
                self.assertNotIn(f"pm disable-user --user 0 {NO_PERMS}", sent)
                self.assertIn(f"Skipping {NO_PERMS}: the TV's dumpsys shows neither", said)

    def test_dumps_switched_off_by_the_watchdog_say_reboot_first(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.dumps_on(30, {"ALL": HUNG})
                said, sent = self.disable(entry)
                for name in (BEFORE, NO_PERMS, AFTER):
                    self.assertNotIn(f"pm disable-user --user 0 {name}", sent)
                self.assertIn("hung system process", said)
                self.assertIn("Reboot the TV first", said)
                self.assertNotIn("Android 5.x", said, "the dumps are off, not an old Android")


if __name__ == "__main__":
    unittest.main()

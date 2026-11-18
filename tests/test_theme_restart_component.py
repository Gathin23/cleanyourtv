"""tools/theme_restart.py starts the launcher's real activity, not PACKAGE+"/.MainActivity".

The activity class belongs to the Java package the launcher was written under
(com.gathin.tvhome.MainActivity), and some firmware only hands HOME to an allowlisted applicationId
(com.ts.launcher, see docs/devices/xiaomi-mitv-mftr0-hyperos3.md). Spelled PACKAGE+"/.MainActivity", that
pair expands to com.ts.launcher.MainActivity, which is not a class that exists:

    Error type 3
    Error: Activity class {com.ts.launcher/com.ts.launcher.MainActivity} does not exist.

so the restart failed on exactly the sets the rest of this change exists for. Found on a real
MiTV-MFTR0 (OS3.0.28.0): every theme push ended in "Home did not restart after the theme was pushed",
while the files themselves had arrived.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(package):
    """Import theme_restart.py with LAUNCHER_PACKAGE set, the way theme.sh runs it."""
    previous = os.environ.get("LAUNCHER_PACKAGE")
    os.environ["LAUNCHER_PACKAGE"] = package
    try:
        spec = importlib.util.spec_from_file_location("theme_restart_under_test",
                                                      ROOT / "tools" / "theme_restart.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    finally:
        if previous is None:
            os.environ.pop("LAUNCHER_PACKAGE", None)
        else:
            os.environ["LAUNCHER_PACKAGE"] = previous


class FakeDev:
    """Answers the LEANBACK_LAUNCHER query the way a TV does, and records what was asked.

    theme_restart.py's shell() requires the __CYT_THEME_RC__ marker, because adb_shell's transport does
    not return the remote exit code; the fake appends it as the TV does.
    """

    def __init__(self, answer):
        self.answer = answer
        self.asked = []

    def shell(self, command, read_timeout_s=None):
        self.asked.append(command)
        return self.answer + "\n__CYT_THEME_RC__0\n"


class ComponentQueryTest(unittest.TestCase):
    def test_the_component_comes_from_the_tv(self):
        """The case that broke: allowlisted applicationId, class name from another package."""
        module = load("com.ts.launcher")
        dev = FakeDev("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n"
                      "com.ts.launcher/com.gathin.tvhome.MainActivity\n")
        self.assertEqual("com.ts.launcher/com.gathin.tvhome.MainActivity", module.component(dev, 0))

    def test_the_default_identity_is_unchanged(self):
        module = load("com.gathin.tvhome")
        dev = FakeDev("com.gathin.tvhome/com.gathin.tvhome.MainActivity\n")
        self.assertEqual("com.gathin.tvhome/com.gathin.tvhome.MainActivity", module.component(dev, 0))

    def test_the_query_names_this_package(self):
        """A TV with several launchers must not answer with another one's component."""
        module = load("com.ts.launcher")
        dev = FakeDev("com.other.launcher/com.other.launcher.Home\n"
                      "com.ts.launcher/com.gathin.tvhome.MainActivity\n")
        self.assertEqual("com.ts.launcher/com.gathin.tvhome.MainActivity", module.component(dev, 0))
        self.assertIn("com.ts.launcher", dev.asked[0])

    def test_a_tv_that_answers_nothing_keeps_the_old_spelling(self):
        module = load("com.gathin.tvhome")
        for answer in ("", "android/com.android.internal.app.ResolverActivity\n", "No activity found\n"):
            with self.subTest(answer=answer):
                dev = FakeDev(answer)
                self.assertEqual("com.gathin.tvhome/.MainActivity", module.component(dev, 0))

    def test_the_restart_starts_the_queried_component(self):
        """The end of the path that failed: `am start -n` has to name the component the TV gave."""
        module = load("com.ts.launcher")
        commands = []

        class Dev:
            def shell(self, command, read_timeout_s=None):
                commands.append(command)
                if "am get-current-user" in command:
                    return "0\n__CYT_THEME_RC__0\n"
                if "resolve-activity" in command and "LEANBACK_LAUNCHER" in command:
                    return "com.ts.launcher/com.gathin.tvhome.MainActivity\n__CYT_THEME_RC__0\n"
                if "resolve-activity" in command:
                    # HOME belongs to the launcher, so restart() takes the MAIN+HOME branch only when it
                    # starts with PACKAGE + "/"; answer something else to reach the -n branch under test.
                    return "com.other.launcher/com.other.Home\n__CYT_THEME_RC__0\n"
                # settings get: an empty accessibility list and the flag on, so no helper work follows.
                if "enabled_accessibility_services" in command:
                    return "null\n__CYT_THEME_RC__0\n"
                if "accessibility_enabled" in command:
                    return "1\n__CYT_THEME_RC__0\n"
                return "\n__CYT_THEME_RC__0\n"

        module.restart(Dev())
        starts = [c for c in commands if "am start" in c]
        self.assertTrue(starts, commands)
        self.assertIn("com.ts.launcher/com.gathin.tvhome.MainActivity", starts[0])
        self.assertNotIn("com.ts.launcher/.MainActivity", starts[0])


if __name__ == "__main__":
    unittest.main()

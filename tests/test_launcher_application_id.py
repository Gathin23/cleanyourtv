"""Both launcher installers take the applicationId as an opt-in, and build the default when they are not given one.

Some firmware only hands HOME to a launcher installed under a name on its allowlist: Xiaomi China sets on
Android 14 (HyperOS TV 3) want com.ts.launcher, and the same APK under the default falls through to
com.xiaomi.mitv.settings/.entry.FallbackHome with a black picture
(docs/devices/xiaomi-mitv-mftr0-hyperos3.md). The installers used to have no way to say so at all, so that
handover had to be done by hand.

Deliberately opt-in, with no auto-detection: nothing tells a China set from a global one before the launcher
is installed - the allowlist is a property of the installed launcher, not something the TV reports - so a
guess would rebuild every Xiaomi Android 14 set under a name it does not need. These tests hold that line:
the override reaches every step that addresses the TV, and with no override the default build is unchanged.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SH = ROOT / "launcher" / "build-and-install.sh"
PS1 = ROOT / "launcher" / "build-and-install.ps1"


def code(path):
    """The script without its comment lines, so a name in a comment does not count as a use."""
    return "\n".join(line for line in path.read_text(encoding="utf-8").splitlines()
                     if not line.lstrip().startswith("#"))


SH_CODE = code(SH)
PS1_CODE = code(PS1)


class OptInTest(unittest.TestCase):
    def test_the_shell_takes_the_override(self):
        self.assertIn("LAUNCHER_APPLICATION_ID", SH_CODE)

    def test_the_powershell_takes_the_override(self):
        # -LauncherApplicationId <pkg>, or LAUNCHER_APPLICATION_ID in the environment.
        self.assertIn("LauncherApplicationId", PS1_CODE)
        self.assertIn("LAUNCHER_APPLICATION_ID", PS1_CODE)

    def test_the_override_reaches_gradle(self):
        """Without -PlauncherApplicationId the APK is built under the default and the choice is decoration."""
        self.assertIn("-PlauncherApplicationId", SH_CODE)
        self.assertIn("-PlauncherApplicationId", PS1_CODE)

    def test_the_default_build_is_unchanged(self):
        """No override: the Gradle line has to be what it was before this switch existed."""
        for body, task in ((SH_CODE, "assembleRelease"), (PS1_CODE, "assembleRelease")):
            plain = [line for line in body.splitlines()
                     if task in line and "PlauncherApplicationId" not in line]
            self.assertTrue(plain, "the plain build line is still there")
            self.assertTrue(any(line.strip().endswith("assembleRelease -q --console=plain")
                                for line in plain), plain)

    def test_the_default_is_read_from_gradle_not_copied(self):
        """One source of truth: the default comes from app/build.gradle, not a second literal."""
        for body in (SH_CODE, PS1_CODE):
            self.assertRegex(body, r"app[\\/]build\.gradle")
            self.assertRegex(body, r"launcherApplicationId")
        # The only place the literal may appear is the fallback for a file that cannot be read.
        self.assertLessEqual(SH_CODE.count("com.gathin.tvhome"), 1, "no copies of the default in the shell")
        self.assertLessEqual(PS1_CODE.count("com.gathin.tvhome"), 1, "no copies of the default in PowerShell")

    def test_no_detection_of_any_kind(self):
        """The maintainer's point: a guess would rebuild working sets. Nothing may infer the identity."""
        for body in (SH_CODE, PS1_CODE):
            for probe in ("ro.boot.is_xiaomi", "ro.product.manufacturer", "ro.miui", "is_xiaomi"):
                self.assertNotIn(probe, body, f"{probe} would auto-detect")
            # No "if this looks like a Xiaomi set then use com.ts.launcher".
            self.assertNotRegex(body, r"if[^\n]*xiaomi[^\n]*com\.ts\.launcher", 
                                "the identity must not be inferred from the brand")

    def test_every_step_that_addresses_the_tv_uses_the_variable(self):
        """Build, start, force-stop, the grants and the Home check must agree on one identity.

        A leftover com.gathin.tvhome in any of them addressed a package the TV does not have, which is the
        failure this switch exists to avoid.
        """
        for name, body in (("shell", SH_CODE), ("powershell", PS1_CODE)):
            with self.subTest(script=name):
                # Drop the fallback that names the default on purpose.
                stripped = re.sub(r"[^\n]*LAUNCHER_APPLICATION_ID[^\n]*\n[^\n]*-not[^\n]*\n(?:[^\n]*\n)*?[^\n]*com\.gathin\.tvhome[^\n]*\n",
                                  "", body)
                stripped = stripped.replace("$gradleDefault = 'com.gathin.tvhome'", "")
                stripped = stripped.replace('DEFAULT_APPLICATION_ID=com.gathin.tvhome', "")
                self.assertNotIn("com.gathin.tvhome", stripped)


if __name__ == "__main__":
    unittest.main()

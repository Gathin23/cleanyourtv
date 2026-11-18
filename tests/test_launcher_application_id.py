"""launcher/build-and-install.sh builds under the applicationId the TV will accept as Home.

Some firmware only hands HOME to a launcher installed under a name on its allowlist. On Xiaomi China sets
running Android 14 (HyperOS TV 3) that is com.ts.launcher: the same APK under the default
com.gathin.tvhome falls through to com.xiaomi.mitv.settings/.entry.FallbackHome with a black picture, so
the identity is part of the recipe, not a preference (docs/devices/xiaomi-mitv-mftr0-hyperos3.md). The
script used to build with the default applicationId unconditionally and then find nothing at Home.

The inference reads the Android version, because the allowlist is only visible once something answers
HOME: it is a property of the installed launcher, not a property the TV reports.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SH = (ROOT / "launcher" / "build-and-install.sh").read_text(encoding="utf-8")
CODE = "\n".join(line for line in SH.splitlines() if not line.lstrip().startswith("#"))


class LauncherApplicationIdTest(unittest.TestCase):
    def test_the_application_id_reaches_gradle(self):
        """The APK has to be built under the chosen identity, or the choice is decoration."""
        self.assertRegex(CODE, r"-PlauncherApplicationId=")

    def test_every_step_that_names_the_launcher_uses_the_variable(self):
        """Install, start, Home switch and verify all have to agree on one package.

        A leftover com.gathin.tvhome in any of them addressed a package the TV does not have: the start
        check failed, or the verify read Home from the wrong name. The one place the literal belongs is the
        fallback that the inference starts from, which the check below allows.
        """
        # Drop the inference block, which is where the default is legitimately named.
        body = re.sub(r'LAUNCHER_APPLICATION_ID="\$\{LAUNCHER_APPLICATION_ID:-\}".*?^fi$', "",
                      CODE, flags=re.S | re.M)
        self.assertNotIn("com.gathin.tvhome", body)

    def test_xiaomi_android_14_is_built_as_com_ts_launcher(self):
        self.assertIn("ro.boot.is_xiaomi", CODE)
        self.assertIn("com.ts.launcher", CODE)
        # The version decides, not the brand: the Android 11 sets of the same models take the default.
        self.assertIn("ro.build.version.sdk", CODE)
        self.assertRegex(CODE, r"-ge\s+34")

    def test_the_application_id_can_be_named_outright(self):
        self.assertIn("LAUNCHER_APPLICATION_ID", CODE)

    def test_the_inference_runs_before_the_build(self):
        self.assertLess(CODE.index("ro.boot.is_xiaomi"), CODE.index("-PlauncherApplicationId="))

    def test_the_home_slot_is_checked_against_the_chosen_id(self):
        """`Home already belongs to this launcher` has to recognise the allowlisted identity too."""
        self.assertIn('"$HOME_PKG" = "$LAUNCHER_APPLICATION_ID"', CODE)


if __name__ == "__main__":
    unittest.main()

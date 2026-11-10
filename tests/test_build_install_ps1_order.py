"""launcher/build-and-install.ps1 keeps the three guards build-and-install.sh has (#3187).

- Home moves to the new launcher only after `am start -W` showed it starts. The script used to run
  `set-home-activity` first and not undo it when the start check failed, so the remote's Home button
  opened a launcher that did not start.
- The TV's SDK is checked against the minSdk in app/build.gradle before Gradle runs, and a pm install
  refused with INSTALL_FAILED_OLDER_SDK is named as such instead of a raw "Failure" (#576).
- A missing tools\\venv is reported before the build, not after it as "python.exe is not recognized"
  (#1142).

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PS1 = (ROOT / "launcher" / "build-and-install.ps1").read_text(encoding="utf-8")
CODE = "\n".join(line for line in PS1.splitlines() if not line.lstrip().startswith("#"))


class BuildInstallPs1OrderTest(unittest.TestCase):
    def test_home_moves_only_after_the_start_check(self):
        start = CODE.index("am start -W")
        check = CODE.index("the new launcher did not start")
        homes = [m.start() for m in re.finditer(r"set-home-activity --user 0 com\.gathin\.tvhome", CODE)]
        self.assertTrue(homes, "the script still sets the new launcher as Home")
        self.assertLess(start, check)
        for at in homes:
            self.assertGreater(at, check, "Home is set to the new launcher before its start check")

    def test_the_sdk_is_checked_before_the_build(self):
        self.assertIn("ro.build.version.sdk", CODE)
        self.assertRegex(CODE, r"app\\build\.gradle")
        self.assertLess(CODE.index("ro.build.version.sdk"), CODE.index("gradlew.bat"))
        self.assertIn("INSTALL_FAILED_OLDER_SDK", CODE)
        # The pattern reads the same line build-and-install.sh reads.
        self.assertTrue(re.search(r"^\s*minSdk\s+\d{1,3}\s*$", (ROOT / "launcher" / "app" / "build.gradle")
                                  .read_text(encoding="utf-8"), re.M))

    def test_a_missing_venv_stops_before_the_build(self):
        venv_check = CODE.index("Test-Path $py")
        self.assertLess(venv_check, CODE.index("gradlew.bat"))
        self.assertLess(CODE.index("$py = "), venv_check)


if __name__ == "__main__":
    unittest.main()

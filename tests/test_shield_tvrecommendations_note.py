"""The SHIELD lists must keep com.google.android.tvrecommendations enabled.

Disabled, Android stops binding the SHIELD TV phone app's input service at boot: the app connects and
every key fails. Bisected on two SHIELDs (darcy and foster_e, Android 11), and both example lists had it
as an active line. These tests keep the line out of the SHIELD lists and keep the diagnosis in
docs/TROUBLESHOOTING.md. Run from the repository root:
python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
DOC = (ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8")
PACKAGE = "com.google.android.tvrecommendations"
SHIELD_LISTS = sorted((ROOT / "examples").glob("nvidia-shield-*/debloat.txt"))


def active_packages(path):
    """The package names a list would disable: first word of every line that is not a comment."""
    names = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            names.append(line.split()[0])
    return names


def section(title):
    start = DOC.index(title)
    end = DOC.find("\n## ", start + len(title))
    return re.sub(r"\s+", " ", DOC[start:end if end != -1 else None])


class ShieldTvRecommendationsTest(unittest.TestCase):
    def test_there_are_shield_lists_to_check(self):
        self.assertGreaterEqual(len(SHIELD_LISTS), 2)

    def test_no_shield_list_disables_it(self):
        for path in SHIELD_LISTS:
            with self.subTest(list=path.parent.name):
                self.assertNotIn(PACKAGE, active_packages(path))
                self.assertIn(PACKAGE, path.read_text(encoding="utf-8"),
                              "keep the commented line, so the reason stays next to the package")

    def test_troubleshooting_has_the_diagnosis_and_the_fix(self):
        text = section("## SHIELD TV phone app connects, but no key does anything")
        for needle in ("Key injection failed", "isInitialized=false", "virtualinput.InputService",
                       "IntentBindRecord", f"pm enable {PACKAGE}", "20 seconds", "darcy", "foster_e"):
            with self.subTest(needle=needle):
                self.assertIn(needle, text)


if __name__ == "__main__":
    unittest.main()

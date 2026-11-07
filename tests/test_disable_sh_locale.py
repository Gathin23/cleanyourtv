"""tools/disable.sh matches package names by bytes, whatever the computer's locale (#3286).

In tr_TR.UTF-8 GNU grep 3.12 leaves i and I out of [A-Za-z], so disable.sh refused every package name
with an i in it, and its sed that reads the Live TV and Settings handlers matched nothing. The script
now sets LC_ALL=C before any of those checks.

Run from the repository root: python3 -m unittest discover -s tests -v
The behaviour case needs bash and skips on Windows and where tr_TR.UTF-8 is not generated.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "tools" / "disable.sh"


class DisableShLocale(unittest.TestCase):
    def test_locale_is_pinned_before_the_first_name_check(self):
        lines = SCRIPT.read_text(encoding="utf-8").splitlines()
        pin = next(i for i, l in enumerate(lines) if l.strip() == "export LC_ALL=C")
        first = next(i for i, l in enumerate(lines) if "[A-Za-z" in l and not l.lstrip().startswith("#"))
        self.assertLess(pin, first)

    @unittest.skipIf(sys.platform == "win32", "needs bash and a POSIX locale")
    def test_turkish_locale_does_not_refuse_names_with_an_i(self):
        have = subprocess.run(["locale", "-a"], capture_output=True, text=True).stdout
        if "tr_tr.utf8" not in have.lower().replace("-", ""):
            self.skipTest("tr_TR.UTF-8 is not generated here")
        with tempfile.TemporaryDirectory() as tmp:
            lst = Path(tmp) / "debloat.txt"
            lst.write_text("com.google.android.katniss\n", encoding="utf-8")
            env = dict(os.environ, LC_ALL="tr_TR.UTF-8", LANG="tr_TR.UTF-8", TV_HOST="127.0.0.1")
            out = subprocess.run(["bash", str(SCRIPT), str(lst)], capture_output=True, text=True,
                                 env=env, timeout=60)
        self.assertNotIn("not a package name", out.stdout + out.stderr)


if __name__ == "__main__":
    unittest.main()

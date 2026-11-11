"""tools/disable.sh, restore.sh and theme.sh match names by bytes, whatever the computer's locale (#3286).

In tr_TR.UTF-8 bash and GNU grep leave i and I out of [A-Za-z], so disable.sh refused every package name
with an i in it, and its sed that reads the Live TV and Settings handlers matched nothing. restore.sh
checks the Home it reads back with the same kind of range, and theme.sh checks every wallpaper name with
one: a picture of a shipped theme whose name carries an i (themes/aerial's
01-northern-europe-sunrise.jpg) was refused as "unsupported wallpaper filename", a rule the name does not
break, so the push stopped before anything was sent. Each script sets LC_ALL=C before its first check.

theme.ps1, disable.ps1 and restore.ps1 compare with PowerShell's own regex, whose ranges are code points,
so they need no pin.

Run from the repository root: python3 -m unittest discover -s tests -v
The behaviour cases need bash and skip on Windows and where tr_TR.UTF-8 is not generated.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "tools" / "disable.sh"
RESTORE = ROOT / "tools" / "restore.sh"
THEME = ROOT / "tools" / "theme.sh"

# The marks that make a line an ASCII-range regex rather than a `case` glob: a `=~` match, a grep or sed
# run, or a variable holding a pattern (PKG_RE=, NAME_RE=). A drive-letter glob such as
# `[A-Za-z]:[\\/]*` reads letters one at a time and is left out on purpose.
MARKERS = ("=~", "grep", "sed", "_RE=")


def name_checks():
    """Every tools/*.sh that matches a name with an ASCII range, with the line of its first check."""
    found = {}
    for script in sorted((ROOT / "tools").glob("*.sh")):
        lines = script.read_text(encoding="utf-8").splitlines()
        for number, line in enumerate(lines):
            if line.lstrip().startswith("#") or "[A-Za-z" not in line:
                continue
            if not any(marker in line for marker in MARKERS):
                continue
            found[script.name] = (lines, number)
            break
    return found


def turkish_locale(tmp):
    """The environment that runs a script under tr_TR.UTF-8: the machine's own locale, or one built into tmp.

    `locale -a` lists only what the machine has generated, so a host without tr_TR.UTF-8 (most CI images)
    builds one with localedef when that is installed. None means neither worked, and the caller skips.
    """
    have = subprocess.run(["locale", "-a"], capture_output=True, text=True).stdout
    if "tr_tr.utf8" in have.lower().replace("-", ""):
        return {}
    try:
        built = Path(tmp) / "locale"
        built.mkdir()
        made = subprocess.run(["localedef", "-i", "tr_TR", "-f", "UTF-8", str(built / "tr_TR.UTF-8")],
                              capture_output=True, text=True)
    except OSError:
        return None
    return {"LOCPATH": str(built)} if made.returncode == 0 else None


class ShellLocale(unittest.TestCase):
    def test_locale_is_pinned_before_the_first_name_check(self):
        # theme.sh checks wallpaper names, restore.sh the Home it reads back, disable.sh package names.
        checks = name_checks()
        for name in ("disable.sh", "restore.sh", "theme.sh"):
            with self.subTest(script=name):
                self.assertIn(name, checks, f"{name} no longer matches a name with an ASCII range")
        self.assertGreaterEqual(len(checks), 3, f"the walk read {sorted(checks)}")

        for name, (lines, first) in checks.items():
            with self.subTest(script=name):
                pin = next((i for i, l in enumerate(lines) if l.strip() == "export LC_ALL=C"), None)
                if pin is None:
                    self.fail(f"{name} matches a name with an ASCII range but never sets LC_ALL=C")
                self.assertLess(
                    pin, first,
                    f"{name} sets LC_ALL=C at line {pin + 1}, after its first name check at line {first + 1}")

    @unittest.skipIf(sys.platform == "win32", "needs bash and a POSIX locale")
    def test_turkish_locale_does_not_refuse_names_with_an_i(self):
        with tempfile.TemporaryDirectory() as tmp:
            locale = turkish_locale(tmp)
            if locale is None:
                self.skipTest("tr_TR.UTF-8 is neither generated here nor buildable with localedef")
            lst = Path(tmp) / "debloat.txt"
            lst.write_text("com.google.android.katniss\n", encoding="utf-8")
            env = dict(os.environ, LC_ALL="tr_TR.UTF-8", LANG="tr_TR.UTF-8", TV_HOST="127.0.0.1", **locale)
            out = subprocess.run(["bash", str(SCRIPT), str(lst)], capture_output=True, text=True,
                                 env=env, timeout=60)
        self.assertNotIn("not a package name", out.stdout + out.stderr)

    @unittest.skipIf(sys.platform == "win32", "needs bash and a POSIX locale")
    def test_turkish_locale_does_not_refuse_a_wallpaper_name_with_an_i(self):
        with tempfile.TemporaryDirectory() as tmp:
            locale = turkish_locale(tmp)
            if locale is None:
                self.skipTest("tr_TR.UTF-8 is neither generated here nor buildable with localedef")
            theme = Path(tmp) / "aerial"
            (theme / "wallpapers").mkdir(parents=True)
            (theme / "config.json").write_text("{}", encoding="utf-8")
            # A shipped theme's own caption, with the i in "sunrise" that the range dropped.
            (theme / "wallpapers" / "01-northern-europe-sunrise.jpg").write_bytes(b"\xff\xd8\xff")
            env = dict(os.environ, LC_ALL="tr_TR.UTF-8", LANG="tr_TR.UTF-8", TV_HOST="127.0.0.1", **locale)
            out = subprocess.run(["bash", str(THEME), str(theme)], capture_output=True, text=True,
                                 env=env, timeout=120)
        self.assertNotIn("unsupported wallpaper filename", out.stdout + out.stderr)


if __name__ == "__main__":
    unittest.main()

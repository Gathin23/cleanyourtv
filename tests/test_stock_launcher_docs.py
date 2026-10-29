"""The README names every stock launcher the install scripts will disable.

`launcher/build-and-install.sh` and its PowerShell twin only disable a stock launcher they recognise:
`KNOWN_STOCK_LAUNCHERS` holds four packages (Android TV's `tvlauncher`, Google TV's `launcherx`, the
older `leanbacklauncher` and Xiaomi's PatchWall `com.mitv.tvhome`). Anything else stops the script and
asks for `STOCK_LAUNCHER=<package>`, which is what the README's "How the scripts protect Home" block
tells an owner to expect.

That sentence named two of the four, so an owner of a Xiaomi Mi TV (a fourth of the lists here) or of
a `leanbacklauncher` build read that their stock launcher was not recognised - when the script does
disable it. The packages are read out of the two scripts rather than copied here, so a launcher added
to the pair fails this test until the README names it.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
README = ROOT / "README.md"
SH = ROOT / "launcher" / "build-and-install.sh"
PS1 = ROOT / "launcher" / "build-and-install.ps1"
SUMMARY = "<summary><b>How the scripts protect Home</b></summary>"


def sh_known(text=None):
    """The packages in the bash script's KNOWN_STOCK_LAUNCHERS (space separated)."""
    text = SH.read_text(encoding="utf-8") if text is None else text
    m = re.search(r'^KNOWN_STOCK_LAUNCHERS="([^"]*)"', text, re.M)
    return m.group(1).split() if m else []


def ps1_known(text=None):
    """The packages in the PowerShell twin's $knownStock array (single quoted)."""
    text = PS1.read_text(encoding="utf-8") if text is None else text
    m = re.search(r"\$knownStock\s*=\s*@\(([^)]*)\)", text)
    return re.findall(r"'([^']+)'", m.group(1)) if m else []


def home_section(text=None):
    """The README block about the Home guard, or None when the summary is gone."""
    text = README.read_text(encoding="utf-8") if text is None else text
    start = text.find(SUMMARY)
    if start == -1:
        return None
    end = text.find("</details>", start)
    return text[start:] if end == -1 else text[start:end]


class Scripts(unittest.TestCase):
    def test_bash_script_recognises_stock_launchers(self):
        known = sh_known()
        self.assertGreaterEqual(len(known), 2, "KNOWN_STOCK_LAUNCHERS did not read as a list")
        for pkg in known:
            self.assertRegex(pkg, r"^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$")

    def test_powershell_twin_recognises_the_same_ones(self):
        self.assertEqual(sh_known(), ps1_known(),
                         "build-and-install.sh and build-and-install.ps1 disagree on the stock launchers")


class Readme(unittest.TestCase):
    def test_section_is_there(self):
        self.assertIsNotNone(home_section(), "README has no 'How the scripts protect Home' block")

    def test_every_recognised_launcher_is_named(self):
        section = home_section()
        self.assertIsNotNone(section)
        known = sh_known()
        self.assertTrue(known, "no stock launchers were read out of the script")
        for pkg in known:
            self.assertIn(pkg, section,
                          f"README's Home block does not name {pkg}, which the install scripts disable")

    def test_the_none_override_is_still_offered(self):
        section = home_section()
        self.assertIsNotNone(section)
        self.assertIn("`none`", section, "the README no longer says STOCK_LAUNCHER=none keeps the stock launcher")


class Readers(unittest.TestCase):
    """The readers above, on documents written here, so neither test can pass by reading nothing."""

    def test_bash_reader(self):
        self.assertEqual(sh_known('KNOWN_STOCK_LAUNCHERS="a.b c.d"\n'), ["a.b", "c.d"])
        self.assertEqual(sh_known("# no list here\n"), [])

    def test_powershell_reader(self):
        self.assertEqual(ps1_known("$knownStock = @('a.b', 'c.d')\n"), ["a.b", "c.d"])
        self.assertEqual(ps1_known("$other = @('a.b')\n"), [])

    def test_section_reader_stops_at_the_block(self):
        text = f"{SUMMARY}\n\nbody\n</details>\n\nafter\n"
        self.assertEqual(home_section(text), f"{SUMMARY}\n\nbody\n")
        self.assertIsNone(home_section("nothing here\n"))

    def test_section_reader_keeps_an_unclosed_block(self):
        self.assertEqual(home_section(f"{SUMMARY}\ntail\n"), f"{SUMMARY}\ntail\n")


if __name__ == "__main__":
    unittest.main()

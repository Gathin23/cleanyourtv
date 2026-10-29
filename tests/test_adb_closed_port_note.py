"""The notes that send an owner to close Wireless debugging hand `tools/adb-closed.py` that port.

`tools/adb-closed.py <tv ip[:port]> [port]` has taken a port since it was added: it falls back to
`TV_PORT` or 5555 only when it is given none. Two notes said it "only checks 5555": the Connecting
bullet "USB debugging never opens port 5555" in docs/TROUBLESHOOTING.md and the header of the
Chromecast with Google TV 4K list, both from the `sabrina` report that turning USB debugging off left
the Wireless debugging port accepting connections. The README FAQ "Isn't ADB access a security hole?"
says to note that port and run `tools/adb-closed.py <tv ip> <port>` before the reboot, so an owner who
read the bullet was told there was no check for the port the README asks them to check.

The port is read out of the tool's own usage line rather than taken on trust here: if adb-closed.py
ever loses its port argument, the bullet and the header have to be rewritten and this fails. Running
it against a listener on a port other than 5555 is tests/test_readme_wireless_debugging.py's job.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_adb_closed import SCRIPT  # noqa: E402
from test_sabrina_cast_restart_note import bullets  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
SABRINA = ROOT / "examples" / "google-chromecast-gtv-4k" / "debloat.txt"
ONLY_5555 = re.compile(r"adb-closed\.py`?\s+(?:only|just)\s+checks\s+(?:port\s+)?5555")


def flat(text):
    """Collapse line wrapping and a list header's `#` markers, so a wrapped phrase reads as one."""
    return re.sub(r"\s+", " ", re.sub(r"^#+ ?", "", text, flags=re.M))


def pages():
    """The pages an owner reads: the READMEs and the other top-level guides, docs/ and the lists."""
    found = sorted(ROOT.glob("README*.md"))
    found += [ROOT / "PROMPT.md", ROOT / "AGENTS.md", ROOT / "CONTRIBUTING.md"]
    found += sorted(ROOT.glob("tools/*.md")) + sorted((ROOT / "docs").rglob("*.md"))
    found += sorted(p for p in (ROOT / "examples").rglob("*") if p.suffix in (".txt", ".md"))
    return found


def says_only_5555(text):
    """True when the text says adb-closed.py checks nothing but 5555."""
    return ONLY_5555.search(flat(text)) is not None


def header(path):
    """A list's comment lines, flattened."""
    lines = path.read_text(encoding="utf-8").splitlines()
    return flat("\n".join(l for l in lines if l.startswith("#")))


class TheToolTakesAPort(unittest.TestCase):
    def test_the_usage_line_takes_a_port(self):
        usage = SCRIPT.read_text(encoding="utf-8").split('"""')[1]
        self.assertRegex(usage, r"adb-closed\.py <tv ip(?:\[:port\])?> \[port\]",
                         "adb-closed.py no longer takes a port: the notes below hand it one")


class NoPageSaysItOnlyChecks5555(unittest.TestCase):
    def test_no_page_says_it_only_checks_5555(self):
        wrong = [str(p.relative_to(ROOT)) for p in pages()
                 if says_only_5555(p.read_text(encoding="utf-8"))]
        self.assertEqual(wrong, [],
                         "these say tools/adb-closed.py only checks 5555, but it takes a port")

    def test_the_connecting_bullet_hands_it_the_wireless_debugging_port(self):
        found = [flat(b) for b in bullets(TROUBLE.read_text(encoding="utf-8"))
                 if "USB debugging never opens port 5555" in b]
        self.assertEqual(len(found), 1,
                         "the Connecting bullet 'USB debugging never opens port 5555' moved")
        bullet = found[0]
        self.assertIn("switch Wireless debugging off on its own", bullet)
        self.assertIn("`_adb-tls-connect._tcp`", bullet,
                      "the bullet has to say where the port is listed")
        self.assertIn("`tools/adb-closed.py <tv ip> <port>` straight after, before the reboot", bullet)

    def test_the_sabrina_header_hands_it_the_wireless_debugging_port(self):
        text = header(SABRINA)
        start = text.find("Turning USB debugging off leaves Wireless debugging listening on its own")
        self.assertNotEqual(start, -1, "the sabrina header's Wireless debugging quirk moved")
        quirk = text[start:text.find(" - ", start)]
        self.assertIn("tools/adb-closed.py <ip> <port> before the reboot", quirk)


class TheSweep(unittest.TestCase):
    def test_a_wrapped_header_line_is_caught(self):
        self.assertTrue(says_only_5555("#   you are done; tools/adb-closed.py only\n"
                                       "#   checks 5555.\n"))

    def test_a_backticked_name_is_caught(self):
        self.assertTrue(says_only_5555("and\n  `tools/adb-closed.py` only checks 5555. `adb mdns`"))

    def test_the_corrected_wording_is_not_caught(self):
        self.assertFalse(says_only_5555("`tools/adb-closed.py` checks 5555 unless it is given "
                                        "that port."))
        self.assertFalse(says_only_5555("GlobalKey only checks that a package is installed"))

    def test_the_sweep_reads_the_notes_and_the_readme(self):
        read = pages()
        for page in (TROUBLE, SABRINA, ROOT / "README.md"):
            self.assertIn(page, read)


if __name__ == "__main__":
    unittest.main()

"""The HY300 Pro+ header says the swap to Allwinner's own panel can land on the wrong port.

The header has recommended that swap since the picker turned out to be wider than the hardware, and
#4296's 2026-11-26 report added the half that made it unsafe to recommend bare: on that unit the panel
came up on HDMI3 while the only wired port was HDMI2, and showed "No Signal" over a live source until it
was started with a string extra. So three claims are held here rather than trusted to stay put:

  * the note names the wrong-port failure, the connected port it was diagnosed on, and the by-hand start
    that fixed it, with the report's own evidence (`dumpsys tv_input` states, the two properties) and the
    absence of a build string in that report;
  * the same paragraph's second half - that `config.json` cannot send the extra - is read out of the
    launcher: `openSourcePanel()` must still send one extra, and it must still be an int. A launcher that
    grows a string-extra route fails here instead of leaving the header saying something untrue;
  * `themes/README.md`'s `source_panel_extra` row still describes that int-only behaviour, which is the
    page the header's sentence leans on.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIST = ROOT / "examples" / "magcubic-hy300-pro-plus" / "debloat.txt"
MAIN = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "MainActivity.java"
THEMES = ROOT / "themes" / "README.md"

# What the paragraph has to carry: the report's diagnosis, its evidence, and the by-hand start.
NOTE = [
    "not free on every board",
    "input_source",
    "falls back to HDMI3",
    "source_panel_extra",
    "#4296",
    "com.softwinner.vis/.HdmiInputService/HW2 at state 0 with HW1 and HW3 at state 1",
    "/product/etc/config.ini and persist.sys.hdmisource both say HDMI2",
    "No Signal",
    "kHalSourceID_HDMI_2",
    "3840x2160 30Hz",
    "am start -n com.softwinner.awlivetv/.MainActivity --es input_source HDMI2",
    "no build string",
    "Projector.20260820.173302",
    "dumpsys tv_input",
]


def joined(text):
    return " ".join(text.split())


def header():
    """The list's leading `#` block: everything before the first active package line."""
    lines = []
    for line in LIST.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            break
        lines.append(line)
    return "\n".join(lines)


def open_source_panel():
    """MainActivity.openSourcePanel()'s body, or '' when the method is gone."""
    body = MAIN.read_text(encoding="utf-8")
    start = body.find("private void openSourcePanel()")
    if start < 0:
        return ""
    end = body.find("\n    }", start)
    return body[start:end] if end > start else body[start:]


class TheNoteInTheHeader(unittest.TestCase):
    def setUp(self):
        self.header = joined(header())

    def test_the_header_carries_every_claim_in_order(self):
        missing = [needle for needle in NOTE if needle not in self.header]
        self.assertEqual([], missing, "the header lost part of the wrong-port note")

    def test_it_sits_before_the_first_package_line(self):
        # A note that drifts into the body reads as a package the TV might disable.
        full = LIST.read_text(encoding="utf-8")
        self.assertIn("falls back to HDMI3", header())
        self.assertNotIn("falls back to HDMI3", full.split("\ncom.", 1)[-1])

    def test_it_does_not_claim_a_build_for_that_unit(self):
        # The report carried none, so the paragraph says so rather than borrowing the first unit's build.
        self.assertIn("no build string", self.header)
        self.assertNotIn("Projector.20260820.173302 the only wired port", self.header)


class TheLauncherStillSendsAnIntOnly(unittest.TestCase):
    def setUp(self):
        self.body = open_source_panel()

    def test_the_method_is_still_there(self):
        self.assertTrue(self.body, "openSourcePanel() moved; the header's sentence needs re-reading")

    def test_it_sends_one_extra_and_parses_it_as_a_number(self):
        self.assertEqual(1, self.body.count("putExtra("),
                         "openSourcePanel() sends more than one extra now; check the header")
        self.assertIn("Integer.parseInt", self.body,
                      "the panel extra is no longer an int, so config.json may be able to send a string")


class TheKeyRowSaysTheSame(unittest.TestCase):
    def test_the_source_panel_extra_row_is_still_int_only(self):
        page = joined(THEMES.read_text(encoding="utf-8"))
        self.assertIn("| `source_panel_extra` |", page)
        self.assertIn("a whole number after it", page,
                      "themes/README.md no longer says the extra must be a whole number")


if __name__ == "__main__":
    unittest.main()

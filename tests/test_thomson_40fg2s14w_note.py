"""The Thomson 40FG2S14W note (#1035) and the CVTE advice it corrects.

The note is documentation only, so the tests pin what makes it useful - the board, the build, the six
packages, and the fact that the stock launcher stayed enabled - plus the one claim in
docs/TROUBLESHOOTING.md's CVTE bullet that this session falsifies: `set-home-activity` answering
`Success` is not proof that Home moved.

They assert identifiers and commands, not sentences, so rewording the prose does not fail them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "thomson-40fg2s14w.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
EXAMPLES_README = ROOT / "examples" / "README.md"
DISABLE_SH = ROOT / "tools" / "disable.sh"

LAUNCHER = "com.gathin.tvhome"
STOCK = "com.google.android.apps.tv.launcherx"
HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
SET_HOME = f"cmd package set-home-activity --user 0 {LAUNCHER}/.MainActivity"

DISABLED = [
    "tv.anoki.acr.anokiacroptin",
    "com.android.adservices.api",
    "com.android.ondevicepersonalization.services",
    "com.android.federatedcompute.services",
    "com.google.android.feedback",
    "com.android.tv.feedbackconsent",
]

LINK = re.compile(r"\]\(([^)\s]+)\)")


def bullet(text, start):
    """The markdown bullet that begins with `start`, up to the next top-level bullet or heading."""
    lines = text.split("\n")
    first = next(i for i, line in enumerate(lines) if line.startswith(start))
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def broken_links(path):
    broken = []
    for target in LINK.findall(path.read_text(encoding="utf-8")):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        local = target.split("#", 1)[0]
        if local and not (path.parent / local).exists():
            broken.append(target)
    return broken


def note_text():
    return NOTE.read_text(encoding="utf-8")


class ThomsonNoteTest(unittest.TestCase):
    def test_note_exists_and_names_the_board(self):
        self.assertTrue(NOTE.is_file(), "docs/devices note missing")
        text = note_text()
        for token in ("40FG2S14W", "nagata", "Expressluck", "UKNV.260317.001", "Android TV 14",
                      "34", "com.cvte.tv.at", STOCK, "issues/1035"):
            with self.subTest(token=token):
                self.assertIn(token, text)

    def test_every_disabled_package_is_recorded(self):
        text = note_text()
        for package in DISABLED:
            with self.subTest(package=package):
                self.assertIn(package, text)

    def test_note_keeps_the_stock_launcher_enabled(self):
        # The whole point: the handover is not kept, because a CVTE board without a tested profile
        # keeps its stock launcher (the #741 boot hang). A note that told readers to disable it would
        # be the opposite advice.
        text = note_text()
        self.assertIn(SET_HOME, text)
        self.assertIn("Success", text)
        self.assertIn("741", text)
        self.assertIsNone(re.search(r"pm disable-user[^\n]*" + re.escape(STOCK), text),
                          "the note must not disable the stock launcher")

    def test_note_reads_the_resolver(self):
        # The check, not the sentence around it: the note has to send a reader to resolve-activity,
        # because that is the query the Home guard reads and the one that disagreed on this board.
        self.assertIn("resolve-activity", note_text())

    def test_note_is_not_an_examples_list(self):
        # A one-session report stays a device note; an examples/ folder needs several days of use.
        self.assertFalse(list((ROOT / "examples").glob("thomson-40fg2s14w")),
                         "a one-session report does not belong in examples/")
        self.assertTrue(NOTE.is_file())

    def test_note_links_resolve(self):
        self.assertEqual(broken_links(NOTE), [])


class TroubleshootingCvteBulletTest(unittest.TestCase):
    def setUp(self):
        self.text = TROUBLE.read_text(encoding="utf-8")
        self.cvte = bullet(self.text, "- **Black screen at every boot after the launcher change")

    def test_the_fallback_home_check_is_still_there(self):
        # The caveat is added under an existing recipe; the recipe itself must not be dropped.
        self.assertIn("FallbackHome", self.cvte)
        self.assertIn("DIRECT_BOOT_AWARE", self.cvte)
        self.assertIn("set-home-activity", self.cvte)

    def test_success_is_no_longer_presented_as_proof(self):
        for token in ("Success", "resolve-activity", "40FG2S14W"):
            with self.subTest(token=token):
                self.assertIn(token, self.cvte)

    def test_the_resolver_command_matches_the_tools(self):
        # Read the query out of disable.sh instead of trusting a copy: the guard and the docs have to
        # ask the same question to get the same answer.
        src = DISABLE_SH.read_text(encoding="utf-8")
        match = re.search(r"HOME_QUERY='([^']+)'", src)
        self.assertIsNotNone(match, "tools/disable.sh no longer defines HOME_QUERY")
        query = match.group(1).strip()
        self.assertEqual(query, HOME_QUERY, "disable.sh's Home query changed")
        self.assertIn(query, self.cvte)

    def test_the_bullet_points_at_the_note(self):
        self.assertIn("devices/thomson-40fg2s14w.md", self.cvte)
        self.assertEqual(broken_links(TROUBLE), [])


class IndexTest(unittest.TestCase):
    def test_examples_readme_indexes_the_note(self):
        text = EXAMPLES_README.read_text(encoding="utf-8")
        section = text.split("Notes for models that have no tested list yet", 1)[1]
        self.assertIn("docs/devices/thomson-40fg2s14w.md", section)
        self.assertIn("40FG2S14W", section)

    def test_examples_readme_links_resolve(self):
        self.assertEqual(broken_links(EXAMPLES_README), [])


class CheckerTest(unittest.TestCase):
    """The readers above must report a real problem, not pass by reading nothing."""

    PAGE = ("- **One** first bullet\n"
            "  continued here\n"
            "  and here\n"
            "- **Two** second bullet\n"
            "## Section\n"
            "- **Three** third bullet\n")

    def test_bullet_keeps_its_continuation_lines(self):
        self.assertEqual(bullet(self.PAGE, "- **One**"),
                         "- **One** first bullet\n  continued here\n  and here")

    def test_bullet_stops_at_a_heading(self):
        self.assertNotIn("Three", bullet(self.PAGE, "- **Two**"))

    def test_bullet_raises_on_a_missing_bullet(self):
        with self.assertRaises(StopIteration):
            bullet(self.PAGE, "- **Missing**")

    def test_broken_links_reports_a_missing_target(self):
        page = ROOT / "tests" / "_link_probe.md"
        page.write_text("[gone](no-such-file.md)\n", encoding="utf-8")
        try:
            self.assertEqual(broken_links(page), ["no-such-file.md"])
        finally:
            page.unlink()

    def test_broken_links_ignores_external_and_anchors(self):
        page = ROOT / "tests" / "_link_probe.md"
        page.write_text("[a](https://example.com/x.md)\n[b](#some-heading)\n", encoding="utf-8")
        try:
            self.assertEqual(broken_links(page), [])
        finally:
            page.unlink()


if __name__ == "__main__":
    unittest.main()

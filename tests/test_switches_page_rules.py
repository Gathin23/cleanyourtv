"""The picture rules docs/switches.md states are the ones tests/test_switches.py enforces.

A key author follows the page's "Adding a key" bullets and never opens the checker, so each limit in
the bullets has to be the limit the checker holds and has to be stated there rather than somewhere
else on the page. The numbers are read out of the two checkers instead of being copied in, so a
checker that stops enforcing one fails here rather than letting the page drift.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "switches.md"
SWITCH_TEST = ROOT / "tests" / "test_switches.py"
THEME_TEST = ROOT / "tests" / "test_themes.py"
SECTION = "Adding a key"


def section(text, heading):
    """The lines under '## <heading>', up to the next '## ' heading."""
    out, on = [], False
    for line in text.split("\n"):
        if re.match(r"^##\s", line):
            on = line.strip() == f"## {heading}"
            continue
        if on:
            out.append(line)
    return "\n".join(out)


def bullets(block):
    """Bullet items of a section, a wrapped continuation joined onto the item it belongs to."""
    items, current = [], None
    for line in block.split("\n"):
        if re.match(r"^-\s", line):
            if current:
                items.append(current)
            current = line[1:].strip()
        elif current is not None and line[:1] in (" ", "\t") and line.strip():
            current += " " + line.strip()
        elif not line.strip() and current is not None:
            items.append(current)
            current = None
    if current:
        items.append(current)
    return items


def size_limit_bytes():
    """MAX_SCREENSHOT in tests/test_themes.py, the cap test_switches.py checks every picture against."""
    m = re.search(r"MAX_SCREENSHOT\s*=\s*(\d+)\s*\*\s*(\d+)", THEME_TEST.read_text(encoding="utf-8"))
    return int(m.group(1)) * int(m.group(2)) if m else None


def width_limit_px():
    """The width cap test_switches.py puts on a listed picture (1280x720 is plenty, 4K is not)."""
    m = re.search(r"assertLessEqual\(\s*width\s*,\s*(\d+)", SWITCH_TEST.read_text(encoding="utf-8"))
    return int(m.group(1)) if m else None


def aspect_checked():
    return bool(re.search(r"assertEqual\(\s*width\s*\*\s*9\s*,\s*height\s*\*\s*16",
                          SWITCH_TEST.read_text(encoding="utf-8")))


def stated_rules(text):
    """What the 'Adding a key' bullets say, lower-cased and joined, so a wrapped bullet reads whole."""
    return " ".join(bullets(section(text, SECTION))).lower()


class SwitchesPageRulesTest(unittest.TestCase):
    def test_the_checker_still_enforces_each_limit(self):
        self.assertIsNotNone(size_limit_bytes(), "test_themes.py no longer defines MAX_SCREENSHOT")
        self.assertIsNotNone(width_limit_px(),
                             "test_switches.py no longer caps the width: check the page and this test")
        self.assertTrue(aspect_checked(), "test_switches.py no longer checks the 16:9 aspect")

    def test_the_size_bullet_states_the_cap(self):
        limit = size_limit_bytes()
        self.assertIn(f"{limit // 1024} kb", stated_rules(PAGE.read_text(encoding="utf-8")),
                      "the Adding a key bullets do not say the size cap the checker enforces")

    def test_the_size_bullet_states_the_width_cap(self):
        limit = width_limit_px()
        self.assertIn(str(limit), stated_rules(PAGE.read_text(encoding="utf-8")),
                      f"the Adding a key bullets do not say a picture may be no wider than {limit} px: "
                      "a 4K screencap that fits every other rule fails the checker")

    def test_the_size_bullet_states_the_aspect(self):
        self.assertIn("16:9", stated_rules(PAGE.read_text(encoding="utf-8")),
                      "the Adding a key bullets do not say 16:9")

    def test_the_size_bullet_states_the_formats(self):
        rules = stated_rules(PAGE.read_text(encoding="utf-8"))
        self.assertIn("jpeg", rules, "the Adding a key bullets do not name JPEG")
        self.assertIn("png", rules, "the Adding a key bullets do not name PNG")

    def test_a_page_that_states_the_limits_passes(self):
        limit = width_limit_px()
        page = ("# Switches\n\nEvery new key gets a screenshot.\n\n## Adding a key\n\n"
                f"- From a real TV, as a JPEG or PNG, 16:9, no wider than {limit} px, under "
                f"{size_limit_bytes() // 1024} KB.\n\n## Screenshots\n\n| Key | Screenshot |\n")
        rules = stated_rules(page)
        self.assertIn(str(limit), rules)
        self.assertIn(f"{size_limit_bytes() // 1024} kb", rules)

    def test_the_real_page_lists_at_least_three_bullets(self):
        items = bullets(section(PAGE.read_text(encoding="utf-8"), SECTION))
        self.assertGreaterEqual(len(items), 3, "the Adding a key bullets read as empty")

    def test_bullets_join_a_wrapped_line(self):
        block = "- From a real TV, as a JPEG, 16:9 and no wider\n  than 1920 px.\n- Blur names.\n"
        self.assertEqual(bullets(block), ["From a real TV, as a JPEG, 16:9 and no wider than 1920 px.",
                                          "Blur names."])

    def test_a_bullet_in_another_section_is_not_read(self):
        page = ("## Screenshots\n\n- no wider than 1920 px, under 300 KB.\n\n"
                "## Adding a key\n\n- From a real TV.\n")
        self.assertEqual(stated_rules(page), "from a real tv.")

    def test_a_limit_in_the_prose_does_not_count(self):
        page = ("## Adding a key\n\nA picture no wider than 1920 px, under 300 KB.\n\n"
                "- From a real TV.\n")
        self.assertNotIn("1920", stated_rules(page))

    def test_a_page_without_the_section_reads_as_no_bullets(self):
        self.assertEqual(stated_rules("# Switches\n\nNothing here.\n"), "")


if __name__ == "__main__":
    unittest.main()

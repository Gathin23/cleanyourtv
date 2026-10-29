"""Pins the G09 key-repeat note and the warning that now sits in front of INPUT_KEY_SERVICE=1 (#763).

Enabling a key helper used to read as a free upgrade: the helpers filter the buttons that were lost with
the stock launcher and the docs said nothing about a cost. On a TCL 55P71B Pro (G09, Google TV 12) a bound
key-filtering service costs the remote's key auto-repeat - holding volume steps once per press - and it is
all or nothing, so the app buttons and a ramping volume cannot both be had on that build. The trap is that
the G09 example's install line passes INPUT_KEY_SERVICE=1 without saying so: an owner who follows it and
then finds the volume button "broken" has no reason to look at the helper he just enabled.

These tests keep the bisect (with its control), the way back to either behaviour, the three choices, the
honest limits, and the pass-through claim pinned to the helpers' own code so that a service which starts
consuming every key fails here instead of quietly invalidating the note. Whitespace is flattened before
matching because every file below wraps its prose.
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "tcl-55p71b-g09-key-repeat.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
PROMPT = ROOT / "PROMPT.md"
EXAMPLES = ROOT / "examples" / "README.md"
G09_README = ROOT / "examples" / "tcl-g09-google-tv" / "README.md"
INPUT_SERVICE = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "InputKeyService.java"
HOME_SERVICE = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "HomeKeyService.java"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"
CONFIG_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Config.java"


def flat(path):
    return re.sub(r"\s+", " ", path.read_text(encoding="utf-8"))


def links(path):
    """Every non-http link target in a file, resolved, so a moved page fails instead of 404ing for readers."""
    text = path.read_text(encoding="utf-8")
    return [str((path.parent / target).resolve()) for target in re.findall(r"\]\(([^)\s]+)\)", text)
            if not target.startswith(("http", "#"))]


def sections(text):
    """(heading, flattened body) for every '## ' section of a page.

    A needle anywhere below a heading is not proof that the section carries it: `section` above runs to
    the end of the page, which is why a fact that moved into the next section still reads as present.
    """
    out = []
    for block in re.split(r"(?m)^## ", text)[1:]:
        heading, _, body = block.partition("\n")
        out.append(("## " + heading.strip(), re.sub(r"\s+", " ", body)))
    return out


class KeyRepeatNoteTest(unittest.TestCase):
    def setUp(self):
        self.page = NOTE.read_text(encoding="utf-8")
        self.flat = flat(NOTE)

    def test_names_the_unit_and_the_build_not_the_family(self):
        self.assertIn("#763", self.flat)
        self.assertIn("55P71B Pro", self.flat)
        self.assertIn("`G09`", self.flat)
        self.assertIn("STT2.230203.001.AS23", self.flat)
        self.assertIn("Android 12", self.flat)
        self.assertIn("the claim belongs to the build, not to \"G09\"", self.flat)

    def test_keeps_the_bisect_and_its_control(self):
        for row in (
            "both `HomeKeyService` and `InputKeyService` | one step per press",
            "`HomeKeyService` alone | one step per press",
            "`InputKeyService` alone | one step per press",
            "`\"\"`, with `accessibility_enabled 0` | ramps continuously",
        ):
            with self.subTest(row=row):
                self.assertIn(row, self.flat)
        self.assertIn("All or nothing", self.flat)

    def test_names_the_capability_that_is_the_common_factor(self):
        self.assertIn("capabilities=8", self.flat)
        self.assertIn("CAPABILITY_CAN_FILTER_KEY_EVENTS", self.flat)
        self.assertIn("dumpsys accessibility", self.flat)
        self.assertIn("settings get secure enabled_accessibility_services", self.flat)

    def test_the_way_back_does_not_tell_people_to_guess(self):
        self.assertIn("write back what the read printed", self.flat)
        self.assertIn("unbinds TalkBack", self.flat,
                      "clearing the list drops every other accessibility service too")
        self.assertIn("theme.sh", self.flat)
        self.assertIn("INPUT_KEY_SERVICE=1", self.flat)
        self.assertNotIn("equivalent", self.flat,
                         "there is no 'equivalent' list to regenerate, only the string that was there")

    def test_the_launcher_is_exonerated_by_its_own_code(self):
        self.assertIn("The launcher is not the one eating the repeats", self.flat)
        self.assertIn("return `false` for keys they do not map", self.flat)
        input_service = flat(INPUT_SERVICE)
        home_service = flat(HOME_SERVICE)
        self.assertIn("if (!isInputKey(event.getKeyCode())) return false;", input_service,
                      "InputKeyService must keep passing unmatched keys through, or the note is wrong")
        self.assertIn("if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;", home_service,
                      "HomeKeyService must keep passing unmatched keys through, or the note is wrong")
        self.assertIn("not a captured log", self.flat,
                      "the platform-filter reading is inference until an onKeyEvent line exists")

    def test_records_the_trade_with_the_keycodes_that_make_it_real(self):
        for needle in ("`KEYCODE_NOTIFICATION` 83", "scancode 561", "4062", "scancode 472", "4099", "4065"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.flat)
        self.assertIn("cannot both be had", self.flat)
        self.assertIn("KeyDownBehaviorReceiver", self.flat)

    def test_offers_the_three_ways_out_and_a_toggle(self):
        self.assertIn("## Choices", self.page)
        self.assertIn("Keep the repeat and skip the helpers", self.flat)
        self.assertIn("Keep the buttons and accept stepwise volume", self.flat)
        self.assertIn("Keep launcherx enabled and bind no helper", self.flat)
        self.assertIn("never both at once", self.flat)

    def test_limits_stay_honest(self):
        self.assertIn("## Limits", self.page)
        for needle in ("One unit, one firmware build", "Only volume up/down was held", "No cold boot",
                       "No launcher-side fix is offered", "does the same is untested"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.flat)

    def test_links_resolve(self):
        for target in links(NOTE):
            with self.subTest(target=target):
                self.assertTrue(Path(target).exists(), "relative link target does not exist")


class EntryPointsTest(unittest.TestCase):
    def test_example_warns_before_the_install_line(self):
        text = flat(G09_README)
        index = text.index("`INPUT_KEY_SERVICE=1`")
        warning = text.index("Ask for that only if you want the buttons more than a ramping volume")
        self.assertLess(index, warning, "the warning has to follow the install line it qualifies")
        self.assertIn("moves one step instead of repeating", text)
        self.assertIn(str(NOTE.resolve()), links(G09_README))
        self.assertIn("leave launcherx enabled", text)

    def test_troubleshooting_section_names_the_symptom_and_the_trade(self):
        text = flat(TROUBLE)
        heading = "## A key helper is bound and a held remote button stopped repeating"
        self.assertEqual(text.count(heading), 1)
        start = text.index(heading)
        section = text[start:]
        for needle in ("one step per press instead of ramping", "capabilities=8", "4062",
                       "INPUT_KEY_SERVICE=1", "unbinds TalkBack", "tcl-55p71b-g09-key-repeat.md"):
            with self.subTest(needle=needle):
                self.assertIn(needle, section)
        self.assertEqual(TROUBLE.read_text(encoding="utf-8").count(heading), 1)

    def test_prompt_asks_before_enabling_a_helper(self):
        text = flat(PROMPT)
        self.assertIn("on a TCL G09 build (Google TV 12) ask me to hold volume up before", text,
                      "the check is scoped to the build the evidence came from")
        self.assertIn("all or nothing", text)
        self.assertIn("docs/devices/tcl-55p71b-g09-key-repeat.md", text)
        self.assertIn("say which one I am giving up", text)
        self.assertIn("On other sets this was not measured", text,
                      "one unit is not a rule for every TV")

    def test_examples_index_lists_the_note(self):
        text = EXAMPLES.read_text(encoding="utf-8")
        self.assertIn(str(NOTE.resolve()), links(EXAMPLES))
        bullets = [line for line in text.split("\n") if line.startswith("- [TCL 55P71B Pro]")]
        self.assertEqual(len(bullets), 1, "exactly one index bullet for this note")
        self.assertIn("all or nothing", bullets[0])
        self.assertIn("no debloat list of its own", bullets[0])


class OneSectionForTheTradeTest(unittest.TestCase):
    """The switch that buys the repeat back belongs in the same section as the evidence.

    docs/TROUBLESHOOTING.md used to carry this trade twice: a short "Held-down remote keys do not
    repeat" section that named `key_filter` and nothing else, and, 130 lines further down, the section
    with the G09/MOSR4 bisect, `capabilities=8` and the link to the note - which never mentioned the
    switch. A reader who found either one got half of it, and the half that named the switch said
    "there is no way to keep the buttons and the repeat" without the build, the bisect or the limits.
    """

    def setUp(self):
        self.found = sections(TROUBLE.read_text(encoding="utf-8"))

    def test_the_switch_is_documented_in_exactly_one_section(self):
        naming = [heading for heading, body in self.found if "key_filter" in body]
        self.assertEqual(len(naming), 1,
                         f"key_filter is documented in {len(naming)} sections: {naming}")

    def test_that_section_carries_the_evidence_and_the_note(self):
        for heading, body in self.found:
            if "key_filter" not in body:
                continue
            for needle in ("capabilities=8", "INPUT_KEY_SERVICE=1", "FLAG_REQUEST_FILTER_KEY_EVENTS",
                           "tcl-55p71b-g09-key-repeat.md"):
                with self.subTest(needle=needle):
                    self.assertIn(needle, body, f"{heading} names key_filter but not {needle}")

    def test_the_switch_is_one_the_launcher_reads(self):
        self.assertIn('<bool name="key_filter">', CONFIG_XML.read_text(encoding="utf-8"),
                      "the doc names a key_filter switch config.xml does not define")
        self.assertIn("R.bool.key_filter", CONFIG_JAVA.read_text(encoding="utf-8"),
                      "the doc names a key_filter switch the launcher never reads")


class TheSectionReaderTest(unittest.TestCase):
    """sections() itself, so the two tests above cannot pass by finding no sections."""

    def test_returns_one_entry_per_heading_with_its_own_body(self):
        found = dict(sections("intro\n## A\nbody a\n\n## B\nbody b\n"))
        self.assertEqual(list(found), ["## A", "## B"])
        self.assertIn("body a", found["## A"])
        self.assertNotIn("body b", found["## A"])

    def test_a_wrapped_sentence_is_flattened_so_it_still_matches(self):
        found = dict(sections("## A\ntools/tv.py 'settings get secure\nenabled_accessibility_services'\n"))
        self.assertIn("settings get secure enabled_accessibility_services", found["## A"])

    def test_a_page_without_headings_has_no_sections(self):
        self.assertEqual(sections("just prose, no headings\n"), [])


if __name__ == "__main__":
    unittest.main()

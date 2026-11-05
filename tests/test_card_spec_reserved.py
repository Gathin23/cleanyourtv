"""docs/specs/card.md's reserved names, read against the code that does the refusing.

A module author picks a label from the contract, so the contract has to name every label the
launcher will refuse on its own account. The spec listed nine, and two were missing: `App library`,
the name of the library pill, and `Choose default home`, the title of the default-home dialog. Both
come from `Cards.uiNames()`, which folds the launcher's own strings in the TV's language, so either
one is refused with "label '...' is reserved" by a rule the author never read.

So the list is derived here instead of trusted: the resource ids in `Cards.uiNames()`, resolved to
their English strings, plus the hard-coded set in `Cards.RESERVED_EXACT` and the `hdmi` prefix, must
all appear in the spec's "Reserved names" section, and every name the spec lists must be one the
launcher actually refuses (so a name cannot be invented either). Names are compared in the launcher's
own folded form - lower case, ASCII letters and digits only - so `Wi-Fi`, `WiFi` and `wi-fi` are one
name and rewording around the list stays free.

Only the English strings can be checked here: a translation of `App library` is refused too, and no
table can hold every language.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "docs/specs/card.md"
CARDS = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/Cards.java"
STRINGS = ROOT / "launcher/app/src/main/res/values/strings.xml"
SECTION = "### Reserved names"


def fold(name):
    """The launcher's folded form: `Cards.fold()` keeps only ASCII letters and digits, lower case."""
    return "".join(c for c in name.lower() if ("a" <= c <= "z") or ("0" <= c <= "9"))


def section(markdown, heading=SECTION):
    """The lines under `heading`, up to the next heading of the same or a higher level."""
    lines = markdown.splitlines()
    for start, line in enumerate(lines):
        if line.strip() == heading:
            level = len(line) - len(line.lstrip("#"))
            body = []
            for line in lines[start + 1:]:
                stripped = line.lstrip()
                if stripped.startswith("#") and len(stripped) - len(stripped.lstrip("#")) <= level:
                    break
                body.append(line)
            return body
    return []


def named(markdown):
    """{folded name: name as written} for every `backticked` name in the reserved-names section."""
    found = {}
    in_fence = False
    for line in section(markdown):
        if line.strip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        for token in re.findall(r"`([^`]+)`", line):
            found.setdefault(fold(token), token)
    return found


def ui_names(strings, java):
    """The English text of every string `Cards.uiNames()` folds."""
    body = java.split("private static Set<String> uiNames(", 1)[-1].split("\n    }", 1)[0]
    ids = re.findall(r"R\.string\.([A-Za-z0-9_]+)", body)
    if not ids:
        raise AssertionError("Cards.uiNames() names no resources; has it been rewritten?")
    out = {}
    for name in ids:
        match = re.search(r'<string name="%s">(.*?)</string>' % re.escape(name), strings, re.S)
        if not match:
            raise AssertionError(f"values/strings.xml has no string named {name}")
        text = match.group(1).replace("\\'", "'").replace("&amp;", "&")
        out[fold(text)] = text
    return out


def hardcoded(java):
    """(names in RESERVED_EXACT, RESERVED_PREFIX) as the launcher compares them, already folded."""
    block = java.split("RESERVED_EXACT = new HashSet<>(Arrays.asList(", 1)[-1].split("));", 1)[0]
    names = {fold(n) for n in re.findall(r'"([^"]+)"', block)}
    if not names:
        raise AssertionError("Cards.RESERVED_EXACT has no names; has it been rewritten?")
    prefix = re.search(r'RESERVED_PREFIX = "([^"]+)"', java)
    if not prefix:
        raise AssertionError("Cards.RESERVED_PREFIX is gone; has it been rewritten?")
    return names, fold(prefix.group(1))


def reserved_anywhere(java):
    """The words refused anywhere in a label (`verified`, `official`)."""
    block = java.split("RESERVED_ANYWHERE = {", 1)[-1].split("};", 1)[0]
    return {fold(n) for n in re.findall(r'"([^"]+)"', block)}


class ReservedNames(unittest.TestCase):

    def setUp(self):
        self.spec = SPEC.read_text(encoding="utf-8")
        self.java = CARDS.read_text(encoding="utf-8")
        self.strings = STRINGS.read_text(encoding="utf-8")
        self.listed = named(self.spec)

    def test_section_is_read(self):
        """The section and its names are found, so the tests below cannot pass by reading nothing."""
        self.assertGreaterEqual(len(section(self.spec)), 10)
        self.assertGreaterEqual(len(self.listed), 8, self.listed)

    def test_every_ui_name_is_listed(self):
        """Each string Cards.uiNames() folds is named in the spec (on main: App library, Choose default home)."""
        missing = {folded: text for folded, text in ui_names(self.strings, self.java).items()
                   if folded not in self.listed}
        self.assertEqual({}, missing, "reserved by Cards.uiNames() and not in the spec: "
                         + ", ".join(sorted(missing.values())))

    def test_every_hardcoded_name_is_listed(self):
        """RESERVED_EXACT and the HDMI prefix are named too, or the spec is short of the code."""
        names, prefix = hardcoded(self.java)
        missing = sorted(n for n in names if n not in self.listed)
        self.assertEqual([], missing, "reserved in Cards.java and not in the spec")
        self.assertIn(prefix, self.listed, "the HDMI prefix is not named in the spec")

    def test_no_name_is_invented(self):
        """Every name the spec lists is one the launcher refuses, so the contract cannot over-promise."""
        names, prefix = hardcoded(self.java)
        known = names | set(ui_names(self.strings, self.java)) | reserved_anywhere(self.java)
        invented = sorted(self.listed[k] for k in self.listed if k not in known and k != prefix)
        self.assertEqual([], invented, "named in the spec and refused by nothing in Cards.java")

    def test_app_library_pill_is_named(self):
        """The regression itself: the library pill's own name is a reserved label."""
        self.assertIn("applibrary", self.listed)

    def test_default_home_dialog_is_named(self):
        """And so is the default-home dialog, which the table used to skip."""
        self.assertIn("choosedefaulthome", self.listed)


class TheChecker(unittest.TestCase):
    """The comparisons above, against small documents written here, so each rule is known to work."""

    PAGE = "### Reserved names\n\nA label is rejected when:\n\n- it is `Home` or `Wi-Fi`;\n\n## Limits\n\n"

    def test_missing_name_is_reported(self):
        listed = named(self.PAGE)
        self.assertNotIn("applibrary", listed)
        self.assertIn("home", listed)

    def test_folded_spelling_counts(self):
        """`App-library`, `APP LIBRARY` and `App library` are one name, as they are in the launcher."""
        for spelling in ("App library", "App-library", "applibrary", "APP LIBRARY"):
            self.assertIn("applibrary", named(f"### Reserved names\n\n- it is `{spelling}`;\n"))

    def test_heading_bounds_the_section(self):
        """A name after the next heading is not part of this section."""
        self.assertNotIn("limits", named(self.PAGE))
        self.assertNotIn("applibrary", named("## Limits\n\n`App library`\n\n### Reserved names\n\n- `Home`\n"))

    def test_fenced_block_is_not_read(self):
        page = "### Reserved names\n\n- it is `Home`;\n\n```xml\ncard label=\"App library\"\n```\n"
        self.assertEqual({"home": "Home"}, named(page))

    def test_reading_nothing_is_visible(self):
        self.assertEqual([], section("### Something else\n\n- `Home`\n"))
        self.assertEqual({}, named("### Reserved names\n\nNo names here.\n"))


if __name__ == "__main__":
    unittest.main()

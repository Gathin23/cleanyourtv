"""The translation recipe names the per-language files beside strings.xml, and those files stay whole.

`values-*/` holds more than `strings.xml`. German needs the date on two lines (`bools.xml`, its
weekday and month names are longer than the English ones), Vietnamese swaps the bundled font for the
system one because Outfit has no glyphs for 96 of its 134 accented letters (`fonts.xml`, #833), and
Chinese and Vietnamese name the lunar months and stems themselves (`lunar.xml`). A translator whose
language needs one of those reads `docs/workflows/translation.md` and nothing else, so the recipe has
to name every file the tree already overrides; the set is read from the folders themselves rather
than listed here, so a fourth one is covered the day it lands.

The second half holds those files to the default: a resource name `values/<file>` does not define is
never read by the launcher, and `Lunar.java` reaches into the two lunar lists by position
(`stems[Math.floorMod(year + 6, 10)]`, `branches[Math.floorMod(year + 8, 12)]`), so one item short is
an ArrayIndexOutOfBoundsException on a TV running that language and nothing anywhere else.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "launcher/app/src/main/res"
PAGE = ROOT / "docs/workflows/translation.md"
LISTS = ("lunar_stems", "lunar_branches")


def locale_files(res=RES):
    """{file name: [locale folder, ...]} for every file a values-* folder adds beside strings.xml."""
    found = {}
    for folder in sorted(res.glob("values-*")):
        for path in sorted(folder.glob("*.xml")):
            if path.name != "strings.xml":
                found.setdefault(path.name, []).append(folder.name)
    return found


def names(path):
    """{resource name: item count} for one resource file; a plain string counts as one item."""
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        raise AssertionError(f"{path}: not valid XML ({e})") from e
    out = {}
    for node in root:
        if "name" not in node.attrib:
            continue
        out[node.attrib["name"]] = len(list(node)) if node.tag in ("string-array", "array") else 1
    return out


def missing_from_page(text, files):
    """The file names a page never mentions; each name has to appear as itself, not as a prefix."""
    return [name for name in sorted(files) if not re.search(r"(?<![\w.-])" + re.escape(name) + r"(?![\w-])", text)]


def unknown_names(default, mine):
    """Names a locale file defines that the default does not: the launcher never asks for those."""
    return sorted(set(mine) - set(default))


def short_lists(default, mine, lists=LISTS):
    """(name, default count, locale count) for a translated list that lost or gained an item."""
    out = []
    for name in lists:
        if name in default and name in mine and default[name] != mine[name]:
            out.append((name, default[name], mine[name]))
    return out


class TranslationRecipeTest(unittest.TestCase):
    def test_every_per_language_file_is_named_by_the_recipe(self):
        files = locale_files()
        self.assertGreaterEqual(len(files), 3, "read too few per-language files to prove anything")
        self.assertGreaterEqual(sum(len(v) for v in files.values()), 8)
        for name in missing_from_page(PAGE.read_text(encoding="utf-8"), files):
            with self.subTest(file=name):
                self.fail(f"docs/workflows/translation.md never names {name}, which "
                          f"{', '.join(files[name])} override: a translator whose language needs it "
                          f"has nothing to read")

    def test_a_per_language_file_is_one_the_default_carries(self):
        # A values-xx file that values/ does not have overrides nothing: it is a typo or a leftover.
        for name, locales in sorted(locale_files().items()):
            with self.subTest(file=name):
                self.assertTrue((RES / "values" / name).is_file(),
                                f"{', '.join(locales)} carry {name}, which values/ does not")

    def test_a_per_language_file_only_names_resources_the_default_has(self):
        read = 0
        for name, locales in sorted(locale_files().items()):
            default = names(RES / "values" / name)
            for locale in locales:
                with self.subTest(file=f"{locale}/{name}"):
                    unknown = unknown_names(default, names(RES / locale / name))
                    self.assertEqual([], unknown, f"{locale}/{name} defines {unknown}, which the "
                                                  f"launcher never asks for: values/{name} has no such name")
                read += 1
        self.assertGreaterEqual(read, 8, "read too few per-language files to prove anything")

    def test_lunar_lists_keep_the_default_length(self):
        # Lunar.java indexes both lists by position, so a short one crashes that language only.
        default = names(RES / "values/lunar.xml")
        self.assertEqual(10, default.get("lunar_stems"), "values/lunar.xml lost its stems")
        self.assertEqual(12, default.get("lunar_branches"), "values/lunar.xml lost its branches")
        read = 0
        for folder in sorted(RES.glob("values-*")):
            path = folder / "lunar.xml"
            if not path.is_file():
                continue
            with self.subTest(locale=folder.name):
                self.assertEqual([], short_lists(default, names(path)))
            read += 1
        self.assertGreaterEqual(read, 3, "read too few lunar translations to prove anything")


class TranslationRecipeReaderTest(unittest.TestCase):
    """The three helpers above, against files written here, so neither can pass by reading nothing."""

    def test_a_file_the_page_omits_is_reported(self):
        files = {"bools.xml": ["values-de"], "fonts.xml": ["values-vi"], "lunar.xml": ["values-vi"]}
        self.assertEqual(["bools.xml", "fonts.xml"],
                         missing_from_page("A `lunar.xml` gives the lunar months their own names.", files))

    def test_a_page_that_names_one_file_covers_only_that_one(self):
        files = {"bools.xml": ["values-de"], "lunar.xml": ["values-vi"]}
        self.assertEqual([], missing_from_page("`lunar.xml` and `bools.xml`", files))
        self.assertEqual(["bools.xml"], missing_from_page("`lunar.xml`", files))

    def test_a_name_that_only_starts_the_same_is_not_a_mention(self):
        # `lunar.xml` must not be read as `lunar.xml.bak`, nor `bools.xml` as `mybools.xml`.
        files = {"bools.xml": ["values-de"], "lunar.xml": ["values-vi"]}
        self.assertEqual(["bools.xml"], missing_from_page("copy `lunar.xml.bak` over `mybools.xml`", files))

    def test_a_locale_name_the_default_lacks_is_reported(self):
        self.assertEqual(["date_single_line2"], unknown_names({"date_single_line": 1},
                                                            {"date_single_line": 1, "date_single_line2": 1}))
        self.assertEqual([], unknown_names({"date_single_line": 1}, {"date_single_line": 1}))

    def test_a_short_list_is_reported_with_both_counts(self):
        default = {"lunar_stems": 10, "lunar_branches": 12}
        self.assertEqual([("lunar_branches", 12, 11)], short_lists(default, {"lunar_stems": 10, "lunar_branches": 11}))
        self.assertEqual([], short_lists(default, dict(default)))

    def test_the_reader_counts_arrays_and_plain_resources(self):
        path = RES / "values/lunar.xml"
        read = names(path)
        self.assertEqual(10, read["lunar_stems"])
        self.assertEqual(12, read["lunar_branches"])
        self.assertEqual(1, names(RES / "values/bools.xml")["date_single_line"])


if __name__ == "__main__":
    unittest.main()

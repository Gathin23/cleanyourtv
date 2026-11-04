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

Step 2 also names, per file, the folders that carry it - "(`values-zh-rCN`, `values-vi`, `values-ko`)" for
`lunar.xml` - and those lists had fallen behind the tree: `values-zh-rTW` ships a `lunar.xml` of its own
and was not named, and `values-ast` and `values-b+es+419` already set `date_single_line` false beside the
five languages the `bools.xml` bullet listed, so a translator reading the page for Asturian was told the
tree keeps one line there. The list is read here as the `values-*` names inside that file's bullet and
compared with the folders on disk, so a locale that adds one of these files, or a bullet that gains a
folder the tree does not have, fails instead of leaving the sentence to drift.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "launcher/app/src/main/res"
PAGE = ROOT / "docs/workflows/translation.md"
LISTS = ("lunar_stems", "lunar_branches")
# A step-2 bullet and the `values-*` folder names it writes in backticks.
BULLET = re.compile(r"^- `([\w.]+\.xml)`:")
FOLDER = re.compile(r"`(values-[^`]+)`")


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


def named_folders(text):
    """{file name: {locale folder, ...}} the step-2 bullets name for each per-language file.

    A bullet runs from its `- ` line until the next bullet, a blank line or a line that starts at the
    margin, so the step's own prose ("`values-sr-Latn` is not a folder it reads") stays out of a list.
    """
    out, current = {}, None
    for line in text.split("\n"):
        head = BULLET.match(line.strip())
        if head:
            current = head.group(1)
            out.setdefault(current, set())
        elif current is not None and (not line.strip() or not line.startswith("  ")):
            current = None
        if current is not None:
            out[current].update(FOLDER.findall(line))
    return out


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

    def test_the_recipe_names_every_folder_that_carries_each_file(self):
        # The bullet's parenthesis is the list a translator reads for their own language, so it is
        # held to the folders on disk rather than being prose that can fall behind.
        named = named_folders(PAGE.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(named), 3, "read too few step-2 bullets to prove anything")
        files = locale_files()
        for name, folders in sorted(named.items()):
            with self.subTest(file=name):
                self.assertIn(name, files, f"the step-2 bullet for {name} names no folder that carries it")
                self.assertEqual(sorted(files[name]), sorted(folders),
                                 f"docs/workflows/translation.md names {sorted(folders)} for {name}, "
                                 f"and the tree carries it in {sorted(files[name])}")

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

    def test_a_folder_a_bullet_omits_is_reported(self):
        text = ("2. A language may also need one of the other files `values/` carries.\n"
                "   - `lunar.xml`: the lunar date format (`values-zh-rCN`, `values-vi`).\n"
                "   - `fonts.xml`: the system families (`values-vi`).\n"
                "3. Add the locale to `values-sr-Latn`.\n")
        self.assertEqual({"lunar.xml": {"values-zh-rCN", "values-vi"}, "fonts.xml": {"values-vi"}},
                         named_folders(text))

    def test_a_folder_outside_a_bullet_is_not_part_of_a_list(self):
        text = ("   - `bools.xml`: two lines (`values-de`).\n"
                "\n"
                "`values-pl` is not named here.\n")
        self.assertEqual({"bools.xml": {"values-de"}}, named_folders(text))

    def test_a_resource_name_the_default_lacks_is_reported(self):
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

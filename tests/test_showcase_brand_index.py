"""SHOWCASE.md's brand index has one line per brand, and every row has to be reachable from it.

"Find a project by TV brand" is the index a reader uses to answer "which of these runs on my set", and
docs/workflows/showcase.md asks for a new row to be added under its TV brand. Two things went wrong
there, both caught by the tests below.

A brand named twice: when the Bluetooth pill fork landed (row 47, nikhiltalreja's tree) a second
`- **Xiaomi:**` line was written below XGIMI instead of the fork being added to the Xiaomi line already
there, so the page carried Xiaomi twice - the first line listed 14 projects without the fork, the
second all 15, and a reader who stopped at the first Xiaomi line never saw the fork.

A row the index never links: the MiTV4-ANSM0 launcher (qqwersde's tree, the same board as the Xmen
Android 6 port that *is* on the Xiaomi line) and the phone app "Clean Your TV app" (built for Android TV
sets generally, so no single brand) were both written into the tables and never into the index - which is
what happens when the recipe says only "under its TV brand" and the project has none. Both are in the
index now, and step 1 of the recipe names the generic line for a project like that.

These tests read the index into brand -> links and the tables into rows. A brand named twice fails and
prints the links only one of the two lines carries; a row no index line links fails with its line
number. Rows whose TV cell names no brand at all (NO_BRAND below) are exempt, and a test keeps each
exemption honest: one that is no longer on the page fails instead of hiding a row forever. The brand
labels are not compared in any order: the list is grouped by hand and is not alphabetical today.

A row is matched to an index link by its address, not its label, so renaming a project on one side stays
free; a trailing page name (`.../tree/x/FORK.md`) is ignored, so the two can address the same tree
differently.

Run from the repository root: python3 -m unittest discover -s tests -v
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "SHOWCASE.md"
RECIPE = ROOT / "docs" / "workflows" / "showcase.md"
SECTION = "## Find a project by TV brand"
END = "## Add yours"

# `- **Xiaomi:** [One](url), [Two](url)`: the bullet docs/workflows/showcase.md asks a row to be added to.
ENTRY = re.compile(r"^- \*\*([^*]+?):\*\*(.*)$")
LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)\s]+)\)")
# `| [Name](url) | what it is | the TV | the author |`: a table row whose first cell links the project.
ROW = re.compile(r"^\|\s*\[[^\]]*\]\((https?://[^)\s]+)\)")
# The page name some rows end their address with: .../tree/tv/local-video-wallpaper-and-pointer/FORK.md
PAGE_NAME = re.compile(r"/[^/]*\.md$")

# Rows whose TV cell names no brand a reader could look themselves up under, with the reason. Keeping
# them here rather than on the page means every other unlinked row still fails the test below.
NO_BRAND = {
    "https://github.com/lionlev21/cleanyourtv/blob/tv-button-picker/REMOTE_PICKER.md":
        "its TV cell says 'device details private'",
    "https://github.com/iyts/cleanyourtv/tree/feat/picture-sound-panel":
        "its TV cell names a board ('Realtek-based set'), not a brand",
}


def index(lines):
    """[(line number, brand, [(name, url)])] for every bullet in the brand index."""
    try:
        start = next(n for n, line in enumerate(lines) if line.startswith(SECTION))
    except StopIteration:
        return []
    found = []
    for number in range(start + 1, len(lines)):
        line = lines[number]
        if line.startswith(END):
            break
        match = ENTRY.match(line)
        if match:
            found.append((number + 1, match.group(1).strip(), LINK.findall(match.group(2))))
    return found


def duplicates(entries):
    """{brand: [entries]} for every brand that is named more than once."""
    seen = {}
    for entry in entries:
        seen.setdefault(entry[1], []).append(entry)
    return {brand: group for brand, group in seen.items() if len(group) > 1}


def canonical(url):
    """A project's address with the page name and the blob/tree spelling taken out.

    `.../tree/x/FORK.md` and `.../blob/x/FORK.md` are the same tree, and the index and a row are free to
    address it either way.
    """
    url = PAGE_NAME.sub("", url.rstrip("/"))
    return url.replace("/blob/", "/tree/")


def same_project(one, two):
    """Equal once a trailing page name is dropped, or one address inside the other's tree."""
    one, two = canonical(one), canonical(two)
    return one == two or two.startswith(one + "/") or one.startswith(two + "/")


def rows(lines):
    """[(line number, url)] for every table row whose first cell links a project."""
    found = []
    for number, line in enumerate(lines, 1):
        match = ROW.match(line)
        if match:
            found.append((number, match.group(1)))
    return found


def unindexed(lines):
    """[(line number, url)] for the rows no line of the brand index links."""
    listed = [url for _, _, links in index(lines) for _, url in links]
    return [(number, url) for number, url in rows(lines) if not any(same_project(url, one) for one in listed)]


def generic_label(lines):
    """The index line for projects with no brand of their own: the one labelled 'Any ...'."""
    for _, brand, _ in index(lines):
        if brand.startswith("Any "):
            return brand
    return None


def steps(page):
    """The numbered steps of a recipe page, a wrapped step joined onto its first line."""
    found, current = [], None
    for line in page.splitlines():
        match = re.match(r"^(\d+)\. (.*)$", line)
        if match:
            if current:
                found.append(current)
            current = [int(match.group(1)), match.group(2)]
        elif current is not None and line.startswith("   ") and line.strip():
            current[1] += " " + line.strip()
        elif current is not None and not line.strip():
            continue
        elif current is not None:
            found.append(current)
            current = None
    if current:
        found.append(current)
    return found


class BrandIndex(unittest.TestCase):
    def setUp(self):
        self.lines = PAGE.read_text(encoding="utf-8").splitlines()
        self.entries = index(self.lines)

    def test_the_index_is_found(self):
        # A parser that reads nothing would make the tests below pass for any page.
        self.assertGreaterEqual(
            len(self.entries), 20, "the brand index should name the brands the page lists; the parser found fewer"
        )
        self.assertIn("Xiaomi", [brand for _, brand, _ in self.entries])
        self.assertIsNotNone(generic_label(self.lines), "no index line for projects with no brand of their own")

    def test_no_brand_is_named_twice(self):
        repeated = duplicates(self.entries)
        report = []
        for brand, group in repeated.items():
            first, second = group[0], group[-1]
            only_first = {url for _, url in first[2]} - {url for _, url in second[2]}
            only_second = {url for _, url in second[2]} - {url for _, url in first[2]}
            report.append(
                f"  {brand} is named on lines {', '.join(str(n) for n, _, _ in group)}: "
                f"only line {first[0]} carries {sorted(only_first) or 'nothing'}, "
                f"only line {second[0]} carries {sorted(only_second) or 'nothing'}"
            )
        self.assertEqual(
            [],
            list(repeated),
            "a brand is listed twice in 'Find a project by TV brand', so a project on one of the lines is\n"
            "hard to find: fold the links into the one line, under the brand. Add the new project to the\n"
            "line that is already there instead of writing a second one.\n" + "\n".join(report),
        )

    def test_every_entry_links_a_project(self):
        empty = [f"line {number}: {brand}" for number, brand, links in self.entries if not links]
        self.assertEqual([], empty, "these brand lines name no project: " + "; ".join(empty))

    def test_every_row_is_reachable_from_the_index(self):
        found = rows(self.lines)
        self.assertGreaterEqual(len(found), 90, "the page's project rows should be read; the parser found fewer")
        missing = [f"line {number}: {url}" for number, url in unindexed(self.lines) if url not in NO_BRAND]
        self.assertEqual(
            [],
            missing,
            "these rows are in SHOWCASE.md's tables but no line of 'Find a project by TV brand' links them,\n"
            "so a reader looking up their own TV misses the project: add each one to the line for the brand\n"
            "it was built on, or to the 'Any Android TV' line when it names no single brand.\n" + "\n".join(missing),
        )

    def test_each_exempt_row_is_still_on_the_page(self):
        found = [url for _, url in rows(self.lines)]
        stale = [
            f"{url} ({why})" for url, why in NO_BRAND.items() if not any(same_project(url, one) for one in found)
        ]
        self.assertEqual(
            [],
            stale,
            "these rows are exempted from the index test above but are no longer on the page: drop the\n"
            "exemption, or fix the address it was written with.\n" + "\n".join(stale),
        )


class TheRecipe(unittest.TestCase):
    def setUp(self):
        self.lines = PAGE.read_text(encoding="utf-8").splitlines()
        self.page = RECIPE.read_text(encoding="utf-8")

    def test_the_step_that_asks_for_the_index_names_the_generic_line(self):
        # A project with no brand of its own (a phone app, a generic add-on) has nowhere to go on a brand
        # line, and that is how "Clean Your TV app" was left out of the index.
        label = generic_label(self.lines)
        found = steps(self.page)
        self.assertGreaterEqual(len(found), 4, "the showcase recipe's numbered steps should be read")
        asking = [number for number, text in found if "Find a project by TV brand" in text]
        self.assertEqual(1, len(asking), f"exactly one step should ask for the brand index entry; read {asking}")
        step = dict(found)[asking[0]]
        self.assertIn(
            label,
            step,
            f"step {asking[0]} asks for the 'Find a project by TV brand' entry but does not name\n"
            f"'{label}', so a project that names no single brand has no line to be added to",
        )


class TheReaderItself(unittest.TestCase):
    """The repo-wide tests above are only worth their failures if the reader sees these shapes."""

    def test_a_brand_named_twice_is_reported(self):
        lines = [
            SECTION,
            "",
            "- **TCL:** [One](https://example.com/one)",
            "- **XGIMI:** [Two](https://example.com/two)",
            "- **TCL:** [One](https://example.com/one), [Three](https://example.com/three)",
            "",
            END,
        ]
        repeated = duplicates(index(lines))
        self.assertEqual(["TCL"], list(repeated))
        self.assertEqual([3, 5], [number for number, _, _ in repeated["TCL"]])

    def test_one_line_per_brand_is_quiet(self):
        lines = [SECTION, "- **TCL:** [One](https://example.com/one)", "- **Sony:** [Two](https://example.com/two)", END]
        self.assertEqual({}, duplicates(index(lines)))

    def test_a_comma_separated_line_is_read_into_every_link(self):
        lines = [SECTION, "- **TCL:** [One](https://example.com/1), [Two](https://example.com/2)", END]
        self.assertEqual(
            [("One", "https://example.com/1"), ("Two", "https://example.com/2")], index(lines)[0][2]
        )

    def test_a_brand_named_in_another_bullet_or_in_prose_is_not_an_entry(self):
        lines = [
            SECTION,
            "- [TCL](https://example.com/tcl) is mentioned in prose.",
            "- **TCL:** [One](https://example.com/one)",
            END,
        ]
        self.assertEqual(["TCL"], [brand for _, brand, _ in index(lines)])

    def test_the_section_stops_at_the_next_heading(self):
        lines = [SECTION, "- **TCL:** [One](https://example.com/one)", END, "- **TCL:** [Two](https://example.com/2)"]
        self.assertEqual(1, len(index(lines)))

    def test_a_page_without_the_section_reads_as_empty(self):
        self.assertEqual([], index(["# Made with cleanyourtv", "- **TCL:** [One](https://example.com/one)"]))

    def test_a_row_the_index_never_links_is_reported(self):
        lines = [
            "| [One](https://example.com/one) | what it is | TCL 43P615 | an author |",
            "",
            SECTION,
            "- **TCL:** [Two](https://example.com/two)",
            END,
        ]
        self.assertEqual([(1, "https://example.com/one")], unindexed(lines))

    def test_a_row_linked_by_another_page_of_its_own_tree_is_quiet(self):
        # The TCL line links .../tree/tv/local-video-wallpaper-and-pointer while the row links its FORK.md.
        lines = [
            "| [Fork](https://example.com/fork/FORK.md) | what it is | TCL | an author |",
            SECTION,
            "- **TCL:** [Fork](https://example.com/fork)",
            END,
        ]
        self.assertEqual([], unindexed(lines))

    def test_a_link_outside_the_index_section_is_not_an_entry(self):
        lines = [
            "| [One](https://example.com/one) | what it is | TCL | an author |",
            "## Home screens",
            "- **TCL:** [One](https://example.com/one)",
            SECTION,
            "- **Sony:** [Two](https://example.com/two)",
            END,
        ]
        self.assertEqual([(1, "https://example.com/one")], unindexed(lines))

    def test_two_projects_in_one_tree_are_not_the_same_project(self):
        self.assertFalse(same_project("https://example.com/fork", "https://example.com/fork-2"))
        self.assertTrue(same_project("https://example.com/fork", "https://example.com/fork/FORK.md"))

    def test_a_wrapped_step_is_joined_onto_its_first_line(self):
        page = '1. Add a row, and the project under its TV brand in\n   "Find a project by TV brand".\n2. Bump it.\n'
        found = steps(page)
        self.assertEqual(2, len(found))
        self.assertIn("Find a project by TV brand", found[0][1])


if __name__ == "__main__":
    unittest.main()

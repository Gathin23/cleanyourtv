"""themes/CREATE_A_THEME.md is reachable, and the theme pages agree with the launcher on which keys exist.

`themes/CREATE_A_THEME.md` is the step-by-step prompt a theme author hands to a coding agent with shell
access to the TV, and nothing linked it: `themes/README.md` is the page that author opens, and its "Adding a
theme" section sent them to a new folder and a row without naming the guide, so the page was reachable only
by listing the folder. The section links it now, and the first test below keeps every page in `themes/`
reachable from that README.

The second half is a claim about the launcher that a merge made wrong: `themes/retro/README.md` introduced
its `dock_band` line as "a launcher switch from the companion switches change" and told the reader that "on
a launcher build without it the theme still applies the rest and the key is simply ignored". `dock_band` is
a launcher switch now (`<bool name="dock_band">true</bool>` in config.xml, the change #1113 merged, with a
row in the Keys table and a picture in docs/switches.md), so that note sends an author looking for a build
they do not need. The page says both keys are on the main launcher, and the test refuses any theme page that
still explains a key the launcher carries as one from a build of its own.

The keys are not copied into this file: they come from the Keys table in themes/README.md, which
tests/test_theme_keys_config.py already holds to every key the launcher reads from config.xml, and from
config.xml itself for the second test, so a key the table stops carrying, or a launcher that starts carrying
one, moves these tests with it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "themes"
README = THEMES / "README.md"
GUIDE = THEMES / "CREATE_A_THEME.md"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"

# A row of the Keys table: | `key` | type | what it does |, the shape tests/test_theme_keys_config.py reads.
TABLE_ROW = re.compile(r"^\|\s*`([a-z][a-z0-9_]*)`\s*\|", re.M)
# A key a page names in backticks, the way both the Keys table and the theme pages spell one.
BACKTICK_KEY = re.compile(r"`([a-z][a-z0-9_]*)`")
# A setting the launcher carries: <bool name="key">true</bool>, whatever its type.
CONFIG_KEY = re.compile(r'name="([a-z][a-z0-9_]*)"')
# A fenced block, with the language it declares. A theme's config.json is the only JSON in the guide.
FENCE = re.compile(r"^```([a-zA-Z0-9]*)[ \t]*\n(.*?)^```", re.S | re.M)
# A markdown link or image destination, the form tests/test_markdown_links.py reads.
LINK = re.compile(r"\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
# Phrasings that tell a reader a key needs a launcher build that does not carry it.
OTHER_BUILD = ("companion", "own build", "build without it", "build that lacks",
               "build that does not have", "build of its own")


def table_keys(page):
    """Every key the Keys table lists in one page, in file order."""
    return set(TABLE_ROW.findall(page))


def config_keys(page):
    """Every setting config.xml carries, in file order."""
    return set(CONFIG_KEY.findall(page))


def json_blocks(page):
    """(index, text) for every fenced block the page declares as JSON."""
    return [(i, body) for i, (lang, body) in enumerate(FENCE.findall(page)) if lang.lower() == "json"]


def example_keys(page):
    """{key: block number} for the top-level keys of each of the page's JSON blocks.

    Only the object's own keys count: a key inside a value (`wallpaper_moods`' `morning`) is data for that
    key, not a setting of its own, and reporting it would make this test refuse a correct example.
    """
    out = {}
    for number, (_, body) in enumerate(json_blocks(page)):
        try:
            parsed = json.loads(body)
        except ValueError as e:
            raise AssertionError(f"a JSON block in the page does not parse: {e}") from e
        if not isinstance(parsed, dict):
            raise AssertionError(f"a JSON block in the page is a {type(parsed).__name__}, not an object")
        for key in parsed:
            out.setdefault(key, number)
    return out


def unknown_keys(page, known):
    """Keys in the page's examples that the Keys table does not carry, in file order."""
    return {key: block for key, block in example_keys(page).items() if key not in known}


def sentences(page):
    """The page's sentences, its wrapped lines joined, so a claim split over two lines is read whole."""
    return [s for s in re.split(r"(?<=[.!?])\s+", re.sub(r"\s+", " ", page)) if s]


def other_build_claims(page, carried):
    """(key, sentence) for each sentence that sends a reader to a build without a key the launcher carries."""
    out = []
    for sentence in sentences(page):
        if not any(phrase in sentence for phrase in OTHER_BUILD):
            continue
        for key in BACKTICK_KEY.findall(sentence):
            if key in carried:
                out.append((key, sentence.strip()))
    return out


def linked_pages(page):
    """The relative .md pages one page links, as resolved paths."""
    out = set()
    for target in LINK.findall(page):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        local = target.split("#")[0]
        if local.endswith(".md"):
            out.add(local)
    return out


def guide_pages(folder):
    """Every .md page sitting in a theme folder's top level, README.md aside."""
    return sorted(p.name for p in folder.glob("*.md") if p.name != "README.md")


class ThemeGuideKeysTest(unittest.TestCase):
    def setUp(self):
        self.page = GUIDE.read_text(encoding="utf-8")
        self.known = table_keys(README.read_text(encoding="utf-8"))

    def test_the_keys_table_was_read(self):
        # The Keys table is the source of truth here; a page this test could not read would make every
        # assertion below pass by finding nothing.
        self.assertGreaterEqual(len(self.known), 50,
                                f"the Keys table in themes/README.md lists {len(self.known)} keys, so this "
                                "test is reading the wrong table")

    def test_every_example_key_is_a_key_the_launcher_reads(self):
        blocks = json_blocks(self.page)
        self.assertGreaterEqual(len(blocks), 2,
                                "the guide should keep at least its time-of-day and its single-picture "
                                f"example, found {len(blocks)} JSON block(s)")
        found = example_keys(self.page)
        self.assertGreaterEqual(len(found), 10,
                                f"read {len(found)} keys out of the guide's examples, which is too few for a "
                                "config.json - the block format changed and this test is no longer reading it")
        unknown = unknown_keys(self.page, self.known)
        message = "\n".join(
            f"themes/CREATE_A_THEME.md block {number}: '{key}' is not in the Keys table of themes/README.md, "
            f"so a theme carrying it changes nothing on the TV"
            for key, number in unknown.items())
        self.assertEqual({}, unknown, message)

    def test_the_readme_links_every_theme_page(self):
        pages = guide_pages(THEMES)
        self.assertIn("CREATE_A_THEME.md", pages,
                      "the guide is the page this test exists for; if it moved, point this test at its new home")
        linked = linked_pages(README.read_text(encoding="utf-8"))
        missing = [name for name in pages if name not in linked]
        self.assertEqual([], missing,
                         "pages in themes/ that themes/README.md does not link: " + ", ".join(missing))

    def test_a_linked_page_resolves(self):
        for target in linked_pages(self.page):
            self.assertTrue((GUIDE.parent / target).exists(),
                            f"themes/CREATE_A_THEME.md links {target}, which is not there")

    def test_no_theme_page_sends_a_reader_to_a_build_without_a_key_the_launcher_has(self):
        carried = config_keys(CONFIG_XML.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(carried), 50,
                                f"read {len(carried)} settings out of config.xml, so this test is reading the "
                                "wrong file")
        reported = []
        for page in sorted(THEMES.rglob("*.md")):
            claims = other_build_claims(page.read_text(encoding="utf-8"), carried)
            reported += [f"{page.relative_to(ROOT)}: '{key}' is in config.xml, but the page offers a build "
                         f"without it: {sentence}" for key, sentence in claims]
        self.assertEqual([], reported, "\n".join(reported))


class ThemeGuideReaderTest(unittest.TestCase):
    """The readers above, on pages written here, so a reader that stops reading fails instead of passing."""

    def test_an_unknown_key_is_reported_with_its_block(self):
        page = ('# Guide\n\n```json\n{"background_color": "#000000"}\n```\n\n'
                '```json\n{"glass_style": "rim", "dock_band": false}\n```\n')
        self.assertEqual({"dock_band": 1}, unknown_keys(page, {"background_color", "glass_style"}))

    def test_known_keys_are_quiet(self):
        page = '```json\n{"columns": 5, "wallpaper": true}\n```\n'
        self.assertEqual({}, unknown_keys(page, {"columns", "wallpaper"}))

    def test_a_page_with_no_json_block_has_no_keys(self):
        page = "See `config.json` in [themes/retro/](retro/) for an example.\n"
        self.assertEqual({}, example_keys(page))

    def test_a_block_that_is_not_json_is_not_read(self):
        page = '```bash\ntools/theme.sh themes/aurora\n```\n'
        self.assertEqual([], json_blocks(page))

    def test_a_key_inside_a_value_is_not_a_key_of_its_own(self):
        page = '```json\n{"wallpaper_moods": {"morning": ["a.jpg"]}}\n```\n'
        keys = example_keys(page)
        self.assertIn("wallpaper_moods", keys)
        self.assertNotIn("morning", keys)

    def test_a_page_that_cannot_be_read_is_not_silent(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "themes"
            folder.mkdir()
            (folder / "README.md").write_text("| `columns` | int | layout |\n", encoding="utf-8")
            (folder / "CREATE_A_THEME.md").write_text("```json\n{\"dock_band\": false}\n```\n",
                                                     encoding="utf-8")
            known = table_keys((folder / "README.md").read_text(encoding="utf-8"))
            page = (folder / "CREATE_A_THEME.md").read_text(encoding="utf-8")
            self.assertEqual({"dock_band": 0}, unknown_keys(page, known))

    def test_the_link_reader_reads_relative_pages_only(self):
        page = ("[a](CREATE_A_THEME.md) [b](https://example.com/x.md) [c](retro/screenshot.jpg) "
                "[d](README.md#keys) [e](#adding-a-theme)\n")
        # A link may carry an anchor of its own; the page is still the target, as tests/test_markdown_links.py reads it.
        self.assertEqual({"CREATE_A_THEME.md", "README.md"}, linked_pages(page))

    def test_a_theme_page_nothing_links_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "themes"
            folder.mkdir()
            (folder / "README.md").write_text("# Themes\n", encoding="utf-8")
            (folder / "OLD_GUIDE.md").write_text("# Old\n", encoding="utf-8")
            pages = guide_pages(folder)
            linked = linked_pages((folder / "README.md").read_text(encoding="utf-8"))
            self.assertEqual(["OLD_GUIDE.md"], [name for name in pages if name not in linked])

    def test_the_config_reader_reads_settings_of_every_type(self):
        page = ('<resources>\n  <bool name="dock_band">true</bool>\n'
                '  <integer name="wallpaper_dim">10</integer>\n'
                '  <string name="background_color">#000000</string>\n</resources>\n')
        self.assertEqual({"dock_band", "wallpaper_dim", "background_color"}, config_keys(page))

    def test_a_claim_about_another_build_is_read_out_of_wrapped_lines(self):
        page = ("`glass_style` works on the main launcher. `dock_band` is a launcher switch from the\n"
                "companion switches change; on a launcher build without it the theme still applies the rest.\n")
        claims = other_build_claims(page, {"dock_band", "glass_style"})
        self.assertEqual(["dock_band"], [key for key, _ in claims])
        # The sentence is read whole, not cut at the line break: a claim wrapped over two lines is the case
        # this reader exists for.
        self.assertIn("on a launcher build without it", claims[0][1])

    def test_a_claim_about_a_key_the_launcher_does_not_carry_is_quiet(self):
        page = "`some_other_build_key` comes from a companion build of the launcher.\n"
        self.assertEqual([], other_build_claims(page, {"dock_band"}))

    def test_a_page_that_says_nothing_about_builds_is_quiet(self):
        page = "`dock_band` is `false`, so no dock band and no edge: tiles float on the wallpaper.\n"
        self.assertEqual([], other_build_claims(page, {"dock_band"}))


if __name__ == "__main__":
    unittest.main()

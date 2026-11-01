"""The game_day contract in docs/modules.md is read on every side that implements it.

docs/modules.md hands an app one contract: a `meta-data` named `com.gathin.tvhome.games` whose value is
the authority of a read-only provider, a query on `content://<authority>/turns`, and seven columns
answered by name - the launcher passes them as the projection, so a column renamed on one side comes
back as a null column on the TV rather than as an error anywhere. Three files carry that contract in
this tree: the page, `GameDay.java` (COLUMNS, META, the `turns` path, TURN_DEFAULT and the 10-120
clamp) and `addons/game-day`, the add-on the page ends by naming (`GamesProvider.COLUMNS`, its
manifest's meta-data and provider, and the three numbers in `Repository`). Nothing read any of them.

Reproduced on fe9ad320 with one edit on each side, the kind a rename is:

    # GamesProvider.COLUMNS: "calendar" -> "source", the launcher still asks for "calendar"
    # AndroidManifest.xml: the games meta-data value -> io.github.justinnewman.gameday.files
    # docs/modules.md:        "10 to 120" -> "5 to 120", Repository still clamps at 10

`python3 -m unittest discover -s tests` stayed at 2451 tests OK (skipped=18): the add-on would have
shipped a panel that shows nothing on Home and rotates at 30 seconds whatever the person set, with a
green suite on both sides of the wire.

What is read here is the wire contract only - names, their order, the authority, the path and the three
numbers. The prose around the table is not pinned, and a page that stops stating the numbers fails a
test below rather than reading as "nothing to check".

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs" / "modules.md"
JAVA_DIR = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
GAME_DAY = JAVA_DIR / "GameDay.java"
ADDON = ROOT / "addons" / "game-day"
PROVIDER = ADDON / "app" / "src" / "main" / "java" / "io" / "github" / "justinnewman" / "gameday" / "GamesProvider.java"
REPOSITORY = ADDON / "app" / "src" / "main" / "java" / "io" / "github" / "justinnewman" / "gameday" / "Repository.java"
MANIFEST = ADDON / "app" / "src" / "main" / "AndroidManifest.xml"

# GameDay.java: the meta-data name, the projection, the clamped default and the path.
META_CONSTANT = re.compile(r'\bString\s+META\s*=\s*"([^"]+)"')
COLUMNS_ARRAY = re.compile(r"String\[\]\s+COLUMNS\s*=\s*\{([^}]*)\}")
QUOTED = re.compile(r'"([^"]+)"')
TURN_DEFAULT = re.compile(r"\bint\s+TURN_DEFAULT\s*=\s*(\d+)")
TURN_CLAMP = re.compile(r"\bs\s*>=\s*(\d+)\s*&&\s*s\s*<=\s*(\d+)")
APPENDED_PATH = re.compile(r'appendPath\("([^"]+)"\)')

# The docs page: the table's rows, the two numbers in turn_seconds' cell, and the query example.
TABLE_ROW = re.compile(r"^\s*\|\s*(.+?)\s*\|", re.M)
CELL_NAMES = re.compile(r"`([a-z][a-z0-9_]*)`")
PATH_IN_QUERY = re.compile(r"content://<authority>/([A-Za-z0-9_]+)")
DOC_SECONDS = re.compile(r"Seconds per turn,\s*(\d+)\s*to\s*(\d+);\s*anything else means\s*(\d+)")

# The add-on's manifest, read as text: one meta-data and one provider hold the whole handover.
META_DATA = re.compile(r'<meta-data\s+android:name="([^"]+)"\s+android:value="([^"]+)"')
PROVIDER_TAG = re.compile(r"<provider\b[^>]*>")
ATTRIBUTE = re.compile(r'android:(\w+)="([^"]*)"')


def game_day_section(body):
    """The lines of the '## Each team's next game on Home (game_day)' section, up to the next '## '."""
    lines = body.split("\n")
    start = next((i for i, line in enumerate(lines)
                  if line.startswith("## ") and "game_day" in line), None)
    if start is None:
        return []
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")), len(lines))
    return lines[start + 1:end]


def doc_columns(body):
    """The column names the page's table states, in page order.

    One row carries two (`start_ms`, `end_ms` share a type and a sentence), so every backticked name in
    the first cell counts, in the order written. The header row carries none and is skipped by that.
    """
    names = []
    for row in TABLE_ROW.findall("\n".join(game_day_section(body))):
        first_cell = row.split("|")[0]
        names.extend(CELL_NAMES.findall(first_cell))
    return names


def doc_seconds(body):
    """(low, high, default) out of the turn_seconds row, or None when the page stops stating them."""
    match = DOC_SECONDS.search("\n".join(game_day_section(body)))
    return tuple(int(n) for n in match.groups()) if match else None


def difference(expected, found):
    """A sentence naming what one side has and the other does not, or None when they agree."""
    missing = [name for name in expected if name not in found]
    extra = [name for name in found if name not in expected]
    if not missing and not extra:
        return None
    parts = []
    if missing:
        parts.append("not named: " + ", ".join(missing))
    if extra:
        parts.append("named but not read: " + ", ".join(extra))
    return "; ".join(parts)


def read(path):
    return path.read_text(encoding="utf-8")


def columns_of(text):
    """The names in a `String[] COLUMNS = {...}` declaration, in order; None when there is no such line."""
    match = COLUMNS_ARRAY.search(text)
    if not match:
        return None
    return QUOTED.findall(match.group(1))


def provider_for(manifest_body, authority):
    """The <provider> tag whose android:authorities is AUTHORITY, as a {attribute: value} dict, or None."""
    for tag in PROVIDER_TAG.findall(manifest_body):
        attributes = dict(ATTRIBUTE.findall(tag))
        if attributes.get("authorities") == authority:
            return attributes
    return None


class TheContractTest(unittest.TestCase):
    """The page, the launcher and the add-on, read against each other."""

    @classmethod
    def setUpClass(cls):
        cls.docs = read(DOCS)
        cls.game_day = read(GAME_DAY)
        cls.provider = read(PROVIDER)
        cls.repository = read(REPOSITORY)
        cls.manifest = read(MANIFEST)

    def test_the_page_states_the_columns_the_launcher_asks_for(self):
        asked = columns_of(self.game_day)
        self.assertIsNotNone(asked, "launcher/.../GameDay.java declares no String[] COLUMNS")
        stated = doc_columns(self.docs)
        self.assertIsNone(difference(asked, stated),
                          "docs/modules.md's game_day table and GameDay.COLUMNS disagree: "
                          f"{difference(asked, stated)}")
        # GameDay.java reads them positionally (c.getString(0), c.getLong(2), ...), so the page's order is
        # part of the contract, not a writing choice.
        self.assertEqual(asked, stated,
                         "the page names the same columns in another order, and GameDay.java reads them by "
                         f"position: launcher {asked}, page {stated}")

    def test_the_addon_answers_the_columns_the_launcher_asks_for(self):
        asked = columns_of(self.game_day)
        answered = columns_of(self.provider)
        self.assertIsNotNone(answered, "addons/game-day's GamesProvider.java declares no String[] COLUMNS")
        self.assertEqual(asked, answered,
                         "addons/game-day answers columns the launcher does not read, or is missing one it "
                         f"does: launcher {asked}, GamesProvider {answered}")

    def test_the_turns_path_is_the_one_the_launcher_builds(self):
        on_the_tv = APPENDED_PATH.findall(self.game_day)
        self.assertIn("turns", on_the_tv,
                      f"GameDay.java builds a query with no 'turns' on the path: {on_the_tv}")
        stated = PATH_IN_QUERY.search("\n".join(game_day_section(self.docs)))
        self.assertIsNotNone(stated, "docs/modules.md no longer states `content://<authority>/turns`")
        self.assertEqual(stated.group(1), "turns",
                         f"docs/modules.md sends the reader to /{stated.group(1)}, GameDay.java asks /turns")

    def test_the_meta_data_name_is_one_on_all_three_sides(self):
        constant = META_CONSTANT.search(self.game_day)
        self.assertIsNotNone(constant, "GameDay.java states no String META")
        name = constant.group(1)
        meta_data = dict(META_DATA.findall(self.manifest))
        self.assertIn(name, meta_data,
                      f"addons/game-day's manifest names no meta-data called {name}: "
                      f"{sorted(meta_data)} - the launcher reads that one and nothing else")
        self.assertIn(f'android:name="{name}"', "\n".join(game_day_section(self.docs)),
                      f"docs/modules.md does not name {name} in its code block, so an app following the "
                      "page hands Home nothing to read")

    def test_the_addon_names_a_provider_of_its_own(self):
        authority = dict(META_DATA.findall(self.manifest))[META_CONSTANT.search(self.game_day).group(1)]
        provider = provider_for(self.manifest, authority)
        self.assertIsNotNone(provider,
                             f"addons/game-day's meta-data points at {authority} and its manifest declares no "
                             "provider with that authority: the launcher resolves one and ignores another app's")
        self.assertEqual("true", provider.get("exported"),
                         f"the provider for {authority} is not exported, so the launcher cannot query it")
        # The add-on has a second exported provider, the setup script's door behind WRITE_SECURE_SETTINGS.
        # An authority that names that one answers a query with its own columns, and the panel stays empty.
        answering = re.search(r"class\s+(\w+)\s+extends\s+ContentProvider", self.provider)
        self.assertIsNotNone(answering, "GamesProvider.java declares no class extending ContentProvider")
        self.assertTrue(provider.get("name", "").endswith(answering.group(1)),
                        f"the meta-data names {authority}, which {provider.get('name')} answers, not "
                        f"{answering.group(1)}: that is the class holding COLUMNS and /turns")
        self.assertTrue(authority.startswith("io.github.justinnewman.gameday"),
                        f"{authority} is not the add-on's own package: GameDay.create() drops an authority "
                        "another app holds")

    def test_turn_seconds_is_the_same_range_on_all_three_sides(self):
        default = TURN_DEFAULT.search(self.game_day)
        self.assertIsNotNone(default, "GameDay.java states no int TURN_DEFAULT")
        clamp = TURN_CLAMP.search(self.game_day)
        self.assertIsNotNone(clamp,
                             "GameDay.java no longer states `s >= N && s <= N`; the range the add-on may set "
                             "is not read")
        launcher = (int(clamp.group(1)), int(clamp.group(2)), int(default.group(1)))
        stated = doc_seconds(self.docs)
        self.assertIsNotNone(stated, "docs/modules.md's turn_seconds row no longer states 'Seconds per turn, "
                                     "N to N; anything else means N'")
        self.assertEqual(launcher, stated,
                         f"docs/modules.md states (low, high, default) {stated} and GameDay.java keeps "
                         f"{launcher}: a person sets a value the launcher silently replaces with 30")
        addon = tuple(int(re.search(rf"\bTURN_{name}\s*=\s*(\d+)", self.repository).group(1))
                      for name in ("MIN", "MAX", "DEFAULT"))
        self.assertEqual(launcher, addon,
                         f"addons/game-day's settings allow {addon} and the launcher keeps {launcher}: the "
                         "add-on's own settings screen offers a turn length Home ignores")

    def test_the_columns_are_seven_and_have_no_duplicate(self):
        asked = columns_of(self.game_day)
        self.assertEqual(7, len(asked), f"GameDay.java asks for {len(asked)} columns: {asked}")
        self.assertEqual(len(asked), len(set(asked)),
                         f"GameDay.COLUMNS names a column twice: {asked}")


class ReaderTest(unittest.TestCase):
    """The readers above, over pages and declarations written here."""

    PAGE = """## Each team's next game on Home (game_day)

1. Name a read-only content provider in a `meta-data` on your `<application>`:

   ```xml
   <meta-data android:name="com.gathin.tvhome.games" android:value="com.example.games" />
   ```

2. Answer a query on `content://<authority>/turns` with these columns:

   | Column | Type | What |
   |---|---|---|
   | `top_line` | text | The line. |
   | `start_ms`, `end_ms` | integer | When. |
   | `turn_seconds` | integer | Seconds per turn, 10 to 120; anything else means 30. |
"""

    def test_a_row_with_two_columns_is_read_as_two(self):
        self.assertEqual(["top_line", "start_ms", "end_ms", "turn_seconds"], doc_columns(self.PAGE))

    def test_the_header_and_the_prose_are_not_read_as_columns(self):
        self.assertNotIn("Column", doc_columns(self.PAGE))
        self.assertNotIn("meta", doc_columns(self.PAGE))

    def test_the_three_numbers_are_read_out_of_the_cell(self):
        self.assertEqual((10, 120, 30), doc_seconds(self.PAGE))

    def test_a_page_that_stops_stating_the_numbers_is_not_read_as_agreeing(self):
        self.assertIsNone(doc_seconds(self.PAGE.replace("Seconds per turn, 10 to 120; anything else means 30.",
                                                        "Seconds per turn, see the add-on.")))
        self.assertIsNone(doc_seconds("## Something else\n\n10 to 120.\n"))

    def test_the_path_is_read_out_of_the_query(self):
        self.assertEqual("turns", PATH_IN_QUERY.search(self.PAGE).group(1))

    def test_an_empty_section_states_no_columns(self):
        self.assertEqual([], doc_columns("## Each team's next game on Home (game_day)\n\nNo table yet.\n"))

    def test_a_column_the_other_side_does_not_name_is_reported(self):
        self.assertEqual("not named: calendar", difference(["calendar"], []))
        self.assertEqual("named but not read: source", difference(["calendar"], ["calendar", "source"]))

    def test_a_renamed_column_is_reported_on_both_sides(self):
        self.assertEqual("not named: calendar; named but not read: source",
                         difference(["top_line", "calendar"], ["top_line", "source"]))
        self.assertIsNone(difference(["top_line", "word"], ["word", "top_line"]),
                          "order is a test of its own; this reader only names what is missing or extra")

    def test_the_declaration_is_read_in_order(self):
        self.assertEqual(["top_line", "word"], columns_of('private static final String[] COLUMNS = {"top_line", "word"};'))
        self.assertIsNone(columns_of('static final String META = "com.gathin.tvhome.games";'))

    def test_the_provider_tag_is_found_by_authority(self):
        manifest = ('<meta-data android:name="com.gathin.tvhome.games" android:value="com.example.games" />\n'
                    '<provider android:name=".FilesProvider" android:authorities="com.example.files" android:exported="true" />\n'
                    '<provider android:name=".GamesProvider" android:authorities="com.example.games" android:exported="true" />')
        self.assertEqual({"name": ".GamesProvider", "authorities": "com.example.games", "exported": "true"},
                         provider_for(manifest, "com.example.games"))
        self.assertIsNone(provider_for(manifest, "com.example.nothing"))
        self.assertFalse(provider_for(manifest, "com.example.files").get("permission", ""))

    def test_a_manifest_without_the_meta_data_names_no_authority(self):
        self.assertEqual({}, dict(META_DATA.findall('<provider android:name=".GamesProvider" />')))


if __name__ == "__main__":
    unittest.main()

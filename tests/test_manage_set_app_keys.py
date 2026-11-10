"""An app_keys list `tools/manage.py set` writes is one the launcher keeps, entry by entry.

themes/README.md tells a theme author to write `app_keys` entries such as `"190:com.google.android.youtube.tv"`,
`"scan:586:ignore"` or `"front:172:guide:com.vt.tv.tvcenter"`, and the set script's own docstring promises it
"refuses one the launcher would ignore". Config.stringList() drops only the entry it cannot read, which is the
whole rule a list of package names needs - but app_keys is read again, entry by entry, by RemoteKeys:

    String[] parts = entry.split(":", 2);
    if (parts.length != 2 || parts[1].trim().isEmpty()) continue;
    ... if (NUMERIC_KEY.matcher(key).matches()) ... else if (KEYCODE_KEY.matcher(key).matches()) ... else continue;
    if (code > 0) keys.put(code, parts[1].trim());

and parseAppScancodes() reads `scan:<digits>:<target>` the same way, so an entry whose key is not a keycode or a
KEYCODE_ name, whose keycode is 0, whose scancode is not a number, or that has no colon or no target is skipped
without a word. The same is true one step later: launch() opens a target that is an action word (isAction()'s
ten), "inputs", "input:<id>", "guide:<package>", a "package/.Activity" or a package, and anything else -
"youtube" without its dots, "guide:" with no package - finds no launch intent and does nothing. Nothing on the TV
says which button went dead, so such a list reads as written and half works.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

REMOTE_KEYS = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "RemoteKeys.java"

# The entries a theme author copies from the Keys row, plus the shapes the shipped lists use.
GOOD = [
    "190:com.google.android.youtube.tv",
    "KEYCODE_BUTTON_3:com.netflix.ninja",
    "scan:490:com.wbd.hbomax",
    "390:home",
    "284:library",
    "172:inputs",
    "4001:input:com.tcl.tvinput/.TvPassThroughService/HW15",
    "4001:guide:com.tcl.tv",
    "hold:190:com.netflix.ninja",
    "front:172:guide:com.vt.tv.tvcenter",
    "scan:365:inputs",
    "KEYCODE_PROG_YELLOW:mute",
    "scan:586:ignore",
    "KEYCODE_NOTIFICATION:com.gathin.tvhome/.QuickSettingsActivity",
]


class AppKeysRefusalTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "my-theme"
        self.theme.mkdir()
        self.config = self.theme / "config.json"
        # cmd_set ends by pushing the theme with tools/theme.sh; the tests stop before the TV.
        patcher = mock.patch.object(manage, "run")
        self.push = patcher.start()
        self.push.return_value = 0
        self.addCleanup(patcher.stop)

    def set_key(self, value, key="app_keys"):
        """Runs `set <key>` the way main() does; returns the refusal text, or None when the value was written."""
        try:
            with mock.patch("sys.stdout"):
                manage.cmd_set(key, value, str(self.theme))
        except SystemExit as stop:
            return str(stop.code)
        return None

    def written(self):
        if not self.config.is_file():
            return {}
        return json.loads(self.config.read_text(encoding="utf-8"))

    def refused(self, value, *expected):
        refusal = self.set_key(value)
        text = refusal if refusal is not None else ""
        self.assertIsNotNone(refusal, f"{value} was written, and the launcher skips an entry of it")
        for part in expected:
            self.assertIn(part, text)
        self.assertEqual({}, self.written(), "a refused list must not touch the theme")
        self.push.assert_not_called()
        return text

    def test_an_entry_with_no_colon_is_refused(self):
        # The shape a reader types when they copy "190:com.google..." and drop the target.
        self.refused('["190"]', "no colon")

    def test_an_entry_with_no_target_is_refused(self):
        self.refused('["190:"]', "names a keycode but nothing it opens")

    def test_a_key_that_is_not_a_keycode_is_refused(self):
        # KeyEvent knows names and digits; "HOME" and the lowercase form are neither, so the entry is skipped.
        self.refused('["HOME:com.netflix.ninja"]', "'HOME' is not a key the launcher reads")
        self.refused('["keycode_home:com.netflix.ninja"]', "not a key the launcher reads")

    def test_keycode_zero_is_refused(self):
        # `if (code > 0) keys.put(...)`: 0 is KEYCODE_UNKNOWN, and the entry is dropped.
        self.refused('["0:com.netflix.ninja"]', "keycode 0 is not a button")

    def test_a_scancode_that_is_not_a_number_is_refused(self):
        self.refused('["scan:abc:com.netflix.ninja"]', "'abc' is not a scancode")
        self.refused('["scan:0:com.netflix.ninja"]', "scancode 0 is not a button")
        self.refused('["scan:490"]', "names a scancode but nothing it opens")

    def test_a_target_the_launcher_cannot_open_is_refused(self):
        # launch() falls through to getLeanbackLaunchIntentForPackage(), which a dotless name never answers.
        self.refused('["190:youtube"]', "'youtube' is nothing the launcher opens")
        self.refused('["190:guide:"]', "guide: takes the Live TV app's package")
        self.refused('["190:guide:youtube"]', "guide: takes the Live TV app's package")
        self.refused('["190:input:"]', "input: names no input id")
        self.refused('["190:com.netflix.ninja/"]', "names no activity after the slash")

    def test_an_entry_that_is_not_a_string_is_refused(self):
        # stringList() keeps the Strings and drops the rest, so a number is an entry that never reaches the TV.
        self.refused("[190]", "190 is not a string")

    def test_an_empty_entry_is_refused(self):
        self.refused('[" "]', "an empty entry")

    def test_the_entries_the_launcher_keeps_are_written(self):
        self.assertIsNone(self.set_key(json.dumps(GOOD)))
        self.assertEqual(GOOD, self.written()["app_keys"])
        self.push.assert_called_once()

    def test_an_entry_is_written_trimmed(self):
        # stringList() trims every entry it keeps, so the file holds what the TV reads.
        self.assertIsNone(self.set_key('[" 190:com.google.android.youtube.tv "]'))
        self.assertEqual(["190:com.google.android.youtube.tv"], self.written()["app_keys"])

    def test_an_empty_list_is_taken(self):
        # `[]` is how an owner clears the buttons a theme added.
        self.assertIsNone(self.set_key("[]"))
        self.assertEqual([], self.written()["app_keys"])

    def test_another_list_is_not_held_to_app_keys(self):
        # dock_apps is a list of package names, read by stringList() alone: a name that is not on the TV is skipped
        # by the launcher, not dropped from the list, so set still writes what the owner asked for.
        self.assertIsNone(self.set_key('["com.example.player", "com.example.tv"]', key="dock_apps"))
        self.assertEqual(["com.example.player", "com.example.tv"], self.written()["dock_apps"])


class AppKeyProblemTest(unittest.TestCase):
    def test_the_entries_remote_keys_keeps(self):
        for entry in GOOD:
            with self.subTest(entry=entry):
                self.assertIsNone(manage.app_key_problem(entry))
        self.assertIsNone(manage.app_keys_problem([]))

    def test_the_entries_it_skips(self):
        self.assertIn("no colon", manage.app_key_problem("190"))
        self.assertIn("names a keycode but nothing it opens", manage.app_key_problem("190:"))
        self.assertIn("KEYCODE_ name", manage.app_key_problem("KeyEvent:com.x.y"))
        self.assertIn("names a scancode but nothing it opens", manage.app_key_problem("scan:"))
        self.assertIn("is not a scancode", manage.app_key_problem("scan:fourty:com.x.y"))
        self.assertIn("is nothing the launcher opens", manage.app_key_problem("190:Settings"))
        self.assertIn("not a string", manage.app_key_problem(190))

    def test_a_hold_entry_is_held_to_the_same_rules(self):
        # prefixed() strips "hold:" and hands the rest to parseAppKeys(), so the key behind it has to read too.
        self.assertIn("not a key the launcher reads", manage.app_key_problem("hold:HOME:com.x.y"))
        self.assertIn("no colon", manage.app_key_problem("hold:190"))
        self.assertIn("is nothing the launcher opens", manage.app_key_problem("front:190:Back"))
        self.assertIsNone(manage.app_key_problem("hold:scan:490:com.wbd.hbomax"))

    def test_the_first_bad_entry_is_the_one_named(self):
        problem = manage.app_keys_problem(["190:com.x.y", "191:", "192:"])
        self.assertIn("'191:'", problem or "")


class TheLauncherTest(unittest.TestCase):
    """The rules above are RemoteKeys.java's, read out of the source so the tool cannot drift from the launcher."""

    def source(self):
        return REMOTE_KEYS.read_text(encoding="utf-8")

    def test_the_two_key_patterns_are_the_ones_pinned(self):
        text = self.source()
        self.assertIn('Pattern.compile("[0-9]{1,6}")', text)
        self.assertIn('Pattern.compile("KEYCODE_[A-Z0-9_]+")', text)
        self.assertEqual("[0-9]{1,6}", manage.APP_KEY_NUMBER.pattern)
        self.assertEqual("KEYCODE_[A-Z0-9_]+", manage.APP_KEY_NAME.pattern)

    def test_a_zero_key_is_dropped(self):
        text = self.source()
        self.assertIn("if (code > 0) keys.put(code, parts[1].trim());", text)
        self.assertIn("if (scan > 0) keys.put(scan, parts[2].trim());", text)

    def test_the_action_words_are_the_ones_pinned(self):
        # isAction()'s switch is where the words are; a word added there has to reach APP_KEY_WORDS as well.
        text = self.source()
        block = text[text.index("static boolean isAction(String target)"):]
        block = block[:block.index("}")]
        self.assertEqual(list(manage.APP_KEY_WORDS), re.findall(r'case "([a-z]+)":', block))

    def test_the_target_prefixes_are_the_ones_pinned(self):
        text = self.source()
        self.assertIn('static final String INPUTS = "inputs";', text)
        self.assertIn('target.startsWith("input:")', text)
        self.assertIn('target.startsWith("guide:")', text)
        self.assertIn("target.indexOf('/') > 0", text)


class ShippedListsTest(unittest.TestCase):
    """Every app_keys list this checkout ships is one set would write, so the refusal cannot be vacuous."""

    def shipped(self):
        found = []
        for page in sorted(ROOT.glob("themes/*/config.json")) + sorted(ROOT.glob("examples/**/config.json")):
            try:
                data = json.loads(page.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                continue
            if isinstance(data, dict) and isinstance(data.get("app_keys"), list):
                found.append((page, data["app_keys"]))
        return found

    def test_every_shipped_list_reads_the_way_the_launcher_wants_it(self):
        lists = self.shipped()
        self.assertGreaterEqual(len(lists), 20, "the shipped app_keys lists were not found")
        for page, entries in lists:
            with self.subTest(page=str(page.relative_to(ROOT))):
                self.assertIsNone(manage.app_keys_problem(entries),
                                  f"{page.relative_to(ROOT)} has an entry the launcher skips: {entries}")


class KeysTableTest(unittest.TestCase):
    def test_the_app_keys_row_is_the_one_list_of_strings(self):
        rows = {item["id"]: item for item in manage.switches() if (item.get("type") or "").strip() == manage.APP_KEYS}
        self.assertEqual({"app_keys"}, set(rows))
        self.assertEqual("list", manage.declared_kind(rows["app_keys"]))

if __name__ == "__main__":
    unittest.main()

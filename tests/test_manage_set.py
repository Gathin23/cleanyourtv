"""tools/manage.py set writes a value the launcher will read, or refuses one it would drop.

Every reader in Config.java is strict about the JSON type: bool() takes a Boolean and nothing else, string() a
String, integer() a Number in its range, the list readers an array. A value of another type is not an error
there - the built-in default comes back - so `set dock_band 3` used to write 3, print "dock_band = 3", push the
theme and leave the switch at its default on the TV. The type of every key is in the Keys table of
themes/README.md, so set reads the argument the way that table says and refuses the rest before it writes.
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


def keys_table_types():
    """The type cell of every key in themes/README.md's Keys table, read the way manage.switches() reads it."""
    text = (ROOT / "themes" / "README.md").read_text(encoding="utf-8")
    part = text[text.index("## Keys"):]
    out = {}
    for row in manage.table_rows(part, "| Key |"):
        for key in re.findall(r"`([a-z_]+)`", row[0]):
            out[key] = manage.plain(row[1]) if len(row) > 1 else ""
    return out


class SetValueTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "my-theme"
        self.theme.mkdir()
        self.config = self.theme / "config.json"
        # cmd_set ends by pushing the theme with tools/theme.sh; the tests stop before the TV.
        patcher = mock.patch.object(manage, "run")
        self.push = patcher.start()
        self.addCleanup(patcher.stop)

    def set_key(self, key, value):
        """Runs set the way main() does; returns the refusal text, or None when the value was written."""
        try:
            with mock.patch("sys.stdout"):
                manage.cmd_set(key, value, str(self.theme))
        except SystemExit as stop:
            return str(stop.code)
        return None

    def written(self):
        return json.loads(self.config.read_text(encoding="utf-8")) if self.config.is_file() else None

    def test_a_bool_key_refuses_a_number_before_anything_is_written(self):
        self.config.write_text('{"greeting_name": "Old"}\n', encoding="utf-8")
        before = self.config.read_bytes()
        refusal = self.set_key("dock_band", "3")
        self.assertIn("dock_band", refusal)
        self.assertIn("true or false", refusal)
        self.assertIn("3", refusal)
        self.assertEqual(before, self.config.read_bytes(), "a refused value must not touch the theme")
        self.push.assert_not_called()

    def test_a_bool_key_takes_true_and_false(self):
        for text, expected in (("true", True), ("false", False), ("True", True)):
            with self.subTest(value=text):
                self.assertIsNone(self.set_key("dock_band", text))
                self.assertIs(self.written()["dock_band"], expected)
                self.assertIsInstance(self.written()["dock_band"], bool)

    def test_a_list_key_refuses_a_bare_package_name(self):
        # The shape a reader types by hand: one package where the launcher wants an array.
        refusal = self.set_key("dock_apps", "com.example.player")
        self.assertIn("dock_apps", refusal)
        self.assertIn("list of package names", refusal)
        self.assertIn('["a", "b"]', refusal)
        self.assertIsNone(self.written())
        self.push.assert_not_called()

    def test_a_list_key_takes_a_json_list(self):
        self.assertIsNone(self.set_key("dock_apps", '["com.example.player", "com.example.tv"]'))
        self.assertEqual(["com.example.player", "com.example.tv"], self.written()["dock_apps"])

    def test_a_number_key_refuses_text_and_takes_a_number(self):
        refusal = self.set_key("columns", "six")
        self.assertIn("columns", refusal)
        self.assertIn("1 to 8", refusal)
        self.assertIsNone(self.written())
        self.assertIsNone(self.set_key("columns", "6"))
        self.assertEqual(6, self.written()["columns"])

    def test_a_number_key_refuses_true(self):
        # Python reads True as an int; the launcher's integer() does not, so the value would be dropped.
        refusal = self.set_key("columns", "true")
        self.assertIn("columns", refusal)
        self.assertIsNone(self.written())

    def test_a_number_key_refuses_a_value_outside_the_tables_range(self):
        # integer() keeps its own default for a number outside min..max, so set must not write one either.
        for key, value in (("columns", "9"), ("columns", "0"), ("rows_below_dock", "4"),
                           ("sky_intensity", "101"), ("clock_wallpaper_dim", "91"), ("dock_opacity", "29")):
            with self.subTest(key=key, value=value):
                self.config.unlink(missing_ok=True)
                refusal = self.set_key(key, value)
                self.assertIn(key, refusal)
                self.assertIn("out of range", refusal)
                self.assertIn(value, refusal)
                self.assertIsNone(self.written(), "a refused value must not touch the theme")
                self.push.assert_not_called()

    def test_a_number_key_takes_both_ends_of_its_range(self):
        # The bounds are read out of the Keys table, so a range that moves there moves here with it.
        numbers = {k: v for k, v in keys_table_types().items() if manage.declared_kind({"type": v}) == "number"}
        self.assertGreaterEqual(len(numbers), 10, "the Keys table's number keys were read")
        for key, cell in sorted(numbers.items()):
            low, high = manage.declared_range(cell)
            with self.subTest(key=key, value=low):
                self.config.unlink(missing_ok=True)
                self.assertIsNone(self.set_key(key, str(low)))
                self.assertEqual(low, self.written()[key])
            with self.subTest(key=key, value=high):
                self.assertIsNone(self.set_key(key, str(high)))
                self.assertEqual(high, self.written()[key])

    def test_a_number_key_writes_the_int_the_launcher_would_read(self):
        # integer() does ((Number) v).intValue(), so 8.9 is eight columns on the TV: write 8, not 8.9.
        self.assertIsNone(self.set_key("columns", "8.9"))
        self.assertEqual(8, self.written()["columns"])
        self.assertIsInstance(self.written()["columns"], int)

    def test_a_number_key_refuses_nan_and_infinity(self):
        # json.loads accepts these three; intValue() has no answer for them, so the key keeps its default.
        for text in ("NaN", "Infinity", "-Infinity"):
            with self.subTest(value=text):
                self.config.unlink(missing_ok=True)
                self.assertIn("finite", self.set_key("columns", text))
                self.assertIsNone(self.written())
                self.push.assert_not_called()

    def test_a_range_cell_is_read_as_its_two_bounds(self):
        self.assertEqual((1, 8), manage.declared_range("1 to 8"))
        self.assertEqual((30, 100), manage.declared_range("30 to 100"))
        self.assertEqual((None, None), manage.declared_range("bool"))
        self.assertEqual((None, None), manage.declared_range("1 to 8 if top_menu"))

    def test_an_object_key_refuses_a_word_and_takes_a_json_object(self):
        refusal = self.set_key("wallpaper_moods", "nope")
        self.assertIn("wallpaper_moods", refusal)
        self.assertIn("object", refusal)
        self.assertIsNone(self.written())
        self.assertIsNone(self.set_key("wallpaper_moods", '{"morning": "a.jpg"}'))
        self.assertEqual({"morning": "a.jpg"}, self.written()["wallpaper_moods"])

    def test_a_string_key_takes_a_number_as_its_text(self):
        # string() takes a String only, so a number here would leave the key at its default.
        self.assertIsNone(self.set_key("greeting_name", "42"))
        self.assertEqual("42", self.written()["greeting_name"])

    def test_a_string_key_keeps_a_quoted_value_working(self):
        self.assertIsNone(self.set_key("greeting_name", '"Good evening"'))
        self.assertEqual("Good evening", self.written()["greeting_name"])

    def test_a_key_the_launcher_never_reads_from_a_theme_is_refused(self):
        # The table's own words for this one: "list, config.xml only" ... "Not read from a theme". (input_sources
        # was one too, until #1655 let a theme set it.)
        for key in ("settings_fallback_components",):
            with self.subTest(key=key):
                refusal = self.set_key(key, '["a/b"]')
                self.assertIn(key, refusal)
                self.assertIn("config.xml", refusal)
                self.assertIsNone(self.written())
                self.push.assert_not_called()

    def test_an_unknown_key_is_still_refused_by_name(self):
        self.assertIn("no switch called", self.set_key("no_such_key", "1"))

    def test_the_kind_of_every_key_comes_from_the_keys_table(self):
        types = keys_table_types()
        self.assertGreater(len(types), 90, "the Keys table was read")
        self.assertEqual("bool", manage.declared_kind(manage.find("dock_band")))
        self.assertEqual("list", manage.declared_kind(manage.find("dock_apps")))
        self.assertEqual("number", manage.declared_kind(manage.find("columns")))
        self.assertEqual("object", manage.declared_kind(manage.find("wallpaper_moods")))
        self.assertEqual("string", manage.declared_kind(manage.find("greeting_name")))
        self.assertEqual("config-only", manage.declared_kind(manage.find("settings_fallback_components")))
        self.assertEqual("list", manage.declared_kind(manage.find("input_sources")))
        # A type cell this file does not recognise would fall through to "string" and let a wrong value
        # through, so every cell the table carries today has to land on a kind.
        known = {"bool", "list", "number", "object", "string", "config-only"}
        for cell in sorted(set(types.values())):
            with self.subTest(cell=cell):
                self.assertIn(manage.declared_kind({"type": cell}), known)

    def test_the_readers_it_copies_are_still_strict(self):
        """A launcher that started coercing types would make set's refusals wrong, so pin the readers."""
        config = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/Config.java").read_text(encoding="utf-8")
        self.assertIn("Object v = o.opt(key);", config)
        self.assertIn("return v instanceof Boolean ? (Boolean) v : fallback;", config)
        self.assertIn('return v instanceof String ? ((String) v).trim() : fallback;', config)
        self.assertIn("if (!(v instanceof Number)) return fallback;", config)
        # integer() truncates like Number.intValue() and keeps its default outside min..max: the two rules
        # set's number handling copies.
        self.assertIn("int i = ((Number) v).intValue();", config)
        self.assertIn("return i < min || i > max ? fallback : i;", config)


class SetThemeFolderTest(unittest.TestCase):
    """set writes into the owner's theme folder: one it cannot read, or one this checkout ships, is refused.

    themes/README.md sends a theme author to write config.json by hand, so the file set reads is a hand-edited
    one: a comma left after the last key, a list where the launcher reads an object, a file saved by an editor
    in another encoding. Each of those was a traceback from halfway through set, and `--theme examples/<model>`
    rewrote a tracked config.json and pushed it (tests/test_shipped_config_values.py holds those files to the
    launcher's readers, so the edit could also fail CI in an unrelated pull request).
    """

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "my-theme"
        self.theme.mkdir()
        self.config = self.theme / "config.json"
        patcher = mock.patch.object(manage, "run")
        self.push = patcher.start()
        self.addCleanup(patcher.stop)

    def set_key(self, key="dock_band", value="true", folder=None):
        """Runs set the way main() does; returns the refusal text, or None when the value was written."""
        try:
            with mock.patch("sys.stdout"):
                manage.cmd_set(key, value, str(folder or self.theme))
        except SystemExit as stop:
            return str(stop.code)
        return None

    def test_utf8_themes_keep_other_keys_with_or_without_a_bom(self):
        original = {
            "greeting_name": "客廳 Trịnh",
            "columns": 6,
            "custom": {"label": "夜晚", "values": [True, None, {"amount": 7}]},
            "dock_band": False,
        }
        text = json.dumps(original, ensure_ascii=False, indent=2) + "\n"
        for encoding in ("utf-8", "utf-8-sig"):
            for newline in ("\n", "\r\n"):
                with self.subTest(encoding=encoding, newline=newline):
                    self.config.write_bytes(text.replace("\n", newline).encode(encoding))
                    self.push.reset_mock()
                    self.assertIsNone(self.set_key("dock_band", "true"))
                    written = self.config.read_bytes()
                    self.assertFalse(written.startswith(b"\xef\xbb\xbf"))
                    updated = json.loads(written.decode("utf-8"))
                    self.assertIs(updated["dock_band"], True)
                    self.assertEqual(updated, dict(original, dock_band=True))
                    self.push.assert_called_once_with(
                        ["bash", str(manage.TOOLS / "theme.sh"), str(self.theme.resolve())], manage.ROOT)

    def test_a_config_json_that_is_not_json_is_named_and_left_alone(self):
        self.config.write_text('{"greeting_name": "Old",}\n', encoding="utf-8")
        before = self.config.read_bytes()
        refusal = self.set_key()
        self.assertIn(str(self.config), refusal)
        self.assertIn("is not valid JSON", refusal)
        self.assertIn("Nothing was written or pushed", refusal)
        self.assertEqual(before, self.config.read_bytes(), "a refused theme must not be rewritten")
        self.push.assert_not_called()

    def test_a_config_json_that_is_not_an_object_is_named(self):
        for text, kind in (("[1, 2]", "an array"), ('"a theme"', "a string"), ("12", "a number"),
                           ("null", "null"), ("true", "a boolean")):
            with self.subTest(config=text):
                self.config.write_text(text, encoding="utf-8")
                refusal = self.set_key()
                self.assertIn(f"holds {kind}", refusal)
                self.assertIn("not a JSON object", refusal)
                self.assertEqual(text, self.config.read_text(encoding="utf-8"), "a refused theme must not be rewritten")
                self.push.assert_not_called()

    def test_a_config_json_in_another_encoding_is_named(self):
        # A file an editor saved as UTF-16, the way a Windows editor does by default for "Unicode".
        self.config.write_bytes('{"greeting_name": "Old"}'.encode("utf-16"))
        before = self.config.read_bytes()
        refusal = self.set_key()
        self.assertIn(f"cannot read {self.config.resolve()}", refusal)
        self.assertIn("Nothing was written or pushed", refusal)
        self.assertEqual(before, self.config.read_bytes())
        self.push.assert_not_called()

    def test_a_theme_that_is_a_file_is_named(self):
        afile = Path(self.tmp.name) / "not-a-theme"
        afile.write_text("", encoding="utf-8")
        refusal = self.set_key(folder=afile)
        self.assertIn(f"{afile} is a file, not a folder", refusal)
        self.push.assert_not_called()

    def test_a_write_it_cannot_make_is_named(self):
        # A file in the middle of the path: mkdir cannot create a folder under one.
        afile = Path(self.tmp.name) / "not-a-folder"
        afile.write_text("", encoding="utf-8")
        refusal = self.set_key(folder=afile / "theme")
        self.assertIn("cannot write", refusal)
        self.assertIn("config.json", refusal)
        self.assertIn("Nothing was pushed", refusal)
        self.push.assert_not_called()

    def test_a_folder_this_checkout_ships_is_refused(self):
        # A checkout of its own, so the project's real files are never touched by this test.
        # Resolved like manage.ROOT is, so a temporary folder behind a symlink (/var on macOS) still compares.
        root = (Path(self.tmp.name) / "checkout").resolve()
        for name in manage.PROJECT_FOLDERS:
            (root / name / "shipped").mkdir(parents=True)
            (root / name / "shipped" / "config.json").write_text('{"greeting_name": "Shipped"}\n', encoding="utf-8")
        patcher = mock.patch.object(manage, "ROOT", root)
        patcher.start()
        self.addCleanup(patcher.stop)
        for name in manage.PROJECT_FOLDERS:
            folder = root / name / "shipped"
            before = (folder / "config.json").read_bytes()
            with self.subTest(project=name):
                refusal = self.set_key(folder=folder)
                self.assertIn(f"inside this checkout's {name}/", refusal)
                self.assertIn("Nothing was written or pushed", refusal)
                self.assertEqual(before, (folder / "config.json").read_bytes(),
                                 "set must not rewrite a config.json the checkout ships")
                self.push.assert_not_called()
        # The guard is about where the file is, not about being careful: a folder of the owner's own is written.
        mine = root / "my-theme"
        mine.mkdir()
        self.assertIsNone(self.set_key(folder=mine))
        self.assertIs(json.loads((mine / "config.json").read_text(encoding="utf-8"))["dock_band"], True)

    def test_every_folder_that_ships_a_config_json_is_one_set_refuses(self):
        # A third kind of folder with a config.json in it (a new add-on contract, say) has to be added to
        # PROJECT_FOLDERS, or set would rewrite it the way examples/ used to be rewritable.
        shipped = {}
        for path in sorted(ROOT.glob("*/*/config.json")):
            shipped.setdefault(path.relative_to(ROOT).parts[0], set()).add(path)
        self.assertGreaterEqual(sum(len(v) for v in shipped.values()), 60, "no config.json files were found")
        self.assertEqual({"examples", "themes"}, set(shipped), "a folder with a config.json that set does not guard")
        self.assertEqual(set(shipped), set(manage.PROJECT_FOLDERS))


    def test_the_page_that_hands_out_set_names_the_refusal(self):
        # themes/README.md is where a theme author meets `set`: a refusal they were not told about costs them
        # a run against a TV.
        page = (ROOT / "themes" / "README.md").read_text(encoding="utf-8")
        self.assertIn("outside this checkout", page)
        for name in manage.PROJECT_FOLDERS:
            self.assertIn(f"`{name}/`", page)


if __name__ == "__main__":
    unittest.main()

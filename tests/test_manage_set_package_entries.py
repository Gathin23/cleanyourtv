"""Package lists must not silently lose entries in Config.stringList(). No TV is contacted."""
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


class PackageEntriesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.theme = Path(temporary.name) / "my-theme"
        self.theme.mkdir()
        self.config = self.theme / "config.json"
        self.original = b'\xef\xbb\xbf{\r\n  "greeting_name": "Keep"\r\n}\r\n'
        self.keys = [item["id"] for item in manage.switches()
                     if item["type"] == "list of package names"]
        self.assertTrue(self.keys)

    def set_key(self, key, entries):
        with contextlib.redirect_stdout(io.StringIO()):
            return manage.cmd_set(key, json.dumps(entries), str(self.theme))

    def test_entries_the_launcher_drops_refuse_the_whole_edit(self):
        # Config.stringList() skips non-strings and strings empty after Java trim().
        for key in self.keys:
            for entry in (42, 1.5, True, False, None, {}, [], "", " \t\r\n", "\0\x1f"):
                with self.subTest(key=key, entry=entry):
                    self.config.write_bytes(self.original)
                    with mock.patch.object(manage, "run") as push:
                        with self.assertRaises(SystemExit) as refused:
                            self.set_key(key, ["com.example.player", entry])
                    self.assertIn(key, str(refused.exception))
                    self.assertIn("entry 2", str(refused.exception))
                    self.assertEqual(self.config.read_bytes(), self.original)
                    push.assert_not_called()

    def test_valid_and_empty_lists_keep_the_existing_behavior(self):
        for key in self.keys:
            for entries in ([], ["com.example.player", "com.example.tv"],
                            ["  com.example.player\t", "com.example.player"]):
                with self.subTest(key=key, entries=entries):
                    self.config.write_bytes(self.original)
                    with mock.patch.object(manage, "run", return_value=0) as push:
                        self.assertEqual(self.set_key(key, entries), 0)
                    self.assertEqual(json.loads(self.config.read_text(encoding="utf-8")),
                                     {"greeting_name": "Keep", key: entries})
                    push.assert_called_once()

    def test_java_trim_boundary_and_other_list_types_are_preserved(self):
        # Java trim() leaves NBSP intact; Python str.strip() alone would over-refuse it.
        for key, entries in (("dock_apps", ["\u00a0"]),
                             ("settings_key_keycodes", [83, 176]),
                             ("race_calendar", [{"round": 1, "name": "Example Grand Prix",
                                                 "sessions": [{"name": "RACE", "start": "2027-03-01T12:00:00Z"}]}])):
            with self.subTest(key=key):
                with mock.patch.object(manage, "run", return_value=0):
                    self.assertEqual(self.set_key(key, entries), 0)
                self.assertEqual(json.loads(self.config.read_text(encoding="utf-8"))[key], entries)

    def test_a_refused_list_does_not_create_a_config(self):
        with mock.patch.object(manage, "run") as push:
            with self.assertRaises(SystemExit):
                self.set_key("dock_apps", [None])
        self.assertFalse(self.config.exists())
        push.assert_not_called()


if __name__ == "__main__":
    unittest.main()

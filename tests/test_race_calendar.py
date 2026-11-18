"""The calendar helper reads the same UTF-8 theme files as the launcher, without losing other keys."""
import contextlib
import datetime
import io
import json
import os
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock

from tools import race_calendar


RESPONSE = json.dumps({"MRData": {"RaceTable": {"season": "2026", "Races": [{
    "round": "1", "raceName": "Example Grand Prix", "date": "2026-01-04", "time": "14:00:00Z",
}]}}}).encode("utf-8")


class FixedDatetime(datetime.datetime):
    @classmethod
    def now(cls, tz=None):
        return cls(2026, 1, 1, tzinfo=tz)


class CalendarConfigTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name)
        self.config = self.theme / "config.json"
        opener = mock.patch.object(race_calendar.urllib.request, "urlopen",
                                   side_effect=lambda *args, **kwargs: io.BytesIO(RESPONSE))
        self.urlopen = opener.start()
        self.addCleanup(opener.stop)

    def run_main(self, theme=None):
        clock = SimpleNamespace(datetime=FixedDatetime, timedelta=datetime.timedelta,
                                timezone=datetime.timezone)
        with mock.patch.object(sys, "argv",
                               ["race_calendar.py", str(theme or self.theme), "--season", "2026"]), \
                mock.patch.object(race_calendar, "datetime", clock), \
                contextlib.redirect_stdout(io.StringIO()), \
                contextlib.redirect_stderr(io.StringIO()):
            return race_calendar.main()

    def test_utf8_theme_keeps_other_keys_when_calendar_is_updated(self):
        original = {
            "greeting_name": "客廳 Trịnh",
            "columns": 6,
            "dock_apps": ["com.example.player"],
            "custom": {"label": "夜晚", "values": [True, None, {"amount": 7}]},
            "race_calendar": [{"name": "old calendar"}],
        }
        text = json.dumps(original, ensure_ascii=False, indent=2) + "\n"
        for encoding in ("utf-8", "utf-8-sig"):
            for newline in ("\n", "\r\n"):
                with self.subTest(encoding=encoding, newline=newline):
                    self.config.write_bytes(text.replace("\n", newline).encode(encoding))
                    self.urlopen.reset_mock()
                    self.run_main()
                    self.urlopen.assert_called_once()
                    written = self.config.read_bytes()
                    self.assertFalse(written.startswith(b"\xef\xbb\xbf"))
                    updated = json.loads(written.decode("utf-8"))
                    self.assertEqual({k: v for k, v in updated.items() if k != "race_calendar"},
                                     {k: v for k, v in original.items() if k != "race_calendar"})
                    self.assertEqual(len(updated["race_calendar"]), 1)
                    self.assertEqual(updated["race_calendar"][0]["name"], "Example Grand Prix")

    def test_malformed_theme_is_unchanged_and_never_fetches(self):
        # It used to be a traceback: a trailing comma in the theme's own config.json ended the run in
        # JSONDecodeError, and Python's exit code for that is 1 - the code this script keeps for "this
        # season has no race left to show". It is now the refusal every other bad input gets.
        for encoding in ("utf-8", "utf-8-sig"):
            with self.subTest(encoding=encoding):
                data = '{"greeting_name":"客廳 Trịnh",'.encode(encoding)
                self.config.write_bytes(data)
                self.urlopen.reset_mock()
                self.assertEqual(self.run_main(), 2)
                self.urlopen.assert_not_called()
                self.assertEqual(self.config.read_bytes(), data)

    def test_the_calendar_config_stays_within_the_launchers_byte_limit(self):
        calendar = [{"round": 1, "name": "Example Grand Prix", "place": "",
                     "sessions": [{"name": "RACE", "start": "2026-01-04T14:00:00Z"}]}]
        for size, newline in ((65535, os.linesep), (65536, os.linesep),
                              (65537, os.linesep), (65537, "\r\n")):
            with self.subTest(size=size, newline=newline):
                data = {"greeting_name": "写" * 100, "race_calendar": calendar}
                text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
                data["greeting_name"] += "x" * (size - len(text.replace("\n", newline).encode("utf-8")))
                original = json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                self.assertLessEqual(len(original), 65536)
                self.config.write_bytes(original)
                self.urlopen.reset_mock()
                with mock.patch.object(race_calendar.os, "linesep", newline):
                    code = self.run_main()
                self.urlopen.assert_called_once()
                if size > 65536:
                    self.assertEqual(code, 2)
                    self.assertEqual(self.config.read_bytes(), original)
                    self.assertEqual(list(self.theme.iterdir()), [self.config])
                else:
                    self.assertEqual(code, 0)
                    self.assertEqual(len(self.config.read_bytes()), size)
                    self.assertEqual(json.loads(self.config.read_bytes()), data)

    def test_an_oversize_calendar_does_not_start_a_config(self):
        payload = json.loads(RESPONSE)
        payload["MRData"]["RaceTable"]["Races"][0]["raceName"] = "写" * 22000
        self.urlopen.side_effect = lambda *args, **kwargs: io.BytesIO(json.dumps(payload).encode("utf-8"))
        self.assertEqual(self.run_main(), 2)
        self.assertFalse(self.config.exists())
        self.assertEqual(list(self.theme.iterdir()), [])


class AThemeFolderTheConsoleCannotEncode(unittest.TestCase):
    """The last two lines name the file this run wrote, so a name the console cannot carry ended a run
    that had finished.

    config.json is written before those lines are printed, and Python's status for the UnicodeEncodeError
    that followed was 1 - the code this script keeps for "the season has no race left to show" - so a
    theme in a folder named `客廳` had just been updated while the run said it had nothing to show. The
    window is cp1252 here, which is what a Windows console gives a script that does not ask for UTF-8.
    """

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "客廳-theme"
        self.theme.mkdir()
        opener = mock.patch.object(race_calendar.urllib.request, "urlopen",
                                   side_effect=lambda *args, **kwargs: io.BytesIO(RESPONSE))
        self.urlopen = opener.start()
        self.addCleanup(opener.stop)

    def test_the_calendar_is_written_and_the_run_answers_0(self):
        raw = io.BytesIO()
        console = io.TextIOWrapper(raw, encoding="cp1252", newline="")
        real_stdout = sys.stdout
        self.addCleanup(setattr, sys, "stdout", real_stdout)
        sys.stdout = console
        clock = SimpleNamespace(datetime=FixedDatetime, timedelta=datetime.timedelta,
                                timezone=datetime.timezone)
        with mock.patch.object(sys, "argv",
                               ["race_calendar.py", str(self.theme), "--season", "2026"]), \
                mock.patch.object(race_calendar, "datetime", clock), \
                contextlib.redirect_stderr(io.StringIO()) as err:
            code = race_calendar.main()
        console.flush()
        self.assertEqual(code, 0, err.getvalue())
        self.assertNotIn("Traceback", err.getvalue())
        written = json.loads((self.theme / "config.json").read_text("utf-8"))
        self.assertEqual(len(written["race_calendar"]), 1)
        self.assertEqual(written["race_calendar"][0]["name"], "Example Grand Prix")
        # The file is named with the characters the console has, which is what the reader needs to find it.
        text = raw.getvalue().decode("cp1252")
        self.assertIn("1 races from the 2026 season ->", text)
        self.assertIn("??-theme", text)
        self.assertIn("next: round 1 Example Grand Prix", text)


if __name__ == "__main__":
    unittest.main()

"""The calendar helper reads the same UTF-8 theme files as the launcher, without losing other keys."""
import contextlib
import datetime
import io
import json
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

    def run_main(self):
        clock = SimpleNamespace(datetime=FixedDatetime, timedelta=datetime.timedelta,
                                timezone=datetime.timezone)
        with mock.patch.object(sys, "argv", ["race_calendar.py", str(self.theme), "--season", "2026"]), \
                mock.patch.object(race_calendar, "datetime", clock), \
                contextlib.redirect_stdout(io.StringIO()):
            race_calendar.main()

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
        for encoding in ("utf-8", "utf-8-sig"):
            with self.subTest(encoding=encoding):
                data = '{"greeting_name":"客廳 Trịnh",'.encode(encoding)
                self.config.write_bytes(data)
                self.urlopen.reset_mock()
                with self.assertRaises(json.JSONDecodeError):
                    self.run_main()
                self.urlopen.assert_not_called()
                self.assertEqual(self.config.read_bytes(), data)


if __name__ == "__main__":
    unittest.main()

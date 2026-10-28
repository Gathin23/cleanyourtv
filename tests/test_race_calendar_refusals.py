"""The calendar helper's two ways to fail without a traceback: the folder it is given, and the fetch.

`tools/race_calendar.py` is the one step in this repository that goes online, and it was the last tool
here that answered its own mistakes with a stack trace: `--season 2027x` went to Jolpica as a URL that
404s, an offline laptop ended in `urllib.error.URLError: <urlopen error ...>` with eighteen lines above
it, and a theme folder typed from the wrong directory (`theme/aurora`, missing the `s`) was *created*
with a calendar in it, so the author pushed a theme that is not the one they meant. Each of those is now
a sentence on stderr naming the path or the URL and saying that nothing was written, and exit 2 - the
code the other tools use for a run that could not do its job, next to 1 for a real answer (this season
has no race left to show) and 0 for a calendar in the theme.

The exit codes and the folder rule are in the script's docstring as well, so the page a reader opens
first is not the only place they are written down.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import argparse
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
import urllib.error

from tools import race_calendar


RACES = {"MRData": {"RaceTable": {"season": "2026", "Races": [{
    "round": "1", "raceName": "Example Grand Prix", "date": "2026-01-04", "time": "14:00:00Z",
}]}}}
RESPONSE = json.dumps(RACES).encode("utf-8")


class FixedDatetime(datetime.datetime):
    @classmethod
    def now(cls, tz=None):
        return cls(2026, 1, 1, tzinfo=tz)


def clock():
    """`datetime` as the script uses it: now() at a fixed moment, plus the two names it needs."""
    return SimpleNamespace(datetime=FixedDatetime, timedelta=datetime.timedelta,
                           timezone=datetime.timezone)


class SeasonArgumentTest(unittest.TestCase):
    """--season takes the two forms Jolpica takes, and nothing else reaches it."""

    def test_current_and_a_championship_year_are_accepted(self):
        self.assertEqual(race_calendar.season("current"), "current")
        self.assertEqual(race_calendar.season("2026"), "2026")
        self.assertEqual(race_calendar.season(str(race_calendar.FIRST_SEASON)), str(race_calendar.FIRST_SEASON))

    def test_a_typo_and_a_year_before_the_first_season_are_refused(self):
        for value in ("2027x", "26", "26x", str(race_calendar.FIRST_SEASON - 1), "1899", "", "2026.5",
                      "-2026"):
            with self.subTest(value=value):
                with self.assertRaises(argparse.ArgumentTypeError) as caught:
                    race_calendar.season(value)
                self.assertIn(repr(value), str(caught.exception))
                self.assertIn(str(race_calendar.FIRST_SEASON), str(caught.exception))


class RefusalTest(unittest.TestCase):
    """What main() answers a folder it does not have, and a fetch that fails."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "themes" / "aurora"
        self.theme.mkdir(parents=True)
        self.config = self.theme / "config.json"
        self.err = io.StringIO()
        self.out = io.StringIO()

    def fetch(self, *args, **kwargs):
        raise AssertionError("the fetch should not have been made")

    def stub(self, *, body=None, raises=None):
        if raises is not None:
            side_effect = raises
        else:
            side_effect = lambda *args, **kwargs: io.BytesIO(body if body is not None else RESPONSE)
        opener = mock.patch.object(race_calendar.urllib.request, "urlopen", side_effect=side_effect)
        self.urlopen = opener.start()
        self.addCleanup(opener.stop)

    def run_main(self, *argv):
        """main() as the shell runs it: (status, stderr), with argparse's own exits caught."""
        with mock.patch.object(sys, "argv", ["race_calendar.py", *argv]), \
                mock.patch.object(race_calendar, "datetime", clock()), \
                contextlib.redirect_stdout(self.out), \
                contextlib.redirect_stderr(self.err):
            try:
                status = race_calendar.main()
            except SystemExit as e:
                status = e.code
        return status, self.err.getvalue()

    def assert_refused(self, status, stderr, *needles):
        self.assertEqual(status, 2, stderr)
        self.assertNotIn("Traceback", stderr)
        for needle in needles:
            self.assertIn(needle, stderr)
        self.assertTrue(any("Nothing was written" in line for line in stderr.splitlines()), stderr)

    def test_a_season_that_is_not_a_year_never_reaches_the_api(self):
        self.stub()
        status, stderr = self.run_main(str(self.theme), "--season", "2027x")
        self.assertEqual(status, 2, stderr)
        self.assertIn("a season is 'current' or a year from 1950 on", stderr)
        self.assertIn("'2027x'", stderr)
        self.urlopen.assert_not_called()

    def test_a_theme_folder_that_is_not_there_is_named_and_not_created(self):
        self.stub()
        missing = Path(self.tmp.name) / "themes" / "aurora2"     # one letter off the folder above
        status, stderr = self.run_main(str(missing), "--season", "2026")
        self.assert_refused(status, stderr, "there is no theme folder at", str(missing))
        self.assertFalse(missing.exists(), "the run made the folder it was given")
        self.urlopen.assert_not_called()

    def test_a_theme_path_that_is_a_file_is_refused(self):
        self.stub()
        picture = self.theme / "screenshot.jpg"
        picture.write_bytes(b"jpg")
        status, stderr = self.run_main(str(picture), "--season", "2026")
        self.assert_refused(status, stderr, "there is no theme folder at", str(picture))
        self.urlopen.assert_not_called()

    def test_an_empty_theme_name_is_not_the_directory_it_is_run_from(self):
        # `race_calendar.py "$THEME"` with THEME unset passes the empty word, and Path("") is Path("."):
        # a folder that is always there, so the folder check let it through, the run wrote a config.json
        # into the directory it stood in and exited 0 as if the calendar were in the theme.
        self.stub()
        started = os.getcwd()
        self.addCleanup(os.chdir, started)
        os.chdir(self.theme)
        for word in ("", "   "):
            with self.subTest(word=word):
                status, stderr = self.run_main(word, "--season", "2026")
                self.assert_refused(status, stderr, "there is no theme folder in", repr(word),
                                    "an empty or blank name")
                self.assertEqual(self.out.getvalue(), "", "the run reported a calendar it did not write")
                self.assertFalse((self.theme / "config.json").exists(),
                                 "the run wrote a config.json in the directory it was started from")
                self.urlopen.assert_not_called()

    def test_a_config_json_that_is_not_json_is_named_and_never_fetches(self):
        # The theme's own config.json is hand-written, and a typo in it was a JSONDecodeError traceback
        # whose exit code, 1, is this script's "no race left this season" - so the reader was sent to
        # --season next year over a missing brace. An empty file is the same shape.
        self.stub()              # a fetch would succeed, so only the read can stop the run
        for data in ('{"columns": 5,}', '{"columns": 5', "", '{"greeting_name": "Olohuone" "columns": 5}'):
            with self.subTest(data=data):
                self.config.write_text(data, encoding="utf-8")
                status, stderr = self.run_main(str(self.theme), "--season", "2026")
                self.assert_refused(status, stderr, "not the JSON a theme's config.json has to be",
                                    str(self.config))
                self.urlopen.assert_not_called()
                self.assertEqual(self.config.read_text(encoding="utf-8"), data)

    def test_a_config_json_that_is_not_utf8_is_the_same_refusal(self):
        self.stub()
        data = b'{"greeting_name": "\xff\xfe"}'
        self.config.write_bytes(data)
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, str(self.config), "utf-8")
        self.urlopen.assert_not_called()
        self.assertEqual(self.config.read_bytes(), data)

    def test_a_config_json_that_is_not_an_object_is_named(self):
        # Valid JSON, and `theme["race_calendar"] = races` met it with a TypeError after the fetch.
        for data, kind in (("[1, 2, 3]", "array"), ('"a theme"', "string"), ("12", "number"), ("null", "null")):
            with self.subTest(data=data):
                self.stub()          # a fetch would succeed, so only the read can stop the run
                self.config.write_text(data, encoding="utf-8")
                status, stderr = self.run_main(str(self.theme), "--season", "2026")
                self.assert_refused(status, stderr, f"holds a JSON {kind}, not an object", str(self.config))
                self.urlopen.assert_not_called()
                self.assertEqual(self.config.read_text(encoding="utf-8"), data)

    def test_a_config_json_that_is_a_folder_is_named(self):
        self.stub()
        self.config.mkdir()
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, f"{self.config} is not a file")
        self.urlopen.assert_not_called()

    def test_a_config_json_that_cannot_be_written_is_named_after_the_fetch(self):
        # The other half of the same file: the fetch happens first, so this one is not "nothing was
        # written" - it says the calendar is not in the theme, which is what the reader has to fix.
        self.stub()
        original = json.dumps({"columns": 5})
        self.config.write_text(original, encoding="utf-8")
        refusal = OSError(28, "No space left on device")
        with mock.patch.object(Path, "write_text", side_effect=refusal):
            status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assertEqual(status, 2, stderr)
        self.assertNotIn("Traceback", stderr)
        self.assertIn("could not write", stderr)
        self.assertIn(str(self.config), stderr)
        self.assertIn("No space left on device", stderr)
        self.assertIn("is not in the theme", stderr)
        self.urlopen.assert_called_once()
        self.assertEqual(self.config.read_text(encoding="utf-8"), original)

    def test_a_fetch_that_cannot_connect_says_which_url_and_why(self):
        self.stub(raises=urllib.error.URLError(ConnectionRefusedError(111, "Connection refused")))
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, "could not reach Jolpica at",
                            "api.jolpi.ca/ergast/f1/2026.json", "Connection refused")
        self.assertFalse(self.config.exists(), "a failed fetch wrote a config.json")

    def test_a_timeout_is_the_same_answer(self):
        self.stub(raises=TimeoutError("timed out"))
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, "could not reach Jolpica at", "timed out")

    def test_an_http_error_names_the_status_the_api_gave(self):
        self.stub(raises=urllib.error.HTTPError(race_calendar.API.format(season="2026"), 503,
                                                "Service Unavailable", {}, None))
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, "Jolpica answered 503 Service Unavailable")

    def test_a_body_that_is_not_json_is_named_as_such(self):
        self.stub(body=b"<html><body>502 Bad Gateway</body></html>")
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, "answered something that is not JSON", "api.jolpi.ca")

    def test_an_answer_with_no_race_table_is_named(self):
        self.stub(body=json.dumps({"MRData": {"total": "0"}}).encode("utf-8"))
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, "has no MRData.RaceTable in it", "2026")

    def test_a_session_time_the_script_cannot_read_is_named(self):
        broken = json.loads(json.dumps(RACES))
        broken["MRData"]["RaceTable"]["Races"][0]["date"] = "4 January 2026"
        self.stub(body=json.dumps(broken).encode("utf-8"))
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, "is not a timestamp this script understands")
        self.assertFalse(self.config.exists(), "a calendar the script could not read was written")

    def test_a_refused_fetch_leaves_an_existing_theme_byte_identical(self):
        original = json.dumps({"greeting_name": "Olohuone", "columns": 5}, ensure_ascii=False) + "\n"
        self.config.write_text(original, encoding="utf-8")
        self.stub(raises=urllib.error.URLError(OSError("Network is unreachable")))
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assert_refused(status, stderr, "could not reach Jolpica at")
        self.assertEqual(self.config.read_text(encoding="utf-8"), original)

    def test_a_season_with_no_race_left_is_exit_1_with_its_message(self):
        # A real answer, not a run that failed: the season's races are all behind the fixed clock.
        past = json.loads(json.dumps(RACES))
        past["MRData"]["RaceTable"]["Races"][0]["date"] = "2025-01-04"
        self.stub(body=json.dumps(past).encode("utf-8"))
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assertEqual(status, 1, stderr)
        self.assertIn("no upcoming races in the 2026 calendar", stderr)
        self.assertEqual(self.config.exists(), False)

    def test_a_calendar_the_script_can_read_is_written_and_exits_0(self):
        self.stub()
        self.config.write_text(json.dumps({"columns": 5}), encoding="utf-8")
        status, stderr = self.run_main(str(self.theme), "--season", "2026")
        self.assertEqual(status, 0, stderr)
        self.assertEqual(stderr, "")
        written = json.loads(self.config.read_text(encoding="utf-8"))
        self.assertEqual(written["columns"], 5)
        self.assertEqual(written["race_calendar"][0]["name"], "Example Grand Prix")

    def test_the_help_text_still_exits_0_without_fetching(self):
        self.stub()
        status, stderr = self.run_main("--help")
        self.assertEqual(status, 0, stderr)
        self.assertIn("--season", self.out.getvalue())
        self.urlopen.assert_not_called()


if __name__ == "__main__":
    unittest.main()

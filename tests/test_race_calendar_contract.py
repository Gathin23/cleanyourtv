"""The calendar `tools/race_calendar.py` writes is the one the launcher reads, across the file between them.

Three files agree on the shape of a theme's `race_calendar` and nothing read them against each other.
`tools/race_calendar.py` writes each race as `round`, `name`, `place` and `sessions` of `name` and `start`,
the session names coming from its own `SESSIONS` table ("FP1", "SPRINT QUALI", "RACE"). `Config.raceCalendar()`
reads those keys out of the theme's `config.json` and silently drops a session whose `start`
`java.time.Instant` cannot parse, or a race left with no session. `RaceWeekend` answers each session name in
its two switches, `title()` and `shortName()`, and falls back to the raw name for one it does not know.
`tests/test_race_calendar.py` runs the script on its own and `RaceWeekendTest.java` runs the launcher on its
own fixture, so a rename on either side of that file leaves the suite green while the panel goes blank, or
offers a reader "Sprint quali" where every other session is translated.

The window is the same seam. The panel shows from `RaceWeekend.LEAD` before a weekend's first session until
`RaceWeekend.TAIL` after the race starts, and the script's own cutoff is what decides the weekend is still in
the file for it to show: the race is dropped a day after its start, which is what keeps the four hours after
it, and a cutoff tightened to "a race that has started is over" would darken the panel for exactly the tail
`themes/README.md` promises.

Prose is not pinned: what the page has to state is the two numbers it promises a reader, and the rest of this
file reads the names and keys the two programs exchange, and the behaviour of the calendar around the edges
of the window.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import datetime
from pathlib import Path
import re
import unittest

from tools import race_calendar


ROOT = Path(__file__).resolve().parents[1]
TVHOME = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
RACE_WEEKEND = TVHOME / "RaceWeekend.java"
CONFIG = TVHOME / "Config.java"
PAGE = ROOT / "themes" / "README.md"

SWITCH = re.compile(r'case "([^"]+)":')
DURATION = re.compile(r"static final Duration (LEAD|TAIL) = Duration\.ofHours\((\d+)\);")
# java.time.Instant.parse reads the "2026-10-23T17:30:00Z" form and nothing else: a start without its Z, or
# with an offset spelled "+00:00", arrives as a DateTimeParseException and Config drops the session.
INSTANT = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$")
# The keys the script writes, and the ones Config reads back, on each side of the file.
RACE_KEYS = {"round", "name", "place", "sessions"}
SESSION_KEYS = {"name", "start"}


def switch_cases(text, method):
    """The names a method's switch answers, from `case "..."` up to its `default:` branch."""
    start = text.index(f"String {method}(")
    end = text.index("default:", start)
    return SWITCH.findall(text[start:end])


def duration_hours(text):
    """{LEAD: 12, TAIL: 4}: the two Durations the panel's window is built from."""
    return {name: int(hours) for name, hours in DURATION.findall(text)}


def page_window(text):
    """(hours before the first session, hours after the race) as the key's row states them, or (None, None)."""
    rows = [line for line in text.split("\n") if line.startswith("| `race_calendar` |")]
    if len(rows) != 1:
        return None, None
    before = re.search(r"(\d+) hours? before", rows[0])
    after = re.search(r"(\d+) hours? after", rows[0])
    return (int(before.group(1)) if before else None,
            int(after.group(1)) if after else None)


def config_keys(text):
    """What Config.raceCalendar() reads out of the file, as {theme, sessions, race, session}."""
    start = text.index("private static List<RaceWeekend.Race> raceCalendar(")
    end = text.index("private static void syncThemeChoices(", start)
    body = text[start:end]
    theme = re.search(r'json\.optJSONArray\("([a-z_]+)"\)', body)
    sessions = re.search(r'r\.optJSONArray\("([a-z_]+)"\)', body)
    return {
        "theme": theme.group(1) if theme else None,
        "sessions": sessions.group(1) if sessions else None,
        "race": set(re.findall(r'r\.opt(?:String|Int)\("([a-z_]+)"', body)),
        "session": set(re.findall(r's\.optString\("([a-z_]+)"', body)),
    }


def block(when):
    """A date and time the way Jolpica writes one, split into the two fields it uses."""
    return {"date": when.strftime("%Y-%m-%d"), "time": when.strftime("%H:%M:%S") + "Z"}


def weekend(race_start, sprint=False):
    """One race as Jolpica serves it, with the sessions a weekend of that kind has, around `race_start`."""
    sessions = {"FirstPractice": race_start - datetime.timedelta(days=2)}
    if sprint:
        sessions["SprintQualifying"] = race_start - datetime.timedelta(days=1, hours=6)
        sessions["Sprint"] = race_start - datetime.timedelta(days=1, hours=2)
        sessions["Qualifying"] = race_start - datetime.timedelta(hours=20)
    else:
        sessions["SecondPractice"] = race_start - datetime.timedelta(days=1, hours=20)
        sessions["ThirdPractice"] = race_start - datetime.timedelta(days=1, hours=2)
        sessions["Qualifying"] = race_start - datetime.timedelta(hours=20)
    race = {"round": "7", "raceName": "Example Grand Prix",
            "Circuit": {"Location": {"locality": "Exampleville"}}}
    race.update(block(race_start))
    for key, when in sessions.items():
        race[key] = block(when)
    return race


def written(races, now):
    """The script's own calendar() over `races`, as the file it writes."""
    return race_calendar.calendar(races, now)


class TheFilesTest(unittest.TestCase):
    """The three shipped files, read against each other."""

    def setUp(self):
        self.weekend_java = RACE_WEEKEND.read_text(encoding="utf-8")
        self.config_java = CONFIG.read_text(encoding="utf-8")
        self.page = PAGE.read_text(encoding="utf-8")

    def test_the_session_names_the_script_writes_are_the_ones_the_panel_answers(self):
        script = {name for _, name in race_calendar.SESSIONS} | {"RACE"}
        panel = set(switch_cases(self.weekend_java, "title"))
        self.assertGreaterEqual(len(panel), 7, "no session names were read out of RaceWeekend.java")
        self.assertEqual(sorted(script - panel), [],
                         f"tools/race_calendar.py writes {sorted(script - panel)} and RaceWeekend.title() "
                         f"does not answer it, so the panel shows the raw name")
        self.assertEqual(sorted(panel - script), [],
                         f"RaceWeekend.title() answers {sorted(panel - script)} and no calendar this report "
                         f"writes can name it")

    def test_the_panel_answers_each_name_in_both_of_its_lines(self):
        title = switch_cases(self.weekend_java, "title")
        short = switch_cases(self.weekend_java, "shortName")
        self.assertGreaterEqual(len(short), 7, "no session names were read out of shortName()")
        self.assertEqual(sorted(set(short) - set(title)), [],
                         "a name the countdown line answers and the session row does not (or the other way "
                         "round) reads differently in the panel's two lines")

    def test_a_weekend_is_written_with_the_keys_the_launcher_reads(self):
        keys = config_keys(self.config_java)
        self.assertEqual("race_calendar", keys["theme"],
                         "Config.raceCalendar() no longer reads the theme key tools/race_calendar.py writes")
        self.assertEqual("sessions", keys["sessions"],
                         "Config.raceCalendar() reads the sessions under another name")
        self.assertEqual(RACE_KEYS, keys["race"] | {keys["sessions"]},
                         f"Config.raceCalendar() reads {sorted(keys['race'] | {keys['sessions']})} of a race "
                         f"and the script writes {sorted(RACE_KEYS)}")
        self.assertEqual(SESSION_KEYS, keys["session"],
                         f"Config.raceCalendar() reads {sorted(keys['session'])} of a session and the script "
                         f"writes {sorted(SESSION_KEYS)}")

    def test_a_session_start_is_the_shape_instant_parse_takes(self):
        start = datetime.datetime(2026, 10, 25, 14, 0, tzinfo=datetime.timezone.utc)
        races = written([weekend(start)], start - datetime.timedelta(days=3))
        self.assertEqual(1, len(races), "the weekend the panel would show was dropped")
        for session in races[0]["sessions"]:
            with self.subTest(session=session["name"]):
                self.assertRegex(session["start"], INSTANT,
                                 "java.time.Instant.parse reads 2026-10-25T14:00:00Z and drops anything else")

    def test_a_sprint_weekend_names_its_sessions_the_way_the_panel_does(self):
        start = datetime.datetime(2026, 10, 25, 14, 0, tzinfo=datetime.timezone.utc)
        races = written([weekend(start, sprint=True)], start - datetime.timedelta(days=3))
        names = [s["name"] for s in races[0]["sessions"]]
        self.assertEqual(["FP1", "SPRINT QUALI", "SPRINT", "QUALIFYING", "RACE"], names)
        panel = set(switch_cases(self.weekend_java, "title"))
        for name in names:
            with self.subTest(name=name):
                self.assertIn(name, panel, "the panel does not answer a session this calendar names")

    def test_the_race_is_the_last_session_and_holds_the_race_time(self):
        start = datetime.datetime(2026, 10, 25, 14, 0, tzinfo=datetime.timezone.utc)
        races = written([weekend(start)], start - datetime.timedelta(days=3))
        sessions = races[0]["sessions"]
        self.assertEqual("RACE", sessions[-1]["name"],
                         "RaceWeekend closes the panel TAIL after the last session, so the race is the last one")
        self.assertEqual("2026-10-25T14:00:00Z", sessions[-1]["start"])
        self.assertEqual(7, races[0]["round"])
        self.assertEqual("Exampleville", races[0]["place"])

    def test_the_page_states_the_lead_and_the_tail_the_panel_uses(self):
        hours = duration_hours(self.weekend_java)
        self.assertEqual({"LEAD": 12, "TAIL": 4}, hours,
                         "the panel's two Durations were not read out of RaceWeekend.java")
        before, after = page_window(self.page)
        self.assertEqual(hours["LEAD"], before,
                         "themes/README.md's race_calendar row no longer states when the panel opens")
        self.assertEqual(hours["TAIL"], after,
                         "themes/README.md's race_calendar row no longer states when the panel closes")

    def test_the_calendar_keeps_a_race_the_panel_is_still_showing(self):
        hours = duration_hours(self.weekend_java)["TAIL"]
        start = datetime.datetime(2026, 10, 25, 14, 0, tzinfo=datetime.timezone.utc)
        now = start + datetime.timedelta(hours=hours) - datetime.timedelta(seconds=1)
        races = written([weekend(start)], now)
        self.assertEqual(1, len(races),
                         f"the panel shows until TAIL ({hours} h) after the race starts, so the calendar has "
                         f"to hold the race that long: it was dropped before the panel closed")

    def test_a_weekend_the_panel_left_long_ago_is_not_written(self):
        start = datetime.datetime(2026, 10, 25, 14, 0, tzinfo=datetime.timezone.utc)
        races = written([weekend(start)], start + datetime.timedelta(days=30))
        self.assertEqual([], races, "a race the panel left a month ago is still written into the theme")

    def test_the_three_files_were_read(self):
        # A walk that reads nothing passes every comparison above: pin what there is to read.
        self.assertGreaterEqual(len(self.weekend_java), 3000, "RaceWeekend.java was not read")
        self.assertGreaterEqual(len(self.config_java), 10000, "Config.java was not read")
        self.assertGreaterEqual(len(self.page.split("\n")), 100, "themes/README.md was not read")
        self.assertGreaterEqual(len(race_calendar.SESSIONS), 7)
        self.assertEqual(1, len([line for line in self.page.split("\n") if line.startswith("| `race_calendar` |")]),
                         "the Keys table holds one row per key, and race_calendar is now not one of them")


class TheReaderTest(unittest.TestCase):
    """The readers above, over Java and a page written here."""

    JAVA = ('static final Duration LEAD = Duration.ofHours(6);\n'
            'static final Duration TAIL = Duration.ofHours(2);\n'
            '        String title(Resources res) {\n'
            '            switch (name) {\n'
            '                case "FP1": return res.getString(R.string.race_practice_1);\n'
            '                case "RACE": return res.getString(R.string.race_race);\n'
            '                default: return name;\n'
            '            }\n'
            '        }\n'
            '        String shortName(Resources res) {\n'
            '            switch (name) {\n'
            '                case "FP1": return res.getString(R.string.race_practice_1_short);\n'
            '                default: return name;\n'
            '            }\n'
            '        }\n')

    def test_a_switch_reads_only_its_own_cases(self):
        self.assertEqual(["FP1", "RACE"], switch_cases(self.JAVA, "title"))
        self.assertEqual(["FP1"], switch_cases(self.JAVA, "shortName"))

    def test_a_missing_case_is_a_name_the_panel_does_not_answer(self):
        self.assertNotIn("QUALIFYING", switch_cases(self.JAVA, "title"))

    def test_the_two_durations_are_read(self):
        self.assertEqual({"LEAD": 6, "TAIL": 2}, duration_hours(self.JAVA))

    def test_a_page_row_states_its_window(self):
        page = ("| `race_calendar` | list of races | from 6 hours before a weekend's first session until "
                "2 hours after the race starts |")
        self.assertEqual((6, 2), page_window(page))

    def test_a_row_without_its_numbers_is_reported_as_missing(self):
        page = "| `race_calendar` | list of races | a panel under the clock, times in the TV's zone |"
        self.assertEqual((None, None), page_window(page))
        self.assertEqual((None, None), page_window("no keys table at all"))

    def test_config_reads_are_read_off_the_two_variables(self):
        java = ('private static List<RaceWeekend.Race> raceCalendar(JSONObject json) {\n'
                '    JSONArray list = json.optJSONArray("race_calendar");\n'
                '    JSONArray sessions = r.optJSONArray("sessions");\n'
                '    sessions.optJSONObject(j);\n'
                '    String name = s.optString("name", "");\n'
                '    s.optString("start", "");\n'
                '    r.optInt("round", 0);\n'
                '    r.optString("name", "Grand Prix");\n'
                '    r.optString("place", "");\n'
                '}\n'
                'private static void syncThemeChoices(File dir) {\n'
                '    json.optString("not read here");\n'
                '}\n')
        keys = config_keys(java)
        self.assertEqual("race_calendar", keys["theme"])
        self.assertEqual("sessions", keys["sessions"])
        self.assertEqual({"round", "name", "place"}, keys["race"])
        self.assertEqual({"name", "start"}, keys["session"])


if __name__ == "__main__":
    unittest.main()

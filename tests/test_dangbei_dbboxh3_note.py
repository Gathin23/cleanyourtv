"""docs/devices/dangbei-dbboxh3.md records a box whose Home key kills system_server, and holds its facts.

#2286 is a Dangbei DBBOXH3 (当贝盒子 H3, Rockchip `rk356x_box`, Android 11 `RD2A.211001.002`) whose owner
traced a soft-reboot loop to one disabled package: `com.dangbei.dbdownloadrefs` is the list the vendor's
own `ProcessManagementService` inside `system_server` force-stops packages from, so with the package off
the provider answers null and `forceStopPackages` throws, once per Home press. The name reads like a
promo downloader - exactly what a generic list drops first - which is why the report asked for it to be
known before the next owner of this board meets it.

What the note says about the tools is read out of the tool, not copied: the guard that refuses a package
lives in tools/recovery.py (`CORE_RE`, which tools/disable.sh, disable.ps1 and check.py --fix carry), and
a test here asks it about this name, so a guard that grows to cover it makes the note's "the tools cannot
refuse it" sentence fail rather than sit there as a stale excuse.

The rest is the reporter's measurement from the issue: the four `system_server` pids, the stack trace, the
two commands that reach the crash, the way back past the leftover `stopped` flag, and the nine presses
that held afterwards. No TV was available here, so nothing below is a second run.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import recovery  # noqa: E402

NOTE = ROOT / "docs" / "devices" / "dangbei-dbboxh3.md"
INDEX = ROOT / "examples" / "README.md"
PACKAGE = "com.dangbei.dbdownloadrefs"
HEADING = "Notes for models that have no tested list yet"
CLOSING = "To add your TV"

# What the set is, as #2286 records it.
DEVICE = ["DBBOXH3", "当贝盒子 H3", "rk356x_box", "Rockchip", "Android 11", "API 30",
          "RD2A.211001.002", "com.dangbei.leard.leradlauncher"]

# The stack trace the report pasted, frame by frame.
FRAMES = [
    "FATAL EXCEPTION IN SYSTEM PROCESS: OptEngine-ProcessManagementService",
    "java.lang.NullPointerException: Attempt to invoke interface method",
    "'java.util.Iterator java.util.List.iterator()' on a null object reference",
    "SystemUtilsManagerService.forceStopPackages(SystemUtilsManagerService.java:626)",
    "SystemUtilsManager.forceStopPackages(SystemUtilsManager.java:342)",
    "ProcessManagementService$ProcessManagementHandler.handleMessage(ProcessManagementService.java:68)",
]

# The four deaths, in order: each press took a fresh system_server.
PIDS = ["436", "3116", "5202", "7323"]

# What the package is, as `dumpsys package` answers it on that build.
APK = ["/system/priv-app/DBdownloadrefs", "android.uid.system",
       "com.dangbei.dbdownloadrefs/.db.DatabaseProvider"]


def joined(text):
    """The page's own wording, with its wrapped lines joined: matching is not about the wrapping."""
    return " ".join(text.split())


def missing(text, needles):
    """The needles the page does not carry, in the order they were asked for.

    A list rather than `assertIn`: a failing check on a 60-line page should name the one sentence that
    went missing instead of pasting the whole page back into the CI log.
    """
    body = joined(text)
    return [needle for needle in needles if needle not in body]


def note():
    return NOTE.read_text(encoding="utf-8")


def section(text, start=HEADING, end=CLOSING):
    """The bullets of a README between its notes heading and the paragraph that closes the page."""
    if start not in text:
        return ""
    after = text.split(start, 1)[1]
    return after.split(end, 1)[0]


def linked(markdown, *targets):
    """The destinations in one page's markdown links."""
    out = []
    for chunk in markdown.split("](")[1:]:
        dest = chunk.split(")", 1)[0].split()[0]
        out.append(dest)
    return [dest for dest in out if any(dest.endswith(target) for target in targets)]


class TheDevice(unittest.TestCase):
    def test_the_note_identifies_the_set(self):
        self.assertEqual([], missing(note(), DEVICE), "the note no longer names what #2286 recorded")

    def test_the_title_names_the_model_and_the_board(self):
        self.assertEqual([], missing(note().splitlines()[0], ["DBBOXH3", "rk356x_box"]),
                         "the note's title does not say which box it is about")


class TheKeepEnabledClaim(unittest.TestCase):
    def test_the_package_is_named_as_one_to_keep(self):
        self.assertEqual([], missing(note(), [f"## `{PACKAGE}` has to stay enabled"]),
                         "the note's section no longer says the package stays enabled")

    def test_what_the_package_is_on_that_build(self):
        self.assertEqual([], missing(note(), APK),
                         "the note no longer records that this is a privileged system-uid provider")

    def test_the_tools_cannot_refuse_the_name(self):
        # Read out of the guard the three tools carry: this name is not one it stops a list with.
        self.assertEqual([], recovery.core_packages([PACKAGE]),
                         "the guard now refuses the package, so the note's point is gone: say so in the "
                         "note and move the claim to the guard that answers it")
        self.assertIsNone(recovery.CORE_RE.match(PACKAGE))

    def test_the_note_says_why_the_tools_will_not_help(self):
        needles = ["tools/disable.sh", "disable.ps1", "check.py --fix", "recovery.core_packages()",
                   "TV middleware", "human one"]
        self.assertEqual([], missing(note(), needles),
                         "the note no longer says how far the tools' refusal reaches")

    def test_it_points_at_the_read_that_answers_it(self):
        self.assertEqual([], missing(note(), [f"dumpsys package {PACKAGE}"]),
                         "the note no longer says to read the package before a generic list takes it")


class TheCrash(unittest.TestCase):
    def test_the_stack_trace_is_quoted(self):
        self.assertEqual([], missing(note(), FRAMES),
                         "the note has lost a line of the crash it documents")

    def test_every_death_is_recorded_in_order(self):
        body = joined(note())
        self.assertEqual(PIDS, [pid for pid in PIDS if pid in body],
                         "the note no longer lists the four system_server pids in order")

    def test_the_chain_from_the_home_key_is_spelled_out(self):
        needles = ["Home key", "launcher resumed", "OptEngine", "answers null", "forceStopPackages",
                   "soft-reboots", "uptime"]
        self.assertEqual([], missing(note(), needles),
                         "the note no longer explains how a Home press reaches the crash")

    def test_the_wider_firmware_bug_is_named_as_untested(self):
        needles = ["corrupted database", "that half was not tested"]
        self.assertEqual([], missing(note(), needles),
                         "the note either drops the route that needs no debloat or claims it was tried")


class TheWayBack(unittest.TestCase):
    def test_pm_enable_is_the_way_back(self):
        self.assertEqual([], missing(note(), [f"pm enable {PACKAGE}"]),
                         "the note no longer gives the undo")

    def test_the_leftover_stopped_flag_and_its_fix_are_recorded(self):
        needles = ["stopped", "cmd package set-stopped-state",
                   f"am broadcast -n {PACKAGE}/.receiver.BootReceiver -a com.dangbei.probe.PING"]
        self.assertEqual([], missing(note(), needles),
                         "the note no longer records that pm enable alone leaves the package stopped")

    def test_the_verification_is_recorded(self):
        self.assertEqual([], missing(note(), ["nine consecutive Home presses", "pid unchanged"]),
                         "the note no longer says what the owner checked after the undo")

    def test_the_reproduce_commands_are_marked_as_the_hazard(self):
        self.assertEqual([], missing(note(), ["not a step to copy"]),
                         "the two commands that crash the box read as advice again")


class NothingOutOfLane(unittest.TestCase):
    def test_no_uninstall_and_no_root(self):
        found = [word for word in ("pm uninstall", "magisk", "su -c") if word in note().lower()]
        self.assertEqual([], found,
                         "a docs note in this repo may not suggest uninstalling or rooting a set")

    def test_the_limits_are_said(self):
        needles = ["no debloat list was kept", "there is no list", "reporter's measurement"]
        self.assertEqual([], missing(note(), needles), "the note no longer says what it does not cover")


class TheIndex(unittest.TestCase):
    def test_the_no_list_section_links_the_note(self):
        found = linked(section(INDEX.read_text(encoding="utf-8")), "docs/devices/dangbei-dbboxh3.md")
        self.assertEqual(["../docs/devices/dangbei-dbboxh3.md"], found,
                         "examples/README.md's notes-for-models-without-a-list section does not link "
                         "the note, so nothing leads a reader to it")

    def test_the_note_is_not_a_list_row(self):
        folders = sorted(p.name for p in (ROOT / "examples").iterdir() if p.is_dir())
        self.assertEqual([], [name for name in folders if "dangbei" in name],
                         "an examples/ folder now exists for this board: the note belongs in its row")
        cells = []
        for path in sorted((ROOT / "examples").glob("*/tv.json")):
            data = json.loads(path.read_text(encoding="utf-8"))
            cells.append(f"{data.get('tv', '')} {data.get('notes', '')}")
        self.assertEqual([], [cell for cell in cells if "DBBOXH3" in cell],
                         "a tv.json claims this board, so the bullet belongs in that folder's row")

    def test_the_bullet_says_the_package_and_the_outcome(self):
        bullet = section(INDEX.read_text(encoding="utf-8"))
        self.assertEqual([], missing(bullet, [PACKAGE, "system_server", "no debloat list"]),
                         "the index bullet no longer says what the note is about")


class TheReadersBehindTheseTests(unittest.TestCase):
    """The two helpers above are how the checks above read the tree, so they are checked too."""

    def test_section_stops_at_the_closing_paragraph(self):
        text = f"{HEADING}:\n\n- [A](../docs/devices/a.md)\n\n{CLOSING}, see ...\n- [B](b.md)\n"
        inner = section(text)
        self.assertIn("docs/devices/a.md", inner)
        self.assertNotIn("b.md", inner, "the section runs past the paragraph that closes the page")

    def test_a_link_before_the_heading_is_not_in_the_section(self):
        text = f"- [A](../docs/devices/dangbei-dbboxh3.md)\n\n{HEADING}:\n\n- [B](b.md)\n\n{CLOSING}\n"
        self.assertEqual([], linked(section(text), "docs/devices/dangbei-dbboxh3.md"),
                         "a bullet above the heading was counted as one of the notes")

    def test_a_page_without_the_heading_has_no_section(self):
        self.assertEqual("", section("- [A](a.md)\n"))

    def test_linked_reads_the_destination_not_the_title(self):
        markdown = '- [Dangbei](../docs/devices/dangbei-dbboxh3.md "a title"): text\n'
        self.assertEqual(["../docs/devices/dangbei-dbboxh3.md"],
                         linked(markdown, "docs/devices/dangbei-dbboxh3.md"))
        self.assertEqual([], linked(markdown, "docs/devices/other.md"))


if __name__ == "__main__":
    unittest.main()

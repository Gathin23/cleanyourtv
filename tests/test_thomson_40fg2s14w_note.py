"""The Thomson 40FG2S14W note (#1035) and the CVTE advice it corrects.

The note is documentation only, so the tests pin what makes it useful - the board, the build, the six
packages, and the fact that the stock launcher stayed enabled - plus the one claim in
docs/TROUBLESHOOTING.md's CVTE bullet that this session falsifies: `set-home-activity` answering
`Success` is not proof that Home moved.

They assert identifiers and commands, not sentences, so rewording the prose does not fail them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "thomson-40fg2s14w.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
EXAMPLES_README = ROOT / "examples" / "README.md"
DISABLE_SH = ROOT / "tools" / "disable.sh"
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"
GRADLE = ROOT / "launcher" / "app" / "build.gradle"
ANDROID = "{http://schemas.android.com/apk/res/android}"

LAUNCHER = "com.gathin.tvhome"
STOCK = "com.google.android.apps.tv.launcherx"
HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
SET_HOME = f"cmd package set-home-activity --user 0 {LAUNCHER}/.MainActivity"

DISABLED = [
    "tv.anoki.acr.anokiacroptin",
    "com.android.adservices.api",
    "com.android.ondevicepersonalization.services",
    "com.android.federatedcompute.services",
    "com.google.android.feedback",
    "com.android.tv.feedbackconsent",
]

LINK = re.compile(r"\]\(([^)\s]+)\)")

# The five HOME activities #1350's query answered on this board, highest priority first.
HOME_ACTIVITIES = [
    "com.google.android.apps.tv.launcherx/.home.HomeActivity",
    "com.google.android.tungsten.setupwraith/.RecoveryActivity",
    "com.gathin.tvhome/.MainActivity",
    "com.express.at.factory.factorymanager/.FallbackHome",
    "com.android.tv.settings/.system.FallbackHome",
]


def bullet(text, start):
    """The markdown bullet that begins with `start`, up to the next top-level bullet or heading."""
    lines = text.split("\n")
    first = next(i for i, line in enumerate(lines) if line.startswith(start))
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def broken_links(path):
    broken = []
    for target in LINK.findall(path.read_text(encoding="utf-8")):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        local = target.split("#", 1)[0]
        if local and not (path.parent / local).exists():
            broken.append(target)
    return broken


def note_text():
    return NOTE.read_text(encoding="utf-8")


def cvte_bullet():
    """The bullet of docs/TROUBLESHOOTING.md that carries the FallbackHome and direct-boot checks."""
    return bullet(TROUBLE.read_text(encoding="utf-8"),
                  "- **Black screen at every boot after the launcher change")


def application_id():
    """The launcher's package name, what the manifest's `.MainActivity` expands to."""
    gradle = GRADLE.read_text(encoding="utf-8")
    match = re.search(r"launcherApplicationId'\)\.getOrElse\('([^']+)'\)", gradle)
    if match:
        return match.group(1)
    return re.search(r"namespace '([^']+)'", gradle).group(1)


def manifest_components(text):
    """[(name, home, direct_boot_aware)] for every component of a manifest.

    `home` marks the component that `cmd package query-activities -c android.intent.category.HOME`
    answers with; `direct_boot_aware` is the component's flag, inherited from <application> when the
    component sets none. Nothing in the manifest is expanded, so names keep their leading dot.
    """
    root = ET.fromstring(text)
    application = root.find("application")
    if application is None:
        return []
    inherited = (application.get(ANDROID + "directBootAware") or "").lower() == "true"
    out = []
    for element in application:
        if element.tag not in ("activity", "service", "receiver", "provider"):
            continue
        flag = element.get(ANDROID + "directBootAware")
        aware = inherited if flag is None else flag.lower() == "true"
        home = any(
            "android.intent.action.MAIN" in {a.get(ANDROID + "name") for a in filter_.findall("action")}
            and "android.intent.category.HOME" in {c.get(ANDROID + "name") for c in filter_.findall("category")}
            for filter_ in element.findall("intent-filter"))
        out.append((element.get(ANDROID + "name") or "", home, aware))
    return out


def recipe_commands():
    """The two boot checks the CVTE bullet hands a reader, with the package placeholder filled in.

    Read out of the bullet rather than copied, so a command the bullet changes has to be changed in
    the note as well; the backslash before a quote (the bullet escapes it for `grep -o`) is dropped.
    The resolver command in the same bullet is left out: the note's Home section already carries it.
    """
    commands = [command.replace("<new.launcher.package>", application_id()).replace("\\", "")
                for command in re.findall(r"tools/tv\.py '([^']+)'", cvte_bullet())]
    return [c for c in commands if "query-activities" in c or "DIRECT_BOOT_AWARE" in c]


class ThomsonNoteTest(unittest.TestCase):
    def test_note_exists_and_names_the_board(self):
        self.assertTrue(NOTE.is_file(), "docs/devices note missing")
        text = note_text()
        for token in ("40FG2S14W", "nagata", "Expressluck", "UKNV.260317.001", "Android TV 14",
                      "34", "com.cvte.tv.at", STOCK, "issues/1035"):
            with self.subTest(token=token):
                self.assertIn(token, text)

    def test_every_disabled_package_is_recorded(self):
        text = note_text()
        for package in DISABLED:
            with self.subTest(package=package):
                self.assertIn(package, text)

    def test_note_keeps_the_stock_launcher_enabled(self):
        # The whole point: the handover is not kept, because a CVTE board without a tested profile
        # keeps its stock launcher (the #741 boot hang). A note that told readers to disable it would
        # be the opposite advice.
        text = note_text()
        self.assertIn(SET_HOME, text)
        self.assertIn("Success", text)
        self.assertIn("741", text)
        self.assertIsNone(re.search(r"pm disable-user[^\n]*" + re.escape(STOCK), text),
                          "the note must not disable the stock launcher")

    def test_note_reads_the_resolver(self):
        # The check, not the sentence around it: the note has to send a reader to resolve-activity,
        # because that is the query the Home guard reads and the one that disagreed on this board.
        self.assertIn("resolve-activity", note_text())

    def test_note_is_not_an_examples_list(self):
        # A one-session report stays a device note; an examples/ folder needs several days of use.
        self.assertFalse(list((ROOT / "examples").glob("thomson-40fg2s14w")),
                         "a one-session report does not belong in examples/")
        self.assertTrue(NOTE.is_file())

    def test_note_links_resolve(self):
        self.assertEqual(broken_links(NOTE), [])

    def test_note_records_the_two_boot_checks_and_what_they_answered(self):
        # The note's Home section sends the next session to the checks; #1350 ran them, so the note
        # has to carry the answers: the five HOME activities, the package-level direct-boot line and
        # the commands, as the bullet gives them.
        text = note_text()
        for command in recipe_commands():
            with self.subTest(command=command):
                self.assertIn(command, text, "the note must quote the check the bullet hands a reader")
        self.assertIn("PARTIALLY_DIRECT_BOOT_AWARE", text)
        for activity in HOME_ACTIVITIES:
            with self.subTest(activity=activity):
                self.assertIn(activity, text)

    def test_note_does_not_say_the_boot_was_tried(self):
        # What #1350 did was read-only: no fallback launched, no handover attempted. A note that
        # started handing the board over would need a run on the TV behind it.
        self.assertIsNone(re.search(r"pm disable-user[^\n]*" + re.escape(STOCK), note_text()))
        self.assertIn("not been tried", note_text())


class DirectBootClaimTest(unittest.TestCase):
    """The CVTE bullet's claim about this launcher, against the manifest that has to back it.

    The bullet used to say "This repo's launcher is direct-boot-aware", which reads as "the Home
    activity can start before the first unlock". The manifest says otherwise: one service carries
    the flag, the activity that answers HOME does not, so the package-level dump answers PARTIALLY
    and cannot settle the question on a set with no FallbackHome.
    """

    def setUp(self):
        self.components = manifest_components(MANIFEST.read_text(encoding="utf-8"))
        self.home = [name for name, home, _ in self.components if home]
        self.aware = [name for name, _, aware in self.components if aware]

    def test_the_manifest_is_one_home_activity_a_second_component_marks_aware(self):
        # Both facts the corrected bullet rests on. If either changes, the bullet and the note have
        # to be read again rather than left describing a launcher that no longer exists.
        self.assertGreaterEqual(len(self.components), 8, "the manifest read as almost nothing")
        self.assertEqual(len(self.home), 1, "the launcher declares one HOME activity")
        self.assertTrue(self.aware, "no component declares directBootAware: the answer is not PARTIALLY")
        self.assertNotIn(self.home[0], self.aware,
                         "the launcher's HOME activity now declares directBootAware: the CVTE bullet and "
                         "docs/devices/thomson-40fg2s14w.md have to be updated with it")

    def test_the_bullet_names_the_component_that_carries_the_flag(self):
        cvte = cvte_bullet()
        for name in self.aware:
            with self.subTest(component=name):
                self.assertIn(name.lstrip("."), cvte.replace("`", ""),
                              "the bullet must say which component is direct-boot aware")

    def test_the_bullet_names_the_home_activity_that_does_not(self):
        cvte = cvte_bullet().replace("`", "")
        self.assertIn(self.home[0].lstrip("."), cvte,
                      "the bullet must name the Home activity it is talking about")
        self.assertIn("PARTIALLY_DIRECT_BOOT_AWARE", cvte,
                      "the package-level answer has to be in the bullet, or a reader takes it as a yes")


class ManifestReaderTest(unittest.TestCase):
    """`manifest_components()` must report a real problem, not pass by reading nothing."""

    PAGE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '  <application android:label="x">\n'
            '    <activity android:name=".Home" android:directBootAware="true">\n'
            '      <intent-filter>\n'
            '        <action android:name="android.intent.action.MAIN" />\n'
            '        <category android:name="android.intent.category.HOME" />\n'
            '      </intent-filter>\n'
            '    </activity>\n'
            '    <activity android:name=".Other">\n'
            '      <intent-filter>\n'
            '        <action android:name="android.intent.action.MAIN" />\n'
            '        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />\n'
            '      </intent-filter>\n'
            '    </activity>\n'
            '    <service android:name=".Plain" />\n'
            '  </application>\n'
            '</manifest>\n')

    def test_reads_the_home_filter_and_the_flag(self):
        self.assertEqual(manifest_components(self.PAGE),
                         [(".Home", True, True), (".Other", False, False), (".Plain", False, False)])

    def test_the_application_flag_is_inherited(self):
        page = self.PAGE.replace('<application android:label="x">',
                                 '<application android:label="x" android:directBootAware="true">')
        self.assertEqual([(name, aware) for name, _, aware in manifest_components(page)],
                         [(".Home", True), (".Other", True), (".Plain", True)])

    def test_a_manifest_without_an_application_reads_as_empty(self):
        self.assertEqual(manifest_components('<manifest xmlns:android="http://schemas.android.com/apk/res/android" />'), [])


class TroubleshootingCvteBulletTest(unittest.TestCase):
    def setUp(self):
        self.text = TROUBLE.read_text(encoding="utf-8")
        self.cvte = bullet(self.text, "- **Black screen at every boot after the launcher change")

    def test_the_fallback_home_check_is_still_there(self):
        # The caveat is added under an existing recipe; the recipe itself must not be dropped.
        self.assertIn("FallbackHome", self.cvte)
        self.assertIn("DIRECT_BOOT_AWARE", self.cvte)
        self.assertIn("set-home-activity", self.cvte)

    def test_success_is_no_longer_presented_as_proof(self):
        for token in ("Success", "resolve-activity", "40FG2S14W"):
            with self.subTest(token=token):
                self.assertIn(token, self.cvte)

    def test_the_resolver_command_matches_the_tools(self):
        # Read the query out of disable.sh instead of trusting a copy: the guard and the docs have to
        # ask the same question to get the same answer.
        src = DISABLE_SH.read_text(encoding="utf-8")
        match = re.search(r"HOME_QUERY='([^']+)'", src)
        self.assertIsNotNone(match, "tools/disable.sh no longer defines HOME_QUERY")
        query = match.group(1).strip()
        self.assertEqual(query, HOME_QUERY, "disable.sh's Home query changed")
        self.assertIn(query, self.cvte)

    def test_the_bullet_points_at_the_note(self):
        self.assertIn("devices/thomson-40fg2s14w.md", self.cvte)
        self.assertEqual(broken_links(TROUBLE), [])


class IndexTest(unittest.TestCase):
    def test_examples_readme_indexes_the_note(self):
        text = EXAMPLES_README.read_text(encoding="utf-8")
        section = text.split("Notes for models that have no tested list yet", 1)[1]
        self.assertIn("docs/devices/thomson-40fg2s14w.md", section)
        self.assertIn("40FG2S14W", section)

    def test_examples_readme_links_resolve(self):
        self.assertEqual(broken_links(EXAMPLES_README), [])


class CheckerTest(unittest.TestCase):
    """The readers above must report a real problem, not pass by reading nothing."""

    PAGE = ("- **One** first bullet\n"
            "  continued here\n"
            "  and here\n"
            "- **Two** second bullet\n"
            "## Section\n"
            "- **Three** third bullet\n")

    def test_bullet_keeps_its_continuation_lines(self):
        self.assertEqual(bullet(self.PAGE, "- **One**"),
                         "- **One** first bullet\n  continued here\n  and here")

    def test_bullet_stops_at_a_heading(self):
        self.assertNotIn("Three", bullet(self.PAGE, "- **Two**"))

    def test_bullet_raises_on_a_missing_bullet(self):
        with self.assertRaises(StopIteration):
            bullet(self.PAGE, "- **Missing**")

    def test_broken_links_reports_a_missing_target(self):
        page = ROOT / "tests" / "_link_probe.md"
        page.write_text("[gone](no-such-file.md)\n", encoding="utf-8")
        try:
            self.assertEqual(broken_links(page), ["no-such-file.md"])
        finally:
            page.unlink()

    def test_broken_links_ignores_external_and_anchors(self):
        page = ROOT / "tests" / "_link_probe.md"
        page.write_text("[a](https://example.com/x.md)\n[b](#some-heading)\n", encoding="utf-8")
        try:
            self.assertEqual(broken_links(page), [])
        finally:
            page.unlink()


if __name__ == "__main__":
    unittest.main()

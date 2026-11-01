"""The #795 report, second half: the KitKat set was recovered and the cause was isolated.

The merged troubleshooting bullet said a set stuck on `INICIALIZANDO` was not to be brought back, and
left three candidate causes open. The same reporter then isolated the cause, recovered the TV with
`pm enable` plus a reboot, and re-applied the list in batches. These tests pin that record, the
recovery steps, and that the bullet no longer tells readers the set is lost.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "coop-cotv-0001.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
ISSUE = "https://github.com/Gathin23/cleanyourtv/issues/795"
LAUNCHER = "com.haiersoft.launcherproject2995hk"
DEPENDENCY = "com.tsb.tv"

DISABLED = [
    "com.android.galaxy4",
    "com.android.noisefield",
    "com.android.dreams.phototable",
    "com.android.dreams.basic",
    "com.android.soundrecorder",
    "com.android.speechrecorder",
    "com.realtek.camera",
    "com.rtk.android.miracast",
    "com.svox.pico",
    "com.adups.fota",
    "com.adups.fota.sysoper",
    "cn.haiqin.realtek.other.vstoresubclient",
    "com.cvte.taskmanager",
    "com.rtk.mediabrowser",
    "com.hikeen.xymenu",
]

LINK = re.compile(r"\]\(([^)\s]+)\)")

BULLET_START = "- **The TV stays on the boot logo after a list"


def flat(text):
    """Prose wrapped across lines, so match on a single line of it."""
    return re.sub(r"[ \t]*\n[ \t]*", " ", text)


def bullet(text, start):
    """The markdown bullet that begins with `start`, up to the next top-level bullet."""
    lines = text.split("\n")
    first = next((i for i, line in enumerate(lines) if line.startswith(start)), None)
    if first is None:
        raise AssertionError("no troubleshooting bullet starts with %r" % start)
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def broken_links(path):
    return [
        target for target in LINK.findall(path.read_text(encoding="utf-8"))
        if not target.startswith(("http://", "https://", "mailto:", "#"))
        and target.split("#", 1)[0]
        and not (path.parent / target.split("#", 1)[0]).exists()
    ]


class CoopNoteTest(unittest.TestCase):
    def setUp(self):
        self.text = NOTE.read_text(encoding="utf-8")

    def test_note_exists_and_identifies_the_build(self):
        self.assertIn("rtd298x_tv001", self.text)
        self.assertIn("4.4.2", self.text)
        self.assertIn("SDK 19", self.text)
        self.assertIn("KOT49H eng.yeh.20180522.224300 test-keys", self.text)
        self.assertIn("58", self.text)

    def test_note_links_the_issue_and_resolves_its_links(self):
        self.assertIn(ISSUE, self.text)
        self.assertEqual(broken_links(NOTE), [])

    def test_cause_is_stated_as_isolated_not_guessed(self):
        body = flat(self.text)
        self.assertIn("Disabling `%s` alone reproduces the hang" % LAUNCHER, body)
        self.assertIn("The hard power cut during the first boot was **not** the cause", body)

    def test_dependency_trap_is_recorded_with_its_evidence(self):
        body = flat(self.text)
        self.assertIn("Disabling `%s`" % DEPENDENCY, body)
        self.assertIn("Se ha detenido la aplicación lancherProject", body)
        self.assertIn("re-enabling it clears the dialog", body)

    def test_recovery_keeps_the_stuck_screen_reachable(self):
        body = flat(self.text)
        self.assertIn("still answer ADB", body)
        self.assertIn("INICIALIZANDO", body)
        self.assertIn("pm list packages -d", self.text)
        self.assertIn("pm enable", self.text)
        self.assertIn("tools/tv.py --reboot", self.text)
        self.assertIn("33–73 s", self.text)

    def test_every_disabled_package_is_recorded(self):
        for package in DISABLED:
            self.assertIn("`%s`" % package, self.text)

    def test_the_two_packages_that_must_stay_enabled_are_named(self):
        body = flat(self.text).split("**Must stay enabled:**", 1)[1].split("## Limits", 1)[0]
        self.assertIn(LAUNCHER, body)
        self.assertIn(DEPENDENCY, body)

    def test_no_example_list_is_claimed(self):
        """One evening, one power cycle short of a lived-with list: no examples/ entry."""
        self.assertFalse(
            (ROOT / "examples" / "coop-cotv-0001").exists(),
            "an examples/ entry would claim days of use this report does not have",
        )
        self.assertIn("## Limits", self.text)
        self.assertIn("No cold boot", flat(self.text))

    def test_launcher_half_is_described_as_impossible(self):
        body = flat(self.text)
        self.assertIn("minSdk 26", body)
        self.assertIn("SDK 19", body)

    def test_guard_behaviour_is_not_promised_for_this_set(self):
        body = flat(self.text)
        self.assertIn("dumpsys activity", body)
        self.assertIn("unverified", body)


class TroubleshootingBulletTest(unittest.TestCase):
    def setUp(self):
        self.text = TROUBLE.read_text(encoding="utf-8")
        self.body = bullet(self.text, BULLET_START)

    def test_bullet_exists_and_links_the_note(self):
        self.assertIn("devices/coop-cotv-0001.md", self.body)
        self.assertIn(ISSUE, self.body)

    def test_bullet_states_the_confirmed_cause(self):
        body = flat(self.body)
        self.assertIn(LAUNCHER, body)
        self.assertIn("reproduces the hang", body)

    def test_bullet_drops_the_open_questions_it_no_longer_has(self):
        body = flat(self.body)
        self.assertNotIn("What is *not* known", body)
        self.assertNotIn("Nothing here brings such a set back", body)
        self.assertNotIn("No route to host", body)
        self.assertNotIn("Treat it as how these sets get lost", body)

    def test_bullet_keeps_the_recovery_route(self):
        body = flat(self.body)
        self.assertIn("answered ADB on Ethernet", body)
        self.assertIn("pm enable", body)
        self.assertIn("still out of reach", body)

    def test_bullet_keeps_the_dependency_trap_and_the_batch_workflow(self):
        body = flat(self.body)
        self.assertIn(DEPENDENCY, body)
        self.assertIn("stopped the launcher", body)
        self.assertIn("Se ha detenido la aplicación lancherProject", body)
        self.assertIn("batches", body)


if __name__ == "__main__":
    unittest.main()

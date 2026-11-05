"""tools/disable.sh and tools/disable.ps1 say which listed packages the TV left enabled (#1376).

Both scripts send the list as one batch of `pm disable-user --user 0` lines, and they used to end with
status 0 whatever the TV made of it. On a Philips 70PUS8505 one package of a batch was still enabled in
two runs out of six and nothing in the run said so; on a TV that refuses `pm disable-user` for every
package (#577, #1185) a list that changed nothing read like one that went through. After the batch the
scripts now read `pm list packages -e --user 0`, name the listed packages still in it, say not to retry
in a loop, and exit 1. A read that fails or lists no package prints one line saying the check did not
run, and the run keeps its status 0: the batch itself went through.

The fake TV is the Home guard test's (tests/test_home_guard.py), with two answers added. The enabled
list for user 0 carries out the batch's `pm disable-user` lines, except for the packages in
state["pinned"], which stay as they were the way #1376's two did. The plain enabled list adds what a
second user still has enabled, which is how Android 14 answers without `--user`: it merges every user's
packages, and on a Chromecast with Google TV 4K (Google TV 14) com.google.android.videos and
com.android.tv.feedbackconsent are in it, enabled for a dormant second profile and disabled for user 0.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it.
"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_home_guard as home_guard  # noqa: E402

ROOT = home_guard.ROOT
STOCK = home_guard.STOCK
BEFORE = "com.example.before"
AFTER = "com.example.after"
HEADER = "Still enabled after this run"
COULD_NOT = "Could not check which listed packages are still enabled"

READ_BACK = r'''

# Added by tests/test_disable_verify.py: the batch's `pm disable-user --user 0` lines take effect,
# except for the packages in state["pinned"], which stay as they were (refused, or not held).
home_guard_answer = answer


def enabled_for_user_0():
    asked = {line.rsplit(" ", 1)[-1] for line in state["commands"]
             if line.startswith("pm disable-user --user 0 ")}
    off = set(state["disabled"]) | (asked - set(state.get("pinned", [])))
    return [name for name in state["installed"] if name not in off]


def answer(command):
    if command == "pm list packages -e --user 0":
        if state.get("enabled_fails"):
            raise Fail()
        if "enabled_answer" in state:
            return state["enabled_answer"]
        return "".join(f"package:{name}\n" for name in enabled_for_user_0())
    if command == "pm list packages -e":
        # Android 14 without --user: every user's enabled packages, merged.
        names = enabled_for_user_0()
        names += [name for name in state.get("other_user_enabled", []) if name not in names]
        return "".join(f"package:{name}\n" for name in names)
    return home_guard_answer(command)
'''
MAIN = '\nif __name__ == "__main__":'


class DisableVerifyTest(unittest.TestCase):
    # The temporary repository, the TV's state file and the way each script is run are the Home guard
    # test's own; only tools/tv.py is replaced, by the same fake with the read-back answers above.
    home_guard_setup = home_guard.HomeGuardTest.setUp
    write_list = home_guard.HomeGuardTest.write_list
    tv = home_guard.HomeGuardTest.tv
    disable = home_guard.HomeGuardTest.disable
    entries = home_guard.HomeGuardTest.entries
    output = home_guard.HomeGuardTest.output

    def setUp(self):
        self.home_guard_setup()
        self.assertIn(MAIN, home_guard.FAKE_TV, "the Home guard test's fake changed shape")
        (self.tools / "tv.py").write_text(home_guard.FAKE_TV.replace(MAIN, READ_BACK + MAIN, 1))
        self.write_list([BEFORE, AFTER])

    def said(self, entry, result):
        """What the script says, with each run of whitespace as one space.

        disable.sh says it on stderr. PowerShell's warnings go to the host, which may wrap them, so both
        of its streams are read, joined the way tests/test_home_guard.py joins them."""
        return " ".join((result.stderr if entry == "sh" else self.output(result)).split())

    def report(self, entry, result):
        """What the scripts say from the still-enabled header on, or "" when there is none."""
        text = self.said(entry, result)
        return text[text.index(HEADER):] if HEADER in text else ""

    def test_a_list_the_tv_took_ends_with_status_0(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {BEFORE}", sent)
                self.assertNotIn(HEADER, self.said(entry, result))
                self.assertNotIn(COULD_NOT, self.said(entry, result), "the check has to have run")

    def test_a_package_the_tv_left_enabled_is_named_and_fails_the_run(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(pinned=[AFTER])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 1, self.output(result))
                report = self.report(entry, result)
                self.assertIn(AFTER, report)
                self.assertNotIn(BEFORE, report, "only the package the TV left on is named")
                self.assertIn("Do not run the list again in a loop", report)
                self.assertIn("Every way of disabling a package is refused", report)
                # The report changes nothing that is sent.
                self.assertEqual(sent, [f"pm disable-user --user 0 {BEFORE}",
                                        f"pm disable-user --user 0 {AFTER}", "pm list packages -d"])

    def test_a_tv_that_refuses_every_package_fails_the_run(self):
        # #577 and #1185: nothing changed, the case that must not read as a list that went through.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(pinned=[BEFORE, AFTER])
                result, _ = self.disable(entry)
                self.assertEqual(result.returncode, 1, self.output(result))
                report = self.report(entry, result)
                self.assertIn(BEFORE, report)
                self.assertIn(AFTER, report)

    def test_a_package_that_is_not_on_this_tv_is_not_reported(self):
        self.write_list([BEFORE, "com.example.missing", AFTER])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, _ = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(HEADER, self.said(entry, result))

    def test_a_package_the_factory_already_disabled_is_not_reported(self):
        # The Chromecast's tvrecommendations: disabled out of the box, and `pm disable-user` on it answers
        # "Shell cannot change component state". Refused, but already where the list wants it.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(disabled=[AFTER], pinned=[AFTER])
                result, _ = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(HEADER, self.said(entry, result))

    def test_a_package_another_user_keeps_enabled_is_not_reported(self):
        # The batch disables for user 0 only; a second profile's own copy is not this run's to judge.
        # Without --user 0, Android 14 lists it as enabled and the run would fail on a TV it fixed.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(other_user_enabled=[BEFORE, AFTER])
                result, _ = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(HEADER, self.said(entry, result))
                self.assertNotIn(COULD_NOT, self.said(entry, result), "the check has to have run")

    def test_a_package_a_guard_skipped_is_not_reported_again(self):
        # The Home guard keeps the only launcher and says so; it is not a package the TV refused.
        self.write_list([BEFORE, STOCK, AFTER])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertIn(f"Skipping {STOCK}", self.output(result))
                self.assertNotIn(HEADER, self.said(entry, result))

    def test_a_failed_read_says_the_check_did_not_run(self):
        # Partial output before the transport failed is not the enabled list: it would name BEFORE.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(pinned=[BEFORE, AFTER], enabled_fails=True,
                        failed_output=f"package:{BEFORE}\n")
                result, _ = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                said = self.said(entry, result)
                self.assertNotIn(HEADER, said)
                self.assertEqual(said.count(COULD_NOT), 1, self.output(result))
                self.assertIn("Compare 'pm list packages -d --user 0' with the list by hand", said)
                if entry == "sh":
                    lines = [line for line in result.stderr.splitlines() if COULD_NOT in line]
                    self.assertIn("pm list packages -d --user 0", lines[0],
                                  "one line, and it says how to check by hand")

    def test_an_answer_with_no_package_in_it_says_the_check_did_not_run(self):
        # A pm that does not know an option prints an error and lists nothing.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(pinned=[AFTER], enabled_answer="Error: Unknown option: --user\n")
                result, _ = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(HEADER, self.said(entry, result))
                self.assertIn(COULD_NOT, self.said(entry, result))

    def test_nothing_left_to_disable_reads_nothing_back(self):
        # Every listed package was skipped, so there is nothing to check, and no line saying it could not.
        self.write_list([STOCK])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(enabled_fails=True)
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertEqual(sent, ["pm list packages -d"])
                self.assertNotIn(COULD_NOT, self.said(entry, result))

    def test_disable_ps1_reads_and_says_what_disable_sh_does(self):
        # Without pwsh the cases above run disable.sh only, so pin the PowerShell twin's text here.
        sh = (ROOT / "tools" / "disable.sh").read_text(encoding="utf-8")
        ps1 = (ROOT / "tools" / "disable.ps1").read_text(encoding="utf-8")
        # What each script runs: the read, the line that fails the run, and the ADB warning before it.
        lines = {
            "disable.sh": ("\"$PY\" -I tools/tv.py 'pm list packages -e --user 0' 2>/dev/null",
                           '\n[ -z "$STILL_ON" ] || exit 1\n',
                           '\nif [ "$ADB_SECURE" = "0" ] || [ -z "$ADB_SECURE" ]; then\n'),
            "disable.ps1": ("& $py -I $tvPy 'pm list packages -e --user 0' 2>$null",
                            "\nif ($stillOn.Count -gt 0) { exit 1 }\n",
                            "\nif ($null -ne $adbSecure -and ($adbSecure -eq '0' -or $adbSecure -eq '')) {\n"),
        }
        for name, text in (("disable.sh", sh), ("disable.ps1", ps1)):
            read, fail, adb = lines[name]
            with self.subTest(script=name):
                self.assertIn(read, text, "the read asks for user 0, the user the batch disables for")
                self.assertGreater(text.index(read), text.index("--batch"), "the read comes after the batch")
                self.assertIn(fail, text)
                self.assertGreater(text.index(fail), text.index(adb),
                                   "the ADB warning is printed before a failed run ends")
                for words in (HEADER, COULD_NOT, "Do not run the list again in a loop",
                              "Every way of disabling a package is refused"):
                    self.assertIn(words, text)


if __name__ == "__main__":
    unittest.main()

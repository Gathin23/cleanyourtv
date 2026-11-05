"""The tools read package state for user 0, the user every one of their commands changes.

`pm disable-user --user 0` and `pm enable` act on user 0 only. Android 14's pm, asked without --user,
answers for every user on the TV and merges the answers (PackageManagerShellCommand.runListPackages
starts from UserHandle.USER_ALL; android11-release started from user 0). On a TV with a second user,
the plain `pm list packages -d` then lists a package that only the other user has disabled, and the
plain `pm list packages` lists one that is installed only for the other user. The Chromecast with
Google TV 4K (sabrina, Google TV 14, UTTC.250917.004) has such a user, a dormant profile with the id
10, and its merged -e and -d lists both name seven packages that are disabled for user 0 alone.

The fake TVs from the recovery record, drift check and privacy audit tests are reused here with a
second user added, answering the way Android 14 does, so the real tools run against the merged answers
and only the ADB transport is simulated.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import inspect
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import privacy  # noqa: E402
import recovery  # noqa: E402
# Imported as modules, not as classes, so their tests are not collected a second time from here.
import test_drift_check as drift_harness  # noqa: E402
import test_privacy_audit as privacy_harness  # noqa: E402
import test_recovery_record as record_harness  # noqa: E402


def list_packages_like_android_14(users, command):
    """What `pm list packages [-d|-e] [--user N]` prints on Android 14.

    `users` maps each user id (a string) to {package: "enabled" or "disabled"} for the packages that
    are installed for that user. Without --user every user is asked in turn, each answer is filtered
    by -d or -e on its own, and the union is printed with each line once, as runListPackages does.
    """
    words = command.split()
    ids = [words[words.index("--user") + 1]] if "--user" in words else list(users)
    lines = []
    for user in ids:
        for name, state in users.get(user, {}).items():
            if "-d" in words and state != "disabled":
                continue
            if "-e" in words and state != "enabled":
                continue
            if f"package:{name}" not in lines:
                lines.append(f"package:{name}")
    return "".join(f"{line}\n" for line in lines)


def with_two_users(fake):
    """One of the other modules' fake tools/tv.py, with its package queries answered from
    state["users"] the way Android 14 answers them. Every other query keeps that fake's answer."""
    head, guard, tail = fake.rpartition('\nif __name__ == "__main__":')
    if not guard:
        raise AssertionError("the fake TV no longer ends with its __main__ guard")
    override = inspect.getsource(list_packages_like_android_14) + '''

one_user_answer = answer


def answer(command):
    if command.startswith("pm list packages"):
        return list_packages_like_android_14(state["users"], command)
    return one_user_answer(command)

'''
    return f"{head}\n\n{override}{guard}{tail}"


def harness(case, test_class, fake):
    """Another module's fake-TV test case, set up for this test with a two-user TV and cleaned up
    with it, so its scratch repo and helpers are reused instead of copied."""
    other = test_class()
    other.setUp()
    case.addCleanup(other.doCleanups)
    (other.repo / "tools" / "tv.py").write_text(with_two_users(fake))
    return other


class TwoUserTV:
    """An adb_shell device for the tools' own functions, answering pm the way Android 14 does."""

    def __init__(self, users, takes_user=True):
        self.users = users
        self.takes_user = takes_user
        self.commands = []

    def shell(self, command, read_timeout_s=None):
        self.commands.append(command)
        if command.startswith("pm list packages"):
            if "--user" in command and not self.takes_user:
                return "Error: Unknown option: --user\n"
            return list_packages_like_android_14(self.users, command)
        return ""


# Stand-in names. off_for_owner is the Chromecast's seven: disabled for user 0, still enabled for
# user 10, which the merged -d happens to get right. on_for_owner is the case that breaks undo and
# that TV does not have: only user 10 has it disabled. profile_only is installed for user 10 alone;
# the Chromecast's cast2class is hidden for user 0, which pm lists the same way.
USERS = {
    "0": {"com.example.on_for_owner": "enabled",
          "com.example.off_for_owner": "disabled",
          "com.google.android.tvlauncher": "enabled"},
    "10": {"com.example.on_for_owner": "disabled",
           "com.example.off_for_owner": "enabled",
           "com.google.android.tvlauncher": "enabled",
           "com.example.profile_only": "enabled"},
}


class QueryPackagesTest(unittest.TestCase):
    """recovery.query_packages and privacy.query_packages against the merged answers."""

    def both(self, dev):
        return {"recovery": recovery.query_packages(dev), "privacy": privacy.query_packages(dev)}

    def test_state_is_user_0s_on_a_two_user_tv(self):
        for tool, (installed, disabled) in self.both(TwoUserTV(USERS)).items():
            with self.subTest(tool=tool):
                self.assertEqual(set(installed), {"com.example.on_for_owner",
                                                  "com.example.off_for_owner",
                                                  "com.google.android.tvlauncher"})
                self.assertEqual(disabled, {"com.example.off_for_owner"})

    def test_an_empty_disabled_list_is_not_a_reason_to_fall_back(self):
        # The first run on a TV nobody has cleaned yet: nothing is disabled for user 0, and the
        # merged -d would hand the other user's disabled package to the record as the owner's.
        users = {"0": {"com.example.on_for_owner": "enabled"},
                 "10": {"com.example.on_for_owner": "disabled"}}
        for tool, query in (("recovery", recovery.query_packages), ("privacy", privacy.query_packages)):
            with self.subTest(tool=tool):
                dev = TwoUserTV(users)
                installed, disabled = query(dev)
                self.assertEqual(set(installed), {"com.example.on_for_owner"})
                self.assertEqual(disabled, set())
                self.assertEqual(dev.commands, ["pm list packages --user 0",
                                                "pm list packages -d --user 0"])

    def test_a_pm_without_the_user_option_falls_back_to_the_plain_queries(self):
        users = {"0": {"com.example.on_for_owner": "enabled",
                       "com.example.off_for_owner": "disabled"}}
        for tool, query in (("recovery", recovery.query_packages), ("privacy", privacy.query_packages)):
            with self.subTest(tool=tool):
                dev = TwoUserTV(users, takes_user=False)
                installed, disabled = query(dev)
                self.assertEqual(set(installed), {"com.example.on_for_owner",
                                                  "com.example.off_for_owner"})
                self.assertEqual(disabled, {"com.example.off_for_owner"})
                self.assertEqual(dev.commands, ["pm list packages --user 0", "pm list packages",
                                                "pm list packages -d"])

    def test_a_tv_that_returns_nothing_is_still_an_error(self):
        for tool, query in (("recovery", recovery.query_packages), ("privacy", privacy.query_packages)):
            with self.subTest(tool=tool):
                with self.assertRaises(SystemExit) as stop:
                    query(TwoUserTV({}))
                self.assertIn("returned no packages", str(stop.exception))


class RecordOnTwoUsersTest(unittest.TestCase):
    """disable.sh and restore.sh, through the recovery record, on a TV with a second user."""

    def setUp(self):
        self.h = harness(self, record_harness.RecoveryRecordTest, record_harness.FAKE_TV)
        self.h.write_list(["com.example.on_for_owner", "com.example.off_for_owner",
                           "com.example.profile_only"])
        self.h.tv(users=USERS)

    def test_record_holds_user_0s_state(self):
        result = self.h.run_script("disable.sh", str(self.h.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.h.record()["packages"], {
            # Disabled only for the other user: on for the owner, so undo turns it back on.
            "com.example.on_for_owner": "enabled",
            # The Chromecast's seven: off for user 0 and still on for user 10.
            "com.example.off_for_owner": "disabled",
            # Installed only for the other user: there is nothing for user 0 to enable.
            "com.example.profile_only": "absent",
            "com.google.android.tvlauncher": "enabled",
        })

    def test_restore_turns_back_on_what_the_owner_had_on(self):
        self.assertEqual(self.h.run_script("disable.sh", str(self.h.list_path)).returncode, 0)
        self.h.clear_sent()
        result = self.h.run_script("restore.sh", str(self.h.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.h.sent()
        self.assertIn("pm enable com.example.on_for_owner", sent,
                      "a package only the other user had disabled is not left off for good")
        self.assertNotIn("pm enable com.example.off_for_owner", sent)
        self.assertNotIn("pm enable com.example.profile_only", sent)


class DriftOnTwoUsersTest(unittest.TestCase):
    """check.py's report on a TV with a second user."""

    def setUp(self):
        self.h = harness(self, drift_harness.DriftCheckTest, drift_harness.FAKE_TV)

    def users(self, owner, other):
        base = {"com.example.store": "disabled", drift_harness.STOCK: "disabled",
                drift_harness.CUSTOM: "enabled"}
        rest = {"com.example.store": "enabled", drift_harness.STOCK: "enabled",
                drift_harness.CUSTOM: "enabled"}
        return {"0": dict(base, **owner), "10": dict(rest, **other)}

    def test_on_for_user_0_is_drift_even_when_the_other_user_has_it_off(self):
        self.h.tv(users=self.users({"com.example.music": "enabled"},
                                   {"com.example.music": "disabled"}))
        result = self.h.run_check(str(self.h.list_path))
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("back on (1): com.example.music", result.stdout)

    def test_gone_for_user_0_is_not_drift_while_the_other_user_keeps_it(self):
        # Removed for user 0 only (`pm uninstall -k --user 0`, which other guides use, or hidden like
        # the Chromecast's cast2class): there is nothing to disable, so no drift and no failing --fix.
        self.h.tv(users=self.users({}, {"com.example.music": "enabled"}))
        result = self.h.run_check(str(self.h.list_path))
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("not installed any more (1): com.example.music", result.stdout)
        self.assertNotIn("back on", result.stdout)


class PrivacyOnTwoUsersTest(unittest.TestCase):
    """privacy.py's report and --write on a TV with a second user."""

    def setUp(self):
        self.h = harness(self, privacy_harness.PrivacyAuditTest, privacy_harness.FAKE_TV)
        names = ["com.google.android.katniss", "tv.inscape.tvclient.inscapeacr",
                 "org.droidtv.usagelogger", "com.example.voicecontrol", "com.example.plain"]
        owner = {name: "enabled" for name in names}
        other = dict(owner, **{"org.droidtv.usagelogger": "disabled"})
        self.h.tv(users={"0": owner, "10": other})

    def test_report_shows_user_0s_state(self):
        out = self.h.run_script().stdout
        self.assertIn("5 packages, 5 enabled", out)
        self.assertIn("enabled   org.droidtv.usagelogger", out)

    def test_write_keeps_a_package_only_the_other_user_has_off(self):
        target, result = self.h.write()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("org.droidtv.usagelogger", target.read_text())


if __name__ == "__main__":
    unittest.main()

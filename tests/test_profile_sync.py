"""`profile.py sync` converges the target unit to the master: same enabled and disabled state, same
third-party apps. It never uninstalls, and it never touches a package the profile does not name.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from profile_fake import Harness, MASTER, TARGET, tv_state  # noqa: E402


class SyncTest(unittest.TestCase):
    def setUp(self):
        self.h = Harness(self)
        self.h.profile.mkdir(parents=True)
        (self.h.profile / "apps.txt").write_text(
            "# apps\ncom.example.brave\ncom.example.tube\n")
        (self.h.profile / "debloat.txt").write_text(
            "# disabled\ncom.google.android.feedback\n")
        self.h.put(MASTER, tv_state(
            installed=["com.example.brave", "com.example.tube", "com.google.android.feedback",
                       "com.google.android.tvlauncher", "com.gathin.tvhome"],
            third_party=["com.example.brave", "com.example.tube"],
            disabled=["com.google.android.feedback", "com.google.android.tvlauncher"],
            paths={"com.example.brave": ["/data/app/brave/base.apk"],
                   "com.example.tube": ["/data/app/tube/base.apk"]}))
        self.h.put(TARGET, tv_state(
            installed=["com.example.brave", "com.google.android.feedback", "com.example.other",
                       "com.gathin.tvhome"],
            third_party=["com.example.brave", "com.example.other"],
            disabled=["com.google.android.feedback"],
            paths={"com.example.brave": ["/data/app/brave/base.apk"],
                   "com.example.other": ["/data/app/other/base.apk"]}))

    def test_dry_run_prints_the_plan_and_changes_nothing(self):
        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, host=TARGET)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("install from the master (1): com.example.tube", result.stdout)
        self.assertNotIn("pm disable-user", result.stdout)
        self.assertEqual(self.h.commands(TARGET), [])
        self.assertNotIn("com.example.tube", self.h.state(TARGET)["installed"])

    def test_apply_installs_the_missing_app_from_the_master(self):
        master = self.h.state(MASTER)
        master["files"]["/data/app/tube/base.apk"] = "APK"
        self.h.put(MASTER, master)
        target = self.h.state(TARGET)
        target["apks"]["/data/local/tmp/com.example.tube.apk"] = "com.example.tube"
        self.h.put(TARGET, target)

        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, "--apply", host=TARGET)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("com.example.tube", self.h.state(TARGET)["installed"])
        self.assertTrue(any(c.startswith("pm install -r") for c in self.h.commands(TARGET)))

    def test_apply_mirrors_the_disabled_state_both_ways(self):
        master = self.h.state(MASTER)
        master["disabled"] = ["com.google.android.feedback"]
        self.h.put(MASTER, master)
        target = self.h.state(TARGET)
        target["disabled"] = []
        self.h.put(TARGET, target)

        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, "--apply", host=TARGET)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("pm disable-user --user 0 com.google.android.feedback",
                      self.h.commands(TARGET))

    def test_a_package_only_on_the_target_is_reported_and_left_alone(self):
        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, host=TARGET)
        self.assertEqual(result.returncode, 1)
        self.assertIn("only on this unit (1): com.example.other", result.stdout)
        self.h.run("sync", str(self.h.profile), "--source", MASTER, "--apply", host=TARGET)
        self.assertNotIn("com.example.other", self.h.state(TARGET)["disabled"])
        self.assertIn("com.example.other", self.h.state(TARGET)["installed"])

    def test_nothing_is_ever_uninstalled(self):
        self.h.run("sync", str(self.h.profile), "--source", MASTER, "--apply", host=TARGET)
        for command in self.h.commands(TARGET) + self.h.commands(MASTER):
            self.assertNotIn("uninstall", command)
            self.assertNotIn("pm hide", command)

    def test_a_split_package_is_installed_in_one_session(self):
        master = self.h.state(MASTER)
        master["paths"]["com.example.tube"] = [
            "/data/app/tube/base.apk", "/data/app/tube/split_config.arm64_v8a.apk"]
        master["files"]["/data/app/tube/base.apk"] = "BASE"
        master["files"]["/data/app/tube/split_config.arm64_v8a.apk"] = "SPLIT"
        self.h.put(MASTER, master)
        target = self.h.state(TARGET)
        for index in (1, 2):
            target["apks"][f"/data/local/tmp/com.example.tube-{index}.apk"] = "com.example.tube"
        self.h.put(TARGET, target)

        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, "--apply", host=TARGET)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("com.example.tube", self.h.state(TARGET)["installed"])
        commands = self.h.commands(TARGET)
        self.assertTrue(any(c.startswith("pm install-create") for c in commands))
        self.assertEqual(2, sum(1 for c in commands if c.startswith("pm install-write ")))
        self.assertTrue(any(c.startswith("pm install-commit") for c in commands))
        self.assertNotIn("install by hand", result.stdout)

    def test_a_failed_commit_abandons_the_session_instead_of_leaving_it_open(self):
        master = self.h.state(MASTER)
        master["paths"]["com.example.tube"] = [
            "/data/app/tube/base.apk", "/data/app/tube/split_config.arm64_v8a.apk"]
        master["files"]["/data/app/tube/base.apk"] = "BASE"
        master["files"]["/data/app/tube/split_config.arm64_v8a.apk"] = "SPLIT"
        self.h.put(MASTER, master)
        target = self.h.state(TARGET)
        target["commit_fails"] = True
        for index in (1, 2):
            target["apks"][f"/data/local/tmp/com.example.tube-{index}.apk"] = "com.example.tube"
        self.h.put(TARGET, target)

        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, "--apply", host=TARGET)

        self.assertEqual(result.returncode, 0, result.stderr)
        commands = self.h.commands(TARGET)
        self.assertTrue(any(c.startswith("pm install-abandon ") for c in commands))
        self.assertIn("install by hand (1): com.example.tube", result.stdout)
        self.assertNotIn("com.example.tube", self.h.state(TARGET)["installed"])

    def test_a_package_with_no_apk_on_the_master_is_reported(self):
        master = self.h.state(MASTER)
        master["paths"]["com.example.tube"] = []
        self.h.put(MASTER, master)

        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, "--apply", host=TARGET)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("install by hand (1): com.example.tube", result.stdout)
        self.assertFalse(any("install" in c and "tube" in c for c in self.h.commands(TARGET)))

    def test_an_unreachable_source_exits_2(self):
        (self.h.states / (MASTER.replace(":", "_") + ".json")).unlink()
        result = self.h.run("sync", str(self.h.profile), "--source", MASTER, host=TARGET)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(self.h.commands(TARGET), [])


if __name__ == "__main__":
    unittest.main()

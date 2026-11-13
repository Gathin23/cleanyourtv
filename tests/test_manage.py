"""tools/manage.py reads the add-ons, themes and switches from the repo's own READMEs; check it finds them all, and
that every add-on has a way in: its own install script or the steps in addons/README.md."""
import contextlib
import io
import os
import subprocess
import sys
import unittest
from unittest import mock
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


class ManageTest(unittest.TestCase):
    def test_every_addon_folder_is_listed_with_its_package(self):
        listed = {a["id"]: a for a in manage.addons()}
        folders = {p.name for p in (ROOT / "addons").iterdir() if p.is_dir()}
        self.assertEqual(folders, set(listed), "addons/README.md rows and addons/ folders")
        for a in listed.values():
            with self.subTest(addon=a["id"]):
                self.assertTrue(a["package"], "applicationId found in app/build.gradle(.kts)")
                if a["script"]:
                    self.assertTrue((ROOT / "addons" / a["id"] / a["script"]).is_file())

    def test_every_theme_is_listed_with_its_screenshot(self):
        for t in manage.themes():
            with self.subTest(theme=t["id"]):
                self.assertTrue((ROOT / "themes" / t["id"] / "config.json").is_file())
                self.assertTrue(t["screenshot"] and Path(t["screenshot"]).is_file())

    def test_switches_are_the_documented_keys(self):
        keys = {s["id"] for s in manage.switches()}
        self.assertIn("columns", keys)
        self.assertIn("dock_apps", keys)
        self.assertGreater(len(keys), 40)

    def test_an_application_id_that_is_not_a_package_name_is_not_used(self):
        # `manage.py remove` sends `pm uninstall <id>` to the TV's shell, so an id carrying shell words
        # must read as no package at all rather than run on the TV.
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            app = Path(tmp) / "app"
            app.mkdir()
            gradle = app / "build.gradle"
            gradle.write_text("android { defaultConfig { applicationId 'x.y; settings put global adb_enabled 0 #' } }\n")
            self.assertIsNone(manage.package(Path(tmp)))
            gradle.write_text("android { defaultConfig { applicationId 'com.example.addon' } }\n")
            self.assertEqual("com.example.addon", manage.package(Path(tmp)))

    def test_find_by_folder_or_name(self):
        self.assertEqual("weather", manage.find("Weather")["id"])
        self.assertEqual("theme", manage.find("aurora/")["kind"])
        self.assertEqual("switch", manage.find("columns")["kind"])
        self.assertIsNone(manage.find("no-such-thing"))

    def test_set_refuses_the_projects_own_themes(self):
        with self.assertRaises(SystemExit):
            manage.cmd_set("columns", "6", str(ROOT / "themes" / "aurora"))


    def status(self, *answers):
        """Runs `status` with tv.py's answers (stdout, returncode, stderr) in order; returns what it printed."""
        replies = iter(subprocess.CompletedProcess([], code, out, err) for out, code, err in answers)
        out = io.StringIO()
        with mock.patch.dict(os.environ, {"TV_HOST": "192.0.2.1"}), \
                mock.patch.object(manage, "tv_python", return_value="python3"), \
                mock.patch.object(manage.subprocess, "run", side_effect=lambda *a, **k: next(replies)), \
                contextlib.redirect_stdout(out):
            code = manage.main(["status"])
        return code, out.getvalue()

    def test_status_stops_when_the_tv_does_not_answer(self):
        """The exit status is tv.py's - 2 for a TV it never reached, 1 for a command that failed - and the words
        it printed go to stderr, where a caller reads them."""
        for answers, code, message in [
            ([("", 1, "failed to connect")], 1, "failed to connect"),
            ([("package:com.a\n", 1, "")], 1, "package:com.a"),
            ([("", 255, "")], 1, "tv.py exited with status 255"),
            # `status` asks which package answers HOME before it reads that launcher's config.json; a firmware
            # that cannot answer that query is not a failure (the default id is used), but a failed read is.
            ([("", 0, ""), ("", 0, ""), ("", 1, "unauthorized")], 1, "unauthorized"),
            ([("", 2, "127.0.0.1:1 refused the connection")], 2, "refused the connection"),
        ]:
            with self.subTest(code=code, message=message):
                err = io.StringIO()
                with contextlib.redirect_stderr(err), self.assertRaises(SystemExit) as stop:
                    self.status(*answers)
                self.assertEqual(stop.exception.code, code)
                self.assertIn(message, err.getvalue())

    def test_status_reads_an_empty_tv(self):
        code, out = self.status(("", 0, ""), ("", 0, ""), ("", 0, ""))
        self.assertEqual(code, 0)
        self.assertIn("launcher: not installed", out)
        self.assertIn("theme: none", out)

    def test_status_finds_the_launcher_and_a_theme(self):
        theme = ROOT / "themes" / manage.themes()[0]["id"] / "config.json"
        code, out = self.status(("package:" + manage.LAUNCHER_PKG + "\n", 0, ""), ("", 0, ""),
                                (theme.read_text(encoding="utf-8"), 0, ""))
        self.assertEqual(code, 0)
        self.assertIn("launcher: installed", out)
        self.assertIn("theme: " + manage.themes()[0]["id"], out)

if __name__ == "__main__":
    unittest.main()

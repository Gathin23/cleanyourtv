"""theme.sh and theme.ps1 check that each pushed file arrived, and a push that stops part-way exits 1.

put() deletes the file on the TV, writes it through the launcher's ThemeProvider and falls back to cp. On a set
where both are refused (Android 8 and 9 have no `content write`; some firmware locks the shell out of
Android/data) nothing was written, and the run still ended "is on the TV" with 0. put() now looks for the file
afterwards, by the shell or through the provider, and prints `cyt-theme-not-written <name>` when it is not
there; both scripts stop on that line with 1.

theme.sh also passed a later step's own status out under `set -e`: a TV that stopped answering after
config.json was written made tv.py or b64push.py exit 2, the status the header keeps for a run that sent
nothing. Every step from the first put on now ends the run with 1 and names the file it stopped at, as
restart_home already does. theme.ps1 throws there, which already gives 1.

The harness is tests/test_theme_push_ownership.py's: a fake tv.py whose shell is /bin/sh on this machine and a
stub `content`. The theme.sh cases need bash, the theme.ps1 case also `pwsh`; each skips without them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_theme_push_ownership as push  # noqa: E402  (its harness, not its tests)

PWSH = shutil.which("pwsh")


class ThemeVerifyPushTest(unittest.TestCase):
    run_theme = push.ThemePushTest.run_theme

    def setUp(self):
        push.ThemePushTest.setUp(self)
        # The harness's client drops what the shell answers; the real tv.py prints it, and the check reads it.
        (self.tools / "tv.py").write_text(push.FAKE_TV.replace('    Device().shell(" ".join(args))',
                                                               '    sys.stdout.write(Device().shell(" ".join(args)))'))
        # cp into the TV's Android/data refused, as on firmware that locks the shell out of it.
        cp = self.bin / "cp"
        cp.write_text('#!/bin/sh\nif [ -n "$FAKE_NO_CP" ]; then exit 1; fi\nexec /bin/cp "$@"\n')
        cp.chmod(0o755)
        # rm refused inside the TV's Android/data, as when the shell may not delete a file the launcher owns.
        rm = self.bin / "rm"
        rm.write_text('#!/bin/sh\nif [ -n "$FAKE_NO_RM" ]; then case "$*" in *"$FAKE_DIR"*) exit 1 ;; esac; fi\n'
                      'exec /bin/rm "$@"\n')
        rm.chmod(0o755)

    def test_a_file_both_writes_refused_fails_the_push(self):
        result = self.run_theme(str(self.theme), FAKE_NO_CONTENT_WRITE="1", FAKE_NO_CP="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("neither the provider write nor the copy of config.json", result.stderr)
        self.assertNotIn("is on the TV", result.stdout)

    def test_a_push_that_took_still_exits_0(self):
        result = self.run_theme(str(self.theme), FAKE_NO_CONTENT_WRITE="1")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("is on the TV", result.stdout)

    def test_a_tv_lost_after_config_json_is_1_not_2(self):
        (self.theme / "wallpaper.jpg").write_bytes(b"\xff\xd8\xff")
        # b64push.py's own status for a TV it could not reach: 2, "Nothing was sent to the TV".
        (self.tools / "b64push.py").write_text("import sys\nsys.exit(2)\n")
        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("stopped at wallpaper.jpg", result.stderr)

    def test_an_empty_file_that_was_written_is_not_a_failure(self):
        (self.theme / "wallpapers").mkdir()
        (self.theme / "wallpapers" / "captions.txt").write_bytes(b"")
        for name, env in (("provider", {}), ("cp", {"FAKE_NO_CONTENT_WRITE": "1"})):
            with self.subTest(written_by=name):
                result = self.run_theme(str(self.theme), **env)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual((self.device / "wallpapers" / "captions.txt").read_bytes(), b"")

    def test_an_old_file_that_survived_a_refused_removal_is_not_the_new_one(self):
        (self.device / "config.json").write_bytes(b"old")
        result = self.run_theme(str(self.theme), FAKE_NO_CONTENT_WRITE="1", FAKE_NO_CP="1", FAKE_NO_RM="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("neither the provider write nor the copy of config.json", result.stderr)

    def test_an_old_file_of_the_same_size_is_not_the_new_one(self):
        # Same length as the new config.json, so only knowing that a write went through tells them apart.
        old_config = bytes(b ^ 0x20 for b in (self.theme / "config.json").read_bytes())
        (self.device / "config.json").write_bytes(old_config)
        (self.device / "theme_revision").write_bytes(b"0" * 32)
        (self.device / "wallpaper.jpg").write_bytes(b"\xff\xd8\xffold")
        result = self.run_theme(str(self.theme), FAKE_NO_CONTENT_WRITE="1", FAKE_NO_CP="1", FAKE_NO_RM="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("neither the provider write nor the copy of config.json", result.stderr)
        self.assertNotIn("is on the TV", result.stdout)
        # It stops at config.json: the steps after it (the wallpaper removal, theme_revision) never run.
        self.assertEqual((self.device / "config.json").read_bytes(), old_config)
        self.assertEqual((self.device / "theme_revision").read_bytes(), b"0" * 32)
        self.assertTrue((self.device / "wallpaper.jpg").exists())

    def test_a_reset_that_could_not_write_theme_revision_exits_1(self):
        result = self.run_theme("--reset", FAKE_NO_CONTENT_WRITE="1", FAKE_NO_CP="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("neither the provider write nor the copy of theme_revision", result.stderr)
        self.assertNotIn("reset", result.stdout)

    def test_theme_ps1_fails_the_push_too(self):
        if not PWSH or os.name != "posix":
            self.skipTest("theme.ps1 needs pwsh, and this fake TV's shell is /bin/sh")
        shutil.copy2(push.TOOLS / "theme.ps1", self.tools / "theme.ps1")
        client = self.tools / "venv" / "Scripts" / "python.exe"
        client.parent.mkdir(parents=True, exist_ok=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update({"FAKE_LOG": str(self.log), "FAKE_DIR": str(self.device), "FAKE_STAGE": str(self.stage),
                    "FAKE_CONTENT_LOG": str(self.content_log), "FAKE_SHELL_DIRS": str(self.shell_dirs),
                    "PATH": f"{self.bin}:{env.get('PATH', '')}", "FAKE_NO_CONTENT_WRITE": "1", "FAKE_NO_CP": "1"})
        result = subprocess.run([PWSH, "-NoProfile", "-File", str(self.tools / "theme.ps1"), "-TvHost", "192.0.2.10",
                                 str(self.theme)], env=env, capture_output=True, text=True, timeout=300)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("neither the provider write nor the copy of config.json", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()

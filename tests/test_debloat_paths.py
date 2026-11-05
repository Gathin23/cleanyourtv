"""Exercise the shell helpers without connecting to a TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class DebloatPathsTest(unittest.TestCase):
    def test_list_paths(self):
        for script in ("disable.sh", "restore.sh"):
            for path_kind in ("caller-relative", "absolute", "repo-relative"):
                with self.subTest(script=script, path_kind=path_kind):
                    self.check_list_path(script, path_kind)

    def test_injected_command_is_refused(self):
        # A contributor-supplied list must not be able to add shell syntax (#165).
        for script in ("disable.sh", "restore.sh"):
            with self.subTest(script=script):
                self.check_injected_line(script)

    def test_windows_bom_and_crlf_are_normalised(self):
        # Notepad saves a BOM and CRLF; the list must still yield clean package names (#208).
        for script in ("disable.sh", "restore.sh"):
            with self.subTest(script=script):
                self.check_windows_list(script)

    def prepare(self, repo, script):
        """Copy the script under test and stand in for tools/venv/bin/python.

        Both scripts ask tools/recovery.py first: disable.sh for the record of what it is about to
        change, restore.sh for the batch that replays it. Taking a record needs a TV, so the fake
        accepts --save as a no-op; building the batch from a record does not, so --commands goes to
        the real recovery.py and the test sees exactly what would be sent (#239).
        """
        tools = repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / script, tools / script)
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        client.write_text(
            '#!/bin/sh\n'
            'if [ "$1" = "-I" ] && [ "$2" = "tools/recovery.py" ]; then\n'
            '  [ "$3" = "--save" ] && exit 0\n'
            '  shift 2\n'
            f'  exec "{sys.executable}" "{ROOT / "tools" / "recovery.py"}" "$@"\n'
            'fi\n'
            # The read-back after the batch (#1376) answers nothing, so CAPTURE keeps the batch.
            'case "$*" in *"pm list packages -e"*) exit 0 ;; esac\n'
            'cat > "$CAPTURE"\n'
        )
        client.chmod(0o755)
        home = repo.parent / "home"
        home.mkdir(exist_ok=True)
        return tools, home

    def run_script(self, script_path, argument, cwd, base, home):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("disable.sh and restore.sh are POSIX scripts; they need bash")
        capture = base / "commands.txt"
        # No TV_HOST, so recovery.py looks for a record that cannot exist: that is the fallback these
        # tests exercise. HOME is isolated so a record from a real run cannot answer instead.
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update(CAPTURE=str(capture), HOME=str(home))
        # The fake tv.py reads its stdin, so an inherited terminal would leave it waiting.
        result = subprocess.run(
            ["bash", str(script_path), argument], cwd=cwd, env=env,
            capture_output=True, text=True, stdin=subprocess.DEVNULL,
        )
        return result, capture

    def check_windows_list(self, script):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools, home = self.prepare(base / "repo", script)
            package_list = base / "debloat.txt"
            package_list.write_bytes(
                b"\xef\xbb\xbf# My TV, saved in Notepad\r\n"
                b"com.example.one\r\n"
                b"com.example.two   # a note\r\n"
            )
            result, capture = self.run_script(tools / script, str(package_list), base, base, home)
            self.assertEqual(result.returncode, 0, result.stderr)
            sent = capture.read_bytes()
            self.assertNotIn(b"\xef\xbb\xbf", sent, "BOM reached the TV")
            self.assertNotIn(b"\r", sent, "CR reached the TV")
            operation = "pm disable-user --user 0" if script == "disable.sh" else "pm enable"
            self.assertEqual(sent.decode().splitlines()[:2], [
                f"{operation} com.example.one",
                f"{operation} com.example.two",
            ])

    def check_injected_line(self, script):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools, home = self.prepare(base / "repo", script)
            package_list = base / "debloat.txt"
            package_list.write_text(
                "# My TV\ncom.example.good\ncom.example.evil;settings put global adb_enabled 0\n"
            )
            result, capture = self.run_script(tools / script, str(package_list), base, base, home)
            self.assertNotEqual(result.returncode, 0, "the list should have been refused")
            self.assertIn("not a package name", result.stderr)
            sent = capture.read_text() if capture.exists() else ""
            self.assertNotIn("settings put", sent)
            self.assertNotIn("com.example.good", sent)

    def check_list_path(self, script, path_kind):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            repo = base / "repo checkout"
            tools, home = self.prepare(repo, script)

            caller = base / "caller folder"
            caller.mkdir()
            cwd = repo if path_kind == "repo-relative" else caller
            relative = Path("my TV lists") / "debloat.txt"
            package_list = cwd / relative
            package_list.parent.mkdir()
            package_list.write_text("# My TV\ncom.example.keep_order_one # note\n\ncom.example.keep_order_two\n")
            if cwd != repo:
                # An incorrect resolution must not silently operate on this other list.
                decoy = repo / relative
                decoy.parent.mkdir()
                decoy.write_text("com.example.wrong_tv\n")
            argument = str(package_list) if path_kind == "absolute" else str(relative)
            result, capture = self.run_script(tools / script, argument, cwd, base, home)
            self.assertEqual(result.returncode, 0, result.stderr)
            commands = capture.read_text().splitlines()
            operation = "pm disable-user --user 0" if script == "disable.sh" else "pm enable"
            self.assertEqual(commands[:2], [
                f"{operation} com.example.keep_order_one",
                f"{operation} com.example.keep_order_two",
            ])
            self.assertNotIn("wrong_tv", "\n".join(commands))
            self.assertEqual(commands[-1], "pm list packages -d")
            if script == "restore.sh":
                self.assertIn("pm enable com.google.android.tvlauncher", commands)


if __name__ == "__main__":
    unittest.main()

"""tools/theme.sh finds the theme folder however it is given, without connecting to a TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ThemePathsTest(unittest.TestCase):
    def test_theme_paths(self):
        for path_kind in ("caller-relative", "absolute"):
            with self.subTest(path_kind=path_kind):
                self.check_theme_path(path_kind)

    def test_a_folder_with_a_space_reaches_python_as_one_argument(self):
        """A theme in "my themes/living room": the stand-in has to hand the path over whole.

        The readable call log joins the words with spaces, so reading the path out of it cut this one at
        the space and answered "/.../my" for a folder theme.sh had passed on correctly.
        """
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo")
            theme = base / "my themes" / "living room"
            theme.mkdir(parents=True)
            config = theme / "config.json"
            config.write_text(json.dumps({"columns": 4}))
            result, capture = self.run_theme(tools, self.bash_path(theme), cwd=base)
            self.assertEqual(result.returncode, 0, result.stderr)
            pushed = self.pushed_argument(capture)
            self.assertEqual(Path(pushed).resolve(), config.resolve(), pushed)
            self.assertEqual(Path(f"{capture}.push").read_bytes(), config.read_bytes())

    def test_missing_folder_names_the_path_given(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo")
            missing = self.bash_path(base) + "/no-such-theme"
            result, _ = self.run_theme(tools, missing, cwd=base)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(f"no config.json in {missing}", result.stderr)
            self.assertNotIn("not valid JSON", result.stderr)

    def test_utf8_config_is_pushed_unchanged(self):
        # Config.read accepts UTF-8 with an optional BOM, including files saved by Windows editors.
        text = json.dumps({"columns": 6, "greeting_name": "Trịnh"}, ensure_ascii=False) + "\n"
        for encoding in ("utf-8", "utf-8-sig"):
            for newline in ("\n", "\r\n"):
                with self.subTest(encoding=encoding, newline=newline), tempfile.TemporaryDirectory() as tmp:
                    base = Path(tmp)
                    tools = self.prepare(base / "repo")
                    theme = base / "mytheme"
                    theme.mkdir()
                    config = theme / "config.json"
                    data = text.replace("\n", newline).encode(encoding)
                    config.write_bytes(data)
                    result, capture = self.run_theme(tools, self.bash_path(theme), cwd=base)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(Path(self.pushed_argument(capture)).resolve(), config.resolve())
                    self.assertEqual(Path(str(capture) + ".push").read_bytes(), data)
                    self.assertEqual(config.read_bytes(), data)

    def test_malformed_config_is_refused_before_transfer(self):
        for encoding in ("utf-8", "utf-8-sig"):
            with self.subTest(encoding=encoding), tempfile.TemporaryDirectory() as tmp:
                base = Path(tmp)
                tools = self.prepare(base / "repo")
                theme = base / "mytheme"
                theme.mkdir()
                (theme / "config.json").write_bytes('{"greeting_name":"Trịnh",'.encode(encoding))
                result, capture = self.run_theme(tools, self.bash_path(theme), cwd=base)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("not valid JSON", result.stderr)
                self.assertFalse(capture.exists(), "invalid JSON must not reach the TV transport")
                self.assertFalse(Path(f"{capture}.argv").exists(), "nothing may be recorded as sent")

    def prepare(self, repo):
        """Copy theme.sh and stand in for tools/venv/bin/python.

        The JSON check and the revision marker are plain `-c` snippets, so they go to the real Python
        and the check is the one theme.sh really runs. tv.py and b64push.py would need a TV: the fake
        records their arguments instead.

        It records two files, because the readable one joins the words with spaces and a theme folder
        can hold one ("my themes/living room", or a temp dir under a folder with a space): `$CAPTURE`
        is the line a person reads, and `$CAPTURE.argv` is one JSON array per call, which is what a
        test takes a path out of.
        """
        tools = repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "theme.sh", tools / "theme.sh")
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        python = Path(sys.executable).as_posix()
        record = 'import json,sys;open(sys.argv[1],"a").write(json.dumps(sys.argv[2:])+"\\n")'
        client.write_text(
            '#!/bin/sh\n'
            'if [ "$1" = "-I" ] && [ "$2" = "-c" ]; then\n'
            f'  exec "{python}" "$@"\n'
            'fi\n'
            'shift\n'
            'if [ "$1" = "tv.py" ] && [ "$2" = "--push" ]; then cp "$3" "$CAPTURE.push"; fi\n'
            'printf "%s\\n" "$*" >> "$CAPTURE"\n'
            f'"{python}" -c \'{record}\' "$CAPTURE.argv" "$@"\n'
            'echo applied\n'
        )
        client.chmod(0o755)
        return tools

    def pushed_argument(self, capture):
        """The path handed to `tv.py --push`, as the one word it was: `$*` cannot be split back."""
        calls = [json.loads(line) for line in Path(f"{capture}.argv").read_text().splitlines()]
        push = next(call for call in calls if call[:2] == ["tv.py", "--push"])
        return push[2]

    def bash_path(self, path):
        """The path as bash spells it: /tmp/... on Linux and macOS, /c/Users/... under Git Bash."""
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        out = subprocess.run(["bash", "-c", 'cd "$1" && pwd', "_", str(path)],
                             capture_output=True, text=True, check=True)
        return out.stdout.strip()

    def run_theme(self, tools, argument, cwd):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        capture = Path(cwd) / "calls.txt"
        env = dict(os.environ, TV_HOST="192.0.2.1", CAPTURE=str(capture))
        result = subprocess.run(["bash", str(tools / "theme.sh"), argument], cwd=cwd, env=env,
                                capture_output=True, text=True, stdin=subprocess.DEVNULL)
        return result, capture

    def check_theme_path(self, path_kind):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo")
            theme = base / "caller" / "mytheme"
            theme.mkdir(parents=True)
            (theme / "config.json").write_text(json.dumps({"columns": 5}))
            if path_kind == "absolute":
                argument = self.bash_path(theme)
            else:
                argument = "mytheme"
            result, capture = self.run_theme(tools, argument, cwd=base / "caller")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn("not valid JSON", result.stderr)
            pushed = self.pushed_argument(capture)
            self.assertTrue(pushed.endswith("mytheme/config.json"), pushed)
            # The path handed to Python must be one Python can open (C:/... rather than /c/... on Windows).
            self.assertTrue(Path(pushed).is_file(), f"Python cannot open {pushed}")


if __name__ == "__main__":
    unittest.main()

"""tools/theme.sh and theme.ps1 must judge config.json the way the launcher reads it.

Both scripts' only check was "does this parse as JSON". The launcher does more than parse: Config.read()
builds `new JSONObject(text)` and answers "no overlay" for anything that is not a JSON object - an array,
a number, a string, `true`, `null` all throw there and the overlay is dropped, with one logcat line. A
config.json of that shape therefore replaces the theme the TV had with a file the launcher ignores, so the
set goes back to its built-in config and the push ends "Theme X is on the TV. Undo with: theme.sh --reset".

These tests run the real tools/theme.sh against a stub tools/tv.py that records every call, so "nothing was
sent to the TV" is what the stub not being called means.
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
TOOLS = ROOT / "tools"

# The one thing these tests need from the TV: whether it was spoken to at all.
FAKE_TV = r'''
import os
import sys

with open(os.environ["FAKE_LOG"], "a") as log:
    log.write(" ".join(sys.argv[1:]) + "\n")
sys.stderr.write("stub tv.py: there is no TV in this test\n")
sys.exit(1)
'''


class ThemeConfigRootTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        for name in ("theme.sh", "theme_restart.py"):
            shutil.copy2(TOOLS / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        venv = self.tools / "venv" / "bin"
        venv.mkdir(parents=True)
        (venv / "python").symlink_to(sys.executable)
        self.theme = self.base / "my-theme"
        self.theme.mkdir()
        self.log = self.base / "tv-calls.log"

    def theme_with(self, text):
        (self.theme / "config.json").write_text(text, encoding="utf-8")
        return self.theme

    def run_theme(self):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log)})
        return subprocess.run(["bash", str(self.tools / "theme.sh"), str(self.theme)], env=env,
                              capture_output=True, text=True, timeout=120)

    def tv_calls(self):
        if not self.log.exists():
            return []
        return [line for line in self.log.read_text().splitlines() if line.strip()]

    def assert_refused_as_not_an_object(self, text):
        self.theme_with(text)
        result = self.run_theme()
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("is not a JSON object", result.stderr, result.stderr)
        self.assertIn(str(self.theme / "config.json"), result.stderr, "the message has to name the file")
        self.assertIn("themes/README.md", result.stderr, "and the page that says what a theme holds")
        self.assertEqual([], self.tv_calls(), "nothing may be sent to the TV for a file it would ignore")
        self.assertNotIn("is on the TV", result.stdout, "and it must not read as applied")

    def test_an_array_root_is_refused_before_the_tv(self):
        self.assert_refused_as_not_an_object('[{"background": "#101418"}]\n')

    def test_a_number_a_string_a_boolean_and_null_are_refused(self):
        for text in ("12\n", '"a theme"\n', "true\n", "false\n", "null\n"):
            with self.subTest(config=text.strip()):
                self.assert_refused_as_not_an_object(text)

    def test_broken_json_keeps_its_own_message(self):
        for text in ('{"background": "#101418",}\n', "", "{\n"):
            with self.subTest(config=text.strip()):
                self.theme_with(text)
                result = self.run_theme()
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                self.assertIn("is not valid JSON", result.stderr, result.stderr)
                self.assertNotIn("is not a JSON object", result.stderr)
                self.assertEqual([], self.tv_calls())

    def test_an_empty_object_is_still_a_theme(self):
        """The guard must not start refusing a legal theme: a wallpaper-only folder has {}."""
        self.theme_with("{}\n")
        result = self.run_theme()
        self.assertNotIn("is not a JSON object", result.stderr)
        self.assertTrue(self.tv_calls(), "a theme whose config.json is an object still has to be pushed")

    def test_a_shipped_theme_still_reaches_the_tv(self):
        shutil.copy2(ROOT / "themes" / "aurora" / "config.json", self.theme / "config.json")
        result = self.run_theme()
        self.assertNotIn("is not a JSON object", result.stderr)
        self.assertTrue(self.tv_calls(), "the pictures in themes/ are pushed the way they always were")
        self.assertTrue(any("--push" in call for call in self.tv_calls()),
                        f"config.json goes up with --push: {self.tv_calls()}")

    def test_the_check_comes_before_the_first_push(self):
        source = (TOOLS / "theme.sh").read_text(encoding="utf-8")
        self.assertLess(source.index("is not a JSON object"), source.index("--push"),
                        "the shape is judged before anything is pushed")

    def test_the_launcher_really_ignores_a_non_object(self):
        """The message's claim, read out of the launcher: an object is built, and any failure is no overlay."""
        source = (ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
                  / "Config.java").read_text(encoding="utf-8")
        read = source[source.index("private static JSONObject read(File file)"):]
        self.assertIn("return new JSONObject(text);", read)
        self.assertIn("return null;", read, "a file that is not an object has to come back as no overlay")

    def test_the_windows_twin_makes_the_same_judgment(self):
        source = (TOOLS / "theme.ps1").read_text(encoding="utf-8")
        self.assertIn("[System.Management.Automation.PSCustomObject]", source,
                      "ConvertFrom-Json answers an array or a number for those roots, and the launcher does not")
        self.assertIn("is not a JSON object", source)
        self.assertIn("themes/README.md", source)
        self.assertLess(source.index("is not a JSON object"), source.index("--push"),
                        "the Windows twin judges the file before it pushes it too")


if __name__ == "__main__":
    unittest.main()

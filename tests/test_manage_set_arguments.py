"""tools/manage.py set reads its own arguments before it reads a theme folder.

themes/README.md hands a theme author `tools/manage.py set <switch> <value> --theme <your folder>`, and the value
is the easiest word to leave out: `set hidden_apps --theme ~/my-theme` took --theme out, leaving one word, and
reached cmd_set with two arguments - IndexError out of `args[1]`, a traceback with exit 1. Exit 1 is the status
this script keeps for a theme.sh push that did not succeed, so a word left out read as "the theme was pushed and
the TV refused it", and the run had already made no change anywhere to say otherwise. The same command line has
a second shape worth one sentence: `set <switch> <value> --theme` with no folder behind the flag answered "set
needs --theme" with exit 1, while every other usage error in this script is exit 2.

So set's arguments are read in one place, before a folder is looked at: the switch, its value, and --theme with
its folder, exactly once each, or the usage line naming what is missing. Nothing here reaches the TV.
"""
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


class SetArgumentsTest(unittest.TestCase):
    """A command line set cannot run is the usage line and exit 2, before a theme folder is read or written."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "my-theme"
        self.theme.mkdir()
        self.config = self.theme / "config.json"
        # cmd_set ends by pushing the theme with tools/theme.sh; the tests stop at that call.
        patcher = mock.patch.object(manage, "run")
        self.push = patcher.start()
        self.push.return_value = 0
        self.addCleanup(patcher.stop)

    def run_set(self, *argv):
        """manage.main() with set's arguments: (status, stderr). Nothing is sent to a TV."""
        err = io.StringIO()
        with mock.patch("sys.stderr", err), mock.patch("sys.stdout", io.StringIO()):
            status = manage.main(["set", *argv])
        return status, err.getvalue()

    def written(self):
        return json.loads(self.config.read_text(encoding="utf-8")) if self.config.is_file() else None

    def test_a_value_left_out_is_the_usage_line_and_not_an_index_error(self):
        # The shape a reader types when they are still choosing the value: the switch and the folder only.
        status, err = self.run_set("hidden_apps", "--theme", str(self.theme))
        self.assertEqual(status, 2, err)
        self.assertIn("the value of hidden_apps", err)
        self.assertIn("--theme <your theme folder>", err)
        self.assertIsNone(self.written(), "a refused command line must not write the theme")
        self.push.assert_not_called()

    def test_a_theme_with_no_folder_behind_it_is_the_usage_line(self):
        status, err = self.run_set("hidden_apps", '["a"]', "--theme")
        self.assertEqual(status, 2, err)
        self.assertIn("--theme needs the folder it writes into", err)
        self.assertIn("set hidden_apps", err)
        self.push.assert_not_called()

    def test_the_switch_alone_names_the_value_and_the_folder(self):
        status, err = self.run_set("hidden_apps")
        self.assertEqual(status, 2, err)
        self.assertIn("the value of hidden_apps", err)
        self.assertIn("--theme <your theme folder>", err)

    def test_no_arguments_names_everything_the_command_needs(self):
        status, err = self.run_set()
        self.assertEqual(status, 2, err)
        self.assertIn("set needs a switch, its value and --theme <your theme folder>", err)

    def test_a_word_left_over_is_refused_rather_than_ignored(self):
        # The push takes no script arguments (install does), so a third word is a typo, not a value.
        status, err = self.run_set("hidden_apps", '["a"]', "--theme", str(self.theme), "extra")
        self.assertEqual(status, 2, err)
        self.assertIn("3 words were given", err)
        self.assertIsNone(self.written())
        self.push.assert_not_called()

    def test_a_second_theme_is_refused(self):
        status, err = self.run_set("hidden_apps", '["a"]', "--theme", str(self.theme),
                                   "--theme", str(self.theme))
        self.assertEqual(status, 2, err)
        self.assertIn("one --theme", err)
        self.push.assert_not_called()

    def test_the_shape_the_pages_print_still_writes_and_pushes(self):
        status, err = self.run_set("hidden_apps", '["a", "b"]', "--theme", str(self.theme))
        self.assertEqual(status, 0, err)
        self.assertEqual(self.written(), {"hidden_apps": ["a", "b"]})
        self.push.assert_called_once_with(
            ["bash", str(manage.TOOLS / "theme.sh"), str(self.theme.resolve())], manage.ROOT)

    def test_theme_first_is_still_accepted(self):
        # --theme is lifted out wherever it sits, as it always was.
        status, err = self.run_set("--theme", str(self.theme), "dock_band", "true")
        self.assertEqual(status, 0, err)
        self.assertEqual(self.written(), {"dock_band": True})
        self.push.assert_called_once()

    def test_the_judgement_is_above_the_folder_and_the_push_in_the_source(self):
        source = (ROOT / "tools" / "manage.py").read_text(encoding="utf-8")
        branch = source[source.index('if c == "set":'):source.index("print(__doc__)", source.index('if c == "set":'))]
        self.assertIn("set_args(args)", branch)
        self.assertIn("except Usage", branch)
        self.assertIn("return 2", branch)
        self.assertNotIn("args[1]", branch, "set indexes its arguments without judging the count again")
        self.assertLess(branch.index("except Usage"), branch.index("cmd_set("),
                        "the refusal must come before cmd_set reads the folder")


if __name__ == "__main__":
    unittest.main()

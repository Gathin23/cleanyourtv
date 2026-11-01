"""tools/manage.py's list, status, show, install and remove read their own words, as set does.

Each of them looked at the first word or two of its own command line and ignored the rest. `list addons themes`
printed the add-ons and exited 0, which reads as "both are listed"; `status --json` printed the status it prints
without the flag; and `remove weather --dry-run` ran pm uninstall on the TV, so a set was changed by a command line
that asked for something else. set has answered a word left over with the usage line and exit 2 since it was given
one (tests/test_manage_set_arguments.py), and the other commands answer the same way now, before a README is read
or the TV is asked anything.

`show` and `install` keep taking several words on purpose: an add-on's name in addons/README.md can be two
("Frigate Glance"), and install passes what is left on its line to the creator's own script - both pinned here so
the judgement cannot start refusing the documented shape.
"""
import io
import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

# The three groups `manage.py list` prints, as the docstring's usage line writes them.
GROUPS = ("addons", "themes", "switches")


class CommandWordsTest(unittest.TestCase):
    """A word a command does not read is the usage line and exit 2, before the TV is asked anything."""

    def setUp(self):
        # The refusals are the point: a command line that cannot run must not reach a TV. Any call fails here.
        patcher = mock.patch.object(manage, "tv", side_effect=AssertionError("the TV was asked"))
        self.tv = patcher.start()
        self.addCleanup(patcher.stop)

    def run_main(self, *argv):
        """manage.main() for that command line: (status, stdout, stderr)."""
        out, err = io.StringIO(), io.StringIO()
        with mock.patch("sys.stdout", out), mock.patch("sys.stderr", err):
            status = manage.main(list(argv))
        return status, out.getvalue(), err.getvalue()

    def assertRefused(self, argv, *expected):
        status, out, err = self.run_main(*argv)
        self.assertEqual(status, 2, err)
        self.assertEqual(out, "", "a refused command line prints nothing on stdout")
        for phrase in expected:
            self.assertIn(phrase, err)
        self.tv.assert_not_called()
        return err

    def test_a_second_word_after_list_would_be_dropped(self):
        # The shape that printed the add-ons only, and exited 0 as if both groups were listed.
        err = self.assertRefused(["list", "addons", "themes"], "'themes'", "would drop")
        self.assertIn("list [addons|themes|switches]", err)

    def test_list_of_a_group_that_is_not_there_names_the_three(self):
        self.assertRefused(["list", "apps"], "'apps'", "addons, themes or switches")

    def test_the_group_list_documents_still_lists(self):
        for group in GROUPS:
            with self.subTest(group=group):
                status, out, err = self.run_main("list", group)
                self.assertEqual(status, 0, err)
                self.assertTrue(out.startswith(f"{group} ("), out)
                self.assertEqual(err, "")

    def test_no_word_lists_the_three_groups(self):
        status, out, err = self.run_main("list")
        self.assertEqual(status, 0, err)
        for group in GROUPS:
            self.assertIn(f"{group} (", out)

    def test_status_takes_no_words(self):
        self.assertRefused(["status", "--json"], "'--json'", "status takes no words")

    def test_a_word_after_the_addon_is_refused_before_the_uninstall(self):
        # The one that changed a TV: `remove weather --dry-run` ran pm uninstall with a word it never read.
        err = self.assertRefused(["remove", "weather", "--dry-run"], "'--dry-run'", "remove takes one add-on")
        self.assertIn("remove <add-on>", err)

    def test_remove_needs_the_addon(self):
        self.assertRefused(["remove"], "remove needs one add-on", "remove <add-on>")

    def test_show_still_takes_a_name_in_two_words(self):
        # addons/README.md writes names such as "Frigate Glance"; these are not words to drop.
        spaced = next((a for a in manage.addons() if " " in a["name"]), None)
        if spaced is None:
            self.fail("an add-on whose row label is two words keeps `show` a many-word command")
        status, out, err = self.run_main("show", *spaced["name"].split())
        self.assertEqual(status, 0, err)
        self.assertIn(spaced["id"], out)
        self.tv.assert_not_called()

    def test_show_needs_a_name(self):
        self.assertRefused(["show"], "show needs an add-on, theme or switch", "show <add-on, theme or switch>")

    def test_install_needs_something_to_install(self):
        self.assertRefused(["install"], "install needs an add-on or a theme", "install <add-on or theme>")

    def test_every_command_in_the_usage_block_is_judged(self):
        # The usage block in the docstring is what a reader copies; a command added there without its words being
        # read here is the bug this file is about, so the two lists are held to each other.
        block = [line.strip() for line in (manage.__doc__ or "").splitlines() if "manage.py " in line]
        commands = {line.split("manage.py ", 1)[1].split()[0] for line in block if "manage.py " in line}
        self.assertGreaterEqual(len(commands), 6, block)
        self.assertEqual(commands - {"set"}, set(manage.WORDS), "usage block and WORDS")
        self.assertEqual(set(manage.WORDS), set(manage.USAGE_LINES))
        self.assertEqual(set(manage.LIST_GROUPS), set(GROUPS))

    def test_a_word_before_the_command_still_prints_the_usage(self):
        status, out, err = self.run_main("publish", "addons")
        self.assertEqual(status, 2)
        self.assertIn("manage.py list", out)     # the docstring, as `--help` prints it
        self.tv.assert_not_called()


if __name__ == "__main__":
    unittest.main()

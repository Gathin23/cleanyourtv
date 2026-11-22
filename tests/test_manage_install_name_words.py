"""tools/manage.py install and remove read an add-on name written in several words, as show does.

addons/README.md names add-ons such as "Frigate Glance". show joined the words, but install and remove looked up
the first word alone, so `install Frigate Glance` answered "no add-on or theme called Frigate" and exited 1.
"""
import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


class NameInSeveralWords(unittest.TestCase):
    def setUp(self):
        self.spaced = next(a for a in manage.addons() if " " in a["name"] and a["package"])

    def test_install_finds_the_addon_and_passes_the_rest_on(self):
        with mock.patch.object(manage, "install_addon", return_value=0) as install:
            status = manage.main(["install", *self.spaced["name"].split(), "--extra"])
        self.assertEqual(status, 0)
        item, extra = install.call_args.args
        self.assertEqual(item["id"], self.spaced["id"])
        self.assertEqual(extra, ["--extra"])

    def test_remove_finds_the_addon(self):
        with mock.patch.object(manage, "tv", return_value="Success\n") as tv:
            status = manage.main(["remove", *self.spaced["name"].split()])
        self.assertEqual(status, 0)
        tv.assert_called_once_with(f"pm uninstall {self.spaced['package']}")

    def test_remove_still_refuses_a_word_after_the_name(self):
        with mock.patch.object(manage, "tv") as tv, mock.patch("sys.stderr"):
            status = manage.main(["remove", *self.spaced["name"].split(), "--dry-run"])
        self.assertEqual(status, 2)
        tv.assert_not_called()


if __name__ == "__main__":
    unittest.main()

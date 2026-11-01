"""A file an add-on's README hands the reader to run has to be executable in the checkout.

addons/README.md builds every add-on with `cd addons/<name> && ./gradlew assembleRelease`, and six of the
folders' own pages go further and give one of the folder's scripts as a command: frigate-glance's Setup
step is `TV_HOST=<tv ip> addons/frigate-glance/install.sh backup/frigate-glance-config.json` (that script
then runs `./gradlew assembleRelease` itself), photo-screensaver's is
`TV_HOST=192.168.1.50 addons/photo-screensaver/build-and-install.sh` with `sync.sh` for every later upload,
and bluetooth-toggle and digital-rain print `cd addons/<name> && ./gradlew assembleRelease`.

git records whether a file may be started, and a fresh clone keeps that: six of those files were stored
100644, so the documented command stopped at `sh: 1: addons/frigate-glance/install.sh: Permission denied`
with exit 126 - before the build, before TV_HOST was read, and with nothing in the page saying why. The
sibling test for the tools' own printed next steps (tests/test_printed_commands_run_as_typed.py) reads the
same bit out of `os.access`, which is why the .py files a doc starts with `python3` are not asked for it and
the shell scripts are.

Nothing here runs an add-on or reaches a TV: it reads the tree and the pages.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_addons import addon_dirs  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ADDONS = ROOT / "addons"

# `addons/<name>/<script>.sh`, the form a page can be copied from, and `./gradlew`, the form that only
# works from inside the folder.
SCRIPT = re.compile(r"(?<![\w/.-])addons/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\.sh(?![\w.-])")
GRADLEW = re.compile(r"(?<![\w/.-])\./gradlew(?![\w.-])")


def runnable(path):
    """Whether the checkout lets PATH be started; True for anything this check cannot judge."""
    if os.name == "nt":
        return True
    return os.access(path, os.X_OK)


class AddonScriptsRunnableTest(unittest.TestCase):
    def test_every_addon_gradlew_is_executable(self):
        """addons/README.md's macOS/Linux route is `cd addons/<name> && ./gradlew assembleRelease`."""
        read = 0
        for folder in addon_dirs():
            wrapper = folder / "gradlew"
            if not wrapper.is_file():
                continue
            read += 1
            with self.subTest(addon=folder.name):
                self.assertTrue(
                    runnable(wrapper),
                    f"{wrapper.relative_to(ROOT)} is not executable: git has it as 100644, so the command "
                    f"addons/README.md gives - cd addons/{folder.name} && ./gradlew assembleRelease - "
                    f"answers \"Permission denied\" in a fresh clone. chmod +x it and commit the mode")
        self.assertGreaterEqual(read, 20, "every add-on carries a gradlew: none was read")

    def test_every_script_an_addon_readme_runs_is_executable(self):
        """The page gives the command, so the file it starts has to be one git marks as startable."""
        scripts = 0
        for folder in addon_dirs():
            readme = folder / "README.md"
            if not readme.is_file():
                continue
            text = readme.read_text(encoding="utf-8", errors="ignore")
            for name in sorted(set(SCRIPT.findall(text))):
                scripts += 1
                target = ROOT / name
                with self.subTest(readme=readme.relative_to(ROOT), script=name):
                    self.assertTrue(target.is_file(),
                                    f"{readme.relative_to(ROOT)} runs {name}, which is not in the tree")
                    self.assertTrue(
                        runnable(target),
                        f"{name} is not executable: {readme.relative_to(ROOT)} gives it as a command, "
                        f"and a checkout that has it 100644 answers \"Permission denied\" (exit 126) "
                        f"before the script runs a line. chmod +x it and commit the mode")
        self.assertGreaterEqual(scripts, 10,
                                "several add-on READMEs run a script of their own: none was read")

    def test_a_readme_that_shows_dot_slash_gradlew_means_that_folder(self):
        """`./gradlew` is only ./gradlew from inside the add-on, so it is that folder's wrapper."""
        shown = 0
        for folder in addon_dirs():
            readme = folder / "README.md"
            if not readme.is_file():
                continue
            text = readme.read_text(encoding="utf-8", errors="ignore")
            if not GRADLEW.search(text):
                continue
            shown += 1
            wrapper = folder / "gradlew"
            with self.subTest(addon=folder.name):
                self.assertTrue(wrapper.is_file(), f"{readme.relative_to(ROOT)} runs ./gradlew")
                self.assertTrue(runnable(wrapper), f"{wrapper.relative_to(ROOT)} is not executable")
        self.assertGreaterEqual(shown, 2, "add-on READMEs print ./gradlew: none was read")

    def test_the_two_pages_that_need_the_bit_still_give_those_commands(self):
        """Without these lines the check above would pass on a tree it no longer describes."""
        page = (ADDONS / "README.md").read_text(encoding="utf-8")
        self.assertIn("cd addons/<name> && ./gradlew assembleRelease", page,
                      "addons/README.md no longer builds an add-on with ./gradlew")
        self.assertIn("./gradlew", (ADDONS / "bluetooth-toggle" / "README.md").read_text(encoding="utf-8"),
                      "the page this check was written for no longer prints ./gradlew")
        self.assertIn("addons/frigate-glance/install.sh",
                      (ADDONS / "frigate-glance" / "README.md").read_text(encoding="utf-8"),
                      "the page this check was written for no longer runs install.sh")
        self.assertIn("addons/photo-screensaver/sync.sh",
                      (ADDONS / "photo-screensaver" / "README.md").read_text(encoding="utf-8"),
                      "the page this check was written for no longer runs sync.sh")


if __name__ == "__main__":
    unittest.main()

"""Every add-on's Gradle project keeps Android Studio's `local.properties` out of git.

Each add-on folder is its own Gradle project (`settings.gradle`, `gradlew`, `app/`) and its own README
tells the owner to build in it. Opening one in Android Studio, which is how the launcher's own
build is set up (README.md: "On Windows with Android Studio installed nothing else is needed"),
writes `addons/<name>/local.properties` there:

    sdk.dir=/home/<you>/Android/Sdk

That is the contributor's own home directory, and CONTRIBUTING.md and AGENTS.md both keep personal
paths out of a contribution ("No secrets or identifiers ... no IP addresses, Wi-Fi names ..."). It is
also not a file anything links to, so `git add -A` after a build would commit it by accident. `*.apk`
and `*.aab` are ignored by the root .gitignore and `.gradle/` and `build/` by each folder's own, which
is why a plain `./gradlew assembleRelease` leaves nothing to commit - but `local.properties` was
carried by only some of the folders: 24 of the 26 add-ons ignore it and flock and tv-static did not,
so the two folders a build was opened in for a screenshot were the ones that leaked the path. The
maintainer added the same .gitignore to addons/tv-inspector by hand in "Round AF" for the same reason
("the .gitignore the other add-ons carry (.gradle/, build/, app/build/, local.properties), so a local
build leaves nothing to commit"), so this is two folders missed rather than the pattern.

The check is git's own answer for a path inside each folder, not a copy of the lines: the folders'
ignore files differ in shape (`/local.properties`, `/**/build/`, a comment above the line) and any
spelling that keeps the file out is fine. The other half is the opposite mistake - a folder that
ignores what it ships, which would leave a broken add-on in a fresh clone.

Needs no TV, no Gradle and no Android SDK: the paths are judged by git and the folders are read. It does
need a git checkout: in a source tarball, or a tree exported with `git archive`, there is no index and no
ignore rules to ask about, and the cases that read them skip with the reason rather than failing once per
add-on and path.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from functools import lru_cache
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
ADDONS = ROOT / "addons"

# What every add-on folder carries: its own Gradle project.
PROJECT_MARKERS = ("settings.gradle", "settings.gradle.kts", "gradlew")
# The one file Android Studio and the Android Gradle plugin write into the project root.
LOCAL_PROPERTIES = "local.properties"


def git(*args, root=ROOT):
    """git's answer for this checkout, or None when git is not installed at all.

    A tree that is not a repository is a different case: git is there and answers 128, so callers that
    cannot use the answer ask git_work_tree() first.
    """
    try:
        return subprocess.run(["git", *args], cwd=str(root), capture_output=True, text=True)
    except (OSError, subprocess.SubprocessError):
        return None


@lru_cache(maxsize=None)
def git_work_tree(root=ROOT):
    """True for a checkout git can answer for; SkipTest, with the reason, when it cannot.

    `git check-ignore` exits 128 both for a tree git cannot read at all and for a pathspec it does not
    understand, and this module wants the second to fail rather than pass. So the first is told apart
    once, by asking git whether it is in a work tree: a source tarball, or a tree exported with
    `git archive`, carries the add-ons' .gitignore files but has no index and no work tree, and nothing
    can be asked about what git would ignore there. The other modules that read git's answer return None
    for the tree and skip (tests/test_no_prebuilt_binaries.py's tracked(), test_tool_usage_commands.py's
    tool_modes()); this one answered 128 with a failure for every add-on and every path - 297 of them in
    an exported tree, which reads as a broken suite rather than as a checkout nothing can be asked about.
    """
    run = git("rev-parse", "--is-inside-work-tree", root=root)
    if run is None:
        raise unittest.SkipTest("git is not installed, so a folder's ignore rules cannot be read")
    if run.returncode != 0 or run.stdout.strip() != "true":
        raise unittest.SkipTest(
            "this checkout is not a git repository, so the add-ons' ignore rules and the tracked files "
            f"cannot be read (git said: {run.stderr.strip() or 'no answer'})")
    return True


def projects():
    """Every add-on folder that is its own Gradle project."""
    return [d for d in sorted(ADDONS.iterdir())
            if d.is_dir() and any((d / marker).exists() for marker in PROJECT_MARKERS)]


@lru_cache(maxsize=None)
def ignored(folder_name, relpath):
    """True when git ignores addons/<folder_name>/<relpath> in this checkout."""
    run = git("check-ignore", "-q", "--no-index", f"addons/{folder_name}/{relpath}")
    if run is None:
        raise unittest.SkipTest("git is not installed, so a folder's ignore rules cannot be read")
    # 0 ignored, 1 not ignored, 128 something git could not read: the last two are both "not ignored"
    # for a reader, but a 128 is worth failing on rather than reading as a missing line.
    if run.returncode not in (0, 1):
        raise AssertionError(f"git check-ignore addons/{folder_name}/{relpath} exited "
                             f"{run.returncode}: {run.stderr.strip()}")
    return run.returncode == 0


def ignore_lines(folder):
    path = folder / ".gitignore"
    if not path.is_file():
        return "(no .gitignore)"
    return " / ".join(line for line in path.read_text(encoding="utf-8").splitlines() if line.strip())


class EveryAddonKeepsLocalPropertiesOut(unittest.TestCase):
    def test_every_addon_project_ignores_local_properties(self):
        git_work_tree()
        missed = []
        for folder in projects():
            if not ignored(folder.name, LOCAL_PROPERTIES):
                missed.append(f"{folder.relative_to(ROOT).as_posix()}/{LOCAL_PROPERTIES} is not ignored; "
                              f"{folder.relative_to(ROOT).as_posix()}/.gitignore carries: "
                              f"{ignore_lines(folder)}")
        self.assertEqual([], missed, "an Android Studio or Gradle run writes local.properties with "
                                     "the contributor's own sdk.dir, and `git add -A` would commit it "
                                     "(the other add-ons keep it out): " + "; ".join(missed))

    def test_no_addon_has_a_local_properties_committed(self):
        git_work_tree()
        run = git("ls-files", "--", "addons")
        self.assertIsNotNone(run, "git_work_tree() skips when git is not installed")
        self.assertEqual(0, run.returncode, run.stderr)
        committed = [line for line in run.stdout.splitlines()
                     if line.rsplit("/", 1)[-1] == LOCAL_PROPERTIES]
        self.assertEqual([], committed, "the file names the machine it was built on and belongs to "
                                        "nobody else's checkout")

    def test_the_build_output_is_kept_out_too(self):
        # flock's .gitignore has no `app/build/` line and this still holds: `build/` matches at any
        # depth. The behaviour is what matters, not one spelling of it.
        git_work_tree()
        for folder in projects():
            for relpath in (".gradle/file", "build/file", "app/build/outputs/apk/release/app-release.apk"):
                with self.subTest(folder=folder.name, path=relpath):
                    self.assertTrue(ignored(folder.name, relpath),
                                    f"{folder.name}/{relpath} would be committed after a local build")

    def test_a_project_does_not_ignore_what_it_ships(self):
        # The opposite mistake: a folder that ignores its own files is missing from a fresh clone.
        git_work_tree()
        for folder in projects():
            shipped = ["README.md", "screenshot.jpg", "gradlew", "settings.gradle"]
            shipped += [p.name for p in (folder / "app").glob("build.gradle*")] if (folder / "app").is_dir() else []
            for relpath in shipped:
                if not (folder / relpath).exists():
                    continue
                with self.subTest(folder=folder.name, path=relpath):
                    self.assertFalse(ignored(folder.name, relpath),
                                     f"{folder.name}/{relpath} is part of the add-on and has to be in "
                                     f"the checkout")

    def test_the_sweep_read_the_addons(self):
        # A walk that read nothing must not pass.
        found = projects()
        self.assertGreaterEqual(len(found), 20, f"only {len(found)} add-on Gradle projects were found "
                                                f"under {ADDONS}")
        with_ignore = [f for f in found if (f / ".gitignore").is_file()]
        self.assertGreaterEqual(len(with_ignore), 20, "an add-on folder has no .gitignore; every one "
                                                      "carries its own (addons/README.md's build route "
                                                      "runs inside it)")
        self.assertIn("flock", [f.name for f in found])
        self.assertIn("tv-static", [f.name for f in found])


class AnExportedTreeTest(unittest.TestCase):
    """This module's cases in a tree git cannot answer for: they skip, and say what is missing.

    A source tarball and a `git archive` export both carry the add-ons' .gitignore files and no index, so
    the question this module asks git has no answer there. Answered as an error it was 297 failures from
    three cases - one per add-on and per path - which reads as a broken suite rather than as a checkout
    nothing can be asked about. The fixture is a tree of this module's own, so the run judges it and not
    this checkout: 22 add-ons, enough for the sweep's own floor, with no .git anywhere above them.
    """

    def exported_tree(self):
        root = Path(tempfile.mkdtemp(prefix="no-git-"))
        self.addCleanup(shutil.rmtree, root, ignore_errors=True)
        # A temporary folder can itself sit inside a checkout (TMPDIR set to one), and then git does have
        # an answer for the fixture. Say so instead of asserting on a shape this machine cannot make.
        probe = subprocess.run(["git", "rev-parse", "--is-inside-work-tree"], cwd=str(root),
                               capture_output=True, text=True)
        if probe.returncode == 0 and probe.stdout.strip() == "true":
            self.skipTest(f"{root} is inside a git checkout, so a tree git cannot answer for cannot be "
                          "built here")
        for name in ["flock", "tv-static"] + ["addon-%02d" % i for i in range(20)]:
            addon = root / "addons" / name
            addon.mkdir(parents=True)
            (addon / "settings.gradle").write_text("", encoding="utf-8")
            (addon / ".gitignore").write_text("local.properties\n.gradle/\nbuild/\n", encoding="utf-8")
            (addon / "README.md").write_text("", encoding="utf-8")
        (root / "tests").mkdir()
        shutil.copy2(Path(__file__), root / "tests" / Path(__file__).name)
        return root

    def run_module(self, root):
        # Only the class that reads git, so the fixture run does not collect this one: it builds a fixture
        # of its own, and a fixture inside a fixture would nest for as long as the runs are allowed to.
        target = f"tests.{Path(__file__).stem}.{EveryAddonKeepsLocalPropertiesOut.__name__}"
        env = {k: v for k, v in os.environ.items() if k not in ("PYTHONPATH", "TV_HOST")}
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        return subprocess.run([sys.executable, "-m", "unittest", "-v", target],
                              cwd=str(root), capture_output=True, text=True, timeout=120, env=env)

    def test_the_cases_that_read_git_skip_and_say_why(self):
        run = self.run_module(self.exported_tree())
        output = run.stdout + run.stderr
        self.assertEqual(run.returncode, 0, output)
        self.assertNotIn("FAILED", output)
        # The four cases that ask git: two of the three ignore-rule sweeps, the tracked-file case and the
        # counterpart sweep. The sweep floor reads only the folders, so it still runs and passes.
        self.assertEqual(4, output.count("this checkout is not a git repository"), output)
        self.assertIn("skipped=4", output, output)

    def test_a_checkout_git_can_answer_for_is_not_skipped(self):
        # The other side: this checkout is a repository, and the cases read git's answer here.
        self.assertTrue(git_work_tree())


if __name__ == "__main__":
    unittest.main()

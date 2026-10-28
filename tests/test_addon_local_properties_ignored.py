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

Needs no TV, no Gradle and no Android SDK: the paths are judged by git and the folders are read.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from functools import lru_cache
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parent.parent
ADDONS = ROOT / "addons"

# What every add-on folder carries: its own Gradle project.
PROJECT_MARKERS = ("settings.gradle", "settings.gradle.kts", "gradlew")
# The one file Android Studio and the Android Gradle plugin write into the project root.
LOCAL_PROPERTIES = "local.properties"


def git(*args):
    """git's answer, or None when git is not installed (a source tarball has no .git)."""
    try:
        return subprocess.run(["git", *args], cwd=str(ROOT), capture_output=True, text=True)
    except (OSError, subprocess.SubprocessError):
        return None


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
        run = git("ls-files", "--", "addons")
        if run is None:
            self.skipTest("git is not installed, so the tracked files cannot be listed")
        self.assertEqual(0, run.returncode, run.stderr)
        committed = [line for line in run.stdout.splitlines()
                     if line.rsplit("/", 1)[-1] == LOCAL_PROPERTIES]
        self.assertEqual([], committed, "the file names the machine it was built on and belongs to "
                                        "nobody else's checkout")

    def test_the_build_output_is_kept_out_too(self):
        # flock's .gitignore has no `app/build/` line and this still holds: `build/` matches at any
        # depth. The behaviour is what matters, not one spelling of it.
        for folder in projects():
            for relpath in (".gradle/file", "build/file", "app/build/outputs/apk/release/app-release.apk"):
                with self.subTest(folder=folder.name, path=relpath):
                    self.assertTrue(ignored(folder.name, relpath),
                                    f"{folder.name}/{relpath} would be committed after a local build")

    def test_a_project_does_not_ignore_what_it_ships(self):
        # The opposite mistake: a folder that ignores its own files is missing from a fresh clone.
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


if __name__ == "__main__":
    unittest.main()

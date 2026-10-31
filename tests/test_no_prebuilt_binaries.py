"""A prebuilt binary is refused anywhere in the tree, not only an APK in an add-on folder.

AGENTS.md's hard rules: "**No prebuilt APKs or binaries** in the repo, not even as CI artifacts. People
build from source", and it is the reason the maintainer closed #761, #398 and #1298.
docs/DOS-AND-DONTS.md puts the same rule to a contributor and names the shapes: "No APKs (not even as
workflow artifacts), `.so` files or zip uploads: everyone builds from source".

tests/test_addons.py's test_no_prebuilt_apks is the only guard reading it, and it reads one folder and
one suffix: `ADDONS.rglob("*.apk")`. A `.so` beside an add-on, an `.apk` outside addons/, a `.zip` a
contributor uploaded was read by nothing: on d4ac32c3, three such files forced into the tree
(docs/tool.apk, docs/libfoo.so, addons/photo-screensaver/bundle.zip) leave the suite green - `Ran 2368
tests OK` - while this file names all three.

The walk is git's index, so what a clone gets is what is judged, and a build output on this computer
(launcher/app/build/...) is not. The Gradle wrapper jar is the one exception, named below with its reason.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import subprocess
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]

# The shapes docs/DOS-AND-DONTS.md names (APKs and .so files and zip uploads), plus the archives and
# bytecode a built app or add-on produces: each one is somebody's build output, which is what the rule is
# about. A font, a screenshot or a theme's wallpaper is a resource the tree means to ship, so it is not here.
BINARIES = (".apk", ".aab", ".so", ".aar", ".dex", ".class", ".jar", ".zip")

# Gradle's wrapper, which every add-on ships as gradle/wrapper/gradle-wrapper.jar: addons/<name>/gradlew
# starts it, and tests/test_addons.py's PowerShell twin check names the same file. It is a prebuilt binary
# by design, so the rule passes it - and only it.
WRAPPER = "gradle/wrapper/gradle-wrapper.jar"


def tracked(root=ROOT):
    """Every path git has in its index, as a clone gets it; None when git is not installed."""
    try:
        run = subprocess.run(["git", "ls-files", "-z"], cwd=str(root), capture_output=True)
    except (FileNotFoundError, OSError):
        return None
    if run.returncode != 0:
        return None
    return [p.decode("utf-8", "replace") for p in run.stdout.split(b"\0") if p]


def problems(paths):
    """`path: what` for every prebuilt binary in PATHS; [] when there is none."""
    out = []
    for path in paths:
        if path.endswith("/" + WRAPPER) or path == WRAPPER:
            continue
        for suffix in BINARIES:
            if path.lower().endswith(suffix):
                out.append(f"{path}: a prebuilt {suffix} is committed; this repo builds from source "
                           f"(AGENTS.md, \"No prebuilt APKs or binaries\"; docs/DOS-AND-DONTS.md)")
                break
    return out


def clone():
    """The index a clone gets, or a skip when git is not installed."""
    paths = tracked()
    if paths is None:
        raise unittest.SkipTest("git is not installed, so the index a clone gets cannot be read")
    return paths


class TheTreeTest(unittest.TestCase):
    """What a clone of this repository gets holds no prebuilt binary but the Gradle wrapper."""

    def test_the_walk_reads_a_clone(self):
        # A walk that read nothing would pass; this clone has ~2,800 tracked files.
        paths = clone()
        self.assertGreaterEqual(len(paths), 2000, "the walk read %d paths" % len(paths))

    def test_no_prebuilt_binary_is_committed(self):
        paths = clone()
        self.assertEqual([], problems(paths), "\n".join(problems(paths)))

    def test_the_wrapper_is_the_one_exception_and_it_still_ships(self):
        wrappers = [p for p in clone() if p.endswith("/" + WRAPPER)]
        self.assertGreaterEqual(len(wrappers), 20,
                                "the exceptions read %d wrapper jars; every add-on ships one" % len(wrappers))
        self.assertEqual([], problems(wrappers), "the Gradle wrapper is the one binary the rule passes")
        # The exception is the wrapper's path, not "a .jar is fine": a jar anywhere else is reported.
        self.assertEqual(1, len(problems(["addons/quiet-hours/gradle/tool.jar"])))


class ReaderTest(unittest.TestCase):
    """The rule itself, over paths written here: each shape is reported, the wrapper is not."""

    def test_each_shape_is_reported(self):
        for path, suffix in (("examples/acme-demo-1/tool.apk", ".apk"),
                             ("docs/libfoo.so", ".so"),
                             ("addons/photo-sync/bundle.zip", ".zip"),
                             ("tools/helper.jar", ".jar"),
                             ("launcher/app/libs/thing.aar", ".aar"),
                             ("launcher/app/classes.dex", ".dex"),
                             ("addons/x/app/build/classes/App.class", ".class")):
            with self.subTest(path=path):
                found = problems([path])
                self.assertEqual(1, len(found), found)
                self.assertIn(path, found[0])
                self.assertIn(suffix, found[0])
                self.assertIn("builds from source", found[0])

    def test_an_uppercase_suffix_is_the_same_shape(self):
        self.assertEqual(1, len(problems(["docs/TOOL.APK"])))

    def test_the_gradle_wrapper_is_not_reported(self):
        self.assertEqual([], problems(["addons/quiet-hours/gradle/wrapper/gradle-wrapper.jar"]))

    def test_a_resource_the_tree_means_to_ship_is_not_reported(self):
        for path in ("launcher/app/src/main/res/font/outfit.ttf",
                     "themes/nature-calm/wallpapers/002.jpg",
                     "docs/new-home.jpg",
                     "addons/tv-hub/app/src/test/resources/sample.m3u"):
            with self.subTest(path=path):
                self.assertEqual([], problems([path]))

    def test_a_clean_list_is_quiet(self):
        self.assertEqual([], problems(["examples/xiaomi-mi-tv-4a/debloat.txt", "tools/tv.py"]))


if __name__ == "__main__":
    unittest.main()

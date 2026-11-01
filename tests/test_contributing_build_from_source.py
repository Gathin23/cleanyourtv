"""CONTRIBUTING says how to build the launcher on a laptop, not only how to install it on a TV.

Section 2 of `CONTRIBUTING.md` is the page a contributor follows for anything in `launcher/`, and its
one build sentence handed out `TV_HOST=<ip> launcher/build-and-install.sh`, which needs a TV answering
ADB, then listed the JDK and the two sdkmanager packages. So a reader who wanted the APK - because the
set is not on the network yet, or because the APK is for someone else to install - had the
prerequisites and no command: `./gradlew` is named nowhere in CONTRIBUTING.md or in README.md, and the
two things that stop a hand build are not on either page. Gradle cannot find the SDK unless
`ANDROID_HOME` or `launcher/local.properties` says where it is (the install script writes that file,
which `.gitignore` keeps out of the tree), and it stops with `SDK location not found`; and the JDK has
to be a full 17, because a JRE answers `java -version` as a 17 but has no `jlink`, which the Android
Gradle plugin needs (#1255). #1721 is a reader who reported exactly this - "not able to build the
launcher from my laptop" - after following the pages.

Nothing is copied into the test: the command, the APK path, the size limit and the working directory
come from `.github/workflows/ci.yml`'s `launcher` job, the platform from `launcher/app/build.gradle`'s
`compileSdk`, so a page left behind by a CI or Gradle change fails here instead of misleading the next
reader.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "CONTRIBUTING.md"
CI = ROOT / ".github/workflows/ci.yml"
BUILD_GRADLE = ROOT / "launcher/app/build.gradle"

HEADING = "## 2. Improve the launcher"
# The launcher job's size check, in the form ci.yml writes it:
#   size=$(stat -c %s launcher/app/build/outputs/apk/release/app-release.apk)
#   test "$size" -lt 589824
STAT_LINE = re.compile(r"size=\$\(stat -c %s ([^)\s]+)\)")
LIMIT_LINE = re.compile(r'test "\$size" -lt (\d+)')
JOB_HEAD = re.compile(r"^  ([a-z][a-z0-9-]*):\s*$", re.M)
COMPILE_SDK = re.compile(r"^\s*compileSdk\s+(\d+)\s*$", re.M)

# What the route needs to say, each with the reason it is on the list.
NEEDED = {
    "./gradlew": "the build command itself",
    "assembleRelease": "the task that produces the release APK",
    "local.properties": "the file Gradle reads sdk.dir from",
    "ANDROID_HOME": "the environment variable that does the same",
    "jlink": "what a JRE lacks, so a Java 17 that answers is not enough",
}


def section(text, heading=HEADING):
    """The lines under one `## ` heading, wrapped lines joined; a `### ` subheading stays inside it."""
    out, inside = [], False
    for line in text.splitlines():
        if line.startswith("## "):
            if inside:
                break
            inside = line.strip() == heading
            continue
        if inside:
            out.append(line)
    if not inside and not out:
        raise AssertionError(f"{heading!r} is not a heading in the page")
    return join_wrapped(out)


def join_wrapped(lines):
    """The lines as prose: a paragraph is read as one string, so a phrase split over two lines is found."""
    paragraphs, current = [], []
    for line in lines:
        if line.strip():
            current.append(line.strip())
        elif current:
            paragraphs.append(" ".join(current))
            current = []
    if current:
        paragraphs.append(" ".join(current))
    return "\n".join(paragraphs)


def missing(text):
    """The NEEDED tokens the page's section does not carry."""
    body = section(text)
    return [token for token in NEEDED if token not in body]


def job_block(ci_text, name):
    """The lines of one job in ci.yml."""
    head = None
    for m in JOB_HEAD.finditer(ci_text):
        if head is not None:
            return ci_text[head:m.start()]
        if m.group(1) == name:
            head = m.start()
    return ci_text[head:] if head is not None else ""


def launcher_job(ci_text):
    block = job_block(ci_text, "launcher")
    if not block:
        raise AssertionError("ci.yml has no `launcher:` job, so the build this page describes is not run")
    return block


def apk_path(ci_text):
    m = STAT_LINE.search(ci_text)
    if not m:
        raise AssertionError("ci.yml no longer measures the release APK: the page points at a path nothing builds")
    return m.group(1)


def size_limit(ci_text):
    m = LIMIT_LINE.search(ci_text)
    if not m:
        raise AssertionError("ci.yml no longer refuses an oversized APK: the page quotes a limit nothing applies")
    return int(m.group(1))


def compile_sdk():
    m = COMPILE_SDK.search(BUILD_GRADLE.read_text(encoding="utf-8"))
    if not m:
        raise AssertionError("launcher/app/build.gradle no longer states compileSdk, so the platform is unknown")
    return m.group(1)


class ContributingBuildTest(unittest.TestCase):
    def setUp(self):
        self.page = PAGE.read_text(encoding="utf-8")
        self.ci = CI.read_text(encoding="utf-8")

    def test_the_section_is_read(self):
        # A section that came back empty, or a heading that moved, would make every check below vacuous.
        body = section(self.page)
        self.assertGreaterEqual(len(body), 2000,
                                "CONTRIBUTING's launcher section was not read")
        self.assertGreaterEqual(len(body.splitlines()), 5,
                                "CONTRIBUTING's launcher section was read as too few paragraphs")

    def test_ci_still_builds_the_release_apk_from_launcher(self):
        job = launcher_job(self.ci)
        self.assertIn("assembleRelease", job,
                      "ci.yml's launcher job no longer builds the release APK the page tells people to build")
        self.assertIn("working-directory: launcher", job,
                      "ci.yml's launcher job no longer runs from launcher/, which is where the page's command runs")
        self.assertTrue(apk_path(self.ci).startswith("launcher/app/build/outputs/"),
                        "the APK path ci.yml measures is not the one under launcher/app/build")

    def test_the_page_names_the_command_and_where_the_apk_lands(self):
        body = section(self.page)
        self.assertTrue("assembleRelease" in body,
                        "the build route CONTRIBUTING gives for a laptop names no Gradle task")
        self.assertTrue(apk_path(self.ci) in body,
                        "the page does not say where a by-hand build leaves the APK")

    def test_the_page_names_the_size_the_apk_has_to_stay_under(self):
        body = section(self.page)
        limit = f"{size_limit(self.ci):,}"
        self.assertTrue(limit in body,
                        "the page does not quote the APK size limit CI applies to the build it describes")

    def test_the_page_names_where_gradle_finds_the_sdk_and_a_jdk_with_jlink(self):
        self.assertEqual([], missing(self.page),
                         "a reader following the page is left without the two things that stop a hand build")

    def test_the_page_names_the_platform_the_launcher_compiles_against(self):
        body = section(self.page)
        package = f"platforms;android-{compile_sdk()}"
        self.assertTrue(package in body,
                        "the sdkmanager package the page asks for is not the platform build.gradle compiles against")

    # The reader, against pages written here: wording is free, a phrase that wraps changes nothing, and a
    # page that only installs on a TV is reported.

    def test_a_wrapped_phrase_is_read(self):
        page = ("## 2. Improve the launcher\n\nFrom launcher/, ./gradlew --no-daemon\n"
                "assembleRelease leaves the APK in launcher/app/build/outputs/apk/release.\n"
                "Set ANDROID_HOME or write local.properties, and use a JDK with jlink.\n\n## 3. Next\n")
        self.assertEqual([], missing(page))

    def test_another_section_is_not_read(self):
        page = ("## 1. Add your TV model\n\n./gradlew assembleRelease with ANDROID_HOME, local.properties "
                "and jlink.\n\n## 2. Improve the launcher\n\nBuild with build-and-install.sh.\n\n## 3. Next\n")
        self.assertEqual(sorted(NEEDED), sorted(missing(page)),
                         "the launcher section was read from another heading's text")

    def test_a_page_that_only_installs_on_a_tv_is_reported(self):
        page = ("## 2. Improve the launcher\n\nBuild with `TV_HOST=<ip> launcher/build-and-install.sh`.\n\n"
                "## 3. Next\n")
        self.assertEqual(sorted(NEEDED), sorted(missing(page)))

    def test_a_missing_heading_is_reported(self):
        with self.assertRaises(AssertionError):
            section("## 1. Add your TV model\n\nnothing here\n")


if __name__ == "__main__":
    unittest.main()

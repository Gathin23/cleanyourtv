"""docs/devices/mtek-mk32cs1nb.md records an Android 7 CVTE set, and what it says is held to the tree.

#1664 is a Mtek MK32CS1NB (CVTE `TP.MT5510I.PB801`, MT5863, Android 7.0 / SDK 24) whose owner got the
launcher running from a local build and asked what to collect before cleaning the set up; the
maintainer asked for a `docs/devices/` note with the `minSdk 24` build steps, since the repo's launcher
stays at Android 8.0. A page like that is easy to leave behind: the launcher keeps changing, and a
reader who follows a stale list of API-26+ calls gets a build that crashes the way this one did.

So the claims are read out of their sources rather than copied in here:

  * the repo's `minSdk` out of launcher/app/build.gradle, so a note that stops naming it fails;
  * the four API-26+ calls out of the launcher's own Java, each with the file that makes it, so a call
    that moves or goes away is reported instead of silently dropped from the note;
  * the `java.time` files whose use is what the desugaring covers;
  * the extra name the launcher's `input_sources` route sends, out of MainActivity.openInput, because
    the note's point is that CVTE's app answers a different one;
  * the CVTE factory-menu route out of examples/super-general-cvte-t972/README.md, the board page that
    already documents it, so the two cannot drift apart;
  * AGENTS.md's CVTE rule that the stock launcher stays enabled, and PROMPT.md's `INSTALL_FAILED_OLDER_SDK`
    sentence the note defers to.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "mtek-mk32cs1nb.md"
INDEX = ROOT / "examples" / "README.md"
BUILD_GRADLE = ROOT / "launcher" / "app" / "build.gradle"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
CVTE_PAGE = ROOT / "examples" / "super-general-cvte-t972" / "README.md"
AGENTS = ROOT / "AGENTS.md"
PROMPT = ROOT / "PROMPT.md"

# What the note replaces: the file that makes each call, the call as the launcher's Java spells it, the
# name the note gives it, and the replacement its table gives. The call is searched for in that file:
# when it is no longer there, the note has a stale row and the test fails rather than passing quietly.
API_CALLS = {
    "MainActivity.java": ("getChangedPackages", "getChangedPackages", "installed package count"),
    "UiFont.java": ("getFont(", "Resources.getFont", "ResourcesCompat.getFont"),
    "WallpaperCaptions.java": ("toPath()", "toPath()", "BufferedReader"),
    "WallpaperPlayer.java": ("Config.HARDWARE", "Config.HARDWARE", "Android 8"),
}

# What the set is, as #1664 records it.
DEVICE = ["MK32CS1NB", "TP.MT5510I.PB801", "MT5863", "Android 7.0", "SDK 24", "20171230_042214",
          "com.cvte.tv.launcher"]

# The set writes its notes the way the page does: wrapped lines, so matching is on joined text.
def joined(text):
    return " ".join(text.split())


def note():
    return NOTE.read_text(encoding="utf-8")


def java_files():
    return {p.name: p.read_text(encoding="utf-8") for p in JAVA.glob("*.java")}


def missing(page, needles):
    """The needles the page does not carry, in the order they were asked for."""
    body = joined(page)
    return [needle for needle in needles if needle not in body]


class TheNoteItself(unittest.TestCase):
    def setUp(self):
        self.page = note()
        self.body = joined(self.page)

    def test_the_page_is_long_enough_to_be_a_note(self):
        lines = [line for line in self.page.splitlines() if line.strip()]
        self.assertGreaterEqual(len(lines), 25, "the device note has shrunk to a stub")
        self.assertGreaterEqual(self.page.count("\n## "), 4, "the note lost its sections")

    def test_it_names_the_set_and_its_firmware(self):
        self.assertEqual(missing(self.page, DEVICE), [],
                         "#1664's model, board, chip, version, build or stock launcher is gone")

    def test_the_repo_min_sdk_is_read_from_the_build(self):
        gradle = BUILD_GRADLE.read_text(encoding="utf-8")
        found = re.search(r"minSdk (\d+)", gradle)
        if found is None:
            self.fail("launcher/app/build.gradle no longer states its minSdk")
        sdk = found.group(1)
        # The note exists because the launcher stops at 26: an Android 7 set needs a fork.
        self.assertIn(f"minSdk {sdk}", self.body,
                      f"the note should say the repo's launcher is minSdk {sdk}")
        self.assertIn("launcher/app/build.gradle", self.body,
                      "the note should say where that minSdk is read")
        self.assertIn("INSTALL_FAILED_OLDER_SDK", self.body,
                      "the note should name what an Android 7 set answers")

    def test_it_defers_to_the_prompt_rule_instead_of_overriding_it(self):
        prompt = joined(PROMPT.read_text(encoding="utf-8"))
        self.assertIn("INSTALL_FAILED_OLDER_SDK", prompt,
                      "PROMPT.md no longer names the SDK refusal the note defers to")
        self.assertIn("PROMPT.md", self.body,
                      "the note should say the prompt sends an Android 7 set to the debloat half")

    def test_the_note_hands_out_no_build_artifact_and_no_uninstall(self):
        # A fork is source that others build; an APK in the tree is what AGENTS.md forbids.
        for banned in ("pm uninstall", ".apk", "adb install"):
            self.assertNotIn(banned, self.page, f"the note should not hand out {banned!r}")


class TheBuildStepsMatchTheLauncher(unittest.TestCase):
    def setUp(self):
        self.page = note()
        self.body = joined(self.page)
        self.java = java_files()

    def test_every_api_call_it_replaces_is_still_that_call_in_that_file(self):
        for name, (call, called, replacement) in API_CALLS.items():
            self.assertIn(name, self.java, f"the launcher no longer has {name}")
            self.assertIn(call, self.java[name],
                          f"{name} no longer makes the {call} call the note replaces; "
                          "update the note's table")
            self.assertIn(called, self.body, f"the note no longer names {called}")
            self.assertIn(name, self.body, f"the note no longer names {name}")
            self.assertIn(replacement, self.body,
                          f"the note no longer says what {called} was replaced with")

    def test_the_java_time_files_it_names_are_the_ones_that_import_it(self):
        importing = {name for name, text in self.java.items() if "java.time." in text}
        self.assertGreaterEqual(len(importing), 3, "the launcher stopped using java.time")
        named = [name for name in importing if name in self.body]
        self.assertGreaterEqual(len(named), 3,
                                "the note should name the launcher's java.time files: "
                                + ", ".join(sorted(importing)))
        for name in named:
            self.assertIn(name, importing,
                          f"the note names {name} as a java.time file and it is not one")

    def test_it_says_what_the_desugaring_is_for(self):
        self.assertIn("coreLibraryDesugaring", self.body,
                      "the note should name the switch the build needs")
        self.assertIn("desugar_jdk_libs", self.body,
                      "the note should name the desugaring dependency")
        self.assertIn("NoClassDefFoundError", self.body,
                      "the note should quote the crash the desugaring fixes")


class TheNotesClaimsAboutThisBoard(unittest.TestCase):
    def setUp(self):
        self.page = note()
        self.body = joined(self.page)

    def test_the_cvte_stock_launcher_stays_enabled(self):
        agents = joined(AGENTS.read_text(encoding="utf-8"))
        self.assertIn("com.cvte.tv.launcher", agents,
                      "AGENTS.md no longer states the CVTE stock-launcher rule")
        self.assertIn("keeps that launcher enabled", self.body,
                      "the note should carry AGENTS.md's CVTE rule")
        # Nothing in it may send a reader to disable the package on this board.
        self.assertNotRegex(self.page, r"pm disable-user[^\n]*com\.cvte\.tv\.launcher")
        self.assertNotIn("disable the stock launcher", self.body)

    def test_the_adb_route_is_the_one_the_cvte_board_page_gives(self):
        board = joined(CVTE_PAGE.read_text(encoding="utf-8"))
        route = [token for token in ("MENU", "1 1 4 7", "ADB ENABLE") if token in board]
        self.assertEqual(route, ["MENU", "1 1 4 7", "ADB ENABLE"],
                         "examples/super-general-cvte-t972 no longer records the factory-menu route")
        self.assertEqual(missing(self.page, route), [],
                         "the note and the CVTE board page should give the same way in")
        self.assertIn("super-general-cvte-t972", self.body,
                      "the note should point at the board page it takes the route from")

    def test_the_factory_menu_rule_is_stated(self):
        self.assertIn("AGENTS.md", self.body,
                      "the note should name the rule it is following for the factory menu")
        self.assertIn("test package", self.body,
                      "the note should say no test package in that menu is started")
        # The page sends nobody into a factory or test activity from a shell.
        self.assertNotRegex(self.page, r"am start[^\n]*(factory|hkcfac|factorymenu)")

    def test_the_inputs_extra_is_the_one_the_launcher_sends(self):
        source = java_files()["MainActivity.java"]
        self.assertIn('putExtra("input"', source,
                      "MainActivity.openInput no longer sends the source number as the extra 'input', "
                      "so the note's comparison with CVTE's extra is stale")
        self.assertEqual(missing(self.page, ["input_sources", "input_action",
                                             "MTK_EXTRA_INPUT_ID",
                                             "com.cvte.tv.androidsetting/.TvSettingActivity"]), [],
                         "the note should name both routes and CVTE's activity and extra")
        self.assertIn("extra named `input`", self.body,
                      "the note should say which extra the launcher's own route sends")


class TheIndexAndTheReader(unittest.TestCase):
    def test_the_note_is_linked_from_the_index_readers_use(self):
        index = INDEX.read_text(encoding="utf-8")
        self.assertIn("../docs/devices/mtek-mk32cs1nb.md", index,
                      "examples/README.md does not link the note, so nothing reaches it")

    def test_the_reader_reports_a_page_that_does_not_carry_the_claims(self):
        skeleton = "# Mtek MK32CS1NB\n\nAndroid 7.0.\n"
        self.assertEqual(missing(skeleton, DEVICE),
                         [token for token in DEVICE if token not in joined(skeleton)])
        self.assertTrue(missing(skeleton, ["minSdk 26"]), "the reader should report the missing minSdk")

    def test_the_reader_accepts_the_same_claims_wrapped(self):
        wrapped = "CE\n\nthe repo's launcher is\nminSdk 26\nand an Android 7 set answers\n" \
                  "INSTALL_FAILED_OLDER_SDK\n"
        self.assertEqual(missing(wrapped, ["minSdk 26", "INSTALL_FAILED_OLDER_SDK"]), [])


if __name__ == "__main__":
    unittest.main()

"""The page a TCL owner lands on when the remote's mic button dies after a launcher install.

#1869 is a TCL BeyondTV2 (board `rtd285o`, Android TV 11, `PPR1.180610.011`/`AR05`) whose owner traced
the voice button hop by hop: the button sends `KEYCODE_SEARCH`, `TclActivityManager` brings the activity
that owns Home to the front and `TclWinInjector` clicks that activity's own voice button, which is what
fires `ACTION_ASSIST` in the foreground and lets Android 11 hand katniss an audio stream. A background
`ACTION_ASSIST` - a service, an accessibility remap, `am start` - is refused. His first report said
"after replacing Home", and his own re-test narrowed it: changing which launcher owns Home
(`cmd package set-home-activity`, both directions) recorded normally, while installing or updating the
package that owns Home broke the button until the next reboot. The maintainer asked for a note for the
next owner of this set.

Two things are checked against the repository rather than asserted in prose:

  - the bullet sits in "## Working on the TV", after the mic-button bullet a reader arrives at first,
    and quotes the denial line the report recorded, so the log line is searchable on the page.
  - the model, board, build and variant it records are read out of
    `examples/tcl-beyondtv2-android-11/debloat.txt`'s own fingerprint, so the page cannot name a set the
    folder does not carry; and the bullet sends the reader to `com.google.android.katniss` before the
    reboot, which that folder's list really does disable - on this set the button is dead a second way,
    on purpose.

The folder's own record of the same unit is a separate PR (#1885), so nothing here edits the folder.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "TROUBLESHOOTING.md"
SECTION = "## Working on the TV"
HELPER_SECTION = "## Accessibility helper enabled but the remote button does nothing"
FOLDER = ROOT / "examples" / "tcl-beyondtv2-android-11"
LIST = FOLDER / "debloat.txt"
# The line the report recorded, verbatim: it is what someone who reads their own logcat will search for.
DENIAL = "Recording audio is not allowed for this invocation on R."
KATNISS = "com.google.android.katniss"


def section(text=None, heading=SECTION):
    """One section of the page, or an empty string when the heading is gone."""
    text = PAGE.read_text(encoding="utf-8") if text is None else text
    start = text.find(heading)
    if start == -1:
        return ""
    rest = text[start + len(heading):]
    end = re.search(r"^## ", rest, re.M)
    return rest if end is None else rest[:end.start()]


def bullets(text=None, heading=SECTION):
    """The section's bullets with their continuation lines joined: one string per bullet."""
    out = []
    for line in section(text, heading).splitlines():
        if line.startswith("- "):
            out.append(line[2:].strip())
        elif out and line.startswith("  ") and line.strip():
            out[-1] = out[-1] + " " + line.strip()
        elif not line.strip():
            continue
    return out


def bullet_naming(*needles, text=None):
    """The first bullet that names every needle, or None."""
    for b in bullets(text):
        if all(n in b for n in needles):
            return b
    return None


def folder_header(path=None):
    """(model, build, variant, board) out of the list's own fingerprint and first comment lines."""
    path = LIST if path is None else path
    header = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            break
        header.append(line)
    text = "\n".join(header)
    # <vendor>/<model>/<device>:<android>/<build>/<variant>:user/release-keys
    fingerprint = re.search(
        r"([^/\s]+)/([^/\s]+)/([^/\s:]+):(\d+)/([^/\s]+)/([^:\s]+):user/release-keys", text)
    if fingerprint is None:
        return None, None, None, None
    board = re.search(r"board\s+([A-Za-z0-9_]+)", text)
    return (fingerprint.group(2), fingerprint.group(5), fingerprint.group(6),
            board.group(1) if board else None)


class ThePageAnswersIt(unittest.TestCase):
    def test_the_section_has_bullets_to_read(self):
        found = bullets()
        self.assertGreaterEqual(len(found), 10,
                                "Working on the TV read back %d bullets: the reader is broken, not the "
                                "page" % len(found))

    def test_a_button_dead_after_a_launcher_install_is_explained(self):
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        self.assertIsNotNone(
            bullet,
            "nothing in Working on the TV says the remote's voice button comes back after one reboot "
            "when the Home package has just been installed or updated, so an owner reinstalls the "
            "launcher and stays stuck (#1869)")
        assert bullet is not None
        self.assertIn("1869", bullet, "the bullet has to name the issue its evidence came from")

    def test_the_bullet_records_the_unit_the_folder_carries(self):
        model, build, variant, board = folder_header()
        self.assertEqual(model, "BeyondTV2",
                         "examples/tcl-beyondtv2-android-11/debloat.txt no longer carries the BeyondTV2 "
                         "fingerprint this bullet describes")
        self.assertIsNotNone(board, "the list header no longer names its board")
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        # Read out of the folder rather than copied in: a page that names a build the folder does not
        # carry sends the next owner to a set this is not about.
        for token, what in ((build, "build"), (variant, "variant"), (board, "board")):
            self.assertIn(token, bullet,
                          "the bullet no longer names the %s the folder's list was written on (%r)"
                          % (what, token))
        self.assertIn("examples/tcl-beyondtv2-android-11", bullet,
                      "the bullet has to point at the folder for the same model and build")
        self.assertTrue(FOLDER.is_dir(),
                        "the folder the bullet points at is gone")

    def test_the_bullet_says_why_the_button_goes_through_home(self):
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        for needle in ("KEYCODE_SEARCH", "ACTION_ASSIST"):
            self.assertIn(needle, bullet,
                          "the reason the reboot is needed is the firmware's own chain, so the bullet "
                          "has to name %r: without it the advice reads as superstition" % needle)
        self.assertIn("not the Assistant's", bullet,
                      "the bullet has to separate the firmware's path from VoiceInteractionService, "
                      "which is what a reader would otherwise go and check")

    def test_the_bullet_quotes_the_denial_the_report_recorded(self):
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        self.assertIn(DENIAL, bullet,
                      "the bullet no longer quotes the log line the report recorded, so a reader who "
                      "searches their own logcat will not find the page")

    def test_the_bullet_says_the_reboot_is_the_way_out(self):
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        self.assertIn("reboot once", bullet.lower(),
                      "the one step that fixes it has to be in the bullet, not only in its heading")
        self.assertIn("no fix", bullet.lower(),
                      "the report's point was that installing the launcher again is not the answer, and "
                      "that is what the owner reaches for")

    def test_the_bullet_says_changing_home_is_not_the_cause(self):
        # The correction the reporter made in his own comment: this is about the package being
        # installed or updated, not about which launcher owns Home.
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        self.assertIn("cmd package set-home-activity", bullet,
                      "the bullet has to name the test that cleared 'changing Home', or a reader will "
                      "still read the failure as this launcher's fault")
        self.assertIn("not what breaks it", bullet,
                      "the bullet has to say that switching launchers was measured and is fine")

    def test_the_bullet_sends_the_reader_to_katniss_first(self):
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        self.assertIn(KATNISS, bullet,
                      "on this board the button needs the Assistant, and the lists that turn voice off "
                      "disable it - the bullet has to say so before the reboot advice")
        listed = [line for line in LIST.read_text(encoding="utf-8").splitlines()
                  if line.strip() and not line.strip().startswith("#")]
        self.assertTrue(any(line.split("#")[0].strip() == KATNISS for line in listed),
                        "examples/tcl-beyondtv2-android-11 no longer disables %s, which is the second "
                        "reason the bullet gives for a dead button on this set" % KATNISS)

    def test_the_bullet_sits_after_the_mic_bullet(self):
        found = bullets()
        mic_at = [i for i, b in enumerate(found) if "mic button does nothing" in b]
        voice_at = [i for i, b in enumerate(found) if "BeyondTV2" in b and "reboot" in b]
        self.assertEqual(len(mic_at), 1,
                         "the bullet about a mic button that does nothing is not in the section exactly "
                         "once; the new bullet is the same symptom with another cause")
        self.assertTrue(voice_at, "the new bullet did not read back out of the section")
        self.assertLess(mic_at[0], voice_at[0],
                        "a reader arrives at the mic button bullet first, so the new one has to follow it")

    def test_the_bullet_points_at_the_helper_section_for_injected_keys(self):
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        self.assertIn("input keyevent", bullet,
                      "an injected key is not a test of this button, and the bullet has to say so")
        self.assertIn(HELPER_SECTION[3:], bullet,
                      "the bullet has to name the section that explains injected keys, not just say "
                      "'below'")
        self.assertIn(HELPER_SECTION, PAGE.read_text(encoding="utf-8"),
                      "the accessibility-helper section the bullet points at is gone")

    def test_the_bullet_offers_no_uninstall(self):
        bullet = bullet_naming("BeyondTV2", "reboot", "installed")
        assert bullet is not None
        self.assertNotRegex(bullet.lower(), r"uninstall",
                            "AGENTS.md keeps uninstalling out of every page: the bullet may only say to "
                            "reboot")


class TheFolderItNames(unittest.TestCase):
    def test_the_folder_is_still_the_one_the_table_lists(self):
        row = json.loads((FOLDER / "tv.json").read_text(encoding="utf-8"))
        model, build, variant, _ = folder_header()
        self.assertIn(model, row["tv"],
                      "the tv.json row no longer names the model its list was written on")
        self.assertEqual(row["os"], "Android TV 11")


class TheReaderItself(unittest.TestCase):
    """The helpers above, on pages written here: so a quiet page test cannot be a reader that saw nothing."""

    def test_a_wrapped_bullet_is_read_as_one(self):
        text = (SECTION + "\n\n- **One** thing that runs over\n  two lines.\n- **Two** other.\n")
        self.assertEqual(bullets(text), ["**One** thing that runs over two lines.", "**Two** other."])

    def test_a_bullet_in_another_section_is_not_read(self):
        text = (SECTION + "\n\n- here\n\n## Another\n\n- there\n")
        self.assertEqual(bullets(text), ["here"])

    def test_a_page_without_the_section_answers_nothing(self):
        self.assertEqual(bullets("# Troubleshooting\n\n- nothing\n"), [])
        self.assertEqual(section("# Troubleshooting\n\n- nothing\n"), "")

    def test_a_bullet_naming_only_some_needles_is_not_matched(self):
        text = (SECTION + "\n\n- **BeyondTV2** and a reboot, and nothing about the install.\n")
        self.assertIsNone(bullet_naming("BeyondTV2", "reboot", "installed", text=text))
        self.assertIsNotNone(bullet_naming("BeyondTV2", "reboot", text=text))

    def test_the_fingerprint_reader_takes_the_model_build_and_variant(self):
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "debloat.txt"
            path.write_text("# One, board otherboard,\n"
                            "# build B.2, fingerprint X/Model1/Model1:11/B.2/V9:user/release-keys.\n"
                            "#\n"
                            "com.example.one\n", encoding="utf-8")
            self.assertEqual(folder_header(path), ("Model1", "B.2", "V9", "otherboard"))
            path.write_text("com.example.one\n# fingerprint X/Model1/Model1:11/B.2/V9:user/release-keys\n",
                            encoding="utf-8")
            self.assertEqual(folder_header(path), (None, None, None, None),
                             "a fingerprint below the first package line is not a header")


if __name__ == "__main__":
    unittest.main()

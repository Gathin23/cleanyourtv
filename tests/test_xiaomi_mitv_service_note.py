"""`mitv.service` disabled on China-market Xiaomi firmware takes the Settings app down with it (#1310).

Xiaomi's own settings app (`com.xiaomi.mitv.settings`) reads the TV's hardware configuration through the
`TvService` system service that `mitv.service` registers. With the package disabled the middleware
retries ten times half a second apart, gives up and returns the literal string `NULL`, and
`SoundView.init` throws `NumberFormatException` on it: Settings freezes for 20-30 s and then crashes its
Sound page while the rest of the TV keeps working. The report also carries the trap that cost the
reporter a day - `pm enable` alone changes nothing, because the service is registered at boot, and the
reboot that would register it closes network ADB on that firmware, whose ADB switch lives inside the
broken Settings app.

`examples/xiaomi-mitv-aesp0` is the only list here that disables the package and its comment said only
what the package was for; the China-market lists that name it said "do not touch it" and no more. These
tests keep the note a reader has to be able to act on - the device, the logcat signature, the commands
that put the service back, the two ways out and the trap - and keep every list that names the package in
either direction pointing at it, so the line cannot be carried to a CN set without the warning.

The walk reads every list a folder ships, not only its `debloat.txt`: eight folders here carry a second
list (`debloat-keep-cast.txt` in two, `debloat-65u7esg.txt`, `debloat-32-inch.txt`, `debloat-extra.txt`,
`debloat-display-only.txt` in two, `debloat-75t8c-eu.txt`), `tools/disable.sh` applies each like the
first, and a `mitv.service` line moved into one would have been read by nothing.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
EXAMPLES = ROOT / "examples"
HEADING = "## Xiaomi CN firmware: Settings freezes or crashes after the debloat"
ISSUE = "https://github.com/Gathin23/cleanyourtv/issues/1310"
PACKAGE = "mitv.service"
# The sentence the China-market lists use for "this one stays on".
DO_NOT_TOUCH = "mitv.service is the core MiTV service: do not touch it"


def sections(text):
    """`## ` heading -> the body under it, up to the next `## ` heading."""
    found, heading, body = {}, None, []
    for line in text.split("\n"):
        if line.startswith("## "):
            if heading is not None:
                found[heading] = "\n".join(body)
            heading, body = line.strip(), []
        elif heading is not None:
            body.append(line)
    if heading is not None:
        found[heading] = "\n".join(body)
    return found


def slug(heading):
    """The anchor GitHub gives a heading: lower case, punctuation dropped, spaces to dashes."""
    text = heading.lstrip("#").strip().lower()
    return re.sub(r"[^\w\- ]", "", text).replace(" ", "-")


def list_lines(path):
    """(line number, package, comment) for every active line of a debloat.txt.

    Parsed the way tools/disable.sh parses a list: everything from the first `#` is a comment, and the
    package is the first field of what is left, so a commented-out line is not an entry.
    """
    out = []
    for number, line in enumerate(path.read_text(encoding="utf-8").split("\n"), 1):
        code, _, comment = line.partition("#")
        fields = code.split()
        if fields:
            out.append((number, fields[0], comment.strip()))
    return out


def lists(root=EXAMPLES):
    """Every list under `examples`, not only each folder's debloat.txt.

    Eight folders ship a second list beside it (debloat-keep-cast.txt in two, debloat-65u7esg.txt,
    debloat-32-inch.txt, debloat-extra.txt, debloat-display-only.txt in two, debloat-75t8c-eu.txt),
    and tools/disable.sh applies one like the first: a second list that disables `mitv.service` is the
    same mistake as a `debloat.txt` that does, and one that says to leave it alone carries the same
    duty to say why.
    """
    return sorted(Path(root).glob("*/debloat*.txt"))


ANCHOR = "#" + slug(HEADING)


def anchor_missing(paths):
    """[(path, line number or None)] for the lists that need the note's anchor and do not carry it.

    Two shapes: a line that disables `mitv.service`, and a list that carries the "do not touch it"
    sentence. The number is the disabling line, or None for the sentence.
    """
    problems = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for number, package, _ in list_lines(path):
            if package == PACKAGE and ANCHOR not in text:
                problems.append((path, number))
        if DO_NOT_TOUCH in text and ANCHOR not in text:
            problems.append((path, None))
    return problems


def relative(path):
    return path.relative_to(ROOT).as_posix()


class TheNoteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = TROUBLE.read_text(encoding="utf-8")
        cls.note = sections(cls.text).get(HEADING, "")

    def test_the_section_is_there_once(self):
        self.assertEqual(self.text.count(HEADING), 1,
                         f"docs/TROUBLESHOOTING.md should carry '{HEADING}' once")
        self.assertTrue(self.note.strip(), "the section is empty")

    def test_names_the_device_and_the_report(self):
        for needed in ("MiTV-ASTP0", "mulan", "Android 9", "com.xiaomi.mitv.settings", ISSUE):
            with self.subTest(needed=needed):
                self.assertIn(needed, self.note)

    def test_names_the_crash_and_what_returns_null(self):
        # A reader searching their own logcat needs the exception and the middleware's line, not a story.
        for needed in ("NumberFormatException", 'For input string: "NULL"', "SoundView.init",
                       "ConfigurationManager", "service = null", "TvService"):
            with self.subTest(needed=needed):
                self.assertIn(needed, self.note)

    def test_gives_the_commands_that_put_the_service_back(self):
        for command in ("pm enable mitv.service",
                        "am startservice -n mitv.service/.TvServer",
                        "service list | grep -i tvservice"):
            with self.subTest(command=command):
                self.assertIn(command, self.note)

    def test_both_ways_out_and_the_trap_are_named(self):
        # Enabling the package alone is not enough: the service is registered at boot, so the note has to
        # name the restart and the on-demand start, and warn that a restart closes ADB on that firmware.
        self.assertIn("reboot", self.note)
        self.assertIn("am startservice", self.note)
        self.assertIn("ADB", self.note)


class EveryListThatNamesThePackageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.texts = {path: path.read_text(encoding="utf-8") for path in lists()}

    def test_the_sweep_reads_the_xiaomi_lists(self):
        named = [p for p, text in self.texts.items() if PACKAGE in text]
        self.assertGreaterEqual(len(named), 15,
                                "the sweep is meant to read the Xiaomi lists that name this package")

    def test_only_one_list_disables_it(self):
        disabling = [(path, number) for path in self.texts
                     for number, package, _ in list_lines(path) if package == PACKAGE]
        self.assertEqual([relative(path) for path, _ in disabling], ["examples/xiaomi-mitv-aesp0/debloat.txt"],
                         "a second list disables it: that set needs a test on a CN-firmware TV first")

    def test_a_list_that_says_do_not_touch_it_says_why(self):
        keeping = [path for path, text in self.texts.items() if DO_NOT_TOUCH in text]
        self.assertGreaterEqual(len(keeping), 3,
                                "the China-market lists carry that sentence; the sweep found fewer")

    def test_every_list_that_names_it_points_at_the_note(self):
        missing = anchor_missing(lists())
        self.assertFalse(missing, "\n".join(
            f"{relative(path)}{':' + str(number) if number else ''} names {PACKAGE} without saying what "
            f"disabling it costs: link docs/TROUBLESHOOTING.md{ANCHOR}" for path, number in missing))

    def test_the_sweep_reads_a_second_list(self):
        # The rule is only as wide as the walk: a folder's second list is applied like its first, so a
        # line carried there is the same mistake as one in debloat.txt.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "acme-tv").mkdir()
            (root / "acme-tv" / "debloat.txt").write_text(f"{PACKAGE}   # see {ANCHOR}\n", encoding="utf-8")
            extra = root / "acme-tv" / "debloat-extra.txt"
            extra.write_text(f"{PACKAGE}   # core MiTV service\n", encoding="utf-8")
            self.assertIn(extra, lists(root))
            self.assertEqual([(path.name, number) for path, number in anchor_missing(lists(root))],
                             [("debloat-extra.txt", 1)])
            extra.write_text(f"# {DO_NOT_TOUCH}, see {ANCHOR}\n", encoding="utf-8")
            self.assertEqual(anchor_missing(lists(root)), [])
            extra.write_text(f"# {DO_NOT_TOUCH}\n", encoding="utf-8")
            self.assertEqual([(path.name, number) for path, number in anchor_missing(lists(root))],
                             [("debloat-extra.txt", None)])


class ReaderTest(unittest.TestCase):
    def test_a_section_stops_at_the_next_h2_but_keeps_h3(self):
        text = "\n".join(["## One", "a", "### under one", "b", "## Two", "c"])
        found = sections(text)
        self.assertEqual(found["## One"], "a\n### under one\nb")
        self.assertEqual(found["## Two"], "c")
        self.assertNotIn("c", found["## One"])

    def test_a_page_without_sections_reads_as_empty(self):
        self.assertEqual(sections("intro\nmore intro\n"), {})
        self.assertEqual(sections("## Only\nbody"), {"## Only": "body"})

    def test_the_slug_drops_the_colon_and_keeps_words(self):
        self.assertEqual(slug("## Xiaomi CN firmware: Settings freezes or crashes after the debloat"),
                         "xiaomi-cn-firmware-settings-freezes-or-crashes-after-the-debloat")
        self.assertEqual(slug("## A, B and C"), "a-b-and-c")
        self.assertEqual(slug("## `mitv.service` (PERSISTENT)"), "mitvservice-persistent")

    def test_a_commented_out_package_is_not_an_entry(self):
        import tempfile
        body = "\n".join(["# header", PACKAGE + "   # comment stays", "#" + PACKAGE, "  com.example.tv  "])
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "debloat.txt"
            path.write_text(body, encoding="utf-8")
            self.assertEqual([(number, package) for number, package, _ in list_lines(path)],
                             [(2, PACKAGE), (4, "com.example.tv")])

    def test_the_comment_after_a_line_is_the_undocumented_tail(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "debloat.txt"
            path.write_text(PACKAGE + "    # MiTV service framework\n", encoding="utf-8")
            self.assertEqual(list_lines(path), [(1, PACKAGE, "MiTV service framework")])


if __name__ == "__main__":
    unittest.main()

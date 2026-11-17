"""The lists that disable the remote mic's bridge say so, and a reader can find all of them (#1441).

`com.google.android.tv.remote.service` carries the physical remote's microphone, not only the phone-app
remote. On a TCL 43T6C (G10, Realtek, Google TV 12, firmware `STT2.230203.001`) a mic press starts
`AtvSearchAssistantService` inside it. With the package disabled the button did nothing, and re-enabling
only that package brought it back (#1441). A TCL 55Q651G with the same product and AS50 build had the same
dead mic until the package was re-enabled (#1547). tests/test_g10_mic_bridge.py pins the G10 list, which
keeps the package now.

Two places still pointed the other way:

- examples/tcl-55s470g/debloat.txt is on that same G10 build (AS50). It disables the package by choice,
  together with voice, which is fine, but its note said "the physical remote is unaffected". An owner who
  comments out katniss to get voice back is told this line does not matter, and the mic stays dead. The
  line stays active so the owner's choice stands; only its note changes.
- The troubleshooting bullet named the folders that still disable the package as if the set were
  complete, and five lists added after it disable it too. Making every new list edit the bullet would break
  CONTRIBUTING.md's "Change nothing outside your folder", so the bullet also gives the grep that always
  finds the current set. These tests run that grep's pattern over the lists instead of trusting it.

The list and the bullet are read with the helpers in tests/test_g10_mic_bridge.py, by identifier: the
bullet's prose can be rewritten without touching this file.

The grep and the walk around it read `examples/*/debloat.txt` until now, and a folder may ship a second
list beside it: eight do (debloat-keep-cast.txt twice, debloat-65u7esg.txt, debloat-32-inch.txt,
debloat-extra.txt, debloat-display-only.txt twice, debloat-75t8c-eu.txt), each applied with the same
tools/disable.sh, and the EU 75T8C one already carries the mirror of this warning on its katniss line
("comment out to keep the mic button"). A second list that turns the bridge off answered "not in the set"
to the reader, and the walk that models the grep could not see it either, because both stopped at the
folder's debloat.txt. The glob is now `examples/*/debloat*.txt` in both, and the glob is read out of the
page rather than written down here, so a page that goes back to the narrow one fails on a stand-in tree.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
import test_g10_mic_bridge as mic  # noqa: E402  (a module, so its tests do not run twice)

PACKAGE = mic.PACKAGE
DOC = ROOT / "docs" / "TROUBLESHOOTING.md"
# The command the bullet hands out: the pattern, then the lists it reads. Its pattern is read as a Python
# regex, which is the same as grep's basic regex for a pattern made of `^`, `.` and plain characters. The
# glob is read too, because it decides which lists the command prints: a folder may ship a second list
# beside debloat.txt (debloat-keep-cast.txt, debloat-display-only.txt and the rest), tools/disable.sh
# applies it like the first, and a glob that stops at debloat.txt never looks at one.
GREP = re.compile(r"`grep -l '([^'`]+)' (examples/[^`]+)`")
# Every list a folder may ship, the second ones included. tools/disable.sh and restore.sh take any of them.
LISTS_GLOB = "examples/*/debloat*.txt"
# A note that says what the package does for the physical remote's mic.
MIC_NOTE = re.compile(r"\bmic\b|AtvSearchAssistantService|#1441")


def lists(root=ROOT):
    """Every list the tree ships, a folder's second list included."""
    return sorted(Path(root).glob(LISTS_GLOB))


def read(path):
    return path.read_text(encoding="utf-8")


def write(root, relative, text):
    """A file under root, its folders made; returns the path."""
    target = Path(root) / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    return target


def header(text):
    """The comment block above a list's first package line, where the model and build are named."""
    lines = []
    for line in text.splitlines():
        if line.split("#", 1)[0].strip():
            break
        lines.append(line)
    return "\n".join(lines)


def on_g10_stt2(text):
    head = header(text)
    return bool(re.search(r"\bdevice G10\b", head)) and "STT2.230203.001" in head


def disablers():
    """The folders whose list disables the package on an active line."""
    return {path.parent.name for path in lists() if PACKAGE in mic.active_packages(read(path))}


def bullet(doc):
    """The bullet from its list of folders to its end, with the line wraps joined."""
    start = doc.index(mic.ANCHOR)
    tail = doc[start:]
    end = tail.find("\n- ")
    return " ".join(tail[: end if end >= 0 else len(tail)].split())


def grep_finds(pattern, glob, root=ROOT):
    """What `grep -l '<pattern>' <glob>` prints, as folder names, run over the tree at root."""
    rx = re.compile(pattern)
    found = set()
    for path in sorted(Path(root).glob(glob)):
        if any(rx.search(line) for line in read(path).splitlines()):
            found.add(path.parent.name)
    return found


class G10ListsSayTheLineCarriesTheMic(unittest.TestCase):
    def test_the_g10_build_is_read_from_the_headers(self):
        found = {path.parent.name for path in lists() if on_g10_stt2(read(path))}
        self.assertLessEqual({"tcl-g10-google-tv", "tcl-55s470g"}, found, f"read {sorted(found)}")

    def test_an_active_line_on_that_build_names_the_mic(self):
        for path in lists():
            text = read(path)
            if not on_g10_stt2(text):
                continue
            lines = text.splitlines()
            # A comment line that names the package (a header note, as in panavox-google-tv) counts too.
            elsewhere = [line for line in lines if line.lstrip().startswith("#") and PACKAGE in line]
            for number, line in enumerate(lines, 1):
                code, _, note = line.partition("#")
                if code.split()[:1] != [PACKAGE]:
                    continue
                with self.subTest(example=path.parent.name, line=number):
                    self.assertNotIn(
                        "unaffected",
                        note,
                        "on this build the package carries the remote's mic (#1441), so the note should not "
                        "say the physical remote is unaffected",
                    )
                    self.assertTrue(
                        any(MIC_NOTE.search(n) for n in [note] + elsewhere),
                        "the note should say the line also carries the remote's mic, so an owner who wants "
                        "voice back comments it out with katniss",
                    )


class TheBulletLeadsToEveryListThatDisablesIt(unittest.TestCase):
    def setUp(self):
        self.doc = read(DOC)
        self.text = bullet(self.doc)
        self.command = GREP.search(self.text)

    def test_every_list_that_disables_it_is_named_or_found_by_the_grep(self):
        missing = disablers() - set(mic.disabler_list(self.doc))
        if self.command:
            missing -= grep_finds(self.command.group(1), self.command.group(2))
        self.assertEqual(
            [],
            sorted(missing),
            "these lists disable the package but the bullet neither names them nor gives a grep that finds them",
        )

    def test_the_grep_finds_exactly_the_lists_that_disable_it(self):
        if not self.command:
            self.skipTest("the bullet gives no grep command")
        self.assertEqual(sorted(disablers()),
                         sorted(grep_finds(self.command.group(1), self.command.group(2))))

    def test_the_grep_reads_a_second_list_beside_debloat_txt(self):
        # The bullet says its grep "prints the current set", and a folder may ship a second list that
        # disable.sh applies like the first: examples/tcl-65c7k-google-tv/debloat-75t8c-eu.txt, the two
        # Sony display-only lists and the rest. A glob that stops at debloat.txt prints a set that is
        # missing them the first time one of them carries the line.
        if not self.command:
            self.skipTest("the bullet gives no grep command")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "examples/first/debloat.txt", f"# TV\n{PACKAGE}\n")
            write(root, "examples/second/debloat-display-only.txt", f"# TV\n{PACKAGE}\n")
            found = grep_finds(self.command.group(1), self.command.group(2), root)
            self.assertEqual({"first", "second"}, found,
                             f"the bullet's grep reads {self.command.group(2)!r}, so it prints {sorted(found)} "
                             "where the second list carries the line too")


class EveryListIsReadTest(unittest.TestCase):
    """The walk above reads debloat.txt alone if its glob goes back to `*/debloat.txt`."""

    def test_a_second_list_is_one_of_the_lists_the_walk_reads(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "examples/x/debloat.txt", "# TV\ncom.example.one\n")
            second = write(root, "examples/x/debloat-extra.txt", f"# TV\n{PACKAGE}\n")
            self.assertIn(second, lists(root))

    def test_the_tree_ships_second_lists_the_walk_has_to_read(self):
        # A floor: a glob that reads nothing passes every test above.
        shipped = lists()
        self.assertGreaterEqual(len(shipped), 400, f"read {len(shipped)} lists")
        second = [p for p in shipped if p.name != "debloat.txt"]
        self.assertGreaterEqual(len(second), 8, f"read {len(second)} second lists")
        self.assertIn(ROOT / "examples" / "tcl-65c7k-google-tv" / "debloat-75t8c-eu.txt", shipped)


if __name__ == "__main__":
    unittest.main()

"""One package, one pin, across the requirement files that share `tools/venv`.

`tools/requirements.txt` is what cleaning a TV needs, and the two files beside it are optional extras
installed into that same venv: `requirements-perf.txt` (the perf tools and the three scripts that draw the
README card) and `requirements-photos.txt` (`tools/wallpaper_prep.py`). A reader can be handed both -
`docs/workflows/theme.md` says the picture, card and wallpaper checks need the perf file, and `README.md`'s
"your own photos as the background" installs the photos one - and two pins of one package disagreeing make
the pair uninstallable: pip resolves the two files together and refuses the whole command, so the reader
gets neither extra.

    $ tools/venv/bin/pip install -r tools/requirements-perf.txt -r tools/requirements-photos.txt
    ERROR: Cannot install pillow==11.3.0 and pillow==13.0.0 because these package versions have
    conflicting dependencies.
    ERROR: ResolutionImpossible: ...

Installed one after the other instead, the venv keeps whichever file ran last, so which Pillow a reader ends
up with is the order they typed. A package can be in one file, or in both with the same pin.
"""
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
# A requirement line: the name, an optional [extra], and the specifier run that follows it.
LINE = re.compile(r"^(?P<name>[A-Za-z0-9][A-Za-z0-9._-]*)\s*(?:\[[^\]]*\])?\s*(?P<spec>[<>=!~][^;#]*)?\s*$")


def pins(folder):
    """{package: [(specifier, file name, line number)]}, one entry per requirement line in the tree."""
    found = {}
    for path in sorted(Path(folder).glob("requirements*.txt")):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            line = line.split("#", 1)[0].split(";", 1)[0].strip()
            if not line:
                continue
            match = LINE.match(line)
            if not match:
                continue
            found.setdefault(match.group("name").lower(), []).append(
                ((match.group("spec") or "").replace(" ", ""), path.name, number))
    return found


def disagreements(folder):
    """Every package two requirement files pin differently, as lines a reader can act on."""
    found = {}
    for name, entries in pins(folder).items():
        specs = {spec for spec, _, _ in entries}
        if len(specs) > 1:
            found[name] = [f"{file}:{number}: {name}{spec}" for spec, file, number in entries]
    return found


class PinsTest(unittest.TestCase):
    def test_no_package_is_pinned_two_ways(self):
        found = disagreements(TOOLS)
        self.assertEqual({}, found, "\n".join(
            ["two requirement files pin one package differently, so installing them together is refused:"]
            + [line for lines in found.values() for line in lines]))


class ReaderTest(unittest.TestCase):
    """A stand-in tools/ folder, so the rule is judged on both shapes without this tree's files."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.folder = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def write(self, name, *lines):
        (self.folder / name).write_text("\n".join(lines) + "\n", encoding="utf-8")

    def test_two_pins_of_one_package_are_reported(self):
        self.write("requirements-perf.txt", "# optional tools", "pillow==11.3.0")
        self.write("requirements-photos.txt", "pillow==13.0.0", "pillow-heif==1.8.1")
        found = disagreements(self.folder)
        self.assertEqual(["pillow"], list(found))
        self.assertEqual(["requirements-perf.txt:2: pillow==11.3.0",
                          "requirements-photos.txt:1: pillow==13.0.0"], found["pillow"])

    def test_the_same_pin_twice_is_quiet(self):
        self.write("requirements.txt", "adb-shell==0.4.4")
        self.write("requirements-perf.txt", "perfetto==0.58.2")
        self.write("requirements-photos.txt", "pillow==13.0.0", "adb-shell==0.4.4")
        self.assertEqual({}, disagreements(self.folder))

    def test_a_package_under_another_specifier_is_reported(self):
        # `>=` in one file and `==` in another is the same disagreement to pip.
        self.write("requirements.txt", "cryptography>=46.0.7")
        self.write("requirements-perf.txt", "cryptography==46.0.7")
        self.assertEqual(["cryptography"], list(disagreements(self.folder)))

    def test_a_marker_and_a_comment_do_not_hide_a_pin(self):
        self.write("requirements-perf.txt", "pillow==11.3.0  # the card scripts")
        self.write("requirements-photos.txt", "pillow==13.0.0 ; python_version >= '3.10'")
        found = disagreements(self.folder)
        self.assertEqual(["pillow"], list(found))
        self.assertEqual("requirements-perf.txt:1: pillow==11.3.0", found["pillow"][0])

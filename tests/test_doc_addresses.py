"""Pages and lists give `<tv ip>` or `192.168.1.50`, never the LAN address of the set they were written on.

AGENTS.md ("Hard rules") keeps IP addresses out of lists and docs, and CODE_OF_CONDUCT.md counts them as
private information. Five TV pages and lists, and the two scripts in one of those folders, still gave
the address of the contributor's own set, mostly in commands a reader pastes: the Philips TA7 Undo
among them, which a reader who pastes it unchanged points at another host. One page also gave a
Windows user name in a `JAVA_HOME` path, and docs/REMOTE.md an example address other than the one the
rule names. These tests keep the lists, the TV pages and the guides on the placeholders.

What is not a TV's address: a route such as `192.168.1.0/24` (the subnet a helper advertises in
docs/REMOTE.md). The add-on pages are not read: the address they show is the reader's own server
(Pi-hole, Home Assistant), given as an example.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
# Lists and TV pages, the guides and the themes; the top-level pages and tools/*.md are read too.
FOLDERS = ("examples", "docs", "themes")
SUFFIXES = (".md", ".txt", ".json", ".sh", ".ps1")
EXAMPLE = "192.168.1.50"

# An RFC 1918 IPv4 address, not part of a longer dotted number such as a firmware build; a full stop
# after it ends the sentence.
PRIVATE = re.compile(
    r"(?<![\w.])(?:192\.168|10\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}(?!\d|\.\d)")
# A home folder with a person's name in it; `<user name>` and the shared folders are fine.
HOME_FOLDER = re.compile(
    r"(?:\b[A-Za-z]:[\\/]|(?<![\w.])/)Users[\\/](?!<|Shared\b|Public\b|Default\b)[^\\/\s'\"`]+")


def pages(root):
    """Every page, list and script a reader copies a command from, in a stable order."""
    root = Path(root)
    found = [path for path in root.glob("*.md")]
    found += [path for path in (root / "tools").glob("*.md")]
    for folder in FOLDERS:
        found += [path for path in (root / folder).rglob("*")
                  if path.is_file() and path.suffix in SUFFIXES]
    return sorted(found)


def addresses(line):
    """The LAN addresses in one line that name a host, leaving out the example address and routes."""
    out = []
    for match in PRIVATE.finditer(line):
        if match.group(0) == EXAMPLE:
            continue
        if match.group(0).endswith(".0") and line[match.end():match.end() + 1] == "/":
            continue  # a route: the network address with its prefix length
        out.append(match.group(0))
    return out


def problems(root, find):
    """`path:line: what` for every match of `find` in the pages below root."""
    root = Path(root)
    out = []
    for path in pages(root):
        text = path.read_text(encoding="utf-8", errors="replace")
        for number, line in enumerate(text.split("\n"), 1):
            for what in find(line):
                out.append("%s:%d: %s" % (path.relative_to(root).as_posix(), number, what))
    return out


class ReaderTest(unittest.TestCase):
    def test_a_tv_address_is_found(self):
        self.assertEqual(["192.168.7.21"], addresses("TV_HOST=192.168.7.21 tools/restore.sh x"))
        self.assertEqual(["10.0.0.37"], addresses("`adb connect 10.0.0.37:5555`"))
        self.assertEqual(["172.20.1.4"], addresses("$env:TV_HOST='172.20.1.4'"))
        self.assertEqual(["192.168.4.33"], addresses("adb pair 192.168.4.33:<pairing port> <code>"))
        self.assertEqual(["192.168.0.23"], addresses("The TV answered at 192.168.0.23."))
        self.assertEqual(["10.0.0.37"], addresses("then connect to 10.0.0.37..."))

    def test_the_example_address_routes_and_builds_are_not(self):
        self.assertEqual([], addresses("export TV_HOST=192.168.1.50"))
        self.assertEqual([], addresses("TV_HOST=192.168.1.50."))
        self.assertEqual([], addresses("tailscale set --advertise-routes=192.168.1.0/24"))
        self.assertEqual([], addresses("`--advertise-routes=10.0.0.0/8`"))
        self.assertEqual([], addresses("firmware V8-R851T02-LF1V310.10.0.1.2 build"))
        self.assertEqual([], addresses("build 10.0.0.1.5 of the firmware"))
        self.assertEqual([], addresses("Tailscale's DNS (100.100.100.100)"))
        self.assertEqual([], addresses("both `192.168.1.x`, say"))

    def test_a_home_folder_is_found_and_a_placeholder_is_not(self):
        self.assertTrue(HOME_FOLDER.search(r"$env:JAVA_HOME='C:\Users\Someone\.jdks\jbr-17'"))
        self.assertTrue(HOME_FOLDER.search("JAVA_HOME=/Users/someone/jdk17"))
        self.assertFalse(HOME_FOLDER.search("sdk.dir=C:/Users/<user name>/AppData/Local/Android/Sdk"))
        self.assertFalse(HOME_FOLDER.search(r"$env:JAVA_HOME='<path to JDK 17>'"))
        self.assertFalse(HOME_FOLDER.search("/Users/Shared/cleanyourtv"))


class DocAddressesTest(unittest.TestCase):
    def test_pages_are_read(self):
        read = {path.relative_to(ROOT).as_posix() for path in pages(ROOT)}
        for page in ("README.md", "docs/REMOTE.md", "examples/philips-google-tv-ta7/README.md",
                     "examples/skyworth-swtv-24ae-4k-nf/debloat.txt"):
            self.assertIn(page, read)
        self.assertIn(".sh", SUFFIXES, "a folder's own scripts are read too")

    def test_no_page_gives_the_address_of_a_real_set(self):
        self.assertEqual([], problems(ROOT, addresses),
                         "use <tv ip>, or %s where the page already gives an example address" % EXAMPLE)

    def test_no_page_gives_a_persons_home_folder(self):
        self.assertEqual([], problems(ROOT, lambda line: HOME_FOLDER.findall(line)),
                         "use a placeholder such as <path to JDK 17> or <user name>")


if __name__ == "__main__":
    unittest.main()

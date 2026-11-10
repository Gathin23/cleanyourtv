"""No page gives a MAC address or an Android ID: AGENTS.md names both beside the IP addresses it keeps out.

AGENTS.md ("Hard rules") asks for "no secrets or identifiers in commits, lists or screenshots: no IP
addresses, Wi-Fi names, account emails, serial numbers, Android IDs, MAC addresses or ADB keys", and
CODE_OF_CONDUCT.md counts a device's identifiers as private information. `tests/test_doc_addresses.py`
reads the IP half of that sentence, and a person's home folder; the same sentence's MAC address and
Android ID halves were read by no test at all, so a page could carry either and stay green.

That is not only a shape nothing checked - it happened. `docs/devices/coop-cotv-0001.md` printed the
unit's serial number in its Device table, while the notes around it say they leave the serial out
(`docs/devices/sony-x9000h-cn.md`, `docs/devices/xiaomi-mitv-axs00.md`, `docs/devices/changhong-58d4p.md`
and others). The record is fixed by hand rather than by a test: a unit's serial number cannot be told
from a model prefix here, since `examples/byintek-u4/README.md` names the prefix `BYU4B01C` that every
unit of that version starts with and which belongs on the page. A MAC address and an Android ID do have
a shape of their own, which is what this file reads.

What is not a unit's MAC address: `02:00:00:00:00:00`, the Wi-Fi MAC that two TCL 50DP660 files record
as the symptom a disable leaves behind (`examples/tcl-50dp660/debloat.txt`,
`docs/devices/tcl-50dp660.txt`): the same value on every set that hits it, so it says nothing about one.
A partial address such as the `f0:fd:6c:5b...` in `docs/devices/tcl-50dp660.txt` is not a whole address
either, and a note may name it.

A third shape in that sentence was read by nothing either: account emails. The text a contributor
pastes into a page is a session log, and the address that names the person on the unit travels with it -
a Play Store or sign-in error, a `dumpsys account` dump, an About screen, a screenshot's transcript.
CODE_OF_CONDUCT.md counts an account among the private information a device's identifiers carry, and an
email has a shape of its own, so it is read here the way a MAC address is. The project's own contact,
`hello@itsgathin.com`, is the one address a page may print: SECURITY.md hands it out for private
reports and the front pages' press row for queries, the same way the symptom MAC is the one value a page
may name. A Kotlin or Groovy label (`this@ChannelsScreen`, `return@forEach`) is a word before the `@`
rather than an address, and is left alone so a page quoting add-on code stays green.

The pages read are the ones `tests/test_doc_addresses.py` reads - this repository's front pages,
`tools/*.md`, and everything under `examples/`, `docs/` and `themes/` - so the two guards look at the
same reading a contributor can paste a command from.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
# Lists and TV pages, the guides and the themes; the top-level pages and tools/*.md are read too.
FOLDERS = ("examples", "docs", "themes")
SUFFIXES = (".md", ".txt", ".json", ".sh", ".ps1")
# The Wi-Fi MAC two TCL 50DP660 files record as the symptom of a disable: the same on every set that
# hits it, so it names no unit.
SYMPTOM_MAC = "02:00:00:00:00:00"

# A whole MAC address: six pairs of hex digits, not part of a longer run such as a partial address or a
# build stamp.
MAC = re.compile(r"(?i)(?<![\w:])(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?![\w:])")
# An Android ID: the SSAID Android's Settings shows, sixteen hex digits, claimed as one by the words
# beside it. A 40-character commit hash is not one: the closing boundary fails inside it.
ANDROID_ID = re.compile(r"(?i)\bandroid[_ ]id\b[^0-9a-f\n]{0,16}([0-9a-f]{16})\b")
# An account email: a local part, a domain and a dotted suffix of at least two letters, so `shell@android`
# and `@Override` are not one. The project's own contact is the address a page may print (SECURITY.md and
# the front pages' press row), and the words a Kotlin or Groovy label puts before its `@` are not local
# parts: `this@ChannelsScreen` and `return@forEach` name a scope, not a person.
EMAIL = re.compile(r"(?i)(?<![\w.%+-])([A-Za-z0-9._%+-]+)@([A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,})(?![\w-])")
PROJECT_CONTACT = "hello@itsgathin.com"
LABELS = {"this", "super", "return", "break", "continue", "it"}


def pages(root):
    """Every page, list and script a reader copies a command from, in a stable order."""
    root = Path(root)
    found = [path for path in root.glob("*.md")]
    found += [path for path in (root / "tools").glob("*.md")]
    for folder in FOLDERS:
        found += [path for path in (root / folder).rglob("*")
                  if path.is_file() and path.suffix in SUFFIXES]
    return sorted(found)


def mac_addresses(line):
    """The whole MAC addresses on one line, leaving out the value a disabled set reports as its own."""
    return [match.group(0) for match in MAC.finditer(line)
            if match.group(0).lower() != SYMPTOM_MAC]


def android_ids(line):
    """The Android IDs one line states."""
    return [match.group(1) for match in ANDROID_ID.finditer(line)]


def emails(line):
    """The account emails one line states, leaving out the project's own contact and a label's word."""
    out = []
    for match in EMAIL.finditer(line):
        if match.group(1).lower() in LABELS:
            continue
        address = match.group(0)
        if address.lower() == PROJECT_CONTACT:
            continue
        out.append(address)
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
    def test_a_mac_address_is_found(self):
        self.assertEqual(["a4:2b:8c:11:22:33"], mac_addresses("| Wi-Fi MAC | a4:2b:8c:11:22:33 |"))
        self.assertEqual(["F0:FD:6C:5B:11:22"], mac_addresses("the unit answered as F0:FD:6C:5B:11:22."))
        self.assertEqual(["00:1a:79:aa:bb:cc"], mac_addresses("`ADB_SERIAL=00:1a:79:aa:bb:cc`"))

    def test_the_symptom_and_a_partial_address_are_not(self):
        # The value a disable leaves behind, the same on every unit: both TCL 50DP660 files name it.
        self.assertEqual([], mac_addresses("#   - Wi-Fi MAC becomes 02:00:00:00:00:00"))
        self.assertEqual([], mac_addresses("# 02:00:00:00:00:00 Wi-Fi MAC and Netflix error -121"))
        # Four octets and an ellipsis: a prefix, not a whole address.
        self.assertEqual([], mac_addresses("# On this unit both matched (GMS f0:fd:6c:5b..., WebView)"))
        self.assertEqual([], mac_addresses("| build | 2025:10:26:12:00:00 |"))

    def test_an_android_id_is_found(self):
        self.assertEqual(["0123456789abcdef"], android_ids("| Android ID | `0123456789abcdef` |"))
        self.assertEqual(["89abcdef01234567"], android_ids("Android ID: 89abcdef01234567"))
        self.assertEqual(["deadbeefdeadbeef"], android_ids("`settings get secure android_id` = deadbeefdeadbeef"))

    def test_the_word_without_a_value_is_not(self):
        self.assertEqual([], android_ids("This report omits network addresses, serials, Android ID and accounts."))
        self.assertEqual([], android_ids("It does not read the serial number, Android ID, MAC address or keys."))
        self.assertEqual([], android_ids("Android IDs are 16 hex digits long."))
        # A commit hash is 40 hex digits: the sixteen inside it are not an ID the line states.
        self.assertEqual([], android_ids("Android ID is 0123456789abcdef0123456789abcdef01234567 here"))


    def test_an_account_email_is_found(self):
        self.assertEqual(["someone@gmail.com"], emails("| Account | someone@gmail.com |"))
        self.assertEqual(["first.last+tv@example.co.uk"],
                         emails("the Play Store error named first.last+tv@example.co.uk"))
        self.assertEqual(["a.b@mail.example.net", "c@example.org"],
                         emails("`dumpsys account` listed a.b@mail.example.net and c@example.org"))

    def test_the_project_contact_and_a_label_are_not(self):
        # SECURITY.md hands this one out for private reports and the front pages' press row for queries.
        self.assertEqual([], emails('[hello@itsgathin.com](mailto:hello@itsgathin.com) with "Security" '
                                    'in the subject'))
        # A word before the @ naming a scope, not a person: Kotlin's this@Outer and Groovy's return@each.
        self.assertEqual([], emails("val row = this@ChannelsScreen.adapter"))
        self.assertEqual([], emails("items.forEach { return@forEach }"))
        # A host or a shell prompt has no dotted suffix of two letters, and @Override has no local part.
        self.assertEqual([], emails("shell@android reboots and @Override is overridden"))


class DocIdentifiersTest(unittest.TestCase):
    def test_pages_are_read(self):
        read = {path.relative_to(ROOT).as_posix() for path in pages(ROOT)}
        for page in ("README.md", "docs/REMOTE.md", "docs/devices/coop-cotv-0001.md",
                     "examples/tcl-50dp660/debloat.txt", "docs/devices/tcl-50dp660.txt"):
            self.assertIn(page, read)
        self.assertIn(".sh", SUFFIXES, "a folder's own scripts are read too")

    def test_no_page_gives_a_mac_address(self):
        self.assertEqual([], problems(ROOT, mac_addresses),
                         "a unit's MAC address stays out of a page; the value a disable leaves behind "
                         "(%s) is the one a page may name" % SYMPTOM_MAC)

    def test_no_page_gives_an_android_id(self):
        self.assertEqual([], problems(ROOT, android_ids),
                         "an Android ID stays out of a page: it identifies the unit")

    def test_no_page_states_an_account_email(self):
        self.assertEqual([], problems(ROOT, emails),
                         "an account email stays out of a page: it names the person on the unit. The "
                         "project's own contact (%s) is the one a page may print" % PROJECT_CONTACT)


if __name__ == "__main__":
    unittest.main()

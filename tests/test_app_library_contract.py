"""docs/app-library.md's numbers, file names and keys are the ones the library code enforces.

`docs/app-library.md` is the page that explains the App library to an owner, and it states the
library's own storage: two files under the launcher's private directory, a cap on the entries in
each, the window that makes an app "new", and five keys a theme can set. Every one of those is a
value in the code rather than prose - `LibraryApps.MAX_ADDED`, its `RECENT_MS` and the two names its
`store()` builds, the `bool(json, ...)` lines in `Config.java` and the defaults in `config.xml` - and
nothing read the page against them:

    grep -rl 'app-library' tests/     # no file
    grep -rn 'LibraryApps' tests/     # no file

so raising the cap, moving the window or renaming a file would leave the page describing storage the
launcher no longer uses, on the one page an owner reads before trusting it with their Home choices.
The library's key defaults are held by `tests/test_theme_key_defaults.py` against the Keys table in
`themes/README.md`; what is read here is the page's own sentences, in the shapes it writes them.

What is read is the fact, not the wording: two backticked file names, one number, one word ("seven"),
the keys the page hands out as `"key": true|false`, and the paragraphs that call a key off by default.
The prose around them is not pinned, and a page that stops stating one of these fails a test below
rather than reading as "nothing to check".

Reproduced on f63a1ebc by moving the page's cap alone - `at most 128 entries in each` to `64` while
`MAX_ADDED` stays 128 - and by changing `RECENT_MS` to `5L`: the suite stayed green through both,
because no test opened either file.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "app-library.md"
JAVA_DIR = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
STORE = JAVA_DIR / "LibraryApps.java"
CONFIG_JAVA = JAVA_DIR / "Config.java"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"

# The page's sentences, each read as one claim: the cap, the window, a backticked file, a key pair.
PAGE_CAP = re.compile(r"at most (\d+) entries in each")
PAGE_WINDOW = re.compile(r"within the last ([a-z]+) days")
PAGE_FILE = re.compile(r"`(library_[a-z_]+\.txt)`")
PAGE_KEY = re.compile(r'`"([a-z][a-z0-9_]+)"\s*:')
OFF_BY_DEFAULT = re.compile(r"\boff by default\b")

# The code's own values, in the forms LibraryApps.java writes them.
JAVA_CAP = re.compile(r"\bint\s+MAX_ADDED\s*=\s*(\d+)")
JAVA_WINDOW = re.compile(r"\bRECENT_MS\s*=\s*(\d+)L\s*\*\s*24\s*\*\s*60\s*\*\s*60\s*\*\s*1000")
JAVA_FILE = re.compile(r'"(library_[a-z_]+\.txt)"')
JAVA_BOOL_KEY = re.compile(r'\bbool\(\s*json\s*,\s*"([a-z][a-z0-9_]+)"')

WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7,
    "eight": 8, "nine": 9, "ten": 10, "fourteen": 14, "thirty": 30, "sixty": 60,
}


def page_text():
    return " ".join(PAGE.read_text(encoding="utf-8").split())


def cap_of(text):
    """The cap the page states, or None when it states none."""
    match = PAGE_CAP.search(text)
    return int(match.group(1)) if match else None


def window_of(text):
    """The window the page states, in days, or None when it states none or a word this reads not."""
    match = PAGE_WINDOW.search(text)
    return WORDS.get(match.group(1)) if match else None


def files_of(text):
    """The `library_*.txt` names the page backticks, in the order it names them."""
    return PAGE_FILE.findall(text)


def keys_of(text):
    """The keys the page hands out as `"key": true|false`, in the order it names them."""
    seen = []
    for key in PAGE_KEY.findall(text):
        if key not in seen:
            seen.append(key)
    return seen


def paragraph_claims_off(paragraph):
    return bool(OFF_BY_DEFAULT.search(" ".join(paragraph.split())))


def java_cap(text):
    match = JAVA_CAP.search(text)
    return int(match.group(1)) if match else None


def java_window(text):
    match = JAVA_WINDOW.search(text)
    return int(match.group(1)) if match else None


def java_files(text):
    return sorted(set(JAVA_FILE.findall(text)))


def config_bools(text):
    """name -> the value config.xml ships, as a bool."""
    out = {}
    for match in re.finditer(r'<bool\s+name="([^"]+)"\s*>([^<]*)<', text):
        out[match.group(1)] = match.group(2).strip() == "true"
    return out


class PageAgainstCode(unittest.TestCase):
    """The page's own claims, read against the code that enforces them."""

    def setUp(self):
        self.page = page_text()
        self.store = STORE.read_text(encoding="utf-8")
        self.config = CONFIG_XML.read_text(encoding="utf-8")
        self.bools = config_bools(self.config)

    def test_the_cap_is_the_one_the_code_enforces(self):
        stated = cap_of(self.page)
        enforced = java_cap(self.store)
        self.assertIsNotNone(stated, "docs/app-library.md no longer states 'at most N entries in each'")
        self.assertIsNotNone(enforced, "LibraryApps.java declares no int MAX_ADDED")
        self.assertEqual(enforced, stated,
                         f"docs/app-library.md says at most {stated} entries and LibraryApps.MAX_ADDED "
                         f"is {enforced}")

    def test_the_window_is_the_one_recent_uses(self):
        stated = window_of(self.page)
        enforced = java_window(self.store)
        self.assertIsNotNone(stated,
                             "docs/app-library.md no longer says an app is new 'within the last N days'")
        self.assertIsNotNone(enforced, "LibraryApps.java no longer sets RECENT_MS in days")
        self.assertEqual(enforced, stated,
                         f"docs/app-library.md says the last {stated} days and LibraryApps.RECENT_MS "
                         f"is {enforced} days")

    def test_the_files_are_the_two_the_code_writes(self):
        stated = sorted(set(files_of(self.page)))
        written = java_files(self.store)
        self.assertEqual(2, len(written), f"LibraryApps.java names {written}, not the two files")
        self.assertEqual(written, stated,
                         f"docs/app-library.md names {stated} and LibraryApps.store() writes {written}")

    def test_the_keys_the_page_names_are_ones_the_launcher_reads(self):
        keys = keys_of(self.page)
        self.assertGreaterEqual(len(keys), 5,
                                f"docs/app-library.md hands out {keys}: the five library keys are gone")
        config_java = CONFIG_JAVA.read_text(encoding="utf-8")
        read = set(JAVA_BOOL_KEY.findall(config_java))
        for key in keys:
            with self.subTest(key=key):
                self.assertIn(key, self.bools,
                              f"docs/app-library.md hands out {key} and config.xml defines no bool for it")
                self.assertIn(key, read,
                              f"docs/app-library.md hands out {key} and Config.java reads no bool for it")

    def test_each_key_the_page_calls_off_by_default_is_off(self):
        """A paragraph that hands out a key and calls it off by default is a claim about config.xml."""
        claims = 0
        for paragraph in PAGE.read_text(encoding="utf-8").split("\n\n"):
            keys = keys_of(paragraph)
            if not keys or not paragraph_claims_off(paragraph):
                continue
            for key in keys:
                claims += 1
                with self.subTest(key=key):
                    self.assertIn(key, self.bools,
                                  f"docs/app-library.md calls {key} off by default and config.xml "
                                  f"defines no bool for it")
                    self.assertFalse(self.bools[key],
                                     f"docs/app-library.md calls {key} off by default and config.xml "
                                     f"ships it true")
        self.assertGreaterEqual(claims, 3,
                                f"only {claims} key paragraphs read as off by default: the page states "
                                f"three")

class ReadersTest(unittest.TestCase):
    """Each reader, on the shapes it has to tell apart."""

    def test_the_cap_is_read_as_a_number(self):
        self.assertEqual(64, cap_of("with at most 64 entries in each. Each file"))
        self.assertIsNone(cap_of("with entries capped somewhere. Each file"))

    def test_the_window_is_read_from_the_word_the_page_uses(self):
        self.assertEqual(7, window_of("within the last seven days lead that group"))
        self.assertEqual(5, window_of("within the last five days lead that group"))
        self.assertIsNone(window_of("within the last week lead that group"))

    def test_the_files_are_read_from_backticks_only(self):
        self.assertEqual(["library_home_removed.txt"],
                         files_of("the private `library_home_removed.txt`, which"))
        self.assertEqual([], files_of("two files in the launcher's directory"))

    def test_a_key_is_read_from_the_pair_the_page_hands_out(self):
        self.assertEqual(["app_library", "library_ok_opens"],
                         keys_of('Set `"app_library": true` with `"library_ok_opens": true`'))
        self.assertEqual([], keys_of("enable app_library in config.xml"))

    def test_off_by_default_is_read_from_the_paragraph_not_the_page(self):
        self.assertTrue(paragraph_claims_off("It is off by default and the standard behavior"))
        self.assertTrue(paragraph_claims_off("It is off by\ndefault, and with the other one also on"))
        self.assertFalse(paragraph_claims_off("It is on by default since 3.3"))

    def test_the_code_side_reads_its_own_declarations(self):
        self.assertEqual(128, java_cap("    static final int MAX_ADDED = 128;"))
        self.assertIsNone(java_cap("    static final int MAX_ENTRIES = 128;"))
        self.assertEqual(7, java_window("RECENT_MS = 7L * 24 * 60 * 60 * 1000;"))
        self.assertEqual(5, java_window("RECENT_MS = 5L * 24 * 60 * 60 * 1000;"))
        self.assertIsNone(java_window("RECENT_DAYS = 7;"))
        self.assertEqual(["library_apps.txt", "library_home_removed.txt"],
                         java_files('new File(dir, removed ? "library_home_removed.txt" : "library_apps.txt")'))

    def test_config_bools_are_read_with_their_value(self):
        bools = config_bools('<bool name="app_library">false</bool>\n<bool name="show_library_pill">true</bool>')
        self.assertEqual({"app_library": False, "show_library_pill": True}, bools)


if __name__ == "__main__":
    unittest.main()

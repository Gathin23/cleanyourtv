"""The App library page's numbers and file names are the ones `LibraryApps.java` keeps.

docs/app-library.md is the page an owner reads to understand what the library stores: the two private
files in the launcher's `files/` folder, "at most 128 entries in each", and apps "first installed within
the last seven days" leading the missing group with a green dot. All three numbers live in
`LibraryApps.java` (`MAX_ADDED`, `RECENT_MS`, and the two names `store()` builds), and the page also says
the additional manifest query covers ordinary `LAUNCHER` activities, which is one block of the launcher's
`AndroidManifest.xml`. Nothing read either side against the other: `LibraryAppsTest.java` exercises the
Java by the constant (`MAX_ADDED - 1`, `MAX_ADDED`), so the file keeps compiling and passing whatever the
constant becomes, and no Python test opens `LibraryApps.java` at all.

Reproduced on 57c00a40 in a worktree, three drifts at once, the kind a follow-up commit makes:

    # LibraryApps.java: MAX_ADDED 128 -> 96, RECENT_MS 7L -> 14L, "library_apps.txt" -> "library_shortcuts.txt"
    # docs/app-library.md: unchanged, still 128 entries, seven days and the old file name

`python3 -m unittest discover -s tests` stayed at 2562 tests OK (skipped=18) - `LibraryAppsTest` reads
`MAX_ADDED` rather than 128, so it stays green too - while the page told an owner to look for a file the
launcher no longer writes and promised a week of "new" dots over a fortnight's worth.

Read here: the two file names, the entry cap, the recent window in days and the ordinary-`LAUNCHER`
query. The prose around them is not pinned; a page that stops stating one of them fails a test below
instead of reading as "nothing to compare". A range the reader cannot parse is reported, not skipped.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "app-library.md"
JAVA_DIR = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
LIBRARY_APPS = JAVA_DIR / "LibraryApps.java"
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"

# docs/app-library.md: the two stored files, the entry cap and the recent window.
STORED_FILE = re.compile(r"`([A-Za-z0-9_]+\.txt)`")
PAGE_CAP = re.compile(r"at most (\d+) entries in each")
PAGE_DAYS = re.compile(r"within the last (\w+) days")
PAGE_QUERY = re.compile(r"manifest query covers? (?:ordinary )?`?LAUNCHER`? activities")

NUMBER_WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8,
    "nine": 9, "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14,
    "fifteen": 15, "sixteen": 16, "seventeen": 17, "eighteen": 18, "nineteen": 19, "twenty": 20,
}

# LibraryApps.java: the cap, the window, the two names store() builds, and how a write is bounded.
MAX_ADDED_DECL = re.compile(r"\bMAX_ADDED\s*=\s*(\d+)\s*;")
RECENT_MS_DECL = re.compile(r"\bRECENT_MS\s*=\s*(\d+)L\s*\*\s*24\s*\*\s*60\s*\*\s*60\s*\*\s*1000\b")
STORE_NAMES = re.compile(r'getFilesDir\(\),\s*removed\s*\?\s*"([^"]+)"\s*:\s*"([^"]+)"\)')
CAP_CHECK = re.compile(r"next\.size\(\)\s*>\s*MAX_ADDED\b")
WRITE_CALL = re.compile(r"write\(store\(context,\s*(false|true)\)")


def one_line(body):
    """The page with every run of whitespace folded to one space: the page wraps its sentences by hand."""
    return " ".join(body.split())


def stored_files(body):
    """The `*.txt` file names the page names, sorted; empty when it names none."""
    return sorted(set(STORED_FILE.findall(body)))


def entries_cap(body):
    """The number in "at most N entries in each", or None when the page stops stating one."""
    match = PAGE_CAP.search(one_line(body))
    return int(match.group(1)) if match else None


def recent_days(body):
    """The window in "within the last N days", as a number, or None when it is not stated."""
    match = PAGE_DAYS.search(one_line(body))
    if not match:
        return None
    word = match.group(1).lower()
    return int(word) if word.isdigit() else NUMBER_WORDS.get(word)


def names_the_launcher_query(body):
    """Whether the page says the additional manifest query covers ordinary LAUNCHER activities."""
    return PAGE_QUERY.search(one_line(body)) is not None


def store_names(java):
    """(kept, removed) file names out of `store()`, or () when the line moved."""
    match = STORE_NAMES.search(java)
    return (match.group(2), match.group(1)) if match else ()


def max_added(java):
    """The value of `MAX_ADDED`, or None when the declaration is not there."""
    match = MAX_ADDED_DECL.search(java)
    return int(match.group(1)) if match else None


def recent_window_days(java):
    """The `RECENT_MS` window expressed in whole days, or None when the declaration is not there."""
    match = RECENT_MS_DECL.search(java)
    return int(match.group(1)) if match else None


def capped_writes(java):
    """The stores written through `write()` -> `change()`, which is where MAX_ADDED refuses a 129th entry."""
    written = {group for group in WRITE_CALL.findall(java)}
    bounded = CAP_CHECK.search(java) is not None and "change(saved, pkg, added)" in java
    return written, bounded


def launcher_query(manifest):
    """True when <queries> holds a MAIN intent for the ordinary LAUNCHER category (not LEANBACK)."""
    if "<queries>" not in manifest or "</queries>" not in manifest:
        return False
    queries = manifest.split("<queries>", 1)[1].split("</queries>", 1)[0]
    for intent in re.findall(r"<intent>(.*?)</intent>", queries, re.S):
        if "android.intent.action.MAIN" in intent and 'android.intent.category.LAUNCHER"' in intent:
            return True
    return False


def read(path):
    return path.read_text(encoding="utf-8")


class ThePageAndTheCode(unittest.TestCase):
    """docs/app-library.md against LibraryApps.java and the launcher's manifest, both directions."""

    def setUp(self):
        self.page = read(PAGE)
        self.java = read(LIBRARY_APPS)
        self.manifest = read(MANIFEST)

    def test_both_files_are_read(self):
        """The guards below cannot pass by reading nothing."""
        self.assertEqual(2, len(stored_files(self.page)), stored_files(self.page))
        self.assertIsNotNone(entries_cap(self.page))
        self.assertIsNotNone(recent_days(self.page))
        self.assertTrue(names_the_launcher_query(self.page), "the page no longer names the query it relies on")
        self.assertTrue(store_names(self.java), "store() no longer names the two files")
        self.assertIsNotNone(max_added(self.java))
        self.assertIsNotNone(recent_window_days(self.java))

    def test_the_page_names_the_two_files_the_store_writes(self):
        """Both ways round: a file the launcher writes under a name the owner cannot find, or a name it
        stopped writing, is the same defect - the page is where an owner looks before an `adb shell`."""
        named = stored_files(self.page)
        written = store_names(self.java)
        self.assertEqual(sorted(written), sorted(named),
                         "docs/app-library.md names %s and LibraryApps.java's store() writes %s"
                         % (", ".join(named), ", ".join(sorted(written))))

    def test_each_file_is_capped_at_the_number_the_page_states(self):
        """One constant bounds both files: both stores are written through write(), and change() is what
        refuses the entry over the cap, so "in each" holds for the two the page names."""
        stated = entries_cap(self.page)
        kept = max_added(self.java)
        self.assertEqual(stated, kept,
                         "docs/app-library.md tells an owner at most %s entries in each and "
                         "LibraryApps.java keeps %s" % (stated, kept))
        written, bounded = capped_writes(self.java)
        self.assertEqual({"false", "true"}, written,
                         "only one of the two stored files goes through write(): %s" % sorted(written))
        self.assertTrue(bounded, "change() no longer refuses an entry over MAX_ADDED")

    def test_the_recent_window_is_the_one_the_page_states(self):
        """The green "new" dot: a page promising seven days over a fortnight's RECENT_MS is a wrong dot."""
        stated = recent_days(self.page)
        kept = recent_window_days(self.java)
        self.assertEqual(stated, kept,
                         "docs/app-library.md tells an owner apps within the last %s days lead the group "
                         "and LibraryApps.java's RECENT_MS is %s days" % (stated, kept))

    def test_the_manifest_query_covers_the_activities_the_page_names(self):
        """The page says the additional manifest query covers ordinary LAUNCHER activities; without that
        block a library on Android 11+ sees only the TV apps, and the page still says otherwise."""
        self.assertTrue(names_the_launcher_query(self.page))
        self.assertTrue(launcher_query(self.manifest),
                        "docs/app-library.md says the manifest query covers ordinary LAUNCHER activities "
                        "and AndroidManifest.xml's <queries> has no MAIN + LAUNCHER intent")


class TheReaders(unittest.TestCase):
    """The same readers over a page, a Java file and a manifest written here, so each rule is known to fire."""

    PAGE = ("Choices are stored in the launcher's private `library_apps.txt` and `library_home_removed.txt`, "
            "with at most 128 entries in each. Each file is written atomically.\n\n"
            "Apps first installed within the last seven days lead that group, newest first.\n\n"
            "The additional manifest query covers ordinary LAUNCHER activities on Android 11 and later.\n")

    JAVA = ("final class LibraryApps {\n"
            "    static final int MAX_ADDED = 128;\n"
            "    private static final long RECENT_MS = 7L * 24 * 60 * 60 * 1000;\n"
            "    private static AtomicFile store(Context context, boolean removed) {\n"
            "        return new AtomicFile(new File(context.getFilesDir(), "
            "removed ? \"library_home_removed.txt\" : \"library_apps.txt\"));\n"
            "    }\n"
            "    static boolean save(Context context, String pkg, boolean onHome) {\n"
            "        if (!write(store(context, false), additions, pkg, true)) return false;\n"
            "        if (!write(store(context, true), removed, pkg, true)) return false;\n"
            "    }\n"
            "    private static boolean write(AtomicFile file, Set<String> saved, String pkg, boolean added) {\n"
            "        Set<String> next = change(saved, pkg, added);\n"
            "    }\n"
            "    static Set<String> change(Set<String> saved, String pkg, boolean added) {\n"
            "        if (next.size() > MAX_ADDED) throw new IllegalArgumentException(\"Too many shortcuts\");\n"
            "    }\n"
            "}\n")

    QUERIES = ("<manifest>\n"
               "    <queries>\n"
               "        <intent>\n"
               "            <action android:name=\"android.intent.action.MAIN\" />\n"
               "            <category android:name=\"android.intent.category.LAUNCHER\" />\n"
               "        </intent>\n"
               "    </queries>\n"
               "</manifest>\n")

    def test_the_page_is_read(self):
        self.assertEqual(128, entries_cap(self.PAGE))
        self.assertEqual(7, recent_days(self.PAGE))
        self.assertEqual(["library_apps.txt", "library_home_removed.txt"], stored_files(self.PAGE))
        self.assertTrue(names_the_launcher_query(self.PAGE))

    def test_the_java_is_read(self):
        self.assertEqual(("library_apps.txt", "library_home_removed.txt"), store_names(self.JAVA))
        self.assertEqual(128, max_added(self.JAVA))
        self.assertEqual(7, recent_window_days(self.JAVA))
        self.assertEqual(({"false", "true"}, True), capped_writes(self.JAVA))

    def test_a_renamed_file_on_either_side_is_reported(self):
        self.assertNotEqual(sorted(store_names(self.JAVA)),
                            sorted(stored_files(self.PAGE.replace("library_apps.txt", "library_shortcuts.txt"))))
        self.assertNotEqual(sorted(store_names(self.JAVA.replace("library_apps.txt", "shortcuts.txt"))),
                            sorted(stored_files(self.PAGE)))

    def test_a_widened_cap_on_either_side_is_reported(self):
        self.assertNotEqual(entries_cap(self.PAGE), max_added(self.JAVA.replace("MAX_ADDED = 128", "MAX_ADDED = 96")))
        self.assertNotEqual(entries_cap(self.PAGE.replace("at most 128 entries", "at most 256 entries")),
                            max_added(self.JAVA))

    def test_a_wider_window_is_reported(self):
        self.assertEqual(14, recent_window_days(self.JAVA.replace("7L * 24", "14L * 24")))
        self.assertEqual(14, recent_days(self.PAGE.replace("last seven days", "last fourteen days")))
        self.assertNotEqual(recent_days(self.PAGE), recent_window_days(self.JAVA.replace("7L * 24", "14L * 24")))

    def test_a_window_this_reader_cannot_read_is_not_a_zero(self):
        """A word the table does not carry reads as None, so the comparison fails instead of passing as 0."""
        self.assertIsNone(recent_days(self.PAGE.replace("last seven days", "last fortnight days")))
        self.assertIsNone(recent_days(self.PAGE.replace("within the last seven days", "within a week")))

    def test_a_sentence_wrapped_by_hand_is_read(self):
        """The page wraps its own lines: a number or a name split across the break is still the claim."""
        self.assertEqual(7, recent_days(self.PAGE.replace("last seven days", "last seven\n            days")))
        self.assertEqual(128, entries_cap(self.PAGE.replace("at most 128 entries", "at most\n            128 entries")))
        self.assertTrue(names_the_launcher_query(
            self.PAGE.replace("ordinary LAUNCHER activities", "ordinary\n            LAUNCHER activities")))

    def test_a_page_without_the_numbers_is_reported(self):
        bare = "# App library\n\nA pill after the last Home row.\n"
        self.assertIsNone(entries_cap(bare))
        self.assertIsNone(recent_days(bare))
        self.assertEqual([], stored_files(bare))
        self.assertFalse(names_the_launcher_query(bare))
        self.assertEqual([], [name for name in stored_files(bare) if name in store_names(self.JAVA)])

    def test_a_bounded_write_needs_the_check_and_the_change(self):
        """A store written straight from save() would leave the cap in change() unenforced."""
        unwritten = self.JAVA.replace("write(store(context, true)", "write(only_store(context, true)")
        self.assertEqual({"false"}, capped_writes(unwritten)[0])
        unchecked = self.JAVA.replace("next.size() > MAX_ADDED", "next.size() > 0")
        self.assertFalse(capped_writes(unchecked)[1])

    def test_the_manifest_reader_wants_the_ordinary_category(self):
        self.assertTrue(launcher_query(self.QUERIES))
        leanback = self.QUERIES.replace("category.LAUNCHER", "category.LEANBACK_LAUNCHER")
        self.assertFalse(launcher_query(leanback), "a LEANBACK-only query is not the ordinary one")
        self.assertFalse(launcher_query("<manifest>\n</manifest>\n"))
        self.assertFalse(launcher_query("<manifest>\n    <queries>\n    </queries>\n</manifest>\n"))


if __name__ == "__main__":
    unittest.main()

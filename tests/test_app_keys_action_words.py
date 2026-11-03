"""The action words `app_keys` accepts are the ones `RemoteKeys` acts on and the Keys table names.

An `app_keys` entry in place of a package runs an action: `"284:library"` opens the App library, and
`"KEYCODE_PROG_YELLOW:mute"` toggles the sound. The words an author may write are stated twice - in
the `app_keys` row of `themes/README.md`, and in the comment above `config.xml`'s `app_keys` array -
and read in three places in `RemoteKeys.java`: `isAction()` decides a target is a word, `globalAction()`
answers the four the framework performs, and `runAction()`'s switch answers the rest.

`RemoteKeysTest.actionWordsAreTargets` walks the ten words the launcher takes today and asserts `isAction()`
accepts each one, which is one direction of one edge: a word added to `isAction()` with no row in that
array stays green, and so does a word removed from `isAction()` and from that array while the pages
still name it. Nothing read the pages at all: `grep -rn "isAction\\|action word" tests/` printed nothing.

Two failures this closes, both of them silent on the TV:

- A page that names a word the launcher no longer takes. A theme author writes `"586:settings"` from
  the Keys table and the button does nothing, the way an uninstalled package does; nothing says the
  word was dropped, because `isAction()` returning false sends the target down the app path instead.
- A word added to `isAction()` alone. `isAction()` saying yes is what swallows the button: `runAction()`
  falls through to that switch's `default` and the press is consumed doing nothing. A word nobody
  documented is a feature nobody can find, and a word with no handler is one that eats the press.

`config.xml`'s comment states the words in bare prose beside "back" in "brings it back" and the
library's own parenthetical, so it is named here rather than read; the page's list is backticked,
which is what this file reads.

Only the words are pinned, never the prose around them: a page that rewrites its sentences passes,
a page whose list no longer matches `RemoteKeys` fails and names the word.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "themes" / "README.md"
REMOTE_KEYS = (ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
               / "RemoteKeys.java")

# The sentence in the Keys table that lists the action words: "An action word in place of the package
# runs that action: `back`, ...". Everything after it is read, not only up to the first example: every
# example entry in the row is a code span with a colon in it (`"284:library"`, `"scan:586:ignore"`,
# `hold:`, `front:`, `guide:<package>`), so a later sentence adds no word this reader would take.
LIST_OPENER = "runs that action:"
# A word in that list is a backticked, lowercase, letters-only span. `app_library` (the key the library
# word needs) carries an underscore and `"284:library"` a colon and quotes, so neither is a second word.
WORD = re.compile(r"`([a-z]+)`")

# The words `isAction()` accepts and nothing else acts on, on purpose: the press is swallowed so a
# remote shortcut pressed by accident does nothing. `RemoteKeys.runAction()` says so in a comment.
SWALLOWED = {"ignore"}


def method_body(java, signature, where="RemoteKeys.java"):
    """The body of one method, from its signature to the line that closes it at the class's indent."""
    start = java.find(signature)
    if start < 0:
        raise AssertionError(f"{where} has no `{signature.strip()}`, so the launcher and this file no "
                             f"longer agree on where an action word is read")
    end = java.find("\n    }\n", start)
    if end < 0:
        raise AssertionError(f"`{signature.strip()}` does not end at the class's indent in {where}, so "
                             f"its cases cannot be read")
    return java[start:end]


def cases(body):
    """The `case "x"` labels of one switch, in the order the file writes them.

    Written on one line as `case "a": case "b":` or one per line; both are read.
    """
    return re.findall(r'case\s+"([^"]+)"', body)


def is_action_words(java):
    """The words `isAction()` accepts."""
    return set(cases(method_body(java, "static boolean isAction(String target)")))


def global_action_words(java):
    """The words `globalAction()` answers with a framework action."""
    return set(cases(method_body(java, "static int globalAction(String target, int sdk)")))


def run_action_words(java):
    """The words `runAction()`'s own switch answers (the rest is a framework action or swallowed)."""
    body = method_body(java, "private static boolean runAction(Context ctx, String target)")
    switch = body[body.find("switch (target)"):] if "switch (target)" in body else ""
    return set(cases(switch))


def keys_row(page):
    """The `app_keys` row of themes/README.md's Keys table, as one line."""
    for line in page.splitlines():
        if re.match(r"^\|\s*`app_keys`\s*\|", line):
            return line
    raise AssertionError("themes/README.md's Keys table has no `app_keys` row, so a theme author has "
                         "no page that names the action words")


def page_words(row):
    """The action words the row names after the sentence that lists them, in the order it writes them."""
    if LIST_OPENER not in row:
        raise AssertionError("the `app_keys` row in themes/README.md no longer says "
                             f"{LIST_OPENER!r} and lists the action words, so a reader has nothing to "
                             "read: say 'runs that action: `back`, ...' as before")
    return WORD.findall(row.split(LIST_OPENER, 1)[1])


def java_text(root=ROOT):
    return (root / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
            / "RemoteKeys.java").read_text(encoding="utf-8")


def page_text(root=ROOT):
    return (root / "themes" / "README.md").read_text(encoding="utf-8")


class ActionWordsTest(unittest.TestCase):
    """The tree: the pages, `isAction()` and the two methods that answer each word."""

    def test_the_page_names_the_words_the_launcher_accepts(self):
        accepted, named = is_action_words(java_text()), page_words(keys_row(page_text()))
        self.assertEqual(sorted(accepted), sorted(set(named)),
                         "the `app_keys` row in themes/README.md and RemoteKeys.isAction() name "
                         f"different action words: the page {sorted(set(named))}, the launcher "
                         f"{sorted(accepted)}")

    def test_the_page_names_each_word_once(self):
        named = page_words(keys_row(page_text()))
        duplicates = sorted({w for w in named if named.count(w) > 1})
        self.assertEqual([], duplicates,
                         "the `app_keys` row in themes/README.md names the same action word twice, so "
                         f"a reader cannot tell which line is the word's own: {duplicates}")

    def test_every_word_the_launcher_accepts_is_one_it_acts_on(self):
        # isAction() saying yes is what consumes the press: runAction() then falls through to its
        # default, so a word with no handler is a button that does nothing and a log line that says
        # nothing happened.
        java = java_text()
        accepted = is_action_words(java)
        answered = global_action_words(java) | run_action_words(java) | SWALLOWED
        self.assertEqual(sorted(answered), sorted(accepted),
                         "RemoteKeys.isAction() accepts an action word nothing answers, so the press "
                         f"is swallowed: {sorted(accepted - answered)}; and answers a word isAction() "
                         f"does not take, so it is unreachable: {sorted(answered - accepted)}")

    def test_the_words_are_the_ones_the_launcher_test_names(self):
        # RemoteKeysTest walks its own array of the words and asserts isAction() accepts each, so a
        # word added to isAction() alone leaves that test green: it only ever asks about the words it
        # already carries. Holding the array to isAction() is what makes the next word be named
        # here too, and the page test above then makes it be named on the page.
        accepted = is_action_words(java_text())
        java_test = (ROOT / "launcher" / "app" / "src" / "test" / "java" / "com" / "gathin" / "tvhome"
                     / "RemoteKeysTest.java").read_text(encoding="utf-8")
        body = method_body(java_test, "void actionWordsAreTargets()", "RemoteKeysTest.java")
        array = re.search(r"new String\[\]\s*\{([^}]*)\}", body)
        if array is None:
            self.fail("RemoteKeysTest.actionWordsAreTargets no longer walks an array of the action "
                      "words, so a word added to isAction() is named nowhere")
        walked = set(re.findall(r'"([^"]+)"', array.group(1)))
        self.assertEqual(sorted(accepted), sorted(walked),
                         "RemoteKeys.isAction() and the array RemoteKeysTest walks hold different "
                         f"action words: the launcher {sorted(accepted)}, the test {sorted(walked)}")


class ReaderTest(unittest.TestCase):
    """The same readers over a row and a Java file written in the test."""

    def test_a_word_dropped_from_the_page_is_reported(self):
        declined = "`back`, `home`, `recents`, `mute`, `sleep` (standby), `screensaver`, `settings`, " \
                   "`wifi`, `ignore` (swallows the button), e.g. `\"KEYCODE_PROG_YELLOW:mute\"`"
        named = page_words(f"| `app_keys` | list of strings | An action word runs that action: {declined} |")
        self.assertEqual(["back", "home", "recents", "mute", "sleep", "screensaver", "settings", "wifi",
                          "ignore"], named)

    def test_a_word_the_launcher_does_not_take_is_reported(self):
        row = "| `app_keys` | list | An action word runs that action: `back`, `netflix`, e.g. `\"1:mute\"` |"
        self.assertEqual(["back", "netflix"], page_words(row))

    def test_the_library_key_is_not_read_as_an_action_word(self):
        row = ("| `app_keys` | list | An action word runs that action: `library` (Home with the App "
               "library open; needs `app_library`) or `ignore` (swallows the button; with app_library "
               "off the library word only brings Home forward), e.g. `\"284:library\"` |")
        self.assertEqual(["library", "ignore"], page_words(row))

    def test_the_example_entry_is_not_read_as_a_word(self):
        row = ("| `app_keys` | list | An action word runs that action: `mute`, e.g. "
               "`\"KEYCODE_PROG_YELLOW:mute\"` |")
        self.assertEqual(["mute"], page_words(row))

    def test_a_capitalised_word_is_not_read(self):
        # isAction() matches the lowercase word, so `Mute` in a list is not the word `mute`: reading
        # it as one would hide a page that stops naming the word the launcher takes.
        row = "| `app_keys` | list | An action word runs that action: `Mute`, e.g. `\"1:mute\"` |"
        self.assertEqual([], page_words(row))

    def test_a_row_with_no_list_is_reported(self):
        with self.assertRaises(AssertionError) as caught:
            page_words("| `app_keys` | list | Remote buttons that open an app |")
        self.assertIn("runs that action", str(caught.exception))

    def test_a_page_with_no_row_is_reported(self):
        with self.assertRaises(AssertionError) as caught:
            keys_row("| `dock_apps` | list | The dock row |")
        self.assertIn("no `app_keys` row", str(caught.exception))

    def test_the_three_switches_are_read_from_the_file(self):
        java = java_text()
        self.assertIn("back", global_action_words(java))
        self.assertIn("library", run_action_words(java))
        self.assertIn("ignore", is_action_words(java))
        self.assertNotIn("back", run_action_words(java))

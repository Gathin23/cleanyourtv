"""A list that keeps its stock launcher enabled does not hand that package to the install script.

`launcher/build-and-install.sh` disables the package STOCK_LAUNCHER names (`pm disable-user --user 0
$STOCK_LAUNCHER`, once the new launcher has started), and with no STOCK_LAUNCHER it disables the app
that owns Home when that is one of its `KNOWN_STOCK_LAUNCHERS`. Only `STOCK_LAUNCHER=none` leaves the
stock launcher enabled (CONTRIBUTING.md, "Add your TV model").

Two Xiaomi CN lists said the opposite of their own notes. The MiTV-ANSP0 header read "KEEP IT
ENABLED" and then gave `STOCK_LAUNCHER=com.mitv.tvhome` as the install command, and the MiTV-ASTP0
header passed the same package while its Home note says to keep it. On both sets Home falls to
FallbackHome once PatchWall is disabled, so that command ends in the script's rollback
(docs/devices/xiaomi-mitv-ansp0-jobs.txt saw it on the first attempt). The ASTP0 and ASTP1 headers
also said com.mitv.tvhome "is not in KNOWN_STOCK_LAUNCHERS", when it is one of them.

The checks read the lists rather than naming them:

- a comment paragraph that says "keep it enabled", "keep <package> enabled" or "must stay enabled"
  keeps the package it names, and the same list must not pass that package as STOCK_LAUNCHER=.
  "Left enabled here" next to a STOCK_LAUNCHER that "disables it after the swap" (freebox-player-pop,
  vidda-mt9653) is the other, consistent way round: kept out of the list, disabled by the script.
- a paragraph that says a package is not in KNOWN_STOCK_LAUNCHERS must not be about one the script
  lists. The packages are read out of the script by test_stock_launcher_docs.sh_known.

Every list a folder ships is read, not only its debloat.txt: eight folders put a second list beside it
(debloat-keep-cast.txt twice, debloat-75t8c-eu.txt, debloat-32-inch.txt, debloat-extra.txt,
debloat-display-only.txt twice, debloat-65u7esg.txt) and each is a file an owner applies with the same
tools/disable.sh and launcher/build-and-install.sh pair. The sweep globbed */debloat.txt, so an install
command on one of those eight could have passed a stock launcher its own note keeps enabled and neither
rule above would have seen it. None does today: the eight carry no STOCK_LAUNCHER=<package> and the two
packages they say to keep (TalkBack on the OnePlus 32-inch, com.vewd.core.service on the Sony) are not
sent by either list. tests/test_sweeps_read_second_lists.py holds this file to reading them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_stock_launcher_docs import sh_known  # noqa: E402  (the reader of the script's KNOWN_STOCK_LAUNCHERS)

ROOT = Path(__file__).resolve().parents[1]

# Not the tail of a path or a component: "build-and-install.sh", "/.system.FallbackHome".
PACKAGE = r"(?<![\w.$/-])[a-z][a-z0-9_]*(?:\.[A-Za-z0-9_]+)+"
PACKAGE_RE = re.compile(PACKAGE)
# `STOCK_LAUNCHER=<package>`; `STOCK_LAUNCHER=none` names no package and is not read.
PASSED = re.compile(r"STOCK_LAUNCHER\s*=\s*(" + PACKAGE + r")")
# "KEEP IT ENABLED", "Keep it enabled", "KEEP ENABLED", "keep com.x enabled", "must stay enabled".
# "Leave it enabled" and "left enabled here" are not read: the lists use them for a package that only
# stays out of the list, and for setupwraith.
KEEP = re.compile(r"(?i)\bkeep\s+(?:it\s+|`?(" + PACKAGE + r")`?\s+)?enabled\b|\bmust\s+stay\s+enabled\b")
NOT_KNOWN = re.compile(r"(?i)\bnot\s+in\s+(?:\S+\s+)?KNOWN_STOCK_LAUNCHERS\b")
# Every list a folder ships: debloat.txt and the second list beside it (debloat-keep-cast.txt,
# debloat-75t8c-eu.txt, ...). Both are applied with tools/disable.sh and the install script.
LIST_GLOB = "*/debloat*.txt"


def notes(text):
    """A list's comment paragraphs as (first line, text): a run of `#` lines that a bare `#`, a new
    `# - ` bullet or a package line ends, with the wrapped lines joined."""
    found, start, parts = [], None, []
    for number, line in enumerate(text.splitlines(), 1):
        body = line.strip()
        comment = body.startswith("#") and body != "#"
        if start is not None and (not comment or body.startswith("# - ")):
            found.append((start, " ".join(parts)))
            start, parts = None, []
        if comment:
            if start is None:
                start = number
            parts.append(body.lstrip("#").strip())
    if start is not None:
        found.append((start, " ".join(parts)))
    return found


def kept_packages(text):
    """{package: first line of the paragraph} for every package the list says to keep enabled."""
    kept = {}
    for start, note in notes(text):
        for match in KEEP.finditer(note):
            named = [match.group(1)] if match.group(1) else PACKAGE_RE.findall(note)
            for package in named:
                kept.setdefault(package, start)
    return kept


def passed_packages(text):
    """(line, package) for every STOCK_LAUNCHER=<package> the list shows."""
    return [(number, match.group(1))
            for number, line in enumerate(text.splitlines(), 1)
            for match in PASSED.finditer(line)]


def install_problems(text):
    """Why this list's install command disables a stock launcher the list keeps, one sentence each."""
    kept = kept_packages(text)
    return [f"line {number} passes STOCK_LAUNCHER={package}, which the note at line {kept[package]} "
            f"says to keep enabled; build-and-install.sh disables the package STOCK_LAUNCHER names, so "
            f"the command needs STOCK_LAUNCHER=none"
            for number, package in passed_packages(text) if package in kept]


def unknown_claims(text, known):
    """Where the list calls a package that is in KNOWN_STOCK_LAUNCHERS unknown, one sentence each.
    The package is the last one the paragraph names before the claim, as in "Stock launcher: x. It is
    not in KNOWN_STOCK_LAUNCHERS"."""
    problems = []
    for start, note in notes(text):
        for match in NOT_KNOWN.finditer(note):
            before = PACKAGE_RE.findall(note[:match.start()])
            if before and before[-1] in known:
                problems.append(f"the note at line {start} says {before[-1]} is not in "
                                f"KNOWN_STOCK_LAUNCHERS, but build-and-install.sh lists it")
    return problems


def list_files(examples):
    """Every list under examples/, debloat.txt and the second list a folder ships, in path order."""
    return sorted(path for path in examples.glob(LIST_GLOB) if path.is_file())


def sweep(examples):
    """(lists read, second lists read, passes, keeps, failures) over every list under examples/.

    The second-list count is what tests/test_sweeps_read_second_lists.py holds to more than zero: the
    walk exists for the files beside debloat.txt as much as for debloat.txt itself.
    """
    lists = second = passes = keeps = 0
    failures = []
    for path in list_files(examples):
        text = path.read_text(encoding="utf-8")
        lists += 1
        second += path.name != "debloat.txt"
        passes += len(passed_packages(text))
        keeps += len(kept_packages(text))
        for problem in install_problems(text):
            failures.append(f"{path.relative_to(examples.parent).as_posix()}: {problem}")
    return lists, second, passes, keeps, failures


class KeptStockLauncherInstallTest(unittest.TestCase):
    def test_no_list_passes_a_stock_launcher_it_keeps(self):
        lists, second, passes, keeps, failures = sweep(ROOT / "examples")
        self.assertGreaterEqual(lists, 200, "the sweep read too few lists to be meaningful")
        self.assertGreaterEqual(second, 5, "the sweep read no second list: paths beside debloat.txt are "
                                           "applied the same way and are held to the same rule")
        self.assertGreaterEqual(passes, 8, "the sweep found too few STOCK_LAUNCHER=<package> commands")
        self.assertGreaterEqual(keeps, 10, "the sweep found too few packages a list keeps enabled")
        self.assertEqual([], failures, "\n".join(failures))

    def test_no_list_calls_a_known_stock_launcher_unknown(self):
        known = set(sh_known())
        self.assertGreaterEqual(len(known), 2, "KNOWN_STOCK_LAUNCHERS did not read as a list")
        read = 0
        failures = []
        for path in list_files(ROOT / "examples"):
            read += 1
            for problem in unknown_claims(path.read_text(encoding="utf-8"), known):
                failures.append(f"{path.relative_to(ROOT).as_posix()}: {problem}")
        self.assertGreaterEqual(read, 200, "the sweep read too few lists to be meaningful")
        self.assertEqual([], failures, "\n".join(failures))


class Readers(unittest.TestCase):
    """The rules above, on lists written here, so neither sweep can pass by reading nothing."""

    def test_a_header_that_keeps_and_passes_the_launcher_is_reported(self):
        text = (
            "# Stock launcher: com.mitv.tvhome (HOME activity .MainActivityUserMode) - KEEP IT ENABLED,\n"
            "# see \"Home\" below. Install with: STOCK_LAUNCHER=com.mitv.tvhome launcher/build-and-install.sh\n"
            "#\n"
            "com.miui.analytics\n"
        )
        problems = install_problems(text)
        self.assertEqual(1, len(problems))
        self.assertIn("line 2 passes STOCK_LAUNCHER=com.mitv.tvhome", problems[0])
        self.assertIn("note at line 1", problems[0])

    def test_a_keep_note_further_down_counts(self):
        text = (
            "# Stock launcher: com.mitv.tvhome. Install with: STOCK_LAUNCHER=com.mitv.tvhome\n"
            "#\n"
            "# Notes from this firmware:\n"
            "# - Home stays com.mitv.tvhome: with com.mitv.tvhome disabled Home falls to\n"
            "#   com.android.tv.settings/.system.FallbackHome. Keep it enabled.\n"
            "# - Network ADB closes on every reboot.\n"
        )
        self.assertEqual({"com.mitv.tvhome": 4, "com.android.tv.settings": 4}, kept_packages(text))
        self.assertEqual(1, len(install_problems(text)))

    def test_none_keeps_it(self):
        text = (
            "# Stock launcher: com.mitv.tvhome - KEEP IT ENABLED. Install with:\n"
            "# STOCK_LAUNCHER=none launcher/build-and-install.sh\n"
        )
        self.assertEqual([], passed_packages(text))
        self.assertEqual([], install_problems(text))

    def test_left_enabled_until_the_swap_is_quiet(self):
        text = (
            "# Run launcher/build-and-install.sh with STOCK_LAUNCHER=net.oqee.launcher; it disables OQEE\n"
            "# after the swap. setupwraith owns a saved Wi-Fi network here: leave it enabled.\n"
            "\n"
            "# Stock launcher: left enabled here. STOCK_LAUNCHER=net.oqee.launcher makes\n"
            "# build-and-install.sh disable it after the swap.\n"
            "# net.oqee.launcher\n"
        )
        self.assertEqual({}, kept_packages(text))
        self.assertEqual([], install_problems(text))

    def test_keeping_another_package_by_name_is_quiet(self):
        text = (
            "# - Keep com.xiaomi.mitv.smartpanel enabled (Menu-button panel); STOCK_LAUNCHER=com.mitv.tvhome\n"
            "#   disables PatchWall after the swap.\n"
        )
        self.assertEqual({"com.xiaomi.mitv.smartpanel": 1}, kept_packages(text))
        self.assertEqual([], install_problems(text))

    def test_a_known_launcher_called_unknown_is_reported(self):
        text = (
            "# Stock launcher: com.mitv.tvhome (HOME activity .MainActivityUserMode). It is not in\n"
            "# KNOWN_STOCK_LAUNCHERS, so install with: STOCK_LAUNCHER=com.mitv.tvhome launcher/build-and-install.sh\n"
        )
        problems = unknown_claims(text, {"com.mitv.tvhome"})
        self.assertEqual(1, len(problems))
        self.assertIn("com.mitv.tvhome is not in KNOWN_STOCK_LAUNCHERS", problems[0])

    def test_an_unknown_launcher_called_unknown_is_quiet(self):
        text = (
            "# Stock launcher/Home:\n"
            "#   com.dangbei.TVHomeLauncher/com.dangbei.leard.leradlauncher.ui.sony.main.MainActivity\n"
            "# It is NOT in build-and-install.sh's KNOWN_STOCK_LAUNCHERS, so install the reference launcher with\n"
            "# STOCK_LAUNCHER=none and leave Dangbei enabled as the fallback.\n"
        )
        self.assertEqual([], unknown_claims(text, {"com.google.android.tvlauncher", "com.mitv.tvhome"}))

    def test_paragraphs_end_at_a_bare_hash_a_bullet_and_a_package_line(self):
        text = "# a\n#   b\n#\n# - c\n# - d\ncom.x\n# e\n"
        self.assertEqual([(1, "a b"), (4, "- c"), (5, "- d"), (7, "e")], notes(text))


class TheWalk(unittest.TestCase):
    """The sweep over a tree of lists written here, so a walk that goes back to debloat.txt alone fails."""

    def tree(self, files):
        """A temporary repository root holding `examples/<folder>/<file>` for every (folder, file, text)."""
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        root = Path(folder.name)
        for name, text in files.items():
            path = root / "examples" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        return root

    def test_a_second_list_that_passes_a_launcher_it_keeps_is_reported(self):
        # The header the two Xiaomi lists shipped, moved into the second list a folder can carry: the
        # file an owner opens instead of debloat.txt, so the same rule has to reach it.
        root = self.tree({
            "x/debloat.txt": "# Stock launcher: com.mitv.tvhome\ncom.example.one\n",
            "x/debloat-keep-cast.txt":
                "# Stock launcher: com.mitv.tvhome - KEEP IT ENABLED with the stick's own Home, so the\n"
                "# phone's cast receiver keeps a Home to answer from.\n"
                "# Install with: STOCK_LAUNCHER=com.mitv.tvhome launcher/build-and-install.sh\n"
                "com.example.one\n",
        })
        lists, second, _, _, failures = sweep(root / "examples")
        self.assertEqual((2, 1), (lists, second))
        self.assertEqual(1, len(failures), failures)
        self.assertTrue(failures[0].startswith("examples/x/debloat-keep-cast.txt: "), failures[0])
        self.assertIn("passes STOCK_LAUNCHER=com.mitv.tvhome", failures[0])
        self.assertIn("note at line 1", failures[0])

    def test_a_second_list_is_read_where_the_folder_ships_no_debloat_txt(self):
        root = self.tree({
            "y/debloat-32-inch.txt": "# Stock launcher: com.google.android.tvlauncher. It is not in\n"
                                     "# KNOWN_STOCK_LAUNCHERS, so install with STOCK_LAUNCHER=none.\n"
                                     "com.example.two\n",
        })
        lists, second, _, _, failures = sweep(root / "examples")
        self.assertEqual((1, 1), (lists, second))
        self.assertEqual([], failures)
        problems = unknown_claims((root / "examples/y/debloat-32-inch.txt").read_text(encoding="utf-8"),
                                  {"com.google.android.tvlauncher"})
        self.assertEqual(1, len(problems), problems)

    def test_a_file_that_is_not_a_list_is_not_read(self):
        root = self.tree({
            "z/debloat.txt": "com.example.one\n",
            "z/debloat.txt.bak": "# KEEP IT ENABLED. STOCK_LAUNCHER=com.mitv.tvhome\ncom.mitv.tvhome\n",
            "z/notes.md": "# KEEP IT ENABLED. STOCK_LAUNCHER=com.mitv.tvhome\n",
        })
        lists, second, _, _, failures = sweep(root / "examples")
        self.assertEqual((1, 0), (lists, second))
        self.assertEqual([], failures)


if __name__ == "__main__":
    unittest.main()

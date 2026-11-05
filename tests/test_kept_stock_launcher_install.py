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

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
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


class KeptStockLauncherInstallTest(unittest.TestCase):
    def test_no_list_passes_a_stock_launcher_it_keeps(self):
        lists = passes = keeps = 0
        failures = []
        for path in sorted((ROOT / "examples").glob("*/debloat.txt")):
            lists += 1
            text = path.read_text(encoding="utf-8")
            passes += len(passed_packages(text))
            keeps += len(kept_packages(text))
            for problem in install_problems(text):
                failures.append(f"examples/{path.parent.name}/debloat.txt: {problem}")
        self.assertGreaterEqual(lists, 200, "the sweep read too few lists to be meaningful")
        self.assertGreaterEqual(passes, 8, "the sweep found too few STOCK_LAUNCHER=<package> commands")
        self.assertGreaterEqual(keeps, 10, "the sweep found too few packages a list keeps enabled")
        self.assertEqual([], failures, "\n".join(failures))

    def test_no_list_calls_a_known_stock_launcher_unknown(self):
        known = set(sh_known())
        self.assertGreaterEqual(len(known), 2, "KNOWN_STOCK_LAUNCHERS did not read as a list")
        failures = []
        for path in sorted((ROOT / "examples").glob("*/debloat.txt")):
            for problem in unknown_claims(path.read_text(encoding="utf-8"), known):
                failures.append(f"examples/{path.parent.name}/debloat.txt: {problem}")
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


if __name__ == "__main__":
    unittest.main()

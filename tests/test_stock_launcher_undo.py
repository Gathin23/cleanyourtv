"""A list's undo has to pass the stock launcher the list itself names.

`tools/restore.sh` takes the stock launcher as its second argument and hands it to
`tools/recovery.py` as `--commands <list> <stock>`; `restore.ps1` does the same with `-Stock`. With no
argument both fall back to `com.google.android.tvlauncher`, and recovery.py then plans
`pm enable com.google.android.tvlauncher` and a `set-home-activity` that resolves that package (see
`restore_commands`: `fallback = stock or "com.google.android.tvlauncher"`).

A list that says its stock launcher is something else - launcherx on Google TV, a vendor launcher on
an OEM board - and then disables that package gets an undo that reaches for a package the set never
shipped: `pm enable` answers "unknown package" and the resolver finds nothing, so Home is left where
the cleanup put it instead of going back. That is the moment an owner runs the undo, so the two lines
have to agree.

The rule below reads both facts out of the list itself: the packages on active lines, the package a
line names as the stock launcher (`Stock launcher:`, `Stock launcher is`, `Stock launcher package:`,
`STOCK_LAUNCHER=`, or an active line whose own comment says `# stock launcher` / `# stock home`), and
the packages the file's restore commands pass. A named stock launcher that the list disables has to be
one of those passed packages; `com.google.android.tvlauncher` is exempt because it is the default.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

PACKAGE = r"(?:com|cn|org|io|net|tv|de|it)\.[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+"
PACKAGE_RE = re.compile(PACKAGE)
# The package a header line names as the stock launcher: `Stock launcher: x`, `Stock launcher is x`,
# `Stock launcher package: x`, `Stock Home: x`. A possessive ("the stock launcher's rows") is not it.
STOCK_CLAIM = re.compile(
    r"(?i)stock\s+(?:launcher|home)(?!'s)\s*(?:package)?\s*(?::|is|=)\s*(" + PACKAGE + r")")
STOCK_ENV = re.compile(r"STOCK_LAUNCHER\s*[=:]\s*(" + PACKAGE + r")")
# An active line's own trailing comment, e.g. `com.google.android.apps.tv.launcherx  # stock Google TV
# home; com.gathin.tvhome is Home now`. The phrase has to end the naming: "stock launcher
# recommendation rows" (com.google.android.tvrecommendations) is not a launcher.
STOCK_COMMENT = re.compile(
    r"(?i)^\s*stock\s+(?:\S+\s+){0,3}(?:launcher|home)\b\s*(?=$|[;,.()]|\b(?:is|now|at|on|and)\b)")
# `restore.sh <list> [package]`, `restore.ps1 <list> [-Stock package]`.
RESTORE = re.compile(r"restore\.(?:sh|ps1)\s+(\S+)")
PASSED = re.compile(r"\s+(?:-Stock\s+)?[\"`]?(" + PACKAGE + r")")

DEFAULT_STOCK = "com.google.android.tvlauncher"


def active_packages(text):
    """(line number, package) for every package on an active line of a list."""
    found = []
    for number, line in enumerate(text.splitlines(), 1):
        code = line.split("#", 1)[0].split()
        if code and PACKAGE_RE.fullmatch(code[0]):
            found.append((number, code[0]))
    return found


def stock_claims(text):
    """The packages the list names as its own stock launcher, mapped to the lines that name them."""
    claims = {}
    for number, line in enumerate(text.splitlines(), 1):
        code, _, comment = line.partition("#")
        package = code.split()[0] if code.split() else None
        if package and PACKAGE_RE.fullmatch(package) and STOCK_COMMENT.match(comment):
            claims.setdefault(package, []).append(number)
        for pattern in (STOCK_CLAIM, STOCK_ENV):
            for match in pattern.finditer(line):
                claims.setdefault(match.group(1), []).append(number)
    return claims


def restore_invocations(text):
    """(line number, package or None) for every restore command the file shows."""
    invocations = []
    for number, line in enumerate(text.splitlines(), 1):
        for match in RESTORE.finditer(line):
            # The list path has to follow, so prose like "restore.sh would turn them ON" is not a
            # command. Only the rest of that command is read, not the next sentence.
            if not match.group(1).endswith(".txt"):
                continue
            rest = line[match.end():]
            passed = PASSED.match(rest)
            invocations.append((number, passed.group(1) if passed else None))
    return invocations


def undo_problems(text):
    """Why this list's undo does not reach its own stock launcher, one sentence each."""
    active = {package for _, package in active_packages(text)}
    passed = {package for _, package in restore_invocations(text) if package}
    problems = []
    for package, lines in sorted(stock_claims(text).items()):
        if package not in active or package == DEFAULT_STOCK or package in passed:
            continue
        problems.append(
            f"line {lines[0]} calls {package} the stock launcher and the list disables it, but the "
            f"restore command in this file does not pass it (restore.sh falls back to "
            f"{DEFAULT_STOCK}, which this set does not ship)")
    return problems


class StockLauncherUndoTest(unittest.TestCase):
    def test_every_list_passes_the_stock_launcher_it_disables(self):
        lists = files = invocations = claims = passed_somewhere = 0
        failures = []
        for path in sorted((ROOT / "examples").glob("*/debloat.txt")):
            files += 1
            text = path.read_text(encoding="utf-8")
            calls = restore_invocations(text)
            invocations += len(calls)
            passed_somewhere += sum(1 for _, package in calls if package)
            claims += len(stock_claims(text))
            for problem in undo_problems(text):
                failures.append(f"examples/{path.parent.name}/debloat.txt: {problem}")
        lists = files
        self.assertGreaterEqual(lists, 200, "the sweep read too few lists to be meaningful")
        self.assertGreaterEqual(invocations, 150, "the sweep found too few restore commands")
        self.assertGreaterEqual(claims, 150, "the sweep found too few stock-launcher statements")
        self.assertGreaterEqual(passed_somewhere, 100,
                                "the sweep found too few undos that pass a package at all")
        self.assertEqual([], failures, "\n".join(failures))

    def test_an_undo_without_the_stock_launcher_is_reported(self):
        text = (
            "# Stock launcher: com.google.android.apps.tv.launcherx (pass it as STOCK_LAUNCHER)\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "\n"
            "com.google.android.apps.tv.launcherx\n"
        )
        problems = undo_problems(text)
        self.assertEqual(1, len(problems))
        self.assertIn("com.google.android.apps.tv.launcherx", problems[0])
        self.assertIn("does not pass it", problems[0])

    def test_an_undo_that_passes_it_is_quiet(self):
        text = (
            "# Stock launcher: com.google.android.apps.tv.launcherx\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt com.google.android.apps.tv.launcherx\n"
            "com.google.android.apps.tv.launcherx\n"
        )
        self.assertEqual([], undo_problems(text))
        self.assertEqual([(2, "com.google.android.apps.tv.launcherx")], restore_invocations(text))

    def test_the_powershell_twin_passes_it_with_dash_stock(self):
        quiet = (
            "# Stock Home: com.google.android.apps.tv.launcherx\n"
            "# Undo:    $env:TV_HOST=\"<tv ip>\"; tools\\restore.ps1 examples\\x\\debloat.txt -Stock com.google.android.apps.tv.launcherx\n"
            "com.google.android.apps.tv.launcherx\n"
        )
        self.assertEqual([], undo_problems(quiet))
        loud = quiet.replace(" -Stock com.google.android.apps.tv.launcherx", "")
        self.assertEqual(1, len(undo_problems(loud)))

    def test_the_default_stock_launcher_needs_no_argument(self):
        text = (
            "# Stock launcher: com.google.android.tvlauncher (the default in restore.sh)\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "com.google.android.tvlauncher\n"
        )
        self.assertEqual([], undo_problems(text))

    def test_a_stock_launcher_the_list_keeps_needs_nothing(self):
        text = (
            "# Stock launcher: com.google.android.apps.tv.launcherx\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "com.homwee.store\n"
        )
        self.assertEqual([], undo_problems(text))

    def test_an_active_lines_stock_home_comment_counts(self):
        text = (
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "com.google.android.apps.tv.launcherx         # stock Google TV home; com.gathin.tvhome is Home now\n"
        )
        self.assertEqual(1, len(undo_problems(text)))

    def test_recommendation_rows_are_not_the_stock_launcher(self):
        text = (
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "com.google.android.tvrecommendations  # stock launcher recommendation rows\n"
        )
        self.assertEqual({}, stock_claims(text))
        self.assertEqual([], undo_problems(text))

    def test_prose_about_restore_is_not_a_command(self):
        text = (
            "# Disabled from the factory, so not listed (restore.sh would turn them ON): com.example.tool\n"
            "#   Android ignores disabled components, and restore.sh brings both back untouched.\n"
        )
        self.assertEqual([], restore_invocations(text))
        self.assertEqual([], undo_problems(text))

    def test_the_list_path_has_to_follow_the_script(self):
        self.assertEqual([], restore_invocations("# restore.sh\n"))
        self.assertEqual([(1, None)], restore_invocations("tools/restore.sh examples/x/debloat.txt\n"))


if __name__ == "__main__":
    unittest.main()

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

A list can also leave the stock launcher off its active lines and let `launcher/build-and-install.sh`
disable it once the new launcher owns Home. That counts as disabling it when the line naming it says so
(`build-and-install.sh disables it`), or when the list hands the package to the installer as
`STOCK_LAUNCHER=<package>`, the setting the installer disables. A line can also name the package
itself (`build-and-install.sh disables launcherx`), and `STOCK_LAUNCHER=<that package>` is the stock
launcher named on that line or before it. A package the installer is said to disable is checked even
when no line calls it the stock launcher.

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
# A line that says the install script disables the stock launcher it names: `launcher/build-and-install.sh
# disables it`, `(build-and-install.sh disables it when the launcher takes Home)`. "never disables",
# "does not disable" and "it would disable" are not it.
INSTALL_DISABLES = re.compile(
    r"(?i)build-and-install(?:\.sh|\.ps1)?\b(?:(?!\bnever\b)[^.])*\bdisables\b")
# The same, with the package it disables named: `launcher/build-and-install.sh disables launcherx
# itself`, `(build-and-install disables com.google.android.tvlauncher itself)`.
INSTALL_DISABLES_NAMED = re.compile(
    r"(?i)build-and-install(?:\.sh|\.ps1)?\b(?:(?!\bnever\b)[^.])*\bdisables\s+(launcherx\b|"
    + PACKAGE + r")")
# Google TV's stock launcher as list headers shorten it.
SHORT_NAMES = {"launcherx": "com.google.android.apps.tv.launcherx"}
# `STOCK_LAUNCHER=<that package>`: the stock launcher named on this line or an earlier one.
STOCK_ENV_BACK = re.compile(r"STOCK_LAUNCHER\s*=\s*<th(?:at|is) package>")
# An active line's own trailing comment, e.g. `com.google.android.apps.tv.launcherx  # stock Google TV
# home; com.gathin.tvhome is Home now`. The phrase has to end the naming: "stock launcher
# recommendation rows" (com.google.android.tvrecommendations) is not a launcher.
STOCK_COMMENT = re.compile(
    r"(?i)^\s*stock\s+(?:\S+\s+){0,3}(?:launcher|home)\b\s*(?=$|[;,.()]|\b(?:is|now|at|on|and)\b)")
# `restore.sh <list> [package]`, `restore.ps1 <list> [-Stock package]`.
RESTORE = re.compile(r"restore\.(?:sh|ps1)\s+(\S+)")
PASSED = re.compile(r"\s+(?:-Stock\s+)?[\"`]?(" + PACKAGE + r")")

DEFAULT_STOCK = "com.google.android.tvlauncher"


def list_files(root):
    """Every list under root's examples/: each folder's debloat.txt and a second list beside it, such
    as google-chromecast-gtv-4k/debloat-keep-cast.txt, which has its own undo line."""
    return sorted((root / "examples").glob("*/debloat*.txt"))


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


def install_disables(text):
    """The packages the list says launcher/build-and-install.sh disables, mapped to the lines that say so.

    A package the line names as the stock launcher counts, a package the line says the installer
    disables by name (`disables launcherx`), and every `STOCK_LAUNCHER=<package>`: that is the package
    the installer disables. `STOCK_LAUNCHER=none` names no package, and `STOCK_LAUNCHER=<that package>`
    is the stock launcher named on that line or before it.
    """
    disabled = {}
    named_stock = None
    for number, line in enumerate(text.splitlines(), 1):
        claimed = [match.group(1) for match in STOCK_CLAIM.finditer(line)]
        if claimed:
            named_stock = claimed[-1]
        named = [match.group(1) for match in STOCK_ENV.finditer(line)]
        if INSTALL_DISABLES.search(line):
            named += claimed
        named += [SHORT_NAMES.get(match.group(1).lower(), match.group(1))
                  for match in INSTALL_DISABLES_NAMED.finditer(line)]
        if named_stock and STOCK_ENV_BACK.search(line):
            named.append(named_stock)
        for package in dict.fromkeys(named):
            disabled.setdefault(package, []).append(number)
    return disabled


def undo_problems(text):
    """Why this list's undo does not reach its own stock launcher, one sentence each."""
    active = {package for _, package in active_packages(text)}
    passed = {package for _, package in restore_invocations(text) if package}
    claims = stock_claims(text)
    installer = install_disables(text)
    problems = []
    for package in sorted(set(claims) | set(installer)):
        if package == DEFAULT_STOCK or package in passed:
            continue
        if package in active:
            how = "the list disables it"
        elif package in installer:
            how = f"launcher/build-and-install.sh disables it (line {installer[package][0]})"
        else:
            continue
        if package in claims:
            said = f"line {claims[package][0]} calls {package} the stock launcher and {how}"
        else:
            said = f"line {installer[package][0]} says launcher/build-and-install.sh disables {package}"
        problems.append(
            f"{said}, but the restore command in this file does not pass it (restore.sh falls back "
            f"to {DEFAULT_STOCK}, which this set does not ship)")
    return problems


def sweep(root):
    """What the lists under root's examples/ show: how many lists, restore commands, stock-launcher
    statements and undos that pass a package there are, and `list: problem` for each undo that misses
    the stock launcher its list disables."""
    lists = invocations = claims = passed_somewhere = 0
    failures = []
    for path in list_files(root):
        lists += 1
        text = path.read_text(encoding="utf-8")
        calls = restore_invocations(text)
        invocations += len(calls)
        passed_somewhere += sum(1 for _, package in calls if package)
        claims += len(stock_claims(text))
        for problem in undo_problems(text):
            failures.append(f"{path.relative_to(root).as_posix()}: {problem}")
    return lists, invocations, claims, passed_somewhere, failures


class StockLauncherUndoTest(unittest.TestCase):
    def test_every_list_passes_the_stock_launcher_it_disables(self):
        lists, invocations, claims, passed_somewhere, failures = sweep(ROOT)
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

    def test_a_stock_launcher_the_install_script_disables_counts(self):
        text = (
            "# Stock launcher:  com.google.android.apps.tv.launcherx (build-and-install.sh disables it when the launcher takes Home)\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "com.google.android.feedback\n"
            "# com.google.android.apps.tv.launcherx\n"
        )
        self.assertEqual({"com.google.android.apps.tv.launcherx": [1]}, install_disables(text))
        problems = undo_problems(text)
        self.assertEqual(1, len(problems))
        self.assertIn("build-and-install.sh disables it (line 1)", problems[0])
        passed = text.replace("debloat.txt\n", "debloat.txt com.google.android.apps.tv.launcherx\n")
        self.assertEqual([], undo_problems(passed))

    def test_the_sentence_after_the_claim_counts(self):
        text = (
            "# - The stock home is com.google.android.apps.tv.launcherx. launcher/build-and-install.sh disables it.\n"
            "# Undo:    TV_HOST=<box ip> tools/restore.sh examples/x/debloat.txt\n"
        )
        self.assertEqual(1, len(undo_problems(text)))

    def test_stock_launcher_in_the_environment_is_what_the_installer_disables(self):
        text = (
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "# Stock launcher: com.jamdeo.tv.vod (JuUI). launcher/build-and-install.sh does not know that name:\n"
            "#   TV_HOST=<tv ip> HOME_KEY_SERVICE=1 STOCK_LAUNCHER=com.jamdeo.tv.vod launcher/build-and-install.sh\n"
        )
        self.assertEqual({"com.jamdeo.tv.vod": [3]}, install_disables(text))
        self.assertEqual(1, len(undo_problems(text)))
        kept = text.replace("STOCK_LAUNCHER=com.jamdeo.tv.vod", "STOCK_LAUNCHER=none")
        self.assertEqual({}, install_disables(kept))
        self.assertEqual([], undo_problems(kept))

    def test_the_installer_disabling_launcherx_by_name_counts(self):
        text = (
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "com.google.android.feedback\n"
            "# Batch 5 - with the launcher in this repo installed (launcher/build-and-install.sh disables launcherx)\n"
            "com.google.android.tungsten.setupwraith\n"
        )
        self.assertEqual({}, stock_claims(text))
        self.assertEqual({"com.google.android.apps.tv.launcherx": [3]}, install_disables(text))
        problems = undo_problems(text)
        self.assertEqual(1, len(problems))
        self.assertIn("line 3 says launcher/build-and-install.sh disables "
                      "com.google.android.apps.tv.launcherx", problems[0])
        passed = text.replace("debloat.txt\n", "debloat.txt com.google.android.apps.tv.launcherx\n")
        self.assertEqual([], undo_problems(passed))
        default = (
            "# --- After launcher/ owns Home (build-and-install disables com.google.android.tvlauncher itself)\n"
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
        )
        self.assertEqual({DEFAULT_STOCK: [1]}, install_disables(default))
        self.assertEqual([], undo_problems(default))

    def test_stock_launcher_that_package_is_the_one_named_before(self):
        text = (
            "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
            "# Stock launcher is cn.zeasn.launcher.vstoresubclient.toptech (Zeasn). It is not in this list: disable it only\n"
            "# once a replacement Home is installed (launcher/build-and-install.sh with STOCK_LAUNCHER=<that package>).\n"
        )
        self.assertEqual({"cn.zeasn.launcher.vstoresubclient.toptech": [3]}, install_disables(text))
        problems = undo_problems(text)
        self.assertEqual(1, len(problems))
        self.assertIn("line 2 calls cn.zeasn.launcher.vstoresubclient.toptech the stock launcher and "
                      "launcher/build-and-install.sh disables it (line 3)", problems[0])
        passed = text.replace("debloat.txt\n", "debloat.txt cn.zeasn.launcher.vstoresubclient.toptech\n")
        self.assertEqual([], undo_problems(passed))
        nothing_named = text.splitlines(True)[2]
        self.assertEqual({}, install_disables(nothing_named))

    def test_an_installer_that_leaves_it_alone_needs_nothing(self):
        for note in ("launcher/build-and-install.sh never disables it here",
                     "launcher/build-and-install.sh never disables launcherx here",
                     "launcher/build-and-install.sh does not disable it",
                     "do not run build-and-install.sh with its defaults: it would disable it"):
            with self.subTest(note=note):
                text = (
                    f"# Stock launcher: com.google.android.apps.tv.launcherx ({note})\n"
                    "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
                )
                self.assertEqual({}, install_disables(text))
                self.assertEqual([], undo_problems(text))

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

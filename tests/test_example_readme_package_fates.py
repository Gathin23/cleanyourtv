"""Checks the fate each examples/README.md row claims for a package against that folder's list.

A row answers "what does this list do?" with a package name in backticks and one word
after it: `kept` (the list leaves the package alone) or `disabled` (the list turns it
off). tools/disable.sh sends the bare package names it finds in debloat.txt and strips
everything from the first `#`, so a claim in the row and a comment in the list can both
read "skipped" about a package the script disables anyway - which is what
examples/sankey-smart2ktvse did with com.zeasn.livetv, the software layer under its
HDMI inputs (#1203). This test reads the claim where a reader reads it and compares it
with the set the script would actually send.

Two packages are exempt from the "disabled" side because they are not disabled by the
list: the stock launchers launcher/build-and-install.sh takes Home from (the list only
names them in a header comment). The exempt set is read out of that script rather than
copied here.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"

# A row of the examples table: | `folder/` | TV | OS | Notes |
ROW = re.compile(r"^\|\s*`([A-Za-z0-9][A-Za-z0-9._-]*)/`\s*\|(.*)\|\s*$")
# A package name in backticks. Deliberately loose about the shape so a typo in a row is
# compared rather than skipped; test_list_format is what refuses a bad name in a list.
BACKTICKED = re.compile(r"`([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)`")
# What a row says about that package, right after the closing backtick.
FATE = re.compile(r"^\s*(?:is\s+)?(kept|disabled)\b", re.IGNORECASE)


def claims(text):
    """(package, "kept"|"disabled") for every backticked package a row gives a fate to.

    Only the words straight after the name count, so "`pkg` (Chromecast kept)" talks
    about Chromecast, not about pkg, and "`pkg` deliberately left out" claims nothing.
    """
    found = []
    for match in BACKTICKED.finditer(text):
        fate = FATE.match(text[match.end():])
        if fate:
            found.append((match.group(1), fate.group(1).lower()))
    return found


def list_packages(path):
    """The package names tools/disable.sh would send for this list.

    Same parse as the script and tests/test_examples.py: everything from the first `#`
    is a comment, blank lines are skipped, the rest is the package name.
    """
    packages = set()
    for line in Path(path).read_text(encoding="utf-8").split("\n"):
        body = line.split("#", 1)[0].strip()
        if body:
            packages.add(body)
    return packages


def stock_launchers():
    """The stock launchers launcher/build-and-install.sh disables once Home has moved."""
    script = (ROOT / "launcher" / "build-and-install.sh").read_text(encoding="utf-8")
    line = next(l for l in script.split("\n") if l.startswith("KNOWN_STOCK_LAUNCHERS="))
    return set(line.split("=", 1)[1].strip().strip('"').split())


def table_rows(readme):
    """(folder, line number, notes text) for every row of the examples table that has a folder."""
    rows = []
    lines = Path(readme).read_text(encoding="utf-8").split("\n")
    start = next(i for i, l in enumerate(lines) if l.startswith("| Folder |"))
    for number, line in enumerate(lines[start + 2:], start + 3):
        if not line.startswith("|"):
            break
        match = ROW.match(line)
        if match and (EXAMPLES / match.group(1)).is_dir():
            rows.append((match.group(1), number, match.group(2)))
    return rows


def disagreements(folder, notes, active, exempt):
    """Lines describing every claim in `notes` that the list does not back up."""
    problems = []
    for package, fate in claims(notes):
        if fate == "kept" and package in active:
            problems.append(
                f"{folder}: says {package} is kept, but its debloat.txt disables it "
                f"(a trailing comment does not keep a package off the TV)")
        if fate == "disabled" and package not in active and package not in exempt:
            problems.append(
                f"{folder}: says {package} is disabled, but its debloat.txt does not list it")
    return problems


class ReadmePackageFatesTest(unittest.TestCase):
    def test_every_claim_matches_the_list(self):
        exempt = stock_launchers()
        problems = []
        for folder, number, notes in table_rows(EXAMPLES / "README.md"):
            active = list_packages(EXAMPLES / folder / "debloat.txt")
            problems += [f"examples/README.md:{number} {p}"
                         for p in disagreements(folder, notes, active, exempt)]
        self.assertFalse(problems, "\n".join(problems))

    def test_it_reads_the_rows_that_state_a_fate(self):
        # So the sweep above cannot pass by reading nothing: a parse change that stops
        # matching would otherwise report zero claims instead of failing.
        stated = [(f, n) for f, n, notes in table_rows(EXAMPLES / "README.md") if claims(notes)]
        self.assertGreaterEqual(len(stated), 8,
                                f"only {len(stated)} rows state a package's fate: {stated}")


class ReaderTest(unittest.TestCase):
    def test_claims_reads_both_fates(self):
        text = ("kept `com.a.b` kept, `com.c.d` disabled, `com.e.f` is kept")
        self.assertEqual(claims(text),
                         [("com.a.b", "kept"), ("com.c.d", "disabled"), ("com.e.f", "kept")])

    def test_claims_ignores_a_package_a_row_says_nothing_about(self):
        text = ("`com.a.b` deliberately left out because it is a system app; "
                "`com.c.d` (shown here, kept by another list) is fine")
        self.assertEqual(claims(text), [])

    def test_claims_ignores_a_fate_word_that_belongs_to_another_package(self):
        # "Chromecast kept" after an unrelated package name is not a claim about it.
        self.assertEqual(claims("AirScreen off (`com.ionitech.airscreen`; Chromecast kept)"), [])

    def test_list_packages_reads_the_line_not_its_comment(self):
        path = ROOT / "tests" / "_fates_list.txt"
        path.write_text("com.a.b     # kept\n"
                        "# com.c.d    # disabled\n"
                        "\n"
                        "com.e.f\n", encoding="utf-8")
        try:
            self.assertEqual(list_packages(path), {"com.a.b", "com.e.f"})
        finally:
            path.unlink()

    def test_the_exempt_stock_launchers_come_from_the_install_script(self):
        exempt = stock_launchers()
        self.assertIn("com.google.android.tvlauncher", exempt)
        self.assertGreaterEqual(len(exempt), 4,
                                f"the install script's stock launchers read as {exempt}")

    def test_a_stock_launcher_may_be_claimed_disabled_without_a_line(self):
        # The install script disables it, not the list, so the row is still right.
        problems = disagreements("tcl-x", "stock launcher `com.google.android.tvlauncher` disabled",
                                 active=set(), exempt={"com.google.android.tvlauncher"})
        self.assertEqual(problems, [])

    def test_a_kept_claim_on_an_active_line_is_reported(self):
        problems = disagreements("sankey", "Live TV `com.zeasn.livetv` kept (needed for HDMI)",
                                 active={"com.zeasn.livetv"}, exempt=set())
        self.assertEqual(len(problems), 1)
        self.assertIn("com.zeasn.livetv", problems[0])
        self.assertIn("disables it", problems[0])

    def test_a_disabled_claim_without_a_line_is_reported(self):
        problems = disagreements("sankey", "camera `com.android.camera2` disabled",
                                 active={"com.a.b"}, exempt=set())
        self.assertEqual(len(problems), 1)
        self.assertIn("does not list it", problems[0])

    def test_rows_are_read_from_the_examples_table_only(self):
        rows = table_rows(EXAMPLES / "README.md")
        folders = [f for f, _, _ in rows]
        self.assertIn("sankey-smart2ktvse", folders)
        # The device-notes table at the end of the page indexes no list and is not read.
        self.assertTrue(all((EXAMPLES / f / "debloat.txt").is_file() for f in folders))


if __name__ == "__main__":
    unittest.main()

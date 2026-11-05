"""The font the README card scripts take is not in this repo, and nothing said so.

`docs/workflows/press.md` step 2 is where a contributor redraws `docs/featured.png` and `docs/card.jpg`, and
it prints two commands that pass `Fredoka.ttf` as the first argument. No folder of this repo holds that file -
Fredoka is the variable TTF from Google Fonts, which only `tools/readme_stats.py`'s docstring ever said - so
a contributor who installed Pillow as the page asks and then ran the command the page prints got:

    OSError: cannot open resource

with no file name in it and no way to tell whether the font, the argument order or the draw call was wrong.
Before that, running any of the three with no arguments at all died on `IndexError: list index out of range`,
because each one read `sys.argv[1], sys.argv[2]` at the top of the module.

The three scripts now take the font through one guard: it names the path it could not open, says where the
font comes from, and prints the two arguments when there are not two. It runs before the Pillow import, since
the font is the argument the reader brings themselves and a missing one is the likelier failure - so a run
with no Pillow still reports the font first, which is what makes this testable in CI, where Pillow is absent.

`tools/gen_tables.py --font` hands the font to two of these scripts with `check=True`, so a wrong path there
became a `CalledProcessError` traceback around their message; it now refuses the path itself.

A path that exists is not yet a font, and both of the shapes a reader is likely to hand over reached Pillow:
Google Fonts serves Fredoka as a zip holding the variable `Fredoka[wdth,wght].ttf` and a folder of static
single-weight files beside it, so the zip was `OSError: unknown file format` and a static file was
`OSError: invalid argument` - either way a Pillow traceback in the middle of a draw, naming neither the
argument nor what it should have been. The three scripts now open the font once, before anything is drawn,
and answer both with the file named, the variable TTF named, and 'Nothing was written.'

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
PRESS = ROOT / "docs" / "workflows" / "press.md"

# The three scripts that draw the card, with the picture each one writes.
CARD_SCRIPTS = {
    "readme_stats.py": "stats.png",
    "readme_featured.py": "featured.png",
    "readme_card.py": "card.jpg",
}

# What the guard says the reader cannot use, and what a wrong path must not be mistaken for.
NOT_THE_FONT = "requirements-perf.txt"


def shadow_dir(tmp):
    """A PIL package that cannot be imported, so a script behaves as on a venv without Pillow."""
    pkg = Path(tmp) / "PIL"
    pkg.mkdir(exist_ok=True)
    (pkg / "__init__.py").write_text('raise ImportError("No module named \'PIL\'")\n', encoding="utf-8")
    return str(tmp)


def run(argv, tmp):
    env = dict(os.environ)
    env["PYTHONPATH"] = shadow_dir(tmp)
    return subprocess.run([sys.executable, *argv], cwd=ROOT, env=env, capture_output=True, text=True)


class FontArgumentTest(unittest.TestCase):
    def test_the_shadow_really_hides_pillow(self):
        # Without this every check below could be reading the Pillow line and proving nothing.
        with tempfile.TemporaryDirectory() as tmp:
            done = run(["-c", "import PIL"], tmp)
        self.assertNotEqual(done.returncode, 0, "the PIL shadow did not take effect")

    def test_a_font_that_is_not_there_is_reported_by_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    out = Path(tmp) / picture
                    done = run([str(TOOLS / name), "Fredoka.ttf", str(out)], tmp)
                    self.assertNotEqual(done.returncode, 0, f"{name} accepted a font that does not exist")
                    self.assertIn("Fredoka.ttf", done.stderr, f"{name} does not name the font it could not open")
                    self.assertNotIn("Traceback", done.stderr, f"{name} still dies on a bare traceback")
                    self.assertNotIn(NOT_THE_FONT, done.stderr,
                                     f"{name} reports the font only after the Pillow import, so a reader without "
                                     "Pillow never sees it")
                    self.assertFalse(out.exists(), f"{name} wrote {out} before it had a font")

    def test_the_font_is_checked_before_the_pillow_import(self):
        # A path in the message plus no Pillow line in it is only possible if the guard sits first: this
        # reads the two apart in the source, in each script.
        for name in CARD_SCRIPTS:
            with self.subTest(script=name):
                text = (TOOLS / name).read_text(encoding="utf-8")
                guard = text.find("font_and_out(sys.argv)")
                pillow = text.find("from PIL import")
                self.assertGreater(guard, 0, f"{name} does not call the font guard")
                self.assertGreater(pillow, 0, f"{name} no longer imports Pillow, so this check would prove nothing")
                self.assertLess(guard, pillow, f"{name} checks the font after importing Pillow")

    def test_no_arguments_prints_the_two_it_takes(self):
        with tempfile.TemporaryDirectory() as tmp:
            for name in CARD_SCRIPTS:
                with self.subTest(script=name):
                    done = run([str(TOOLS / name)], tmp)
                    self.assertNotEqual(done.returncode, 0, f"{name} ran with no arguments")
                    self.assertIn("usage:", done.stderr, f"{name} does not print how it is called")
                    self.assertIn(name, done.stderr, f"{name}'s usage line does not name the script")
                    self.assertNotIn("Traceback", done.stderr, f"{name} still dies on a bare traceback")

    def test_one_argument_is_not_enough(self):
        with tempfile.TemporaryDirectory() as tmp:
            for name in CARD_SCRIPTS:
                with self.subTest(script=name):
                    done = run([str(TOOLS / name), "Fredoka.ttf"], tmp)
                    self.assertIn("usage:", done.stderr, f"{name} took one argument as a full command")

    def test_a_font_that_exists_gets_past_the_guard(self):
        # The guard must not swallow a path that is there: the next thing that goes wrong is Pillow.
        with tempfile.TemporaryDirectory() as tmp:
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"")
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    done = run([str(TOOLS / name), str(font), str(Path(tmp) / picture)], tmp)
                    self.assertIn(NOT_THE_FONT, done.stderr, f"{name} rejected a font path that exists")
                    self.assertNotIn("cannot open the font at", done.stderr,
                                     f"{name} still reports the font it was given")


HAVE_PIL = importlib.util.find_spec("PIL") is not None
# The launcher's own font is the one font file this checkout carries, and it is variable: a real font the
# scripts must go on accepting.
LAUNCHER_FONT = ROOT / "launcher" / "app" / "src" / "main" / "res" / "font" / "outfit.ttf"


def run_plain(argv):
    """The same run with the real Pillow, so the font this machine has is what the script reads.

    From the repository root, as the scripts are documented: the two picture scripts read `docs/press/`.
    """
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    return subprocess.run([sys.executable, *argv], cwd=ROOT, env=env, capture_output=True, text=True)


def fonts(kind):
    """TTF files this machine has, wanted for what Pillow says about setting a weight on them."""
    candidates = [LAUNCHER_FONT]
    fc_match = shutil.which("fc-match")
    if fc_match:
        done = subprocess.run([fc_match, "-f", "%{file}", "sans"], capture_output=True, text=True)
        if done.stdout.strip():
            candidates.append(Path(done.stdout.strip()))
    for folder in ("/usr/share/fonts", "/usr/local/share/fonts", str(Path.home() / ".fonts")):
        if Path(folder).is_dir():
            candidates += sorted(Path(folder).rglob("*.ttf"))[:200]
    found = []
    if not HAVE_PIL:
        return found
    from PIL import ImageFont
    for path in candidates:
        if not path.is_file():
            continue
        try:
            font = ImageFont.truetype(str(path), 12)
        except (OSError, ValueError):
            continue
        try:
            font.set_variation_by_axes([400])
            variable = True
        except (OSError, ValueError):
            variable = False
        if variable == (kind == "variable"):
            found.append(path)
    return found


class FontFileTest(unittest.TestCase):
    """A font path that is there is not a font: the zip, and one of the static files inside it."""

    def assert_refused(self, font, why):
        with tempfile.TemporaryDirectory() as tmp:
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    out = Path(tmp) / picture
                    done = run_plain([str(TOOLS / name), str(font), str(out)])
                    self.assertNotEqual(done.returncode, 0, f"{name} accepted {why}")
                    self.assertIn(str(font), done.stderr, f"{name} does not name the font it could not use")
                    self.assertIn("variable TTF", done.stderr,
                                  f"{name} does not say which of Fredoka's files it needs")
                    self.assertIn("Nothing was written", done.stderr,
                                  f"{name} does not say whether a picture was written")
                    self.assertNotIn("Traceback", done.stderr, f"{name} still dies on a Pillow traceback")
                    self.assertFalse(out.exists(), f"{name} wrote {out} with {why}")

    @unittest.skipUnless(HAVE_PIL, "reading a font needs Pillow (tools/requirements-perf.txt)")
    def test_a_file_that_is_not_a_font_is_named(self):
        # The shared download is a zip: `truetype` answers it with `unknown file format`.
        with tempfile.TemporaryDirectory() as tmp:
            font = Path(tmp) / "Fredoka.zip"
            font.write_bytes(b"PK\x03\x04 a zip holding a font, not a font")
            self.assert_refused(font, "a zip in place of the font")

    @unittest.skipUnless(HAVE_PIL, "reading a font needs Pillow (tools/requirements-perf.txt)")
    def test_a_static_single_weight_font_is_named(self):
        # A static Fredoka-NamedInstance.ttf from the same download has no weight to set: `invalid argument`.
        static = fonts("static")
        if not static:
            self.skipTest("no non-variable TTF on this machine to hand the scripts")
        self.assert_refused(static[0], "a single-weight font in place of the variable one")

    @unittest.skipUnless(HAVE_PIL, "reading a font needs Pillow (tools/requirements-perf.txt)")
    def test_a_variable_font_still_draws_the_picture(self):
        # The guard must not refuse the file the page asks for: a real variable TTF still writes its picture.
        variable = fonts("variable")
        if not variable:
            self.skipTest("no variable TTF on this machine to hand the scripts")
        with tempfile.TemporaryDirectory() as tmp:
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    out = Path(tmp) / picture
                    done = run_plain([str(TOOLS / name), str(variable[0]), str(out)])
                    self.assertEqual(0, done.returncode, f"{name} refused a variable font: {done.stderr}")
                    self.assertTrue(out.is_file() and out.stat().st_size > 0, f"{name} wrote no picture")

    def test_each_script_names_the_font_it_cannot_use(self):
        # Read apart in the source, so the message cannot quietly go back to a bare Pillow error: the two
        # calls that raise for a file that is not a variable font sit in one try, and the run stops there.
        for name in CARD_SCRIPTS:
            with self.subTest(script=name):
                text = (TOOLS / name).read_text(encoding="utf-8")
                at = text.find("set_variation_by_axes([wt])")
                self.assertGreater(at, 0, f"{name} no longer sets a weight, so nothing is checked")
                after = text[at + len("set_variation_by_axes([wt])"):]
                guarded = re.match(r"[^\n]*\n\s*except \((?:OSError|ValueError)[^)]*\)", after)
                self.assertIsNotNone(guarded, f"{name} sets the weight outside a try for OSError/ValueError")
                self.assertIn("cannot use the font at", text, f"{name} does not name the font it cannot use")


class GenTablesFontTest(unittest.TestCase):
    """gen_tables.py --font runs two of those scripts, so it has to refuse a wrong path itself."""

    def check(self, *args):
        """`gen_tables.py <args> --check` on this tree."""
        return subprocess.run([sys.executable, str(TOOLS / "gen_tables.py"), *args, "--check"],
                              cwd=ROOT, capture_output=True, text=True)

    def test_a_font_that_is_not_there_is_refused_before_anything_is_written(self):
        done = self.check("--font", "Fredoka.ttf")
        self.assertEqual(done.returncode, 2, "gen_tables.py --font accepted a font that does not exist")
        self.assertIn("Fredoka.ttf", done.stderr, "gen_tables.py does not name the font it could not open")
        self.assertNotIn("Traceback", done.stderr, "gen_tables.py died with a traceback")

    def test_an_existing_font_leaves_the_check_verdict_alone(self):
        # The verdict itself is the branch's business, not this suite's: --check exits 0 where the tables
        # are current (main, whose CI job runs it after merging) and 1 on any branch that adds a list
        # folder, which is exactly what docs/workflows/tv-list.md tells a list PR to do - "a maintainer
        # runs tools/gen_tables.py after merging". So what is held here is that handing --font does not
        # change that verdict, and that neither run turns into a refusal or a traceback.
        with tempfile.TemporaryDirectory() as tmp:
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"")
            plain = self.check()
            with_font = self.check("--font", str(font))
        self.assertIn(plain.returncode, (0, 1),
                      f"gen_tables.py --check exited {plain.returncode}: {plain.stdout}{plain.stderr}")
        self.assertNotIn("Traceback", plain.stdout + plain.stderr, "gen_tables.py --check died with a traceback")
        self.assertEqual(with_font.returncode, plain.returncode,
                         "handing --font changed what --check says about the tables")


# A command: what runs the script, then the script and what it is given.
SCRIPT = re.compile(r"tools/readme_[\w]+\.py")
COMMAND = re.compile(r"`([^`]*" + SCRIPT.pattern + r" [^`]*)`")


class PressRecipeShapeTest(unittest.TestCase):
    """The commands the page prints have to be the two-argument shape the scripts now insist on."""

    def commands(self):
        return COMMAND.findall(PRESS.read_text(encoding="utf-8"))

    def test_the_page_prints_the_redraw_commands(self):
        named = {SCRIPT.findall(cmd)[0] for cmd in self.commands()}
        self.assertEqual({"tools/readme_featured.py", "tools/readme_card.py"}, named,
                         "press.md no longer prints both redraw commands, so this check would prove nothing")

    def test_each_command_passes_two_arguments_and_writes_a_picture_the_repo_has(self):
        for cmd in self.commands():
            with self.subTest(command=cmd):
                parts = cmd.split()
                at = parts.index(SCRIPT.findall(cmd)[0])
                arguments = parts[at + 1:]
                self.assertEqual(2, len(arguments),
                                 f"press.md prints '{cmd}', which is not <script> <font> <picture>")
                self.assertTrue((ROOT / parts[at]).is_file(),
                                f"press.md names {parts[at]}, which is not in the repo")
                self.assertTrue((ROOT / arguments[1]).is_file(),
                                f"press.md writes {arguments[1]}, which the repo does not ship")

    def test_the_page_says_the_font_is_not_in_the_repo(self):
        # The one fact a reader cannot get from the commands themselves: the font path is theirs to supply.
        text = PRESS.read_text(encoding="utf-8")
        self.assertIn("Google Fonts", text, "press.md prints a font path without saying where the font comes from")
        self.assertIn("not in the repo", text, "press.md prints a font path without saying it is not in the repo")


if __name__ == "__main__":
    unittest.main()

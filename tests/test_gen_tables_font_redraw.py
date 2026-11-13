"""tools/gen_tables.py --font: a picture it could not draw is reported, not raised.

--font is the one argument of gen_tables.py that starts two other programs, tools/readme_stats.py and
tools/readme_card.py, and each of them answers a font that is there but is not Fredoka's variable TTF (the
Google Fonts zip renamed, one of the static single-weight files beside it) or a venv without Pillow -
requirements.txt has none, docs/workflows/press.md installs requirements-perf.txt - with its own line and
exit 1. subprocess.run(check=True) then turned that into a CalledProcessError traceback naming the font path
as a command, after README.md, examples/README.md and tools/readme_stats.py had already been rewritten: the
run read like "nothing was drawn" while two files had changed, and neither picture was there. A maintainer
following the script's own hint ("redraw docs/stats.png and docs/card.jpg (--font Fredoka.ttf ...)") in the
venv AGENTS.md tells them to build (requirements.txt) got that.

The redraw now reads each script's exit status: the table run finishes and says which picture is missing, the
script that stopped, and the command to draw it with. Exit 0 still means the tables and both pictures were
written (or nothing had moved); exit 1 means a picture is not redrawn, said on stderr.

The interpreter that draws them is the checkout's own tools/venv/bin/python when that venv has Pillow, and
the one that ran gen_tables.py otherwise. It was always the second, and the two differ: the invocation every
message in the script recommends, and the one CI and docs/workflows/tv-list.md use, is
`python3 tools/gen_tables.py`, while docs/workflows/press.md installs Pillow into tools/venv - the
interpreter both card scripts' own usage lines name. So a maintainer who had set the checkout up exactly as
the recipe says, run `python3 tools/gen_tables.py --font Fredoka.ttf`, and been told
"readme_stats.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt", was then handed
`python3 tools/readme_stats.py ...` as the way to draw it: the system Python on a stock Ubuntu 24.04 has no
Pillow and cannot pip install one (PEP 668), so the remedy answered with the same line, twice, with a venv
that could draw both pictures sitting beside the list. The remedy now names the interpreter that ran.

Both calls into that interpreter can also fail to start it. `is_file()` and the execute bit pass for a
`bin/python` this machine cannot run - a venv copied from another platform, a symlink to an interpreter that
was removed, a shebang naming a path that is not here - and the two calls then raise OSError, which is a
traceback in a run whose tables publish() had already written: exit 1, the code that says the generated
files are stale. The probe's OSError is answered like the venv that has no Pillow (the Python that ran this
script draws instead); the draw's is answered like the script that exited non-zero (the picture is named,
the tables are known to be written, and the remedy names an interpreter that runs). Two tests below pin
each half.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
sys.path.insert(0, str(ROOT / "tools"))
import test_gen_tables as helper  # noqa: E402  (its tree() writes the three files the script rewrites)
from test_card_font_argument import HAVE_PIL, LAUNCHER_FONT, shadow_dir  # noqa: E402

SCRIPT = ROOT / "tools" / "gen_tables.py"
# A card script stub: the STATS line render() rewrites (so a count moved and the redraw is reached), then the
# picture it was asked for. Exit code and bytes say which half of the redraw answered.
STUB = """STATS = [("2", "TV MODELS"), ("2", "BRANDS"), ("2", "LANGUAGES"), ("1 KB", "LAUNCHER")]
import sys
from pathlib import Path
Path(sys.argv[2]).write_bytes(b"{name}")
sys.exit({code})
"""


def checkout(tmp, lists=(("tcl-a", None),)):
    """A small repository with the real card scripts, the docs/ a checkout has, and one list added."""
    root = Path(tmp) / "repo"
    root.mkdir()
    helper.tree(root, [(name, data if data is not None else helper.tv("TCL A", "TCL"))
                       for name, data in lists])
    for name in ("readme_stats.py", "readme_card.py"):
        shutil.copy(ROOT / "tools" / name, root / "tools" / name)
    # The two publish through tools/picture_file.py, which is imported from beside them.
    shutil.copy(ROOT / "tools" / "picture_file.py", root / "tools" / "picture_file.py")
    (root / "docs").mkdir()
    return root


def stub_checkout(tmp):
    """The same repository with two card scripts that only write their picture, so both routes can be run."""
    root = checkout(tmp)
    (root / "tools" / "readme_stats.py").write_text(STUB.format(name="stats", code=0), encoding="utf-8")
    (root / "tools" / "readme_card.py").write_text(STUB.format(name="card", code=0), encoding="utf-8")
    return root


def run(root, font, cwd=None, env=None):
    return subprocess.run([sys.executable, str(SCRIPT), "--root", str(root), "--font", str(font)],
                          cwd=str(cwd or root.parent), env=env, capture_output=True, text=True)


def venv_python(root, body):
    """The checkout's tools/venv/bin/python, executable, from a shell-script body.

    The interpreter gen_tables.py hands the two card scripts is probed with `-I -c "import PIL"` and then
    used for the script itself, so one stub answers both: the body decides which by looking at its arguments.
    """
    path = root / "tools" / "venv" / "bin" / "python"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("#!/bin/sh\n" + body, encoding="utf-8")
    path.chmod(0o755)
    return path


# A venv Python that has Pillow: the probe answers 0, the script call is logged where it can be read.
VENV_HAS_PILLOW = """case "$*" in *"import PIL"*) exit 0 ;; esac
printf '%s\\n' "$*" >> "$(dirname "$0")/calls"
exit 0
"""
# A venv Python that does not: the probe answers 1 and nothing that runs under it counts.
VENV_NO_PILLOW = """case "$*" in *"import PIL"*) exit 1 ;; esac
printf '%s\\n' "$*" >> "$(dirname "$0")/calls"
exit 0
"""
# A venv Python that has Pillow and fails the script anyway (a font it cannot use), so the remedy is reached.
VENV_SCRIPT_FAILS = """case "$*" in *"import PIL"*) exit 0 ;; esac
exit 1
"""
# A venv Python that answers the probe and then removes itself: the script call cannot start it.
VENV_VANISHES = """case "$*" in *"import PIL"*) rm -f "$0"; exit 0 ;; esac
exit 0
"""
# A venv Python that is there, is executable, and is nothing this machine can run: execve answers ENOENT for
# a shebang naming a path that does not exist, which is how a venv copied from another machine looks.
VENV_CANNOT_START = """#!/nonexistent/interpreter
"""


class RedrawFailureTest(unittest.TestCase):
    def test_a_font_that_is_not_fredoka_names_the_picture_and_how_to_draw_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            caller = Path(tmp) / "caller"
            caller.mkdir()
            font = caller / "Fredoka.ttf"
            # The Google Fonts download is a zip; a file that is there and is not the variable TTF is the
            # case the card scripts answer with their own line and exit 1.
            font.write_bytes(b"PK\x03\x04 not a font\n")
            done = run(root, "Fredoka.ttf", cwd=caller)
            readme = (root / "README.md").read_text(encoding="utf-8")
            pictures = [root / "docs" / "stats.png", root / "docs" / "card.jpg"]
        self.assertEqual(done.returncode, 1, f"a redraw that could not happen exited {done.returncode}")
        self.assertIn("docs/stats.png was not redrawn", done.stderr, done.stderr)
        self.assertIn("tools/readme_stats.py exited 1", done.stderr, done.stderr)
        self.assertIn("docs/stats.png", done.stderr, "the remedy does not name the file to write")
        # And the interpreter the retry has to use: this checkout has no venv, so it is the one that ran the
        # script. A bare `python3` is the interpreter that just answered "needs Pillow" on a stock machine.
        self.assertIn(f"{sys.executable} tools/readme_stats.py", done.stderr,
                      f"the remedy names an interpreter that did not run: {done.stderr!r}")
        self.assertNotIn("Traceback", done.stderr, "the failure still ends in a CalledProcessError")
        # The tables are written: saying "nothing was drawn" would be wrong, and so would hiding it.
        self.assertIn("TCL A", readme, "the tables were not written before the redraw")
        self.assertNotIn("Updated:", done.stdout, f"the run claims it updated {done.stdout!r}")
        for picture in pictures:
            self.assertFalse(picture.exists(), f"{picture.name} was written with a font that is not Fredoka")

    def test_a_second_picture_that_fails_is_named_too(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            (root / "tools" / "readme_card.py").write_text(STUB.format(name="card", code=3), encoding="utf-8")
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            stats = (root / "docs" / "stats.png").exists()
        self.assertEqual(done.returncode, 1, f"a card picture that was not drawn exited {done.returncode}")
        self.assertIn("docs/card.jpg was not redrawn", done.stderr, done.stderr)
        self.assertIn("tools/readme_card.py exited 3", done.stderr, done.stderr)
        self.assertNotIn("docs/stats.png was not redrawn", done.stderr, "the picture that was drawn is blamed")
        self.assertTrue(stats, "the first picture was not drawn before the second was tried")
        self.assertNotIn("Traceback", done.stderr, "the failure still ends in a traceback")

    @unittest.skipUnless(HAVE_PIL, "the card scripts need Pillow (tools/requirements-perf.txt)")
    def test_a_venv_without_pillow_says_so_and_does_not_traceback(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: Pillow is checked first")
            (Path(tmp) / "no-pillow").mkdir()
            env = dict(os.environ, PYTHONPATH=str(shadow_dir(Path(tmp) / "no-pillow")))
            done = run(root, font, env=env)
        self.assertEqual(done.returncode, 1, f"a redraw without Pillow exited {done.returncode}")
        self.assertIn("needs Pillow", done.stderr, "the script that stopped does not say what it needs")
        self.assertIn("docs/stats.png was not redrawn", done.stderr, done.stderr)
        self.assertNotIn("Traceback", done.stderr, "a missing Pillow still ends in a traceback")


class RedrawSuccessTest(unittest.TestCase):
    def test_both_pictures_are_drawn_and_listed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            stats = (root / "docs" / "stats.png").read_bytes() if (root / "docs" / "stats.png").exists() else b""
            card = (root / "docs" / "card.jpg").read_bytes() if (root / "docs" / "card.jpg").exists() else b""
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual((stats, card), (b"stats", b"card"), "the redraw did not write both pictures")
        self.assertIn("Updated:", done.stdout, done.stdout)
        self.assertIn("docs/stats.png", done.stdout, "the run does not list the picture it drew")
        self.assertIn("docs/card.jpg", done.stdout, "the run does not list the picture it drew")
        self.assertNotIn("was not redrawn", done.stderr, done.stderr)

    def test_a_count_that_did_not_move_draws_nothing(self):
        # --font draws when a count moved; a second run has nothing to draw, so a font it could not use is
        # never read and must not be reported as a failure.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            first = subprocess.run([sys.executable, str(SCRIPT), "--root", str(root)],
                                   cwd=tmp, capture_output=True, text=True)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"not a font at all")
            second = run(root, font, cwd=tmp)
            pictures = [root / "docs" / "stats.png", root / "docs" / "card.jpg"]
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertIn("Up to date", second.stdout, second.stdout)
        self.assertNotIn("was not redrawn", second.stderr, second.stderr)
        for picture in pictures:
            self.assertFalse(picture.exists(), f"{picture.name} was drawn with no count to draw it for")

    @unittest.skipUnless(HAVE_PIL, "the card scripts need Pillow (tools/requirements-perf.txt)")
    def test_the_launchers_own_variable_font_reaches_the_scripts(self):
        # The guard must not refuse the font a checkout can hand it: launcher/ carries a variable TTF, and
        # readme_stats.py draws the picture with it. (readme_card.py also needs the screenshots docs/ has,
        # so it is the stubs above that prove its half.)
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            done = run(root, LAUNCHER_FONT)
            stats = root / "docs" / "stats.png"
            written = stats.stat().st_size if stats.exists() else 0
        self.assertTrue(LAUNCHER_FONT.is_file(), "the launcher's font is gone")
        self.assertNotIn("cannot use the font", done.stderr, done.stderr)
        self.assertTrue(written > 1000, f"docs/stats.png is {written} B with a variable font")


class RedrawInterpreterTest(unittest.TestCase):
    """Which Python the two card scripts are handed: the checkout's venv when that venv has Pillow."""

    def test_the_venvs_python_draws_the_pictures(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            venv_python(root, VENV_HAS_PILLOW)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            calls = root / "tools" / "venv" / "bin" / "calls"
            logged = calls.read_text(encoding="utf-8") if calls.exists() else ""
            drawn = [p.name for p in (root / "docs" / "stats.png", root / "docs" / "card.jpg") if p.exists()]
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertIn("tools/readme_stats.py", logged,
                      f"the venv Python was not handed the first script: {logged!r}")
        self.assertIn("tools/readme_card.py", logged,
                      f"the venv Python was not handed the second script: {logged!r}")
        # The picture names travel with them, so a first run is not mistaken for the probe.
        self.assertIn("docs/stats.png", logged, logged)
        self.assertEqual(drawn, [], "the card scripts ran under the Python that ran gen_tables.py")

    def test_a_venv_python_that_cannot_start_leaves_this_python_to_draw(self):
        # bin/python is there and executable, and it is not something this machine can run. That is one more
        # unusable venv, answered like the venv without Pillow - not a traceback with the tables written.
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            venv = root / "tools" / "venv" / "bin" / "python"
            venv.parent.mkdir(parents=True)
            venv.write_text(VENV_CANNOT_START, encoding="utf-8")
            venv.chmod(0o755)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            stats = (root / "docs" / "stats.png").read_bytes() if (root / "docs" / "stats.png").exists() else b""
            card = (root / "docs" / "card.jpg").read_bytes() if (root / "docs" / "card.jpg").exists() else b""
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertNotIn("Traceback", done.stderr, "an interpreter that cannot start ends in a traceback")
        self.assertEqual((stats, card), (b"stats", b"card"),
                         "an interpreter that cannot start stopped the pictures being drawn")
        self.assertIn("Updated:", done.stdout, done.stdout)

    def test_a_venv_python_that_disappears_before_the_draw_is_named(self):
        # The probe answered (so the venv looked usable) and the interpreter was gone for the picture: the
        # same run as a script that exited non-zero, not a traceback after the tables were written.
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            venv_python(root, VENV_VANISHES)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            readme = (root / "README.md").read_text(encoding="utf-8")
            pictures = [root / "docs" / "stats.png", root / "docs" / "card.jpg"]
        self.assertEqual(done.returncode, 1, f"a draw that could not start exited {done.returncode}")
        self.assertIn("docs/stats.png was not redrawn", done.stderr, done.stderr)
        self.assertIn("cannot start tools/venv/bin/python", done.stderr,
                      f"the interpreter that could not be started is not named: {done.stderr!r}")
        self.assertIn("The tables are written", done.stderr, done.stderr)
        # The remedy is one the reader can run: the interpreter that just failed to start is not it.
        self.assertIn(f"{sys.executable} tools/readme_stats.py", done.stderr,
                      f"the remedy names an interpreter that cannot start: {done.stderr!r}")
        self.assertNotIn("python3 tools/readme_stats.py", done.stderr, "the remedy hands back a bare python3")
        self.assertNotIn("Traceback", done.stderr, "a draw that could not start still ends in a traceback")
        self.assertIn("TCL A", readme, "the tables were not written before the redraw")
        for picture in pictures:
            self.assertFalse(picture.exists(), f"{picture.name} was reported as drawn by a missing interpreter")

    def test_a_venv_without_pillow_leaves_this_python_to_draw(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            venv_python(root, VENV_NO_PILLOW)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            calls = root / "tools" / "venv" / "bin" / "calls"
            stats = (root / "docs" / "stats.png").read_bytes() if (root / "docs" / "stats.png").exists() else b""
            card = (root / "docs" / "card.jpg").read_bytes() if (root / "docs" / "card.jpg").exists() else b""
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual((stats, card), (b"stats", b"card"),
                         "a venv that cannot import Pillow was used instead of the Python that ran the script")
        self.assertFalse(calls.exists(), "a venv whose Pillow probe answered 1 was still used")

    def test_the_remedy_names_the_venv_python_that_ran_the_scripts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            venv_python(root, VENV_SCRIPT_FAILS)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stub never reads it")
            done = run(root, font)
        self.assertEqual(done.returncode, 1, f"a redraw the venv answered with 1 exited {done.returncode}")
        self.assertIn("tools/venv/bin/python tools/readme_stats.py <Fredoka.ttf> docs/stats.png", done.stderr,
                      f"the remedy names another interpreter: {done.stderr!r}")
        self.assertNotIn("python3 tools/readme_stats.py", done.stderr,
                         "the remedy still hands the reader the interpreter that lacks Pillow")


class OrderTest(unittest.TestCase):
    def test_the_redraw_reads_exit_status_instead_of_raising(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("stopped = redraw(root, font)", source, "main() does not read what the redraw returned")
        for line in source.splitlines():
            if "subprocess.run(" in line and not line.lstrip().startswith("#"):
                self.assertNotIn("check=True", line, f"a child's exit 1 is raised again: {line.strip()}")

    def test_the_docstring_names_the_exit_status_a_missing_picture_answers_with(self):
        doc = SCRIPT.read_text(encoding="utf-8").split('"""')[1]
        self.assertIn("1 out of date", doc, "the exit-status line is gone")
        self.assertIn("pictures", doc, "the docstring does not say what exit 1 also means")
        self.assertIn("the tables are written", doc, "the docstring does not say what a failed redraw left")


if __name__ == "__main__":
    unittest.main()

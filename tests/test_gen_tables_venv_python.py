"""What tools/gen_tables.py asks for its venv interpreter, on either platform's layout.

`python -m venv` keeps its interpreter at `tools/venv/bin/python` on macOS and Linux, and at
`tools/venv/Scripts/python.exe` on Windows. tools/gen_tables.py's card_python() asked only for the first, so a
Windows checkout's venv - Pillow installed exactly as docs/workflows/press.md says - was never the interpreter
the two card scripts were handed: the redraw fell back to the Python that ran gen_tables.py and answered with
the Pillow line, with the venv that could draw it sitting beside the list (#3897). tools/bench.py's venv_python()
has asked both paths since it was written, and gen_tables.py now asks them in the same order.

These cases read the paths instead of running an interpreter, so they hold on every platform. The two that need
the file to answer the Pillow probe build a `#!/bin/sh` stub and skip where Windows cannot start one; the
end-to-end cases that run gen_tables.py with such a stub stay in tests/test_gen_tables_font_redraw.py, which
skips them on Windows for the same reason.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import gen_tables  # noqa: E402  (venv_python()/card_python(), the two paths a checkout's venv can have)

# A venv Python that has Pillow: the probe answers 0, and the script call is logged beside the stub.
VENV_HAS_PILLOW = """case "$*" in *"import PIL"*) exit 0 ;; esac
printf '%s\\n' "$*" >> "$(dirname "$0")/calls"
exit 0
"""

POSIX = Path("tools") / "venv" / "bin" / "python"
WINDOWS = Path("tools") / "venv" / "Scripts" / "python.exe"


def checkout(tmp):
    """A folder shaped like a checkout: tools/ for a venv to sit in, which is all either path needs."""
    root = Path(tmp) / "repo"
    (root / "tools").mkdir(parents=True)
    return root


def stub(root, relative):
    """An executable file at `relative` that answers the Pillow probe: a venv interpreter, without the venv."""
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("#!/bin/sh\n" + VENV_HAS_PILLOW, encoding="utf-8")
    path.chmod(0o755)
    return path


class VenvInterpreterPathTest(unittest.TestCase):
    def test_a_checkout_with_no_venv_answers_the_posix_path(self):
        # What the path is before either file exists: card_python() then finds nothing to start and hands the
        # scripts the Python that ran gen_tables.py, which is how every run without a venv works.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            self.assertEqual(gen_tables.venv_python(root), root / POSIX)
            self.assertEqual(gen_tables.card_python(root), sys.executable)

    def test_the_posix_path_is_asked_for_first(self):
        # A checkout can hold both - a venv copied from another machine - so the order is pinned: the POSIX
        # path, the one this tool asked for on its own, stays the first question.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            posix = root / POSIX
            posix.parent.mkdir(parents=True)
            posix.write_text("", encoding="utf-8")
            windows = root / WINDOWS
            windows.parent.mkdir(parents=True)
            windows.write_text("", encoding="utf-8")
            self.assertEqual(gen_tables.venv_python(root), posix)

    @unittest.skipIf(os.name == "nt", "the stub interpreter is a #!/bin/sh script, which Windows cannot start; "
                                      "the platform's own .exe is what a Windows checkout would have here")
    def test_a_windows_venv_is_the_interpreter_the_scripts_are_handed(self):
        # #3897's checkout: the venv is Scripts/python.exe with Pillow in it. No shell script can be an .exe, so
        # the stub stands at that path and answers the same two runs the redraw makes - the Pillow probe, then
        # the card script.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            exe = stub(root, WINDOWS)
            self.assertEqual(gen_tables.venv_python(root), exe)
            self.assertEqual(gen_tables.card_python(root), str(exe))

    @unittest.skipIf(os.name == "nt", "the stub interpreter is a #!/bin/sh script, which Windows cannot start")
    def test_a_posix_venv_is_still_the_interpreter_the_scripts_are_handed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            venv = stub(root, POSIX)
            self.assertEqual(gen_tables.venv_python(root), venv)
            self.assertEqual(gen_tables.card_python(root), str(venv))

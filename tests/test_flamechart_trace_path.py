"""tools/flamechart.py answers a TRACE path the filesystem does not have, before the trace engine starts.

`--out` was checked before any trace was opened (a folder, a name with no extension, a folder that is not
there: exit 2), but the traces themselves went straight into TraceProcessor. On a computer with the perfetto
package installed, a typo in a trace path therefore downloaded the trace_processor_shell first and then came
back as a traceback out of a tool that promises argument errors:

    Downloading https://commondatastorage.googleapis.com/perfetto-luci-artifacts/v59.1/linux-amd64/trace_processor_shell
    FileNotFoundError [Errno 2] No such file or directory: '/nope.pftrace'

and on a computer without it, the same typo was answered with the package's install line
("trace_report.py needs the optional perfetto package: ..."), which is not what went wrong. tools/trace_report.py,
the tool docs/workflows/perf-ab.md puts beside this one, answers `not a file: /nope.pftrace` at exit 1 for the
same argument. flamechart.py now checks every TRACE argument the same way, and with the same words, before
`trace_report.opener()` is called; a folder with no .pftrace in it still names the folder, and a path that is
there still reaches the engine.

Read from the filesystem only: no perfetto, no Pillow, no TV. Pillow is stubbed for the cases that get past
the check, and the engine is replaced by a mock, so what these tests read is the order of the two.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
spec = importlib.util.spec_from_file_location("cyt_flamechart_trace_path", ROOT / "tools" / "flamechart.py")
flamechart = importlib.util.module_from_spec(spec)
spec.loader.exec_module(flamechart)
# The module flamechart.py itself imported, so patching its opener is what main() calls.
trace_report = flamechart.trace_report


class TracePathTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)
        self.out = str(self.folder / "chart.png")

    def run_main(self, argv):
        """(SystemExit's code, what the run printed) for flamechart.main, Pillow present, the engine mocked.

        A refusal here is `sys.exit("...")`, whose code is the message: the shell exits 1, as
        tools/trace_report.py does for the same path.
        """
        err = io.StringIO()
        with mock.patch.dict(sys.modules, {"PIL": mock.MagicMock()}), \
                mock.patch.object(trace_report, "opener") as opener, \
                contextlib.redirect_stderr(err), \
                self.assertRaises(SystemExit) as caught:
            flamechart.main(argv)
        return caught.exception.code, err.getvalue(), opener

    def trace(self, name="nav-1.pftrace"):
        """A file at the path flamechart is given; its bytes are never read, the engine is mocked."""
        path = self.folder / name
        path.write_bytes(b"")
        return str(path)

    def test_a_path_that_is_not_a_file_is_named_before_the_engine_starts(self):
        missing = str(self.folder / "nav-1.pftrace")
        code, _, opener = self.run_main([missing, "--out", self.out])
        self.assertEqual(code, f"not a file: {missing}")
        opener.assert_not_called()
        self.assertFalse(Path(self.out).exists())

    def test_the_words_are_the_ones_trace_report_uses(self):
        missing = str(self.folder / "nav-1.pftrace")
        code, _, _ = self.run_main([missing, "--out", self.out])
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as caught:
            trace_report.main([missing])
        self.assertEqual(code, caught.exception.code)
        self.assertEqual(code, f"not a file: {missing}")

    def test_a_folder_with_no_traces_names_the_folder(self):
        folder = self.folder / "traces"
        folder.mkdir()
        code, _, opener = self.run_main([str(folder), "--out", self.out])
        self.assertEqual(code, f"no .pftrace files in {folder}")
        opener.assert_not_called()

    def test_every_panel_is_checked_before_the_first_one_is_read(self):
        before, missing = self.trace("before-1.pftrace"), str(self.folder / "after-1.pftrace")
        code, _, opener = self.run_main([before, missing, "--out", self.out])
        self.assertEqual(code, f"not a file: {missing}")
        opener.assert_not_called()

    def test_a_trace_that_is_there_still_reaches_the_engine(self):
        path = self.trace()
        with mock.patch.dict(sys.modules, {"PIL": mock.MagicMock()}), \
                mock.patch.object(trace_report, "opener") as opener:
            opener.return_value = mock.Mock(side_effect=RuntimeError("engine opened a trace"))
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(RuntimeError):
                flamechart.main([path, "--out", self.out])
        opener.assert_called_once()

    def test_a_folder_with_a_trace_in_it_still_reaches_the_engine(self):
        self.trace("before-1.pftrace")
        with mock.patch.dict(sys.modules, {"PIL": mock.MagicMock()}), \
                mock.patch.object(trace_report, "opener") as opener:
            opener.return_value = mock.Mock(side_effect=RuntimeError("engine opened a trace"))
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(RuntimeError):
                flamechart.main([str(self.folder), "--out", self.out])
        opener.assert_called_once()

    def test_the_output_is_still_checked_first(self):
        # `--out` is an argument error (exit 2) and keeps winning over a trace path that is missing too.
        code, err, opener = self.run_main([str(self.folder / "nav-1.pftrace"), "--out", str(self.folder)])
        self.assertEqual(code, 2)
        self.assertIn("--out", err)
        opener.assert_not_called()


if __name__ == "__main__":
    unittest.main()

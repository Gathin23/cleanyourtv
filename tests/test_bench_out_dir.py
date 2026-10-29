"""bench.py judges its --out-dir before it opens the TV, and does not lose a finished run.

tools/bench.py is the tool docs/workflows/perf-ab.md measures a launcher change with, and its results
folder was the one thing it read as whatever was typed: `os.makedirs(args.out_dir, exist_ok=True)` sat
after the resolve-activity and the `dumpsys package` that read the launcher's version, so

    --out-dir results.txt          FileExistsError, a file where the folder goes
    --out-dir results.txt/nested   NotADirectoryError, a path through a file
    --out-dir ""                   FileNotFoundError: ''

were each a 12-line traceback with exit 1, at the start of a run that measured nothing - and exit 1 is
the code a finished run that could not be written returns, so a typo looked like a benchmark that ran.
The write at the end had the same problem at the worst moment: after the keys, the cold starts, the
returns and the idle window, a folder removed or a disk that filled up took every number with it.

The folder is now an argparse type= (judged before tv.py is imported, before anything is asked of the
TV), a folder that cannot be created is answered before measuring starts, and a result.json that cannot
be written is printed to stdout instead, with exit 1 saying the file is not it. These tests run the real
script beside a stub tools/tv.py that records every command, so "nothing was asked of the TV" means the
stub's log is not there - no TV, no network and no ADB key is needed.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

# The client bench.py imports: it answers the two commands the start of a run sends, and records every
# command it was asked, so a refusal that happened before connecting leaves no log at all.
STUB = '''import json, os
from pathlib import Path

HOST = PORT = None

class Device:
    def shell(self, command, read_timeout_s=None, decode=True):
        log = Path(os.environ["BENCH_STUB_LOG"])
        calls = json.loads(log.read_text()) if log.exists() else []
        calls.append(command)
        log.write_text(json.dumps(calls))
        if "resolve-activity" in command:
            return "priority=0\\n  com.gathin.tvhome/.MainActivity\\n"
        if "dumpsys package" in command:
            return "versionName=1.2.3"
        return ""

    def close(self):
        pass

def connect():
    return Device()
'''


class BenchOutDirTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self._tmp.name)
        shutil.copyfile(ROOT / "tools" / "bench.py", self.folder / "bench.py")
        (self.folder / "tv.py").write_text(STUB, encoding="utf-8")
        self.work = self.folder / "work"
        self.work.mkdir()
        self.log = self.folder / "calls.json"

    def tearDown(self):
        self._tmp.cleanup()

    def run_bench(self, args):
        env = os.environ.copy()
        for key in ("TV_HOST", "TV_PORT"):
            env.pop(key, None)
        env["BENCH_STUB_LOG"] = str(self.log)
        return subprocess.run([sys.executable, str(self.folder / "bench.py"), "--host", "192.0.2.20", *args],
                              cwd=self.work, env=env, capture_output=True, text=True, timeout=60)

    def calls(self):
        return json.loads(self.log.read_text()) if self.log.exists() else []

    def test_a_file_where_the_folder_goes_is_refused_before_the_tv(self):
        (self.work / "results.txt").write_text("hello\n", encoding="utf-8")
        result = self.run_bench(["--out-dir", "results.txt"])
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("results.txt is a file, not a folder", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual("", result.stdout)
        self.assertEqual([], self.calls(), "the TV was asked something for a folder that cannot be written")
        self.assertEqual("hello\n", (self.work / "results.txt").read_text(encoding="utf-8"))

    def test_a_path_through_a_file_is_refused_before_the_tv(self):
        (self.work / "results.txt").write_text("hello\n", encoding="utf-8")
        result = self.run_bench(["--out-dir", "results.txt/nested"])
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("results.txt/nested runs through", result.stderr)
        self.assertIn("which is a file", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual([], self.calls())

    def test_an_empty_out_dir_is_refused_before_the_tv(self):
        result = self.run_bench(["--out-dir", ""])
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("needs a folder to write the results into", result.stderr)
        self.assertNotIn("FileNotFoundError", result.stderr)
        self.assertEqual([], self.calls())

    def test_a_folder_that_is_not_there_yet_is_still_accepted(self):
        # os.makedirs() has always created the folder, and docs/workflows/perf-ab.md's
        # `--out-dir work/bench-before` is a folder a run makes. The stub answers HOME, so the run
        # gets past the point where --out-dir is judged - which is all this test needs to know.
        result = self.run_bench(["--out-dir", "new-folder/today"])
        self.assertGreaterEqual(len(self.calls()), 2,
                                "a folder that does not exist yet was refused before the TV: " + result.stderr)
        self.assertIn("resolve-activity", self.calls()[0])
        self.assertNotIn("--out-dir:", result.stderr)

    def test_an_existing_folder_is_accepted(self):
        result = self.run_bench(["--out-dir", "."])
        self.assertGreaterEqual(len(self.calls()), 2, result.stderr)
        self.assertNotIn("--out-dir:", result.stderr)

    def test_the_two_refusals_sit_where_the_run_cannot_lose_anything(self):
        source = (ROOT / "tools" / "bench.py").read_text(encoding="utf-8")
        # The folder is judged in argparse, so it is judged before TV() is built.
        option = source.index('parser.add_argument("--out-dir", type=out_dir')
        self.assertLess(option, source.index("tv = TV("),
                        "the --out-dir check has to run in argparse, before the TV is opened")
        # A folder that cannot be created is answered before the key sequence starts.
        start = source.index("    try:\n        os.makedirs(args.out_dir, exist_ok=True)")
        self.assertLess(start, source.index("    samples = []"),
                        "a folder that cannot be created has to be answered before measuring")
        # The write at the end is guarded, and the numbers are printed when it fails.
        write = source.index('    try:\n        with open(path, "w", encoding="utf-8") as f:')
        self.assertIn("except OSError as e:", source[write:write + 900])
        self.assertIn("this run's only copy", source)
        self.assertIn("    return 1\n", source[write:])
        self.assertIn("sys.exit(main())", source)

    def test_the_docstring_states_the_exit_status(self):
        source = (ROOT / "tools" / "bench.py").read_text(encoding="utf-8")
        docstring = source.split('"""')[1]
        self.assertIn("Exit status: 0", docstring)
        self.assertIn("2 an --out-dir that cannot be written into", docstring)
        self.assertIn("1 anything else", docstring)


if __name__ == "__main__":
    unittest.main()

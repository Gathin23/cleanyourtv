"""The folders a run leaves the owner's own TV in are the ones .gitignore keeps out of a checkout.

`tools/bench.py` writes `bench-results/result.json` into the folder it was run from - its docstring's
first usage line is `python tools/bench.py --keys 36 --series 3`, with no `--out-dir`, so the default is
what a reader copies - and that file carries `"host": args.host`, the address the run was given, next to
the TV's fingerprint, which the script reads with `getprop ro.build.fingerprint` into `environment`.
CONTRIBUTING.md and AGENTS.md both forbid an IP address in the tree, and .gitignore already keeps every
other place a run leaves the set: `backup/` (package dumps), `shots/` (session screenshots), `work/`
(notes) and `traces/` (Perfetto traces, "they hold the TV's process list"), each with the reason in its
own comment. `bench-results/` was the one folder the tools write into by default that it did not, so a
`git add -A` after the documented bench run put the TV's address in a pull request.

The folder names are read out of the tools rather than copied in, and the matcher below has its own
reader tests, so a tool that changes its default fails here instead of passing on a stale string.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from fnmatch import fnmatch
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
GITIGNORE = ROOT / ".gitignore"
TOOLS = ROOT / "tools"
PATTERNS = [line.strip() for line in GITIGNORE.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")]


def pattern_matches(pattern, path):
    """Whether one .gitignore line matches a path inside the checkout."""
    body = pattern[:-1] if pattern.endswith("/") else pattern
    if "/" in body:
        # A pattern with a slash in it is anchored at the checkout root, and a folder takes what is under it.
        return path == body or path.startswith(body + "/")
    # Otherwise it names a file or folder anywhere: *.apk, .DS_Store, backup/.
    return any(fnmatch(part, body) for part in path.split("/"))


def ignored(path):
    path = path.strip("/")
    return any(pattern_matches(pattern, path) for pattern in PATTERNS)


def default_of(tool, flag):
    """The default of one argument, out of the tool's own call to add_argument().

    Read from the whole call, not from the flag and the default side by side: an argument takes
    type=, metavar= and help= as well, in whatever order the tool writes them, and bench.py's
    --out-dir grew a type= when it started judging the folder before opening the TV.
    """
    text = (TOOLS / tool).read_text(encoding="utf-8")
    call = re.search(r'add_argument\(\s*"%s"(.*?)\)\s*\n' % re.escape(flag), text, re.S)
    found = re.search(r'default="([^"]*)"', call[1]) if call else None
    return found[1] if found else ""


class ToolResultsAreIgnored(unittest.TestCase):
    def test_bench_default_results_folder_is_ignored(self):
        folder = default_of("bench.py", "--out-dir")
        self.assertNotEqual(folder, "", "tools/bench.py has no --out-dir default; update this test")
        self.assertTrue(ignored(f"{folder}/result.json"),
                        f"tools/bench.py writes {folder}/result.json by default and .gitignore "
                        f"does not keep it out of a checkout: {PATTERNS}")
        self.assertTrue(ignored(folder), "the folder itself has to be ignored, not only the file name")

    def test_trace_default_traces_folder_is_ignored(self):
        folder = default_of("trace.py", "--out")
        self.assertNotEqual(folder, "", "tools/trace.py has no --out default; update this test")
        self.assertTrue(ignored(f"{folder}/home-1.pftrace"),
                        f"tools/trace.py writes {folder}/ by default and .gitignore does not keep it out")

    def test_the_documented_bench_run_uses_the_default(self):
        """The reason the default matters: the usage line a reader copies passes no --out-dir."""
        text = (TOOLS / "bench.py").read_text(encoding="utf-8")
        usage = [line.strip() for line in text.splitlines() if line.strip().startswith("TV_HOST=")]
        self.assertTrue(usage, "tools/bench.py's docstring names no usage line")
        self.assertTrue(any("bench.py" in line and "--out-dir" not in line for line in usage),
                        f"no usage line relies on the default: {usage}")

    def test_the_result_carries_the_address_and_the_fingerprint(self):
        """Why the folder has to be ignored at all: what bench.py puts in result.json."""
        text = (TOOLS / "bench.py").read_text(encoding="utf-8")
        for claim in ('"host": args.host', "ro.build.fingerprint", 'os.path.join(args.out_dir, "result.json")'):
            self.assertIn(claim, text,
                          f"{claim!r} is what makes {default_of('bench.py', '--out-dir')} an identifier leak")

    def test_the_folders_the_checkout_already_kept(self):
        """The convention this line joins; if one of these loses its entry, the list is being emptied."""
        for path in ("backup/getprop.txt", "shots/01-after.png", "work/bench-before/result.json",
                     "traces/home-1.pftrace"):
            self.assertTrue(ignored(path), f"{path} has to stay out of the checkout: {PATTERNS}")

    def test_what_must_stay_tracked(self):
        for path in ("docs/TROUBLESHOOTING.md", "tools/bench.py", "themes/aurora/config.json",
                     "examples/tcl-43s5k/debloat.txt", "README.md"):
            self.assertFalse(ignored(path), f"{path} is part of the repository and must not be ignored")

    def test_a_folder_entry_is_not_a_prefix_match(self):
        """`bench-results/` keeps that folder, not every folder whose name starts the same way."""
        for path in ("bench-results-2026/result.json", "workbench/result.json", "traces-notes/home.pftrace"):
            self.assertFalse(ignored(path), f"{path} is not one of the ignored folders")

    def test_the_patterns_the_file_already_has(self):
        """The matcher's own readers: a path into a folder, a type, a name with no folder in it."""
        self.assertTrue(ignored("tools/venv/bin/python"))
        self.assertTrue(ignored("launcher/app/build/outputs/apk/release/app-release.apk"))
        self.assertTrue(ignored("tools/adbkey/adbkey"))
        self.assertFalse(ignored("adbkey/adbkey"))


if __name__ == "__main__":
    unittest.main()

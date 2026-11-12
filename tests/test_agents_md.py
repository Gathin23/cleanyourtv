"""Keeps AGENTS.md honest: every path it names exists, and its numbers match the files they come from.

The workflows in docs/workflows/ hold the recipes AGENTS.md used to carry, so their paths are checked
the same way: words in backticks from the repository root, links from the workflow's own folder.

Coding agents follow AGENTS.md to the letter, so a renamed file or a moved gate turns into a confident
wrong instruction. A path is anything in a link or in backticks that has a slash or a file extension;
templates such as `examples/<brand>-<model>/` and globs are skipped, and so are the local folders
.gitignore names (`backup/`, `tools/venv`), which a fresh clone does not have yet. A bare file name
such as `config.json` counts if a file of that name exists somewhere in the repo.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
AGENTS = ROOT / "AGENTS.md"
WORKFLOWS = ROOT / "docs" / "workflows"
CI = ROOT / ".github" / "workflows" / "ci.yml"
GRADLE = ROOT / "launcher" / "app" / "build.gradle"

EXTENSION = re.compile(r"\.(md|py|sh|ps1|yml|yaml|gradle|xml|json|txt|jpg|png|java)$")
TEMPLATE = re.compile(r"[<>*{}$=%]")
LINK = re.compile(r"\]\(([^)\s]+)\)")
CODE = re.compile(r"(`+)(.+?)\1")
SKIP_DIRS = {".git", "venv", "build", ".gradle", "__pycache__"}
WRITTEN = {"result.json"}  # files a tool writes on a contributor's machine (tools/bench.py), not in a clone


def text():
    return AGENTS.read_text(encoding="utf-8")


def ignored_prefixes():
    """Folders .gitignore names: they exist on a contributor's machine, not in a clone.

    An entry only counted as a folder while it ended in `/`, so `tools/venv` - which .gitignore
    writes without one - was never in this list: the check then wanted `tools/venv/bin/pip` to
    exist, which it does only because CI builds the venv before running the suite. A name whose
    last part has no extension counts as a folder too, and on Windows that venv's `pip` lives in
    `Scripts/`, not `bin/`.
    """
    folders = []
    for line in (ROOT / ".gitignore").read_text(encoding="utf-8").split("\n"):
        entry = line.strip()
        if not entry or entry.startswith("#"):
            continue
        last = entry.rsplit("/", 1)[-1]
        if entry.endswith("/") or ("*" not in entry and "." not in last):
            folders.append(entry.rstrip("/"))
    return folders


def file_names():
    names = set()
    for path in ROOT.rglob("*"):
        if not SKIP_DIRS.intersection(path.relative_to(ROOT).parts):
            names.add(path.name)
    return names


def named_paths(body=None):
    """(source, path) for every link target outside code and every path-like word in backticks."""
    body = text() if body is None else body
    found = []
    for target in LINK.findall(CODE.sub("", body)):
        if not re.match(r"(https?:|mailto:|#)", target):
            found.append(("link", target.split("#")[0]))
    for _, span in CODE.findall(body):
        for word in span.split():
            word = word.strip("'\"(),;:")
            if word.startswith("./"):
                word = word[2:]
            if not word or TEMPLATE.search(word) or word.startswith(("~", "/", "-", "http")):
                continue
            if "/" in word or EXTENSION.search(word):
                found.append(("code", word))
    return found


class AgentsPathsTest(unittest.TestCase):
    def test_every_named_path_exists(self):
        ignored = ignored_prefixes()
        names = file_names()
        for source, path in named_paths():
            with self.subTest(path=path, source=source):
                if any(path == p or path.startswith(p + "/") for p in ignored):
                    continue
                if "/" not in path.rstrip("/"):
                    self.assertTrue((ROOT / path).exists() or path.rstrip("/") in names,
                                    f"AGENTS.md names {path}, and no file in the repo has that name")
                else:
                    self.assertTrue((ROOT / path).exists(), f"AGENTS.md names {path}, which does not exist")

    def test_every_path_a_workflow_names_exists(self):
        ignored = ignored_prefixes()
        names = file_names()
        for workflow in sorted(WORKFLOWS.glob("*.md")):
            for source, path in named_paths(workflow.read_text(encoding="utf-8")):
                with self.subTest(workflow=workflow.name, path=path, source=source):
                    if source == "link":
                        target = (workflow.parent / path).resolve()
                        self.assertTrue(target.exists(), f"{workflow.name} links {path}, which does not exist")
                    elif path in WRITTEN or any(path == p or path.startswith(p + "/") for p in ignored):
                        continue
                    elif "/" not in path.rstrip("/"):
                        self.assertTrue((ROOT / path).exists() or path.rstrip("/") in names,
                                        f"{workflow.name} names {path}, and no file in the repo has that name")
                    else:
                        self.assertTrue((ROOT / path).exists(), f"{workflow.name} names {path}, which does not exist")

    def test_every_workflow_is_listed(self):
        for workflow in sorted(WORKFLOWS.glob("*.md")):
            with self.subTest(workflow=workflow.name):
                self.assertIn(f"](docs/workflows/{workflow.name})", text(),
                              f"AGENTS.md's Workflows section has no line for {workflow.name}")

    def test_it_names_paths_at_all(self):
        # A parser that silently finds nothing would make the test above pass for any text.
        paths = [p for _, p in named_paths()]
        for expected in ("PROMPT.md", "tools/disable.sh", ".github/maintainer/MAINTAINER.md", "examples/README.md"):
            self.assertIn(expected, paths)


class AgentsSizeTest(unittest.TestCase):
    """Every agent loads AGENTS.md into every session, so its size is a cost on every task.

    Anthropic's Claude Code docs target under 200 lines for a CLAUDE.md, which imports this file, and
    Codex stops reading project instructions at 32 KiB across all AGENTS.md files. A procedure that is
    only sometimes needed belongs in docs/workflows/, with one line in the Workflows section.
    """

    def test_under_200_lines(self):
        self.assertLess(len(text().split("\n")), 200, "AGENTS.md is 200 lines or more: move a procedure to docs/workflows/")

    def test_under_half_of_codex_limit(self):
        self.assertLess(len(text().encode("utf-8")), 16 * 1024,
                        "AGENTS.md is over 16 KiB, half of Codex's 32 KiB limit: move a procedure to docs/workflows/")


class AgentsNumbersTest(unittest.TestCase):
    def test_apk_gate_matches_ci(self):
        gate = int(re.search(r'test "\$size" -lt (\d+)', CI.read_text(encoding="utf-8")).group(1))
        stated = re.findall(r"([\d,]+) B \((\d+) KB\)", text())
        self.assertTrue(stated, "AGENTS.md no longer states the APK gate as 'N B (N KB)'")
        for size, kb in stated:
            self.assertEqual(int(size.replace(",", "")), gate, "AGENTS.md and ci.yml disagree on the APK gate")
            self.assertEqual(int(kb), gate // 1024, "the KB figure next to the gate is wrong")

    def test_min_sdk_matches_build_gradle(self):
        min_sdk = re.search(r"^\s*minSdk\s+(\d+)", GRADLE.read_text(encoding="utf-8"), re.M).group(1)
        stated = re.findall(r"minSdk`?\D{0,6}(\d+)", text())
        self.assertTrue(stated, "AGENTS.md no longer states minSdk")
        for value in stated:
            self.assertEqual(value, min_sdk, "AGENTS.md and launcher/app/build.gradle disagree on minSdk")

    def test_commands_are_the_ones_ci_runs(self):
        ci = CI.read_text(encoding="utf-8")
        for command in ("tools/venv/bin/python -m unittest discover -s tests -v",
                        "./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease",
                        "CLEANYOURTV_STRICT"):
            with self.subTest(command=command):
                self.assertIn(command, ci, "ci.yml changed; update AGENTS.md and this test")
                self.assertIn(command, text(), "AGENTS.md no longer gives the command CI runs")


SUITE_COMMAND = "tools/venv/bin/python -m unittest discover -s tests"


def closing_test_line(body):
    """The paragraph a recipe closes with: AGENTS.md's Workflows section promises each one a check to run."""
    return " ".join(line for line in body.split("\n") if line.startswith("Then run the tests"))


def suite_command_problem(line):
    """Why a recipe's closing line is not the check AGENTS.md names, or None.

    An empty line is no line: two of the recipes (input-sources, perf-ab) end with their own check.
    """
    if not line:
        return None
    if "unittest discover -s tests" not in line:
        return "the closing line no longer runs the suite"
    if SUITE_COMMAND in line:
        return None
    if re.search(r"(?<![\w/.-])python3?\b", line):
        return ("the closing line runs the suite with an interpreter outside the checkout's venv; the suite "
                "imports adb_shell and Pillow, which tools/requirements.txt puts in that venv, so under the "
                f"system python3 those checks skip and the run still answers OK. Write `{SUITE_COMMAND} -v`")
    return f"the closing line does not name `{SUITE_COMMAND}`"


class WorkflowSuiteCommandTest(unittest.TestCase):
    """Each recipe ends with the check to run, and it has to be the one AGENTS.md's row names.

    5865347d moved AGENTS.md's recipes into docs/workflows/ word for word, closing line included. #1800
    then took the suite off the runner's python3 in ci.yml and in AGENTS.md's "Tests, as CI runs them" row,
    because that interpreter has no adb_shell: the commit says test_tv_reboot only reached its tests
    through the child-rerun workaround. The recipes kept the old line, and they are what a contributor
    reads before opening a PR. The suite imports adb_shell and Pillow, which tools/requirements.txt puts in
    tools/venv, so under a system python3 the checks that need them skip, the run still answers OK, and
    nothing in the output says the run was the poorer one.
    """

    def closing_lines(self):
        found = {}
        for workflow in sorted(WORKFLOWS.glob("*.md")):
            line = closing_test_line(workflow.read_text(encoding="utf-8"))
            if line:
                found[workflow.name] = line
        return found

    def test_every_recipe_ends_with_the_suite_agents_md_names(self):
        found = self.closing_lines()
        self.assertGreaterEqual(len(found), 8, "the recipes' closing test line is gone, not fixed")
        for name, line in found.items():
            with self.subTest(workflow=name):
                problem = suite_command_problem(line)
                self.assertIsNone(problem, f"{name}: {problem}")

    def test_it_reads_the_line_the_recipes_carried(self):
        # A reader quiet about this shape would have passed all eight pages as they stood.
        problem = suite_command_problem("Then run the tests: `python3 -m unittest discover -s tests -v`.")
        if problem is None:
            self.fail("the reader passed the eight pages as they stood")
        self.assertIn(SUITE_COMMAND, problem)
        for quiet in ("Then run the tests with the checkout's venv: `" + SUITE_COMMAND + " -v`.",
                      "Then run the tests: `" + SUITE_COMMAND + " -v`.",
                      ""):
            with self.subTest(line=quiet):
                self.assertIsNone(suite_command_problem(quiet))


class AgentsEntryPointsTest(unittest.TestCase):
    def test_claude_and_gemini_import_agents_md(self):
        for name in ("CLAUDE.md", "GEMINI.md"):
            with self.subTest(file=name):
                lines = (ROOT / name).read_text(encoding="utf-8").split("\n")
                self.assertIn("@AGENTS.md", [l.strip() for l in lines], f"{name} must import AGENTS.md")

    def test_people_docs_point_at_it(self):
        for name in ("README.md", "CONTRIBUTING.md"):
            with self.subTest(file=name):
                self.assertIn("](AGENTS.md)", (ROOT / name).read_text(encoding="utf-8"))

    def test_cleaning_a_tv_comes_first(self):
        body = text()
        self.assertLess(body.index("PROMPT.md"), body.index("## Repo map"),
                        "an agent cleaning a TV must be sent to PROMPT.md before the contributor sections")


if __name__ == "__main__":
    unittest.main()

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
    """Folders .gitignore names: they exist on a contributor's machine, not in a clone."""
    lines = (ROOT / ".gitignore").read_text(encoding="utf-8").split("\n")
    return [l.strip().rstrip("/") for l in lines if l.strip().endswith("/") and not l.startswith("#")]


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
        for command in ("python3 -m unittest discover -s tests -v",
                        "./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease",
                        "CLEANYOURTV_STRICT"):
            with self.subTest(command=command):
                self.assertIn(command, ci, "ci.yml changed; update AGENTS.md and this test")
                self.assertIn(command, text(), "AGENTS.md no longer gives the command CI runs")


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

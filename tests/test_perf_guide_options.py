"""Every option a perf tool takes is named in the guide a reader is sent to, and every flag those guides name is real.

`docs/workflows/perf-ab.md` sends a contributor to `tools/trace.md`, `tools/flamechart.md` and
`tools/screendiff.md`, and each of those pages reads as the reference for its tool: the option tables are what
a reader looks in, and there is no `--help` in a page. Six options were in no page at all, `--package` among
them, so a fork built under another package name - SHOWCASE.md lists several - measured `com.gathin.tvhome`,
and the report printed `-` for every step without saying the package was never in the trace.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import ast
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# tool -> (the guide a reader is sent to, the fewest long options it is known to take)
GUIDES = {
    "tools/flamechart.py": ("tools/flamechart.md", 10),
    "tools/trace.py": ("tools/trace.md", 6),
    "tools/trace_report.py": ("tools/trace.md", 4),
    "tools/screendiff.py": ("tools/screendiff.md", 7),
}

# Defaults the option tables state, and the code's own default for them: the table is what a reader compares a
# run against, so a default that moves in one place has to move in the other. (tool, flag) -> table default.
# --highlight's own row escapes its pipe for Markdown and --scale has no row of its own (it is named in --out's),
# so those two are left to the sweep above.
TABLE_DEFAULTS = {
    ("tools/flamechart.py", "--window"): "first-frame",
    ("tools/flamechart.py", "--threads"): "main,RenderThread",
    ("tools/flamechart.py", "--package"): "com.gathin.tvhome",
    ("tools/flamechart.py", "--labels"): "Before,After",
    ("tools/flamechart.py", "--title"): "Launcher: start to first frame",
    ("tools/flamechart.py", "--width"): "1600",
    ("tools/screendiff.py", "--labels"): "Before,After",
    ("tools/screendiff.py", "--threshold"): "8",
}


def source(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


def long_options(tool):
    """Every `--long-option` the tool adds to its parser, read from its own source."""
    found = set()
    for node in ast.walk(ast.parse(source(tool))):
        if not (isinstance(node, ast.Call) and getattr(node.func, "attr", "") == "add_argument"):
            continue
        for arg in node.args:
            if isinstance(arg, ast.Constant) and isinstance(arg.value, str) and arg.value.startswith("--"):
                found.add(arg.value)
    return found


def option_cells(guide):
    """{flag: the rest of its table row} for every option table row of the guide, as cell strings."""
    cells = {}
    for line in source(guide).split("\n"):
        if not line.startswith("|"):
            continue
        parts = [c.strip() for c in line.strip().strip("|").split(" | ")]
        found = re.findall(r"`(--[a-z][a-z-]*)", parts[0])
        if found and parts[0].startswith("`"):
            cells[found[0]] = parts[1:]
    return cells


def table_flags(guide):
    """The `--flags` named in any table row of the guide, cells that only point at a default included."""
    flags = set()
    for line in source(guide).split("\n"):
        if line.startswith("|"):
            flags.update(re.findall(r"`(--[a-z][a-z-]*)", line))
    return flags


class PerfGuideOptionsTest(unittest.TestCase):
    def test_every_option_is_named_in_its_guide(self):
        for tool, (guide, least) in GUIDES.items():
            with self.subTest(tool=tool):
                options = long_options(tool)
                self.assertGreaterEqual(len(options), least, f"{tool}: the parser was not read")
                missing = sorted(o for o in options if o not in source(guide))
                self.assertEqual([], missing, f"{tool}: {guide} names no {', '.join(missing)}")

    def test_every_flag_in_a_guides_table_is_one_of_its_tools(self):
        for guide in {g for g, _ in GUIDES.values()}:
            tools = [t for t, (g, _) in GUIDES.items() if g == guide]
            known = set().union(*(long_options(t) for t in tools))
            with self.subTest(guide=guide):
                self.assertTrue(known, f"{guide}: no tool's options were read")
                self.assertEqual([], sorted(table_flags(guide) - known),
                                 f"{guide} names a flag {' or '.join(tools)} does not take")

    def test_the_option_tables_state_the_defaults_the_code_has(self):
        for (tool, flag), stated in TABLE_DEFAULTS.items():
            cells = option_cells(GUIDES[tool][0])
            with self.subTest(tool=tool, flag=flag):
                self.assertIn(flag, cells, f"{GUIDES[tool][0]} has no {flag} row")
                row = " | ".join(cells[flag])
                self.assertIn(stated, row, f"{flag}'s row does not state its default")
                # the same default, in the code: add_argument(..., default="...") with that flag.
                tree = ast.parse(source(tool))
                values = [node for node in ast.walk(tree)
                          if isinstance(node, ast.Call) and getattr(node.func, "attr", "") == "add_argument"
                          and any(isinstance(a, ast.Constant) and a.value == flag for a in node.args)]
                self.assertTrue(values, f"{tool} has no {flag}")
                default = [k for k in values[0].keywords if k.arg == "default"]
                self.assertTrue(default, f"{tool}'s {flag} has no default, so the table should state none")
                self.assertEqual(stated, str(getattr(default[0].value, "value", None)),
                                 f"{tool}'s {flag} default moved and the table did not")

    def test_the_guides_are_the_pages_the_tools_point_at(self):
        for tool, (guide, _) in GUIDES.items():
            with self.subTest(tool=tool):
                self.assertIn(Path(guide).name, source(tool), f"{tool} does not name {guide}")

    def test_the_fork_package_is_named_where_a_fork_is_measured(self):
        for guide in ("tools/trace.md", "tools/flamechart.md"):
            with self.subTest(guide=guide):
                self.assertIn("--package", source(guide))
                self.assertIn("com.gathin.tvhome", source(guide))


if __name__ == "__main__":
    unittest.main()

"""The regression check in docs/workflows/perf-ab.md, read against the numbers tools/bench.py writes.

Step 3 of the check is "compare the two result.json files" and its table lists the fields with a
limit. bench.py writes eight measured numbers, and two of them were in no page of the repo: the
table's six gates are `response_ms`, `return_to_home_ms`, `cold_start_ms`, `jank_pct`, `pss_mb` and
`idle_launcher_cpu_pct`.

`movement_ms` is the one that matters. It is the end of the run of frames a key set off, so a change
that makes the focus settle later - a longer highlight slide, an added bounce, a slower scroll - moves
it and leaves `response_ms` where it was. A launcher change here regularly touches motion, and the
page sent the reader past the only number that would have shown the cost, under a step that says to
leave out a field only when it is `null`. `idle_cpu_pct` is the whole TV's idle CPU rather than the
launcher's, which is why it is not a limit: it belongs in the page as the context its neighbour
`idle_launcher_cpu_pct` is read in.

So the two are named in the page, and this file keeps the page in step with the script: every number
bench.py measures is named in perf-ab.md, every field the table gates is one bench.py writes (so a
field cannot be invented either), and the sentence that counts the table's fields - "Put the six
fields" - carries the number of rows the table actually has.

Run from the repository root: python3 -m unittest discover -s tests -v
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BENCH = ROOT / "tools" / "bench.py"
PAGE = ROOT / "docs" / "workflows" / "perf-ab.md"

# The keys of the `result` dict bench.py writes to result.json, at the indentation of that dict.
RESULT = re.compile(r"\n    result = \{(.*?)\n    \}", re.S)
KEY = re.compile(r'^        "([a-z_]+)":', re.M)

# A measured number: milliseconds, a percentage, a size. The dict's other keys are identity
# (`launcher`, `package`), the environment, the count of keys, `frames`/`janky_frames` that
# jank_pct is computed from, and `samples`.
MEASURED = re.compile(r"_(?:ms|pct|mb)$")

TABLE_HEADER = "| Field | Fails when the after value is |"
NUMBERS = {word: number for number, word in enumerate(
    "zero one two three four five six seven eight nine ten eleven twelve".split())}


def page():
    return PAGE.read_text(encoding="utf-8")


def bench_fields():
    """Every key of the `result` dict bench.py writes, in the order it writes them."""
    body = RESULT.search(BENCH.read_text(encoding="utf-8"))
    if not body:
        raise AssertionError("tools/bench.py has no `result = {...}` dict; has it been rewritten?")
    fields = KEY.findall(body.group(1))
    if len(fields) < 8:
        raise AssertionError(f"tools/bench.py's result dict holds {len(fields)} keys, not the "
                             "measured numbers; has it been rewritten?")
    return fields


def measured(fields):
    """The measured numbers among the result keys, in the order bench.py writes them."""
    return [f for f in fields if MEASURED.search(f)]


def table_rows(text):
    """The body rows of the step-3 table, as their first cell without Markdown or the `.p50`."""
    lines = text.splitlines()
    start = next((i for i, line in enumerate(lines) if line.strip() == TABLE_HEADER), None)
    if start is None:
        return []
    rows = []
    for line in lines[start + 1:]:
        # The table sits inside a numbered list, so its rows are indented.
        if not line.lstrip().startswith("|"):
            break
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if all(set(cell) <= set("-: ") and cell for cell in cells):
            continue          # the |---|---| separator
        rows.append(cells[0].strip("`").split(".")[0])
    return rows


def counted(text):
    """The number in the sentence that counts the fields to put in the PR, or None."""
    match = re.search(r"Put the ([a-z]+) fields", text)
    return NUMBERS.get(match.group(1)) if match else None


class Page(unittest.TestCase):

    def setUp(self):
        self.text = page()
        self.fields = bench_fields()

    def test_the_parts_are_found(self):
        """The table, the sentence and the numbers are read, so the checks below cannot pass on nothing."""
        self.assertGreaterEqual(len(table_rows(self.text)), 6)
        self.assertIsNotNone(counted(self.text), "perf-ab.md no longer says how many fields to compare")
        self.assertGreaterEqual(len(measured(self.fields)), 8, measured(self.fields))

    def test_every_measured_number_is_named(self):
        """Each number bench.py measures is named in the page that tells the reader what to compare."""
        missing = [f for f in measured(self.fields) if f not in self.text]
        self.assertEqual([], missing, "measured by tools/bench.py and named in no part of "
                         "docs/workflows/perf-ab.md: " + ", ".join(missing))

    def test_movement_is_named(self):
        """The regression itself: the field a motion change moves is in the page."""
        self.assertIn("movement_ms", self.text)

    def test_device_idle_cpu_is_named(self):
        """And the device-wide idle CPU, next to the launcher's own."""
        self.assertIn("idle_cpu_pct", self.text)

    def test_every_gated_field_is_one_bench_writes(self):
        """No invented field: each row of the table names a key of the result dict."""
        invented = [f for f in table_rows(self.text) if f not in self.fields]
        self.assertEqual([], invented, "gated in perf-ab.md and written by nothing in tools/bench.py")


class TheCountedSentence(unittest.TestCase):
    """The number in "Put the six fields" against the rows the table has."""

    def test_the_sentence_counts_the_rows(self):
        text = page()
        self.assertEqual(len(table_rows(text)), counted(text),
                         "perf-ab.md's sentence counts a different number of fields than its table holds")

    def test_a_wrong_number_is_visible(self):
        """The comparison itself: a page whose sentence and table disagree is caught."""
        text = page().replace("Put the six fields", "Put the four fields")
        self.assertNotEqual(len(table_rows(text)), counted(text))

    def test_rows_are_read_without_their_metric(self):
        """"`response_ms.p50`" is row `response_ms`: the suffix is the statistic, not the field."""
        self.assertIn("response_ms", table_rows(page()))


if __name__ == "__main__":
    unittest.main()

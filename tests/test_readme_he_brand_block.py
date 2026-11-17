"""README.he.md keeps a hand copy of the brand grid, and this reads it against the copy the generator writes.

`tools/gen_tables.py` writes the brand block into the pages that carry a `<!-- tv-brands:start -->` marker
and the card whose alt text states the model, brand and language counts (`TRANSLATIONS`). README.he.md
has neither: the Hebrew page went without the card, and its story sentence states those totals in Hebrew,
so `counts()` has no alt triple to read there and the page cannot be added to the script (that is what
PR #3405 recorded when it corrected the block by hand). A copy nobody reads drifts, and this one did
within a day of lists landing: the page said `TCL · 82` against the grid's 83, `Xiaomi · 42` against 43
and `Philips · 22` against 23, held `Toshiba · 5` after the fifth unit was written into that folder, kept
`CHiQ · 2` in the grid where `FFalcon · 3` belongs, and left Challenger and CHiQ out of the two-list line
- while README.md beside it was right and the whole suite was green.

The copy is read against README.md's block, not against `examples/*/tv.json`: a list PR adds its folder and
changes nothing outside it (`docs/workflows/tv-list.md` step 5), so both pages lag together while one is
open and a contributor stays green. The maintainer's regeneration moves README.md, and this then holds the
hand copy to it in the same commit - the rule `tests/test_press_counts.py` applies to the story sentence
beside the card.

The names and the counts are the same words in both pages, so the two blocks are compared as data: the
grid in its order, the two-list line, the single-list names, the projector makers, and the two numbers the
summary states. The Hebrew around them is not read, so rewording the page stays free.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GENERATED = "README.md"
HAND = "README.he.md"
# A grid cell: `[TCL](https://cleanyour.tv/tvs?q=TCL) · 83`.
CELL = re.compile(r"\[([^\]]+)\]\(https://cleanyour\.tv/tvs\?q=[^)]*\) · (\d+)")
# A two-list brand in the details block: `**EKO** 2`.
BOLD = re.compile(r"\*\*([^*]+)\*\* (\d+)")
SUMMARY = re.compile(r"<summary>([^<]*)</summary>")
NUMBER = re.compile(r"\d+")
# The grid's own header: pipes with a space in every cell and no words, `| | | | | | |`.
GRID_HEADER = re.compile(r"\|(?: \|)+")


class Block:
    """The four lists a brand block is made of, plus the two numbers its summary states."""

    def __init__(self, grid, two_lists, single, projectors, summary):
        self.grid = grid            # [(brand, count)] in page order
        self.two_lists = two_lists  # {brand: count}
        self.single = single        # [brand] in page order
        self.projectors = projectors
        self.summary = summary      # (rest, brands of projectors) as the page states them

    def names(self):
        return [b for b, _ in self.grid] + list(self.two_lists) + self.single + self.projectors


def block_lines(text):
    """The lines of the brand block, from the grid's header to the `</details>` that closes it."""
    lines = text.split("\n")
    start = next((i for i, line in enumerate(lines) if GRID_HEADER.fullmatch(line.strip())), None)
    if start is None:
        return None
    end = next((i for i in range(start, len(lines)) if lines[i].strip() == "</details>"), None)
    return None if end is None else lines[start:end + 1]


def block(text):
    """The block a page carries as data, or None when it carries none."""
    lines = block_lines(text)
    if lines is None:
        return None
    summary = next((m.group(1) for line in lines for m in [SUMMARY.search(line)] if m), None)
    if summary is None:
        return None
    numbers = NUMBER.findall(summary)
    if len(numbers) != 2:
        return None
    # The details block: what the page writes under its summary, without the summary itself.
    body = lines[[i for i, line in enumerate(lines) if SUMMARY.search(line)][0] + 1:]
    two_lists = {name: int(count) for line in body for name, count in BOLD.findall(line)}
    projectors = []
    single = []
    for line in body:
        if not line.strip() or line.strip().startswith("<"):
            continue
        if "," in line or " and " in line:
            # The generated line joins its last two makers with "and" and ends "… projectors"; the
            # Hebrew page joins them all with commas and drops the trailing word.
            spell = line.replace(" projectors", "")
            projectors = [name.strip() for name in re.split(r",| and ", spell) if name.strip()]
            continue
        for part in BOLD.sub("", line).split("·"):
            name = part.strip()
            if name:
                single.append(name)
    return Block([(name, int(count)) for line in lines for name, count in CELL.findall(line)],
                 two_lists, single, projectors, (int(numbers[0]), int(numbers[1])))


def counted_differences(generated, hand, what):
    """The names `what` states differently: a count that moved, one the page lost, one it kept."""
    out = []
    counts = [(b, hand[b], generated[b]) for b in generated if b in hand and generated[b] != hand[b]]
    if counts:
        out.append(f"{what} states {counts} as (brand, the page's count, the generator's count)")
    missing = [b for b in generated if b not in hand]
    extra = [b for b in hand if b not in generated]
    if missing:
        out.append(f"{what} lost {missing}")
    if extra:
        out.append(f"{what} names {extra} that the generator does not")
    return out


def differences(generated, hand):
    """Every way the hand copy states something other than the generated block does."""
    if generated is None:
        return [f"{GENERATED} carried no brand block to read"]
    if hand is None:
        return [f"{HAND} carries no brand block any more"]
    out = []
    grid = counted_differences(dict(generated.grid), dict(hand.grid), "the grid")
    if not grid and hand.grid != generated.grid:
        grid = [f"the grid is in another order: {hand.grid}"]
    out += [f"the brand grid: {line}" for line in grid]
    out += [f"the two-list line: {line}"
            for line in counted_differences(generated.two_lists, hand.two_lists, "the two-list line")]
    out += [f"the single-list names: {line}"
            for line in counted_differences({b: 1 for b in generated.single},
                                            {b: 1 for b in hand.single}, "the single-list names")]
    out += [f"the projector makers: {line}"
            for line in counted_differences({b: 1 for b in generated.projectors},
                                            {b: 1 for b in hand.projectors}, "the projector makers")]
    if hand.summary != generated.summary:
        out.append(f"the summary: {HAND} states {hand.summary[0]} more brands and "
                   f"{hand.summary[1]} projector brands, {GENERATED} states {generated.summary[0]} "
                   f"and {generated.summary[1]}")
    return out


def page(name, root=ROOT):
    path = Path(root) / name
    return block(path.read_text(encoding="utf-8")) if path.is_file() else None


class HandCopyTest(unittest.TestCase):
    def test_the_hand_copy_states_what_the_generator_writes(self):
        difference = differences(page(GENERATED), page(HAND))
        self.assertEqual([], difference,
                         "README.he.md is a hand copy of the brand block, and the page it is copied "
                         "from has moved: " + "; ".join(difference))

    def test_both_pages_carry_a_block_to_read(self):
        # A reader that finds nothing would pass the check above for two empty pages.
        for name in (GENERATED, HAND):
            found = page(name)
            self.assertIsNotNone(found, f"{name} carries no brand block any more")
            self.assertGreaterEqual(len(found.grid), 30, f"the grid in {name} was not read")
            self.assertGreaterEqual(len(found.two_lists), 5, f"the two-list line in {name} was not read")
            self.assertGreaterEqual(len(found.single), 40, f"the single-list names in {name} were not read")
            self.assertGreaterEqual(len(found.projectors), 10,
                                    f"the projector makers in {name} were not read")


# A page written here, in the shapes both pages use.
GRID = "| | | | | | |\n|:---|:---|:---|:---|:---|:---|\n" \
       "| [Acme](https://cleanyour.tv/tvs?q=Acme) · 3 | [Bolt](https://cleanyour.tv/tvs?q=Bolt) · 2 |"


def details(summary, two_lists, single, projectors):
    return ("<details>\n<summary>" + summary + "</summary>\n\n<div dir=\"ltr\" align=\"left\">\n\n"
            + "\n".join(two_lists) + "\n" + " · ".join(single) + " ·\n"
            + ", ".join(projectors) + "\n\n</div>\n\n</details>")


def page_text(summary="+2 more brands and 2 projector brands", grid=GRID,
              two_lists=("**Cyan** 2 · **Dune** 2 ·",), single=("Echo", "Fern"), projectors=("Gizmo", "Hub")):
    return "# Page\n\n" + grid + "\n\n" + details(summary, two_lists, single, projectors) + "\n"


class TheReader(unittest.TestCase):
    """differences(), over pages written here rather than the two in the tree."""

    def test_two_pages_that_state_the_same_thing_differ_in_nothing(self):
        self.assertEqual([], differences(block(page_text()), block(page_text())))

    def test_a_localised_summary_is_read_as_the_two_numbers(self):
        hebrew = page_text(summary="עוד 2 מותגים ו־2 מותגי מקרנים",
                           single=("Echo", "Fern"), projectors=("Gizmo", "Hub"))
        self.assertEqual((2, 2), block(hebrew).summary)
        self.assertEqual([], differences(block(page_text()), block(hebrew)))

    def test_a_count_the_page_did_not_move_is_reported(self):
        behind = page_text(grid=GRID.replace("[Acme](https://cleanyour.tv/tvs?q=Acme) · 3",
                                             "[Acme](https://cleanyour.tv/tvs?q=Acme) · 2"))
        reported = differences(block(page_text()), block(behind))
        self.assertEqual(1, len(reported), reported)
        self.assertIn("the brand grid", reported[0])
        self.assertIn("Acme", reported[0])

    def test_a_brand_in_the_grid_where_the_generator_put_another_is_reported(self):
        swapped = page_text(grid=GRID.replace("[Bolt](https://cleanyour.tv/tvs?q=Bolt) · 2",
                                              "[Fern](https://cleanyour.tv/tvs?q=Fern) · 2"))
        reported = differences(block(page_text()), block(swapped))
        self.assertIn("the brand grid", reported[0])

    def test_a_brand_that_gained_a_list_is_reported_between_the_two_lines(self):
        grown = page_text(two_lists=("**Cyan** 2 · **Dune** 2 · **Echo** 2 ·",), single=("Fern",))
        reported = " ".join(differences(block(page_text()), block(grown)))
        self.assertIn("the two-list line", reported)
        self.assertIn("Echo", reported)

    def test_a_name_the_page_dropped_is_reported(self):
        dropped = page_text(single=("Echo",))
        reported = " ".join(differences(block(page_text()), block(dropped)))
        self.assertIn("Fern", reported)

    def test_a_summary_behind_the_block_is_reported(self):
        behind = page_text(summary="+1 more brand and 2 projector brands")
        reported = differences(block(page_text()), block(behind))
        self.assertEqual(1, len(reported), reported)
        self.assertIn("the summary", reported[0])

    def test_a_page_with_no_block_is_reported(self):
        self.assertIsNone(block("# Page\n\nNo block here.\n"))
        self.assertEqual([f"{HAND} carries no brand block any more"], differences(block(page_text()), None))

    def test_the_projector_line_is_not_read_as_single_list_names(self):
        found = block(page_text())
        self.assertEqual(["Gizmo", "Hub"], found.projectors)
        self.assertEqual(["Echo", "Fern"], found.single)
        self.assertNotIn("Gizmo", found.single)


if __name__ == "__main__":
    unittest.main()

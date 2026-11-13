"""A folder that ships a second list names it in the row its `tv.json` produces.

`tools/gen_tables.py` writes both TV tables (README.md's brand line and examples/README.md's row)
from each folder's `tv.json`, so `notes` is the only text a reader browsing the tables sees about a
folder. A folder can carry more than one list - `debloat-keep-cast.txt`, `debloat-display-only.txt`,
`debloat-65u7esg.txt` and the like - and nine of the eleven second lists on main were named in their
folder's `notes` before this test, because that is the only way the reader of a table row learns the
list exists at all.

Two were not. `examples/benq-e55-750` carries `debloat-impex-4k.txt` (the Impex 4K, India, DVB96IP,
Android 11 - a different retail brand on the same Changhong DVB96 board, folded in as a second list
rather than a folder of its own): its row named the BenQ E55-750 only, so an Impex 4K owner reading
the tables found no row for their set and no pointer to the list that covers it. The list's own header
and `debloat.txt` line 5 were the only way in. `examples/oppo-k9-55-oppocnm632` described its second
list as "optional extra list" without naming `debloat-extra.txt`, so a reader had to guess which file
in the folder it was.

The other direction is checked too: a `notes` that names a `debloat*.txt` the folder does not ship
promises a list that is not there, and `docs/workflows/tv-list.md`'s "Docs and README must stay in
step" is what both directions come down to.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"

# The list a folder ships under a name other than `debloat.txt`: `debloat-keep-cast.txt` and friends.
SECOND = re.compile(r"^debloat-.+\.txt$")
# A list file named inside a row, backticked or not.
NAMED = re.compile(r"debloat[\w.-]*\.txt")


def folders(examples):
    """Every folder under `examples/` that ships a `tv.json`, in a stable order."""
    return sorted(p for p in Path(examples).iterdir() if p.is_dir() and (p / "tv.json").is_file())


def notes_of(folder):
    """The `notes` text `gen_tables.py` prints as this folder's row."""
    return json.loads((Path(folder) / "tv.json").read_text(encoding="utf-8")).get("notes", "")


def second_lists(folder):
    """The list files in the folder besides `debloat.txt`."""
    return sorted(p.name for p in Path(folder).iterdir() if p.is_file() and SECOND.match(p.name))


def unnamed(examples):
    """`folder/file` for every second list its folder's row does not name."""
    found = []
    for folder in folders(examples):
        notes = notes_of(folder)
        for name in second_lists(folder):
            if name not in notes:
                found.append(f"{Path(folder).name}/{name}")
    return found


def promised_missing(folder):
    """The list names a row mentions that the folder does not ship."""
    present = {p.name for p in Path(folder).iterdir() if p.is_file()}
    return sorted(set(NAMED.findall(notes_of(folder))) - present)


class SecondListTest(unittest.TestCase):
    def test_every_second_list_is_named_in_its_row(self):
        self.assertEqual(unnamed(EXAMPLES), [],
                         "a reader of the tables cannot learn that these lists exist")

    def test_which_second_lists_the_walk_reads(self):
        # A floor on the walk itself: a tree read that stops opening folders would pass the test above.
        shipped = [(Path(f).name, second_lists(f)) for f in folders(EXAMPLES) if second_lists(f)]
        self.assertGreaterEqual(sum(len(names) for _, names in shipped), 10, shipped)
        self.assertGreaterEqual(len(shipped), 8, shipped)

    def test_a_row_that_does_not_name_its_list_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "benq-e55-750"
            folder.mkdir()
            (folder / "debloat.txt").write_text("com.example.ad\n", encoding="utf-8")
            (folder / "debloat-impex-4k.txt").write_text("com.example.ad\n", encoding="utf-8")
            (folder / "tv.json").write_text(json.dumps({
                "tv": "BenQ E55-750", "os": "Google TV (Android 11)", "brand": "BenQ",
                "notes": "Homwee store/help, Google TV recommendations and ambient mode",
            }), encoding="utf-8")
            self.assertEqual(unnamed(tmp), ["benq-e55-750/debloat-impex-4k.txt"])

    def test_a_row_that_names_it_is_quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "oppo-k9-55-oppocnm632"
            folder.mkdir()
            (folder / "debloat.txt").write_text("com.example.ad\n", encoding="utf-8")
            (folder / "debloat-extra.txt").write_text("com.example.ad\n", encoding="utf-8")
            (folder / "tv.json").write_text(json.dumps({
                "tv": "OPPO K9 55", "os": "Android 9 (ColorOS TV)", "brand": "OPPO",
                "notes": "the optional `debloat-extra.txt` turns off gallery, weather and voice",
            }), encoding="utf-8")
            self.assertEqual(unnamed(tmp), [])

    def test_a_folder_with_one_list_is_quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "tcl-43s5k"
            folder.mkdir()
            (folder / "debloat.txt").write_text("com.example.ad\n", encoding="utf-8")
            (folder / "tv.json").write_text(json.dumps({
                "tv": "TCL 43S5K", "os": "Google TV 14", "brand": "TCL", "notes": "TCL promos",
            }), encoding="utf-8")
            self.assertEqual(unnamed(tmp), [])

    def test_a_row_naming_a_list_that_is_not_there_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "hisense-u75-hengshan"
            folder.mkdir()
            (folder / "debloat.txt").write_text("com.example.ad\n", encoding="utf-8")
            (folder / "debloat-65u7esg.txt").write_text("com.example.ad\n", encoding="utf-8")
            (folder / "tv.json").write_text(json.dumps({
                "tv": "Hisense U75", "os": "Google TV 12", "brand": "Hisense",
                "notes": "its own list (`debloat-65u7esg.txt`), and (`debloat-65u86lua.txt`) that is not here",
            }), encoding="utf-8")
            self.assertEqual(promised_missing(folder), ["debloat-65u86lua.txt"])

    def test_the_tree_promises_no_list_it_does_not_ship(self):
        self.assertEqual({Path(f).name: promised_missing(f) for f in folders(EXAMPLES)
                          if promised_missing(f)}, {})


if __name__ == "__main__":
    unittest.main()

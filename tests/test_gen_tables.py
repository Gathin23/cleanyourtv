"""tools/gen_tables.py: the README tables, the brand line and the counts come out of examples/*/tv.json.

Every list PR used to add rows to the same two tables, so each merge made the other open list PRs conflict.
Now a PR adds only its folder and the script writes the rows. These tests run it on this repository (running
it twice changes nothing, both tables come out in the same alphabetical order) and on small trees written here
(a new folder gets its rows, the brand line counts right, a bad tv.json is refused, a --font path given from
another folder reaches the card scripts, an old-style PR's rows become a tv.json).

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
sys.path.insert(0, str(ROOT / "tools"))
import gen_tables  # noqa: E402  (needs tools/ on the path first)
from test_card_font_argument import shadow_dir  # noqa: E402  (a PIL that cannot be imported)

START = "<!-- {}:start: generated -->"
END = "<!-- {}:end -->"

README = f"""# Title

![card](docs/card.jpg "2 TV models · 2 brands · 2 languages · 1 KB launcher")

| **2 languages** | yes |

Tested lists from contributors:

{START.format("tv-brands")}

old brand line

{END.format("tv-brands")}

<details>

{START.format("tv-table")}

| TV | OS |
|---|---|
| old row | x |

{END.format("tv-table")}

</details>

Hand-written text stays.
"""

EXAMPLES = f"""# Examples

{START.format("tv-table")}

| Folder | TV | OS | Notes |
|---|---|---|---|

{END.format("tv-table")}

Notes for models that have no tested list yet: hand-written.
"""

STATS = 'STATS = [("2", "TV MODELS"), ("2", "BRANDS"), ("2", "LANGUAGES"), ("1 KB", "LAUNCHER")]\n'


def tree(tmp, lists):
    """A small repository: the three files the script writes parts of, and one folder per (name, tv.json)."""
    root = Path(tmp)
    (root / "examples").mkdir()
    (root / "tools").mkdir()
    (root / "README.md").write_text(README, encoding="utf-8")
    (root / "examples" / "README.md").write_text(EXAMPLES, encoding="utf-8")
    (root / "tools" / "readme_stats.py").write_text(STATS, encoding="utf-8")
    for lang in ("de", "fr"):
        (root / f"launcher/app/src/main/res/values-{lang}").mkdir(parents=True)
        (root / f"launcher/app/src/main/res/values-{lang}/strings.xml").write_text("<resources/>\n")
    for name, data in lists:
        add(root, name, data)
    return root


def add(root, name, data):
    folder = root / "examples" / name
    folder.mkdir()
    (folder / "debloat.txt").write_text("# a list\ncom.example.ads\n", encoding="utf-8")
    if data is not None:
        (folder / "tv.json").write_text(json.dumps(data), encoding="utf-8")


def tv(name, brand, os_="Android TV 11", notes="ads off", **extra):
    return dict({"tv": name, "os": os_, "brand": brand, "notes": notes}, **extra)


def run(root, *args):
    out = io.StringIO()
    with redirect_stdout(out), redirect_stderr(out):
        code = gen_tables.main(["--root", str(root), *args])
    return code, out.getvalue()


def section(text, name):
    lines = text.split("\n")
    start = next(i for i, l in enumerate(lines) if l.startswith(f"<!-- {name}:start"))
    end = lines.index(END.format(name))
    return [l for l in lines[start + 1:end] if l]


class ThisRepositoryTest(unittest.TestCase):
    def test_running_it_twice_changes_nothing(self):
        once = gen_tables.render(ROOT)
        self.assertEqual(gen_tables.render(ROOT, texts=once), once)

    def test_the_brand_line_counts_every_folder_once(self):
        readme = gen_tables.render(ROOT)["README.md"]
        line = " ".join(section(readme, "tv-brands"))
        projectors = re.search(r"·\s*([^·]*) projectors$", line).group(1)
        names = re.split(r", | and ", projectors)
        bold = [int(n) for n in re.findall(r"\*\*[^*]+\*\* (\d+)", line)]
        plain = [p.strip() for p in line[:line.rindex(projectors)].split("·")
                 if p.strip() and not p.strip().startswith("**")]
        entries = gen_tables.load(ROOT)
        projector_lists = sum(1 for _, d in entries if d["brand"] in names)
        self.assertEqual(sum(bold) + len(plain) + projector_lists, len(entries))
        brands = len(bold) + len(plain) + len(names)
        self.assertIn(f"{len(entries)} TV models · {brands} brands · ", readme)

    def test_both_tables_are_in_the_same_alphabetical_order(self):
        texts = gen_tables.render(ROOT)
        readme = [r.split(" | ")[0][2:] for r in section(texts["README.md"], "tv-table")[2:]]
        examples = [r.split(" | ")[1] for r in section(texts["examples/README.md"], "tv-table")[2:]]
        self.assertEqual(readme, examples)
        self.assertEqual(readme, sorted(readme, key=str.casefold))

    @unittest.skipUnless(os.environ.get("CLEANYOURTV_STRICT"), "tables are generated on merge; strict on main only")
    def test_the_committed_tables_are_sorted(self):
        # What main shows a reader, not only what the script would write.
        for path, header, cell in (("README.md", "| TV | OS |", 1), ("examples/README.md", "| Folder |", 2)):
            with self.subTest(path=path):
                lines = (ROOT / path).read_text(encoding="utf-8").split("\n")
                start = next(i for i, l in enumerate(lines) if l.startswith(header))
                rows = []
                for line in lines[start + 2:]:
                    if not line.startswith("|"):
                        break
                    rows.append(line.split("|")[cell].strip())
                self.assertGreaterEqual(len(rows), 200)
                self.assertEqual(rows, sorted(rows, key=str.casefold), f"{path}: rows out of order")

    def test_every_folder_has_a_row_in_both_tables(self):
        texts = gen_tables.render(ROOT)
        names = sorted(p.name for p in (ROOT / "examples").iterdir() if p.is_dir())
        rows = section(texts["examples/README.md"], "tv-table")[2:]
        self.assertEqual(sorted(re.match(r"\| `([^`]+)/`", r).group(1) for r in rows), names)
        self.assertEqual(len(section(texts["README.md"], "tv-table")) - 2, len(names))


class GeneratorTest(unittest.TestCase):
    def test_a_new_folder_adds_its_rows_and_counts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL")), ("sony-b", tv("Sony B", "Sony"))])
            self.assertEqual(run(root)[0], 0)
            self.assertEqual(run(root, "--check")[0], 0)
            add(root, "tcl-c", tv("TCL C", "TCL", notes="`com.tcl.ads` disabled"))
            code, out = run(root, "--check")
            self.assertEqual(code, 1)
            self.assertIn("gen_tables.py", out)
            run(root)
            readme = (root / "README.md").read_text(encoding="utf-8")
            examples = (root / "examples/README.md").read_text(encoding="utf-8")
            self.assertEqual(section(readme, "tv-table")[2:],
                             ["| Sony B | Android TV 11 |", "| TCL A | Android TV 11 |", "| TCL C | Android TV 11 |"])
            self.assertEqual(section(examples, "tv-table")[-1],
                             "| `tcl-c/` | TCL C | Android TV 11 | `com.tcl.ads` disabled |")
            self.assertEqual(section(readme, "tv-brands"), ["**TCL** 2 ·", "Sony ·"])
            self.assertIn('"3 TV models · 2 brands · 3 languages · 1 KB launcher"', readme)
            self.assertIn("| **3 languages** | yes |", readme)
            self.assertIn('("3", "TV MODELS"), ("2", "BRANDS"), ("3", "LANGUAGES")',
                          (root / "tools/readme_stats.py").read_text(encoding="utf-8"))
            self.assertTrue(readme.endswith("Hand-written text stays.\n"))
            self.assertTrue(examples.endswith("Notes for models that have no tested list yet: hand-written.\n"))

    def test_it_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL"))])
            run(root)
            before = {p: (root / p).read_text(encoding="utf-8") for p in gen_tables.OWNED}
            self.assertEqual(run(root), (0, "Up to date.\n"))
            self.assertEqual({p: (root / p).read_text(encoding="utf-8") for p in gen_tables.OWNED}, before)

    def test_brand_line_bold_plain_and_projectors(self):
        lists = [("tcl-1", tv("TCL 1", "TCL")), ("tcl-2", tv("TCL 2", "TCL")), ("sony-1", tv("Sony 1", "Sony")),
                 ("sony-2", tv("Sony 2", "Sony")), ("sony-3", tv("Sony 3", "Sony")), ("acer", tv("Acer 1", "Acer")),
                 ("xgimi", tv("XGIMI MoGo projector", "XGIMI")), ("epson", tv("Epson LS800 laser projector", "Epson")),
                 ("benq-tv", tv("BenQ E55", "BenQ")), ("benq-gv", tv("BenQ GV32 portable projector", "BenQ"))]
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, lists)
            run(root)
            readme = (root / "README.md").read_text(encoding="utf-8")
            # Most lists first, then single-list brands; BenQ also makes TVs, so it stays off the projector line.
            self.assertEqual(section(readme, "tv-brands"),
                             ["**Sony** 3 · **BenQ** 2 · **TCL** 2 ·", "Acer ·", "Epson and XGIMI projectors"])
            self.assertIn("10 TV models · 6 brands", readme)

    def test_rows_sort_by_tv_in_both_tables_and_readme_os_applies(self):
        lists = [("z", tv("zulu", "Z")), ("b", tv("Beta", "B")), ("a", tv("Alpha", "A")),
                 ("y", tv("Yankee", "Y", readme_os="Google TV 12"))]
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, lists)
            run(root)
            readme = (root / "README.md").read_text(encoding="utf-8")
            examples = (root / "examples/README.md").read_text(encoding="utf-8")
            # Case is ignored: "zulu" still sorts last.
            self.assertEqual([r.split(" | ")[0][2:] for r in section(readme, "tv-table")[2:]],
                             ["Alpha", "Beta", "Yankee", "zulu"])
            self.assertEqual([r.split(" | ")[1] for r in section(examples, "tv-table")[2:]],
                             ["Alpha", "Beta", "Yankee", "zulu"])
            self.assertIn("| Yankee | Google TV 12 |", readme)
            self.assertIn("| `y/` | Yankee | Android TV 11 |", examples)

    def test_a_bad_tv_json_is_refused_with_its_path(self):
        cases = [(None, "tv.json is missing"),
                 ({"tv": "X", "os": "Android 9", "brand": "X"}, '"notes" must be'),
                 (tv("X", "X", colour="red"), "unknown key"),
                 (tv("X", "X", notes="a | b"), "split the table cell"),
                 (tv("X\n", "X"), "one line"),
                 (tv("X", "X", notes="[note](../docs/devices/none.md)"), "leads nowhere"),
                 # Not a string: refused by name, where the link check used to end in a TypeError (exit 1).
                 (tv("X", "X", notes=["a", "b"]), '"notes" must be'),
                 (tv(["X"], "X"), '"tv" must be')]
        for data, message in cases:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as tmp:
                root = tree(tmp, [("x-1", data)])
                with self.assertRaises(gen_tables.Problem) as caught:
                    gen_tables.load(root)
                self.assertIn("examples/x-1/tv.json", str(caught.exception))
                self.assertIn(message, str(caught.exception))
                self.assertEqual(run(root, "--check")[0], 2)

    def test_an_escaped_pipe_and_a_working_link_are_fine(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("x-1", tv("X", "X", notes="a \\| b, [list](x-1/debloat.txt), [web](https://x.y)"))])
            self.assertEqual(len(gen_tables.load(root)), 1)

    def test_markers_must_be_there_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("a", tv("Alpha", "A"))])
            (root / "examples/README.md").write_text("# Examples\n", encoding="utf-8")
            with self.assertRaisesRegex(gen_tables.Problem, "tv-table:start"):
                gen_tables.render(root)


class FontTest(unittest.TestCase):
    def test_a_font_path_from_another_folder_reaches_the_card_scripts(self):
        # --font is checked from where the script is run, and the card scripts run in the checkout.
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "repo").mkdir()
            root = tree(Path(tmp) / "repo", [("tcl-a", tv("TCL A", "TCL"))])
            # The real script, whose counts this tree changes, so --font hands it the font.
            shutil.copy(ROOT / "tools" / "readme_stats.py", root / "tools" / "readme_stats.py")
            # A checkout has docs/, where the picture goes: the script names the folder it cannot find.
            (root / "docs").mkdir()
            caller = Path(tmp) / "caller"
            caller.mkdir()
            (caller / "Fredoka.ttf").write_bytes(b"")
            (Path(tmp) / "no-pillow").mkdir()
            env = dict(os.environ, PYTHONPATH=shadow_dir(Path(tmp) / "no-pillow"))
            done = subprocess.run([sys.executable, str(ROOT / "tools" / "gen_tables.py"), "--root", str(root),
                                   "--font", "Fredoka.ttf"], cwd=caller, env=env, capture_output=True, text=True)
        self.assertNotIn("cannot open the font at", done.stderr, "the card script looked for the font in the checkout")
        # Pillow is hidden, so the script stops at its Pillow line: it got past the font.
        self.assertIn("needs Pillow", done.stderr, done.stderr)


class AdoptTest(unittest.TestCase):
    """--adopt: an open PR from before the script carries its rows in the tables, not in a tv.json."""

    PATCH = """diff --git a/README.md b/README.md
@@ -120,3 +120,4 @@
 | TCL A | Android TV 11 |
+| TCL 55L8 (China, x32a0) | Android TV 9 (CN build) |
 | TCL Z | Android TV 11 |
diff --git a/examples/README.md b/examples/README.md
@@ -10,3 +10,4 @@
 | `tcl-a/` | TCL A | Android TV 11 | ads off |
+| `tcl-55l8/` | TCL 55L8 (China, x32a0) | Android TV 9 | Huan ads off; `com.tcl.ota` kept |
+| `hartens-x/` | Hartens X (YaOS) | Android 9 | Yandex rows off |
-| `tcl-a/` | TCL A | Android TV 11 | ads off |
+| `tcl-a/` | TCL A | Android TV 11 | ads off; second unit, UK |
"""

    def test_rows_become_tv_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL", notes="ads off"))])
            add(root, "tcl-55l8", None)
            add(root, "hartens-x", None)
            patch = root / "pr.diff"
            patch.write_text(self.PATCH, encoding="utf-8")
            code, out = run(root, "--adopt", str(patch))
            self.assertEqual(code, 0, out)
            new = json.loads((root / "examples/tcl-55l8/tv.json").read_text(encoding="utf-8"))
            self.assertEqual(new, {"tv": "TCL 55L8 (China, x32a0)", "os": "Android TV 9", "brand": "TCL",
                                   "notes": "Huan ads off; `com.tcl.ota` kept",
                                   "readme_os": "Android TV 9 (CN build)"})
            self.assertEqual(json.loads((root / "examples/hartens-x/tv.json").read_text())["brand"], "Hartens")
            self.assertIn("Hartens", out)
            self.assertIn("check the spelling", out)
            # A PR that edited its own row: the existing tv.json takes the new notes, and keeps its brand.
            edited = json.loads((root / "examples/tcl-a/tv.json").read_text(encoding="utf-8"))
            self.assertEqual(edited["notes"], "ads off; second unit, UK")
            self.assertEqual(edited["brand"], "TCL")
            self.assertEqual(run(root, "--check")[0], 0)
            self.assertIn("| TCL 55L8 (China, x32a0) | Android TV 9 (CN build) |",
                          (root / "README.md").read_text(encoding="utf-8"))

    def test_a_row_main_has_changed_since_is_not_overwritten(self):
        # The PR edits the row as it was ("ads off"); main has moved on, so taking the PR's row would undo main.
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL", notes="ads off; promos off, second unit"))])
            add(root, "tcl-55l8", None)
            patch = root / "pr.diff"
            patch.write_text(self.PATCH, encoding="utf-8")
            code, out = run(root, "--adopt", str(patch))
            self.assertEqual(code, 2)
            self.assertIn("examples/tcl-a/tv.json: the row this PR edits has changed on main", out)
            self.assertEqual(json.loads((root / "examples/tcl-a/tv.json").read_text())["notes"],
                             "ads off; promos off, second unit")
            # The new folder in the same PR still gets its tv.json.
            self.assertTrue((root / "examples/tcl-55l8/tv.json").is_file())


if __name__ == "__main__":
    unittest.main()

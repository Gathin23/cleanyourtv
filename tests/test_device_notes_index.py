"""Every device note under docs/devices/ is linked from a page a reader can reach.

docs/devices/ is where a device report lives until someone has lived with a list long enough to
promote it into examples/. examples/README.md is the index readers come through: each row of its
table links the note that belongs to that list, and the section "Notes for models that have no
tested list yet" links the reports that have no list at all. A note in neither place is only found
by browsing the folder on GitHub, and three of them were: the Sharp 32FI4EA report, the TCL/FFalcon
MT9655 China report (whose example row never got the link its siblings have) and the MiTV-MTEQ0
baymax firmware notes. This walks the markdown once and fails on the notes nothing points at.

What counts as a reference: markdown links and images, reference definitions and `href`/`src` in raw
HTML - the destinations a reader can click, resolved relative to the page carrying them. What does
not: `http(s)`, `mailto:`, `data:`, a bare `#anchor`, and the insides of fenced code blocks, where a
path is an example rather than a link. Anchors are ignored: `note.md#section` still points at the
file, and a heading's slug is GitHub's, not ours.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import tempfile
import unittest
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
NOTES = "docs/devices"

# Not documentation: the checkout's git data, the tools venv CI builds before this job runs
# (site-packages ships its own .md files), Gradle's output and a theme's node_modules.
SKIP = {".git", "venv", "node_modules", "build", ".gradle", "__pycache__"}

MARKDOWN_LINK = re.compile(r"\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
REFERENCE_DEF = re.compile(r"^\[[^\]]+\]:\s*(\S+)", re.M)
HTML_ATTR = re.compile(r"(?:href|src)=\"([^\"]+)\"")
FENCE = re.compile(r"```.*?```", re.S)
EXTERNAL = ("http://", "https://", "mailto:", "data:", "#", "<")


def markdown_files(root):
    """Every .md below root, outside the folders in SKIP, in a stable order."""
    root = Path(root)
    for dirpath, dirnames, filenames in os.walk(root):
        # Prune instead of filtering after the walk: a venv holds thousands of files we never read.
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP)
        for name in sorted(filenames):
            if name.endswith(".md"):
                yield Path(dirpath) / name


def destinations(text):
    """The link destinations in one page: inline links/images, reference definitions, HTML."""
    text = FENCE.sub("", text)
    return MARKDOWN_LINK.findall(text) + REFERENCE_DEF.findall(text) + HTML_ATTR.findall(text)


def referenced(root):
    """Repository-relative paths of every file the markdown links to, as posix strings."""
    # Resolved like the link targets below: on macOS the temporary directory /var/folders/... is a symlink to
    # /private/var/folders/..., and an unresolved root put every target outside it.
    root = Path(root).resolve()
    out = set()
    for page in markdown_files(root):
        for target in destinations(page.read_text(encoding="utf-8", errors="replace")):
            if target.startswith(EXTERNAL):
                continue
            # A link may carry an anchor of its own ("note.md#section"): the file is still the target.
            local = target.split("#")[0]
            if not local:
                continue
            resolved = (page.parent / unquote(local)).resolve()
            out.add(os.path.relpath(resolved, root).replace(os.sep, "/"))
    return out


def unlinked_notes(root):
    """Repository-relative paths of the files under docs/devices/ that no markdown file links to."""
    root = Path(root)
    linked = referenced(root)
    out = []
    for name in sorted(os.listdir(root / NOTES)):
        path = root / NOTES / name
        if not path.is_file():
            continue
        rel = f"{NOTES}/{name}"
        if rel not in linked:
            out.append(rel)
    return out


# The heading that opens examples/README.md's list of device reports that have no list of their own.
NOTE_INDEX_HEADING = "Notes for models that have no tested list yet"
# A table row of examples/README.md, by the folder its first cell names.
FOLDER_ROW = re.compile(r"^\|\s*`([A-Za-z0-9_.-]+)/`\s*\|")


def section(text, heading):
    """The lines below the line that contains `heading`, up to the next '## ' heading."""
    out, inside = [], False
    for line in text.splitlines():
        if not inside:
            inside = heading in line
            continue
        if line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def index_notes(root):
    """The device notes listed under the "no tested list yet" heading, by file name."""
    page = Path(root) / "examples" / "README.md"
    if not page.is_file():
        return []
    prefix = "../docs/devices/"
    notes = []
    for target in destinations(section(page.read_text(encoding="utf-8", errors="replace"),
                                       NOTE_INDEX_HEADING)):
        if target.startswith(EXTERNAL):
            continue
        local = unquote(target.split("#")[0])
        if local.startswith(prefix):
            notes.append(local[len(prefix):])
    return notes


def own_list(root, note):
    """The list a note claims for its own model - examples/<the note's file name>/debloat.txt - when it links one.

    Read from the note rather than from a list kept here: a note that says "a tested package list for
    this unit lives in examples/<folder>/debloat.txt" has a list, whatever the index above says.
    """
    root = Path(root)
    path = root / NOTES / note
    if not path.is_file():
        return None
    stem = Path(note).stem
    for target in destinations(path.read_text(encoding="utf-8", errors="replace")):
        if target.startswith(EXTERNAL):
            continue
        resolved = (path.parent / unquote(target.split("#")[0])).resolve()
        try:
            rel = os.path.relpath(resolved, root.resolve()).replace(os.sep, "/")
        except ValueError:
            continue
        if rel == f"examples/{stem}/debloat.txt" and resolved.is_file():
            return rel
    return None


def index_notes_with_their_own_list(root):
    """Notes filed under "no tested list yet" that link a list for their own model anyway."""
    out = []
    for note in index_notes(root):
        own = own_list(root, note)
        if own:
            out.append(f"{NOTES}/{note} is filed under \"{NOTE_INDEX_HEADING}\" but links {own}: "
                       f"that folder's row is where it belongs")
    return out


def table_rows(root):
    """examples/README.md's folder rows, as {folder: the row's text}."""
    page = Path(root) / "examples" / "README.md"
    if not page.is_file():
        return {}
    rows = {}
    for line in page.read_text(encoding="utf-8", errors="replace").splitlines():
        match = FOLDER_ROW.match(line)
        if match:
            rows[match.group(1)] = line
    return rows


def rows_not_linking_their_note(root):
    """Folders whose own device note exists but whose row in examples/README.md does not link it."""
    root = Path(root)
    rows = table_rows(root)
    out = []
    if not (root / "examples").is_dir():
        return out
    for folder in sorted(os.listdir(root / "examples")):
        directory = root / "examples" / folder
        if not directory.is_dir() or not (directory / "debloat.txt").is_file():
            continue
        for suffix in (".md", ".txt"):
            note = f"{folder}{suffix}"
            if not (root / NOTES / note).is_file():
                continue
            link = f"../docs/devices/{note}"
            if link not in rows.get(folder, ""):
                out.append(f"examples/{folder}/ has {NOTES}/{note} but its row does not link it "
                           f"({link})")
    return out


class DeviceNotesLinkedTest(unittest.TestCase):
    def test_every_device_note_is_linked(self):
        unlinked = unlinked_notes(ROOT)
        message = "\n".join(unlinked)
        self.assertEqual([], unlinked, "notes nothing links to (add them to examples/README.md, "
                                       "either to the row of their list or to the notes index):\n" + message)

    def test_a_note_nothing_links_to_is_reported(self):
        # The checker has to be able to fail, or the test above proves nothing.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / NOTES).mkdir(parents=True)
            (root / NOTES / "orphan.md").write_text("# Orphan\n", encoding="utf-8")
            (root / "README.md").write_text("[list](examples/other/debloat.txt)\n", encoding="utf-8")
            self.assertEqual([f"{NOTES}/orphan.md"], unlinked_notes(root))

    def test_a_row_that_links_its_note_counts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / NOTES).mkdir(parents=True)
            (root / NOTES / "baymax.md").write_text("# Notes\n", encoding="utf-8")
            (root / "examples").mkdir()
            (root / "examples" / "README.md").write_text(
                "| `xiaomi-mitv-mteq0/` | Xiaomi | Android 10 | list; "
                "[notes](../docs/devices/baymax.md) |\n", encoding="utf-8")
            self.assertEqual([], unlinked_notes(root))

    def test_a_note_named_outside_a_link_is_still_reported(self):
        # A path shown in a command, a bare backticked path or an external URL is not a way through.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / NOTES).mkdir(parents=True)
            (root / NOTES / "sharp-32fi4ea.md").write_text("# Sharp\n", encoding="utf-8")
            (root / "README.md").write_text(
                "```\nsee docs/devices/sharp-32fi4ea.md\n```\n"
                "`docs/devices/sharp-32fi4ea.md`\n"
                "[upstream](https://github.com/Gathin23/cleanyourtv/blob/main/docs/devices/sharp-32fi4ea.md)\n",
                encoding="utf-8")
            self.assertEqual([f"{NOTES}/sharp-32fi4ea.md"], unlinked_notes(root))

    def test_an_anchor_or_an_encoded_space_in_the_destination_still_counts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / NOTES).mkdir(parents=True)
            (root / NOTES / "note one.md").write_text("# Note\n", encoding="utf-8")
            (root / "README.md").write_text("[notes](docs/devices/note%20one.md)\n", encoding="utf-8")
            self.assertEqual([], unlinked_notes(root))

    def test_build_folders_are_skipped(self):
        # site-packages inside the CI venv links to plenty of markdown that is not ours.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / NOTES).mkdir(parents=True)
            (root / NOTES / "note.md").write_text("# Note\n", encoding="utf-8")
            (root / "venv" / "lib").mkdir(parents=True)
            (root / "venv" / "lib" / "third-party.md").write_text(
                "[note](../../docs/devices/note.md)\n", encoding="utf-8")
            self.assertEqual([f"{NOTES}/note.md"], unlinked_notes(root))


class NoteFiledWithItsListTest(unittest.TestCase):
    """A note whose model has a list belongs to that list's row, not to the "no tested list yet" index.

    The index's own heading is the rule: a report filed there says its model has no list. When the
    list later lands, the report belongs to the row (the way tcl-50dp660, tcl-beyondtv2-v660 and
    xiaomi-mitv-mosr4 are filed), otherwise the page tells a reader the opposite of what it ships.
    """

    def test_a_note_with_its_own_list_is_not_in_the_no_list_index(self):
        self.assertGreaterEqual(len(index_notes(ROOT)), 20, "the notes index was not read")
        offenders = index_notes_with_their_own_list(ROOT)
        self.assertEqual([], offenders, "\n".join(offenders))

    def test_every_row_links_the_note_of_its_own_folder(self):
        self.assertGreaterEqual(len(table_rows(ROOT)), 200, "the examples table was not read")
        offenders = rows_not_linking_their_note(ROOT)
        self.assertEqual([], offenders, "\n".join(offenders))

    # --- the two checkers, against pages written here, so neither can pass by reading nothing ---

    @staticmethod
    def _tree(tmp):
        root = Path(tmp)
        (root / NOTES).mkdir(parents=True)
        folder = root / "examples" / "sony-x9000h-cn"
        folder.mkdir(parents=True)
        (folder / "debloat.txt").write_text("# a list\n", encoding="utf-8")
        return root

    def test_a_row_that_links_its_note_is_quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp)
            (root / NOTES / "sony-x9000h-cn.md").write_text("# Note\n", encoding="utf-8")
            (root / "examples" / "README.md").write_text(
                "| `sony-x9000h-cn/` | Sony BRAVIA X9000H | Android TV 12 | reboot-checked; "
                "[handover notes](../docs/devices/sony-x9000h-cn.md) |\n", encoding="utf-8")
            self.assertEqual([], rows_not_linking_their_note(root))

    def test_a_row_that_does_not_link_its_note_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp)
            (root / NOTES / "sony-x9000h-cn.md").write_text("# Note\n", encoding="utf-8")
            (root / "examples" / "README.md").write_text(
                "| `sony-x9000h-cn/` | Sony BRAVIA X9000H | Android TV 12 | reboot-checked |\n",
                encoding="utf-8")
            offenders = rows_not_linking_their_note(root)
            self.assertEqual(1, len(offenders))
            self.assertIn("docs/devices/sony-x9000h-cn.md", offenders[0])

    def test_a_note_in_the_index_that_links_its_own_list_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp)
            (root / NOTES / "sony-x9000h-cn.md").write_text(
                "# Note\n\nA tested package list for this unit lives in "
                "[`examples/sony-x9000h-cn/debloat.txt`](../../examples/sony-x9000h-cn/debloat.txt).\n",
                encoding="utf-8")
            (root / "examples" / "README.md").write_text(
                f"{NOTE_INDEX_HEADING} live in [`docs/devices/`](../docs/devices/):\n\n"
                "- [Sony X9000H](../docs/devices/sony-x9000h-cn.md): a 33-package reboot-checked list.\n",
                encoding="utf-8")
            offenders = index_notes_with_their_own_list(root)
            self.assertEqual(1, len(offenders))
            self.assertIn("examples/sony-x9000h-cn/debloat.txt", offenders[0])

    def test_a_note_that_links_a_siblings_list_is_quiet(self):
        # The usual shape of an index entry: a report with no list of its own that compares itself
        # with another model's list. Only the note's own folder counts.
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp)
            (root / NOTES / "strong-leap-s3.md").write_text(
                "# Note\n\nSame firmware family as "
                "[the Wanbo list](../../examples/sony-x9000h-cn/debloat.txt).\n", encoding="utf-8")
            (root / "examples" / "README.md").write_text(
                f"{NOTE_INDEX_HEADING} live in [`docs/devices/`](../docs/devices/):\n\n"
                "- [Strong LEAP-S3](../docs/devices/strong-leap-s3.md): a one-day list kept as a note.\n",
                encoding="utf-8")
            self.assertEqual([], index_notes_with_their_own_list(root))

    def test_a_note_linked_outside_the_index_section_is_not_an_index_entry(self):
        # "Promote this to examples/<folder>/" in prose is not a link either, so a report waiting for
        # its list does not read as one that has it.
        with tempfile.TemporaryDirectory() as tmp:
            root = self._tree(tmp)
            note = root / NOTES / "sony-x9000h-cn.md"
            note.write_text(
                "# Note\n\nPromote this to `examples/sony-x9000h-cn/` once someone has lived with it.\n",
                encoding="utf-8")
            (root / NOTES / "fpd-cg50-c3.md").write_text("# Note\n", encoding="utf-8")
            (root / "examples" / "README.md").write_text(
                f"{NOTE_INDEX_HEADING} live in [`docs/devices/`](../docs/devices/):\n\n"
                "- [FPD CG50-C3](../docs/devices/fpd-cg50-c3.md): a one-day list.\n\n"
                "## Add yours\n\n"
                "- [Sony X9000H](../docs/devices/sony-x9000h-cn.md): a 33-package list.\n",
                encoding="utf-8")
            self.assertEqual(["fpd-cg50-c3.md"], index_notes(root))
            self.assertEqual([], index_notes_with_their_own_list(root))


if __name__ == "__main__":
    unittest.main()

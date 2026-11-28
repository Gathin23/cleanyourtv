"""The Wanbo X2 Pro note (#2809), and the rule that keeps a recovery page out of the repo's files.

The maintainer asked for this page on 2026-11-03 and again on 2026-11-11, after the reporter declined to open the
PR himself: `docs/devices/wanbo-x2-pro.md`, from the thread, crediting @666xgod, with the USB stick layout and steps,
the two panel revisions, and the OTA warning - and without a firmware link, because the image is Wanbo's and the repo
cannot check what is inside it.

So the tests pin what makes the page usable and safe: the board, the SoC, the two panel descriptors, the two file
names and the stick's format; that the page is reachable from the index a reader comes through; and that it links no
image at all. They assert identifiers, not sentences, so rewording the prose does not fail them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "wanbo-x2-pro.md"
EXAMPLES_README = ROOT / "examples" / "README.md"
INDEX_HEADING = "Notes for models that have no tested list yet"
ISSUE = "https://github.com/Gathin23/cleanyourtv/issues/2809"

LINK = re.compile(r"\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
EXTERNAL = ("http://", "https://", "mailto:", "#")

# What the thread records about this board, and what a reader needs to tell the two revisions apart.
IDENTIFIERS = [
    "WPA22",
    "RG_5H045_I20_3D",
    "Hi3751V352F",
    "QY50015FOG_ST5892B_1080P_FM",
    "FPC_QY40091FOG_01_EK79202_BOE3.96_720P_M",
    "newlink_update.bin",
    "nlusbautoupdate.txt",
    "FAT32",
    "MBR",
]

# A firmware archive or a hosted image: the page must not hand one out, whatever else it says.
IMAGE_SUFFIXES = (".7z", ".zip", ".rar", ".bin", ".img")
IMAGE_HOSTS = ("drive.google.com", "mega.nz", "dropbox.com", "mediafire.com")


def note_text():
    return NOTE.read_text(encoding="utf-8")


def destinations(text):
    """Every link destination in a piece of markdown."""
    return LINK.findall(text)


def index_section():
    """The lines of examples/README.md below the "no tested list yet" heading, up to the next heading."""
    out, inside = [], False
    for line in EXAMPLES_README.read_text(encoding="utf-8").splitlines():
        if not inside:
            inside = INDEX_HEADING in line
            continue
        if line.startswith("#"):
            break
        out.append(line)
    return "\n".join(out)


def index_links():
    """The destinations under the index heading."""
    return destinations(index_section())


def broken_links(path):
    """Relative link destinations in `path` that name a file the checkout does not hold."""
    broken = []
    for target in destinations(path.read_text(encoding="utf-8")):
        if target.startswith(EXTERNAL):
            continue
        local = target.split("#", 1)[0]
        if local and not (path.parent / local).exists():
            broken.append(target)
    return broken


def image_links(text):
    """Links that hand out a firmware file rather than a page about one."""
    out = []
    for target in destinations(text):
        lowered = target.lower().split("?", 1)[0]
        if lowered.startswith(EXTERNAL):
            if any(host in lowered for host in IMAGE_HOSTS):
                out.append(target)
            continue
        if lowered.endswith(IMAGE_SUFFIXES):
            out.append(target)
    return out


class WanboX2ProNoteTest(unittest.TestCase):
    def test_the_note_exists_and_names_the_board(self):
        self.assertTrue(NOTE.is_file(), f"{NOTE} is missing")
        text = note_text()
        for identifier in IDENTIFIERS:
            self.assertIn(identifier, text, f"the note no longer names {identifier}")

    def test_the_note_opens_with_a_heading_naming_the_model(self):
        first = note_text().splitlines()[0]
        self.assertTrue(first.startswith("# "), first)
        self.assertIn("X2 Pro", first)

    def test_the_note_links_the_issue_it_came_from(self):
        self.assertIn(ISSUE, note_text())

    def test_the_note_links_resolve(self):
        self.assertEqual([], broken_links(NOTE))

    def test_the_note_hands_out_no_image(self):
        self.assertEqual([], image_links(note_text()))

    def test_the_index_bullet_hands_out_no_image(self):
        self.assertEqual([], image_links(index_section()))

    def test_the_note_claims_no_debloat_list(self):
        # Nothing in the report is a package list, so the page must not link one it does not have.
        linked = [t for t in destinations(note_text()) if "debloat" in t]
        self.assertEqual([], linked)

    def test_examples_readme_indexes_the_note_once(self):
        hits = [target for target in index_links() if target.endswith("docs/devices/wanbo-x2-pro.md")]
        self.assertEqual(["../docs/devices/wanbo-x2-pro.md"], hits,
                         "the note needs exactly one entry under the notes index")

    def test_the_index_entry_names_the_model_and_the_stick_files(self):
        line = next(l for l in index_section().splitlines() if "wanbo-x2-pro.md" in l)
        for token in ("Wanbo X2 Pro", "WPA22", "newlink_update.bin", "nlusbautoupdate.txt"):
            self.assertIn(token, line)

    # --- the readers above, against text written here, so a quiet result means something ---

    def test_broken_links_reports_a_missing_target(self):
        with self.subTest("missing"):
            self.assertEqual(["gone.md"], broken_links_path("see [x](gone.md)\n"))

    def test_broken_links_ignores_external_links_and_anchors(self):
        self.assertEqual([], broken_links_path("[a](https://example.com/x.md) [b](#heading)\n"))

    def test_an_archive_link_is_reported(self):
        self.assertEqual(["https://drive.google.com/file/d/abc/view"],
                         image_links("[firmware](https://drive.google.com/file/d/abc/view)\n"))

    def test_a_page_link_is_not_an_image(self):
        self.assertEqual([], image_links("[the issue](https://github.com/Gathin23/cleanyourtv/issues/2809)\n"))


def broken_links_path(text, name="page.md"):
    """broken_links() on a page written into a temporary directory, so the checker can be seen to fail."""
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / name
        page.write_text(text, encoding="utf-8")
        return broken_links(page)


if __name__ == "__main__":
    unittest.main()

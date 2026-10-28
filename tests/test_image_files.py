"""Every file the repository ships with an image extension is really that image.

`tools/tv.py --screenshot` pulls the picture off the TV and refuses it when it did not come back
as a PNG (the `PNG` magic at the top of the file), because `adb_shell`'s `shell()` decodes returns
as text and a screenshot that went through it is 700 KB of base64. That guard covers the shots the
tool writes; nothing covered the ones already in the tree, and two had come in as text:
`examples/xiaomi-mitv-astp1/screenshot.png` (727 KB) and `screenshot_settings.png` (68 KB) both
began `iVBORw0KGgo`, so GitHub drew a broken image, a text editor showed base64, and no page or test
noticed - the folder has no README and nothing linked either file.

This walks every file once and reads its first bytes: a `.png`, `.jpg`, `.jpeg`, `.gif` or `.webp`
has to start with the magic those formats carry. It also tells a base64 dump from another kind of
damage, so the message names the cause instead of the symptom.

What is not read: `.git`, Gradle's `build`, a theme's `node_modules`, `__pycache__` and the tools
venv CI builds before the job (site-packages ships images of its own), the same folders the link
check skips.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SKIP = {".git", "venv", "node_modules", "build", ".gradle", "__pycache__"}

# What the file's first bytes have to be. A GIF has two accepted headers; WebP is RIFF....WEBP.
MAGIC = {
    ".png": (b"\x89PNG\r\n\x1a\n",),
    ".jpg": (b"\xff\xd8\xff",),
    ".jpeg": (b"\xff\xd8\xff",),
    ".gif": (b"GIF87a", b"GIF89a"),
    ".webp": (),  # checked in why_not(), it is not a prefix
}
HEAD = 16
# The start of a base64-encoded PNG, which is what a screenshot written through a text pipe looks like.
BASE64_PNG = (b"iVBORw0KGgo", b"iVBORw0KGgoAAAANSUhEUg")


def image_files(root):
    """Every file with an image extension below root, outside the folders in SKIP, in a stable order."""
    root = Path(root)
    for dirpath, dirnames, filenames in os.walk(root):
        # Prune instead of filtering afterwards: the tools venv holds thousands of files we never read.
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP)
        for name in sorted(filenames):
            path = Path(dirpath) / name
            if path.suffix.lower() in MAGIC:
                yield path


def why_not(path):
    """None when the file starts with the image its name promises, else the reason, in a sentence.

    A truncated file is reported too: the formats all begin with their magic, so an image that does
    not is either empty, damaged, or not an image at all.
    """
    head = Path(path).open("rb").read(HEAD)
    suffix = Path(path).suffix.lower()
    if suffix == ".webp":
        if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
            return None
        return f"starts with {head[:12]!r}, not RIFF....WEBP"
    if any(head.startswith(magic) for magic in MAGIC[suffix]):
        return None
    if head.startswith(BASE64_PNG):
        return f"starts with {head[:12]!r}: that is base64 text of a PNG, written out instead of the image itself"
    return f"starts with {head[:12]!r}, not the {suffix[1:].upper()} header"


def failures(root):
    """(file, reason) for every image-named file that is not that image, in file order."""
    out = []
    for path in image_files(root):
        reason = why_not(path)
        if reason is not None:
            out.append((path, reason))
    return out


class RepositoryImages(unittest.TestCase):
    def test_every_image_in_the_repository_is_that_image(self):
        bad = failures(ROOT)
        self.assertEqual([], [f"{path.relative_to(ROOT)}: {reason}" for path, reason in bad],
                         "an image-named file that is not that image: a screenshot saved as text, "
                         "or a pull that came back damaged")

    def test_the_walk_finds_the_repositorys_images(self):
        # themes/ requires a screenshot.jpg each, docs/ carries the card art: a walk that found
        # nothing would pass the test above without reading anything.
        self.assertGreaterEqual(len(list(image_files(ROOT))), 80)


class TheChecker(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.addCleanup(self.tmp.cleanup)

    def write(self, name, data):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def test_a_png_is_accepted(self):
        self.write("shot.png", b"\x89PNG\r\n\x1a\n" + b"\x00" * 40)
        self.assertEqual([], failures(self.root))

    def test_base64_text_named_png_is_reported_as_base64(self):
        path = self.write("shot.png", b"iVBORw0KGgoAAAANSUhEUgAABQAAAALQCAIAAABAH0oBA" + b"A" * 40)
        bad = failures(self.root)
        self.assertEqual([path], [p for p, _ in bad])
        self.assertIn("base64", bad[0][1], "the message should name the cause, not only the symptom")

    def test_a_jpeg_and_both_gif_headers_are_accepted(self):
        self.write("a.jpg", b"\xff\xd8\xff\xe0" + b"\x00" * 20)
        self.write("b.jpeg", b"\xff\xd8\xff\xe1" + b"\x00" * 20)
        self.write("c.gif", b"GIF87a" + b"\x00" * 20)
        self.write("d.gif", b"GIF89a" + b"\x00" * 20)
        self.assertEqual([], failures(self.root))

    def test_a_webp_needs_riff_and_webp_not_only_one_of_them(self):
        self.write("real.webp", b"RIFF\x24\x00\x00\x00WEBPVP8 " + b"\x00" * 8)
        self.assertEqual([], failures(self.root))
        # A RIFF file that is not WebP (an AVI) keeps the first half of the header and fails.
        self.write("avi.webp", b"RIFF\x24\x00\x00\x00AVI LIST" + b"\x00" * 8)
        self.assertEqual([self.root / "avi.webp"], [p for p, _ in failures(self.root)])

    def test_an_uppercase_extension_is_read(self):
        self.write("Shot.PNG", b"iVBORw0KGgoAAAANSUhEUg" + b"A" * 40)
        self.assertEqual([self.root / "Shot.PNG"], [p for p, _ in failures(self.root)])

    def test_a_png_that_was_never_written_is_reported(self):
        # An empty file is what a pull that failed half way leaves behind.
        self.write("empty.png", b"")
        self.assertEqual([self.root / "empty.png"], [p for p, _ in failures(self.root)])

    def test_a_pruned_folder_is_not_read(self):
        self.write("venv/lib/shot.png", b"iVBORw0KGgoAAAANSUhEUg" + b"A" * 40)
        self.write("node_modules/shot.png", b"iVBORw0KGgoAAAANSUhEUg" + b"A" * 40)
        self.assertEqual([], failures(self.root))

    def test_a_file_without_an_image_extension_is_not_read(self):
        self.write("notes.txt", b"iVBORw0KGgoAAAANSUhEUg" + b"A" * 40)
        self.assertEqual([], list(image_files(self.root)))


if __name__ == "__main__":
    unittest.main()

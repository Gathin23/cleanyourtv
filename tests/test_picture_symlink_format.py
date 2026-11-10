"""A staged picture keeps the requested format even when its symlink target has another suffix."""
import contextlib
import io
from pathlib import Path
import stat
import sys
import tempfile
import types
import unittest
from unittest import mock

TOOLS = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(TOOLS))
import blocky_wallpapers  # noqa: E402
import picture_file  # noqa: E402

try:
    from PIL import Image
except ImportError:
    Image = None


class RecordingEncoder:
    def __init__(self):
        self.paths = []

    def save(self, path, **options):
        self.paths.append(Path(path))
        Path(path).write_bytes(b"encoded picture")


class PictureSymlinkFormatTest(unittest.TestCase):
    def write(self, writer, image, path):
        if writer == "card":
            picture_file.save(image, str(path), optimize=True)
            return
        # Exercise the real scene publication with a small image rather than drawing eleven scenes.
        with mock.patch.object(blocky_wallpapers, "Canvas", return_value=types.SimpleNamespace(img=image)), \
                mock.patch.object(blocky_wallpapers, "SCENES", [(path.stem, lambda cv: None)]), \
                mock.patch.dict(sys.modules, {"PIL": sys.modules.get("PIL", types.ModuleType("PIL"))}), \
                contextlib.redirect_stdout(io.StringIO()):
            blocky_wallpapers.write(str(path.parent))

    def link(self, path, target):
        try:
            path.symlink_to(target)
        except (OSError, NotImplementedError) as error:
            self.skipTest(f"symlinks are not available: {error}")

    def test_the_encoder_sees_the_requested_suffix_not_the_targets(self):
        for writer in ("card", "blocky"):
            for name in ("stored-picture", "stored.jpg", "stored.bin"):
                with self.subTest(writer=writer, target=name), tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    destination = root / "storage"
                    destination.mkdir()
                    target, path = destination / name, root / "picture.png"
                    target.write_bytes(b"previous picture")
                    self.link(path, target)
                    image = RecordingEncoder()
                    self.write(writer, image, path)
                    self.assertEqual(len(image.paths), 1)
                    self.assertEqual(image.paths[0].suffix, ".png")
                    self.assertTrue(image.paths[0].parent.parent.samefile(destination))
                    self.assertTrue(path.is_symlink())
                    self.assertEqual(target.read_bytes(), b"encoded picture")
                    self.assertEqual(list(destination.iterdir()), [target])

    @unittest.skipIf(Image is None, "Pillow is not installed")
    def test_real_pngs_keep_their_format_through_existing_and_dangling_links(self):
        for writer in ("card", "blocky"):
            for name in ("stored-picture", "stored.jpg", "stored.bin"):
                for existing in (False, True):
                    with self.subTest(writer=writer, target=name, existing=existing), \
                            tempfile.TemporaryDirectory() as tmp:
                        root = Path(tmp)
                        target, path = root / name, root / "picture.png"
                        if existing:
                            target.write_bytes(b"previous picture")
                            target.chmod(0o640)
                        self.link(path, target)
                        with Image.new("RGB", (8, 4), (17, 91, 203)) as image:
                            self.write(writer, image, path)
                        self.assertTrue(path.is_symlink())
                        with Image.open(target) as saved:
                            self.assertEqual(saved.format, "PNG")
                            self.assertEqual(saved.size, (8, 4))
                            self.assertEqual(saved.getpixel((0, 0)), (17, 91, 203))
                        if existing and sys.platform != "win32":
                            self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o640)
                        self.assertEqual(sorted(p.name for p in root.iterdir()), sorted([name, path.name]))

    @unittest.skipIf(Image is None, "Pillow is not installed")
    def test_a_regular_png_still_uses_png(self):
        for writer in ("card", "blocky"):
            with self.subTest(writer=writer), tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "picture.png"
                with Image.new("RGB", (8, 4), (17, 91, 203)) as image:
                    self.write(writer, image, path)
                with Image.open(path) as saved:
                    self.assertEqual(saved.format, "PNG")

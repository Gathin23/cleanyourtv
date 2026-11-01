"""themes/blocky and the script that draws its wallpapers, tools/blocky_wallpapers.py.

The theme's record of its pictures is wallpapers/captions.txt (`<file><TAB><name>`, themes/README.md); the folder,
the captions, the script's scene list and config.json's wallpaper_moods must all name the same 11 files, or a
scene goes without its name, or never shows because no time of day lists it.

The script takes exactly one folder, like tools/scenes_wallpapers.py: -h/--help prints its note and writes
nothing, and a leading dash, an empty word or a path that is a file is refused with exit 2 before anything is
written. Those checks run without Pillow. With Pillow (tools/requirements-perf.txt) the script is run into a
folder of its own, and every picture must be a 1920x1080 PNG under themes/README.md's 300 KB whose top six block
rows, where the clock and the pills sit, are plain sky in every outdoor scene.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "blocky_wallpapers.py"
THEME = ROOT / "themes" / "blocky"
INDOOR = {"10-cave.png", "11-lava-cavern.png"}   # rock from edge to edge: no sky to keep plain

try:
    from PIL import Image
except ImportError:
    Image = None


def run(*args, cwd):
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], cwd=cwd, capture_output=True, text=True,
                          timeout=300)


def caption_files():
    lines = (THEME / "wallpapers" / "captions.txt").read_text(encoding="utf-8").splitlines()
    return [line.split("\t")[0].strip() for line in lines if line.strip()]


class BlockyTheme(unittest.TestCase):
    def test_folder_captions_and_script_name_the_same_pictures(self):
        pictures = sorted(p.name for p in (THEME / "wallpapers").glob("*.png"))
        self.assertEqual(len(pictures), 11)
        self.assertEqual(caption_files(), pictures)
        source = SCRIPT.read_text(encoding="utf-8")
        for name in pictures:
            self.assertIn(f'"{name[:-4]}"', source, f"{name} is not drawn by {SCRIPT.name}")

    def test_every_picture_has_a_time_of_day_and_every_mood_names_a_picture(self):
        moods = json.loads((THEME / "config.json").read_text(encoding="utf-8"))["wallpaper_moods"]
        listed = {name for group in moods.values() for name in group}
        self.assertEqual(listed, set(caption_files()))


class BlockyScript(unittest.TestCase):
    def test_help_writes_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            for flag in ("-h", "--help"):
                result = run(flag, cwd=d)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("blocky_wallpapers.py <folder>", result.stdout)
            self.assertEqual(list(Path(d).iterdir()), [])

    def test_refuses_what_is_not_one_folder(self):
        with tempfile.TemporaryDirectory() as d:
            Path(d, "file.txt").write_text("x")
            for args in ([], ["--out", "x"], ["-x"], [""], ["file.txt"], ["a", "b"]):
                result = run(*args, cwd=d)
                self.assertEqual(result.returncode, 2, (args, result.stdout, result.stderr))
            self.assertEqual(sorted(p.name for p in Path(d).iterdir()), ["file.txt"])

    @unittest.skipIf(Image is None, "Pillow is not installed (tools/requirements-perf.txt)")
    def test_draws_eleven_screen_sized_pictures_with_a_plain_header(self):
        with tempfile.TemporaryDirectory() as d:
            result = run("out", cwd=d)
            self.assertEqual(result.returncode, 0, result.stderr)
            pictures = sorted(Path(d, "out").glob("*.png"))
            self.assertEqual([p.name for p in pictures], caption_files())
            for path in pictures:
                self.assertLess(path.stat().st_size, 300 * 1024, path.name)
                with Image.open(path) as im:
                    self.assertEqual(im.size, (1920, 1080), path.name)
                    if path.name in INDOOR:
                        continue
                    rgb = im.convert("RGB")
                    for row in range(6):
                        band = rgb.crop((0, row * 40, 1920, row * 40 + 40))
                        self.assertEqual(len(band.getcolors(1 << 16) or [None, None]), 1,
                                         f"{path.name}: header row {row} is not plain sky")


if __name__ == "__main__":
    unittest.main()

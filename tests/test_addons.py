"""addons/ stays separate from the launcher: the core build never includes it, launcher/ never refers to it,
and every add-on folder follows the rules in addons/README.md."""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_themes import MAX_SCREENSHOT, jpeg_size  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ADDONS = ROOT / "addons"
LAUNCHER = ROOT / "launcher"
TEXT = {".gradle", ".kts", ".java", ".kt", ".xml", ".properties", ".sh", ".ps1", ".pro", ".md"}


# Add-ons merged before screenshots were required. This list only shrinks: once one gets a
# screenshot.jpg, take it off here.
SCREENSHOT_WANTED = set()


def addon_dirs():
    return sorted(p for p in ADDONS.iterdir() if p.is_dir()) if ADDONS.is_dir() else []


class AddonIsolationTest(unittest.TestCase):
    def test_core_build_does_not_include_addons(self):
        for name in ("settings.gradle", "settings.gradle.kts"):
            settings = LAUNCHER / name
            if settings.is_file():
                text = settings.read_text(encoding="utf-8")
                self.assertNotIn("addons", text, f"launcher/{name} must not include add-ons")
                self.assertEqual(re.findall(r"include\s*\(?\s*['\"]([^'\"]+)", text), [":app"],
                                 "the core build is the launcher app alone")

    def test_launcher_never_references_addons(self):
        for path in LAUNCHER.rglob("*"):
            if not path.is_file() or path.suffix not in TEXT or "build" in path.relative_to(LAUNCHER).parts:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            self.assertNotRegex(text, r"(\.\./)+addons/|\baddons/", f"{path.relative_to(ROOT)} refers to addons/")

    def test_no_prebuilt_apks(self):
        # Gradle's ignored build outputs are expected after compiling or testing an add-on.
        apks = [str(p.relative_to(ROOT)) for p in ADDONS.rglob("*.apk")
                if "build" not in p.relative_to(ADDONS).parts] if ADDONS.is_dir() else []
        self.assertEqual(apks, [], "no prebuilt APKs in addons/")


class AddonFolderTest(unittest.TestCase):
    def test_every_addon_follows_the_rules(self):
        index = (ADDONS / "README.md").read_text(encoding="utf-8") if ADDONS.is_dir() else ""
        for folder in addon_dirs():
            with self.subTest(addon=folder.name):
                self.assertTrue(any((folder / n).is_file() for n in ("settings.gradle", "settings.gradle.kts")),
                                "an add-on is its own Gradle project")
                readme = folder / "README.md"
                self.assertTrue(readme.is_file(), "an add-on needs its own README")
                self.assertRegex(readme.read_text(encoding="utf-8"), r"(?im)^#+\s*permissions",
                                 "the README lists its permissions under a Permissions heading")
                self.assertIn(f"]({folder.name}/)", index, "addons/README.md needs a row linking this add-on")

    def test_every_addon_has_a_screenshot(self):
        index = (ADDONS / "README.md").read_text(encoding="utf-8") if ADDONS.is_dir() else ""
        for folder in addon_dirs():
            with self.subTest(addon=folder.name):
                shot = folder / "screenshot.jpg"
                if folder.name in SCREENSHOT_WANTED:
                    self.assertFalse(shot.is_file(), "it has a screenshot now: take it off SCREENSHOT_WANTED")
                    continue
                self.assertTrue(shot.is_file(),
                                "no screenshot.jpg: add one of the add-on on a real TV, see addons/README.md")
                data = shot.read_bytes()
                self.assertLessEqual(len(data), MAX_SCREENSHOT, "screenshot.jpg is over 300 KB")
                size = jpeg_size(data)
                self.assertIsNotNone(size, "screenshot.jpg is not a JPEG")
                width, height = size
                self.assertEqual(width * 9, height * 16, f"screenshot.jpg is {width}x{height}, not 16:9")
                self.assertLessEqual(width, 1920, "screenshot.jpg is wider than 1920; 1280x720 is enough")
                row = next((l for l in index.split("\n") if f"]({folder.name}/)" in l), "")
                self.assertIn(f"{folder.name}/screenshot.jpg", row, "the addons/README.md row does not show the screenshot")

    def test_wanted_list_names_real_addons(self):
        names = {p.name for p in addon_dirs()}
        self.assertEqual(SCREENSHOT_WANTED - names, set(), "SCREENSHOT_WANTED names a folder that is gone")


if __name__ == "__main__":
    unittest.main()

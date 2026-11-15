"""clock_glow false keeps the clock screensaver on plain black, with or without clock_wallpaper."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCENE = (ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "ClockScene.java"
         ).read_text(encoding="utf-8")


class ClockGlowTest(unittest.TestCase):
    def test_clock_glow_is_applied_before_the_wallpaper_branch(self):
        use = SCENE.index("void useHomeWallpaper(Context ctx)")
        glow = SCENE.index("glowOff = !cfg.clockGlow;", use)
        self.assertIn("if (glowOff) ambient.setBackground(null);", SCENE[glow:glow + 120])
        self.assertLess(glow, SCENE.index("if (!cfg.clockWallpaper) return;", use))

    def test_the_glow_colour_cycle_stops_on_its_own_flag(self):
        shift = SCENE.index("private void shiftGlow()")
        self.assertIn("if (wallpaperShown || glowOff) return;", SCENE[shift:shift + 120])

    def test_glow_off_does_not_reuse_wallpaper_shown(self):
        # start() skips loadWallpaper() while wallpaperShown is set.
        use = SCENE.index("void useHomeWallpaper(Context ctx)")
        self.assertNotIn("wallpaperShown = true", SCENE[use:SCENE.index("if (!cfg.clockWallpaper) return;", use)])


if __name__ == "__main__":
    unittest.main()

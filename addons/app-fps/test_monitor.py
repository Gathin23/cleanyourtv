import unittest
import subprocess
import sys
from pathlib import Path
from monitor import counters, Rate, is_foreground, layer_available

class CounterTest(unittest.TestCase):
    def test_ignores_global_and_display_rate(self):
        self.assertEqual(counters('totalFrames = 900\ndisplayRefreshRate = 60 fps\nlayerName = test\ntotalFrames = 20\n'), {'test':20})
    def test_ambiguous_layers_are_unavailable(self):
        self.assertEqual(counters('layerName = test\ntotalFrames = 20\nlayerName = test\ntotalFrames = 30\n')['test'],None)
    def test_unreadable_and_missing(self):
        self.assertEqual(counters('Permission denied'),{})
        self.assertEqual(counters('layerName = test\ntotalFrames = bad'),{})
    def test_actual_elapsed_time(self):
        r=Rate();self.assertIsNone(r.sample(100,1));self.assertEqual(r.sample(160,3),30)
    def test_reset_and_missing_reset_baseline(self):
        r=Rate();r.sample(100,1);self.assertIsNone(r.sample(2,2));self.assertEqual(r.sample(32,3),30)
        self.assertIsNone(r.sample(None,4));self.assertIsNone(r.sample(100,5))
    def test_foreground_requires_exact_package(self):
        self.assertTrue(is_foreground("mResumedActivity: ActivityRecord{x u0 example.app/.Main t1}", "example.app"))
        self.assertFalse(is_foreground("mResumedActivity: ActivityRecord{x u0 example.app.other/.Main t1}", "example.app"))
        self.assertFalse(is_foreground("Hist #1 example.app/.Main", "example.app"))
    def test_launcher_cannot_be_a_measurement_target(self):
        result = subprocess.run([sys.executable, str(Path(__file__).with_name('monitor.py')),
                                 '--package', 'com.gathin.tvhome', '--layer', 'com.gathin.tvhome/.Main'],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn('Home cannot be an FPS target', result.stderr)
    def test_removed_layer_does_not_use_old_counters(self):
        self.assertFalse(layer_available('new-layer', 'old-layer'))
        self.assertFalse(layer_available('some-other-layer', 'layer'))
        self.assertTrue(layer_available('abc example.app/.Main#0', 'example.app/.Main#0'))
        self.assertTrue(layer_available('SurfaceView[example.app/.Main]#0', 'SurfaceView[example.app/.Main]#0'))
    def test_idle_is_zero(self):
        r=Rate();r.sample(100,1);self.assertEqual(r.sample(100,2),0)
    def test_bad_clock_and_impossible_rate(self):
        r=Rate();r.sample(1,1);self.assertIsNone(r.sample(2,1));self.assertIsNone(r.sample(100000,2))

if __name__=='__main__':unittest.main()

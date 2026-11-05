"""Read PSS from the App Summary, whose label changed between Android 8 and 14."""
from types import SimpleNamespace
import unittest

from tools import bench


class PssSummaryTest(unittest.TestCase):
    def read(self, output):
        return bench.pss_of(SimpleNamespace(shell=lambda command: output), "com.gathin.tvhome")

    def test_android_8_summary(self):
        # ActivityThread.java, android-8.0.0_r1, lines 2547-2554.
        for swap in ("TOTAL SWAP PSS:", "TOTAL SWAP (KB):"):
            with self.subTest(swap=swap):
                self.assertEqual(self.read(f"    TOTAL:    65536    {swap}    2048\n"), 64)

    def test_android_14_summary_uses_pss(self):
        # https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/app/ActivityThread.java#L3261-L3270
        for swap in ("TOTAL SWAP PSS:", "TOTAL SWAP (KB):"):
            with self.subTest(swap=swap):
                out = ("    TOTAL    1024    2048    4096\n\n App Summary\n"
                       f"    TOTAL PSS:    65536    TOTAL RSS:    98304    {swap}    2048\n")
                self.assertEqual(self.read(out), 64)

    def test_zero_pss_is_a_value(self):
        self.assertEqual(self.read("    TOTAL PSS:    0    TOTAL RSS:    8192\n"), 0)

    def test_no_pss_summary_is_unknown(self):
        for output in ("", "No process found for: com.gathin.tvhome",
                       "TOTAL RSS: 98304", "TOTAL SWAP PSS: 2048",
                       "TOTAL PSS: unavailable", "TOTAL 65536 98304"):
            with self.subTest(output=output):
                self.assertIsNone(self.read(output))


if __name__ == "__main__":
    unittest.main()

"""The median in result.json is the measured median, not one cut down to a whole millisecond."""
import unittest

from tools import bench


class StatsMedianTest(unittest.TestCase):
    def test_median_keeps_its_fraction(self):
        # response_ms is rounded to 0.1 ms; p95 and max keep that, so p50 has to as well.
        self.assertEqual(bench.stats([16.9])["p50"], 16.9)
        self.assertEqual(bench.stats([16.2, 16.9, 40.0])["p50"], 16.9)

    def test_median_is_never_above_max(self):
        result = bench.stats([16.9])
        self.assertLessEqual(result["max"] - result["p50"], 0)

    def test_even_count_of_whole_milliseconds(self):
        # Cold starts are whole ms; the middle of two is half way, not the lower one floored.
        self.assertEqual(bench.stats([600, 601])["p50"], 600.5)

    def test_no_values(self):
        self.assertIsNone(bench.stats([])["p50"])


if __name__ == "__main__":
    unittest.main()

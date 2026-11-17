"""CPU percentages from synthetic Linux /proc/stat counter samples, without a TV."""
from unittest import TestCase, mock

from tools import bench


class IdleCpuTest(TestCase):
    def measure(self, before, after):
        tv = mock.Mock()
        tv.shell.side_effect = [before, after]
        with mock.patch.object(bench, "launcher_pid", return_value=None), \
                mock.patch.object(bench.time, "sleep"):
            result = bench.idle_cpu(tv, "com.gathin.tvhome", 5)
        self.assertEqual(tv.shell.call_count, 2)
        return result

    def test_guest_counters_are_not_added_twice(self):
        # Linux accounts guest in user, and guest_nice in nice, before exposing both.
        for user, nice, guest, guest_nice in ((100, 0, 80, 0), (0, 100, 0, 80),
                                               (60, 40, 50, 30)):
            with self.subTest(guest=guest, guest_nice=guest_nice):
                before = "cpu  100 100 100 1000 100 10 10 10 50 50\n"
                after = (f"cpu  {100 + user} {100 + nice} 100 1900 100 10 10 10 "
                         f"{50 + guest} {50 + guest_nice}\n")
                device, launcher = self.measure(before, after)
                self.assertAlmostEqual(device, 10.0)
                self.assertIsNone(launcher)

    def test_all_non_guest_fields_contribute_to_total(self):
        # 200 busy ticks, 700 idle and 100 iowait ticks out of 1000 total.
        device, launcher = self.measure("cpu  0 0 0 0 0 0 0 0 0 0\n",
                                        "cpu  50 20 30 700 100 40 50 10 0 0\n")
        self.assertAlmostEqual(device, 20.0)
        self.assertIsNone(launcher)

    def test_older_samples_without_guest_columns(self):
        device, _ = self.measure("cpu  0 0 0 0 0\n", "cpu  100 0 0 800 100\n")
        self.assertAlmostEqual(device, 10.0)

    def test_unchanged_counters_are_zero(self):
        sample = "cpu  100 20 30 700 100 40 50 10 80 10\n"
        self.assertEqual(self.measure(sample, sample), (0.0, None))

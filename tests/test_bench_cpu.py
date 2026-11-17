"""CPU percentages from synthetic Linux /proc/stat counter samples, without a TV."""
from unittest import TestCase, mock

from tools import bench


class IdleCpuTest(TestCase):
    def launcher_measure(self, before_uptime, after_uptime):
        def sample(ticks, uptime):
            # /proc/PID/stat fields 3..15: state, ten fields, utime and stime.
            process = "42 (com.gathin.tvho) " + " ".join(["S"] + ["0"] * 10 + [str(ticks), "0"])
            return f"cpu  100 0 0 1000 0\n{process}\n{uptime}\n"

        tv = mock.Mock()
        snapshots = iter(((100, before_uptime), (200, after_uptime)))

        def read(command, **kwargs):
            ticks, uptime = next(snapshots)
            return sample(ticks, uptime if "/proc/uptime" in command else "")

        tv.shell.side_effect = read
        with mock.patch.object(bench, "launcher_pid", return_value="42"), \
                mock.patch.object(bench.time, "sleep") as sleep:
            result = bench.idle_cpu(tv, "com.gathin.tvhome", 5)
        sleep.assert_called_once_with(5)
        self.assertEqual(tv.shell.call_count, 2)
        return result[1]

    def test_launcher_cpu_uses_device_sample_interval(self):
        # Five seconds requested, ten between device snapshots: PID lookup and transport also take time.
        self.assertAlmostEqual(self.launcher_measure("100.00 300.00", "110.00 330.00"), 10.0)

    def test_launcher_cpu_with_the_requested_interval(self):
        self.assertAlmostEqual(self.launcher_measure("100.00 300.00", "105.00 315.00"), 20.0)

    def test_launcher_cpu_without_a_positive_device_interval_is_unknown(self):
        for before, after in (("", ""), ("100.00 300.00", ""), ("", "110.00 330.00"),
                              ("100.00 300.00", "100.00 300.00"),
                              ("100.00 300.00", "99.00 297.00")):
            with self.subTest(before=before, after=after):
                self.assertIsNone(self.launcher_measure(before, after))

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

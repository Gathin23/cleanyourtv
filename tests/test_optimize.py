"""tools/optimize.py reads what the TV prints and picks the apps to compile without asking it anything.

The parsers are checked against output copied from a Xiaomi MiTV-MOOQ0 (Android TV 10), and the choices
against the rules in the tool's docstring: the four favourites first when installed, launchers never,
speed-profile only, and nothing compiled again unless --refresh.
Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools import optimize  # noqa: E402

USAGE = """
  In-memory daily stats
      package=com.netflix.ninja totalTimeUsed="12:30" lastTimeUsed="2026-11-01 20:10:00" totalTimeVisible="12:30" appLaunchCount=2
  In-memory yearly stats
      package=com.netflix.ninja totalTimeUsed="3:02:05" lastTimeUsed="2026-11-01 20:10:00" totalTimeVisible="3:02:05" appLaunchCount=41
      package=tv.airtel.xstream.tvapp totalTimeUsed="45:00" lastTimeUsed="2026-10-30 21:00:00" appLaunchCount=7
      package=com.mxtech.videoplayer.ad totalTimeUsed="05:00" lastTimeUsed="2026-10-12 21:00:00" appLaunchCount=2
      package=com.spotify.tv.android totalTimeUsed="00:00" lastTimeUsed="1970-01-01 05:30:00" appLaunchCount=0
      package=com.gathin.tvhome totalTimeUsed="9:00:00" lastTimeUsed="2026-11-01 20:10:00" appLaunchCount=300
"""

DEXOPT = """
Packages:
  Package [com.google.android.youtube.tv] (4b1c2d):
    versionCode=4.40.303 minSdk=26 targetSdk=33
Dexopt state:
  [com.google.android.youtube.tv]
    path: /data/app/com.google.android.youtube.tv-1/base.apk
      arm: [status=verify] [reason=install]
"""


class ParseTest(unittest.TestCase):
    def test_seconds_reads_both_forms(self):
        self.assertEqual(optimize.seconds("00:00"), 0)
        self.assertEqual(optimize.seconds("12:30"), 750)
        self.assertEqual(optimize.seconds("3:02:05"), 3 * 3600 + 125)

    def test_usage_keeps_the_longest_interval(self):
        usage = optimize.parse_usage(USAGE)
        self.assertEqual(usage["com.netflix.ninja"], (3 * 3600 + 125, 41))
        self.assertEqual(usage["com.spotify.tv.android"], (0, 0))

    def test_dexopt_status_reads_the_dexopt_section(self):
        self.assertEqual(optimize.dexopt_status(DEXOPT), "verify")
        self.assertEqual(optimize.dexopt_status("nothing here"), "unknown")

    def test_packages_from_pm_and_brief_queries(self):
        text = "package:com.netflix.ninja\ncom.netflix.ninja/.MainActivity\nin.startv.hotstar/com.hotstar.MainActivity\n" \
               "3 activities found:\n; rm -rf /\n"
        self.assertEqual(optimize.packages(text), ["com.netflix.ninja", "in.startv.hotstar"])

    def test_mem_available(self):
        self.assertEqual(optimize.mem_available_mb("MemTotal: 1841840 kB\nMemAvailable:   756072 kB\n"), 738)
        self.assertEqual(optimize.mem_available_mb(""), -1)

    def test_boot_times(self):
        events = ("11-01 20:15:08.687  3368  3396 I boot_progress_enable_screen: 22982\n"
                  "11-01 20:15:13.837  2735  4093 I sf_stop_bootanim: 28133\n")
        self.assertEqual(optimize.boot_times(events), [("screen on", 22.982), ("boot animation ends", 28.133)])
        self.assertEqual(optimize.boot_times(""), [])


class ChoiceTest(unittest.TestCase):
    def test_favourites_first_then_most_used_without_launchers(self):
        usage = optimize.parse_usage(USAGE)
        launchable = ["com.gathin.tvhome", "tv.airtel.xstream.tvapp", "com.netflix.ninja",
                      "com.mxtech.videoplayer.ad", "com.spotify.tv.android", "in.startv.hotstar"]
        apps = optimize.pick_apps(launchable, usage, {"com.gathin.tvhome"}, top=5)
        self.assertEqual(apps, ["com.netflix.ninja", "in.startv.hotstar",
                                "tv.airtel.xstream.tvapp", "com.mxtech.videoplayer.ad"])

    def test_top_limits_only_the_usage_part(self):
        usage = optimize.parse_usage(USAGE)
        launchable = ["tv.airtel.xstream.tvapp", "com.netflix.ninja", "com.mxtech.videoplayer.ad"]
        self.assertEqual(optimize.pick_apps(launchable, usage, set(), top=1),
                         ["com.netflix.ninja", "tv.airtel.xstream.tvapp"])
        self.assertEqual(optimize.pick_apps(launchable, usage, set(), top=0), ["com.netflix.ninja"])

    def test_compiled_apps_are_left_alone_unless_refresh(self):
        for status in ("speed-profile", "speed", "everything"):
            self.assertFalse(optimize.needs_compile(status, refresh=False))
            self.assertTrue(optimize.needs_compile(status, refresh=True))
        for status in ("verify", "quicken", "run-from-apk", "extract", "unknown"):
            self.assertTrue(optimize.needs_compile(status, refresh=False))

    def test_never_compiles_with_speed(self):
        source = Path(optimize.__file__).read_text(encoding="utf-8")
        self.assertIn("compile -m speed-profile -f", source)
        self.assertNotRegex(source, r"compile -m speed -f|compile -m everything")

    def test_boot_wakers_are_installed_apps_not_kept_fast(self):
        receivers = "com.netflix.ninja/.BootReceiver\ntv.airtel.xstream.tvapp/.Boot\nandroid/.Boot\n"
        self.assertEqual(optimize.boot_wakers(receivers, {"com.netflix.ninja", "tv.airtel.xstream.tvapp"},
                                              {"com.netflix.ninja"}),
                         ["tv.airtel.xstream.tvapp"])

    def test_written_list_is_all_commented_and_only_unused_apps(self):
        usage = optimize.parse_usage(USAGE)
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "boot.txt")
            optimize.write_list(path, ["com.spotify.tv.android", "tv.airtel.xstream.tvapp"], usage)
            lines = Path(path).read_text(encoding="utf-8").splitlines()
        self.assertIn("# com.spotify.tv.android", lines)
        self.assertNotIn("# tv.airtel.xstream.tvapp", lines)
        self.assertTrue(all(not line or line.startswith("#") for line in lines))


if __name__ == "__main__":
    unittest.main()

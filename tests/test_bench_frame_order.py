"""Merge per-window framestats by completion time; all frames and TV answers are synthetic."""
import itertools
import unittest
from unittest import mock

from tests.test_bench_framestats import HEADERS, frame, profile
from tools import bench


class FrameOrderTest(unittest.TestCase):
    def measure(self, blocks, count):
        answers = {
            "dumpsys gfxinfo com.gathin.tvhome reset": "Uptime: 1000 Realtime: 1000\n",
            "input keyevent 22": "",
            "dumpsys gfxinfo com.gathin.tvhome framestats":
                f"Uptime: 2000 Realtime: 2000\nTotal frames rendered: {count}\nJanky frames: 0\n"
                + "".join(blocks),
            "logcat -d -b main -t 2000 | grep injectKeyEvent":
                "injectKeyEvent action=ACTION_DOWN KEYCODE_DPAD_RIGHT eventTime=1000",
        }
        tv = mock.Mock()
        tv.shell.side_effect = lambda command, *, retry=False: answers[command]
        with mock.patch.object(bench.time, "sleep"):
            return bench.measure_key(tv, 22, package="com.gathin.tvhome")

    def test_a_late_window_cannot_hide_the_first_response(self):
        # Android dumps renderers by ViewRoot order, not by their frames' timestamps:
        # https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/view/WindowManagerGlobal.java#L559-L578
        for version, header in HEADERS.items():
            blocks = (profile(header, frame(header, 200_000_000)), profile(header, frame(header)))
            for order in itertools.permutations(blocks):
                with self.subTest(version=version, order=order):
                    self.assertEqual(self.measure(order, 2), (20.0, 20.0, 2, 0))

    def test_interleaved_windows_do_not_create_a_false_movement_gap(self):
        for version, header in HEADERS.items():
            # One window has the first/last movement frames; the other fills the gap.
            # The 220 ms completion is a later movement and must still be left out.
            blocks = (profile(header, *(frame(header, offset) for offset in
                                       (0, 60_000_000, 200_000_000))),
                      profile(header, frame(header, 20_000_000), frame(header, 40_000_000)))
            for order in itertools.permutations(blocks):
                with self.subTest(version=version, order=order):
                    self.assertEqual(self.measure(order, 5), (20.0, 80.0, 5, 0))

    def test_a_late_completion_cannot_move_the_vsync_cursor_backwards(self):
        for version, header in HEADERS.items():
            # An older frame can finish after the first response in another window.
            older = frame(header, -100_000_000).split(",")
            older[header.split(",").index("FrameCompleted")] = "1030000000"
            blocks = (profile(header, frame(header), ",".join(older)),
                      profile(header, frame(header, 20_000_000), frame(header, 40_000_000)))
            for order in itertools.permutations(blocks):
                with self.subTest(version=version, order=order):
                    self.assertEqual(self.measure(order, 4), (20.0, 60.0, 4, 0))


if __name__ == "__main__":
    unittest.main()

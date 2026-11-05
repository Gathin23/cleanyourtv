"""Parse AOSP framestats headers with synthetic timestamps; no TV is needed."""
import unittest
from unittest import mock

from tools import bench


# Header order from FrameInfoNames, not a dump from a real TV:
# https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/libs/hwui/FrameInfo.cpp#L23
# https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.0.0_r1/libs/hwui/FrameInfo.cpp#L23
# https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/libs/hwui/FrameInfo.cpp#L23
# https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/libs/hwui/FrameInfo.cpp#L25
# JankTracker prints the header and values in that order, with trailing commas:
# https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/libs/hwui/JankTracker.cpp#L301
HEADERS = {
    "android-8": (
        "Flags,IntendedVsync,Vsync,OldestInputEvent,NewestInputEvent,HandleInputStart,"
        "AnimationStart,PerformTraversalsStart,DrawStart,SyncQueued,SyncStart,"
        "IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,QueueBufferDuration"),
    "android-12": (
        "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,"
        "PerformTraversalsStart,DrawStart,FrameDeadline,FrameInterval,FrameStartTime,SyncQueued,"
        "SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,"
        "QueueBufferDuration,GpuCompleted,SwapBuffersCompleted,DisplayPresentTime"),
    "android-14": (
        "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,"
        "PerformTraversalsStart,DrawStart,FrameDeadline,FrameInterval,FrameStartTime,SyncQueued,"
        "SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,"
        "QueueBufferDuration,GpuCompleted,SwapBuffersCompleted,DisplayPresentTime,CommandSubmissionCompleted"),
    "android-16": (
        "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,"
        "PerformTraversalsStart,DrawStart,FrameDeadline,FrameStartTime,FrameInterval,WorkloadTarget,"
        "SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,"
        "QueueBufferDuration,GpuCompleted,SwapBuffersCompleted,DisplayPresentTime,CommandSubmissionCompleted"),
}


def frame(header, offset=0, vsync_id=100):
    start = 1_000_000_000 + offset
    values = {"Flags": 0, "FrameTimelineVsyncId": vsync_id, "IntendedVsync": start - 1_000_000,
              "SyncQueued": start + 4_000_000, "SyncStart": start + 5_000_000,
              "FrameCompleted": start + 20_000_000}
    return ",".join(str(values.get(name, 0)) for name in header.split(",")) + ","


def profile(header, *rows):
    return "---PROFILEDATA---\n" + header + ",\n" + "\n".join(rows) + "\n---PROFILEDATA---\n"


class FrameStatsTest(unittest.TestCase):
    def test_aosp_formats_read_the_named_timestamps(self):
        for version, header in HEADERS.items():
            with self.subTest(version=version):
                self.assertEqual(bench.frame_rows(profile(header, frame(header))),
                                 [(0, 999_000_000, 1_020_000_000)])

    def test_latency_and_movement_gap_use_timestamps_not_vsync_ids(self):
        for version, header in HEADERS.items():
            with self.subTest(version=version):
                # The second frame is 200 ms later, outside the first movement's 50 ms gap.
                stats = ("Uptime: 2000 Realtime: 2000\nTotal frames rendered: 2\nJanky frames: 0\n"
                         + profile(header, frame(header), frame(header, 200_000_000, 112)))
                answers = {
                    "dumpsys gfxinfo com.gathin.tvhome reset": "Uptime: 1000 Realtime: 1000\n",
                    "input keyevent 22": "",
                    "dumpsys gfxinfo com.gathin.tvhome framestats": stats,
                    "logcat -d -b main -t 2000 | grep injectKeyEvent":
                        "injectKeyEvent action=ACTION_DOWN KEYCODE_DPAD_RIGHT eventTime=1000",
                }
                tv = mock.Mock()
                tv.home_component.return_value = "com.gathin.tvhome/.MainActivity"
                tv.package_of.return_value = "com.gathin.tvhome"
                tv.shell.side_effect = answers.__getitem__
                with mock.patch.object(bench.time, "sleep"):
                    self.assertEqual(bench.measure_key(tv, 22), (20.0, 20.0, 2, 0))

    def test_each_block_uses_its_own_header(self):
        old, new = HEADERS["android-8"], HEADERS["android-16"]
        stats = profile(old, frame(old)) + profile(new, frame(new, 200_000_000))
        self.assertEqual(bench.frame_rows(stats),
                         [(0, 999_000_000, 1_020_000_000), (0, 1_199_000_000, 1_220_000_000)])

    def test_missing_or_unknown_headers_do_not_reuse_previous_columns(self):
        header = HEADERS["android-8"]
        for bad_header in ("", "Vendor data", header.replace("FrameCompleted", "Unknown"),
                           header.replace("Flags", "Unknown")):
            with self.subTest(header=bad_header):
                stats = profile(header, frame(header)) + profile(bad_header, frame(header))
                self.assertEqual(bench.frame_rows(stats), [(0, 999_000_000, 1_020_000_000)])

    def test_malformed_and_short_rows_are_skipped(self):
        header = HEADERS["android-14"]
        valid = frame(header)
        malformed = valid.replace("1020000000", "unknown")
        short = ",".join(valid.split(",")[:14]) + ","
        self.assertEqual(bench.frame_rows(profile(header, malformed, short, valid)),
                         [(0, 999_000_000, 1_020_000_000)])

    def test_rows_outside_profiledata_are_ignored(self):
        header = HEADERS["android-8"]
        row = frame(header)
        stats = row + "\n" + profile(header, row) + row + "\n"
        self.assertEqual(bench.frame_rows(stats), [(0, 999_000_000, 1_020_000_000)])


if __name__ == "__main__":
    unittest.main()

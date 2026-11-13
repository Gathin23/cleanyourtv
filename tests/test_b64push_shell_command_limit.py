"""tools/b64push.py: a chunk is a shell command, so it is sized by the command its adbd will take.

adb_shell's `max_chunk_size` is the payload it puts on the wire for a filesync chunk: 65536 bytes on
a modern TV, and not a statement about how long a shell command its adbd will run. b64push.py read it
as one, so a chunk became a ~65 KB `echo '<base64>' >> '<staging>'` command. A Philips 55PUS7503/12
on Android 8.0 (#3871) accepted single commands of 32,800 and 49,200 bytes, timed out 3 of 3 at
57,000, and never got past chunk 0: the transfer died on its first command as a TcpTimeoutException
after the 60 s read, leaving an empty `.b64.part`. The same file that failed four runs out of four
went over in chunks of 24 KB raw, which is what this sizing now sends.

The fake below is that TV: it runs the command through /bin/sh, so the upload really assembles a
file, and raises the timeout its adbd gives instead of a command over `command_ceiling` bytes.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("b64push_command_budget", ROOT / "tools" / "b64push.py")
push = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(push)

# What adb_shell negotiates on a modern TV, and what the reported set answered: commands up to
# 49,200 bytes went through, 57,000 did not.
NEGOTIATED_PAYLOAD = 65536
COMMAND_CEILING = 50000


class CommandLimitedDevice:
    """A TV whose adbd stops answering a shell command over `command_ceiling` bytes.

    `max_shell_command_size` is left out unless a test states it, the way adb_shell's device object
    carries no such attribute and the native adapter does.
    """

    command_ceiling = COMMAND_CEILING

    def __init__(self, max_chunk_size=NEGOTIATED_PAYLOAD, max_shell_command_size=None):
        self.max_chunk_size = max_chunk_size
        if max_shell_command_size is not None:
            self.max_shell_command_size = max_shell_command_size
        self.commands = []
        self.closed = False

    def shell(self, command, read_timeout_s=None):
        self.commands.append(command)
        length = len(command.encode("utf-8"))
        if length > self.command_ceiling:
            # adb_shell's own words for a read that ran out: the report quotes this exception with
            # the 60.0 seconds the chunk sends with.
            raise TimeoutError(f"Reading from <tv ip>:5555 timed out ({read_timeout_s} seconds); "
                               f"the command was {length} bytes")
        return subprocess.run(["/bin/sh", "-c", command], capture_output=True, text=True).stdout

    def close(self):
        self.closed = True


class ShellCommandBudgetTest(unittest.TestCase):
    def run_push(self, data, remote, device=None):
        device = device if device is not None else CommandLimitedDevice()
        out, err = io.StringIO(), io.StringIO()
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "source.bin"
            local.write_bytes(data)
            remote = str(Path(tmp) / remote) if not remote.startswith("/") else remote
            with mock.patch.object(sys, "argv", ["b64push.py", str(local), remote]), \
                    mock.patch.dict(sys.modules, {"tv": types.SimpleNamespace(connect=lambda: device)}), \
                    contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = push.main()
            try:
                copied = Path(remote).read_bytes() if Path(remote).is_file() else None
            except OSError:
                # A path long enough to be refused as an argument is also long enough for the OS to
                # refuse to stat it, and a refusal is the case where nothing was written.
                copied = None
        return code, device, out.getvalue(), err.getvalue(), copied

    def chunk_commands(self, device):
        return [c for c in device.commands if c.startswith("echo '")]

    def test_a_tv_that_refuses_a_65_kb_command_now_gets_the_file(self):
        # The reported set, and a file the size of the add-on APK install.sh uploads this way.
        data = bytes(range(256)) * 2344
        code, device, out, err, copied = self.run_push(data, "target.apk")
        self.assertEqual(code, 0, out + err)
        self.assertNotIn("Traceback", err)
        self.assertEqual(copied, data, "the copy on the TV is the file that was read")
        self.assertIn("MATCH", out)
        self.assertTrue(device.closed)

    def test_every_command_fits_the_budget_and_still_carries_24_kb(self):
        data = bytes(range(256)) * 2344
        code, device, _, err, _ = self.run_push(data, "target.apk")
        self.assertEqual(code, 0, err)
        lengths = [len(c.encode("utf-8")) for c in device.commands]
        self.assertLessEqual(max(lengths), push.SHELL_COMMAND_LIMIT, "no command is over the budget")
        self.assertGreater(max(lengths), push.SHELL_COMMAND_LIMIT - 1024,
                           "the chunks use the budget, rather than falling back to something tiny")
        chunks = self.chunk_commands(device)
        self.assertGreaterEqual(len(chunks), 20, "the 600 KB file travels in chunks, not pieces")
        self.assertLessEqual(len(chunks), 30)

    def test_the_budget_is_32_kb_a_command(self):
        # Under the smallest command the reported set took (32,800 bytes, 5 of 5) and well under the
        # 57,000 that timed out 3 of 3.
        self.assertEqual(push.SHELL_COMMAND_LIMIT, 32 * 1024)

    def test_a_transport_that_states_its_own_limit_keeps_it(self):
        # The native adapter: its 4096-byte payload, and the command budget it states for itself.
        device = CommandLimitedDevice(max_chunk_size=4096, max_shell_command_size=4088)
        data = b"new contents" * 2000
        code, device, out, err, copied = self.run_push(data, "target.bin", device=device)
        self.assertEqual(code, 0, out + err)
        self.assertEqual(copied, data)
        self.assertLessEqual(max(len(c.encode("utf-8")) for c in device.commands), 4088)
        self.assertGreater(len(self.chunk_commands(device)), 5, "a 4 KB transport still chunks")

    def test_a_stated_limit_with_no_room_left_is_refused_before_anything_is_sent(self):
        device = CommandLimitedDevice(max_chunk_size=NEGOTIATED_PAYLOAD, max_shell_command_size=300)
        code, device, out, err, _ = self.run_push(b"new contents", "target.bin", device=device)
        self.assertEqual(code, 2, out + err)
        self.assertEqual(device.commands, [], "not staging, not cleanup")
        self.assertTrue(device.closed)
        self.assertIn("under 256 bytes per chunk", err)
        self.assertIn("300-byte limit", err, "the refusal names the budget that leaves no room")
        self.assertIn("Nothing was sent to the TV.", err)

    def test_a_verification_command_over_the_budget_is_refused(self):
        # The preflight reads the same budget as the chunks: this path's verification command is over
        # 32 KB, and it is refused before the TV is touched rather than after 24 chunks went over.
        path = "/data/local/tmp/" + "a" * 8300 + "/file.bin"
        code, device, out, err, _ = self.run_push(b"new contents" * 10, path)
        self.assertEqual(code, 2, out + err)
        self.assertIn("verification needs", err)
        self.assertIn(f"at this transport's {push.SHELL_COMMAND_LIMIT}-byte command limit", err)
        self.assertEqual(device.commands, [])
        self.assertTrue(device.closed)


if __name__ == "__main__":
    unittest.main()

"""tools/adb-closed.py names ADB only for the port ADB listens on.

The closing step takes a port as its second word (`tools/adb-closed.py <tv ip> <port>`: Wireless debugging
has one of its own, and some firmware runs adbd on another, 5877 on the Wanbo T6R Max), so the tool checks
a port it was handed. Any open port answered `<tv ip>:<port> is still open: network ADB is on. ... turn USB
debugging, Network/ADB debugging and Wireless debugging off`.

On a Philips TPM191E (58PUS7304, Android TV 12, `STT2.230526.001`) that sentence was printed for port 1925,
which is the set's JointSpace API: it answers whatever the debugging toggles say, because it is the API the
remote app and Home Assistant use (tools/philips-api.py reads it on 1926 and 1925, and says at its own door
that a port in `TV_HOST` "is the ADB port, not a JointSpace one"). An owner who checks a port like that is
told ADB is on, turns the three toggles off again, finds the port open again, and has no way to finish the
step (#3890).

The port can be judged: 5555 is where adbd listens by default, so an open 5555 is network ADB. Another port
is the owner's choice (Wireless debugging's, a firmware's own adbd port) or another service entirely, so the
message has to name the port it checked and say which of the two it can be, instead of claiming ADB for it.

The TV cases run the script under the stand-in for `socket.create_connection` that
tests/test_adb_closed_only_a_refusal.py uses, so they reach no network; the local cases run against real
sockets.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_adb_closed_only_a_refusal import TV, check_tv, env_with  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"
NOTE = "is this computer: a relay answers for itself, not for the TV."


def flat(text):
    """Collapse line wrapping, so a sentence the tool prints over two lines reads as one."""
    return re.sub(r"\s+", " ", text)


def docstring():
    """The tool's docstring, flattened, so a wrapped sentence reads as one."""
    return flat(SCRIPT.read_text(encoding="utf-8").split('"""')[1])


def check(*args, env=None):
    """The script against real sockets."""
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], capture_output=True, text=True,
                          timeout=30, env=env if env is not None else env_with())


class OnlyTheAdbPortSaysNetworkAdbTest(unittest.TestCase):
    def test_a_port_of_the_owners_choosing_is_named_as_a_port(self):
        # 1925 is Philips' JointSpace API, always open on those sets whatever the debugging toggles say.
        result = check_tv(TV, "1925", tv="1925=open,5555=refused")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:1925 is still open", result.stderr)
        self.assertNotIn("network ADB is on", result.stderr,
                         "an open JointSpace port is not network ADB, and no toggle closes it")
        self.assertIn("an open port like that says nothing about ADB", flat(result.stderr))

    def test_it_says_which_port_to_check_instead(self):
        result = check_tv(TV, "1925", tv="1925=open,5555=refused")
        self.assertIn(f"tools/adb-closed.py {TV}", result.stderr,
                      "the reader has to be sent to the ADB port by the command that checks it")

    def test_the_wireless_debugging_port_is_still_the_debugging_toggles(self):
        # The documented second use: the Wireless debugging port, checked straight after switching it off.
        result = check_tv(TV, "43217", tv="43217=open,5555=refused")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:43217 is still open", result.stderr)
        self.assertIn("Wireless debugging", result.stderr)
        self.assertIn("turn USB debugging", result.stderr)

    def test_the_adb_port_itself_still_says_network_adb_is_on(self):
        result = check_tv(TV, "5555", tv="5555=open")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:5555 is still open: network ADB is on.", result.stderr)
        self.assertIn("turn USB debugging", result.stderr)
        self.assertNotIn("says nothing about ADB", result.stderr)

    def test_a_tv_port_of_its_own_is_not_called_the_adb_port(self):
        # 5877 on the Wanbo T6R Max is the TV's own adbd port, and this tool cannot know that: the message
        # it prints has to leave room for both readings rather than assert the one it cannot see.
        env = env_with(TV_PORT="5877")
        result = check_tv(TV, tv="5877=open,5555=refused", env=env)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"{TV}:5877 is still open", result.stderr)
        self.assertNotIn("network ADB is on", result.stderr)

    def test_an_open_port_on_this_computer_is_still_the_relays(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check("127.0.0.1", env=env_with(TV_HOST="127.0.0.1", TV_PORT=str(port)))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)
        self.assertIn("127.0.0.1 " + NOTE, result.stderr)
        self.assertNotIn("network ADB is on", result.stderr)


class TheDocstringSaysWhichPortIsAdbTest(unittest.TestCase):
    def test_the_docstring_says_5555_is_the_adb_port(self):
        # The rule the message follows, stated where the tool's other exit-status rules are.
        doc = docstring()
        self.assertIn("Port 5555 is the ADB port", doc)
        self.assertIn("another port may be the Wireless debugging port", doc)
        self.assertIn("an open port says nothing about ADB", doc)


if __name__ == "__main__":
    unittest.main()

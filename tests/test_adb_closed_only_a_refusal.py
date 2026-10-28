"""tools/adb-closed.py says "closed" only when the TV refused the connection.

Exit 0 is what the closing step in PROMPT.md, AGENTS.md and the README FAQ accepts as done, and three cases
gave it for a port nobody had checked:

  * Nothing answered in 3 s. That was "closed, unless the TV is off or not at this address", exit 0, but
    port 5555 times out with ADB still on: a TV asleep or rebooting, at a new address, on another VLAN,
    behind a VPN's firewall, or out of reach from an assistant's sandbox (docs/TROUBLESHOOTING.md,
    tools/relay.md). PROMPT.md already says only "Connection refused" proves the port closed. Now it is
    exit 2, "could not check", on stderr.
  * A relay's TV_PORT exported for the tools (TV_HOST=127.0.0.1 TV_PORT=15555, or TV_PORT=15555 left
    behind on its own), then `adb-closed.py <tv ip>` at the closing step: it would check the TV's IP on
    the relay's port, which a TV with 5555 open refuses, and say "closed". It now refuses that TV_PORT on
    tools/relay.py's two conditions, with relay.py's wording, its example adapted, and still takes a
    TV_PORT given beside the TV's own IP (5877 on the Wanbo T6R Max, docs/TROUBLESHOOTING.md).
  * A refusal on 127.0.0.1, which is this computer, not the TV: with the relay's TV_HOST/TV_PORT still
    exported and the relay stopped, that was "the TV refused the connection", exit 0. Now it is exit 2,
    and the result says a relay answers for itself.

The TV cases run the script under a stand-in for socket.create_connection, so they reach no network: it
refuses, takes or never answers by port, as the TV would, and notes each connection it is asked for. The
127.0.0.1 cases run against real sockets.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"
README = ROOT / "README.md"
TV = "192.0.2.1"   # a documentation address; the stand-in answers for it, nothing on the network does
NOTE = "is this computer: a relay answers for itself, not for the TV."

# Replaces socket.create_connection, then runs the script as `python3 tools/adb-closed.py ...` would.
# STAND_IN_TV maps a port to how the TV answers it: refused, open, or silent (nothing within the timeout).
STAND_IN = r"""
import os, runpy, socket, sys
answers = dict(item.split("=") for item in os.environ["STAND_IN_TV"].split(","))
def stand_in(address, timeout=None, *args, **kwargs):
    sys.stderr.write("[stand-in] connect %s:%s\n" % address)
    answer = answers.get(str(address[1]), "refused")
    if answer == "silent":
        raise socket.timeout("timed out")
    if answer == "refused":
        raise ConnectionRefusedError(61, "Connection refused")
    return socket.socket()
socket.create_connection = stand_in
sys.argv = sys.argv[1:]
runpy.run_path(sys.argv[0], run_name="__main__")
"""


def env_with(**values):
    """This environment without the shell's own TV_HOST/TV_PORT, plus the given ones."""
    env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    env.update(values)
    return env


def check(*args, env=None):
    """The script against real sockets."""
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], capture_output=True, text=True,
                          timeout=30, env=env if env is not None else env_with())


def check_tv(*args, tv, env=None):
    """The script against the stand-in TV: tv is "5555=open,15555=refused" and the like."""
    env = env if env is not None else env_with()
    env["STAND_IN_TV"] = tv
    return subprocess.run([sys.executable, "-I", "-c", STAND_IN, str(SCRIPT), *args], capture_output=True,
                          text=True, timeout=30, env=env)


def free_port():
    """A port nothing listens on, so a connection to it is refused."""
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class OnlyTheTvsRefusalIsClosedTest(unittest.TestCase):
    def test_a_refusal_at_the_tvs_ip_is_closed(self):
        # The address and port as an argument, in the address, or from TV_HOST.
        for args, env in (((TV,), env_with()), ((TV, "5555"), env_with()), ((f"{TV}:5555",), env_with()),
                          ((), env_with(TV_HOST=f"{TV}:5555")), ((), env_with(TV_HOST=TV))):
            with self.subTest(args=args, TV_HOST=env.get("TV_HOST")):
                result = check_tv(*args, tv="5555=refused,15555=open", env=env)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(result.stdout, f"{TV}:5555 is closed: the TV refused the connection.\n")
                self.assertIn(f"[stand-in] connect {TV}:5555", result.stderr)

    def test_no_answer_proves_nothing(self):
        result = check_tv(TV, tv="5555=silent")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn(f"[stand-in] connect {TV}:5555", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertIn(f"could not check {TV}:5555: it did not answer in 3 s, and that proves nothing",
                      result.stderr)
        self.assertIn("run this from an ordinary terminal", result.stderr)
        self.assertIn("Only a refused\nconnection shows the port closed.", result.stderr)
        self.assertNotIn("closed, unless", result.stderr)

    def test_the_tvs_ip_gets_no_relay_note(self):
        for tv, code in (("5555=refused", 0), ("5555=open", 1), ("5555=silent", 2)):
            with self.subTest(tv=tv):
                result = check_tv(TV, tv=tv)
                self.assertEqual(result.returncode, code, result.stdout + result.stderr)
                self.assertNotIn("relay", result.stdout + result.stderr)


class ThisComputerIsNotTheTvTest(unittest.TestCase):
    def test_a_refusal_on_this_computer_is_not_the_tvs(self):
        # The relay's own TV_HOST/TV_PORT with the relay stopped (tools/relay.md: "Stop it with Ctrl-C"),
        # then the closing check: nothing listens on 127.0.0.1:15555, and that says nothing about 5555.
        port = free_port()
        for args, env in ((("127.0.0.1", str(port)), env_with()), ((f"127.0.0.1:{port}",), env_with()),
                          ((), env_with(TV_HOST=f"127.0.0.1:{port}")),
                          ((), env_with(TV_HOST="127.0.0.1", TV_PORT=str(port))),
                          (("127.0.0.1",), env_with(TV_HOST="127.0.0.1", TV_PORT=str(port)))):
            with self.subTest(args=args, TV_HOST=env.get("TV_HOST"), TV_PORT=env.get("TV_PORT")):
                result = check(*args, env=env)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertEqual(result.stdout, "")
                self.assertIn(f"could not check 127.0.0.1:{port}: nothing listens there, which says nothing "
                              "about the TV.", result.stderr)
                self.assertIn("127.0.0.1 " + NOTE, result.stderr)
                self.assertIn("from an ordinary terminal:  tools/adb-closed.py <tv ip>", result.stderr)
                self.assertNotIn("is closed", result.stderr)

    def test_every_loopback_name_is_this_computer(self):
        for host in ("localhost", "LOCALHOST", "::1", "[::1]:5555", "127.0.1.1"):
            with self.subTest(host=host):
                result = check_tv(host, tv="5555=refused")
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertEqual(result.stdout, "")
                self.assertIn("nothing listens there, which says nothing about the TV.", result.stderr)
                self.assertIn(NOTE, result.stderr)

    # Every other way the socket API takes to write this computer (#1837 review). On 8a945a09 each of
    # these said "the TV refused the connection" and exited 0.
    ALIASES = ("localhost.", "LOCALHOST.", "127.1", "2130706433", "0x7f000001", "0177.0.0.1",
               "::ffff:127.0.0.1", "[::ffff:127.0.0.1]:5555", "0.0.0.0", "::", "tv.localhost")

    def test_every_way_of_writing_this_computer_is_this_computer(self):
        for host in self.ALIASES:
            with self.subTest(host=host):
                result = check_tv(host, tv="5555=refused")
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertEqual(result.stdout, "")
                self.assertIn("nothing listens there, which says nothing about the TV.", result.stderr)
                self.assertIn(NOTE, result.stderr)

    def test_a_short_ipv4_form_is_this_computer_on_real_sockets(self):
        # Numeric forms getaddrinfo itself resolves to 127.0.0.1, so no name service is involved.
        port = free_port()
        for host in ("127.1", "2130706433", "0x7f000001"):
            with self.subTest(host=host):
                result = check(host, str(port))
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertEqual(result.stdout, "")
                self.assertIn(f"could not check {host}:{port}: nothing listens there", result.stderr)
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check("127.1", str(port))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("127.1 " + NOTE, result.stderr)

    def test_names_that_only_look_local_are_still_the_tvs(self):
        for host in ("localhost.example.com", "mytv.local", "127.example", "10.0.0.1", "::ffff:192.0.2.1"):
            with self.subTest(host=host):
                result = check_tv(host, tv="5555=refused")
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("is closed: the TV refused the connection.", result.stdout)

    def test_an_open_port_on_this_computer_is_said_to_be_the_relays(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check("127.0.0.1", env=env_with(TV_HOST="127.0.0.1", TV_PORT=str(port)))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)
        self.assertIn("127.0.0.1 " + NOTE, result.stderr)


class ARelaysPortIsNotTheTvsTest(unittest.TestCase):
    RELAY = env_with(TV_HOST="127.0.0.1", TV_PORT="15555")   # as tools/relay.md exports them

    def test_the_relays_tv_port_is_refused_for_the_tvs_ip(self):
        # The TV refuses 15555 and has 5555 open: on main each of these says "closed" and exits 0.
        for args, env in (((TV,), self.RELAY),
                          ((TV,), env_with(TV_HOST="localhost", TV_PORT="15556")),   # a second relay's port
                          ((TV,), env_with(TV_PORT="15555")),                        # TV_HOST unset, TV_PORT left
                          ((), env_with(TV_HOST=TV, TV_PORT="15555")),               # TV_HOST pointed at the TV
                          ((TV,), env_with(TV_HOST=TV, TV_PORT="15555"))):
            with self.subTest(args=args, TV_HOST=env.get("TV_HOST"), TV_PORT=env.get("TV_PORT")):
                result = check_tv(*args, tv="5555=open,15555=refused,15556=refused", env=dict(env))
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertEqual(result.stdout, "")
                self.assertIn(f"TV_PORT={env['TV_PORT']} is a relay's port, set for the tools: unset TV_HOST "
                              f"TV_PORT in this terminal, or give the TV's port, e.g.  tools/adb-closed.py {TV} "
                              "5555", result.stderr)
                self.assertNotIn("[stand-in]", result.stderr, "it must not check the TV's IP on the relay's port")

    def test_a_relay_written_another_way_is_still_a_relay(self):
        # A second relay's port beside TV_HOST written as 127.1, localhost. and the like.
        for relay_host in ThisComputerIsNotTheTvTest.ALIASES:
            env = env_with(TV_HOST=relay_host, TV_PORT="15556")
            with self.subTest(TV_HOST=relay_host):
                result = check_tv(TV, tv="5555=open,15556=refused", env=env)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("TV_PORT=15556 is a relay's port", result.stderr)
                self.assertNotIn("[stand-in]", result.stderr, "it must not check the TV's IP on the relay's port")

    def test_a_port_given_with_the_tvs_ip_is_used(self):
        for env in (self.RELAY, env_with(TV_PORT="15555")):
            for args in ((TV, "5555"), (f"{TV}:5555",)):
                with self.subTest(args=args, TV_HOST=env.get("TV_HOST")):
                    result = check_tv(*args, tv="5555=open,15555=refused", env=dict(env))
                    self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                    self.assertIn(f"{TV}:5555 is still open", result.stderr)

    def test_tv_port_still_counts_beside_the_tvs_own_ip(self):
        # A TV whose adbd has a port of its own (5877 on the Wanbo T6R Max): TV_PORT is the TV's there.
        for args, env in (((TV,), env_with(TV_PORT="5877")),
                          ((TV,), env_with(TV_HOST=TV, TV_PORT="5877")),
                          ((), env_with(TV_HOST=TV, TV_PORT="5877"))):
            with self.subTest(args=args, TV_HOST=env.get("TV_HOST")):
                result = check_tv(*args, tv="5877=open,5555=refused", env=env)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn(f"{TV}:5877 is still open", result.stderr)

    def test_15555_is_the_port_relay_py_listens_on(self):
        # adb-closed.py refuses a TV_PORT of 15555 for the TV's IP because relay.py listens there by default.
        relay = (ROOT / "tools" / "relay.py").read_text(encoding="utf-8")
        self.assertRegex(relay, r'add_argument\(\s*"--port"[^)]*\bdefault=15555\b')
        self.assertIn("\nRELAY_PORT = 15555 ", SCRIPT.read_text(encoding="utf-8"))


class TheDocsTest(unittest.TestCase):
    def test_the_docstring_gives_exit_0_for_a_refusal_only(self):
        doc = " ".join(SCRIPT.read_text(encoding="utf-8").split('"""')[1].split())
        self.assertIn("Exits 0 when the TV refuses the connection, 1 when the port is still open, and 2", doc)
        self.assertIn("nothing listened on 127.0.0.1", doc)
        self.assertNotIn("or does not answer", doc)

    def test_the_readme_row_gives_exit_0_for_a_refusal_only(self):
        rows = [line for line in README.read_text(encoding="utf-8").splitlines()
                if line.startswith("| [`tools/adb-closed.py`]")]
        self.assertEqual(len(rows), 1, "the README's Tools row for adb-closed.py moved")
        self.assertIn("exits 0 when the TV refuses the connection", rows[0])
        self.assertIn("2 when nothing answered or the check itself could not run", rows[0])
        self.assertNotIn("nothing answers", rows[0])

    def test_the_relay_guide_sends_the_closing_check_to_an_ordinary_terminal(self):
        guide = " ".join((ROOT / "tools" / "relay.md").read_text(encoding="utf-8").split())
        self.assertIn("**Check that ADB is closed from an ordinary terminal**, at the TV's IP: "
                      "`tools/adb-closed.py 192.168.1.50`", guide)


class RunsAsTheDocsTypeItTest(unittest.TestCase):
    # PROMPT.md, AGENTS.md, SHOWCASE.md and examples/hy320/README.md type `tools/adb-closed.py <tv ip>`,
    # with no interpreter in front. It needs only the standard library, so its shebang's python3 will do.
    def test_git_has_adb_closed_py_as_executable(self):
        # What a clone gets: the mode in git, not the mode of this checkout's file.
        if shutil.which("git") is None:
            self.skipTest("git is not installed")
        r = subprocess.run(["git", "ls-files", "-s", "tools/adb-closed.py"], cwd=str(ROOT), capture_output=True,
                           text=True)
        if r.returncode != 0 or not r.stdout:
            self.skipTest("not a git checkout")
        self.assertTrue(r.stdout.startswith("100755 "),
                        f"tools/adb-closed.py must be executable in git, as the docs run it bare: {r.stdout}")

    @unittest.skipIf(os.name == "nt", "a script started by its shebang needs a POSIX system")
    def test_the_bare_command_starts(self):
        if shutil.which("python3") is None:
            self.skipTest("no python3 on PATH for the shebang")
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            try:
                r = subprocess.run([str(SCRIPT), "127.0.0.1", str(port)], cwd=str(ROOT), capture_output=True,
                                   text=True, timeout=30, env=env_with())
            except PermissionError:
                self.fail("`tools/adb-closed.py <tv ip>` gives 'permission denied': the file is not executable")
        self.assertEqual(r.returncode, 1, r.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", r.stderr)


if __name__ == "__main__":
    unittest.main()

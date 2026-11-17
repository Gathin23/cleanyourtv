"""tools/relay.py names a first word that is a URL or a path, instead of relaying to it.

tools/relay.md hands the owner `tools/relay.py <tv ip>`, and the address defaults to `$TV_HOST`, so the
word it reads is copied off a browser or a page as often as it is typed. Two shapes of it were read as
the TV's host instead:

  * `http://<tv ip>` split into the host "http" and the port "//<tv ip>", and the run answered
    "relay.py: error: not a port number: '//<tv ip>'" - a sentence about a port fragment, for a word
    that is an address of another kind.
  * `http://<tv ip>:5555` and `<tv ip>/6/system` became hosts literally named for the URL and the page:
    the relay bound its loopback port and printed "relay 127.0.0.1:15555 -> http://<tv ip>:5555:5555",
    so it told the owner it was up, for an address it can never forward to. The mistake surfaced only
    when a client connected, as "cannot reach http://<tv ip>:5555:5555 (...)(is the TV on, and network
    debugging still on?)", which blames the TV, its standby and the network for the argument.

The word is named before the TV's port is read out of it and before anything is bound, with status 2 -
what every other refusal in this script answers. Left alone on purpose: an address with its own port
(`<tv ip>:5877`) and a bracketed IPv6 one still forward to the port they name, the forms tools/relay.md
and tests/test_relay.py pin.

The refusals reach no network. The controls start the relay on a free loopback port, read the line that
says where it forwards to, and stop it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "relay.py"
TV = "tv.invalid"   # a name reserved for documentation: it never resolves, and nothing here asks it


def env_with(**values):
    """This environment without the shell's own TV_HOST/TV_PORT, plus the given ones."""
    env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    env.update(values)
    return env


def refused(*args, env=None):
    """The script run with an address it cannot take: it must answer and exit, not start relaying."""
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], capture_output=True, text=True,
                          timeout=30, env=env if env is not None else env_with())


def forwards_to(*args, env=None):
    """The line the relay prints for where it forwards to, then stop it (it serves until stopped)."""
    proc = subprocess.Popen([sys.executable, "-I", str(SCRIPT), *args], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, text=True, env=env if env is not None else env_with())
    assert proc.stdout is not None
    try:
        line = proc.stdout.readline()
    finally:
        proc.terminate()
        try:
            proc.communicate(timeout=20)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.communicate()
    return line


def address_problem_module():
    """The script imported as a module, for address_problem() itself."""
    spec = importlib.util.spec_from_file_location("relay_under_test", SCRIPT)
    assert spec is not None and spec.loader is not None, f"{SCRIPT} cannot be loaded"
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class AnAddressThatIsNotOneTest(unittest.TestCase):
    def test_a_url_with_a_port_is_named_and_the_relay_does_not_start(self):
        # Before: the host was the whole URL, so the relay started, said it was up, and only a client
        # that connected heard the TV blamed.
        result = refused(f"http://{TV}:5555", "--port", "0")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("that is a URL, not the TV's address", result.stderr)
        self.assertNotIn("cannot reach", result.stderr)
        self.assertNotIn("network debugging still on", result.stderr)

    def test_a_url_without_a_port_is_named_as_a_url(self):
        result = refused(f"http://{TV}", "--port", "0")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("that is a URL, not the TV's address", result.stderr)
        self.assertNotIn("not a port number", result.stderr)

    def test_a_tv_host_that_is_a_url_is_named_too(self):
        # The address comes from TV_HOST as often as from the command line (tools/relay.md).
        result = refused("--port", "0", env=env_with(TV_HOST=f"https://{TV}"))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("that is a URL, not the TV's address", result.stderr)

    def test_a_page_at_the_address_is_named_as_a_path(self):
        result = refused(f"{TV}:5555/", "--port", "0")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("that is a path, not the TV's address", result.stderr)
        self.assertNotIn("not a port number", result.stderr)

    def test_a_page_at_the_bare_address_is_named_as_a_path(self):
        result = refused(f"{TV}/6/system", "--port", "0")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("that is a path, not the TV's address", result.stderr)
        self.assertNotIn("network debugging still on", result.stderr)

    def test_the_reader_names_the_url_and_the_path_and_leaves_addresses_alone(self):
        module = address_problem_module()
        self.assertIn("URL", module.address_problem(f"http://{TV}"))
        self.assertIn("URL", module.address_problem(f"https://{TV}:5555"))
        # A page opened at the TV's address is a path, not a URL, and is named as the one it is.
        self.assertIn("path", module.address_problem(f"{TV}/6/system"))
        self.assertIn("path", module.address_problem(f"{TV}:5555/x"))
        for word in (TV, f"{TV}:5555", "localhost", "127.0.0.1", "[fe80::1]", "[fe80::1]:5555",
                     "tv-box.local.", f"  {TV}  "):
            with self.subTest(word=word):
                self.assertIsNone(module.address_problem(word),
                                  f"{word} is an address this script takes and was refused")


class TheCommandLineItAlreadyTookTest(unittest.TestCase):
    """What this change must not move: the address forms tools/relay.md documents still forward."""

    def test_an_address_with_its_port_still_forwards_to_that_port(self):
        self.assertIn(f"-> {TV}:5877\n", forwards_to(f"{TV}:5877", "--port", "0"))

    def test_a_bracketed_address_is_still_one(self):
        self.assertIn("-> fe80::1:5555\n", forwards_to("[fe80::1]:5555", "--port", "0"))

    def test_a_plain_host_still_uses_port_5555(self):
        self.assertIn("-> localhost:5555\n", forwards_to("localhost", "--port", "0"))

    def test_a_tv_host_that_is_an_address_is_still_used(self):
        self.assertIn(f"-> {TV}:5877\n",
                      forwards_to("--port", "0", env=env_with(TV_HOST=f"{TV}:5877")))

    def test_an_explicit_tv_port_still_wins_over_the_one_in_the_address(self):
        self.assertIn(f"-> {TV}:5555\n",
                      forwards_to(f"{TV}:5877", "--tv-port", "5555", "--port", "0"))


if __name__ == "__main__":
    unittest.main()

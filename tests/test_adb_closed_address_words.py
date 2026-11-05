"""tools/adb-closed.py names a first word that is a URL, a path or a flag, instead of asking it.

The closing step in PROMPT.md, AGENTS.md and the README FAQ runs `tools/adb-closed.py <tv ip> [port]`,
and an address pasted from a browser (or a page opened at it) reached the socket, where the answer was
about something else:

  * `http://<tv ip>` split into the host "http" and the port "//<tv ip>", and the run answered
    "not a port number: '//<tv ip>'" - a sentence about a port fragment for a word that is a URL.
  * `http://<tv ip>:5555` became a host literally named for the URL, and the lookup failed into
    "could not check http://<tv ip>:5555:5555 (...). Is the TV on and on this network?" - a question
    about the TV's power, for an argument this check cannot use.
  * `<tv ip>:5555/` and `<tv ip>/6/system` read the same way ("not a port number: '5555/'", and the
    "Is the TV on" question for the path).

A flag copied over from another tool read the same way as the URL: `--port 5555`, `-p 5555` and any
other dash-led word went into getaddrinfo as a host named for the flag, because no hostname starts with
a dash and nothing here takes an option. tools/restore.sh and tools/theme.sh refuse a dash-led word for
the same reason; this check asks the socket instead and blamed the TV's power.

The status was already 2 in every one of them, so only the words were wrong; the word is named before
the socket now. Left alone on purpose: the second word still wins over a port in the address
(`<tv ip>:5877 5555` asks 5555), the command line the tool documents and tests/test_adb_closed.py pins
with test_cli_port_overrides_endpoint_port, and -h or --help on its own still prints the text.

The TV cases run the script under a stand-in for socket.create_connection, so they reach no network: it
refuses, takes or never answers by port, and notes every connection it is asked for - the note is how a
test says nothing was asked.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"
TV = "192.0.2.1"   # a documentation address: the stand-in answers for it, nothing on the network does

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


def check_tv(*args, tv, env=None):
    """The script against the stand-in TV: tv is "5555=open,15555=refused" and the like."""
    env = dict(env if env is not None else env_with())
    env["STAND_IN_TV"] = tv
    return subprocess.run([sys.executable, "-I", "-c", STAND_IN, str(SCRIPT), *args],
                          capture_output=True, text=True, timeout=30, env=env)


def asked(result):
    """The connections the run made, from the stand-in's own notes."""
    return [line for line in result.stderr.splitlines() if line.startswith("[stand-in] connect")]


def address_problem_module():
    """The script imported as a module, for address_problem() itself."""
    spec = importlib.util.spec_from_file_location("adb_closed_under_test", SCRIPT)
    assert spec is not None and spec.loader is not None, f"{SCRIPT} cannot be loaded"
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class AnAddressThatIsNotOneTest(unittest.TestCase):
    def test_a_url_with_a_port_is_named_and_nothing_is_asked(self):
        # Before: the host was the whole URL, so the lookup failed and the answer asked whether the TV
        # was on - about a word that names no host at all.
        result = check_tv(f"http://{TV}:5555", tv="5555=refused")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertEqual([], asked(result))
        self.assertIn("that is a URL, not the TV's address", result.stderr)
        self.assertNotIn("Is the TV on", result.stderr)

    def test_a_url_without_a_port_is_named_as_a_url(self):
        result = check_tv(f"http://{TV}", tv="5555=refused")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("that is a URL, not the TV's address", result.stderr)
        self.assertNotIn("not a port number", result.stderr)
        self.assertNotIn("Is the TV on", result.stderr)
        self.assertEqual([], asked(result))

    def test_a_tv_host_that_is_a_url_is_named_too(self):
        # The address comes from TV_HOST as often as from the command line (tools/relay.md).
        result = check_tv(tv="5555=refused", env=env_with(TV_HOST=f"https://{TV}"))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("that is a URL, not the TV's address", result.stderr)
        self.assertEqual([], asked(result))

    def test_a_page_at_the_address_is_named_as_a_path(self):
        result = check_tv(f"{TV}:5555/", tv="5555=refused")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("that is a path, not the TV's address", result.stderr)
        self.assertNotIn("not a port number", result.stderr)
        self.assertEqual([], asked(result))

    def test_a_page_at_the_bare_address_is_named_as_a_path(self):
        result = check_tv(f"{TV}/6/system", tv="5555=refused")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("that is a path, not the TV's address", result.stderr)
        self.assertNotIn("Is the TV on", result.stderr)
        self.assertEqual([], asked(result))

    def test_a_flag_is_named_and_nothing_is_asked(self):
        # `tools/adb-closed.py --port 5555` and `-p 5555` are the shape a reader copies from a tool that
        # takes options. Nothing here does: the word went into getaddrinfo as a host named for the flag,
        # and the run answered the OSError branch's "Is the TV on and on this network?" on a real
        # terminal - a question about the TV's power, at the closing step whose only answer counts. Under
        # the stand-in below there is no lookup, so the same word reached the stand-in and the run even
        # said "closed: the TV refused the connection" with status 0, which reads as proof that ADB is
        # off. Neither answer is about the command line, and the port after the flag was never read.
        for word in ("--port", "-p", "--nope", "-x"):
            with self.subTest(word=word):
                result = check_tv(word, "5555", tv="5555=refused")
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertEqual("", result.stdout)
                self.assertEqual([], asked(result))
                self.assertIn("that is a flag, not the TV's address", result.stderr)
                self.assertNotIn("Is the TV on", result.stderr)
                self.assertNotIn("not a port number", result.stderr)

    def test_the_reader_names_a_flag_and_leaves_addresses_alone(self):
        module = address_problem_module()
        for word in ("--port", "-p", "-x", "--nope", " --port "):
            with self.subTest(word=word):
                self.assertIn("a flag", module.address_problem(word))
        for word in (TV, f"{TV}:5555", "[2001:db8::1]", "[2001:db8::1]:5555", "localhost",
                     "tv.local", "tv.local:5877"):
            with self.subTest(word=word):
                self.assertIsNone(module.address_problem(word),
                                  f"{word} is an address this check takes and was refused")

    def test_a_name_that_really_does_not_resolve_keeps_its_own_words(self):
        # The control for the branch above: a hostname that is not a flag still reaches the network, and
        # its answer is about the TV's power rather than about the command line. .invalid never resolves
        # (RFC 2606), so this runs offline and online alike.
        result = subprocess.run([sys.executable, "-I", str(SCRIPT), "tv.invalid"],
                                capture_output=True, text=True, timeout=30, env=env_with())
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("could not check tv.invalid:5555", result.stderr)
        self.assertIn("Is the TV on and on this network?", result.stderr)
        self.assertNotIn("that is a flag", result.stderr)

    def test_help_on_its_own_still_prints_the_text(self):
        # -h and --help were already read before the address is judged; this change must not turn them
        # into a flag refusal.
        for word in ("-h", "--help"):
            with self.subTest(word=word):
                result = check_tv(word, tv="5555=refused")
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("Check that the TV's network ADB port is closed", result.stderr)
                self.assertNotIn("that is a flag", result.stderr)
                self.assertEqual([], asked(result))

    def test_an_ipv6_address_still_is_one(self):
        # The control: a bracketed address with a port is an address, and is asked as one.
        result = check_tv("[2001:db8::1]:5877", tv="5877=refused")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(["[stand-in] connect 2001:db8::1:5877"], asked(result))
        # The sentence writes the host and port it asked, as every other line of the script does.
        self.assertEqual("2001:db8::1:5877 is closed: the TV refused the connection.\n", result.stdout)

    def test_the_reader_names_the_url_and_the_path_and_leaves_addresses_alone(self):
        module = address_problem_module()
        self.assertIn("URL", module.address_problem(f"http://{TV}"))
        self.assertIn("URL", module.address_problem(f"https://{TV}:5555"))
        # A page opened at the TV's address is a path, not a URL, and is named as the one it is.
        self.assertIn("path", module.address_problem(f"{TV}/6/system"))
        self.assertIn("path", module.address_problem(f"{TV}:5555/x"))
        for word in (TV, f"{TV}:5555", "[2001:db8::1]", "[2001:db8::1]:5555", "localhost",
                     "tv.local", "tv.local:5877"):
            with self.subTest(word=word):
                self.assertIsNone(module.address_problem(word),
                                  f"{word} is an address this check takes and was refused")


class TheCommandLineItAlreadyTookTest(unittest.TestCase):
    """What this change must not move: the address and port forms the tool documents still work."""

    def test_an_address_with_its_port_still_checks_that_port(self):
        result = check_tv(f"{TV}:5555", tv="5555=refused")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual([f"[stand-in] connect {TV}:5555"], asked(result))
        self.assertEqual(f"{TV}:5555 is closed: the TV refused the connection.\n", result.stdout)

    def test_a_second_word_still_wins_over_a_port_in_the_address(self):
        # The documented command line (tests/test_adb_closed.py's
        # test_cli_port_overrides_endpoint_port): `:5877` in the address and 5555 after it asks 5555.
        result = check_tv(f"{TV}:5877", "5555", tv="5877=open,5555=refused")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual([f"[stand-in] connect {TV}:5555"], asked(result))

    def test_a_second_word_that_is_not_a_port_keeps_its_own_message(self):
        result = check_tv(f"{TV}:5555", "abc", tv="5555=refused")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("not a port number: 'abc'", result.stderr)
        self.assertEqual([], asked(result))


if __name__ == "__main__":
    unittest.main()

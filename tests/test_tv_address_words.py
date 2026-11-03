"""tools/tv.py names a TV_HOST that is a URL or a path, instead of blaming the TV or its network.

TV_HOST is documented as "the TV's IP (or IP:port)", and an address is often pasted from somewhere
else: a browser tab's address bar gives `http://192.168.1.50`, and the tail of a page copied from one
gives `192.168.1.50/`. Four command lines, and what they answered before this change:

    TV_HOST=http://192.168.1.50        -> not a port number: '//192.168.1.50'            exit 1
    TV_HOST=http://192.168.1.50:5555   -> could not reach http://192.168.1.50:5555:5555
                                          ([Errno -2] Name or service not known).
                                          Is the TV on and on this network?               exit 2
    TV_HOST=192.168.1.50/              -> the same "could not reach .../ :5555"          exit 2
    TV_HOST=192.168.1.50:5555/         -> not a port number: '5555/'                     exit 1

The odd ones out name a bug in the port; the middle two send the owner to the TV, the Wi-Fi and the
router for a mistake in the argument. Neither a scheme nor a path is part of an address, so tv.py now
says which one it was given, before the port is read and before anything reaches the network. It
answers 2, the status tv.py keeps for a run refused before anything ran on the TV - and the one
tools/manage.py reads as "nothing was asked of the TV" rather than a command the TV refused.

What this file pins: those four command lines, the address shapes that must still be taken as they are
(the IPv6 in brackets, a host name, IP:port), and the two refusals that already named their own
mistake and keep their own words and status.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import sys
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"


def load_tv(env):
    """tv.py imported with a stand-in adb_shell, as tests/test_tv_target.py does, so no TV is involved."""
    stubs = {name: types.ModuleType(name) for name in ("adb_shell", "adb_shell.auth")}
    device = types.ModuleType("adb_shell.adb_device")
    device.AdbDeviceTcp = object
    keygen = types.ModuleType("adb_shell.auth.keygen")
    keygen.keygen = lambda path: None
    signer = types.ModuleType("adb_shell.auth.sign_pythonrsa")
    signer.PythonRSASigner = lambda pub, priv: None
    stubs.update({"adb_shell.adb_device": device, "adb_shell.auth.keygen": keygen,
                  "adb_shell.auth.sign_pythonrsa": signer})
    spec = importlib.util.spec_from_file_location("cyt_tv_address_test", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    clean_env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    clean_env.update(env)
    with mock.patch.dict(os.environ, clean_env, clear=True), mock.patch.dict(sys.modules, stubs):
        spec.loader.exec_module(module)
    return module


def refused(host):
    """(what the run printed, its exit status) for TV_HOST `host`, or (None, None) when it was taken.

    A string passed to sys.exit() is the sentence the interpreter prints on stderr and answers with 1.
    """
    printed = io.StringIO()
    try:
        with contextlib.redirect_stderr(printed):
            load_tv({"TV_HOST": host})
    except SystemExit as stop:
        if isinstance(stop.code, str):
            return printed.getvalue() + stop.code, 1
        return printed.getvalue(), stop.code
    return None, None


def refusal(test, host):
    """What a run refused for this address printed, failing the test when it was taken instead."""
    printed, status = refused(host)
    test.assertIsNotNone(printed, f"TV_HOST={host!r} was taken: nothing named the mistake")
    return printed, status


class UrlOrPathTest(unittest.TestCase):
    """The four command lines above, each refused by name, in tv.py's own words, before any connection."""

    def test_a_url_is_named(self):
        printed, status = refusal(self, "http://tv.example")
        self.assertIn("that is a URL, not the TV's address", printed)
        self.assertIn("TV_HOST='http://tv.example'", printed)
        self.assertIn("Pass the address alone", printed)
        self.assertIn("192.168.1.50", printed)          # the form that works, in the same sentence
        self.assertEqual(2, status)

    def test_a_url_with_a_port_is_named(self):
        printed, status = refusal(self, "http://tv.example:5555")
        self.assertIn("that is a URL, not the TV's address", printed)
        self.assertNotIn("not a port number", printed)
        self.assertNotIn("could not reach", printed)    # the TV is not blamed for it
        self.assertEqual(2, status)

    def test_a_trailing_slash_is_named(self):
        printed, status = refusal(self, "tv.example/")
        self.assertIn("that is a path, not the TV's address", printed)
        self.assertNotIn("could not reach", printed)
        self.assertEqual(2, status)

    def test_a_port_with_a_trailing_slash_is_named_as_a_path(self):
        printed, status = refusal(self, "tv.example:5555/")
        self.assertIn("that is a path, not the TV's address", printed)
        self.assertNotIn("not a port number: '5555/'", printed)
        self.assertEqual(2, status)

    def test_both_shapes_are_named_whatever_the_scheme_or_brackets(self):
        for host, said in (("https://tv.example", "URL"),
                           ("https://[fe80::1]:5555", "URL"),
                           ("[fe80::1]:5555/", "path"),
                           ("/6/system", "path")):
            printed, status = refusal(self, host)
            self.assertIn(f"that is a {said}, not the TV's address", printed, host)
            self.assertEqual(2, status, host)


class AddressShapesTest(unittest.TestCase):
    """address_problem() answers only the two shapes a real address never has."""

    def test_the_shapes_it_names(self):
        tv = load_tv({"TV_HOST": "192.0.2.1"})
        self.assertEqual("that is a URL, not the TV's address", tv.address_problem("http://192.0.2.1"))
        self.assertEqual("that is a URL, not the TV's address",
                         tv.address_problem("https://[fe80::1]:5555"))
        self.assertEqual("that is a path, not the TV's address", tv.address_problem("192.0.2.1/"))
        self.assertEqual("that is a path, not the TV's address", tv.address_problem("/6/system"))

    def test_the_shapes_it_leaves_alone(self):
        tv = load_tv({"TV_HOST": "192.0.2.1"})
        for address in ("192.0.2.1", "192.0.2.1:5555", "192.0.2.1:39482", "tv.example",
                        "tv.example:5555", "[fe80::1]:5555", "[fe80::1]", "::1", "tv.example.",
                        "tv-box.local"):
            self.assertIsNone(tv.address_problem(address), address)

    def test_every_shape_it_leaves_alone_is_still_read(self):
        for host, expected in (("tv.example", ("tv.example", 5555)),
                               ("tv.example:39482", ("tv.example", 39482)),
                               ("[fe80::1]:5555", ("fe80::1", 5555)),
                               ("192.0.2.1", ("192.0.2.1", 5555))):
            tv = load_tv({"TV_HOST": host})
            self.assertEqual(expected, (tv.HOST, tv.PORT), host)

    def test_an_endpoint_port_still_beats_tv_port(self):
        tv = load_tv({"TV_HOST": "tv.example:39482", "TV_PORT": "15555"})
        self.assertEqual(("tv.example", 39482), (tv.HOST, tv.PORT))


class UnchangedRefusalsTest(unittest.TestCase):
    """The two answers that already named their own mistake keep their words and their status."""

    def test_no_host_still_asks_for_one(self):
        printed, status = refusal(self, "")
        self.assertIn("Set TV_HOST to your TV's IP address", printed)
        self.assertEqual(1, status)

    def test_a_port_that_is_not_a_number_keeps_its_words(self):
        printed, status = refusal(self, "tv.example:not-a-port")
        self.assertIn("not a port number: 'not-a-port'", printed)
        self.assertEqual(1, status)

    def test_a_port_out_of_range_keeps_its_words(self):
        printed, status = refusal(self, "tv.example:70000")
        self.assertIn("not a port number: '70000'", printed)
        self.assertEqual(1, status)


if __name__ == "__main__":
    unittest.main()

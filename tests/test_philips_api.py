"""tools/philips-api.py finds the JointSpace API on 1926 before 1925, and says which package to check.

On a 77OLED806/12 (Android TV 11) port 1925 is open and answers nothing, while
https://<tv>:1926/6/system answers without pairing. The tool must report the API from 1926, count a 401
on the other endpoints as present (they wait for pairing), and name org.droidtv.xtv when a port answered
and it is not the API. No TV and no network: the HTTP calls are replaced with a fake.

The other half is telling a TV this run reached from one it never did. A port that refuses the
connection is the set answering - nothing listens there, which is what a disabled org.droidtv.xtv looks
like - while a timeout, a "No route to host" or a name that does not resolve is the network between here
and the TV. A set that is asleep, at another address or behind a VPN's firewall leaves both ports silent,
and the run answered that with the advice and exit 1 it uses for a TV it did reach, so an owner whose set
was simply asleep went looking for a package nothing had touched. That shape exits 2 now and says
nothing was learned; it is the rule tools/adb-closed.py and docs/TROUBLESHOOTING.md keep for port 5555 -
only a refused connection is the TV speaking.

An address is the TV's own, not a TV_HOST written IP:port or a URL pasted from a browser: the two ports
below are added here, so `192.168.1.50:1926` built `https://192.168.1.50:1926:1926/6/system`, a name that
does not resolve, and the run answered "nothing answered, the set may be off" over an argument it could
not use. The same happens to the rest of what a colon can carry - `tv.example:`, `tv.example:abc` and a
bare `fe80::1` - so every shape is named before a URL is asked, with the address alone to pass (a bare
IPv6 with its brackets, which is the form tools/tv.py documents).

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import errno
import importlib.util
import io
import json
import socket
import ssl
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("philips_api", ROOT / "tools" / "philips-api.py")
philips_api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(philips_api)

SYSTEM = json.dumps({
    "name": "77OLED806/12",
    "api_version": {"Major": 6, "Minor": 1, "Patch": 0},
    "featuring": {"systemfeatures": {"pairing_type": "digest_auth_pairing"},
                  "jsonfeatures": {"ambilight": ["LoungeLight", "Hue", "Ambilight", "HueStreaming"]}},
})

# What a port raises, in the shapes this file needs: the set's own refusal, a network that never
# reached it, and a name that does not resolve.
REFUSED = ConnectionRefusedError(errno.ECONNREFUSED, "Connection refused")
TIMEOUT = TimeoutError("timed out")
NO_ROUTE = OSError(errno.EHOSTUNREACH, "No route to host")
NO_NAME = socket.gaierror(getattr(socket, "EAI_NONAME", -2), "Name or service not known")


def run(answers, argv=("philips-api.py", "192.168.1.50")):
    """(exit code, stdout, urls asked, stderr), with get() answering from {url: (status, body, failure)};
    a URL the test does not answer times out, so a silent TV is the default."""
    asked = []

    def fake_get(url, timeout=8):
        asked.append(url)
        return answers.get(url, (None, "timed out", TIMEOUT))

    out, err = io.StringIO(), io.StringIO()
    with mock.patch.object(philips_api, "get", fake_get), contextlib.redirect_stdout(out), \
            contextlib.redirect_stderr(err):
        code = philips_api.main(list(argv))
    return code, out.getvalue(), asked, err.getvalue()


class PhilipsApi(unittest.TestCase):
    def test_the_api_on_1926_is_found_and_401_counts_as_there(self):
        base = "https://192.168.1.50:1926/6/"
        answers = {base + "system": (200, SYSTEM, None)}
        answers.update({base + p: (401, "", None) for p in philips_api.PROBE})
        code, out, asked, _ = run(answers)
        self.assertEqual(code, 0)
        self.assertIn("API on https://192.168.1.50:1926", out)
        self.assertIn("6.1.0", out)
        self.assertIn("LoungeLight", out)
        self.assertNotIn("MISSING", out)
        self.assertFalse([u for u in asked if ":1925/" in u], "1925 was tried although 1926 answered")

    def test_an_empty_1925_is_not_taken_for_the_api(self):
        # A 200 with no /6/system object is still the set answering, so the package to check is named.
        answers = {"http://192.168.1.50:1925/6/system": (200, "", None)}
        code, out, _, _ = run(answers)
        self.assertEqual(code, 1)
        self.assertIn("org.droidtv.xtv", out)

    def test_a_page_that_is_not_the_api_falls_back_to_1925(self):
        base = "http://192.168.1.50:1925/6/"
        answers = {"https://192.168.1.50:1926/6/system": (200, "<html>login</html>", None),
                   base + "system": (200, SYSTEM, None)}
        answers.update({base + p: (401, "", None) for p in philips_api.PROBE})
        code, out, _, _ = run(answers)
        self.assertEqual(code, 0)
        self.assertIn("API on http://192.168.1.50:1925", out)
        self.assertIn("(not the API)", out)

    def test_json_that_is_not_a_system_object_is_no_api(self):
        for body in ("{}", "null", "[]", '{"api_version": "6"}', "{broken"):
            with self.subTest(body=body):
                code, out, _, _ = run({"https://192.168.1.50:1926/6/system": (200, body, None)})
                self.assertEqual(code, 1)
                self.assertIn("org.droidtv.xtv", out)

    def test_wrong_typed_feature_metadata_counts_as_unknown(self):
        system = json.dumps({"api_version": {"Major": 6, "Minor": 1, "Patch": 0},
                             "featuring": {"systemfeatures": [], "jsonfeatures": {"ambilight": 5}}})
        code, out, _, _ = run({"https://192.168.1.50:1926/6/system": (200, system, None)})
        self.assertEqual(code, 0)
        self.assertIn("6.1.0", out)
        self.assertNotIn("LoungeLight", out)

    def test_no_address_is_a_usage_error(self):
        with mock.patch.dict("os.environ", {"TV_HOST": ""}):
            code, _, asked, _ = run({}, argv=("philips-api.py",))
        self.assertEqual(code, 2)
        self.assertEqual(asked, [])


class ASilentTVIsNotADisabledPackage(unittest.TestCase):
    """Nothing answered on either port: the run learned nothing, so it must not send the owner to the
    package list - the shape a set that is asleep, at another address or behind a VPN produces."""

    def test_a_timeout_on_both_ports_is_exit_2(self):
        code, out, asked, err = run({})
        self.assertEqual(code, 2)
        self.assertEqual(len(asked), 2, "both ports have to be tried before giving up")
        # The two lines naming the ports are all that was learned; no advice and no verdict.
        self.assertTrue(all(line.startswith("  ") for line in out.splitlines()), out)
        self.assertIn("could not check 192.168.1.50", err)
        self.assertIn("nothing answered on 1926 and 1925", err)
        self.assertIn("off, asleep", err)
        self.assertNotIn("No JointSpace API", err + out)
        self.assertNotIn("still enabled", err + out)

    def test_a_name_that_does_not_resolve_is_exit_2(self):
        answers = {f"{scheme}://no-such-set.invalid:{port}/6/system":
                   (None, "Name or service not known", NO_NAME)
                   for port, scheme in philips_api.PORTS}
        code, out, _, err = run(answers, argv=("philips-api.py", "no-such-set.invalid"))
        self.assertEqual(code, 2)
        self.assertNotIn("No JointSpace API", out)
        self.assertIn("could not check no-such-set.invalid", err)

    def test_no_route_to_the_tv_is_exit_2(self):
        answers = {f"{scheme}://192.168.1.51:{port}/6/system": (None, "No route to host", NO_ROUTE)
                   for port, scheme in philips_api.PORTS}
        code, _, _, err = run(answers, argv=("philips-api.py", "192.168.1.51"))
        self.assertEqual(code, 2)
        self.assertIn("could not check 192.168.1.51", err)

    def test_an_address_this_computer_cannot_connect_to_is_not_an_answer(self):
        # A bracketed link-local IPv6 with no zone index (or an address family this computer does not
        # have) is refused by the local socket layer with EINVAL, before any packet goes out. It came
        # back as the TV answering, so `TV_HOST=[fe80::1]` - the form the address guard now asks for -
        # named org.droidtv.xtv as a package to check over an argument the kernel would not send.
        invalid = {f"{scheme}://[fe80::1]:{port}/6/system":
                   (None, "Invalid argument", OSError(errno.EINVAL, "Invalid argument"))
                   for port, scheme in philips_api.PORTS}
        code, out, _, err = run(invalid, argv=("philips-api.py", "[fe80::1]"))
        self.assertEqual(code, 2)
        self.assertIn("could not check [fe80::1]", err)
        self.assertIn("nothing answered on 1926 and 1925", err)
        self.assertNotIn("No JointSpace API", err + out)
        self.assertNotIn("still enabled", err + out)
        self.assertNotIn("org.droidtv.xtv is still", err + out)

    def test_a_refused_port_is_the_tv_answering(self):
        # Nothing listens on 1925, which is what a disabled org.droidtv.xtv looks like: the set itself
        # sent the reset, so the package is worth naming even though 1926 stayed silent.
        answers = {"http://192.168.1.50:1925/6/system": (None, "Connection refused", REFUSED)}
        code, out, _, err = run(answers)
        self.assertEqual(code, 1)
        self.assertIn("org.droidtv.xtv", out)
        self.assertEqual(err, "")

    def test_the_refusal_on_the_https_port_counts_too(self):
        answers = {"https://192.168.1.50:1926/6/system": (None, "Connection refused", REFUSED)}
        code, out, _, _ = run(answers)
        self.assertEqual(code, 1)
        self.assertIn("org.droidtv.xtv", out)


class AnAddressThisCannotProbe(unittest.TestCase):
    """A TV_HOST written as tools/tv.py takes it (IP:port), and a URL pasted from a browser.

    Both reach find_api() as the host, and both build a URL that is not one - the address and a port of
    its own before the 1926 this adds - so the run answered "nothing answered on 1926 and 1925, the set
    may be off" for an argument it could not use, and an owner whose TV_HOST carries the ADB port went
    looking at the TV. Neither shape asks a URL now: the address alone is what the check takes.
    """

    def test_a_tv_host_with_a_port_is_named_before_a_url_is_asked(self):
        with mock.patch.dict("os.environ", {"TV_HOST": "192.168.1.50:1926"}):
            code, out, asked, err = run({}, argv=("philips-api.py",))
        self.assertEqual(code, 2)
        self.assertEqual(asked, [], "a URL built from IP:port was asked: %s" % asked)
        self.assertEqual(out, "")
        self.assertIn("192.168.1.50:1926 carries a port", err)
        self.assertIn("pass the TV's address alone: 192.168.1.50.", err)
        self.assertIn("TV_HOST", err)
        self.assertNotIn("nothing answered", err)
        self.assertNotIn("off, asleep", err)

    def test_an_address_with_a_port_on_the_command_line_is_the_same_mistake(self):
        code, out, asked, err = run({}, argv=("philips-api.py", "192.168.1.50:5555"))
        self.assertEqual(code, 2)
        self.assertEqual(asked, [])
        self.assertEqual(out, "")
        self.assertIn("192.168.1.50:5555 carries a port", err)
        self.assertIn("pass the TV's address alone: 192.168.1.50.", err)

    def test_a_url_is_named_too(self):
        for url in ("http://192.168.1.50", "https://tv.local"):
            with self.subTest(url=url):
                code, _, asked, err = run({}, argv=("philips-api.py", url))
                self.assertEqual(code, 2)
                self.assertEqual(asked, [])
                self.assertIn(f"{url} is a URL, not the TV's address", err)
                self.assertIn("without http:// or https://", err)

    def test_an_address_with_colons_that_is_not_a_port_is_still_probed(self):
        # A bracketed IPv6 address has colons and no port: the guard must leave it to find_api().
        code, _, asked, _ = run({}, argv=("philips-api.py", "[fe80::1]"))
        self.assertEqual(code, 2)
        self.assertEqual(["https://[fe80::1]:1926/6/system", "http://[fe80::1]:1925/6/system"], asked)

    def test_a_colon_with_nothing_after_it_is_named(self):
        code, out, asked, err = run({}, argv=("philips-api.py", "tv.example:"))
        self.assertEqual(code, 2)
        self.assertEqual(asked, [], "a URL built from a trailing colon was asked: %s" % asked)
        self.assertEqual(out, "")
        self.assertIn("tv.example: ends in a colon with nothing after it", err)
        self.assertIn("pass the TV's address alone: tv.example.", err)
        self.assertNotIn("nothing answered", err)
        self.assertNotIn("off, asleep", err)

    def test_a_port_that_is_not_a_number_is_named(self):
        # TV_HOST=192.168.1.50:abc is the shape tools/tv.py refuses as `not a port number`, so an owner
        # who mistyped it there had no reason to read this check's answer as the argument's fault either.
        for host in ("tv.example:abc", "tv.example:1926x", "192.168.1.50: ", "tv.example:-1"):
            with self.subTest(host=host):
                code, out, asked, err = run({}, argv=("philips-api.py", host))
                self.assertEqual(code, 2)
                self.assertEqual(asked, [])
                self.assertEqual(out, "")
                self.assertIn(f"what follows the colon in {host} is", err)
                self.assertIn("not a port number", err)
                self.assertNotIn("off, asleep", err)

    def test_an_ipv6_address_written_without_brackets_is_named(self):
        # `https://fe80::1:1926/6/system` is not a URL: the address has to be bracketed before a port
        # can follow it, which is the form tools/tv.py documents for IPv6 endpoints.
        code, out, asked, err = run({}, argv=("philips-api.py", "fe80::1"))
        self.assertEqual(code, 2)
        self.assertEqual(asked, [])
        self.assertEqual(out, "")
        self.assertIn("fe80::1 is an IPv6 address written without brackets", err)
        self.assertIn("pass it with brackets", err)
        self.assertIn("[fe80::1]", err)
        self.assertNotIn("off, asleep", err)

    def test_the_guard_reads_only_a_port_after_the_address(self):
        for host in ("192.168.1.50", "192.168.1.50.", "tv.local", "[fe80::1]", "tv-1.local"):
            with self.subTest(host=host):
                self.assertIsNone(philips_api.address_problem(host))
        for host in ("192.168.1.50:1926", "tv.local:5555", "[fe80::1]:1926"):
            with self.subTest(host=host):
                self.assertIn("carries a port", philips_api.address_problem(host))
        for host in ("http://192.168.1.50", "https://tv.local", "HTTP://192.168.1.50"):
            with self.subTest(host=host):
                self.assertIn("is a URL", philips_api.address_problem(host))
        self.assertIn("pass the TV's address alone: [fe80::1].",
                      philips_api.address_problem("[fe80::1]:1926"))

    def test_the_splitter_keeps_a_bracketed_address_whole(self):
        self.assertEqual(("[fe80::1]", None), philips_api.split_address("[fe80::1]"))
        self.assertEqual(("[fe80::1]", "1926"), philips_api.split_address("[fe80::1]:1926"))
        self.assertEqual(("tv.local", "5555"), philips_api.split_address("tv.local:5555"))
        self.assertEqual(("tv.local", ""), philips_api.split_address("tv.local:"))
        self.assertEqual(("fe80::1", None), philips_api.split_address("fe80::1"))
        self.assertEqual(("192.168.1.50", None), philips_api.split_address("192.168.1.50"))


class Answered(unittest.TestCase):
    """The one judgement the exit code rests on, over the exceptions the socket layer hands over."""

    def test_a_refusal_is_an_answer(self):
        for failure in (REFUSED, urllib.error.URLError(REFUSED), ssl.SSLError("WRONG_VERSION_NUMBER"),
                        ConnectionResetError(errno.ECONNRESET, "Connection reset")):
            with self.subTest(failure=type(failure).__name__ + str(getattr(failure, "reason", ""))):
                self.assertTrue(philips_api.answered(failure))

    def test_never_reaching_the_tv_is_not(self):
        for failure in (TIMEOUT, urllib.error.URLError(TIMEOUT), NO_ROUTE, urllib.error.URLError(NO_ROUTE),
                        NO_NAME, urllib.error.URLError(NO_NAME),
                        PermissionError(errno.EACCES, "Permission denied"),
                        urllib.error.URLError(PermissionError(errno.EPERM, "Operation not permitted")),
                        OSError(errno.EINVAL, "Invalid argument"),
                        urllib.error.URLError(OSError(errno.EINVAL, "Invalid argument"))):
            with self.subTest(failure=type(failure).__name__ + str(getattr(failure, "reason", ""))):
                self.assertFalse(philips_api.answered(failure))

    def test_an_answer_is_not_a_failure(self):
        self.assertFalse(philips_api.answered(None))


if __name__ == "__main__":
    unittest.main()

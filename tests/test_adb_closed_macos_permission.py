"""tools/adb-closed.py names macOS Local Network privacy when EHOSTUNREACH hides the port.

The closing step accepts only this tool's "closed" (PROMPT.md, AGENTS.md, the README FAQ), so it is the
one check an owner is told to trust. On macOS the kernel reports a process that Local Network privacy
keeps off the subnet as EHOSTUNREACH - "No route to host", the same errno as a TV that is switched off
or on another network. tools/tv.py has named the permission at connect time since #1982; this is the
other client, and until this branch it answered

    could not check 192.0.2.1:5555 ([Errno 65] No route to host). Is the TV on and on this network?

for a network that was never broken, while the port it was asked about may be open (ADB still on) or
closed. Exit 2 was already right - the check could not tell - but the sentence sent the reader to the
router and to the TV instead of to System Settings, which is the hour #1982's reporter lost.

The errno and the platform are the two things the script reads, so the runs here supply exactly those:
socket.create_connection raises the error, sys.platform says darwin (or linux). Both are faked rather
than skipped, unlike tests/test_tv_connect_errors.py's macOS test, because neither value is read for
anything else in this script: the same OSError branch, the same comparison.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import ast
import contextlib
import errno
import importlib.util
import io
import os
from pathlib import Path
import socket
import sys
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"
TV = "192.0.2.1"          # a documentation address; nothing on the network is asked
NOTE = "On macOS this is also what Local Network privacy looks like."


def load():
    spec = importlib.util.spec_from_file_location("cyt_adb_closed_platform", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def run(error, platform="darwin", args=(TV,)):
    """main() with socket.create_connection raising ERROR, on PLATFORM: (code, stdout, stderr)."""
    module = load()
    out, err = io.StringIO(), io.StringIO()
    env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    argv = sys.argv
    sys.argv = ["adb-closed.py", *args]
    try:
        with mock.patch.dict(os.environ, env, clear=True), \
                mock.patch.object(socket, "create_connection", side_effect=error), \
                mock.patch.object(sys, "platform", platform), \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = module.main()
    finally:
        sys.argv = argv
    return code, out.getvalue(), err.getvalue()


class MacOsPermissionTest(unittest.TestCase):
    def test_no_route_to_host_on_macos_names_the_permission(self):
        code, out, err = run(OSError(errno.EHOSTUNREACH, "No route to host"))
        self.assertEqual(code, 2, out + err)
        self.assertEqual(out, "")
        self.assertIn(f"could not check {TV}:5555 ([Errno {errno.EHOSTUNREACH}] No route to host). "
                      "Is the TV on and on this network?", err)
        self.assertIn(NOTE, err)
        # The three things a reader gets wrong: which app to allow, that another client would not help,
        # and whether the network is at fault.
        self.assertIn("System Settings > Privacy & Security > Local Network", err)
        self.assertIn("Terminal, iTerm2, VS Code", err)
        self.assertIn("granting it to python or adb does nothing", err)
        self.assertIn("try the default gateway", err)
        # Exit 2 already said the check could not tell; the sentence has to say it too, or the closing
        # step reads "could not check ... Is the TV on?" as a port that is probably closed.
        self.assertIn("Nothing was learned about the port.", err)
        self.assertNotIn("Traceback", err)
        self.assertNotIn("is closed", err)
        self.assertNotIn("still open", err)

    def test_the_port_it_was_given_is_named_on_macos_too(self):
        code, out, err = run(OSError(errno.EHOSTUNREACH, "No route to host"), args=(f"{TV}:5877",))
        self.assertEqual(code, 2, out + err)
        self.assertIn(f"could not check {TV}:5877 ([Errno {errno.EHOSTUNREACH}]", err)
        self.assertIn(NOTE, err)

    @unittest.skipIf(sys.platform == "darwin", "this is the run for the other platforms")
    def test_another_platform_keeps_the_plain_message(self):
        code, out, err = run(OSError(errno.EHOSTUNREACH, "No route to host"), platform="linux")
        self.assertEqual(code, 2, out + err)
        self.assertEqual(out, "")
        self.assertIn("Is the TV on and on this network?", err)
        self.assertNotIn("Local Network privacy", err)
        self.assertNotIn("System Settings", err)

    def test_a_network_that_is_really_unreachable_is_not_the_permission(self):
        # ENETUNREACH is this computer having no route at all, which is what the plain sentence is for.
        code, out, err = run(OSError(errno.ENETUNREACH, "Network is unreachable"))
        self.assertEqual(code, 2, out + err)
        self.assertIn("Is the TV on and on this network?", err)
        self.assertNotIn(NOTE, err)

    def test_a_timeout_on_macos_is_not_the_permission_either(self):
        # A TV that is off, asleep or on another VLAN never answers; the permission denies the subnet and
        # answers with EHOSTUNREACH. The timeout branch has its own, longer answer.
        code, out, err = run(socket.timeout("timed out"))
        self.assertEqual(code, 2, out + err)
        self.assertIn("it did not answer in 3 s, and that proves nothing", err)
        self.assertNotIn(NOTE, err)

    def test_a_refusal_on_macos_is_still_closed(self):
        # The note must not cost the one answer the closing step waits for.
        code, out, err = run(ConnectionRefusedError(61, "Connection refused"))
        self.assertEqual(code, 0, out + err)
        self.assertEqual(out, f"{TV}:5555 is closed: the TV refused the connection.\n")
        self.assertNotIn("Local Network privacy", err)


class TheTwoClientsAgreeTest(unittest.TestCase):
    def test_both_name_the_same_permission_and_the_same_way_to_grant_it(self):
        # tools/tv.py explains the permission at connect time (#1982); this tool is what the closing step
        # trusts. A reader who is told two different things about one permission has to guess which is so.
        def sentences(path):
            """Every string literal in the file, joined: the sentences as a reader gets them, wrapped or not."""
            tree = ast.parse(path.read_text(encoding="utf-8"))
            return " ".join(node.value for node in ast.walk(tree)
                            if isinstance(node, ast.Constant) and isinstance(node.value, str))

        closed = sentences(SCRIPT)
        tv = sentences(ROOT / "tools" / "tv.py")
        for phrase in (NOTE, "System Settings > Privacy & Security > Local Network",
                       "Terminal, iTerm2, VS Code", "granting it to python or adb does nothing",
                       "try the default gateway"):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, closed)
                self.assertIn(phrase, tv)

    def test_the_note_is_gated_on_macos_and_on_that_one_errno(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("import errno", source)
        gate = 'if sys.platform == "darwin" and e.errno == errno.EHOSTUNREACH:'
        self.assertIn(gate, source)
        # Inside the OSError branch, which is the one that answers "the TV was not reached".
        self.assertLess(source.index("except OSError as e:"), source.index(gate))
        self.assertLess(source.index(gate), source.index('Is the TV on and on this network?'))

    def test_the_docstring_says_what_the_exit_status_still_means(self):
        doc = " ".join(SCRIPT.read_text(encoding="utf-8").split('"""')[1].split())
        self.assertIn("Only the TV's own refusal shows the port closed.", doc)
        self.assertIn("Local Network privacy", doc)
        self.assertIn("`EHOSTUNREACH`", doc)
        self.assertIn("cannot tell an open port from a closed one", doc)

    def test_the_macos_bullet_sends_the_closing_check_too(self):
        # The page's macOS entry is where an owner reads what the two clients do with this error; it named
        # tools/tv.py and the stock adb only, while the closing step trusts this tool alone.
        page = " ".join((ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8").split())
        start = page.index("macOS: \"No route to host\"")
        bullet = page[start:page.index("- **No \"wireless debugging\" toggle**", start)]
        self.assertIn("tools/adb-closed.py", bullet)
        self.assertIn("could not check <ip>:5555 ([Errno 65] No route to host)", bullet)
        self.assertIn("exit 2", bullet)
        self.assertIn("only a \"closed\" from a granted terminal counts", bullet)


if __name__ == "__main__":
    unittest.main()

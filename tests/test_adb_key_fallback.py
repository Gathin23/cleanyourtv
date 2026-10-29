"""The Connecting page says which ADB keys `tools/tv.py` offers, and which one the prompt asks for.

`tools/tv.py` keeps a key of its own in `tools/adbkey/`. While it has not made one it uses the stock
`~/.android/adbkey` alone (`if not os.path.exists(KEY) and os.path.exists(ADB_KEY)`); once it has, it
offers its own key first and the stock one after it. It used to offer only its own, so a set that already
trusted the stock key was asked for one the stock `adb` never registered: #1526 is a TCL 55C61B whose
`tv.py` had made its own key in `tools/adbkey` before the TV approved the stock adb key, and the set stayed
`unauthorized` until that folder was deleted. adb_shell signs the TV's token with each key in turn and
sends the first key's public half (the "Allow USB debugging?" prompt) only when none is accepted, so the
second key adds no prompt and the prompt still asks for tv.py's own key.

Both halves are pinned here: what the code offers (a throwaway tree with a stub adb_shell that records
every key handed to the TV, in order, so no adb_shell and no TV are needed) and what the page says. If the
order or the fallback changes, these tests fail and the bullet has to be rewritten rather than left
describing the old rule. The page's PowerShell command is pinned too: `rmdir /s /q` is Command Prompt
syntax, and in PowerShell `rmdir` is an alias of Remove-Item, which takes `/s` as the path and stops at
`/q`, leaving the folder in place.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "TROUBLESHOOTING.md"
TVPY = ROOT / "tools" / "tv.py"
SECTION = "## Connecting"

STOCK_PUB = "stock-adb-key"
OWN_PUB = "made-by-tv-py"
GENERATED_PUB = "generated-by-tv.py"
NOT_A_KEY = "not a key"

# Enough of adb_shell for tv.py to import; connect() records the keys it was handed, one public half per
# line in the order adb_shell would try them, instead of dialling.
STUB_DEVICE = (
    "import os\n"
    "class AdbDeviceTcp:\n"
    "    def __init__(self, host, port, **kwargs):\n"
    "        self.host, self.port = host, port\n"
    "    def connect(self, rsa_keys=None, **kwargs):\n"
    "        with open(os.environ['KEY_OUT'], 'w') as f:\n"
    "            f.write('\\n'.join(key.pub for key in rsa_keys))\n"
    "    def shell(self, cmd, **kwargs):\n"
    "        return 'x'\n"
    "    def close(self):\n"
    "        pass\n"
)
# The real signer parses the private key when it is made, and raises ValueError for one it cannot read.
STUB_SIGNER = (
    "class PythonRSASigner:\n"
    "    def __init__(self, pub, priv):\n"
    "        if priv == '" + NOT_A_KEY + "':\n"
    "            raise ValueError('Not a DER-encoded OpenSSL private RSA key')\n"
    "        self.pub, self.priv = pub, priv\n"
)
STUB_KEYGEN = (
    "def keygen(filepath):\n"
    "    with open(filepath, 'wb') as f:\n"
    "        f.write(b'private')\n"
    "    with open(filepath + '.pub', 'wb') as f:\n"
    "        f.write(b'" + GENERATED_PUB + "')\n"
)


def connecting(text=None):
    """The Connecting section of the page, or an empty string when the heading is gone."""
    text = PAGE.read_text(encoding="utf-8") if text is None else text
    start = text.find(SECTION)
    if start == -1:
        return ""
    rest = text[start + len(SECTION):]
    end = rest.find("\n## ")
    return rest if end == -1 else rest[:end]


def bullets(section):
    """The section's `- ` bullets with their wrapped lines joined onto the first one.

    A bullet continues on the indented lines under it; anything else - a paragraph, the next heading -
    ends it, so prose that mentions a path is not read as a bullet.
    """
    out = []
    continuing = False
    for line in section.splitlines():
        if line.startswith("- "):
            out.append(line[2:].strip())
            continuing = True
        elif continuing and line.startswith("  ") and line.strip():
            out[-1] += " " + line.strip()
        else:
            continuing = False
    return out


class ReaderTest(unittest.TestCase):
    """The two readers above have to see what the page really lays out."""

    def test_wrapped_bullet_is_one_bullet(self):
        said = bullets("## Connecting\n\n- **a** first line\n  second line\n  third\n\n- b\n")
        self.assertEqual(said, ["**a** first line second line third", "b"])

    def test_prose_outside_a_bullet_is_not_a_bullet(self):
        said = bullets("## Connecting\n\nA paragraph naming `tools/adbkey` and `~/.android/adbkey`.\n\n- b\n")
        self.assertEqual(said, ["b"])

    def test_section_stops_at_the_next_heading(self):
        said = connecting("## Connecting\n\n- keep\n\n## Working on the TV\n\n- drop\n")
        self.assertIn("keep", said)
        self.assertNotIn("drop", said)

    def test_a_page_without_the_section_reads_as_empty(self):
        self.assertEqual(connecting("# Nothing here\n\n- drop\n"), "")


class PageTest(unittest.TestCase):
    def test_the_page_still_has_a_connecting_section_to_read(self):
        section = connecting()
        self.assertTrue(section, "docs/TROUBLESHOOTING.md has no '## Connecting' section")
        self.assertGreaterEqual(len(bullets(section)), 10,
                                "the Connecting section read fewer than 10 bullets, so the checks below "
                                "could pass by reading nothing")

    def key_bullet(self):
        said = [b for b in bullets(connecting()) if "tools/adbkey" in b]
        self.assertEqual(len(said), 1, f"{len(said)} bullets name tools/adbkey: {said}")
        return said[0]

    def test_one_bullet_names_the_key_this_client_keeps(self):
        bullet = self.key_bullet()
        for needle, why in (
            ("offers the stock `~/.android/adbkey`", "that tv.py offers the stock key as well as its own"),
            ("unauthorized", "what the TV answers while it is not trusted"),
            ("rm -rf tools/adbkey", "the way to have the prompt register the stock key"),
            ("Remove-Item -Recurse -Force tools\\adbkey", "the same in PowerShell"),
            ("rmdir /s /q tools\\adbkey", "the same in Command Prompt"),
            ("--probe", "the run that registers the stock key"),
            ("tools\\venv\\Scripts\\python.exe -I tools\\tv.py --probe", "that run as Windows starts it"),
            ("#1526", "the report"),
        ):
            self.assertIn(needle, bullet, f"the tools/adbkey bullet does not name {why} ({needle!r})")

    def test_each_windows_command_is_given_for_the_shell_that_runs_it(self):
        # `rmdir /s /q` is Command Prompt syntax. In PowerShell rmdir is an alias of Remove-Item, whose
        # only positional parameter is the path: it takes `/s` as the path and stops at `/q`.
        said = {shell: command for command, shell in
                re.findall(r"`([^`]+)` in (PowerShell|Command Prompt)", self.key_bullet())}
        self.assertEqual(said.get("PowerShell"), "Remove-Item -Recurse -Force tools\\adbkey",
                         "the tools/adbkey bullet gives PowerShell a command it refuses, or none")
        self.assertEqual(said.get("Command Prompt"), "rmdir /s /q tools\\adbkey")

    def test_the_fallback_is_not_described_without_its_condition(self):
        said = [b for b in bullets(connecting()) if "~/.android/adbkey" in b]
        self.assertTrue(said, "nothing in the Connecting section names the stock ~/.android/adbkey")
        for bullet in said:
            self.assertIn("tools/adbkey", bullet,
                          "a bullet names the stock key without tools/adbkey, whose key tv.py offers first "
                          "and the prompt asks for while that folder holds one")


class FallbackTest(unittest.TestCase):
    """What tv.py actually hands the TV, run against a copy of the script in a throwaway tree."""

    def probe(self, own_key=False, stock_key=False, stock_pub=True, stock_priv="private", stock_mode=None):
        """Run `tv.py --probe` in a tree of its own; return (result, the public keys offered to the TV, in
        order). The stock key is left at self.stock_key."""
        tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, tmp, ignore_errors=True)

        tools = tmp / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(TVPY, tools / "tv.py")

        stub = tmp / "stub" / "adb_shell"
        (stub / "auth").mkdir(parents=True)
        (stub / "__init__.py").write_text("")
        (stub / "adb_device.py").write_text(STUB_DEVICE)
        (stub / "auth" / "__init__.py").write_text("")
        (stub / "auth" / "keygen.py").write_text(STUB_KEYGEN)
        (stub / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER)

        home = tmp / "home"
        (home / ".android").mkdir(parents=True)
        self.stock_key = home / ".android" / "adbkey"
        if stock_key:
            self.stock_key.write_text(stock_priv)
            if stock_mode is not None:
                os.chmod(self.stock_key, stock_mode)
            if stock_pub:
                (home / ".android" / "adbkey.pub").write_text(STOCK_PUB)
        if own_key:
            (tools / "adbkey").mkdir()
            (tools / "adbkey" / "adbkey").write_text("private")
            (tools / "adbkey" / "adbkey.pub").write_text(OWN_PUB)

        out = tmp / "key-out.txt"
        # Windows expands `~` from USERPROFILE, not HOME: without it tv.py reads the real
        # ~/.android and generates its own key, so "the stock key wins" is never observed there.
        home_var = {"USERPROFILE": str(home)} if os.name == "nt" else {}
        env = dict(os.environ, TV_HOST="127.0.0.1", HOME=str(home), **home_var,
                   KEY_OUT=str(out),
                   PYTHONPATH=os.pathsep.join([str(tmp / "stub"), str(tools)]))
        result = subprocess.run([sys.executable, str(tools / "tv.py"), "--probe"],
                                cwd=str(tmp), env=env, capture_output=True, text=True, timeout=120)
        offered = out.read_text().splitlines() if out.exists() else []
        return result, offered

    def test_the_stock_key_is_used_while_the_client_has_none(self):
        result, offered = self.probe(stock_key=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(offered, [STOCK_PUB],
                         "tv.py did not reuse ~/.android/adbkey alone, so the prompt would not register the "
                         "key platform-tools uses")

    def test_the_stock_key_is_offered_after_a_key_of_its_own(self):
        # The state #1526 was stuck in: tools/adbkey held a key, and the TV trusted only the stock one.
        result, offered = self.probe(own_key=True, stock_key=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(STOCK_PUB, offered,
                      "tv.py does not offer ~/.android/adbkey once tools/adbkey holds a key, so a set that "
                      "trusts only the stock key stays unauthorized (#1526)")
        self.assertEqual(offered, [OWN_PUB, STOCK_PUB],
                         "tv.py's own key is not offered first, so the prompt asks for another key than the "
                         "Connecting bullet says")

    def test_its_own_key_alone_without_a_whole_stock_key(self):
        for case in ({}, {"stock_key": True, "stock_pub": False}):
            with self.subTest(**case):
                result, offered = self.probe(own_key=True, **case)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(offered, [OWN_PUB])

    def test_a_stock_key_it_cannot_read_does_not_stop_the_connection(self):
        result, offered = self.probe(own_key=True, stock_key=True, stock_priv=NOT_A_KEY)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(offered, [OWN_PUB],
                         "a ~/.android/adbkey adb_shell cannot parse stopped a connection tv.py's own key "
                         "could make")

    @unittest.skipIf(os.name == "nt", "POSIX permission bits; the chmod is a no-op on Windows")
    def test_the_stock_key_keeps_its_permissions(self):
        # It belongs to the user's own adb: tv.py reads it and restricts only the key in tools/adbkey.
        for case in ({"own_key": True}, {}):
            with self.subTest(**case):
                result, offered = self.probe(stock_key=True, stock_mode=0o644, **case)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(STOCK_PUB, offered)
                self.assertEqual(stat.S_IMODE(self.stock_key.stat().st_mode), 0o644,
                                 "tv.py changed the permissions of ~/.android/adbkey")

    def test_its_own_key_is_made_beside_the_script(self):
        result, offered = self.probe()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(offered, [GENERATED_PUB],
                         "tv.py made no key of its own, so the folder the page tells a reader to delete "
                         "would never exist")

    def test_the_fallback_needs_the_public_half_too(self):
        result, offered = self.probe(stock_key=True, stock_pub=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(offered, [GENERATED_PUB],
                         "tv.py reused a ~/.android/adbkey that has no .pub beside it")


class SourceTest(unittest.TestCase):
    def test_the_fallback_is_still_conditional_on_its_own_key(self):
        source = TVPY.read_text(encoding="utf-8")
        self.assertIn("if not os.path.exists(KEY) and os.path.exists(ADB_KEY)", source,
                      "tv.py's key choice changed: the Connecting bullet's rule has to follow it")
        self.assertIn("KEY = ADB_KEY", source)


if __name__ == "__main__":
    unittest.main()

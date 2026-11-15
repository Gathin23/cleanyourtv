"""No list may disable the core Android packages the system needs to boot (#884).

An agent applied a list to an original Mi TV Stick that disabled the permission controller, the package
installer, ext services/shared, module metadata and the mainline overlays, and the stick never booted
again. tools/disable.sh, tools/disable.ps1 and tools/check.py --fix refuse such a list before the TV is
touched, with no switch to override it, and no list in examples/ may name one of these packages.
The pattern lives in three places (bash, PowerShell, Python); this test keeps them identical.

Ext services and ext shared also ship under AOSP's bare names, android.ext.services and
android.ext.shared, with no com.android. or com.google.android. in front: the package dumps from an OBE
R3 Ultra (Android 11), a CVTE SMART_TV (Android 12) and a MIBOX4 list both, installed and enabled (#1711).

The TV middleware is guarded the same way: com.cltv.mal (MediaTek's MAL), com.mediatek.tis, tv.service and
tv.agent. A Haier H50H5F restarted in a loop after a debloat with com.cltv.mal off (#1578).

The PowerShell case needs `pwsh` on PATH and skips without it; CI's Ubuntu runner has it. It reads the
refusal past the error view `throw` is rendered as - colour, and a `|` gutter on each wrapped line - with
`packed()`, because where that wrap falls follows the length of the path in the message rather than
anything the script did.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import recovery  # noqa: E402

# The packages named in #884, plus the rest of the block.
CORE = [
    "com.google.android.permissioncontroller", "com.android.permissioncontroller",
    "com.google.android.packageinstaller", "com.android.packageinstaller",
    "com.google.android.ext.services", "com.android.ext.services",
    "com.google.android.ext.shared", "com.android.ext.shared",
    "com.google.android.modulemetadata", "com.android.modulemetadata",
    "com.google.android.overlay.modules.ext.services", "com.google.android.overlay.modules.permissioncontroller",
    "com.android.sdksandbox",
    # The GMS build names the same sandbox this way; PackageManager crashed at boot without it (#3881).
    "com.google.android.sdksandbox",
    # A TCL 43S5400A boot-looped into recovery with this one off (#2803).
    "com.android.location.fused",
    "com.android.systemui", "com.android.shell", "com.android.phone",
    "com.android.settings", "com.android.tv.settings",
    "com.android.providers.settings", "com.android.providers.tv", "com.android.providers.media",
    "com.android.providers.downloads",
    # AOSP's bare names for the same two modules (#1711).
    "android.ext.services", "android.ext.shared",
    # TV middleware: a Haier H50H5F restarted in a loop with com.cltv.mal off (#1578).
    "com.cltv.mal", "com.mediatek.tis", "com.mediatek.tv.service", "com.mediatek.tv.agent",
]
# Look alike but are fine: what tested lists disable, and names that only share a prefix.
NOT_CORE = [
    "com.android.providers.calendar", "com.android.providers.contacts",
    "com.android.providers.userdictionary", "com.android.providers.blockednumber",
    "com.android.settings.intelligence", "com.android.tv.settings.overlay", "com.android.phone.extra",
    "com.google.android.tungsten.setupwraith", "com.google.android.apps.tv.launcherx",
    "com.android.shell2", "com.example.android.ext.services",
    "android.ext.services.overlay", "android.ext.sharedlib", "android.extservices",
    # Tested lists turn these off: FAST channels, and the Live TV app the factory disables on some sets.
    "com.cltv.fast", "com.cltv.hybrid", "com.mediatek.tv.settings", "com.mediatek.tis.overlay",
    # Only the exact SDK sandbox is protected for #2557; the other reported modules stay allowed.
    "com.android.adservices.api", "com.android.ondevicepersonalization.services",
    "com.android.federatedcompute.services", "com.android.sdksandbox.overlay",
    "com.android.sdksandbox2", "com.example.sdksandbox", "com.google.android.sdksandbox.overlay",
    # Only the exact fused location package is protected for #2803.
    "com.android.location.fused.overlay", "com.google.android.location.fused",
]


# PowerShell renders a `throw` as an error view rather than as a plain line: ANSI colour on a terminal,
# and the message wrapped at the console width with a `|` gutter down its continuation lines.
ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def packed(text):
    """A PowerShell refusal as one line of words, so an assertion reads what the script said.

    Taking out the whitespace alone joins a wrapped message only while no escape sequence and no gutter
    sits across the break, so whether "android.ext.services, android.ext.shared" reads as one string
    depends on where the wrap falls - and that follows the length of the path inside the message, i.e.
    of the temp directory. Temp directories 26 to 47 characters long in this checkout put the second
    name on the next line and failed the case while `disable.ps1` was refusing exactly as it should; a
    short `/tmp`, which is what CI has, kept both on one line and went green. The escapes and the gutter
    go first here, so the words are all that is left to compare.
    """
    return re.sub(r"\s+|[|│]", "", ANSI.sub("", text))


def script_pattern(path, regex):
    match = re.search(regex, path.read_text(encoding="utf-8"), re.M)
    if not match:
        raise AssertionError(f"no core-package pattern found in {path}")
    return match.group(1)


class CorePackagesTest(unittest.TestCase):
    def test_the_three_copies_of_the_pattern_agree(self):
        sh_re = script_pattern(ROOT / "tools" / "disable.sh", r"^CORE_RE='([^']+)'$")
        sh_ok = script_pattern(ROOT / "tools" / "disable.sh", r"^CORE_OK='([^']+)'$")
        ps_re = script_pattern(ROOT / "tools" / "disable.ps1", r"^\$coreRe = '([^']+)'$")
        ps_ok = script_pattern(ROOT / "tools" / "disable.ps1", r"^\$coreOk = '([^']+)'$")
        self.assertEqual(sh_re, recovery.CORE_RE.pattern)
        self.assertEqual(ps_re, recovery.CORE_RE.pattern)
        self.assertEqual(sh_ok, recovery.CORE_OK.pattern)
        self.assertEqual(ps_ok, recovery.CORE_OK.pattern)

    def test_core_packages_are_blocked(self):
        self.assertEqual(CORE, recovery.core_packages(CORE))

    def test_look_alikes_are_not(self):
        self.assertEqual([], recovery.core_packages(NOT_CORE))

    def test_no_list_in_examples_disables_a_core_package(self):
        found = []
        for path in sorted((ROOT / "examples").glob("*/debloat*.txt")):
            for name in recovery.core_packages(recovery.read_list(path)):
                found.append(f"{path.relative_to(ROOT)}: {name}")
        self.assertEqual([], found, "core Android packages must stay enabled (#884)")

    def test_disable_sh_refuses_before_touching_the_tv(self):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("disable.sh is a POSIX script; it needs bash")
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
            touched = Path(tmp) / "touched"
            # Any call to the TV leaves a mark; the refusal has to come first.
            (tools / "tv.py").write_text(f"open({str(touched)!r}, 'a').write('x')\n")
            python = tools / "venv" / "bin" / "python"
            python.parent.mkdir(parents=True)
            python.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
            python.chmod(0o755)
            listing = Path(tmp) / "debloat.txt"
            listing.write_text("com.example.ads\ncom.google.android.permissioncontroller  # no\n"
                               "com.google.android.overlay.modules.ext.services\n"
                               "android.ext.services\nandroid.ext.shared\n")
            env = dict(os.environ, TV_HOST="192.168.1.50", FORCE_HOME="1", FORCE_SETUPWRAITH="1",
                       TVINPUT_ALLOW="1")
            result = subprocess.run(["bash", str(tools / "disable.sh"), str(listing)], cwd=repo, env=env,
                                    capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=60)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn("#884", result.stderr)
            self.assertIn("com.google.android.permissioncontroller", result.stderr)
            self.assertIn("com.google.android.overlay.modules.ext.services", result.stderr)
            self.assertIn("  android.ext.services\n", result.stderr)
            self.assertIn("  android.ext.shared\n", result.stderr)
            self.assertNotIn("com.example.ads", result.stderr)
            self.assertFalse(touched.exists(), "the TV was contacted before the refusal")

    def test_disable_ps1_refuses_the_bare_names_before_touching_the_tv(self):
        # The same refusal from the PowerShell twin, for the two names #1711 added. pwsh is not on
        # every contributor's machine, so this runs where it is (CI's Ubuntu runner).
        pwsh = shutil.which("pwsh")
        if not pwsh:
            raise unittest.SkipTest("disable.ps1 needs pwsh on PATH")
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.ps1", tools / "disable.ps1")
            touched = Path(tmp) / "touched"
            (tools / "tv.py").write_text(f"open({str(touched)!r}, 'a').write('x')\n")
            python = tools / "venv" / "Scripts" / "python.exe"
            python.parent.mkdir(parents=True)
            python.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
            python.chmod(0o755)
            listing = Path(tmp) / "debloat.txt"
            listing.write_text("com.example.ads\nandroid.ext.services  # no\nandroid.ext.shared\n")
            result = subprocess.run([pwsh, "-NoProfile", "-File", str(tools / "disable.ps1"),
                                     "-TvHost", "192.168.1.50", str(listing)], cwd=repo,
                                    capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=120)
            output = result.stdout + result.stderr
            self.assertNotEqual(0, result.returncode, output)
            # PowerShell's error view can wrap a long message at the console width, so the names are
            # looked for with every whitespace taken out - and with the colour and the gutter the wrap
            # adds taken out first, since those sit across the break when the wrap falls there.
            refusal = packed(output)
            self.assertIn("#884", refusal)
            self.assertIn("android.ext.services,android.ext.shared", refusal)
            self.assertFalse(touched.exists(), "the TV was contacted before the refusal")

    def test_check_py_refuses_the_bare_names_before_connecting(self):
        with tempfile.TemporaryDirectory() as tmp:
            listing = Path(tmp) / "debloat.txt"
            listing.write_text("com.example.ads\nandroid.ext.services\nandroid.ext.shared\n")
            env = dict(os.environ, TV_HOST="192.0.2.1")
            result = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "check.py"), "--fix",
                                     str(listing)], env=env, capture_output=True, text=True,
                                    stdin=subprocess.DEVNULL, timeout=60)
            self.assertEqual(2, result.returncode, result.stdout + result.stderr)
            self.assertIn("#884", result.stderr)
            self.assertIn("android.ext.services, android.ext.shared", result.stderr)
            self.assertNotIn("could not reach", result.stderr)

    def test_check_py_refuses_before_connecting(self):
        with tempfile.TemporaryDirectory() as tmp:
            listing = Path(tmp) / "debloat.txt"
            listing.write_text("com.example.ads\ncom.android.packageinstaller\n")
            # A host nothing answers on: reaching connect() would print "could not reach the TV".
            env = dict(os.environ, TV_HOST="192.0.2.1")
            result = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "check.py"), "--fix",
                                     str(listing)], env=env, capture_output=True, text=True,
                                    stdin=subprocess.DEVNULL, timeout=60)
            self.assertEqual(2, result.returncode, result.stdout + result.stderr)
            self.assertIn("#884", result.stderr)
            self.assertNotIn("could not reach", result.stderr)

    def assert_sdk_sandbox_preflight(self, script, fix=False):
        """The exact package in a mixed BOM/CRLF list must stop before any transport, even with overrides."""
        shell = shutil.which("pwsh" if script == "disable.ps1" else "bash")
        if script != "check.py" and not shell:
            self.skipTest(f"{script} needs its shell on PATH")
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            for name in (script, "recovery.py"):
                shutil.copy2(ROOT / "tools" / name, tools / name)
            touched = Path(tmp) / "transport-called"
            (tools / "tv.py").write_text(
                "from pathlib import Path\n"
                "def connect(*args, **kwargs):\n"
                f"    Path({str(touched)!r}).touch()\n"
                "    raise AssertionError('unexpected transport')\n"
                "if __name__ == '__main__':\n"
                "    connect()\n", encoding="utf-8")
            for relative in ("venv/bin/python", "venv/Scripts/python.exe"):
                client = tools / relative
                client.parent.mkdir(parents=True, exist_ok=True)
                client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
                client.chmod(0o755)
            listing = Path(tmp) / "debloat.txt"
            listing.write_bytes(b"\xef\xbb\xbfcom.example.ads\r\n"
                                b"com.android.sdksandbox # issue 2557\r\n")
            env = dict(os.environ, TV_HOST="192.0.2.1", FORCE_HOME="1", FORCE_SETUPWRAITH="1",
                       TVINPUT_ALLOW="1", HANDLERS_ALLOW="1")
            if script == "check.py":
                command = [sys.executable, "-I", str(tools / script)]
                if fix:
                    command.append("--fix")
                command.append(str(listing))
            elif script == "disable.ps1":
                command = [shell, "-NoProfile", "-NonInteractive", "-File", str(tools / script),
                           "-TvHost", "192.0.2.1", str(listing)]
            else:
                command = [shell, str(tools / script), str(listing)]
            result = subprocess.run(command, cwd=repo, env=env, capture_output=True, text=True,
                                    stdin=subprocess.DEVNULL, timeout=30)
            output = result.stdout + result.stderr
            if script == "check.py":
                self.assertEqual(result.returncode, 2, output)
            else:
                self.assertNotEqual(result.returncode, 0, output)
            self.assertIn("refusing", output)
            self.assertIn("com.android.sdksandbox", packed(output))
            self.assertFalse(touched.exists(), "the core guard must run before the transport")

    def test_sdk_sandbox_disable_sh_refuses_with_all_overrides(self):
        self.assert_sdk_sandbox_preflight("disable.sh")

    def test_sdk_sandbox_disable_ps1_refuses_with_all_overrides(self):
        self.assert_sdk_sandbox_preflight("disable.ps1")

    def test_sdk_sandbox_check_refuses_before_connecting(self):
        self.assert_sdk_sandbox_preflight("check.py")

    def test_sdk_sandbox_check_fix_refuses_before_connecting(self):
        self.assert_sdk_sandbox_preflight("check.py", fix=True)


class TheErrorViewTest(unittest.TestCase):
    """packed() reads a refusal the way pwsh renders it, so a wrap is not read as a different message."""

    def test_a_coloured_refusal_wrapped_between_the_names_still_reads_as_one_string(self):
        rendered = ("\x1b[31;1mException: \x1b[0mtools/disable.ps1:69\x1b[0m\n"
                    "\x1b[36;1m     | \x1b[0mrefusing debloat.txt: it disables core Android packages\n"
                    "\x1b[36;1m     | \x1b[0mboot again (#884, #1578): android.ext.services,\x1b[0m\n"
                    "\x1b[36;1m     | \x1b[0m\x1b[31;1mandroid.ext.shared\x1b[0m. Take these lines out\n")
        self.assertIn("android.ext.services,android.ext.shared", packed(rendered))
        self.assertIn("#884", packed(rendered))

    def test_the_gutter_alone_is_taken_out_like_the_colour_was(self):
        wrapped = ("refusing debloat.txt: (#884): android.ext.services,\n"
                   "     | android.ext.shared. Take these lines out of the list\n")
        self.assertIn("android.ext.services,android.ext.shared", packed(wrapped))

    def test_the_words_keep_their_own_punctuation(self):
        # Only the rendering goes: a name that loses its dots would join the next word instead.
        self.assertEqual("refusingdebloat.txt:(#884,#1578):android.ext.services,android.ext.shared.",
                         packed("refusing debloat.txt: (#884, #1578): android.ext.services, "
                                "android.ext.shared.\n"))


if __name__ == "__main__":
    unittest.main()

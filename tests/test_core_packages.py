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

The PowerShell case needs `pwsh` on PATH and skips without it; CI's Ubuntu runner has it.
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
]


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
            # looked for with every whitespace taken out.
            packed = re.sub(r"\s+", "", output)
            self.assertIn("#884", packed)
            self.assertIn("android.ext.services,android.ext.shared", packed)
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


if __name__ == "__main__":
    unittest.main()

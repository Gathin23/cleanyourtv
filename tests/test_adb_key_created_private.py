"""tools/tv.py writes a new ADB private key at 0600, and sets tools/adbkey/ to 0700 when it already exists.

tests/test_adb_key_permissions.py checks the key and its folder after tv.py has run on a fresh tree.
Two cases fell outside that. adb_shell's keygen opens the key with a plain open(), so it was 0644
under the usual umask until tv.py's chmod ran, after keygen had also worked out and written the .pub.
And os.makedirs(mode=0o700, exist_ok=True) leaves a folder that already exists alone, so a
tools/adbkey/ made by a tv.py older than #491 stayed 0755. Google's adb opens a new key under
umask(077) (packages/modules/adb, client/auth.cpp, generate_key).

The keygen stub writes the key the way the real one does (plain open(), no chmod) and prints the
mode of the key and its folder at that moment, before tv.py gets to its chmod.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_adb_key_permissions as perms  # noqa: E402  (imported as a module so its tests do not run twice)

ROOT = perms.ROOT

STUB_KEYGEN = (
    "import os, stat\n"
    "def keygen(filepath):\n"
    "    # adb_shell/auth/keygen.py: a plain open(), no chmod.\n"
    "    with open(filepath, 'wb') as f:\n"
    "        f.write(b'-----BEGIN PRIVATE KEY-----\\nstub\\n-----END PRIVATE KEY-----\\n')\n"
    "    mode = lambda p: oct(stat.S_IMODE(os.stat(p).st_mode))\n"
    "    print('written', mode(filepath), mode(os.path.dirname(filepath)))\n"
    "    with open(filepath + '.pub', 'wb') as f:\n"
    "        f.write(b'stub pub\\n')\n"
)

# The usual umask is set explicitly, so the test does not pass by luck under a stricter one.
RUN = ("import os; os.umask(0o022); import tv; tv.get_signer(); "
       "print('umask after', oct(os.umask(0o022)))")


@unittest.skipIf(os.name == "nt", "POSIX permission bits; the chmod is a no-op on Windows")
class AdbKeyCreatedPrivateTest(unittest.TestCase):
    def run_tv(self, keydir_mode=None):
        """Run tv.get_signer() against a copy of tools/tv.py, with tools/adbkey/ made first at
        `keydir_mode` when given. Returns the stub's and tv.py's output as {first word(s): modes}."""
        tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, tmp, ignore_errors=True)

        tools = tmp / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "tv.py", tools / "tv.py")
        if keydir_mode is not None:
            (tools / "adbkey").mkdir()
            os.chmod(tools / "adbkey", keydir_mode)

        stub = tmp / "stub" / "adb_shell"
        (stub / "auth").mkdir(parents=True)
        (stub / "__init__.py").write_text("")
        (stub / "adb_device.py").write_text(perms.STUB_DEVICE)
        (stub / "auth" / "__init__.py").write_text("")
        (stub / "auth" / "keygen.py").write_text(STUB_KEYGEN)
        (stub / "auth" / "sign_pythonrsa.py").write_text(perms.STUB_SIGNER)

        # HOME points at the empty tree, so the stock ~/.android/adbkey is never read or touched.
        env = dict(os.environ, TV_HOST="127.0.0.1", HOME=str(tmp),
                   PYTHONPATH=os.pathsep.join([str(tmp / "stub"), str(tools)]))
        result = subprocess.run([sys.executable, "-c", RUN],
                                cwd=tmp, env=env, capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        said = {}
        for line in result.stdout.splitlines():
            if line.startswith("written "):
                said["key"], said["folder"] = (int(m, 8) for m in line.split()[1:])
            elif line.startswith("umask after "):
                said["umask"] = int(line.split()[2], 8)
        self.assertIn("key", said, f"tv.py did not generate a key:\n{result.stdout}{result.stderr}")
        said["folder after"] = stat.S_IMODE((tools / "adbkey").stat().st_mode)
        return said

    def test_new_key_is_private_when_written(self):
        said = self.run_tv()
        self.assertEqual(said["key"] & 0o077, 0,
                         f"the new ADB private key was {oct(said['key'])} when keygen wrote it, so "
                         f"another account could open it before tv.py's chmod")

    def test_existing_loose_folder_is_tightened(self):
        said = self.run_tv(keydir_mode=0o755)
        self.assertEqual(said["folder"] & 0o077, 0,
                         f"tools/adbkey/ was {oct(said['folder'])} when the key was written into it")
        self.assertEqual(said["folder after"] & 0o077, 0,
                         f"tools/adbkey/ is still {oct(said['folder after'])}, expected owner only")
        self.assertEqual(said["key"] & 0o077, 0,
                         f"the new ADB private key was {oct(said['key'])} when keygen wrote it")

    def test_umask_is_put_back(self):
        # Screenshots and --pull write files after get_signer() and should keep the usual mode.
        self.assertEqual(self.run_tv()["umask"], 0o022)


if __name__ == "__main__":
    unittest.main()

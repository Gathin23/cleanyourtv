"""Cygwin uses standard Bash (no pwd -W); cygpath supplies native Docker paths.

Runs the real shell script with stand-in uname, cygpath and docker on macOS/Linux.
No Windows runtime, Docker daemon, signing key, Android build or TV is used.
"""
import json
import os
import unittest

from test_docker_build_first_run import STUB, stub_python
from test_docker_build_host_key_dir import BASH, Repository, run_arguments


@unittest.skipUnless(BASH and os.name != "nt", "shell subprocesses use POSIX stubs")
class CygwinBuildTest(unittest.TestCase):
    def setUp(self):
        self.repo = Repository()
        self.addCleanup(self.repo.cleanup)
        self.keys = self.repo.folder / "native android keys"
        self.keys.mkdir()
        self.conversions = self.repo.folder / "conversions.jsonl"
        docker = self.repo.bin / "docker"
        docker.write_text(STUB.format(python=stub_python()), encoding="utf-8")
        uname = self.repo.bin / "uname"
        uname.write_text('#!/bin/sh\nprintf "%s\\n" "$SIM_UNAME"\n', encoding="utf-8")
        uname.chmod(0o755)
        cygpath = self.repo.bin / "cygpath"
        cygpath.write_text('''#!{python}
import json, os, sys
args = sys.argv[1:]
with open(os.environ["SIM_CONVERSIONS"], "a", encoding="utf-8") as handle:
    handle.write(json.dumps(args) + "\\n")
if len(args) != 2 or args[0] != "-m":
    sys.exit(2)
if args[1] == os.environ.get("SIM_FAIL_PATH"):
    sys.stderr.write("could not convert the path\\n")
    sys.exit(23)
if args[1] == os.environ["SIM_ROOT"]:
    print("C:/Projects/My TV")
elif args[1] == os.environ["SIM_KEYS"]:
    print("D:/Native Android Keys")
else:
    sys.exit(2)
'''.format(python=stub_python()), encoding="utf-8")
        cygpath.chmod(0o755)

    def run_shell(self, **extra):
        environment = dict(SIM_UNAME="CYGWIN_NT-10.0", SIM_ROOT=str(self.repo.folder),
                           SIM_KEYS=str(self.keys), SIM_CONVERSIONS=str(self.conversions),
                           DOCKER_IMAGE_STATE="present")
        environment.update(extra)
        result = self.repo.run(**environment)
        converted = [json.loads(line) for line in self.conversions.read_text().splitlines()] \
            if self.conversions.exists() else []
        if self.conversions.exists():
            self.conversions.unlink()
        return (*result, converted)

    def test_cygwin_converts_the_repository_before_running_docker(self):
        status, out, err, calls, converted = self.run_shell()
        self.assertEqual(0, status, out + err)
        self.assertEqual([["-m", str(self.repo.folder)]], converted)
        run = run_arguments(calls)
        self.assertIn("C:/Projects/My TV:/repo", run)
        self.assertIn("ANDROID_USER_HOME=/gradle/.android", run)
        self.assertNotIn("--user", run)

    def test_the_first_build_uses_the_converted_context_too(self):
        status, out, err, calls, _ = self.run_shell(DOCKER_IMAGE_STATE="absent")
        self.assertEqual(0, status, out + err)
        build = next(call for call in calls if call[0] == "build")
        self.assertEqual("C:/Projects/My TV/tools/docker", build[-1])
        self.assertIn("C:/Projects/My TV:/repo", run_arguments(calls))

    def test_native_signing_keys_are_converted_as_one_mount_argument(self):
        status, out, err, calls, converted = self.run_shell(HOST_KEY_DIR=str(self.keys))
        self.assertEqual(0, status, out + err)
        self.assertEqual([["-m", str(self.repo.folder)], ["-m", str(self.keys)]], converted)
        run = run_arguments(calls)
        self.assertIn("D:/Native Android Keys:/key", run)
        self.assertIn("ANDROID_USER_HOME=/key", run)

    def test_failed_path_conversion_starts_no_container(self):
        for path in (self.repo.folder, self.keys):
            with self.subTest(path=path):
                status, out, err, calls, converted = self.run_shell(
                    HOST_KEY_DIR=str(self.keys), SIM_FAIL_PATH=str(path))
                self.assertEqual(23, status, out + err)
                self.assertIn("could not convert the path", err)
                self.assertTrue(converted)
                self.assertNotIn("run", [call[0] for call in calls])

    def test_git_bash_keeps_its_pwd_conversion_without_needing_cygpath(self):
        environment = self.repo.folder / "bash-environment.sh"
        environment.write_text('''pwd() {
  if [ "$#" -eq 1 ] && [ "$1" = -W ]; then
    printf '%s\\n' 'C:/Projects/My TV'
  else
    builtin pwd "$@"
  fi
}
''', encoding="utf-8")
        status, out, err, calls, converted = self.run_shell(
            SIM_UNAME="MINGW64_NT-10.0", BASH_ENV=str(environment))
        self.assertEqual(0, status, out + err)
        self.assertEqual([], converted)
        self.assertIn("C:/Projects/My TV:/repo", run_arguments(calls))


if __name__ == "__main__":
    unittest.main()

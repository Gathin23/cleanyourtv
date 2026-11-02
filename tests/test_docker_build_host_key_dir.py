"""A HOST_KEY_DIR Docker cannot mount as the folder on this computer is refused before docker runs.

`tools/docker-build.md`, "The signing key", tells a reader who switches between a native build and a
Docker build to sign both with the one native key: `HOST_KEY_DIR=~/.android tools/docker-build.sh`.
Both twins took that value as given and handed it to `docker run -v <value>:/key`, and Docker mounts a
bind only from an absolute path that is there:

    $ docker run --rm -v /tmp/cyt-nokey-abc:/key <image>
    $ ls -ld /tmp/cyt-nokey-abc
    drwxr-xr-x 2 root root 4096 ...        # created by Docker, empty, owned by root on Linux

    $ docker run --rm -v dbr:/key <image>   # a relative path is a volume name, not a folder
    $ docker volume ls | grep dbr
    local     dbr

So a mistyped path, or `'~/.android'` in quotes (no shell expands a tilde inside a variable), left
`ANDROID_USER_HOME=/key` pointing at an empty folder or volume. The build then made a new debug key in
it, exactly the situation "The signing key" describes: an install over the app already on the TV fails
with INSTALL_FAILED_UPDATE_INCOMPATIBLE, and on Linux the folder Docker created is root's.

What is pinned here:

  * the refusal, driven through the real tools/docker-build.sh in a temporary repository, with a
    stand-in docker on PATH that logs every argument it is given (so "nothing ran" is checked, not
    assumed): a folder that is not there, a file where a folder goes, a relative path (one that exists,
    the shape Docker reads as a volume), and a quoted tilde - each answers 1 with its own words, and
    docker is never called; a Git Bash drive-letter path (C:/Users/...) is read as absolute, the way
    tools/theme.sh reads it (#1796), so the same run stops on the folder check rather than on the
    relative-path one;
  * two controls: an absolute folder that is there is still mounted at /key with KEY_DIR=/key, and no
    HOST_KEY_DIR at all still keeps the key in the Gradle volume;
  * the PowerShell twin's two checks and their place before `docker run`, as source assertions - pwsh
    is not on this machine and the same `Test-Path`/`IsPathRooted` pair is what CI would run.

No TV, no docker daemon, no JDK: a stand-in on PATH and text.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SH = ROOT / "tools" / "docker-build.sh"
PS1 = ROOT / "tools" / "docker-build.ps1"
BASH = shutil.which("bash") or ""

# A stand-in docker: it appends the arguments it was given to $DOCKER_LOG and answers every call 0, so a
# run that reaches docker at all is visible in the log even though nothing is built.
STUB = '''#!{python}
import json, os, sys
with open(os.environ["DOCKER_LOG"], "a", encoding="utf-8") as handle:
    handle.write(json.dumps(sys.argv[1:]) + "\\n")
sys.exit(0)
'''


class Repository:
    """A throwaway checkout: the real script, and the Gradle project it looks for."""

    def __init__(self):
        self.folder = Path(tempfile.mkdtemp(prefix="cyt-docker-key-"))
        (self.folder / "tools").mkdir()
        (self.folder / "launcher").mkdir()
        (self.folder / "launcher" / "settings.gradle").write_text("", encoding="utf-8")
        shutil.copy2(SH, self.folder / "tools" / "docker-build.sh")
        self.bin = self.folder / "bin"
        self.bin.mkdir()
        self.log = self.folder / "docker.log"
        stub = self.bin / "docker"
        stub.write_text(STUB.format(python=sys.executable), encoding="utf-8")
        stub.chmod(0o755)

    def run(self, **env):
        """(status, stdout, stderr, [arguments of each docker call])."""
        environment = {key: value for key, value in os.environ.items() if key != "HOST_KEY_DIR"}
        environment.update(PATH=f"{self.bin}{os.pathsep}{os.environ.get('PATH', '')}",
                           DOCKER_LOG=str(self.log))
        environment.update({key: value for key, value in env.items() if value is not None})
        done = subprocess.run([BASH, str(self.folder / "tools" / "docker-build.sh")],
                              cwd=str(self.folder), env=environment,
                              capture_output=True, text=True, timeout=60)
        calls = []
        if self.log.exists():
            calls = [json.loads(line) for line in self.log.read_text(encoding="utf-8").splitlines()
                     if line.strip()]
            self.log.unlink()
        return done.returncode, done.stdout, done.stderr, calls

    def cleanup(self):
        shutil.rmtree(self.folder, ignore_errors=True)


def run_arguments(calls):
    """The arguments of the one `docker run` call, or an AssertionError naming what docker was given."""
    for call in calls:
        if call[:1] == ["run"]:
            return call
    raise AssertionError(f"no docker run among the calls docker was given: {calls}")


@unittest.skipUnless(BASH and os.name != "nt", "tools/docker-build.sh is a POSIX shell script")
class HostKeyDirTest(unittest.TestCase):
    def setUp(self):
        self.repo = Repository()
        self.addCleanup(self.repo.cleanup)

    def test_a_folder_that_is_not_there_is_refused_before_docker_runs(self):
        missing = self.repo.folder / "no-such-android"
        status, out, err, calls = self.repo.run(HOST_KEY_DIR=str(missing))
        self.assertEqual(1, status, out + err)
        self.assertIn(f"HOST_KEY_DIR={missing} is not a folder", err)
        self.assertIn("INSTALL_FAILED_UPDATE_INCOMPATIBLE", err)
        self.assertEqual([], calls, f"docker was called with a folder that is not there: {calls}")
        self.assertEqual("", out)

    def test_a_file_where_the_folder_goes_is_refused(self):
        keystore = self.repo.folder / "debug.keystore"
        keystore.write_text("a key file\n", encoding="utf-8")
        status, out, err, calls = self.repo.run(HOST_KEY_DIR=str(keystore))
        self.assertEqual(1, status, out + err)
        self.assertIn(f"HOST_KEY_DIR={keystore} is not a folder", err)
        self.assertEqual([], calls, calls)

    def test_a_relative_path_is_refused_because_docker_reads_it_as_a_volume(self):
        # The folder is there: `-d` alone would pass it, and `docker run -v android:/key` makes a volume
        # called `android` instead of mounting this one.
        (self.repo.folder / "android").mkdir()
        status, out, err, calls = self.repo.run(HOST_KEY_DIR="android")
        self.assertEqual(1, status, out + err)
        self.assertIn("HOST_KEY_DIR=android is not an absolute path", err)
        self.assertIn("volume", err)
        self.assertEqual([], calls, calls)

    def test_a_quoted_tilde_is_refused_with_the_path_a_shell_would_expand(self):
        status, out, err, calls = self.repo.run(HOST_KEY_DIR="~/.android")
        self.assertEqual(1, status, out + err)
        self.assertIn("HOST_KEY_DIR=~/.android is not an absolute path", err)
        self.assertIn("'~/.android' in quotes is not expanded", err)
        self.assertEqual([], calls, calls)

    def test_a_git_bash_drive_letter_path_is_absolute_not_relative(self):
        # `C:/Users/me/.android` is how a Windows reader's key folder arrives under Git Bash, and tools/theme.sh
        # reads that form as absolute (#1796). Here the folder cannot exist, so the run must stop on the folder
        # check - not before it, on the relative-path one - and still reach no docker.
        status, out, err, calls = self.repo.run(HOST_KEY_DIR="C:/Users/me/.android")
        self.assertEqual(1, status, out + err)
        self.assertNotIn("is not an absolute path", err,
                         "a Git Bash drive-letter path is read as relative, as tools/theme.sh does not")
        self.assertIn("HOST_KEY_DIR=C:/Users/me/.android is not a folder", err)
        self.assertEqual([], calls, calls)

    def test_a_folder_that_is_there_is_still_mounted_and_becomes_the_key_dir(self):
        keys = self.repo.folder / "android"
        keys.mkdir()
        status, out, err, calls = self.repo.run(HOST_KEY_DIR=str(keys))
        self.assertEqual(0, status, out + err)
        run = run_arguments(calls)
        self.assertIn(f"{keys}:/key", run, "the native key folder is no longer mounted at /key")
        self.assertIn("ANDROID_USER_HOME=/key", run, run)

    def test_no_host_key_dir_still_keeps_the_key_in_the_gradle_volume(self):
        status, out, err, calls = self.repo.run()
        self.assertEqual(0, status, out + err)
        run = run_arguments(calls)
        self.assertIn("ANDROID_USER_HOME=/gradle/.android", run, run)
        self.assertNotIn("/key", " ".join(run), run)


class PowerShellTwinTest(unittest.TestCase):
    """The same two checks in tools/docker-build.ps1, as source: pwsh is not on this machine."""

    def setUp(self):
        self.text = PS1.read_text(encoding="utf-8")

    def position(self, needle, why):
        where = self.text.find(needle)
        if where < 0:
            self.fail(why)
        return where

    def test_the_powershell_twin_refuses_both_shapes(self):
        self.assertIn("[System.IO.Path]::IsPathRooted($env:HOST_KEY_DIR)", self.text,
                      "tools/docker-build.ps1 hands a relative HOST_KEY_DIR to docker, which mounts a volume")
        self.assertIn("Test-Path -LiteralPath $env:HOST_KEY_DIR -PathType Container", self.text,
                      "tools/docker-build.ps1 does not check that HOST_KEY_DIR is a folder")

    def test_both_checks_come_before_the_container_is_started(self):
        rooted = self.position("[System.IO.Path]::IsPathRooted($env:HOST_KEY_DIR)",
                               "tools/docker-build.ps1 has no relative-path check")
        container = self.position("Test-Path -LiteralPath $env:HOST_KEY_DIR -PathType Container",
                                  "tools/docker-build.ps1 has no folder check")
        started = re.search(r"^docker run ", self.text, re.M)
        if started is None:
            self.fail("tools/docker-build.ps1 no longer starts a container")
        self.assertLess(rooted, container, "the folder test answers before the relative-path one")
        self.assertLess(container, started.start(), "docker runs before HOST_KEY_DIR is judged")


if __name__ == "__main__":
    unittest.main()

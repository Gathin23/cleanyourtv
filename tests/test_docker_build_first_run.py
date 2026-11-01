"""A first run of tools/docker-build.ps1 builds the image instead of stopping on the probe.

#2890: on Windows 11 with Windows PowerShell 5.1, with no `cleanyourtv-android-build` image yet,
`tools\\docker-build.ps1` stopped at `docker image inspect $Image *> $null` and built nothing. The script sets
`$ErrorActionPreference = 'Stop'`, and PowerShell 5.1 turns a native command's redirected stderr into an
ErrorRecord (NativeCommandError); the image does not exist yet, so docker writes "No such image" to stderr,
which is exactly the case the `if ($LASTEXITCODE -ne 0)` line below is there to handle - and that line was
never reached. The workaround the reporter used was to run `docker build` by hand once.

The twin already got this right - `docker image inspect "$IMAGE" >/dev/null 2>&1` inside `if !` is a shell
that judges by the status, not by the stream - so only the PowerShell twin changed: the probe is asked with
'Continue' in effect and its exit code is read before 'Stop' comes back.

What is pinned here:

  * the shape of the probe (a source assertion, so it runs everywhere): the one redirected native call in the
    file is the inspect, it is asked while 'Continue' is set, and its exit code is read before 'Stop' comes
    back. A PowerShell 5.1 behaviour cannot be run on Linux, so the shape is the guard - and a second
    redirected native call, the same mistake elsewhere in the file, fails the test too.
  * the flow, under PowerShell 7 against a stand-in docker that logs every call it is given: a missing image
    is built and then the launcher is built, an image that is there is not built again, and a build that
    fails stops the script before gradle is run. These cases skip without `pwsh` (as the other suites that
    drive the twins do) and on Windows, where the stub is a POSIX script.
  * the same first-run flow for the shell twin, so the two files are shown to answer the same way. That case
    passes on both sides of this change - it is the control.

No TV, no docker, no JDK: a stand-in on PATH and text.

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
TOOLS = ROOT / "tools"
PS1 = TOOLS / "docker-build.ps1"
SH = TOOLS / "docker-build.sh"
DOCKERFILE_DIR = TOOLS / "docker"
PWSH = shutil.which("pwsh") or ""
BASH = shutil.which("bash") or ""

IMAGE = "cleanyourtv-android-build"
STOP = "$ErrorActionPreference = 'Stop'"
CONTINUE = "$ErrorActionPreference = 'Continue'"

# A native call whose output is redirected: '*>', '2>&1', '2>$null' and the like. In Windows PowerShell 5.1
# each stderr line of such a call becomes an error record, and 'Stop' ends the script on it.
REDIRECT = re.compile(r"\*>\s*\S|(?:^|[^0-9])2>\s*[&$]|&>\s*[12]")

# A stand-in docker: it writes the arguments it was given to $DOCKER_LOG, answers `image inspect` from
# $DOCKER_IMAGE_STATE, and answers `build`/`run` with the status it is given. An absent image writes to
# stderr, as docker does - that line is the one PowerShell 5.1 turned into an error record.
STUB = '''#!{python}
import json, os, sys
with open(os.environ["DOCKER_LOG"], "a", encoding="utf-8") as handle:
    handle.write(json.dumps(sys.argv[1:]) + "\\n")
args = sys.argv[1:]
if args[:2] == ["image", "inspect"]:
    if os.environ.get("DOCKER_IMAGE_STATE", "absent") == "absent":
        sys.stderr.write("Error response from daemon: No such image: %s:latest\\n" % args[-1])
        sys.exit(1)
    sys.exit(0)
if args[:1] == ["build"]:
    sys.exit(int(os.environ.get("DOCKER_BUILD_STATUS", "0")))
if args[:1] == ["run"]:
    sys.exit(int(os.environ.get("DOCKER_RUN_STATUS", "0")))
sys.exit(0)
'''


def redirected_native_calls(text):
    """[(line number, line)] for each line of the script that redirects a call's output."""
    out = []
    for number, line in enumerate(text.splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if REDIRECT.search(stripped):
            out.append((number, stripped))
    return out


def first_line(lines, start, prefix):
    """The index of the first line at or after `start` that begins with `prefix`, or an AssertionError."""
    for index in range(start, len(lines)):
        if lines[index].strip().startswith(prefix):
            return index
    raise AssertionError(f"no line at or after {start} starts with {prefix!r}")


def run_with_stub(script, interpreter, state="absent", build="0"):
    """(completed process, [argv of each docker call]) for one run of `script` with the stub on PATH."""
    work = Path(tempfile.mkdtemp())
    (work / "bin").mkdir()
    stub = work / "bin" / "docker"
    stub.write_text(STUB.format(python=sys.executable), encoding="utf-8")
    stub.chmod(0o755)
    log = work / "calls.jsonl"
    log.write_text("", encoding="utf-8")
    env = dict(os.environ, PATH=f"{work / 'bin'}{os.pathsep}{os.environ.get('PATH', '')}",
               DOCKER_LOG=str(log), DOCKER_IMAGE_STATE=state, DOCKER_BUILD_STATUS=build)
    done = subprocess.run([interpreter, *script], cwd=str(ROOT), env=env,
                          capture_output=True, text=True, timeout=180)
    calls = [json.loads(line) for line in log.read_text(encoding="utf-8").splitlines() if line]
    return done, calls


class ProbeShapeTest(unittest.TestCase):
    """The probe's shape: #2890 cannot be run on Linux, so the shape is what is held."""

    def setUp(self):
        self.text = PS1.read_text(encoding="utf-8")
        self.lines = self.text.splitlines()

    def test_the_only_redirected_call_is_the_image_probe(self):
        calls = redirected_native_calls(self.text)
        self.assertEqual(1, len(calls),
                         "one redirected call, the image probe, is what #2890 is about; another one is the "
                         f"same mistake elsewhere in the file: {calls}")
        self.assertIn("docker image inspect", calls[0][1])

    def test_the_probe_is_asked_while_stop_is_off(self):
        probe = first_line(self.lines, 0, "docker image inspect")
        switches = [line.strip() for line in self.lines[:probe]
                    if line.strip().startswith("$ErrorActionPreference")]
        self.assertTrue(switches, "the script sets $ErrorActionPreference before the probe")
        self.assertEqual(CONTINUE, switches[-1],
                         "Windows PowerShell 5.1 ends the script on the probe's stderr while 'Stop' is set, "
                         "which is #2890")

    def test_the_exit_code_is_read_before_stop_comes_back(self):
        probe = first_line(self.lines, 0, "docker image inspect")
        read = first_line(self.lines, probe, "$Missing = $LASTEXITCODE")
        restored = first_line(self.lines, probe, STOP)
        self.assertLess(read, restored, "the exit code is read while 'Continue' is still set")
        judged = first_line(self.lines, probe, "if ($Missing)")
        self.assertLess(restored, judged, "'Stop' is back before the branch that builds the image")

    def test_the_rest_of_the_script_still_runs_under_stop(self):
        switches = [line.strip() for line in self.lines
                    if line.strip().startswith("$ErrorActionPreference")]
        self.assertEqual([STOP, CONTINUE, STOP], switches,
                         "the script's own setting, the probe's exception, and the restore")
        self.assertIn("throw 'docker build failed'", self.text, "a build that fails still stops the script")


@unittest.skipUnless(PWSH and os.name != "nt",
                     "tools/docker-build.ps1 is run under pwsh, with a POSIX stub docker")
class PowerShellFirstRunTest(unittest.TestCase):
    def test_a_first_run_builds_the_image_and_then_the_launcher(self):
        done, calls = run_with_stub(["-File", str(PS1)], PWSH)
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertEqual(["image", "inspect", IMAGE], calls[0])
        self.assertEqual(["build", "-t", IMAGE, str(DOCKERFILE_DIR)], calls[1],
                         "the build #2890 never reached")
        self.assertEqual("run", calls[2][0], calls)
        self.assertIn("/repo/launcher", calls[2])
        self.assertIn(IMAGE, calls[2])
        self.assertEqual(3, len(calls), calls)

    def test_an_image_that_is_already_there_is_not_built_again(self):
        done, calls = run_with_stub(["-File", str(PS1)], PWSH, state="present")
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertEqual(["image", "inspect", IMAGE], calls[0])
        self.assertEqual("run", calls[1][0], f"nothing between the probe and the run: {calls}")
        self.assertNotIn("build", [call[0] for call in calls])

    def test_a_build_that_fails_stops_before_the_launcher_is_built(self):
        done, calls = run_with_stub(["-File", str(PS1)], PWSH, build="1")
        self.assertNotEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertIn("docker build failed", done.stdout + done.stderr)
        self.assertNotIn("run", [call[0] for call in calls], calls)


@unittest.skipUnless(BASH and os.name != "nt", "tools/docker-build.sh is a POSIX shell script")
class ShellTwinTest(unittest.TestCase):
    def test_the_shell_twin_builds_the_image_on_a_first_run(self):
        done, calls = run_with_stub([str(SH)], BASH)
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertEqual(["image", "inspect", IMAGE], calls[0])
        self.assertEqual(["build", "-t", IMAGE, str(DOCKERFILE_DIR)], calls[1])


if __name__ == "__main__":
    unittest.main()

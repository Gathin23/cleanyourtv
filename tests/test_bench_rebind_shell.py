"""tools/bench.py's rebind command, run in a real POSIX shell with `settings` and `sleep` stood in.

`rebind_accessibility` empties the accessibility list for two seconds and writes it back, in one
command on the TV. A review of #3851 found that when a write putting the list back failed, the
command still ended in success with the list empty, and bench.py could not tell: `tv.shell()` hands
back the output, not the exit status. The command now chains the writes with `&&`, keeps its trap
until they went through, and prints a marker that bench.py checks.

Here the generated command runs under /bin/sh with a `settings` that stores each value in a file and
can be told to fail, once or every time. Android's own shell, the binding itself and a dropped
connection are not exercised: those were checked on a TCL 43C645.

Run from the repository root:
    python -m unittest tests.test_bench_rebind_shell -v
"""
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools import bench

OURS = "com.gathin.tvhome/.HomeKeyService"
OTHER = "com.exemplo.outroapp/.Servico"
LIST = "enabled_accessibility_services"
FLAG = "accessibility_enabled"

# settings put secure <key> <value>: a failure is injected for FAIL_KEY when the value is the one
# being put back (not the emptying write), once if FAIL_ONCE is set, else every time.
SETTINGS = """#!/bin/sh
[ "$1" = put ] || exit 2
key=$3 value=$4
if [ "$key" = "$FAIL_KEY" ] && [ -n "$value" ] && [ "$value" != 0 ]; then
  if [ -z "$FAIL_ONCE" ] || [ ! -e "$STATE/failed" ]; then
    : > "$STATE/failed"
    exit 1
  fi
fi
printf '%s' "$value" > "$STATE/$key"
"""


@unittest.skipUnless(os.name == "posix" and shutil.which("sh"), "needs a POSIX shell")
class RebindShellTest(unittest.TestCase):
    def run_rebind(self, services, fail_key="", once=False):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bin_dir, state = tmp / "bin", tmp / "state"
            bin_dir.mkdir()
            state.mkdir()
            (bin_dir / "settings").write_text(SETTINGS)
            (bin_dir / "sleep").write_text("#!/bin/sh\nexit 0\n")
            for tool in ("settings", "sleep"):
                (bin_dir / tool).chmod(0o755)
            env = dict(os.environ, PATH=f"{bin_dir}:{os.environ.get('PATH', '')}", STATE=str(state),
                       FAIL_KEY=fail_key, FAIL_ONCE="1" if once else "")

            class ShellTV:
                def shell(self, command, read_timeout_s=None, *, retry=False):
                    return subprocess.run(["sh", "-c", command], env=env, capture_output=True,
                                          text=True, timeout=30).stdout

            error = None
            try:
                bench.rebind_accessibility(ShellTV(), services)
            except RuntimeError as failure:
                error = failure
            read = lambda key: (state / key).read_text() if (state / key).exists() else None
            return error, read(LIST), read(FLAG)

    def test_both_writes_go_through(self):
        error, listed, flag = self.run_rebind([OTHER, OURS])
        self.assertIsNone(error)
        self.assertEqual(listed, f"{OTHER}:{OURS}")
        self.assertEqual(flag, "1")

    def test_a_component_with_a_dollar_sign_is_written_as_it_is(self):
        inner = "com.gathin.tvhome/com.gathin.tvhome.Outer$Helper"
        error, listed, _ = self.run_rebind([inner])
        self.assertIsNone(error)
        self.assertEqual(listed, inner)

    def test_a_list_write_that_fails_once_is_put_back_on_exit_and_still_reported(self):
        error, listed, flag = self.run_rebind([OTHER, OURS], LIST, once=True)
        self.assertIsNotNone(error, "a restore that needed the trap is reported")
        self.assertEqual(listed, f"{OTHER}:{OURS}")
        self.assertEqual(flag, "1")

    def test_a_list_write_that_keeps_failing_is_reported(self):
        error, listed, _ = self.run_rebind([OTHER, OURS], LIST)
        self.assertIsNotNone(error)
        self.assertEqual(listed, "")

    def test_a_switch_write_that_fails_once_is_put_back_on_exit_and_still_reported(self):
        error, listed, flag = self.run_rebind([OURS], FLAG, once=True)
        self.assertIsNotNone(error)
        self.assertEqual((listed, flag), (OURS, "1"))

    def test_a_switch_write_that_keeps_failing_is_reported(self):
        error, _, flag = self.run_rebind([OURS], FLAG)
        self.assertIsNotNone(error)
        self.assertEqual(flag, "0")


if __name__ == "__main__":
    unittest.main()

"""tools/disable.sh must keep the packages that drive the HDMI/AV/tuner inputs (#321).

On a OnePlus TV (Android 11, MediaTek/MStar) disabling them left every HDMI port detecting the cable
and showing "No Signal", and only a full restore plus a cold boot brought the picture back.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
# On MediaTek sets this is the package behind every HDMI, AV and tuner input.
MEDIATEK = "com.mediatek.tvinput"
LIST = f"# My TV\ncom.example.before\n{MEDIATEK}  # the inputs\ncom.example.after\n"
BATCH = ["pm disable-user --user 0 com.example.before",
         f"pm disable-user --user 0 {MEDIATEK}",
         "pm disable-user --user 0 com.example.after",
         "pm list packages -d"]


class TvInputGuardTest(unittest.TestCase):
    def run_disable(self, providers, extra_env=None, exit_code=0, eol="\n"):
        """Run the real disable.sh against a fake tv.py client.

        The fake answers the TV_INPUT_HARDWARE lookup with `providers` and tells every other command
        apart: the ones this test cares about (the batch) land in CAPTURE, the rest are answered the
        way a working TV would, so the other guards in the script stay out of the way.
        """
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
            client = tools / "venv" / "bin" / "python"
            client.parent.mkdir(parents=True)
            # Quote every name so the fake echoes it verbatim: the test is about what the script does
            # with a hostile name from the TV, not what sh does with one inside the fake.
            eol_escaped = "\\r\\n" if eol == "\r\n" else "\\n"
            answer = "".join(f"printf '%s{eol_escaped}' {shlex.quote(p)}\n" for p in providers)
            if exit_code:
                answer += f"exit {exit_code}\n"
            client.write_text(
                "#!/bin/sh\n"
                'case "$*" in\n'
                f"  *TV_INPUT_HARDWARE*) {answer or 'true'} ;;\n"
                # The other guards ask who owns Home; none of them is about this test.
                '  *resolve-activity*) echo "com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *query-activities*) echo "  com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *getprop*) echo 1 ;;\n'
                # The read-back after the batch (#1376) answers nothing, so CAPTURE keeps the batch.
                '  *"pm list packages -e"*) ;;\n'
                # recovery.py --save and the batch: the batch is the last write, so CAPTURE holds it.
                '  *) cat > "$CAPTURE" ;;\n'
                "esac\n")
            client.chmod(0o755)
            (repo / "debloat.txt").write_text(LIST)
            capture = Path(tmp) / "commands.txt"
            env = dict(os.environ, CAPTURE=str(capture), **(extra_env or {}))
            if os.name != "posix" or not shutil.which("bash"):
                raise unittest.SkipTest("disable.sh is a POSIX script; it needs bash")
            result = subprocess.run(["bash", str(tools / "disable.sh"), "debloat.txt"], cwd=repo,
                                    env=env, capture_output=True, text=True, stdin=subprocess.DEVNULL)
            self.assertEqual(result.returncode, 0, result.stderr)
            commands = capture.read_text().splitlines() if capture.exists() else []
            return commands, result.stderr

    def test_input_package_is_skipped(self):
        commands, stderr = self.run_disable([MEDIATEK])
        self.assertEqual(commands, ["pm disable-user --user 0 com.example.before",
                                    "pm disable-user --user 0 com.example.after",
                                    "pm list packages -d"])
        self.assertIn(f"Skipping {MEDIATEK}", stderr)
        self.assertIn("TV_INPUT_HARDWARE", stderr)
        self.assertIn("TVINPUT_ALLOW=1", stderr)

    def test_the_skip_message_says_how_to_spot_a_false_positive(self):
        # The permission alone is a loose test: a preloaded app can ask for it and drive no input
        # (a MediaTek USB media player on a Vestel shandao set). The owner is the one who has to
        # judge that, so the message has to name the check that settles it.
        _, stderr = self.run_disable([MEDIATEK])
        self.assertIn("pm query-services --components -a android.media.tv.TvInputService", stderr)
        self.assertIn("false positive", stderr)

    def test_crlf_from_adb_still_matches(self):
        # adb can end a line with CRLF; a stray CR would leave the name unmatched and the port broken.
        commands, stderr = self.run_disable([MEDIATEK], eol="\r\n")
        self.assertNotIn(f"pm disable-user --user 0 {MEDIATEK}", commands)
        self.assertIn(f"Skipping {MEDIATEK}", stderr)

    def test_disabled_when_the_tv_reports_no_hardware_inputs(self):
        commands, stderr = self.run_disable([])
        self.assertEqual(commands, BATCH)
        self.assertNotIn("Skipping", stderr)

    def test_force(self):
        commands, stderr = self.run_disable([MEDIATEK], {"TVINPUT_ALLOW": "1"})
        self.assertEqual(commands, BATCH)
        self.assertNotIn("Skipping", stderr)

    def test_failed_check_warns_and_keeps_the_list(self):
        # tv.py failed (no connection, a timeout): do not skip on a guess, but say the check did not
        # happen, because a black input is easy to blame on the cable.
        commands, stderr = self.run_disable([], exit_code=1)
        self.assertEqual(commands, BATCH)
        self.assertIn("Could not check", stderr)

    def test_only_names_from_the_list_are_matched(self):
        # A package the TV reports that is not in this list must not lose a line or add one.
        commands, stderr = self.run_disable([MEDIATEK, "com.android.tv"])
        self.assertEqual(commands, ["pm disable-user --user 0 com.example.before",
                                    "pm disable-user --user 0 com.example.after",
                                    "pm list packages -d"])
        self.assertNotIn("com.android.tv", stderr)

    def test_a_name_from_the_tv_cannot_add_a_command(self):
        # The lookup output comes from the TV, so it is only ever compared against the list, never
        # pasted back into a command.
        commands, stderr = self.run_disable(["com.example.before; reboot"])
        self.assertEqual(commands, BATCH)
        self.assertNotIn("reboot", "\n".join(commands))
        self.assertNotIn("Skipping", stderr)


if __name__ == "__main__":
    unittest.main()

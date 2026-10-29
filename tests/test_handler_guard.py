"""tools/disable.sh must not disable the apps the TV opens its inputs and Settings with.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
LIVE_TV = "com.example.livetv"
# com.android.tv.settings itself is refused as a core package; this is a vendor one.
SETTINGS = "com.example.tvsettings"
LIST = f"# My TV\ncom.example.before\n{LIVE_TV}\n{SETTINGS}\ncom.example.after\n"
# What `query-activities --brief` prints: the VIEW-channel answer, then the Settings ones.
HANDLERS = (f'printf "%s\\n" "1 activities found:" "  {LIVE_TV}/.MainActivity" == '
            f'"  {SETTINGS}/.MainSettings" "No activities found"')


class HandlerGuardTest(unittest.TestCase):
    def run_disable(self, handlers=HANDLERS, extra_env=None):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
            client = tools / "venv" / "bin" / "python"
            client.parent.mkdir(parents=True)
            # Fake tv.py client: answers the handler query, records --batch input.
            client.write_text(
                '#!/bin/sh\n'
                'case "$*" in\n'
                f'  *cursor.item/channel*) {handlers} ;;\n'
                '  *resolve-activity*) echo "com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *query-activities*) echo "  com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *--batch*) cat > "$CAPTURE" ;;\n'
                '  *) ;;\n'
                'esac\n')
            client.chmod(0o755)
            (repo / "debloat.txt").write_text(LIST)
            capture = Path(tmp) / "commands.txt"
            env = dict(os.environ, CAPTURE=str(capture), **(extra_env or {}))
            result = subprocess.run(["bash", str(tools / "disable.sh"), "debloat.txt"], cwd=repo,
                                    env=env, capture_output=True, text=True, stdin=subprocess.DEVNULL)
            self.assertEqual(result.returncode, 0, result.stderr)
            return capture.read_text().splitlines(), result.stderr

    def test_keeps_the_input_and_settings_apps(self):
        commands, stderr = self.run_disable()
        self.assertEqual(commands[:2], ["pm disable-user --user 0 com.example.before",
                                        "pm disable-user --user 0 com.example.after"])
        self.assertIn(f"Skipping {LIVE_TV}: the TV opens its HDMI/AV inputs", stderr)
        self.assertIn(f"Skipping {SETTINGS}: the TV opens Settings", stderr)

    def test_disables_them_when_nothing_answers(self):
        commands, _ = self.run_disable(handlers='echo "No activities found"; echo ==')
        self.assertIn(f"pm disable-user --user 0 {LIVE_TV}", commands)
        self.assertIn(f"pm disable-user --user 0 {SETTINGS}", commands)

    def test_warns_and_keeps_going_when_the_query_fails(self):
        commands, stderr = self.run_disable(handlers="exit 1")
        self.assertIn(f"pm disable-user --user 0 {LIVE_TV}", commands)
        self.assertIn("Could not check which listed packages open", stderr)

    def test_allow(self):
        commands, _ = self.run_disable(extra_env={"HANDLERS_ALLOW": "1"})
        self.assertIn(f"pm disable-user --user 0 {LIVE_TV}", commands)
        self.assertIn(f"pm disable-user --user 0 {SETTINGS}", commands)


if __name__ == "__main__":
    unittest.main()

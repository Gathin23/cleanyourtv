"""tools/privacy.py against a simulated TV (#444).

The point of the audit is what it refuses to claim, and no TV reproduces that on demand: on firmware
where most packages share android.uid.system, a permission scan flags fifty packages that record
nothing. tools/tv.py is replaced by a fake that answers from a JSON file, so the real audit logic
runs and only the ADB transport is simulated.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

from tools import privacy


ROOT = Path(__file__).resolve().parents[1]

FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


state = load()


def answer(command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if command.startswith("settings get secure "):
        key = command.split()[-1]
        value = state.get("voice", {}).get(key)
        return ("null" if not value else value) + "\n"
    if command.startswith("dumpsys package "):
        return state.get("dumps", {}).get(command.split()[-1], "") + "\n"
    if command.startswith("appops get "):
        name = command.split()[2]
        return state.get("appops", {}).get(name, "No operations.") + "\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
    if state.get("unreachable"):
        raise ConnectionError("could not connect to 192.168.1.50:5555")
    return Device()


def main():
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''

SYSTEM_DUMP = "userId=1000\nrequested permissions:\n  android.permission.RECORD_AUDIO: granted=true\n"
OWN_DUMP = "userId=10123\nrequested permissions:\n  android.permission.RECORD_AUDIO: granted=true\n"
INTERNET_DUMP = "userId=10124\nrequested permissions:\n  android.permission.INTERNET: granted=true\n"


class PrivacyAuditTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", tools / "privacy.py")
        (tools / "tv.py").write_text(FAKE_TV)
        self.script = tools / "privacy.py"
        self.state_path = self.base / "tv-state.json"
        self.tv()

    def tv(self, **overrides):
        state = {
            "installed": [
                "com.google.android.katniss",
                "tv.inscape.tvclient.inscapeacr",
                "org.droidtv.usagelogger",
                "com.example.voicecontrol",
                "com.example.plain",
            ],
            "disabled": ["org.droidtv.usagelogger"],
            "voice": {"voice_interaction_service":
                      "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService",
                      "voice_recognition_service":
                      "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService",
                      "assistant": "null"},
            "dumps": {
                "com.google.android.katniss": OWN_DUMP,
                "tv.inscape.tvclient.inscapeacr": INTERNET_DUMP,
                "com.example.voicecontrol": SYSTEM_DUMP,
                "org.droidtv.usagelogger": "userId=10125\n",
            },
            "appops": {
                "com.google.android.katniss": "No operations.",
                "tv.inscape.tvclient.inscapeacr": "RECORD_AUDIO: allow; time=+1m23s0ms ago",
            },
            "unreachable": False,
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def run_script(self, *args, host="192.168.1.50"):
        env = dict(os.environ, TV_HOST=host, FAKE_TV_STATE=str(self.state_path))
        return subprocess.run([sys.executable, str(self.script), *args], cwd=self.repo, env=env,
                              capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=120)

    # The report

    def test_firmware_nomination_outranks_the_package_name(self):
        out = self.run_script().stdout
        self.assertIn("Voice assistants and wake word", out)
        self.assertIn("named by the TV: voice_interaction_service, voice_recognition_service", out)

    def test_system_uid_is_reported_once_not_as_a_permission_claim(self):
        out = self.run_script().stdout
        self.assertIn("shares the system UID (uid 1000)", out)
        # The same package must not also be advertised as holding the microphone permission.
        entry = out.split("com.example.voicecontrol", 1)[1].split("\n\n", 1)[0]
        self.assertNotIn("declares RECORD_AUDIO", entry)

    def test_appops_separates_declared_from_used(self):
        out = self.run_script().stdout
        katniss = out.split("com.google.android.katniss", 1)[1].split("\n\n", 1)[0]
        self.assertIn("declares RECORD_AUDIO", katniss)
        self.assertIn("appops: no RECORD_AUDIO history returned", katniss)
        inscape = out.split("tv.inscape.tvclient.inscapeacr", 1)[1].split("\n\n", 1)[0]
        self.assertIn("appops: recorded RECORD_AUDIO access", inscape)
        self.assertNotIn("declares RECORD_AUDIO", inscape)
        self.assertNotIn("since boot", out)

    def test_mode_or_rejection_is_not_reported_as_access(self):
        for answer in ("RECORD_AUDIO: allow",
                       "RECORD_AUDIO: ignore; rejectTime=+1m0s0ms ago",
                       "RECORD_AUDIO: vendor history unavailable"):
            with self.subTest(answer=answer):
                self.tv(appops={"com.google.android.katniss": answer})
                result = self.run_script()
                self.assertEqual(result.returncode, 0, result.stderr)
                entry = result.stdout.split("com.google.android.katniss", 1)[1].split("\n\n", 1)[0]
                self.assertIn("appops: RECORD_AUDIO access is unknown", entry)
                self.assertNotIn("recorded RECORD_AUDIO access", entry)
                self.assertNotIn("was used", entry)

    def test_acr_is_kept_apart_from_the_microphone(self):
        out = self.run_script().stdout
        self.assertIn("ACR (identifies what is on screen, no microphone needed)", out)
        self.assertIn("INTERNET, not RECORD_AUDIO", out)

    def test_name_matches_are_not_presented_as_findings(self):
        out = self.run_script().stdout
        self.assertIn("Matched by name only, nothing else known", out)
        self.assertIn("matched by name only", out)

    def test_never_claims_a_package_is_listening(self):
        out = self.run_script().stdout
        self.assertIn("What this audit cannot tell you: whether audio ever leaves the TV", out)
        self.assertIn("the microphone is in the remote rather than the panel", out)
        for claim in ("is recording", "listens in the background", "spy"):
            self.assertNotIn(claim, out)

    def test_disabled_packages_are_shown_as_disabled(self):
        out = self.run_script().stdout
        self.assertIn("disabled  org.droidtv.usagelogger", out)

    def test_names_from_the_tv_are_not_passed_back_into_commands(self):
        self.tv(installed=["com.example.ok; rm -rf /", "com.google.android.katniss"])
        out = self.run_script().stdout
        self.assertNotIn("rm -rf", out)

    # The list it writes

    def write(self, *args):
        target = self.base / "privacy.txt"
        return target, self.run_script("--write", str(target), *args)

    def test_write_leaves_out_name_matches_and_already_disabled_packages(self):
        target, result = self.write()
        self.assertEqual(result.returncode, 0, result.stderr)
        text = target.read_text()
        self.assertIn("com.google.android.katniss", text)
        self.assertIn("tv.inscape.tvclient.inscapeacr", text)
        self.assertNotIn("com.example.voicecontrol", text)
        self.assertNotIn("org.droidtv.usagelogger", text)
        self.assertEqual([line for line in text.splitlines() if line.startswith("com.example.plain")],
                         [])

    def test_written_list_is_a_list_disable_and_restore_accept(self):
        target, _ = self.write()
        lines = [line.split("#", 1)[0].strip() for line in target.read_text().splitlines()]
        packages = [line for line in lines if line]
        self.assertEqual(packages, ["com.google.android.katniss",
                                    "tv.inscape.tvclient.inscapeacr"])
        # The header has to spell out both commands, because the undo is the reason this is safe.
        self.assertIn("tools/disable.sh", target.read_text())
        self.assertIn("tools/restore.sh", target.read_text())
        # And the list has to survive the parser the two scripts share, not just look right.
        sys.path.insert(0, str(ROOT / "tools"))
        import recovery
        self.assertEqual(recovery.read_list(target), packages)

    def test_write_refuses_to_overwrite_a_list(self):
        target, _ = self.write()
        target.write_text("# mine\n")
        _, second = self.write()
        self.assertNotEqual(second.returncode, 0)
        self.assertIn("already exists", second.stderr)
        self.assertEqual(target.read_text(), "# mine\n")

    def test_include_low_writes_the_name_matches_too(self):
        target, result = self.write("--include-low")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("com.example.voicecontrol", target.read_text())

    def test_write_says_nothing_to_do_when_only_name_matches_were_found(self):
        self.tv(installed=["com.example.voicecontrol"],
                voice={}, dumps={"com.example.voicecontrol": SYSTEM_DUMP})
        target, result = self.write()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("nothing identified to write", result.stderr)
        self.assertFalse(target.exists())

    # Failure modes

    def test_unreachable_tv_names_the_setting_to_check(self):
        self.tv(unreachable=True)
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("cannot reach the TV", result.stderr)
        self.assertIn("tools/tv.py --probe", result.stderr)

    def test_an_empty_package_list_is_an_error_not_an_empty_report(self):
        self.tv(installed=[], disabled=[])
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("returned no packages", result.stderr)

    def test_usage_error_for_unknown_arguments(self):
        result = self.run_script("--write")
        self.assertEqual(result.returncode, 2)
        self.assertIn("usage: privacy.py", result.stderr)


class AppOpsEvidenceTest(unittest.TestCase):
    # AppOpsService's `get` prints mode unconditionally, but time/rejectTime separately:
    # https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/services/core/java/com/android/server/appop/AppOpsService.java#L4765-L4789

    def test_access_time_or_running_is_evidence(self):
        for answer in (
                "RECORD_AUDIO: allow; time=+1m23s0ms ago; duration=+2s0ms",
                "RECORD_AUDIO: allow; time=0 ago",
                "RECORD_AUDIO: allow; time=-5ms ago",
                "RECORD_AUDIO: allow (running)",
                "RECORD_AUDIO: ignore; time=+1h0m0s0ms ago; rejectTime=+1m0s0ms ago",
                "Uid mode: RECORD_AUDIO: allow\nRECORD_AUDIO: allow; time=+1s0ms ago"):
            with self.subTest(answer=answer):
                self.assertIs(privacy.used_microphone(answer), True)

    def test_no_operations_means_no_returned_history(self):
        for answer in ("No operations.", "No operations.\nDefault mode: allow"):
            with self.subTest(answer=answer):
                self.assertIs(privacy.used_microphone(answer), False)

    def test_unsupported_or_non_access_output_is_unknown(self):
        for answer in (
                "", "Error: unknown package", "SecurityException: denied",
                "RECORD_AUDIO: allow", "Uid mode: RECORD_AUDIO: allow",
                "RECORD_AUDIO: ignore; rejectTime=+1m0s0ms ago",
                "RECORD_AUDIO: allow; duration=+1s0ms",
                "RECORD_AUDIO: vendor history unavailable",
                "RECORD_AUDIO: allow; time=unknown ago",
                "CAMERA: allow; time=+1s0ms ago\nRECORD_AUDIO: allow",
                "No operations could be read: unsupported vendor command"):
            with self.subTest(answer=answer):
                self.assertIsNone(privacy.used_microphone(answer))


class CatalogueTest(unittest.TestCase):
    """The catalogue is the part a reader trusts; keep it honest and small."""

    @classmethod
    def setUpClass(cls):
        sys.path.insert(0, str(ROOT / "tools"))
        import privacy
        cls.privacy = privacy

    def test_every_entry_says_what_the_package_does(self):
        for name, (category, note) in self.privacy.CATALOGUE.items():
            with self.subTest(package=name):
                self.assertIn(category, ("voice", "acr", "telemetry"))
                self.assertGreater(len(note), 20, "a note has to say something")
                self.assertRegex(name, self.privacy.PACKAGE_RE)

    def test_voice_entries_are_assistants_and_acr_entries_are_not(self):
        # An ACR package that reads the picture is useless to list under the microphone.
        for name, (category, note) in self.privacy.CATALOGUE.items():
            if category == "acr":
                with self.subTest(package=name):
                    self.assertNotIn("microphone", note.split(",")[0])

    def test_name_hint_does_not_match_ordinary_bloat(self):
        for name in ("com.android.vending", "com.example.playground", "com.example.streaming"):
            self.assertFalse(self.privacy.NAME_HINT.search(name), name)
        for name in ("com.example.voice", "com.example.voicecontrol", "tv.example.acr.client",
                     "com.example.speech.service"):
            self.assertTrue(self.privacy.NAME_HINT.search(name), name)


class DocumentationTest(unittest.TestCase):
    def test_readme_lists_the_tool_and_what_it_cannot_say(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("[`tools/privacy.py`](tools/privacy.py)", readme)
        self.assertIn("What the audit cannot do is prove intent", readme)


if __name__ == "__main__":
    unittest.main()

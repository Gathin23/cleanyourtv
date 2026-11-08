"""Lost ADB responses must not replay benchmark actions; all transports and effects are offline."""
from collections import Counter
import contextlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

from tests import test_bench_framestats as frames
from tests import test_bench_home as home
from tools import bench

PACKAGE = 'com.gathin.tvhome'
COMPONENT = PACKAGE + '/.MainActivity'
OTHER = 'com.android.tv.settings/.MainSettings'
SERVICE = PACKAGE + '/.KeyRemapService'
START = 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n ' + COMPONENT


class ReplayState:
    def __init__(self, fail=None, phase='after'):
        self.fail, self.phase = fail, phase
        self.dropped = False
        self.effects = Counter()
        self.events = []
        self.connects = self.closes = self.focus = 0
        self.running = True
        self.services = SERVICE

    def execute(self, command):
        self.effects[command] += 1
        self.events.append('executed: ' + command)
        if command == 'input keyevent 22':
            self.focus += 1
        if command.startswith('am force-stop '):
            self.running = False
            self.services = ''
        if command == START:
            wait = 3 if self.running else 80
            self.running = True
            return 'WaitTime: ' + str(wait)
        if command.startswith('am start '):
            return 'WaitTime: 5'
        if command == 'settings get secure enabled_accessibility_services':
            return self.services
        if command.startswith('settings put secure enabled_accessibility_services '):
            self.services = command.split(' ', 4)[4]
        if 'resolve-activity' in command:
            return COMPONENT
        if command.startswith('dumpsys package '):
            return 'versionName=fixture'
        if command == 'dumpsys window':
            return 'mCurrentFocus=Window{abc u0 ' + COMPONENT + '}'
        if command.endswith(' framestats'):
            header = frames.HEADERS['android-14']
            return ('Uptime: 2000\nTotal frames rendered: 1\nJanky frames: 0\n'
                    + frames.profile(header, frames.frame(header)))
        if command.startswith('dumpsys gfxinfo '):
            return 'Uptime: 1000\n'
        if command.startswith('logcat -d -b main -b events'):
            return home.home() + '\n' + home.resume()
        if command.startswith('logcat -d '):
            return 'injectKeyEvent action=ACTION_DOWN KEYCODE_DPAD_RIGHT eventTime=1000'
        if command.startswith('head -1 /proc/stat'):
            return 'cpu 1000 0 1000 8000 0 0 0 0 0 0\n'
        if command.startswith('dumpsys meminfo '):
            return 'TOTAL PSS: 65536'
        if command.startswith(('input keyevent ', 'am force-stop ', 'settings put secure ',
                               'logcat -c ', 'getprop ', 'pidof ')):
            return ''
        raise AssertionError(f'unexpected fake command: {command}')

    def snapshot(self):
        return dict(effects=dict(self.effects), events=self.events, connects=self.connects,
                    closes=self.closes, focus=self.focus, running=self.running, services=self.services)


class Device:
    def __init__(self, state):
        self.state = state

    def shell(self, command, read_timeout_s=None, decode=True):
        state = self.state
        drop = command == state.fail and not state.dropped
        if drop:
            state.dropped = True
            if state.phase == 'before':
                state.events.append('lost before execution: ' + command)
                raise ConnectionResetError('lost command response')
        reply = state.execute(command)
        if drop:
            state.events.append('acknowledgement lost: ' + command)
            raise ConnectionResetError('lost command response')
        return reply.encode('utf-8')

    def close(self):
        self.state.closes += 1

def fake_client(state):
    client = types.ModuleType('tv')
    client.HOST, client.PORT = 'fake-tv', 5555
    client.connect_error = lambda error: None
    def connect():
        state.connects += 1
        state.events.append('connect ' + str(state.connects))
        return Device(state)
    client.connect = connect
    return client


@contextlib.contextmanager
def connected(state):
    client = fake_client(state)
    with mock.patch.dict(sys.modules, {"tv": client}), mock.patch.dict(os.environ):
        tv = bench.TV("fake-tv")
        try:
            yield tv, client
        finally:
            tv.close()


class RetryPolicyTest(unittest.TestCase):
    def test_commands_with_unknown_completion_are_not_replayed(self):
        commands = ("input keyevent 22", "input keyevent 3", START,
                    "am force-stop " + PACKAGE,
                    "settings put secure enabled_accessibility_services " + SERVICE,
                    "settings put secure accessibility_enabled 1", "logcat -c -b main",
                    "dumpsys gfxinfo " + PACKAGE + " reset")
        for command in commands:
            for phase in ("before", "after"):
                with self.subTest(command=command, phase=phase):
                    state = ReplayState(command, phase)
                    with connected(state) as (tv, client):
                        error = None
                        try:
                            tv.shell(command)
                        except ConnectionResetError as failure:
                            error = failure
                        self.assertEqual(state.effects[command], int(phase == "after"))
                        if command == "input keyevent 22":
                            self.assertEqual(state.focus, int(phase == "after"))
                        self.assertIsInstance(error, ConnectionResetError)
                        self.assertEqual(state.connects, 1)
                        self.assertEqual(state.closes, 1)
                        self.assertIsNone(tv.client)

    def test_read_queries_retry_and_reuse_the_reopened_connection(self):
        command = "dumpsys package " + PACKAGE
        for phase in ("before", "after"):
            with self.subTest(phase=phase):
                state = ReplayState(command, phase)
                with connected(state) as (tv, client):
                    self.assertEqual(tv.version_of(PACKAGE), "fixture")
                    self.assertEqual(tv.version_of(PACKAGE), "fixture")
                    tv.shell("input keyevent 22")
                    self.assertEqual(state.connects, 2)
                    self.assertEqual(state.effects[command], 2 + int(phase == "after"))
                    self.assertEqual(state.focus, 1)

    def test_a_later_read_connects_without_replaying_the_failed_action(self):
        state = ReplayState("input keyevent 22")
        with connected(state) as (tv, client):
            error = None
            try:
                tv.shell("input keyevent 22")
            except ConnectionResetError as failure:
                error = failure
            self.assertEqual(state.focus, 1)
            self.assertIsInstance(error, ConnectionResetError)
            self.assertEqual(tv.version_of(PACKAGE), "fixture")
            self.assertEqual(state.connects, 2)
            self.assertEqual(state.effects["input keyevent 22"], 1)

    def test_a_connect_failure_retries_before_submitting_the_command(self):
        state = ReplayState()
        with connected(state) as (tv, client):
            with mock.patch.object(client, "connect", side_effect=[
                    ConnectionResetError("handshake failed"), Device(state)]) as connect:
                tv.shell("input keyevent 22")
            self.assertEqual(connect.call_count, 2)
            self.assertEqual(state.focus, 1)
            self.assertEqual(state.effects["input keyevent 22"], 1)

    def test_read_retry_exhaustion_closes_and_propagates_the_error(self):
        command = "dumpsys package " + PACKAGE
        state = ReplayState(command)
        with connected(state) as (tv, client):
            original_connect = client.connect

            def connect():
                state.dropped = False
                return original_connect()

            client.connect = connect
            with self.assertRaises(ConnectionResetError):
                tv.version_of(PACKAGE)
            self.assertEqual(state.effects[command], 2)
            self.assertEqual(state.connects, 2)
            self.assertEqual(state.closes, 2)
            self.assertIsNone(tv.client)

    def test_actual_measurements_abort_and_cold_cleanup_still_restores_services(self):
        cases = (
            ("reset", "dumpsys gfxinfo " + PACKAGE + " reset",
             lambda tv: bench.measure_key(tv, 22, package=PACKAGE)),
            ("key", "input keyevent 22", lambda tv: bench.measure_key(tv, 22, package=PACKAGE)),
            ("cold", START, lambda tv: bench.cold_start(tv, COMPONENT, 1)),
            ("stop", "am force-stop " + PACKAGE, lambda tv: bench.cold_start(tv, COMPONENT, 1)),
            ("home", "input keyevent 3", lambda tv: bench.return_to_home(tv, COMPONENT, 1)),
            ("restore", "settings put secure enabled_accessibility_services " + SERVICE,
             lambda tv: bench.restore_accessibility(tv, PACKAGE, [SERVICE])),
        )
        for label, command, measure in cases:
            with self.subTest(operation=label):
                state = ReplayState(command)
                if label == "restore":
                    state.services = ""
                with connected(state) as (tv, client), mock.patch.object(bench.time, "sleep"):
                    tv._other_app = OTHER
                    error = None
                    try:
                        measure(tv)
                    except ConnectionResetError as failure:
                        error = failure
                    self.assertEqual(state.effects[command], 1)
                    self.assertIsInstance(error, ConnectionResetError)
                    if label in ("cold", "stop", "restore"):
                        self.assertEqual(state.services, SERVICE)
                    if label == "home":
                        self.assertEqual(state.effects[
                            "logcat -d -b main -b events -v epoch | grep -e injectKeyEvent "
                            "-e am_on_resume_called -e wm_on_resume_called"], 0)

    def test_retrying_a_frame_read_does_not_inject_another_key(self):
        command = "dumpsys gfxinfo " + PACKAGE + " framestats"
        state = ReplayState(command)
        with connected(state) as (tv, client), mock.patch.object(bench.time, "sleep"):
            self.assertEqual(bench.measure_key(tv, 22, package=PACKAGE), (20.0, 20.0, 1, 0))
            self.assertEqual(state.effects[command], 2)
            self.assertEqual(state.effects["input keyevent 22"], 1)
            self.assertEqual(state.focus, 1)


CLI = """from pathlib import Path
import json,runpy,sys
from unittest import mock
root,script,folder,command,phase,state_path=sys.argv[1:]
sys.path.insert(0,root)
sys.path.insert(0,str(Path(root)/'tests'))
from test_bench_replay import ReplayState,fake_client
state=ReplayState(command or None,phase)
sys.argv=[script,'--host','fake-tv','--out-dir',folder,'--app','com.android.tv.settings/.MainSettings','--keys','1','--series','1','--cold','1','--returns','1','--idle','1']
with mock.patch.dict(sys.modules,{'tv':fake_client(state)}),mock.patch('time.sleep'):
    try:runpy.run_path(script,run_name='__main__')
    finally:Path(state_path).write_text(json.dumps(state.snapshot()),encoding='utf-8')
"""


class BenchmarkReplayCLITest(unittest.TestCase):
    def run_benchmark(self, folder, command="", phase="after", existing=False):
        root = Path(__file__).resolve().parents[1]
        script = folder / "bench.py"
        shutil.copyfile(root / "tools" / "bench.py", script)
        result = folder / "output" / "result.json"
        previous = b'{"previous_run": true}\n'
        if existing:
            result.parent.mkdir()
            result.write_bytes(previous)
        state_path = folder / "state.json"
        run = subprocess.run([sys.executable, "-B", "-c", CLI, str(root), str(script),
                              str(result.parent), command, phase, str(state_path)],
                             capture_output=True, text=True, timeout=60)
        return run, json.loads(state_path.read_text(encoding="utf-8")), result, previous

    def test_normal_benchmark_keeps_one_key_and_a_cold_start(self):
        with tempfile.TemporaryDirectory() as tmp:
            run, state, result, _ = self.run_benchmark(Path(tmp))
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
            self.assertEqual(run.stderr, "")
            self.assertIn("Written:", run.stdout)
            data = json.loads(result.read_text(encoding="utf-8"))
            self.assertEqual(data["keys"], {"series": 1, "per_series": 1, "measured": 1, "lost": 0})
            self.assertEqual(data["response_ms"]["p50"], 20)
            self.assertEqual(data["cold_start_ms"]["p50"], 80)
            self.assertEqual(state["focus"], 1)
            self.assertEqual(state["connects"], 1)
            self.assertEqual(state["services"], SERVICE)

    def test_ambiguous_execution_cannot_publish_a_successful_result(self):
        for command in ("input keyevent 22", START):
            for phase in ("before", "after"):
                for existing in (False, True):
                    with self.subTest(command=command, phase=phase, existing=existing):
                        with tempfile.TemporaryDirectory() as tmp:
                            run, state, result, previous = self.run_benchmark(
                                Path(tmp), command, phase, existing)
                            self.assertEqual(state["effects"].get(command, 0), int(phase == "after"))
                            self.assertEqual(run.returncode, 1, run.stdout + run.stderr)
                            self.assertIn("ConnectionResetError", run.stderr)
                            self.assertNotIn("Written:", run.stdout)
                            if existing:
                                self.assertEqual(result.read_bytes(), previous)
                            else:
                                self.assertFalse(result.exists())
                            self.assertEqual(sorted(p.name for p in result.parent.iterdir()),
                                             ["result.json"] if existing else [])


if __name__ == "__main__":
    unittest.main()

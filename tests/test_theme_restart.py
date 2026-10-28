"""Theme restart recovery against a clocked fake TV and AOSP-shaped service dumps.

No connection to a real TV. Delays model the two independent reports in #994;
fixtures exercise component-specific ActivityManager binding evidence, not labels.
"""
import contextlib
import io
import os
from pathlib import Path
import tempfile
import re
import shlex
import subprocess
import sys
import types
import unittest
from unittest.mock import patch

from tools import theme_restart as restart

HOME = 'com.gathin.tvhome/.HomeKeyService'
INPUT = 'com.gathin.tvhome/.InputKeyService'
OTHER = 'com.example.mapper/.Service'
EXTRA = 'com.example.reader/.Service'


def service(component=HOME, user=0, connected=True):
    # ServiceRecord.dump/IntentBindRecord.dumpInService in AOSP Android 8/11/14.
    return f'''  * ServiceRecord{{a1 u{user} {component}}}
    intent={{cmp={component}}}
    app=ProcessRecord{{b2 1234:com.gathin.tvhome/u{user}a101}}
    Bindings:
    * IntentBindRecord{{c3 CREATE}}:
      intent={{cmp={component}}}
      binder={'android.os.BinderProxy@d4' if connected else 'null'}
      requested=true received={'true' if connected else 'false'} hasBound=true doRebind=false
      * Client AppBindRecord{{e5 ProcessRecord{{f6 222:system/1000}}}}
        Per-process Connections:
          ConnectionRecord{{d7 u{user} CR com.gathin.tvhome/.HomeKeyService:@d4}}
    All Connections:
      ConnectionRecord{{d7 u{user} CR com.gathin.tvhome/.HomeKeyService:@d4}}
'''


class TV:
    def __init__(self, saved=OTHER + ':' + HOME, delay=1, minimum_off=2, late=None,
                 never=False, user=0, flag='1', home='com.gathin.tvhome/.MainActivity'):
        self.saved = saved
        self.value = saved
        self.flag = flag
        self.now = 0
        self.off_at = None
        self.ready = None
        self.delay = delay
        self.minimum_off = minimum_off
        self.late = late
        self.never = never
        self.user = user
        self.home = home
        self.calls = []
        self.bound = set(restart.components(saved))
        self.clear_count = 0
        self.closed = False
        self.fail = None

    def close(self):
        self.closed = True

    def pause(self, duration):
        self.now += duration
        if self.late is not None and self.now >= self.late:
            self.value = ':'.join(x for x in self.value.split(':')
                                  if not x.startswith('com.gathin.tvhome/'))
            self.bound = set()
            self.late = None
        if self.ready is not None and self.now >= self.ready and not self.never:
            self.bound = set(restart.components(self.value)) if self.flag == '1' else set()

    def shell(self, command, read_timeout_s=None):
        self.calls.append(command)
        cmd = command.split('; rc=$?;', 1)[0]
        if self.fail and self.fail in cmd:
            raise OSError('simulated broken transport')
        if cmd.startswith('cyt_restore() { '):
            restore = cmd.split('cyt_restore() { ', 1)[1].split('; };', 1)[0]
            off = re.search(r"(settings --user \d+ put secure enabled_accessibility_services '' && settings --user \d+ put secure accessibility_enabled 0) && sleep 2", cmd)[1]
            try:
                self.shell(off)
                getattr(self, 'remote_pause', self.pause)(2)
            finally:
                self.shell(restore)
            return '\n__CYT_THEME_RC__0\n'
        output = ''
        for piece in cmd.split(' && '):
            args = shlex.split(piece)
            if args[:2] == ['am', 'get-current-user']:
                output += str(self.user)
            elif args[:3] == ['cmd', 'package', 'resolve-activity']:
                output += self.home
            elif args[:2] == ['am', 'force-stop']:
                self.value = ':'.join(x for x in self.value.split(':')
                                      if not x.startswith('com.gathin.tvhome/'))
                self.bound = set()
                self.ready = None
            elif args[0] == 'settings':
                if args[1:3] != ['--user', str(self.user)]:
                    raise AssertionError('wrong Android user: ' + piece)
                op, key = args[3], args[5]
                if op == 'get':
                    output += self.value if key == restart.SERVICES else self.flag
                elif key == restart.SERVICES:
                    self.value = args[6]
                else:
                    self.flag = args[6]
                    if self.flag == '0':
                        self.off_at = self.now
                        self.ready = None
                        self.bound = set()
                        self.clear_count += 1
                    elif self.off_at is not None and self.now - self.off_at >= self.minimum_off:
                        self.ready = self.now + self.delay
            elif args[:3] == ['dumpsys', 'activity', 'services']:
                output += ''.join(service(x, user=self.user, connected=x in self.bound)
                                  for x in restart.components(self.value)
                                  if x.startswith('com.gathin.tvhome/'))
        return output + '\n__CYT_THEME_RC__0\n'


class BindingProbe(unittest.TestCase):
    def test_short_and_full_component_names(self):
        for component in (HOME, 'com.gathin.tvhome/com.gathin.tvhome.HomeKeyService'):
            self.assertEqual(restart.bound_services(service(component), 0), set(restart.components(HOME)))

    def test_component_only_and_accessibility_action_bindings(self):
        explicit = service()
        with_action = explicit.replace('intent={cmp=', 'intent={act=' + restart.ACTION + ' cmp=')
        self.assertEqual(restart.bound_services(explicit, 0), restart.bound_services(with_action, 0))
        wrong_component = explicit.replace('cmp=' + HOME, 'cmp=' + INPUT)
        self.assertFalse(restart.bound_services(wrong_component, 0))

    def test_multiple_helpers_and_users(self):
        dump = service(HOME) + service(INPUT) + service(HOME, user=10, connected=False)
        self.assertEqual(restart.bound_services(dump, 0), set(restart.components(HOME + ':' + INPUT)))
        self.assertEqual(restart.bound_services(dump, 10), set())

    def test_running_or_requested_service_is_not_a_completed_binding(self):
        for before, after in [('binder=android.os.BinderProxy@d4', 'binder=null'),
                              ('received=true', 'received=false'),
                              ('hasBound=true', 'hasBound=false'),
                              ('requested=true', 'requested=false'),
                              (':system/1000', ':com.example/10123'),
                              ('app=ProcessRecord{b2 1234:com.gathin.tvhome/u0a101}', 'app=null'),
                              ('intent={cmp=', 'intent={act=com.example.Unrelated cmp=')]:
            with self.subTest(after=after):
                self.assertFalse(restart.bound_services(service().replace(before, after), 0))

    def test_does_not_borrow_binder_or_client_from_another_record(self):
        bad = service(connected=False)
        self.assertNotIn(next(iter(restart.components(HOME))),
                         restart.bound_services(bad + service(INPUT), 0))
        missing_client = service().replace('Client AppBindRecord', 'Other AppBindRecord')
        self.assertFalse(restart.bound_services(missing_client + '\nAll Connections:\n'
                                               '* Client AppBindRecord{x ProcessRecord{f 222:system/1000}}', 0))

    def test_localized_accessibility_labels_are_not_used(self):
        self.assertFalse(restart.bound_services('Bound services:{Service[label=Bouton Accueil]}', 0))
        self.assertEqual(restart.bound_services(service(), 0), set(restart.components(HOME)))


class Recovery(unittest.TestCase):
    def test_delayed_tcl_binding(self):
        tv = TV(delay=3)
        restart.restart(tv, tv.pause)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.clear_count, 1)
        self.assertGreaterEqual(tv.now, 9)

    def test_late_removal_uses_original_snapshot_and_rechecks(self):
        tv = TV(late=6)
        restart.restart(tv, tv.pause)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.clear_count, 2, 'first successful bind must not end the check')
        tv.pause(3)
        self.assertEqual(set(restart.components(tv.value)), set(restart.components(tv.saved)))

    def test_two_helpers_and_another_app_are_preserved(self):
        tv = TV(saved=HOME + ':' + OTHER + ':' + INPUT)
        restart.restart(tv, tv.pause)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.flag, '1')
        self.assertTrue(set(restart.components(HOME + ':' + INPUT)) <= tv.bound)

    def test_no_helper_has_no_settings_writes_or_waits(self):
        for saved in ('null', '', OTHER, 'com.gathin.tvhome.other/.Service'):
            with self.subTest(saved=saved):
                tv = TV(saved=saved)
                restart.restart(tv, tv.pause)
                self.assertFalse(any(' put secure ' in x for x in tv.calls))
                self.assertFalse(any('dumpsys' in x for x in tv.calls))
                self.assertEqual(tv.now, 0)

    def test_accessibility_was_off_and_stays_off(self):
        tv = TV(flag='0')
        restart.restart(tv, tv.pause)
        self.assertEqual(tv.flag, '0')
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.now, 0)

    def test_another_foreground_android_user(self):
        tv = TV(user=10)
        restart.restart(tv, tv.pause)
        self.assertTrue(any('am start --user 10' in x for x in tv.calls))
        self.assertEqual(tv.clear_count, 1)

    def test_stock_home_redirect_uses_explicit_start(self):
        tv = TV(home='com.google.android.apps.tv.launcherx/.HomeActivity')
        restart.restart(tv, tv.pause)
        start = next(x for x in tv.calls if x.startswith('am start '))
        self.assertIn('-n com.gathin.tvhome/.MainActivity', start)
        tv = TV()
        restart.restart(tv, tv.pause)
        start = next(x for x in tv.calls if x.startswith('am start '))
        self.assertIn('-c android.intent.category.HOME', start)
        self.assertNotIn(' -n ', start)

    def test_unbound_timeout_is_bounded_and_leaves_saved_list(self):
        tv = TV(never=True)
        with self.assertRaisesRegex(RuntimeError, 'did not stay bound'):
            restart.restart(tv, tv.pause)
        self.assertEqual(tv.clear_count, 3)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.flag, '1')
        self.assertLessEqual(tv.now, 33)

    def test_newly_enabled_other_service_is_preserved_on_retry(self):
        tv = TV(late=6)
        original = tv.pause
        def pause(duration):
            original(duration)
            if tv.now >= 6 and EXTRA not in tv.value:
                tv.value += ':' + EXTRA
        restart.restart(tv, pause)
        self.assertIn(EXTRA, tv.value)
        self.assertTrue(set(restart.components(tv.saved)) <= restart.components(tv.value).keys())

    def test_other_apps_late_removal_is_also_repaired(self):
        tv = TV()
        original = tv.pause
        removed = False
        def pause(duration):
            nonlocal removed
            original(duration)
            if tv.now >= 6 and not removed:
                tv.value = tv.value.replace(OTHER + ':', '')
                removed = True
        restart.restart(tv, pause)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.clear_count, 2)

    def test_start_failure_restores_the_snapshot_before_returning_error(self):
        tv = TV()
        tv.fail = 'am start'
        with self.assertRaises(OSError):
            restart.restart(tv, tv.pause)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.flag, '1')

    def test_host_interrupt_during_off_interval_restores_list(self):
        tv = TV()
        def pause(duration):
            if duration == 2:
                raise KeyboardInterrupt()
            tv.pause(duration)
        tv.remote_pause = pause
        with self.assertRaises(KeyboardInterrupt):
            restart.restart(tv, pause)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.flag, '1')

    def test_transport_failure_stops_without_repeated_toggles(self):
        tv = TV()
        tv.fail = 'dumpsys activity'
        with self.assertRaises(OSError):
            restart.restart(tv, tv.pause)
        self.assertEqual(tv.clear_count, 1)
        self.assertEqual(tv.value, tv.saved)
        self.assertEqual(tv.flag, '1')

    def test_bad_setting_is_refused_before_force_stop(self):
        for value in ('Error: permission denied', 'pkg/.Svc; echo unsafe', 'pkg/.Svc::other/.Svc'):
            tv = TV(saved='')
            tv.value = value
            with self.assertRaises(RuntimeError):
                restart.restart(tv, tv.pause)
            self.assertFalse(any('force-stop' in x for x in tv.calls))


class RemoteCycle(unittest.TestCase):
    def test_restore_runs_on_normal_exit_settings_failure_and_remote_termination(self):
        # Run the actual device-side command in a POSIX shell. The settings stub
        # writes files; TERM simulates a remote shell being torn down mid-cycle.
        setup = r'''
settings() {
  shift 2
  printf '%s' "$4" > "$STATE/$3"
  if [ "$3" = accessibility_enabled ] && [ "$4" = 0 ]; then
    case "$MODE" in term) kill -TERM $$ ;; fail) return 1 ;; esac
  fi
}
sleep() { :; }
'''
        for mode in ('ok', 'fail', 'term'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as tmp:
                class Local:
                    def shell(self, command, **kwargs):
                        return subprocess.run(['/bin/sh', '-c', setup + command], text=True,
                            capture_output=True, env=dict(os.environ, STATE=tmp, MODE=mode)).stdout
                if mode == 'ok':
                    restart.cycle(Local(), 0, restart.components(OTHER + ':' + HOME))
                else:
                    with self.assertRaises(RuntimeError):
                        restart.cycle(Local(), 0, restart.components(OTHER + ':' + HOME))
                self.assertEqual((Path(tmp) / restart.SERVICES).read_text(), OTHER + ':' + HOME)
                self.assertEqual((Path(tmp) / restart.FLAG).read_text(), '1')


class ExitStatus(unittest.TestCase):
    def test_real_posix_shell_marker_checks_nonzero_exit(self):
        class Local:
            def shell(self, command, **kwargs):
                return subprocess.run(['/bin/sh', '-c', command], text=True,
                                      capture_output=True, check=True).stdout
        self.assertEqual(restart.shell(Local(), 'printf hello'), 'hello')
        for command in ('false', "printf 'Permission Denial: denied\\n'"):
            with self.assertRaises(RuntimeError):
                restart.shell(Local(), command)

    def test_missing_exit_marker_is_an_error(self):
        class Broken:
            def shell(self, command, **kwargs):
                return 'partial response'
        with self.assertRaises(RuntimeError):
            restart.shell(Broken(), 'true')

    def test_cli_returns_failure_and_closes_transport(self):
        tv = TV()
        module = types.SimpleNamespace(connect=lambda: tv)
        stderr = io.StringIO()
        with patch.dict(sys.modules, tv=module), patch.object(restart, 'restart', side_effect=RuntimeError('unbound')):
            with contextlib.redirect_stderr(stderr):
                self.assertEqual(restart.main(), 1)
        self.assertTrue(tv.closed)
        self.assertIn('Theme restart failed', stderr.getvalue())


if __name__ == '__main__':
    unittest.main()

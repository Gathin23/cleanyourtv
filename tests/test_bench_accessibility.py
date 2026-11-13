"""tools/bench.py must not wipe the accessibility list, and must not warm the cold start.

Two problems, both found on a TCL BeyondTV2 (Android TV 9) while measuring the launcher:

1. `am force-stop` removes that package's services from `enabled_accessibility_services`, and they
   do not come back when the launcher starts again (#503 reported it on a TCL 55C715). The old
   `restore_accessibility` wrote each service on its own with
   `settings put secure enabled_accessibility_services <one service>`, which **replaces the whole
   list**: the launcher's other services and any other app's were wiped.

2. It restored the list *before* the cold-start measurement. Writing that setting makes the system
   bind the service, which starts the launcher's process — so the "cold start" measured a warm one.

3. A service that is back on the list is not thereby bound. On a TCL 43C645 (Smart TV Pro, G03_4K_GB,
   Android 11) the force-stop takes HomeKeyService off the list, writing it back does not make the
   system bind it, and neither does a service left listed: Android binds when the list changes. After
   a run the remote's app buttons did nothing until the theme was pushed again.

The TV is a dict here: the point is the commands the tool would send.

Run from the repository root:
    python -m unittest tests.test_bench_accessibility -v
"""
import unittest
from unittest import mock

from tools import bench

OURS = "com.gathin.tvhome/.KeyRemapService"
OTHER = "com.exemplo.outroapp/.Servico"


class FakeTV:
    """Answers the two settings commands the tool uses, and keeps the value in memory."""

    def __init__(self, setting=""):
        self.setting = setting
        self.commands = []

    def shell(self, command, read_timeout_s=None, *, retry=False):
        self.commands.append(command)
        if command.startswith("settings get secure enabled_accessibility_services"):
            return self.setting
        if command.startswith("settings put secure enabled_accessibility_services "):
            self.setting = command.split(" ", 4)[4]
            return ""
        if command.startswith("am force-stop "):
            # This is what the TV does: the stopped package's services leave the list.
            package = command.split(" ", 2)[2]
            self.setting = ":".join(
                part for part in self.setting.split(":") if part and not part.startswith(package + "/"))
            return ""
        return ""

    def package_of(self, component):
        return component.split("/")[0]


class RestoreAccessibilityTest(unittest.TestCase):
    def test_another_apps_service_is_kept(self):
        tv = FakeTV("com.outro.novo/.Svc")
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertEqual(tv.setting, f"com.outro.novo/.Svc:{OURS}")

    def test_does_not_add_the_same_service_twice(self):
        tv = FakeTV(OURS)
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertEqual(tv.setting, OURS)
        self.assertEqual([c for c in tv.commands if c.startswith("settings put ")], [],
                         "nothing to write means nothing written")

    def test_short_and_long_component_forms_are_the_same_component(self):
        """The TV rewrites `pkg/pkg.Classe` as `pkg/.Classe`; restoring must not duplicate it."""
        tv = FakeTV("com.gathin.tvhome/com.gathin.tvhome.KeyRemapService")
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertEqual(tv.setting.count("KeyRemapService"), 1, tv.setting)

    def test_null_setting_is_treated_as_empty(self):
        tv = FakeTV("null")
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertEqual(tv.setting, OURS)


def services_dump(component, bound):
    """`dumpsys activity services <package>` for one service, as AOSP prints it on Android 8/11/14."""
    return f'''  * ServiceRecord{{a1 u0 {component}}}
    intent={{cmp={component}}}
    app=ProcessRecord{{b2 1234:com.gathin.tvhome/u0a101}}
    * IntentBindRecord{{c3 CREATE}}:
      intent={{cmp={component}}}
      binder={'android.os.BinderProxy@d4' if bound else 'null'}
      requested=true received={'true' if bound else 'false'} hasBound=true doRebind=false
      * Client AppBindRecord{{e5 ProcessRecord{{f6 222:system/1000}}}}
'''


class ListedTV(FakeTV):
    """A TV whose force-stop keeps the service in the list: what is left to tell is whether it is bound."""

    def __init__(self, setting, bound, flag="1"):
        super().__init__(setting)
        self.bound = bound
        self.flag = flag

    def shell(self, command, read_timeout_s=None, *, retry=False):
        if command.startswith("settings get secure accessibility_enabled"):
            self.commands.append(command)
            return self.flag
        if command.startswith("am get-current-user"):
            self.commands.append(command)
            return "0"
        if command.startswith("dumpsys activity services "):
            self.commands.append(command)
            return services_dump(OURS, self.bound)
        return super().shell(command, read_timeout_s, retry=retry)


class ListedButUnboundTest(unittest.TestCase):
    def writes(self, tv):
        return [c for c in tv.commands if "settings put secure" in c]

    def test_a_listed_helper_that_is_not_bound_is_bound_again(self):
        tv = ListedTV(f"{OTHER}:{OURS}", bound=False)
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        writes = self.writes(tv)
        self.assertEqual(len(writes), 1, tv.commands)
        cycle = writes[0]
        # The list has to change for Android to bind: emptied first, then written back whole.
        self.assertIn(f"cyt_restore() {{ settings put secure enabled_accessibility_services {OTHER}:{OURS}; "
                      "settings put secure accessibility_enabled 1; }", cycle)
        emptied = cycle.index("settings put secure enabled_accessibility_services '';")
        restored = cycle.index("; cyt_restore;", emptied)
        self.assertIn("sleep 2", cycle[emptied:restored])
        self.assertTrue(cycle.rstrip().endswith("trap - EXIT HUP INT TERM"), cycle)

    def test_the_tv_puts_the_list_back_by_itself_if_the_connection_drops(self):
        tv = ListedTV(f"{OTHER}:{OURS}", bound=False)
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        cycle = self.writes(tv)[0]
        self.assertLess(cycle.index("trap cyt_restore EXIT HUP INT TERM"),
                        cycle.index("enabled_accessibility_services ''"), cycle)

    def test_a_helper_put_back_on_the_list_and_still_unbound_is_bound_again(self):
        """What the 43C645 does: the stop empties the list, the write puts it back, nothing binds."""
        tv = ListedTV(OTHER, bound=False)
        with mock.patch.object(bench.time, "sleep"):
            bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        writes = self.writes(tv)
        self.assertEqual(writes[0], f"settings put secure enabled_accessibility_services {OTHER}:{OURS}")
        self.assertEqual(len([w for w in writes if w.startswith("cyt_restore()")]), 1, writes)

    def test_a_helper_bound_after_it_is_put_back_needs_nothing_more(self):
        tv = ListedTV(OTHER, bound=True)
        with mock.patch.object(bench.time, "sleep"):
            bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertFalse([w for w in self.writes(tv) if w.startswith("cyt_restore()")], tv.commands)

    def test_a_bound_helper_is_left_alone(self):
        tv = ListedTV(OURS, bound=True)
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertEqual(self.writes(tv), [], tv.commands)

    def test_accessibility_switched_off_stays_off(self):
        tv = ListedTV(OURS, bound=False, flag="0")
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertEqual(self.writes(tv), [], tv.commands)
        self.assertFalse([c for c in tv.commands if c.startswith("dumpsys")], tv.commands)

    def test_the_long_component_form_counts_as_the_same_helper(self):
        tv = ListedTV("com.gathin.tvhome/com.gathin.tvhome.KeyRemapService", bound=True)
        bench.restore_accessibility(tv, "com.gathin.tvhome", [OURS])
        self.assertEqual(self.writes(tv), [], tv.commands)


class ColdStartOrderTest(unittest.TestCase):
    def test_services_are_restored_after_the_measurement(self):
        """A `settings put` starts the process; doing it first would measure a warm start."""
        tv = FakeTV(OURS)
        order = []

        original_shell = tv.shell
        def spy(command, read_timeout_s=None, *, retry=False):
            if command.startswith("settings put secure enabled_accessibility_services"):
                order.append("restore")
            elif command.startswith("am start -W ") and command.endswith("-n com.gathin.tvhome/.MainActivity"):
                order.append("measure")
            return original_shell(command, read_timeout_s, retry=retry)
        tv.shell = spy

        # `am start -W` has to answer something with a WaitTime.
        def shell_with_start(command, read_timeout_s=None, *, retry=False):
            out = spy(command, read_timeout_s, retry=retry)
            if command.startswith("am start -W"):
                return "WaitTime: 420"
            return out
        tv.shell = shell_with_start

        with mock.patch.object(bench, "other_app", lambda _tv: "com.android.tv.settings/.MainSettings"):
            bench.cold_start(tv, "com.gathin.tvhome/.MainActivity", 1)

        self.assertIn("measure", order, order)
        self.assertIn("restore", order, order)
        self.assertLess(order.index("measure"), order.index("restore"),
                        f"restore happened before the measurement: {order}")


if __name__ == "__main__":
    unittest.main()

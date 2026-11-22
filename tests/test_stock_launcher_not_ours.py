"""build-and-install.sh refuses a STOCK_LAUNCHER that is this launcher, or not a package name.

STOCK_LAUNCHER names the package the handover disables after the new launcher has started. Given
com.gathin.tvhome - the package name a reader sees most in this repo - the script installed and started
the new launcher and then ran `pm disable-user --user 0 com.gathin.tvhome` on it, leaving the TV with
the stock launcher still enabled and the new one off. A value that is not a package name (a typo with a
space, a shell character) went into the same TV shell line as written. Both are refused now before
anything is built or sent, with exit 1 like the script's other refusals.

Uses the simulated TV from test_launcher_handover.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_launcher_handover as handover  # noqa: E402  (its simulated TV, not its tests)


class StockLauncherNotOursTest(handover.HandoverTest):
    def test_this_launcher_is_not_the_stock_launcher(self):
        result, state = self.handover(env_extra={"STOCK_LAUNCHER": "com.gathin.tvhome"})
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("STOCK_LAUNCHER", result.stderr)
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]), state["commands"])
        self.assertFalse(any("pm install" in c for c in state["commands"]), state["commands"])

    def test_a_value_that_is_not_a_package_name_is_refused(self):
        for value in ("com.google.android.tvlauncher; reboot", "tv launcher"):
            with self.subTest(value=value):
                result, state = self.handover(env_extra={"STOCK_LAUNCHER": value})
                self.assertEqual(result.returncode, 1, result.stderr)
                self.assertIn("not a package name", result.stderr)
                self.assertEqual(state["commands"], [])

    def test_a_stock_package_passed_in_still_hands_over(self):
        result, state = self.handover(env_extra={"STOCK_LAUNCHER": "com.google.android.tvlauncher"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("pm disable-user --user 0 com.google.android.tvlauncher", state["commands"])


# Only the cases above: the inherited handover tests run in their own module.
for name in [n for n in vars(handover.HandoverTest) if n.startswith("test_")]:
    setattr(StockLauncherNotOursTest, name, None)

if __name__ == "__main__":
    unittest.main()

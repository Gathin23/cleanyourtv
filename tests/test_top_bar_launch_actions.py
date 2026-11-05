"""UI wiring for the policy exercised by LaunchActionsTest on the JVM; no device access."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'launcher/app/src/main/java/com/gathin/tvhome'


class TopBarLaunchActionsTest(unittest.TestCase):
    def setUp(self):
        self.source = (JAVA / 'MainActivity.java').read_text()
        self.method = self.source.split('private void addTopBarApps(', 1)[1].split(
            '@Override public void onLibraryUp()', 1)[0]

    def test_top_bar_passes_configured_actions_to_tested_selection_policy(self):
        self.assertIn('addTopBarApps(cfg.topBarApps, cfg.launchActions)', self.source)
        self.assertIn('LaunchActions.select(pkg, launchActions, launch,', self.method)
        self.assertIn('button.setOnClickListener(v -> open(new Intent(target)))', self.method)

    def test_action_is_package_scoped_resolved_and_does_not_replace_missing_apps(self):
        self.assertIn('new Intent(action).setPackage(targetPackage)', self.method)
        self.assertIn('pm.resolveActivity(custom, 0) != null ? custom : null', self.method)
        self.assertLess(self.method.index('if (launch == null) continue'),
                        self.method.index('LaunchActions.select'))
        self.assertIn('getLeanbackLaunchIntentForPackage(pkg)', self.method)
        self.assertIn('getLaunchIntentForPackage(pkg)', self.method)

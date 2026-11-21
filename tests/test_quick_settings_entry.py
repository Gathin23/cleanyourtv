"""The documented action another app starts to open the quick settings panel stays gated.

#4286: on boards where a key-filtering helper eats the gear key, a provider app answers the remapped
key and can now open the panel by starting com.gathin.tvhome.action.QUICK_SETTINGS instead of
Settings.ACTION_SETTINGS, which cannot reach it. The entry point is a component of its own
(QuickSettingsRequestActivity), because the panel activity itself is the launcher's UI: exported=false,
singleTop and noHistory are its own-task behaviour, not an invitation. The gate mirrors
InputsRequestActivity: enabled="false" in the manifest, switched on by MainActivity only while
settings_key_panel is set, so a launcher with the panel off answers nothing extra.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"
MAIN = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "MainActivity.java"
ENTRY = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "QuickSettingsRequestActivity.java"
DOCS = ROOT / "docs" / "launcher.md"

ACTION = "com.gathin.tvhome.action.QUICK_SETTINGS"


def block(text, name):
    """The whole <activity android:name=\".<name>\" ...> element, attributes and intent-filter."""
    tag = re.search(r'<activity\b[^>]*android:name="\.' + name + r'"[^>]*>', text)
    if not tag:
        return ""
    if tag.group(0).rstrip().endswith("/>"):
        return tag.group(0)   # no children: the element closes itself
    end = text.find("</activity>", tag.end())
    return text[tag.start():end + len("</activity>")] if end >= 0 else tag.group(0)


class ManifestTest(unittest.TestCase):
    def setUp(self):
        self.manifest = MANIFEST.read_text(encoding="utf-8")

    def test_the_entry_point_is_declared_and_exported(self):
        element = block(self.manifest, "QuickSettingsRequestActivity")
        self.assertTrue(element, "QuickSettingsRequestActivity is not in the manifest")
        self.assertIn('android:exported="true"', element,
                      "another app must be able to start the action")
        self.assertIn('android:enabled="false"', element,
                      "the entry point ships off, the way InputsRequestActivity does")
        self.assertIn('android:theme="@android:style/Theme.NoDisplay"', element,
                      "the entry point has no UI of its own")

    def test_the_action_is_explicit_and_default_categorised(self):
        element = block(self.manifest, "QuickSettingsRequestActivity")
        self.assertIn(f'<action android:name="{ACTION}" />', element)
        self.assertIn('<category android:name="android.intent.category.DEFAULT" />', element,
                      "without DEFAULT the action is not startable with startActivity")

    def test_the_panel_itself_stays_unexported(self):
        element = block(self.manifest, "QuickSettingsActivity")
        self.assertTrue(element)
        self.assertIn('android:exported="false"', element,
                      "the panel is the launcher's own UI; only the entry point answers outside")


class GateTest(unittest.TestCase):
    def setUp(self):
        self.main = MAIN.read_text(encoding="utf-8")
        self.entry = ENTRY.read_text(encoding="utf-8")

    def test_mainactivity_switches_the_component_with_the_switch(self):
        self.assertRegex(self.main, r"applyPanelRequestHandler\(settingsKeyPanel\)")
        method = re.search(r"private void applyPanelRequestHandler\(boolean on\) \{.*?\n    \}", self.main, re.S)
        self.assertTrue(method, "applyPanelRequestHandler moved or changed shape")
        self.assertIn("QuickSettingsRequestActivity.class", method.group(0))
        self.assertIn("COMPONENT_ENABLED_STATE_ENABLED", method.group(0),
                      "the component is enabled only while the switch is on")

    def test_the_entry_point_forwards_to_the_panel(self):
        self.assertIn("new Intent(this, QuickSettingsActivity.class)", self.entry)
        self.assertIn("FLAG_ACTIVITY_NEW_TASK", self.entry,
                      "starting an activity from a non-visual context needs the new-task flag")
        self.assertIn("finish()", self.entry)

    def test_the_action_is_documented(self):
        docs = DOCS.read_text(encoding="utf-8")
        self.assertIn(f"`{ACTION}`", docs,
                      "docs/launcher.md must name the action a provider app starts")
        self.assertIn("settings_key_panel", docs,
                      "the doc must say the action answers only while the panel is on")


if __name__ == "__main__":
    unittest.main()

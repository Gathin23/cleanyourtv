"""The launcher's Home activity must be direct-boot aware.

After the stock launcher is disabled on Google TV, a cold boot asks for the Home activity while the
user is still locked (direct boot). A component that is not directBootAware cannot be launched then,
so the system falls to the setup wizard's RecoveryActivity and crash-loops to a black screen (#1989).
HomeKeyService and GuardRevival already set android:directBootAware; MainActivity, the Home activity
itself, must too.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"


def component_block(text, name):
    """The <activity>/<service android:name="NAME" ...> ... opening tag's attributes."""
    # The attributes run from the tag start up to the first '>' that closes the opening tag.
    pattern = re.compile(
        r"<(?:activity|service)\b([^>]*?\bandroid:name=\"" + re.escape(name) + r"\"[^>]*?)>",
        re.S,
    )
    match = pattern.search(text)
    if match is None:
        raise AssertionError(f"no <activity>/<service> with android:name={name!r} in the manifest")
    return match.group(1)


class DirectBootAwareTest(unittest.TestCase):
    def setUp(self):
        self.manifest = MANIFEST.read_text(encoding="utf-8")

    def test_mainactivity_is_direct_boot_aware(self):
        block = component_block(self.manifest, ".MainActivity")
        self.assertIn('android:directBootAware="true"', block,
                      "MainActivity must be directBootAware so a cold boot can bring up Home (#1989)")

    def test_the_boot_helpers_are_direct_boot_aware_too(self):
        # The convention this enforces: the components launched before unlock all carry the attribute.
        for name in (".HomeKeyService", ".GuardRevival"):
            self.assertIn('android:directBootAware="true"', component_block(self.manifest, name),
                          f"{name} is expected to stay directBootAware")


if __name__ == "__main__":
    unittest.main()

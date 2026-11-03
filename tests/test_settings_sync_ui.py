"""Tests for settings_sync UI dump parsing without opening a real TV."""
from pathlib import Path
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import settings_sync  # noqa: E402
from settings_sync_fake import FakeAdb  # noqa: E402


UI_XML = '''<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
<hierarchy rotation="0">
  <node text="Impostazioni" content-desc="" bounds="[10,20][110,80]" focused="true" />
  <node text="Backup e ripristino" content-desc="" bounds="[100,100][300,200]" focused="false" />
  <node text="" content-desc="Impostazioni" bounds="[300,100][500,200]" focused="false" />
</hierarchy>
'''


class UiTest(unittest.TestCase):
    def test_ui_prints_focused_and_text_nodes_and_taps_the_text_centre(self) -> None:
        adb = FakeAdb(ui_xml=UI_XML)
        with tempfile.TemporaryDirectory() as directory:
            output = io.StringIO()
            with redirect_stdout(output):
                status = settings_sync.ui("tv", "Backup", adb)
        self.assertEqual(status, 0)
        text = output.getvalue()
        self.assertIn('focused: text="Impostazioni"', text)
        self.assertIn('text="Backup e ripristino"', text)
        self.assertIn('content-desc="Impostazioni"', text)
        self.assertIn("tap: 200,150", text)
        self.assertIn("input tap 200 150", adb.commands)


if __name__ == "__main__":
    unittest.main()

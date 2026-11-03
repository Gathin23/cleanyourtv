"""Tests for settings_sync export without opening a real TV."""
from pathlib import Path
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from typing import Dict, Optional, Union
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import settings_sync  # noqa: E402
from settings_sync_fake import FakeAdb  # noqa: E402


class ExportTest(unittest.TestCase):
    def test_export_preserves_app_backup_layout_and_hides_account_content(self) -> None:
        account_payload = b"private account payload"
        adb = FakeAdb({
            "/sdcard/Android/media/org.smarttube.beta/data/org.smarttube.beta/Backup/files/app_prefs/video_player_data":
                b"0,0,null,vp9.2,1920,1080,60.0,null,true,",
            "/sdcard/Android/media/org.smarttube.beta/data/org.smarttube.beta/Backup/files/global_prefs/media_service_account_data":
                account_payload,
        })
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "settings.zip"
            stdout = io.StringIO()
            with redirect_stdout(stdout):
                status = settings_sync.export_backup("tv", "org.smarttube.beta", output, adb)

            self.assertEqual(status, 0)
            with ZipFile(output) as archive:
                self.assertEqual(sorted(archive.namelist()), [
                    "data/org.smarttube.beta/Backup/files/app_prefs/video_player_data",
                    "data/org.smarttube.beta/Backup/files/global_prefs/media_service_account_data",
                ])
            text = stdout.getvalue()
            self.assertIn("files: 2", text)
            self.assertIn("preset: 1080p 60fps vp9", text)
            self.assertIn("accounts: present", text)
            self.assertNotIn(account_payload.decode(), text)

    def test_export_reports_the_in_app_backup_step_when_missing(self) -> None:
        def empty_shell(command: str) -> str:
            return ""

        adb = FakeAdb(forbid_push=True)
        adb.shell = empty_shell
        with tempfile.TemporaryDirectory() as directory:
            stdout, stderr = io.StringIO(), io.StringIO()
            with redirect_stdout(stdout), redirect_stderr(stderr):
                status = settings_sync.export_backup("tv", "org.smarttube.beta",
                                                    Path(directory) / "settings.zip", adb)
            self.assertNotEqual(status, 0)
            self.assertIn("Backup locale > Backup dati app", stdout.getvalue())


if __name__ == "__main__":
    unittest.main()

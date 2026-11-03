"""Tests for settings_sync import without opening a real TV."""
from pathlib import Path
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from typing import List, Union
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import settings_sync  # noqa: E402
from settings_sync_fake import FakeAdb  # noqa: E402


class ImportTest(unittest.TestCase):
    def make_zip(self, directory: Union[str, Path]) -> Path:
        path = Path(directory) / "settings.zip"
        with ZipFile(path, "w") as archive:
            archive.writestr(
                "data/org.smarttube.beta/Backup/files/app_prefs/video_player_data",
                b"preset",
            )
            archive.writestr(
                "data/org.smarttube.beta/Backup/files/global_prefs/media_service_account_data",
                b"private account payload",
            )
        return path

    def test_import_pushes_to_target_and_prints_restore_steps_and_keys(self) -> None:
        adb = FakeAdb(forbid_push=False, forbid_pull=True)
        with tempfile.TemporaryDirectory() as directory:
            archive = self.make_zip(directory)
            stdout = io.StringIO()
            with redirect_stdout(stdout):
                status = settings_sync.import_backup(
                    "tv", "io.yash.meetube.tv", "org.smarttube.beta", archive, True, adb)

        self.assertEqual(status, 0)
        self.assertIn("Backup e ripristino", stdout.getvalue())
        self.assertIn("Ripristino dati app", stdout.getvalue())
        self.assertIn("DPAD", stdout.getvalue())
        self.assertTrue(any("/io.yash.meetube.tv/data/org.smarttube.beta/Backup" in command
                            for command in adb.commands))
        self.assertTrue(any(command.startswith("input keyevent KEYCODE_DPAD_")
                            for command in adb.commands))
        self.assertNotIn("private account payload", stdout.getvalue())

    def test_import_refuses_an_unknown_target_before_connecting(self) -> None:
        called: List[str] = []

        def record_runner(tv: str) -> None:
            called.append(tv)

        with tempfile.TemporaryDirectory() as directory:
            archive = self.make_zip(directory)
            stdout = io.StringIO()
            with redirect_stdout(stdout):
                status = settings_sync.main([
                    "import", "tv", "--app", "com.example.other",
                    "--from", "org.smarttube.beta", "--zip", str(archive)],
                    runner_factory=record_runner)
        self.assertNotEqual(status, 0)
        self.assertEqual(called, [])
        self.assertIn("refusing target package", stdout.getvalue())


if __name__ == "__main__":
    unittest.main()

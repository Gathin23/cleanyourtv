"""An unreadable local theme candidate must not hide a later match in manage.py status."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("manage_status_themes", ROOT / "tools" / "manage.py")
manage = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(manage)


class StatusThemesTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.broken = self.root / "themes" / "broken" / "config.json"
        self.working = self.root / "themes" / "working" / "config.json"
        for path in (self.broken, self.working):
            path.parent.mkdir(parents=True)
        self.pushed = json.dumps({"columns": 7, "greeting_name": "Room"})
        self.working.write_text(self.pushed, encoding="utf-8")
        self.broken.write_text("{", encoding="utf-8")

    def status(self, config):
        out = io.StringIO()
        with mock.patch.object(manage, "ROOT", self.root), \
                mock.patch.object(manage, "installed_packages", return_value={manage.LAUNCHER_PKG}), \
                mock.patch.object(manage, "addons", return_value=[]), \
                mock.patch.object(manage, "themes", return_value=[{"id": "broken"}, {"id": "working"}]), \
                mock.patch.object(manage, "tv", return_value=config) as tv, \
                contextlib.redirect_stdout(out):
            code = manage.cmd_status()
        tv.assert_called_once_with(f"cat /sdcard/Android/data/{manage.LAUNCHER_PKG}/files/config.json 2>/dev/null")
        self.assertIn("launcher: installed", out.getvalue())
        return code, out.getvalue()

    def test_a_bad_candidate_does_not_hide_a_later_matching_theme(self):
        for contents in (b'{"columns":', b"\xff"):
            with self.subTest(contents=contents):
                self.broken.write_bytes(contents)
                code, out = self.status(self.pushed)
                self.assertEqual(code, 0)
                self.assertIn("theme: working", out)

    def test_a_candidate_read_error_does_not_abort_status(self):
        read_text = Path.read_text

        def read(path, *args, **kwargs):
            if path == self.broken:
                raise PermissionError("candidate is unreadable")
            return read_text(path, *args, **kwargs)

        with mock.patch.object(Path, "read_text", read):
            code, out = self.status(self.pushed)
        self.assertEqual(code, 0)
        self.assertIn("theme: working", out)

    def test_missing_candidates_and_other_tv_config_shapes_keep_their_status(self):
        self.broken.unlink()
        for config, label in ((self.pushed, "working"), ("{}", "your own"),
                              ("{", "your own"), ("", "none (the built-in config.xml)")):
            with self.subTest(config=config):
                code, out = self.status(config)
                self.assertEqual(code, 0)
                self.assertIn("theme: " + label, out)

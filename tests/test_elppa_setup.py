import importlib.util
from pathlib import Path
import tempfile
import json
import types
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('elppa',Path(__file__).resolve().parents[1]/'tools/elppa.py')
elppa=importlib.util.module_from_spec(spec);spec.loader.exec_module(elppa)

class ElppaSetupTest(unittest.TestCase):
    def test_component_parsing_rejects_noise_and_shell_text(self):
        self.assertEqual('com.vendor.home/.Main',elppa.parse_component('priority=0\ncom.vendor.home/.Main\n'))
        for value in ['No activity found', 'com.vendor.home/.Main;reboot', 'com.vendor.home/.Main another']:
            with self.assertRaises(RuntimeError):elppa.parse_component(value)
    def test_restore_cannot_target_another_endpoint_or_chooser(self):
        args=types.SimpleNamespace(serial='test-tv',adb='adb')
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'restore.json'
            for data in [{'serial':'other','home':'com.vendor.home/.Main'},{'serial':'test-tv','home':'android/.ResolverActivity'}]:
                p.write_text(json.dumps(data))
                with patch.object(elppa,'shell') as command:
                    with self.assertRaises(RuntimeError):elppa.restore(args,p)
                    command.assert_not_called()
    def test_failed_home_restore_is_not_reported_successful(self):
        args=types.SimpleNamespace(serial='test-tv',adb='adb')
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'restore.json';p.write_text(json.dumps({'serial':'test-tv','home':'com.vendor.home/.Main'}))
            with patch.object(elppa,'shell',return_value='Success') as command,patch.object(elppa,'home',return_value=elppa.COMPONENT):
                with self.assertRaises(RuntimeError):elppa.restore(args,p)
                self.assertEqual(1,command.call_count)
    def test_home_backup_refuses_chooser_before_mutating_tv(self):
        args=types.SimpleNamespace(serial='test-tv',adb='adb')
        with patch.object(elppa,'home',return_value='android/.ResolverActivity'),patch.object(elppa,'shell') as command:
            with self.assertRaises(RuntimeError):elppa.backup(args)
            command.assert_not_called()
    def test_security_exception_with_zero_exit_is_failure(self):
        args=types.SimpleNamespace(serial='test-tv',adb='adb')
        response=types.SimpleNamespace(returncode=0,stdout='Security exception: permission is not changeable',stderr='')
        with patch.object(elppa.subprocess,'run',return_value=response):
            with self.assertRaises(RuntimeError):elppa.adb(args,'shell','pm','grant')

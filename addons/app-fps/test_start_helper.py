import contextlib
import hashlib
import importlib.util
import io
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('start_helper',HERE/'start-helper.py')
bootstrap=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)

class Device:
    def __init__(self,pid='',command='',provider='Row: 0 enabled=0',checksum_ok=True):
        self.pid=pid;self.command=command;self.provider=provider;self.checksum_ok=checksum_ok
        self.commands=[];self.pushes=[];self.closed=False
    def shell(self,command):
        self.commands.append(command)
        if command.startswith('cat '+bootstrap.BASE+'/helper.pid'):return self.pid
        if '/cmdline ' in command:return self.command
        if command.startswith('command -v'):return '\n'.join('/system/bin/'+x for x in ('setsid','timeout','awk','content'))
        if command.startswith('content query'):return self.provider
        if command.startswith('sha256sum'):
            name=command.split('/')[-1].removesuffix('.new')
            return (hashlib.sha256((HERE/'helper'/name).read_bytes()).hexdigest() if self.checksum_ok else 'bad')+'  source'
        return ''
    def push(self,source,destination):self.pushes.append((source,destination))
    def close(self):self.closed=True

class BootstrapTest(unittest.TestCase):
    def run_bootstrap(self,device,*args):
        with patch.dict(sys.modules,{'tv':types.SimpleNamespace(connect=lambda:device)}),patch.object(sys,'argv',['start-helper.py',*args]),contextlib.redirect_stdout(io.StringIO()):
            bootstrap.main()
    def test_stop_does_not_kill_reused_pid(self):
        d=Device(pid='123',command='unrelated process')
        self.run_bootstrap(d,'--stop')
        self.assertFalse(any(c.startswith('kill ') for c in d.commands));self.assertTrue(d.closed)
    def test_invalid_pid_is_not_interpolated(self):
        d=Device(pid='123; bad')
        self.run_bootstrap(d,'--stop')
        self.assertFalse(any('/proc/' in c or c.startswith('kill ') for c in d.commands))
    def test_running_unchanged_helper_is_not_duplicated(self):
        d=Device(pid='123',command='sh '+bootstrap.BASE+'/run.sh')
        self.run_bootstrap(d)
        self.assertFalse(d.pushes);self.assertFalse(any('setsid sh' in c or c.startswith('kill ') for c in d.commands))
    def test_missing_provider_stops_before_upload(self):
        d=Device(provider='No result found.')
        with self.assertRaisesRegex(RuntimeError,'Install'):self.run_bootstrap(d)
        self.assertFalse(d.pushes);self.assertTrue(d.closed)
    def test_checksum_mismatch_never_executes_uploaded_script(self):
        d=Device(checksum_ok=False)
        with self.assertRaisesRegex(RuntimeError,'checksum'):self.run_bootstrap(d)
        self.assertFalse(any(c.startswith('mv ') or 'setsid sh' in c for c in d.commands));self.assertTrue(d.closed)

if __name__=='__main__':unittest.main()

#!/usr/bin/env python3
"""Start/stop the fixed-purpose FPS helper over ADB; the computer can then disconnect."""
import argparse
import hashlib
from pathlib import Path
import re
import shlex
import sys
import time

BASE = '/data/local/tmp/app-fps-helper'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stop', action='store_true', help='Stop the local shell helper')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(root / 'tools'))
    import tv
    dev = tv.connect()
    try:
        pid = dev.shell(f'cat {BASE}/helper.pid 2>/dev/null').strip()
        live = False
        if re.fullmatch(r'[1-9][0-9]*', pid):
            command = dev.shell(f"cat /proc/{pid}/cmdline 2>/dev/null | tr '\\000' ' '")
            live = BASE + '/run.sh' in command
        same_files = False
        if live and not args.stop:
            same_files = all(
                hashlib.sha256((Path(__file__).with_name('helper') / name).read_bytes()).hexdigest()
                in dev.shell('sha256sum ' + shlex.quote(BASE + '/' + name))
                for name in ('run.sh', 'select-layer.awk'))
        if live and same_files:
            print('Helper already running. The computer can disconnect.')
            return
        if live:
            dev.shell(f'kill -TERM {pid}')
            for _ in range(12):
                command = dev.shell(f"cat /proc/{pid}/cmdline 2>/dev/null | tr '\\000' ' '")
                if BASE + '/run.sh' not in command:
                    break
                time.sleep(.5)
            else:
                raise RuntimeError('Helper did not stop; refusing to start a duplicate')
        if args.stop:
            print('Helper stopped.' if live else 'Helper is not running.')
            return
        capabilities = dev.shell('command -v setsid; command -v timeout; command -v awk; command -v content')
        if len(capabilities.strip().splitlines()) != 4:
            raise RuntimeError('TV lacks a required shell command; nothing started')
        check = dev.shell('content query --uri content://com.lionlev.tv.fps.state/state')
        if not re.search(r'\benabled=[01]\b', check):
            raise RuntimeError('Install the local-helper version of Frames Counter first')
        dev.shell(f'mkdir -p {BASE}; chmod 700 {BASE}; rmdir {BASE}/run.lock 2>/dev/null')
        for name in ('run.sh', 'select-layer.awk'):
            source = Path(__file__).with_name('helper') / name
            remote = BASE + '/' + name
            dev.push(str(source), remote + '.new')
            checksum = dev.shell('sha256sum ' + shlex.quote(remote + '.new')).split()[0]
            if checksum != hashlib.sha256(source.read_bytes()).hexdigest():
                raise RuntimeError('Helper transfer checksum mismatch')
            dev.shell(f'mv {shlex.quote(remote + ".new")} {shlex.quote(remote)}; chmod 700 {shlex.quote(remote)}')
        # A startup handshake prevents adbd closing the initial shell before setsid has detached.
        dev.shell(f'setsid sh {BASE}/run.sh </dev/null >{BASE}/helper.log 2>&1 & sleep 1')
        for _ in range(5):
            pid = dev.shell(f'cat {BASE}/helper.pid 2>/dev/null').strip()
            if re.fullmatch(r'[1-9][0-9]*', pid) and 'started pid=' in dev.shell(f'cat {BASE}/helper.log'):
                print('TV-local helper started. Enable Frames Counter on the TV; the computer can disconnect.')
                print('Repeat after reboot. Use --stop to stop the helper itself.')
                return
            time.sleep(.5)
        raise RuntimeError('Helper startup not confirmed; inspect the device-local helper.log')
    finally:
        dev.close()


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""One-time, explicit diagnostic/Usage Access setup. Default is read-only status."""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import time

PACKAGE = 'com.lionlev.tv.fps'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--grant', action='store_true', help='Grant persistent DUMP and Usage Access after reviewing README')
    mode.add_argument('--revoke', action='store_true', help='Stop measurement and revoke both grants')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    if args.grant:
        subprocess.run([sys.executable, str(Path(__file__).with_name('start-helper.py')), '--stop'], check=True)
    sys.path.insert(0, str(root / 'tools'))
    import tv
    dev = tv.connect()
    try:
        if not dev.shell('pm path ' + PACKAGE).strip().startswith('package:'):
            raise RuntimeError('Install Frames Counter first')
        if args.grant:
            print('Granting DUMP (broad system diagnostics) and Usage Access (app-usage history).')
            print('These grants persist across reboot until revoked; the app uses fixed frame queries and recent foreground events only.')
            dev.shell('pm grant ' + PACKAGE + ' android.permission.DUMP')
            dev.shell('appops set ' + PACKAGE + ' GET_USAGE_STATS allow')
        elif args.revoke:
            was_running = 'ServiceRecord{' in dev.shell('dumpsys activity services ' + PACKAGE)
            dev.shell('am broadcast -a ' + PACKAGE + '.STOP -n ' + PACKAGE + '/.SampleReceiver')
            time.sleep(1)
            if was_running:
                dev.shell('dumpsys SurfaceFlinger --timestats -disable')
            dev.shell('appops set ' + PACKAGE + ' GET_USAGE_STATS default')
            dev.shell('pm revoke ' + PACKAGE + ' android.permission.DUMP')
        dump = bool(re.search(r'android\.permission\.DUMP: granted=true', dev.shell('dumpsys package ' + PACKAGE)))
        usage = bool(re.search(r'GET_USAGE_STATS:\s*allow', dev.shell('appops get ' + PACKAGE + ' GET_USAGE_STATS')))
        print('DUMP:', 'granted' if dump else 'not granted')
        print('Usage Access:', 'allowed' if usage else 'not allowed')
        if args.grant and not (dump and usage):
            raise RuntimeError('Standalone setup was not confirmed; no workaround attempted')
        if dump and usage:
            print('Ready. Enable Frames Counter on the TV. No helper or computer is needed after reboot.')
    finally:
        dev.close()


if __name__ == '__main__':
    main()

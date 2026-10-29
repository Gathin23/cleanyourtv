#!/usr/bin/env python3
"""Host-only FPS sampler. Uses the repository ADB client; no network permission on TV."""
import argparse
from pathlib import Path
import re
import shlex
import sys
import time

PACKAGE = 'com.lionlev.tv.fps'


def counters(text):
    """Return legacy per-layer frame totals, never global/display counters."""
    result = {}
    for block in re.split(r'(?m)^layerName = ', text)[1:]:
        name = block.splitlines()[0].strip()
        count = re.search(r'(?m)^totalFrames = (\d+)$', block)
        if count:
            if name in result:
                result[name] = None  # ambiguous aggregation is not a measurement
            else:
                result[name] = int(count[1])
    return result


class Rate:
    def __init__(self):
        self.previous = None

    def sample(self, count, now):
        old = self.previous
        self.previous = (count, now) if count is not None else None
        if old is None or count is None or count < old[0] or now <= old[1]:
            return None
        value = (count - old[0]) / (now - old[1])
        return value if 0 <= value <= 1000 else None


def layer_available(text, layer):
    return any(line == layer or line.endswith(" " + layer) for line in text.splitlines())


def is_foreground(text, package):
    return any(("mResumedActivity:" in line or "topResumedActivity=" in line)
               and re.search(r"(?<![A-Za-z0-9_.])" + re.escape(package) + r"/", line)
               for line in text.splitlines())


def send(dev, value, app):
    args = ['am', 'broadcast', '-a', PACKAGE + '.SAMPLE', '-n', PACKAGE + '/.SampleReceiver',
            '--es', 'fps', str(value if value is not None else -1), '--es', 'app', app]
    return dev.shell(' '.join(shlex.quote(x) for x in args))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--package', required=True, help='Exact Android app package to measure')
    p.add_argument('--layer', help='Exact layer from --list; no automatic layer guessing')
    p.add_argument('--list', action='store_true', help='List candidate layers; no capture started')
    p.add_argument('--seconds', type=int, default=60, help='Bounded session, 1–3600 seconds')
    a = p.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.]{1,160}', a.package) or a.package == PACKAGE:
        p.error('select another app package')
    if a.package == 'com.gathin.tvhome':
        p.error('Home cannot be an FPS target')
    if not 1 <= a.seconds <= 3600:
        p.error('--seconds must be between 1 and 3600')
    if not a.list and (not a.layer or a.package + '/' not in a.layer):
        p.error('--layer must name a layer belonging to the selected package')
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
    import tv
    dev = tv.connect()
    enabled = False
    try:
        home = dev.shell('cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
        home_packages = set(re.findall(r'(?m)^([A-Za-z0-9_.]+)/', home)) | {'com.gathin.tvhome'}
        if a.package in home_packages:
            p.error('Home cannot be an FPS target')
        if a.list:
            live_layers = dev.shell('dumpsys SurfaceFlinger --list')
            for layer in counters(dev.shell('dumpsys SurfaceFlinger --timestats -dump')):
                if a.package + '/' in layer and layer_available(live_layers, layer):
                    print(layer)
            return
        dev.shell('dumpsys SurfaceFlinger --timestats -enable')
        enabled = True
        rate = Rate()
        end = time.monotonic() + a.seconds
        while time.monotonic() < end:
            start = time.monotonic()
            raw = dev.shell('dumpsys SurfaceFlinger --timestats -dump')
            now = (start + time.monotonic()) / 2
            active = is_foreground(dev.shell('dumpsys activity activities'), a.package)
            present = layer_available(dev.shell('dumpsys SurfaceFlinger --list'), a.layer) if active else False
            value = rate.sample(counters(raw).get(a.layer) if active and present else None, now)
            send(dev, value, a.package)
            print('unavailable' if value is None else f'{value:.1f} FPS', flush=True)
            time.sleep(max(0, 1 - (time.monotonic() - start)))
    except KeyboardInterrupt:
        pass
    finally:
        try:
            if enabled:
                try:
                    send(dev, None, a.package)
                finally:
                    dev.shell('dumpsys SurfaceFlinger --timestats -disable')
        finally:
            dev.close()


if __name__ == '__main__':
    main()

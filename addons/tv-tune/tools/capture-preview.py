#!/usr/bin/env python3
"""Import a reviewed neutral capture into TV Tune's app-only preview over ADB."""
import argparse
import json
import math
from pathlib import Path
import subprocess

def number(value, low, high):
    return type(value) in (int, float) and low <= value <= high and math.isfinite(value)

def load_capture(path):
    path = Path(path)
    if path.stat().st_size > 262144:
        raise ValueError('Capture report exceeds 256 KiB.')
    data = json.loads(path.read_text())
    if not isinstance(data, dict) or any(data.get(key) != value for key, value in {
        'format': 'tv-tune-compatibility-v1', 'status': 'experimental',
        'source': 'processed-video', 'patchId': 'grey-128',
        'presentation': 'manually-confirmed'}.items()) or data.get('colourMeasurementValidated') is not False:
        raise ValueError('Use a reviewed, manually confirmed grey-128 TV Tune camera report.')
    quality = data.get('quality')
    if not isinstance(quality, dict):
        raise ValueError('Capture quality is missing.')
    means, deviations = quality.get('meanRgb'), quality.get('stdDevRgb')
    if (type(quality.get('sampleCount')) is not int or not 15 <= quality['sampleCount'] <= 45
        or not number(quality.get('durationMs'), 1, 5000)
        or not isinstance(means, list) or len(means) != 3 or not all(number(v, 16, 239) for v in means)
        or not isinstance(deviations, list) or len(deviations) != 3 or not all(number(v, 0, 2) for v in deviations)
        or not number(quality.get('maxClippedFraction'), 0, 0.01) or quality.get('warnings') != []):
        raise ValueError('Capture failed the bounded frame, clipping, stability or neutral-code checks.')
    average = sum(means) / 3
    if any(not 0.75 <= average / value <= 4 / 3 for value in means):
        raise ValueError('Sample requires gains outside the preview range.')
    return means

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--report', type=Path, help='Reviewed grey-128 companion JSON; images are not read.')
    source.add_argument('--demo', action='store_true', help='Use clearly labelled synthetic data, not a TV measurement.')
    parser.add_argument('--serial', help='Explicit adb serial of the owned, connected TV.')
    parser.add_argument('--check-only', action='store_true', help='Validate input without contacting a TV.')
    args = parser.parse_args()
    try:
        means = [144, 128, 112] if args.demo else load_capture(args.report)
    except (ValueError, OSError) as error:
        parser.exit(2, str(error) + '\n')
    print(json.dumps({'scope': 'tv-tune-preview-only', 'source': 'synthetic' if args.demo else 'processed-camera',
                      'observedRgb': means, 'colourMeasurementValidated': False}), flush=True)
    if args.check_only:
        return
    if not args.serial:
        parser.error('--serial is required to contact a TV.')
    command = ['adb', '-s', args.serial, 'shell', 'am', 'start', '-W', '-n',
               'io.github.kalt2212.tvtune/.CorrectionPreviewActivity', '--es', 'source',
               'demo' if args.demo else 'camera']
    for channel, value in zip(('r', 'g', 'b'), means):
        command.extend(['--es', 'observed_' + channel, format(value, '.17g')])
    result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=25)
    if 'Error:' in result.stdout or 'Exception' in result.stdout:
        raise RuntimeError('Android refused the preview activity: ' + result.stdout)
    print(result.stdout)

if __name__ == '__main__':
    main()

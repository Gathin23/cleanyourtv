import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('capture_preview', Path(__file__).with_name('capture-preview.py'))
PREVIEW = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREVIEW)

def report():
    return {'format': 'tv-tune-compatibility-v1', 'source': 'processed-video',
            'status': 'experimental', 'colourMeasurementValidated': False,
            'patchId': 'grey-128', 'presentation': 'manually-confirmed',
            'quality': {'sampleCount': 30, 'durationMs': 2000, 'meanRgb': [144, 128, 112],
                        'stdDevRgb': [0.5, 0.5, 0.5], 'maxClippedFraction': 0, 'warnings': []}}

class CapturePreviewTest(unittest.TestCase):
    def test_imports_only_an_explicit_confirmed_neutral_capture(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'capture.json'; source.write_text(json.dumps(report()))
            self.assertEqual(PREVIEW.load_capture(source), [144, 128, 112])

    def test_refuses_unconfirmed_non_neutral_or_falsely_validated_reports(self):
        for key, value in [('presentation', 'unconfirmed'), ('patchId', 'red'),
                           ('colourMeasurementValidated', True), ('source', 'raw'), ('format', 'unknown')]:
            data = report(); data[key] = value
            with self.subTest(key=key), tempfile.TemporaryDirectory() as directory:
                source = Path(directory) / 'capture.json'; source.write_text(json.dumps(data))
                with self.assertRaises(ValueError): PREVIEW.load_capture(source)

    def test_refuses_low_quality_or_extreme_samples_before_contacting_adb(self):
        for key, value in [('sampleCount', 14), ('sampleCount', True), ('durationMs', 5001),
                           ('stdDevRgb', [0, 3, 0]), ('maxClippedFraction', .02),
                           ('warnings', ['camera_settings_changed']), ('meanRgb', [128, float('nan'), 128]),
                           ('meanRgb', [True, 128, 128]), ('meanRgb', [10**1000, 128, 128]),
                           ('meanRgb', [220, 32, 32])]:
            data = report(); data['quality'][key] = value
            with self.subTest(key=key, value=value), tempfile.TemporaryDirectory() as directory:
                source = Path(directory) / 'capture.json'; source.write_text(json.dumps(data))
                with self.assertRaises(ValueError): PREVIEW.load_capture(source)

    def test_refuses_oversized_input(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'capture.json'; source.write_text(' ' * 262145)
            with self.assertRaises(ValueError): PREVIEW.load_capture(source)

if __name__ == '__main__':
    unittest.main()

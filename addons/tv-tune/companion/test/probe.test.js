import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectCapabilities, summariseSamples, exportReport } from '../src/probe.js';

const sample = (timestampMs, rgb = [80, 100, 120], clippedFraction = 0) =>
  ({ timestampMs, rgb, clippedFraction });
const stable = () => Array.from({ length: 15 }, (_, i) => sample(i * 40));

test('insecure_origin_blocks_camera', () => {
  const result = inspectCapabilities({ secureContext: false, mediaAvailable: true });
  assert.equal(result.cameraAvailable, false);
  assert.ok(result.limitations.includes('secure_origin_required'));
});
test('missing_capabilities_remain_unknown', () => {
  const result = inspectCapabilities({ secureContext: true, mediaAvailable: true });
  assert.deepEqual(result.manualControls, []);
  assert.deepEqual(result.settings, {});
  assert.ok(result.limitations.includes('camera_controls_unknown'));
});
test('manual_controls_do_not_validate_colour', () => {
  const result = inspectCapabilities({ secureContext: true, mediaAvailable: true,
    capabilities: { exposureMode: ['manual', 'continuous'], whiteBalanceMode: ['manual'] },
    settings: { exposureMode: 'continuous', whiteBalanceMode: 'manual', deviceId: 'secret' } });
  assert.deepEqual(result.manualControls, ['exposureMode', 'whiteBalanceMode']);
  assert.equal(result.settings.exposureMode, 'continuous');
  assert.equal(result.colourMeasurementValidated, false);
  assert.equal(result.source, 'processed-video');
  assert.ok(result.limitations.includes('automatic_exposure'));
  assert.equal('deviceId' in result.settings, false);
});
test('unavailable_camera_api_does_not_report_available', () => {
  assert.equal(inspectCapabilities({ secureContext: true, mediaAvailable: false }).cameraAvailable, false);
});
test('identical_frames_have_zero_population_variance', () => {
  const report = summariseSamples(stable());
  assert.deepEqual(report.meanRgb, [80, 100, 120]);
  assert.deepEqual(report.stdDevRgb, [0, 0, 0]);
  assert.equal(report.sampleCount, 15);
  assert.equal(report.durationMs, 560);
  assert.deepEqual(report.warnings, []);
});
test('population_variance_uses_every_frame', () => {
  const report = summariseSamples([sample(0, [0, 10, 20]), sample(40, [4, 14, 24])]);
  assert.deepEqual(report.meanRgb, [2, 12, 22]);
  assert.deepEqual(report.stdDevRgb, [2, 2, 2]);
  assert.ok(report.warnings.includes('insufficient_frames'));
});
test('duplicate_timestamps_are_rejected', () => {
  assert.throws(() => summariseSamples([sample(5), sample(5)]), /increasing/i);
  assert.throws(() => summariseSamples([sample(6), sample(5)]), /increasing/i);
});
test('invalid_samples_are_rejected_instead_of_averaged', () => {
  for (const bad of [sample(NaN), sample(1, [Infinity, 0, 0]), sample(1, [-1, 1, 1]),
    sample(1, [256, 1, 1]), sample(1, [1, 2]), sample(1, [1, 2, 3], 1.1),
    sample(1, [1, 2, 3], -0.1), sample(1, [1, 2, 3], NaN)]) {
    assert.throws(() => summariseSamples([bad]), /invalid/i);
  }
  assert.throws(() => summariseSamples([]), /sample/i);
});
test('clipping_is_not_hidden_by_averaging', () => {
  const frames = stable();
  frames[0].clippedFraction = 0.02;
  const report = summariseSamples(frames);
  assert.ok(report.warnings.includes('clipping_observed'));
  assert.equal(report.maxClippedFraction, 0.02);
});
test('temporal_variation_is_a_quality_warning_not_a_colour_score', () => {
  const report = summariseSamples([sample(0, [20, 20, 20]), sample(40, [100, 100, 100])]);
  assert.ok(report.warnings.includes('temporal_variation'));
  assert.equal('accuracy' in report, false);
});
test('export_removes_identifiers_recursively', () => {
  const report = exportReport({ patchId: 'grey-128', presentationConfirmed: true,
    capabilities: { secureContext: true, mediaAvailable: true,
      capabilities: { whiteBalanceMode: ['manual'] }, settings: { deviceId: 'DO_NOT_EXPORT',
        label: 'DO_NOT_EXPORT', whiteBalanceMode: 'manual' } },
    quality: { ...summariseSamples(stable()), images: ['DO_NOT_EXPORT'], location: 'DO_NOT_EXPORT' },
    deviceId: 'DO_NOT_EXPORT', userAgent: 'DO_NOT_EXPORT', url: 'DO_NOT_EXPORT',
    rawFrames: ['DO_NOT_EXPORT'], calibrationValidated: true });
  assert.equal(JSON.stringify(report).includes('DO_NOT_EXPORT'), false);
  assert.equal(report.colourMeasurementValidated, false);
  assert.equal(report.status, 'experimental');
  assert.equal(report.patchId, 'grey-128');
  assert.equal(report.presentation, 'manually-confirmed');
  assert.equal(report.quality.sampleCount, 15);
});
test('export_does_not_accept_a_caller_supplied_colour_validation_claim', () => {
  const report = exportReport({ colourMeasurementValidated: true,
    capabilities: { colourMeasurementValidated: true }, patchId: 'https://private-device/' });
  assert.equal(report.colourMeasurementValidated, false);
  assert.equal(report.patchId, null);
  assert.equal(report.quality, null);
});


test('export_warns_only_for_differences_in_observed_allowlisted_settings', () => {
  for (const [name, before, after] of [
    ['exposureMode', 'manual', 'continuous'], ['whiteBalanceMode', 'single-shot', 'manual'],
    ['focusMode', 'continuous', 'none'], ['width', 640, 720], ['height', 480, 1280],
    ['frameRate', 30, 25], ['exposureTime', 100, 200], ['iso', 100, 200]
  ]) {
    const report = exportReport({ settingsBeforeCapture: { [name]: before },
      capabilities: { settings: { [name]: after } }, quality: summariseSamples(stable()) });
    assert.deepEqual(report.quality.warnings, ['camera_settings_changed'], name);
    assert.equal(report.format, 'tv-tune-compatibility-v1');
    assert.equal(report.colourMeasurementValidated, false);
  }
});

test('missing_invalid_or_matching_settings_do_not_establish_a_change_or_lock', () => {
  for (const [before, after] of [[{}, {}], [{ exposureMode: 'manual' }, {}],
    [{}, { exposureMode: 'manual' }], [{ exposureMode: 'manual' }, { exposureMode: 'manual' }],
    [{ iso: NaN }, { iso: 100 }], [{ focusMode: 'private' }, { focusMode: 'manual' }]]) {
    const report = exportReport({ settingsBeforeCapture: before,
      capabilities: { settings: after }, quality: summariseSamples(stable()) });
    assert.deepEqual(report.quality.warnings, []);
    assert.ok(report.limitations.some(text => /start and end/.test(text) && /lock/.test(text)));
  }
});

test('settings_comparison_cannot_export_identifiers_or_trust_a_caller_change_warning', () => {
  const before = { exposureMode: 'manual', deviceId: 'DO_NOT_EXPORT_BEFORE', groupId: 'DO_NOT_EXPORT_BEFORE',
    label: 'DO_NOT_EXPORT_BEFORE', url: 'DO_NOT_EXPORT_BEFORE', focusDistance: 2 };
  const after = { exposureMode: 'manual', deviceId: 'DO_NOT_EXPORT_AFTER', groupId: 'DO_NOT_EXPORT_AFTER',
    label: 'DO_NOT_EXPORT_AFTER', url: 'DO_NOT_EXPORT_AFTER', focusDistance: 3 };
  const quality = { ...summariseSamples(stable()), warnings: ['camera_settings_changed', 'DO_NOT_EXPORT_WARNING'] };
  const report = exportReport({ settingsBeforeCapture: before, capabilities: { settings: after }, quality });
  assert.equal(JSON.stringify(report).includes('DO_NOT_EXPORT'), false);
  assert.deepEqual(report.capabilities.settings, { exposureMode: 'manual' });
  assert.deepEqual(report.quality.warnings, []);
  assert.deepEqual(quality.warnings, ['camera_settings_changed', 'DO_NOT_EXPORT_WARNING'], 'do not mutate input');
  assert.equal('settingsBeforeCapture' in report, false);
});

test('none_or_unknown_modes_are_not_labelled_automatic', () => {
  for (const value of ['none', undefined, 'unrecognised']) {
    const result = inspectCapabilities({ settings: { exposureMode: value, whiteBalanceMode: value } });
    assert.equal(result.limitations.includes('automatic_exposure'), false);
    assert.equal(result.limitations.includes('automatic_white_balance'), false);
  }
  for (const value of ['continuous', 'single-shot']) {
    const result = inspectCapabilities({ settings: { exposureMode: value, whiteBalanceMode: value } });
    assert.ok(result.limitations.includes('automatic_exposure'));
    assert.ok(result.limitations.includes('automatic_white_balance'));
  }
});

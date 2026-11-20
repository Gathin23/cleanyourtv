const MODES = ['manual', 'continuous', 'single-shot', 'none'];
const CONTROLS = ['exposureMode', 'whiteBalanceMode', 'focusMode'];
const WARNINGS = ['insufficient_frames', 'clipping_observed', 'temporal_variation'];
const bounded = (value, min, max) => typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;

/** Keep advertised manual capability separate from allowlisted observed settings. */
export function inspectCapabilities({ secureContext = false, mediaAvailable = false,
  capabilities, settings = {} } = {}) {
  const manualControls = CONTROLS.filter(name => Array.isArray(capabilities?.[name]) && capabilities[name].includes('manual'));
  const safeSettings = {};
  for (const name of CONTROLS) if (MODES.includes(settings?.[name])) safeSettings[name] = settings[name];
  for (const name of ['width', 'height', 'frameRate', 'exposureTime', 'iso']) {
    if (bounded(settings?.[name], 0, 1e9)) safeSettings[name] = settings[name];
  }
  const limitations = ['processed_video_not_colourimetry', 'reference_instrument_required'];
  if (!secureContext) limitations.push('secure_origin_required');
  if (!mediaAvailable) limitations.push('camera_api_unavailable');
  if (!CONTROLS.some(name => Array.isArray(capabilities?.[name]))) limitations.push('camera_controls_unknown');
  if (['continuous', 'single-shot'].includes(safeSettings.exposureMode)) limitations.push('automatic_exposure');
  if (['continuous', 'single-shot'].includes(safeSettings.whiteBalanceMode)) limitations.push('automatic_white_balance');
  return { secureContext: secureContext === true, cameraAvailable: secureContext === true && mediaAvailable === true,
    manualControls, settings: safeSettings, source: 'processed-video', colourMeasurementValidated: false, limitations };
}

/** Population statistics over distinct, monotonically increasing video frames. */
export function summariseSamples(samples) {
  if (!Array.isArray(samples) || samples.length === 0) throw new Error('At least one sample is required');
  const meanRgb = [0, 0, 0], m2 = [0, 0, 0];
  let previous = -Infinity, maxClippedFraction = 0;
  samples.forEach((sample, index) => {
    if (!bounded(sample?.timestampMs, 0, Number.MAX_SAFE_INTEGER) ||
      !Array.isArray(sample.rgb) || sample.rgb.length !== 3 ||
      !sample.rgb.every(v => bounded(v, 0, 255)) || !bounded(sample.clippedFraction, 0, 1)) {
      throw new Error('Invalid sample');
    }
    if (sample.timestampMs <= previous) throw new Error('Sample timestamps must be strictly increasing');
    previous = sample.timestampMs;
    maxClippedFraction = Math.max(maxClippedFraction, sample.clippedFraction);
    sample.rgb.forEach((value, channel) => {
      const delta = value - meanRgb[channel];
      meanRgb[channel] += delta / (index + 1);
      m2[channel] += delta * (value - meanRgb[channel]);
    });
  });
  const stdDevRgb = m2.map(value => Math.sqrt(Math.max(0, value / samples.length)));
  const warnings = [];
  if (samples.length < 15) warnings.push('insufficient_frames');
  if (maxClippedFraction > 0.01) warnings.push('clipping_observed');
  if (stdDevRgb.some(value => value > 2)) warnings.push('temporal_variation');
  return { sampleCount: samples.length, durationMs: samples.at(-1).timestampMs - samples[0].timestampMs,
    meanRgb, stdDevRgb, maxClippedFraction, warnings };
}

function sanitiseQuality(quality) {
  if (!quality || !Number.isInteger(quality.sampleCount) || quality.sampleCount < 1 ||
    !bounded(quality.durationMs, 0, Number.MAX_SAFE_INTEGER) ||
    !bounded(quality.maxClippedFraction, 0, 1) ||
    !Array.isArray(quality.meanRgb) || quality.meanRgb.length !== 3 || !quality.meanRgb.every(v => bounded(v, 0, 255)) ||
    !Array.isArray(quality.stdDevRgb) || quality.stdDevRgb.length !== 3 || !quality.stdDevRgb.every(v => bounded(v, 0, 255))) return null;
  return { sampleCount: quality.sampleCount, durationMs: quality.durationMs,
    meanRgb: [...quality.meanRgb], stdDevRgb: [...quality.stdDevRgb], maxClippedFraction: quality.maxClippedFraction,
    warnings: WARNINGS.filter(w => Array.isArray(quality.warnings) && quality.warnings.includes(w)) };
}

/** Construct a fresh allowlisted object; never spread observations into a download. */
export function exportReport(observations = {}) {
  const patchId = typeof observations.patchId === 'string' && /^(black|white|red|green|blue|grey-(8|32|64|128|192|240))$/.test(observations.patchId)
    ? observations.patchId : null;
  const capabilities = inspectCapabilities(observations.capabilities), quality = sanitiseQuality(observations.quality);
  // Only compare known values on both boundaries. Missing data is not an observed change.
  const before = inspectCapabilities({ settings: observations.settingsBeforeCapture }).settings;
  if (quality && Object.keys(before).some(name => Object.hasOwn(capabilities.settings, name) &&
    before[name] !== capabilities.settings[name])) quality.warnings.push('camera_settings_changed');
  return { format: 'tv-tune-compatibility-v1', status: 'experimental', source: 'processed-video',
    colourMeasurementValidated: false, patchId,
    presentation: observations.presentationConfirmed === true ? 'manually-confirmed' : 'unconfirmed',
    capabilities, quality,
    limitations: ['Not a calibration profile.', 'No absolute colour or luminance accuracy has been established.',
      'Patch identity is manually confirmed, not synchronised with the TV.',
      'Only settings exposed at capture start and end can be compared; matching or unknown settings do not establish a camera lock.',
      'Stable camera video does not establish colour accuracy or absence of display flicker.'] };
}

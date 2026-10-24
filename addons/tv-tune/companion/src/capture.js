import { summariseSamples } from './probe.js';
export function sampleRegion(width, height) {
  if (![width, height].every(value => Number.isFinite(value) && value > 0)) throw new Error('Invalid video geometry');
  return { x: width * 0.4, y: height * 0.4, width: width * 0.2, height: height * 0.2 };
}

/** Map sampled pixels into a centred object-fit:contain preview, including its bars. */
export function previewRegion(width, height, containerWidth, containerHeight) {
  const region = sampleRegion(width, height);
  if (![containerWidth, containerHeight].every(value => Number.isFinite(value) && value > 0)) throw new Error('Invalid preview geometry');
  const scale = Math.min(containerWidth / width, containerHeight / height);
  return { left: (containerWidth - width * scale) / 2 + region.x * scale,
    top: (containerHeight - height * scale) / 2 + region.y * scale,
    width: region.width * scale, height: region.height * scale };
}

/** Owns every requested stream, including streams whose permission resolves late. */
export function createCameraController({ mediaDevices, secureContext, onChange = () => {} } = {}) {
  let generation = 0, stream = null, preview = null, observed = {};
  const release = value => value?.getTracks().forEach(track => track.stop());
  function stop() {
    generation++;
    release(stream); stream = null; observed = {};
    if (preview) preview.srcObject = null;
    preview = null;
    onChange({ status: 'idle' });
  }
  async function start(video) {
    stop();
    if (!secureContext || !mediaDevices?.getUserMedia) {
      onChange({ status: 'error', error: !secureContext ? 'secure_origin_required' : 'camera_unavailable' });
      return false;
    }
    const current = generation;
    onChange({ status: 'requesting' });
    try {
      const acquired = await mediaDevices.getUserMedia({ audio: false,
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } } });
      if (current !== generation) { release(acquired); return false; }
      stream = acquired; preview = video;
      const track = acquired.getVideoTracks()[0];
      if (!track) throw new Error('No video track');
      let capabilities;
      try { capabilities = track.getCapabilities?.(); } catch { /* Optional browser API. */ }
      observed = { secureContext: true, mediaAvailable: true, capabilities };
      track.addEventListener('ended', () => { if (current === generation) stop(); });
      video.srcObject = acquired;
      await video.play();
      if (current !== generation) return false;
      onChange({ status: 'ready' });
      return true;
    } catch (error) {
      if (current !== generation) return false;
      stop();
      const codes = { NotAllowedError: 'permission_denied', NotFoundError: 'camera_missing', NotReadableError: 'camera_busy' };
      onChange({ status: 'error', error: codes[error.name] ?? 'preview_failed' });
      return false;
    }
  }
  function observations() {
    if (!stream) return {};
    let settings = {};
    try { settings = stream.getVideoTracks()[0]?.getSettings?.() ?? {}; } catch { /* Optional browser API. */ }
    return { ...observed, settings };
  }
  return { start, stop, observations };
}

export function pixelSample(pixels) {
  if (!pixels || pixels.length === 0 || pixels.length % 4 !== 0) throw new Error('Invalid pixel buffer');
  const rgb = [0, 0, 0]; let clipped = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    for (let c = 0; c < 3; c++) rgb[c] += pixels[i + c];
    if ([pixels[i], pixels[i + 1], pixels[i + 2]].some(v => v <= 1 || v >= 254)) clipped++;
  }
  const count = pixels.length / 4;
  return { rgb: rgb.map(value => value / count), clippedFraction: clipped / count };
}

/** Samples distinct decoded frames; a live track with a frozen preview cannot pass. */
export async function captureFrames({ video, readFrame, signal,
  now = () => performance.now(), wait = () => new Promise(resolve => setTimeout(resolve, 70)),
  limitMs = 5000, targetFrames = 45 }) {
  const started = now(), samples = [];
  let previousTime = -1;
  while (now() - started < Math.min(5000, limitMs)) {
    if (signal?.aborted) throw new Error('Capture cancelled');
    const time = video.currentTime;
    if (video.readyState >= 2 && video.videoWidth > 0 && video.videoHeight > 0 &&
      Number.isFinite(time) && time >= 0 && time > previousTime) {
      const frame = readFrame();
      samples.push({ timestampMs: time * 1000, ...frame });
      previousTime = time;
      if (samples.length >= targetFrames) break;
    }
    await wait();
  }
  if (signal?.aborted) throw new Error('Capture cancelled');
  if (samples.length === 0) throw new Error('No distinct decoded video frames');
  return summariseSamples(samples);
}

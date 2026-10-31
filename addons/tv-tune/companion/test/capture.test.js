import test from 'node:test';
import assert from 'node:assert/strict';
import { createCameraController, captureFrames, pixelSample, sampleRegion, previewRegion } from '../src/capture.js';

function camera() {
  const track = { stopped: false, stop() { this.stopped = true; }, addEventListener() {},
    getCapabilities: () => ({ exposureMode: ['continuous'] }),
    getSettings: () => ({ exposureMode: 'continuous', width: 640, height: 480 }) };
  return { track, stream: { getTracks: () => [track], getVideoTracks: () => [track] } };
}
const video = () => ({ srcObject: null, play: async () => {}, currentTime: 0, readyState: 4,
  videoWidth: 640, videoHeight: 480 });

test('camera_starts_only_on_request_and_never_requests_audio', async () => {
  const c = camera(), v = video(); let request;
  const controller = createCameraController({ secureContext: true,
    mediaDevices: { getUserMedia: async constraints => { request = constraints; return c.stream; } } });
  assert.equal(request, undefined);
  assert.equal(await controller.start(v), true);
  assert.equal(request.audio, false);
  assert.equal(request.video.facingMode.ideal, 'environment');
  assert.equal(v.srcObject, c.stream);
  assert.equal(controller.observations().settings.exposureMode, 'continuous');
  controller.stop();
  assert.equal(c.track.stopped, true);
  assert.equal(v.srcObject, null);
});
test('insecure_camera_start_does_not_prompt', async () => {
  let requested = false;
  const controller = createCameraController({ secureContext: false,
    mediaDevices: { getUserMedia: async () => { requested = true; } } });
  assert.equal(await controller.start(video()), false);
  assert.equal(requested, false);
});
test('late_permission_result_is_stopped_after_background_cancellation', async () => {
  const c = camera(), v = video(); let resolve;
  const controller = createCameraController({ secureContext: true,
    mediaDevices: { getUserMedia: () => new Promise(r => { resolve = r; }) } });
  const pending = controller.start(v);
  controller.stop(); resolve(c.stream);
  assert.equal(await pending, false);
  assert.equal(c.track.stopped, true);
  assert.equal(v.srcObject, null);
});
test('a_preview_failure_releases_the_camera', async () => {
  const c = camera(), v = video(); v.play = async () => { throw new Error('play failed'); };
  const controller = createCameraController({ secureContext: true,
    mediaDevices: { getUserMedia: async () => c.stream } });
  assert.equal(await controller.start(v), false);
  assert.equal(c.track.stopped, true);
});
test('a_denied_camera_reports_a_recoverable_state', async () => {
  const states = [];
  const controller = createCameraController({ secureContext: true, onChange: state => states.push(state),
    mediaDevices: { getUserMedia: async () => { throw new DOMException('private message', 'NotAllowedError'); } } });
  assert.equal(await controller.start(video()), false);
  assert.equal(states.at(-1).error, 'permission_denied');
  assert.equal(JSON.stringify(states).includes('private message'), false);
});
test('pixel_statistics_use_real_rgba_values_and_clipping_fraction', () => {
  assert.deepEqual(pixelSample(new Uint8ClampedArray([0, 10, 20, 255, 100, 110, 120, 255])),
    { rgb: [50, 60, 70], clippedFraction: 0.5 });
  assert.throws(() => pixelSample(new Uint8ClampedArray(0)), /pixel/i);
});
test('duplicate_video_frames_do_not_fill_the_sample_count', async () => {
  const v = video(); let clock = 0;
  const result = await captureFrames({ video: v, now: () => clock,
    wait: async () => { clock += 100; }, limitMs: 500,
    readFrame: () => ({ rgb: [80, 80, 80], clippedFraction: 0 }) });
  assert.equal(result.sampleCount, 1);
  assert.ok(result.warnings.includes('insufficient_frames'));
});
test('fresh_video_frames_finish_at_the_bounded_target', async () => {
  const v = video(); let clock = 0;
  const result = await captureFrames({ video: v, now: () => clock, targetFrames: 15,
    wait: async () => { clock += 100; v.currentTime += 0.1; },
    readFrame: () => ({ rgb: [80, 80, 80], clippedFraction: 0 }) });
  assert.equal(result.sampleCount, 15);
  assert.equal(Math.round(result.durationMs), 1400);
  assert.deepEqual(result.warnings, []);
});
test('cancelled_capture_does_not_emit_a_report', async () => {
  const abort = new AbortController(); abort.abort();
  await assert.rejects(captureFrames({ video: video(), signal: abort.signal,
    readFrame: () => ({ rgb: [1, 1, 1], clippedFraction: 0 }) }), /cancel/i);
});
test('a_video_with_no_decoded_frames_fails_instead_of_hanging', async () => {
  const v = video(); v.readyState = 0; let clock = 0;
  await assert.rejects(captureFrames({ video: v, now: () => clock, limitMs: 200,
    wait: async () => { clock += 100; }, readFrame: () => { throw new Error('must not read'); } }), /frame/i);
});
test('sampling_overlay_matches_landscape_letterboxing', () => {
  assert.deepEqual(sampleRegion(1280, 720), { x: 512, y: 288, width: 256, height: 144 });
  assert.deepEqual(previewRegion(1280, 720, 640, 480), { left: 256, top: 204, width: 128, height: 72 });
});
test('sampling_overlay_matches_portrait_pillarboxing', () => {
  assert.deepEqual(previewRegion(720, 1280, 640, 480), { left: 293, top: 192, width: 54, height: 96 });
});
test('unknown_video_dimensions_cannot_create_a_sampling_overlay', () => {
  assert.throws(() => sampleRegion(0, 480), /geometry/i);
  assert.throws(() => previewRegion(640, 480, NaN, 480), /geometry/i);
});
test('camera_report_reads_current_settings_after_stream_reconfiguration', async () => {
  const c = camera(); let width = 640;
  c.track.getSettings = () => ({ width, height: 480, exposureMode: 'continuous' });
  const controller = createCameraController({ secureContext: true,
    mediaDevices: { getUserMedia: async () => c.stream } });
  await controller.start(video()); width = 720;
  assert.equal(controller.observations().settings.width, 720);
  controller.stop();
});

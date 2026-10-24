import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as capture from '../src/capture.js';
import * as probe from '../src/probe.js';
import * as patterns from '../src/patterns.js';

// DOM/event simulation for application wiring, not browser or physical-camera evidence.
function app(view = 'phone') {
  class Element extends EventTarget {
    constructor() { super(); this.style = {}; this.dataset = {}; this.hidden = false;
      this.disabled = false; this.checked = false; this.textContent = ''; this.value = '';
      this.clientWidth = 640; this.clientHeight = 480; this.classList = { toggle() {} }; }
    append() {} replaceChildren() {} observe() {} disconnect() {}
    querySelector() { return new Element(); }
    click() { if (!this.disabled) this.dispatchEvent(new Event('click')); }
  }
  const elements = new Map();
  const get = id => { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); };
  get('results').hidden = true; get('capture').disabled = true;
  const video = get('preview'); video.parentElement = new Element();
  Object.assign(video, { videoWidth: 640, videoHeight: 480, readyState: 4, currentTime: 0,
    play: async () => {}, srcObject: null });
  const document = Object.assign(new EventTarget(), { documentElement: new Element(), body: new Element(),
    getElementById: get, querySelectorAll: () => [], createElement: () => Object.assign(new Element(), {
      getContext: () => ({ drawImage() {}, getImageData: () => ({ data: new Uint8ClampedArray([128,128,128,255]) }) }) }) });
  const window = new EventTarget(), intervals = new Map(), waits = [];
  const observedBoxes = new Set();
  class ResizeObserver { constructor(callback) { this.callback = callback; } observe(box) { observedBoxes.add(box); } disconnect() { observedBoxes.clear(); } }
  let clock = 0, intervalId = 0;
  const track = Object.assign(new EventTarget(), { stop() {}, getSettings: () => ({}), getCapabilities: () => ({}) });
  const mediaDevices = { getUserMedia: async () => ({ getTracks: () => [track], getVideoTracks: () => [track] }) };
  // Keep the production capture loop; substitute only its clock/wait environment.
  const captureFrames = options => capture.captureFrames({ ...options, now: () => clock,
    wait: () => new Promise(resolve => waits.push(() => { clock += 70; video.currentTime += 0.07; resolve(); })) });
  const scope = { ...probe, ...capture, ...patterns, captureFrames, document, window, Element,
    navigator: { mediaDevices }, isSecureContext: true, location: { search: view === 'tv' ? '?view=tv&lang=en' : '?lang=en' },
    performance: { now: () => clock }, innerWidth: 1920, innerHeight: 1080,
    setInterval: fn => { intervals.set(++intervalId, fn); return intervalId; }, clearInterval: id => intervals.delete(id),
    ResizeObserver };
  const source = readFileSync(new URL('../src/app.js', import.meta.url), 'utf8').replace(/^import .*;\n/gm, '');
  new Function(...Object.keys(scope), source)(...Object.values(scope));
  return { get, window, intervals, waits, observedBoxes, advance: ms => { clock += ms; for (const tick of intervals.values()) tick(); } };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('withdrawing_patch_confirmation_cancels_in_progress_capture', async () => {
  const a = app(); a.get('start').click(); await flush();
  a.get('confirm').checked = true; a.get('confirm').dispatchEvent(new Event('change'));
  a.get('capture').click(); assert.equal(a.waits.length, 1);
  a.get('confirm').checked = false; a.get('confirm').dispatchEvent(new Event('change'));
  // Complete all pending samples; withdrawn confirmation must never produce a report.
  for (let i = 0; i < 50 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
  assert.equal(a.get('results').hidden, true, 'withdrawn confirmation produced a report');
  assert.match(a.get('camera-status').textContent, /cancelled/i);
});

test('presenter_inactivity_protection_survives_back_forward_restoration', () => {
  const a = app('tv');
  for (let i = 0; i < 2; i++) {
    a.window.dispatchEvent(new Event('pagehide'));
    assert.equal(a.intervals.size, 0);
    a.window.dispatchEvent(new Event('pageshow'));
    a.window.dispatchEvent(new Event('pageshow'));
    assert.equal(a.intervals.size, 1, 'restored presenter must own exactly one inactivity timer');
    a.get('next').click();
    a.advance(120001);
    assert.equal(a.get('tv-patch-id').textContent, 'Paused');
  }
});

test('old_cancelled_capture_cannot_unlock_a_new_capture_in_the_application', async () => {
  const a = app(); a.get('start').click(); await flush();
  const confirm = () => { a.get('confirm').checked = true; a.get('confirm').dispatchEvent(new Event('change')); };
  confirm(); a.get('capture').click();
  assert.equal(a.waits.length, 1);
  a.get('stop').click();
  assert.equal(a.get('controls-state').textContent, 'Camera off');
  assert.equal(a.get('preview').srcObject, null);
  a.get('start').click(); await flush(); confirm(); a.get('capture').click();
  assert.equal(a.waits.length, 2);
  a.waits.shift()(); await flush();
  assert.equal(a.get('capture').disabled, true);
  assert.equal(a.get('results').hidden, true);
  a.get('stop').click(); a.waits.shift()(); await flush();
});

test('withdrawing_confirmation_allows_a_new_capture_only_after_reconfirmation', async () => {
  const a = app(); a.get('start').click(); await flush();
  a.get('confirm').checked = true; a.get('confirm').dispatchEvent(new Event('change')); a.get('capture').click();
  a.get('confirm').checked = false; a.get('confirm').dispatchEvent(new Event('change'));
  a.waits.shift()(); await flush();
  assert.equal(a.get('capture').disabled, true);
  assert.notEqual(a.get('preview').srcObject, null, 'cancellation need not restart camera permission');
  a.get('confirm').checked = true; a.get('confirm').dispatchEvent(new Event('change'));
  assert.equal(a.get('capture').disabled, false);
  a.get('capture').click();
  for (let i = 0; i < 50 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
  assert.equal(a.get('results').hidden, false);
  a.get('review').click();
  const report = JSON.parse(a.get('report-preview').textContent);
  assert.equal(report.presentation, 'manually-confirmed');
  assert.equal(report.quality.sampleCount, 45);
  assert.equal(report.colourMeasurementValidated, false);
  a.get('stop').click();
});


test('phone_restores_sampling_overlay_observer_after_back_forward_navigation', () => {
  const a = app();
  for (let i = 0; i < 2; i++) {
    assert.equal(a.observedBoxes.size, 1);
    a.window.dispatchEvent(new Event('pagehide'));
    assert.equal(a.observedBoxes.size, 0);
    a.window.dispatchEvent(new Event('pageshow'));
    assert.equal(a.observedBoxes.size, 1, 'restored preview must be watched for parent size changes');
  }
});

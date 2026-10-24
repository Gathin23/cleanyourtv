import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as capture from '../src/capture.js';
import * as probe from '../src/probe.js';
import * as patterns from '../src/patterns.js';
import { qrSvg } from '../src/qr.js';

// DOM/event simulation for application wiring, not browser or physical-camera evidence.
function app(view = 'phone', language = 'en', fragment = '', clipboard) {
  class Element extends EventTarget {
    constructor() { super(); this.style = {}; this.dataset = {}; this.hidden = false;
      this.disabled = false; this.checked = false; this.textContent = ''; this.value = ''; this.children = [];
      this.clientWidth = 640; this.clientHeight = 480; this.classList = { toggle() {} }; this.attributes = {}; }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; } observe() {} disconnect() {}
    setAttribute(name, value) { this.attributes[name] = value; }
    contains(element) { return element === this || element?.parentElement === this; }
    focus(options) { document.activeElement = this; this.focusOptions = options; this.focuses = (this.focuses || 0) + 1; }
    scrollIntoView() { this.scrolls = (this.scrolls || 0) + 1; }
    querySelector() { return new Element(); }
    click() { if (!this.disabled) this.dispatchEvent(new Event('click')); }
  }
  const elements = new Map();
  const get = id => { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); };
  get('tv-return').href = readFileSync(new URL('../index.html', import.meta.url), 'utf8').match(/class="tv-heading"><a href="([^"]+)"/)[1];
  get('brand').href = readFileSync(new URL('../index.html', import.meta.url), 'utf8').match(/class="brand" href="([^"]+)"/)[1];
  get('phone-link').parentElement = get('handoff-panel'); get('copy-phone-link').parentElement = get('handoff-panel');
  get('handoff-panel').hidden = true; get('results').hidden = true; get('capture').disabled = true;
  const video = get('preview'); video.parentElement = new Element();
  Object.assign(video, { videoWidth: 640, videoHeight: 480, readyState: 4, currentTime: 0,
    play: async () => {}, srcObject: null });
  const document = Object.assign(new EventTarget(), { documentElement: new Element(), body: new Element(),
    getElementById: get, querySelector: selector => { assert.equal(selector, '.tv-heading a'); return get('tv-return'); }, querySelectorAll: selector => selector === 'a[href="?"]' ? [get('tv-return'), get('brand')] : selector === '.tv-link' ? [get('tv-link')] : [], createElement: () => Object.assign(new Element(), {
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
  const currentUrl = new URL(`https://example.test/lab/?${view === 'tv' ? 'view=tv&' : ''}lang=${language}${fragment}`);
  const location = {};
  for (const property of ['href', 'hash', 'search']) Object.defineProperty(location, property, {
    get: () => currentUrl[property], set: value => { currentUrl[property] = property === 'href' ? new URL(value, currentUrl.href).href : value; }
  });
  const replacements = [];
  const history = { state: null, replaceState(state, title, path) { replacements.push(path); location.href = new URL(path, location.href).href; } };
  const scope = { ...probe, ...capture, ...patterns, qrSvg, captureFrames, document, window, Element,
    navigator: { mediaDevices, clipboard }, isSecureContext: true, location, history,
    performance: { now: () => clock }, innerWidth: 1920, innerHeight: 1080,
    setInterval: fn => { intervals.set(++intervalId, fn); return intervalId; }, clearInterval: id => intervals.delete(id),
    ResizeObserver };
  const source = readFileSync(new URL('../src/app.js', import.meta.url), 'utf8').replace(/^import .*;\n/gm, '');
  new Function(...Object.keys(scope), source)(...Object.values(scope));
  return { get, track, document, window, intervals, waits, observedBoxes, location, replacements, advance: ms => { clock += ms; for (const tick of intervals.values()) tick(); } };
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


test('presenter_return_link_preserves_english_selection', () => {
  assert.equal(app('tv', 'en').get('tv-return').href, '?lang=en#patch=grey-128');
});
test('presenter_return_link_retains_spanish_default', () => {
  assert.equal(app('tv', 'es').get('tv-return').href, '?#patch=grey-128');
});


test('phone_brand_home_link_preserves_english_selection', () => {
  assert.equal(app('phone', 'en').get('brand').href, '?lang=en#patch=grey-128');
});
test('phone_brand_home_link_retains_spanish_default', () => {
  assert.equal(app('phone', 'es').get('brand').href, '?#patch=grey-128');
});


test('phone_fragment_preselects_patch_without_camera_or_confirmation', () => {
  const a = app('phone', 'en', '#patch=red');
  assert.equal(a.get('patch').value, 'red');
  assert.equal(a.get('confirm').checked, false);
  assert.equal(a.get('preview').srcObject, null);
  assert.equal(a.get('capture').disabled, true);
  assert.equal(a.get('tv-link').href, '?view=tv&lang=en#patch=red');
});

test('presenter_handoff_preserves_patch_language_and_does_not_copy_until_clicked', async () => {
  const copies = []; const a = app('tv', 'en', '#patch=red', { writeText: async text => copies.push(text) });
  assert.equal(a.get('tv-patch-id').textContent, 'red');
  assert.equal(a.get('phone-link').href, 'https://example.test/lab/?lang=en#patch=red');
  assert.equal(copies.length, 0);
  a.get('copy-phone-link').click(); await flush();
  assert.deepEqual(copies, ['https://example.test/lab/?lang=en#patch=red']);
  assert.match(a.get('handoff-status').textContent, /copied/i);
  a.get('next').click();
  assert.equal(a.get('tv-patch-id').textContent, 'green');
  assert.equal(a.location.hash, '#patch=green');
  assert.equal(a.replacements.length, 1);
  assert.match(a.get('phone-link').href, /#patch=green$/);
});

test('phone_fragment_navigation_cancels_capture_and_resets_manual_confirmation', async () => {
  const a = app('phone', 'en', '#patch=red'); a.get('start').click(); await flush();
  a.get('confirm').checked = true; a.get('confirm').dispatchEvent(new Event('change')); a.get('capture').click();
  a.location.hash = '#patch=blue'; a.window.dispatchEvent(new Event('hashchange'));
  assert.equal(a.get('patch').value, 'blue');
  assert.equal(a.get('confirm').checked, false);
  for (let i = 0; i < 50 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
  assert.equal(a.get('results').hidden, true);
  assert.equal(a.get('capture').disabled, true);
});

test('clipboard_denial_keeps_visible_manual_handoff_without_retry', async () => {
  let calls = 0; const a = app('tv', 'es', '#patch=blue', { writeText: async () => { calls++; throw Error('denied'); } });
  a.get('copy-phone-link').click(); await flush();
  assert.equal(calls, 1);
  assert.match(a.get('handoff-status').textContent, /copia|selecciona/i);
  assert.equal(a.get('phone-link').textContent, 'https://example.test/lab/?#patch=blue');
});


test('camera_section_jump_preserves_patch_fragment_and_normal_anchor_does_not_cancel_capture', async () => {
  const a = app('phone', 'en', '#patch=red');
  const event = new Event('click', { cancelable: true }); a.get('camera-jump').dispatchEvent(event);
  assert.equal(event.defaultPrevented, true);
  assert.equal(a.get('camera-section').scrolls, 1);
  assert.equal(a.get('camera-section').focuses, 1);
  assert.deepEqual(a.get('camera-section').focusOptions, { preventScroll: true });
  assert.match(readFileSync(new URL('../index.html', import.meta.url), 'utf8'), /id="camera-section"[^>]*tabindex="-1"/);
  assert.equal(a.get('preview').srcObject, null);
  assert.equal(a.location.hash, '#patch=red');
  a.get('start').click(); await flush(); a.get('confirm').checked = true;
  a.get('confirm').dispatchEvent(new Event('change')); a.get('capture').click();
  a.location.hash = '#camera-section'; a.window.dispatchEvent(new Event('hashchange'));
  assert.equal(a.get('patch').value, 'red'); assert.equal(a.get('confirm').checked, true);
  for (let i = 0; i < 50 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
  assert.equal(a.get('results').hidden, false);
  a.get('stop').click();
});

test('stale_clipboard_completion_does_not_claim_new_patch_was_copied', async () => {
  let complete; const a = app('tv', 'en', '#patch=red', { writeText: () => new Promise(resolve => { complete = resolve; }) });
  a.get('copy-phone-link').click(); a.get('next').click(); complete(); await flush();
  assert.equal(a.get('handoff-status').textContent, '');
  assert.match(a.get('phone-link').href, /#patch=green$/);
});

test('clipboard_unavailable_keeps_selectable_link_and_explicit_manual_fallback', async () => {
  const a = app('tv', 'en', '#patch=blue');
  a.get('copy-phone-link').click(); await flush();
  assert.match(a.get('handoff-status').textContent, /Select and copy/);
  assert.equal(a.get('phone-link').textContent, a.get('phone-link').href);
});

test('malformed_phone_fragment_and_history_change_fall_back_without_confirmation', async () => {
  const a = app('phone', 'es', '#patch=red&patch=blue');
  assert.equal(a.get('patch').value, 'grey-128');
  a.get('patch').value = 'blue'; a.get('patch').dispatchEvent(new Event('change'));
  assert.equal(a.location.hash, '#patch=blue');
  assert.equal(a.get('tv-link').href, '?view=tv#patch=blue');
  a.get('confirm').checked = true;
  a.location.hash = '#patch=green'; a.window.dispatchEvent(new Event('popstate'));
  assert.equal(a.get('patch').value, 'green'); assert.equal(a.get('confirm').checked, false);
  assert.equal(a.get('preview').srcObject, null);
});

test('language_switch_preserves_current_patch_without_forwarding_unknown_query_data', () => {
  const a = app('tv', 'en', '#patch=red'); a.get('next').click();
  a.location.search += '&private=do-not-share';
  a.get('language').click();
  assert.equal(a.location.search, '?view=tv'); assert.equal(a.location.hash, '#patch=green');
});


test('expanded_handoff_hides_and_pauses_patch_until_explicit_resume', () => {
  const a = app('tv', 'en', '#patch=red');
  a.get('handoff-toggle').click();
  assert.equal(a.get('handoff-panel').hidden, false);
  assert.equal(a.get('tv-patch').hidden, true);
  assert.equal(a.get('tv-patch-id').textContent, 'Paused');
  a.get('handoff-toggle').click();
  assert.equal(a.get('handoff-panel').hidden, true);
  assert.equal(a.get('tv-patch-id').textContent, 'Paused');
  a.get('next').click();
  assert.equal(a.get('tv-patch-id').textContent, 'red');
  a.get('handoff-toggle').click(); a.get('next').click();
  assert.equal(a.get('handoff-panel').hidden, true);
  assert.equal(a.get('tv-patch').hidden, false);
  assert.equal(a.get('tv-patch-id').textContent, 'red');
});


test('handoff_strips_self_hosting_credentials_and_arbitrary_query_parameters', () => {
  const a = app('tv', 'en', '#patch=red');
  a.location.href = 'https://private-user:private-password@example.test/lab/?view=tv&lang=en&secret=private#patch=red';
  a.get('next').click();
  const link = new URL(a.get('phone-link').href);
  assert.equal(link.username, ''); assert.equal(link.password, '');
  assert.equal(link.search, '?lang=en'); assert.equal(link.hash, '#patch=green');
});

test('malformed_history_fragment_never_keeps_an_existing_confirmation', () => {
  const a = app('phone', 'en', '#patch=grey-128');
  a.get('confirm').checked = true;
  a.location.hash = '#patch=red&patch=blue'; a.window.dispatchEvent(new Event('hashchange'));
  assert.equal(a.get('patch').value, 'grey-128');
  assert.equal(a.get('confirm').checked, false);
});

test('escape_closes_handoff_and_restores_focus_to_toggle', () => {
  const a = app('tv', 'en', '#patch=red');
  a.get('handoff-toggle').click();
  const event = new Event('keydown'); Object.defineProperty(event, 'key', { value: 'Escape' });
  a.document.dispatchEvent(event);
  assert.equal(a.get('handoff-panel').hidden, true);
  assert.equal(a.get('handoff-toggle').focuses, 1);
  assert.equal(a.get('tv-patch-id').textContent, 'Paused');
  assert.equal(a.location.hash, '#patch=red');
});


test('QR is generated only for an open paused handoff and updates with the patch', () => {
  const a = app('tv', 'en', '#patch=red');
  assert.equal(a.get('phone-qr').src, undefined);
  a.get('handoff-toggle').click();
  const red = a.get('phone-qr').src;
  assert.equal(red, `data:image/svg+xml;charset=utf-8,${encodeURIComponent(qrSvg(a.get('phone-link').href))}`);
  assert.equal(a.get('phone-qr').hidden, false);
  assert.equal(a.get('tv-patch').hidden, true);
  assert.equal(a.get('tv-patch-id').textContent, 'Paused');
  a.advance(1000); assert.equal(a.get('phone-qr').src, red);
  a.location.hash = '#patch=blue'; a.window.dispatchEvent(new Event('hashchange'));
  assert.notEqual(a.get('phone-qr').src, red);
  assert.equal(a.get('phone-qr').src, `data:image/svg+xml;charset=utf-8,${encodeURIComponent(qrSvg(a.get('phone-link').href))}`);
  a.get('handoff-toggle').click();
  assert.equal(a.get('handoff-panel').hidden, true);
  assert.equal(a.get('tv-patch-id').textContent, 'Paused');
});

test('an oversized self-hosted path keeps the manual phone link without a stale QR', () => {
  const a = app('tv'); a.get('handoff-toggle').click();
  a.location.href = 'https://example.test/' + 'x'.repeat(1200) + '?view=tv&lang=en#patch=blue';
  a.window.dispatchEvent(new Event('hashchange'));
  assert.equal(a.get('phone-qr').hidden, true);
  assert.match(a.get('qr-status').textContent, /use the link/i);
  assert.match(a.get('phone-link').href, /#patch=blue$/);
  assert.equal(a.get('tv-patch').hidden, true);
});


test('arrow navigation closes QR and restores focus outside the hidden panel', () => {
  const a = app('tv'); a.get('handoff-toggle').click();
  a.get('phone-link').focus();
  const event = new Event('keydown', { cancelable: true });
  Object.defineProperty(event, 'key', { value: 'ArrowRight' });
  a.document.dispatchEvent(event);
  assert.equal(a.get('handoff-panel').hidden, true);
  assert.equal(a.get('handoff-toggle').focuses, 1);
  assert.equal(a.get('tv-patch').hidden, false);
});


test('closing QR via external controls does not steal their focus', () => {
  const a = app('tv'); a.get('handoff-toggle').click();
  a.get('next').focus(); a.get('next').click();
  assert.equal(a.document.activeElement, a.get('next'));
  assert.equal(a.get('handoff-toggle').focuses, undefined);
  assert.equal(a.get('handoff-panel').hidden, true);
});


// Camera API doubles expose browser capabilities/settings; the real app and capture loop run.
test('manual_capability_does_not_replace_observed_camera_modes', async () => {
  const a = app();
  a.track.getCapabilities = () => ({ exposureMode: ['manual', 'continuous'],
    whiteBalanceMode: ['manual'], focusMode: ['manual', 'single-shot'] });
  a.track.getSettings = () => ({ exposureMode: 'continuous', whiteBalanceMode: 'manual' });
  a.get('start').click(); await flush();
  assert.match(a.get('controls-state').textContent, /exposure.*white balance.*focus/);
  assert.equal(a.get('exposure-state').textContent, 'Continuous (automatic)');
  assert.equal(a.get('white-balance-state').textContent, 'Manual');
  assert.equal(a.get('focus-state').textContent, 'Unknown / not exposed');
  assert.equal(a.get('capture').disabled, true, 'observing modes must not confirm the patch');
  a.get('stop').click();
  for (const id of ['exposure-state', 'white-balance-state', 'focus-state']) {
    assert.equal(a.get(id).textContent, 'Camera off', 'stopped camera must not retain a current mode');
  }
});

test('missing_throwing_and_unrecognised_settings_are_explicitly_unknown', async () => {
  for (const getSettings of [undefined, () => { throw Error('private settings error'); },
    () => ({ exposureMode: 'private-device-name', whiteBalanceMode: null, focusMode: {} })]) {
    const a = app(); a.track.getSettings = getSettings;
    a.track.getCapabilities = () => ({ exposureMode: ['manual'] });
    a.get('start').click(); await flush();
    for (const id of ['exposure-state', 'white-balance-state', 'focus-state']) {
      assert.equal(a.get(id).textContent, 'Unknown / not exposed');
    }
    a.get('stop').click();
  }
});

test('observed_modes_work_without_capabilities_and_use_spanish_labels', async () => {
  const a = app('phone', 'es');
  a.track.getCapabilities = () => { throw Error('unsupported'); };
  a.track.getSettings = () => ({ exposureMode: 'single-shot', whiteBalanceMode: 'none', focusMode: 'manual' });
  a.get('start').click(); await flush();
  assert.equal(a.get('controls-state').textContent, 'No indicada / desconocida');
  assert.equal(a.get('exposure-state').textContent, 'Automático puntual');
  assert.equal(a.get('white-balance-state').textContent, 'Ninguno indicado');
  assert.equal(a.get('focus-state').textContent, 'Manual');
  a.get('stop').click();
});

test('capture_refreshes_observed_modes_at_both_boundaries_and_warns_on_differences', async () => {
  const a = app();
  // Reuse the same settings object to catch a retained live reference instead of a snapshot.
  const settings = { exposureMode: 'manual', whiteBalanceMode: 'manual', focusMode: 'manual', iso: 100 };
  a.track.getSettings = () => settings;
  a.get('start').click(); await flush();
  assert.equal(a.get('exposure-state').textContent, 'Manual');
  settings.exposureMode = 'continuous';
  a.get('confirm').checked = true; a.get('confirm').dispatchEvent(new Event('change')); a.get('capture').click();
  assert.equal(a.get('exposure-state').textContent, 'Continuous (automatic)');
  settings.exposureMode = 'manual'; settings.iso = 200;
  for (let i = 0; i < 50 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
  assert.equal(a.get('exposure-state').textContent, 'Manual');
  assert.equal(a.get('results').hidden, false);
  a.get('review').click();
  const report = JSON.parse(a.get('report-preview').textContent);
  assert.ok(report.quality.warnings.includes('camera_settings_changed'));
  assert.ok(a.get('warnings').children.some(item => /settings differed.*start and end/i.test(item.textContent)));
  assert.equal(report.capabilities.settings.iso, 200);
  assert.equal(report.colourMeasurementValidated, false);
  a.get('stop').click();
});

test('unchanged_or_unexposed_modes_never_claim_a_verified_camera_lock', async () => {
  for (const settings of [{}, { exposureMode: 'manual', whiteBalanceMode: 'manual', focusMode: 'manual' }]) {
    const a = app(); a.track.getSettings = () => settings;
    a.get('start').click(); await flush(); a.get('confirm').checked = true;
    a.get('confirm').dispatchEvent(new Event('change')); a.get('capture').click();
    for (let i = 0; i < 50 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
    a.get('review').click();
    const report = JSON.parse(a.get('report-preview').textContent);
    assert.equal(report.quality.warnings.includes('camera_settings_changed'), false);
    assert.ok(a.get('warnings').children.some(item => /do not establish.*lock/i.test(item.textContent)));
    a.get('stop').click();
  }
});

test('settings_disappearing_during_capture_become_unknown_without_a_false_change_warning', async () => {
  const a = app();
  a.track.getSettings = () => ({ exposureMode: 'manual', whiteBalanceMode: 'manual', focusMode: 'manual' });
  a.get('start').click(); await flush(); a.get('confirm').checked = true;
  a.get('confirm').dispatchEvent(new Event('change')); a.get('capture').click();
  a.track.getSettings = () => { throw Error('no longer exposed'); };
  for (let i = 0; i < 50 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
  assert.equal(a.get('focus-state').textContent, 'Unknown / not exposed');
  a.get('review').click();
  const report = JSON.parse(a.get('report-preview').textContent);
  assert.equal(report.quality.warnings.includes('camera_settings_changed'), false);
  assert.deepEqual(report.capabilities.settings, {});
  a.get('stop').click();
});

test('cancelled_capture_settings_do_not_leak_into_the_next_capture', async () => {
  const a = app(); a.track.getSettings = () => ({ exposureMode: 'manual', iso: 100 });
  a.get('start').click(); await flush();
  const confirm = () => { a.get('confirm').checked = true; a.get('confirm').dispatchEvent(new Event('change')); };
  confirm(); a.get('capture').click(); a.get('stop').click();
  a.track.getSettings = () => ({ exposureMode: 'continuous', iso: 200 });
  a.get('start').click(); await flush(); confirm(); a.get('capture').click();
  for (let i = 0; i < 60 && a.waits.length; i++) { a.waits.shift()(); await flush(); }
  a.get('review').click();
  const report = JSON.parse(a.get('report-preview').textContent);
  assert.equal(report.quality.warnings.includes('camera_settings_changed'), false);
  assert.equal(report.capabilities.settings.exposureMode, 'continuous');
  assert.equal(a.get('exposure-state').textContent, 'Continuous (automatic)');
  assert.equal(report.quality.sampleCount, 45);
  a.get('stop').click();
});

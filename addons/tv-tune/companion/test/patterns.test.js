import test from 'node:test';
import assert from 'node:assert/strict';
import { PATCHES, presenterState, patchRectangle } from '../src/patterns.js';

test('patches_have_unique_ids_full_range_endpoints_and_integer_codes', () => {
  assert.equal(new Set(PATCHES.map(p => p.id)).size, PATCHES.length);
  assert.deepEqual(PATCHES.find(p => p.id === 'black').rgb, [0, 0, 0]);
  assert.deepEqual(PATCHES.find(p => p.id === 'white').rgb, [255, 255, 255]);
  assert.ok(PATCHES.every(p => p.rgb.length === 3 && p.rgb.every(v => Number.isInteger(v) && v >= 0 && v <= 255)));
});
test('presenter_changes_only_on_input_and_stays_at_the_edges', () => {
  const state = presenterState();
  assert.equal(state.index, 4);
  state.move(-100, 1); assert.equal(state.index, 0);
  state.move(-1, 2); assert.equal(state.index, 0);
  state.move(100, 3); assert.equal(state.index, PATCHES.length - 1);
});
test('idle_and_visibility_pause_the_patch_and_one_key_only_resumes_it', () => {
  const state = presenterState(0);
  state.tick(120001); assert.equal(state.paused, true);
  state.move(1, 120002); assert.equal(state.paused, false);
  assert.equal(state.index, 4);
  state.pause(); assert.equal(state.paused, true);
});
test('normal_tv_patch_occupies_ten_percent_of_the_render_surface', () => {
  const rect = patchRectangle(1920, 1080);
  assert.ok(Math.abs(rect.size ** 2 - 207360) < 0.00001);
  assert.equal(rect.left + rect.size / 2, 960);
  assert.equal(rect.top + rect.size / 2, 540);
  assert.throws(() => patchRectangle(0, 100), /size/i);
});

test('fragment_handoff_accepts_only_one_known_patch_and_never_echoes_unknown_data', async () => {
  const p = await import('../src/patterns.js');
  for (const patch of PATCHES) assert.equal(p.patchFromFragment(`#patch=${patch.id}`).id, patch.id);
  for (const hash of ['', '#camera-section', '#patch=unknown', '#patch=red&patch=blue', '#patch=%E0%A4%A', '#patch=<script>']) {
    assert.equal(p.patchFromFragment(hash).id, 'grey-128');
  }
  assert.equal(p.patchLink('phone', true, 'red'), '?lang=en#patch=red');
  assert.equal(p.patchLink('tv', false, 'blue'), '?view=tv#patch=blue');
  assert.equal(p.patchLink('tv', true, 'https://evil.example/'), '?view=tv&lang=en#patch=grey-128');
  assert.equal(p.patchLink('phone', false, 'black'), '?#patch=black');
});

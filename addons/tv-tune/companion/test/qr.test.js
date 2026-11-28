import test from 'node:test';
import assert from 'node:assert/strict';
import { qrMatrix, qrSvg } from '../src/qr.js';
import { PATCHES, patchLink } from '../src/patterns.js';

test('local QR encodes every patch/language with a four-module quiet zone', () => {
  for (const patch of PATCHES) for (const english of [false, true]) {
    const url = new URL(patchLink('phone', english, patch.id), 'https://tv-tune-kalt2212.vercel.app/').href;
    const matrix = qrMatrix(url), svg = qrSvg(url);
    assert.ok(matrix.length >= 21 && matrix.length <= 57);
    assert.ok(matrix.every(row => row.length === matrix.length && row.every(value => typeof value === 'boolean')));
    assert.match(svg, new RegExp(`viewBox="0 0 ${matrix.length + 8} ${matrix.length + 8}"`));
    assert.match(svg, /fill="#fff"/); assert.match(svg, /fill="#000"/);
    assert.match(svg, /shape-rendering="crispEdges"/);
    assert.ok(!svg.includes(url), 'only numeric modules, not untrusted URL markup, enter SVG');
  }
});

test('oversized or invalid QR input fails safely for the manual-link fallback', () => {
  assert.throws(() => qrSvg('x'.repeat(1025)));
  assert.throws(() => qrSvg('x'.repeat(1000)));
  assert.throws(() => qrSvg(null));
  assert.throws(() => qrSvg(''));
});

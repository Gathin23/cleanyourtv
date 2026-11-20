// Independent synthetic QR decoding. No network calls, no real camera or TV.
// Test-only dependencies, installed OUTSIDE the repository. Reproduce with:
//   QR_VERIFY_DEPENDENCIES=$(mktemp -d)
//   npm install --prefix "$QR_VERIFY_DEPENDENCIES" --cache "$QR_VERIFY_DEPENDENCIES/npm-cache" --ignore-scripts --no-audit --no-fund --no-save --registry=https://registry.npmjs.org jsqr@1.4.0 @resvg/resvg-js@2.6.2
//   QR_VERIFY_DEPENDENCIES="$QR_VERIFY_DEPENDENCIES" node test/qr-decode.mjs
// jsQR is Apache-2.0; resvg-js is MPL-2.0. Neither ships in the companion.
// Optional first argument: artifact output directory. Default: a temporary directory.
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { readFileSync, writeFileSync, mkdirSync, mkdtempSync } from 'node:fs';
if (!process.env.QR_VERIFY_DEPENDENCIES) throw new Error('Set QR_VERIFY_DEPENDENCIES to the isolated directory containing the test dependencies; see this script header.');
const require = createRequire(pathToFileURL(join(resolve(process.env.QR_VERIFY_DEPENDENCIES), 'package.json')));
const jsQR = require('jsqr');
const { Resvg } = require('@resvg/resvg-js');
assert.equal(require('jsqr/package.json').version, '1.4.0');
assert.equal(require('@resvg/resvg-js/package.json').version, '2.6.2');
const companion = fileURLToPath(new URL('..', import.meta.url));
const output = process.argv[2] ? resolve(process.argv[2]) : mkdtempSync(join(tmpdir(), 'tv-tune-qr-decode-'));
mkdirSync(output, { recursive: true });
const { PATCHES, patchLink, patchFromFragment } = await import(pathToFileURL(join(companion, 'src/patterns.js')));
const { qrMatrix, qrSvg } = await import(pathToFileURL(join(companion, 'src/qr.js')));
assert.equal(PATCHES.length, 11, 'This verification expects the 11-patch catalogue');
const bases = ['https://tv-tune-kalt2212.vercel.app/'];
const results = [];
function rgbaMatrix(rows, pixelsPerModule) {
  const width = (rows.length + 8) * pixelsPerModule;
  const data = new Uint8ClampedArray(width * width * 4).fill(255);
  rows.forEach((row, y) => row.forEach((dark, x) => {
    if (dark) for (let dy = 0; dy < pixelsPerModule; dy++) for (let dx = 0; dx < pixelsPerModule; dx++) {
      const i = ((y + 4) * pixelsPerModule + dy) * width * 4 + ((x + 4) * pixelsPerModule + dx) * 4;
      data[i] = data[i + 1] = data[i + 2] = 0;
    }
  }));
  return { data, width, height: width };
}
function rasterize(svg, width) {
  const renderer = new Resvg(svg, { font: { loadSystemFonts: false }, ...(width ? { fitTo: { mode: 'width', value: width } } : {}) });
  const image = renderer.render();
  return { data: new Uint8ClampedArray(image.pixels), width: image.width, height: image.height, png: image.asPng() };
}
function rotate90({ data, width, height }) {
  const rotated = new Uint8ClampedArray(data.length);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const source = (y * width + x) * 4;
    const target = (x * height + height - 1 - y) * 4;
    rotated.set(data.subarray(source, source + 4), target);
  }
  return { data: rotated, width: height, height: width };
}
function check(image, expected, language, patch, variant) {
  const decoded = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
  let error;
  try {
    assert.ok(decoded, 'Decoder did not recognize QR');
    assert.equal(decoded.data, expected, 'Decoded payload differs');
    const url = new URL(decoded.data);
    assert.equal(url.hash, `#patch=${patch}`);
    assert.equal(url.searchParams.has('patch'), false, 'Patch leaked into HTTP query');
    assert.equal(url.searchParams.get('lang'), language === 'en' ? 'en' : null);
    assert.equal(patchFromFragment(url.hash).id, patch);
  } catch (err) { error = err.message; }
  results.push({ language, patch, variant, width: image.width, height: image.height, expected, decoded: decoded?.data ?? null, passed: !error, ...(error ? { error } : {}) });
}
for (const base of bases) for (const language of ['en', 'es']) for (const patch of PATCHES) {
  const expected = new URL(patchLink('phone', language === 'en', patch.id), base).href;
  const rows = qrMatrix(expected), svg = qrSvg(expected);
  check(rgbaMatrix(rows, 2), expected, language, patch.id, 'matrix-two-pixels-per-module');
  const native = rasterize(svg);
  check(native, expected, language, patch.id, 'actual-svg-native');
  // All four full quiet-zone modules must remain opaque white in actual SVG output.
  const pixelsPerModule = native.width / (rows.length + 8), quiet = pixelsPerModule * 4;
  assert.ok(Number.isInteger(pixelsPerModule) && pixelsPerModule > 0);
  assert.equal(native.width, native.height);
  for (let y = 0; y < native.height; y++) for (let x = 0; x < native.width; x++) {
    if (x < quiet || y < quiet || x >= native.width - quiet || y >= native.height - quiet) {
      assert.deepEqual([...native.data.subarray((y * native.width + x) * 4, (y * native.width + x) * 4 + 4)], [255, 255, 255, 255]);
    }
  }
  for (const width of [128, 197, 240, 320]) check(rasterize(svg, width), expected, language, patch.id, `actual-svg-scaled-${width}`);
  check(rotate90(rasterize(svg, 240)), expected, language, patch.id, 'actual-svg-rotated-90');
  if (patch.id === 'grey-128') {
    writeFileSync(join(output, `${language}-grey-128.svg`), svg);
    writeFileSync(join(output, `${language}-grey-128-240.png`), rasterize(svg, 240).png);
  }
}
const blank = new Uint8ClampedArray(240 * 240 * 4).fill(255);
assert.equal(jsQR(blank, 240, 240, { inversionAttempts: 'dontInvert' }), null, 'Negative control must not decode');
const failed = results.filter(result => !result.passed);
const summary = {
  scope: 'Synthetic software rendering and independent decoding only; no physical TV/phone validation.',
  companion, base: bases[0], generatedAt: new Date().toISOString(),
  sourceHashes: Object.fromEntries(['src/qr.js', 'src/vendor/qrcodegen.js', 'src/patterns.js', 'test/qr-decode.mjs'].map(file => [file, createHash('sha256').update(readFileSync(join(companion, file))).digest('hex')])),
  decoder: { name: 'jsqr', version: require('jsqr/package.json').version, license: require('jsqr/package.json').license },
  rasterizer: { name: '@resvg/resvg-js', version: require('@resvg/resvg-js/package.json').version, license: require('@resvg/resvg-js/package.json').license },
  patchCount: PATCHES.length, languages: ['en', 'es'], variantsPerPayload: 7,
  passed: results.length - failed.length, failed: failed.length, total: results.length,
  blankNegativeControl: 'passed', results
};
writeFileSync(join(output, 'qr-decode-results.json'), JSON.stringify(summary, null, 2) + '\n');
console.log(JSON.stringify({ ...summary, output, results: undefined }, null, 2));
for (const failure of failed) console.error(JSON.stringify(failure));
if (failed.length) process.exitCode = 1;

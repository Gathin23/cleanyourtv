import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8');

// Source-contract guards, not a runtime network audit or proof about hosting logs.
test('Android keeps the standalone permission-free module-card boundary', () => {
  const manifest = read('../../app/src/main/AndroidManifest.xml');
  assert.doesNotMatch(manifest, /<uses-permission\b|<service\b|<receiver\b/);
  assert.match(manifest, /android.intent.category.LEANBACK_LAUNCHER/);
  assert.match(manifest, /com.gathin.tvhome.category.DOCK_CARD/);
  assert.match(manifest, /android:resource="@xml\/module_card"/);
  assert.match(read('../../app/src/main/res/xml/module_card.xml'), /<card spec="1" label="TV Tune"/);
});

test('companion keeps restrictive connections and local page assets', () => {
  const html = read('../index.html');
  assert.match(html, /connect-src 'none'/);
  assert.match(html, /form-action 'none'/);
  for (const match of html.matchAll(/<(\w+)\b[^>]*\b(?:src|href)="([^"]+)"[^>]*>/g)) {
    const [, tag, target] = match;
    if (/^https?:/.test(target)) {
      assert.equal(tag, 'a', 'Only user-selected links may use an external origin');
      assert.match(target, /^https:\/\/github\.com\/(?:kalt2212|Gathin23)\/cleanyourtv\//);
      assert.match(match[0], /rel="noopener noreferrer"/);
    }
    else assert.doesNotMatch(target, /^(?:\/\/|[a-z][a-z0-9+.-]*:)/i);
  }
  assert.doesNotMatch(html, /<form\b|<iframe\b/);
});

test('shipped modules do not add application networking or upload APIs', () => {
  for (const file of ['app', 'capture', 'patterns', 'probe', 'qr', 'vendor/qrcodegen']) {
    assert.doesNotMatch(read(`../src/${file}.js`), /\b(?:fetch|XMLHttpRequest|WebSocket|EventSource|sendBeacon|RTCPeerConnection)\s*\(/,
      `Review privacy documentation before adding networking to ${file}`);
  }
  assert.match(read('../src/app.js'), /URL\.createObjectURL\(new Blob/);
});

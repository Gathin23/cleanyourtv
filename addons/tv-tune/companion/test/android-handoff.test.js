import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PATCHES } from '../src/patterns.js';

// Source-contract regression only: this does not validate Android layout or a real TV.
for (const locale of ['values', 'values-es']) {
  test(`Android ${locale} gives the public companion and honest manual handoff`, () => {
    const strings = readFileSync(new URL(`../../app/src/main/res/${locale}/strings.xml`, import.meta.url), 'utf8');
    const body = ['fields_body', 'companion_url', 'fields_limit'].map(name => {
      const value = strings.match(new RegExp(`<string name="${name}">([^<]+)</string>`))?.[1];
      assert.ok(value, `Visible handoff text missing: ${name}`);
      return value;
    }).join(' ');
    assert.ok(body.includes('https://tv-tune-kalt2212.vercel.app/'));
    assert.ok(body.includes('ID') && body.includes('RGB'));
    assert.match(body, locale === 'values' ? /no automatic pairing/i : /no hay emparejamiento automático/i);
    assert.match(body, locale === 'values' ? /not colour accuracy/i : /no la precisión del color/i);
  });
}

test('Android and companion use exactly the same ordered patch IDs and RGB codes', () => {
  const source = readFileSync(new URL('../../app/src/main/java/io/github/kalt2212/tvtune/PatternState.java', import.meta.url), 'utf8');
  const ids = [...source.match(/String\[\] IDS = \{([^;]+)\};/)[1].matchAll(/"([^"]+)"/g)].map(match => match[1]);
  const rgb = [...source.match(/int\[\]\[\] RGB = \{([^;]+)\};/)[1].matchAll(/\{(\d+),(\d+),(\d+)\}/g)].map(match => match.slice(1).map(Number));
  assert.deepEqual(ids, PATCHES.map(patch => patch.id));
  assert.deepEqual(rgb, PATCHES.map(patch => patch.rgb));
});

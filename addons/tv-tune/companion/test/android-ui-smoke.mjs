// Optional UI test for a disposable Android emulator. Resets only TV Tune's own data.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';

const serial = process.argv[2];
if (!/^emulator-\d+$/.test(serial ?? '')) throw Error('Supply a disposable emulator serial, never a physical TV.');
const pkg = 'io.github.kalt2212.tvtune';
const adb = (...args) => execFileSync('adb', ['-s', serial, ...args], { timeout: 20000, encoding: 'utf8' });
assert.equal(adb('shell', 'getprop', 'ro.kernel.qemu').trim(), '1', 'An emulator is required');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'tv-tune-android-'));
const key = name => adb('shell', 'input', 'keyevent', name);
const decode = value => value.replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&#10;/g, '\n').replace(/&apos;/g, "'");
function nodes() {
  adb('shell', 'uiautomator', 'dump', '/data/local/tmp/tv-tune-smoke.xml');
  const xml = adb('shell', 'cat', '/data/local/tmp/tv-tune-smoke.xml');
  const result = [...xml.matchAll(/<node\b[^>]*>/g)].map(match => {
    const node = {};
    for (const attr of match[0].matchAll(/([\w-]+)="([^"]*)"/g)) node[attr[1]] = decode(attr[2]);
    return node;
  });
  assert.ok(result.some(node => node.package === pkg), 'TV Tune must be the visible app');
  return result;
}
function focus(text) {
  for (let tries = 0; tries < 12; tries++) {
    const all = nodes(), target = all.find(node => node.text === text);
    assert.ok(target, `Missing control: ${text}`);
    const current = all.find(node => node.focused === 'true');
    if (current?.text === text) return;
    if (!current) { key('KEYCODE_DPAD_DOWN'); continue; }
    const centre = node => {
      const [x1, y1, x2, y2] = node.bounds.match(/\d+/g).map(Number);
      return [(x1 + x2) / 2, (y1 + y2) / 2];
    };
    const [cx, cy] = centre(current), [tx, ty] = centre(target);
    key(Math.abs(ty - cy) > 10 ? (ty > cy ? 'KEYCODE_DPAD_DOWN' : 'KEYCODE_DPAD_UP') : (tx > cx ? 'KEYCODE_DPAD_RIGHT' : 'KEYCODE_DPAD_LEFT'));
  }
  throw Error(`D-pad cannot reach ${text}`);
}
function click(text) { focus(text); key('KEYCODE_DPAD_CENTER'); }
function screenshot(name) {
  fs.writeFileSync(path.join(output, name), execFileSync('adb', ['-s', serial, 'exec-out', 'screencap', '-p'], { timeout: 20000 }));
}

adb('shell', 'pm', 'clear', pkg);
key('KEYCODE_WAKEUP'); adb('shell', 'wm', 'dismiss-keyguard');
adb('shell', 'am', 'start', '-W', '-n', `${pkg}/.TuneActivity`);
const cover = nodes();
assert.ok(!cover.some(node => node['content-desc'] === 'TV Tune' && node.class === 'android.view.View' && node.focusable === 'true'), 'Decorative welcome artwork must not receive D-pad focus');
screenshot('welcome-emulator.png');
click('Start picture setup');
assert.ok(nodes().some(node => node.text === 'Before the first pattern.'));
click('I’m ready — show the checks');
for (let check = 1; check <= 5; check++) {
  assert.ok(nodes().some(node => node.text === `CHECK ${check} OF 5`));
  click('Show pattern');
  assert.ok(nodes().some(node => node['content-desc'] === 'Show pattern'));
  if (check === 1) screenshot('geometry-emulator.png');
  key('KEYCODE_BACK'); click('Skip this check');
}
assert.ok(nodes().some(node => node.text === 'Your picture checklist.'));
screenshot('checklist-emulator.png');
key('KEYCODE_BACK');
click('Phone experiment fields'); click('Show experiment fields');
key('KEYCODE_DPAD_RIGHT'); screenshot('phone-fields-emulator.png');
key('KEYCODE_BACK'); key('KEYCODE_BACK');
assert.ok(nodes().some(node => node.text === 'Resume picture setup'));
console.log(`Android emulator D-pad flow verified. Artifacts: ${output}`);

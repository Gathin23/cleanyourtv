import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const base = new URL(process.argv[2] ?? 'https://tv-tune-kalt2212.vercel.app/');
if (base.protocol !== 'https:' || base.username || base.password) throw Error('Use a public HTTPS URL without credentials');
const root = new URL('../', import.meta.url);
const paths = ['index.html', 'styles.css', 'cover.svg', 'src/app.js', 'src/capture.js', 'src/patterns.js', 'src/probe.js', 'src/qr.js', 'src/vendor/qrcodegen.js'];
const expected = paths.map(path => ({ path, local: new URL(path, root) }));
expected.push({ path: 'LICENSE', local: new URL('../../../../LICENSE', import.meta.url) });
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
for (const { path, local } of expected) {
  // No cookies, authorization header or deployment bypass. Node validates TLS normally.
  const response = await fetch(new URL(path === 'index.html' ? './' : path, base));
  if (response.status !== 200 || new URL(response.url).origin !== base.origin) throw Error(`Not publicly available: ${path} (${response.status})`);
  const deployed = Buffer.from(await response.arrayBuffer()), source = await readFile(local);
  if (sha(deployed) !== sha(source)) throw Error(`Deployed content differs from local source: ${path}`);
  console.log(`Public HTTPS, source hash matches: ${path}`);
}
console.log('All public assets verified without authentication. This does not validate physical hardware.');

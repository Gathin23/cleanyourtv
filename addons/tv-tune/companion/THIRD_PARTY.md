# Local QR encoder

`src/vendor/qrcodegen.js` is Project Nayuki's QR Code generator under the MIT
license. Its full copyright and permission notice is retained at the top of the
publicly served source. No QR API, CDN or runtime npm download is used.

- Source: https://github.com/nayuki/QR-Code-generator
- Pinned commit: `3c6d0b3cefb4e049dc337e82237c9644399716a8`
- Input: `typescript-javascript/qrcodegen.ts`
- Input SHA-256: `1dc03fb5a10e0e2318ea162755bbdb9977ca6ce52cff959e9c9b6deafdccda9c`
- Generated with TypeScript 5.9.3: `tsc qrcodegen.ts --target ES2020 --outDir build`
- Only subsequent change: append the following ES-module export. Algorithm unchanged.

```js
// TV Tune: ES module export; upstream algorithm unchanged.
export default qrcodegen;
```

The vendored file is 45,449 bytes (about 12 KB gzip). The compiler is only needed
to reproduce this file, not to run or deploy the companion. The wrapper limits
symbols to version 10 and uses medium error correction, a four-module quiet zone,
black modules on white, and six pixels per module at the default display size.
Unusually long URLs fall back to the visible link instead of displaying a stale QR.
The handoff panel hides and pauses the patch before showing the QR, so the QR is
never part of an active measurement patch. Independent decoder checks use synthetic
images; they are not evidence of physical-phone scanning or display calibration.

The native Android experiment screen uses pre-generated QR vector resources from
this same encoder. `test/generate-native-qr.mjs` reproduces both localized assets
from the Android `companion_url` strings, with the same four-module quiet zone.
The encoder runs only during asset generation; no encoder library, QR web service
or additional Android runtime dependency is shipped for this feature.

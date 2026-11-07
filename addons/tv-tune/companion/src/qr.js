import qrcodegen from './vendor/qrcodegen.js';

// Bound size and encoding work for unusually long self-hosted URLs. The visible
// link remains the fallback. No URL is sent to a QR service or inserted as markup.
export function qrMatrix(text) {
  if (typeof text !== 'string' || !text.length || text.length > 1024) throw new RangeError('QR input length');
  const { QrCode, QrSegment } = qrcodegen;
  const qr = QrCode.encodeSegments(QrSegment.makeSegments(text), QrCode.Ecc.MEDIUM, 1, 10, -1, false);
  return Array.from({ length: qr.size }, (_, y) => Array.from({ length: qr.size }, (_, x) => qr.getModule(x, y)));
}

export function qrSvg(text) {
  const rows = qrMatrix(text), size = rows.length + 8;
  const path = [];
  rows.forEach((row, y) => row.forEach((dark, x) => {
    if (dark) path.push(`M${x + 4},${y + 4}h1v1h-1z`);
  }));
  // Four light modules on every side, six display pixels per module by default.
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size * 6}" height="${size * 6}" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges"><rect width="${size}" height="${size}" fill="#fff"/><path d="${path.join('')}" fill="#000"/></svg>`;
}

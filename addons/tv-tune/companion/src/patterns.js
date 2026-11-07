export const PATCHES = Object.freeze([
  { id: 'black', rgb: [0, 0, 0] },
  { id: 'grey-8', rgb: [8, 8, 8] },
  { id: 'grey-32', rgb: [32, 32, 32] },
  { id: 'grey-64', rgb: [64, 64, 64] },
  { id: 'grey-128', rgb: [128, 128, 128] },
  { id: 'grey-192', rgb: [192, 192, 192] },
  { id: 'grey-240', rgb: [240, 240, 240] },
  { id: 'white', rgb: [255, 255, 255] },
  { id: 'red', rgb: [255, 0, 0] },
  { id: 'green', rgb: [0, 255, 0] },
  { id: 'blue', rgb: [0, 0, 255] }
]);

export function presenterState(started = 0) {
  return {
    index: 4, paused: false, lastInput: started,
    move(direction, time) {
      if (this.paused) this.paused = false;
      else this.index = Math.max(0, Math.min(PATCHES.length - 1, this.index + direction));
      this.lastInput = time;
    },
    tick(time) { if (time - this.lastInput >= 120000) this.paused = true; },
    pause() { this.paused = true; }
  };
}

export function patchRectangle(width, height) {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) throw new Error('Invalid render size');
  const size = Math.min(Math.sqrt(width * height * 0.1), width, height);
  return { size, left: (width - size) / 2, top: (height - size) / 2 };
}

/** Fragment-only handoff: patch choice is never added to an HTTP query. */
export function patchFromFragment(fragment = '') {
  const params = new URLSearchParams(String(fragment).replace(/^#/, ''));
  const ids = params.getAll('patch');
  return (ids.length === 1 && PATCHES.find(patch => patch.id === ids[0])) || PATCHES[4];
}

/** Build a same-page path from allowlisted values, never arbitrary URL parameters. */
export function patchLink(view, english, patchId) {
  const patch = PATCHES.find(p => p.id === patchId) || PATCHES[4];
  const query = new URLSearchParams();
  if (view === 'tv') query.set('view', 'tv');
  if (english) query.set('lang', 'en');
  return `?${query}#patch=${patch.id}`;
}

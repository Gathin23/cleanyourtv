package io.github.cleanyourtv.staticfm;

import java.util.Arrays;

/**
 * Paints one frame of a style into a W x H ARGB buffer, which the view scales up without filtering. The grain
 * holds still per cell (see {@link #grain}), so characters change where the music moves the picture rather than
 * flickering. Tide, Dust, Dusk and Blots are abstract; Moon, Deep sea and Ocean Drive shade a real
 * scene, where a cell's brightness picks its character. Deep sea and Ocean Drive use the small font on a finer grid.
 * Pure Java with no Android calls, so the JVM tests can render real frames.
 */
final class Painter {
    static final int W = 960, H = 540;
    /** One character cell: the 5x7 glyph plus a pixel of air right and two below. */
    static final int CW = Glyphs.W + 1, CH = Glyphs.H + 2;
    static final int COLS = W / CW, ROWS = H / CH;

    static final int TIDE = 0, DUST = 1, DUSK = 2, BLOTS = 3, MOON = 4, DEEP = 5, DRIVE = 6;
    static final int STYLES = 7;

    /**
     * The fine grid for the realistic styles, Deep sea and Ocean Drive: the 3x5 font in a 4x6 cell, 240 by 90
     * characters. The other styles use the 5x7 font in a 6x9 cell, 160 by 60.
     */
    static final int FCW = Glyphs.SW + 1, FCH = Glyphs.SH + 1;
    static final int FCOLS = W / FCW, FROWS = H / FCH;
    private static final int FINE_CELLS = FCOLS * FROWS;
    private static final int[] FINE_LUM = fine("\0.:-=+*o%#");
    private static final int F_DOT = Glyphs.smallIndex('.'), F_STAR = Glyphs.smallIndex('*'),
            F_DASH = Glyphs.smallIndex('-'), F_TILDE = Glyphs.smallIndex('~'),
            F_PIPE = Glyphs.smallIndex('|'), F_SLASH = Glyphs.smallIndex('/'), F_BACK = Glyphs.smallIndex('\\');

    private static final int[] RAMP = idx(".,:;-=+*");
    private static final int[] DUSK_RAMP = idx("\0.,:;-=+*#");
    /** Dark to bright, for the styles that shade a real shape. */
    private static final int[] LUM = idx("\0.,:;-=+*%#");
    private static final int TILDE = Glyphs.index('~'), DASH = Glyphs.index('-'), PLUS = Glyphs.index('+'),
            STAR = Glyphs.index('*'), DOT = Glyphs.index('.'), APOS = Glyphs.index('\''), COLON = Glyphs.index(':');
    private static final float[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private float now;

    /**
     * A random number that belongs to a cell (and a channel k) and holds for about ten seconds, re-rolled at a
     * different moment in each cell. Ink appears where it falls under the ink's probability, so the picture only
     * changes where the music (or the slow drift) moves that probability across it, instead of flickering.
     */
    private float grain(int c, int r, int k) {
        float phase = Noise.hash(c * 31 + k, r * 17 - k);
        int slot = (int) (now * 0.1f + phase * 10f);
        return Noise.hash(c * 7919 + k * 104729 + slot * 31, r * 6271 - slot * 17 + k);
    }

    private static int[] fine(String chars) {
        int[] out = new int[chars.length()];
        for (int i = 0; i < out.length; i++) out[i] = chars.charAt(i) == '\0' ? -1 : Glyphs.smallIndex(chars.charAt(i));
        return out;
    }

    private static int[] idx(String chars) {
        int[] out = new int[chars.length()];
        for (int i = 0; i < out.length; i++) out[i] = chars.charAt(i) == '\0' ? -1 : Glyphs.index(chars.charAt(i));
        return out;
    }

    /** The contour field: slow noise, scaled into a few levels; the bass lifts it so the lines jump on a kick. */
    static float field(float u, float v, float t, float bass, float mid) {
        float bend = mid * 0.35f;
        return Noise.fbm(u * 3 + t * 0.013f + bend * 0.2f, v * 2.2f - t * 0.007f) * 6.5f + bass * 0.55f + bend;
    }

    private static float edge(float f, float width) {
        float d = Math.min(f, 1 - f) / width;
        return (float) Math.exp(-d * d);
    }

    void render(int[] frame, int style, Palette p, float t, float bass, float mid, float high) {
        now = t;
        Arrays.fill(frame, p.night);
        switch (style) {
            case DUST: dust(frame, p, t, bass, high); break;
            case DUSK: dusk(frame, p, t, bass); break;
            case BLOTS: blots(frame, p, t, bass, mid, high); break;
            case MOON: moon(frame, p, t, bass, mid, high); break;
            case DEEP: deep(frame, p, t, bass, mid, high); break;
            case DRIVE: drive(frame, p, t, bass, mid, high); break;
            default: tide(frame, p, t, bass, mid, high); break;
        }
    }

    private void sparkle(int[] frame, int c, int r, float high, int color) {
        if (high > 0.15f && grain(c, r, 6) < high * 0.015f) Glyphs.draw(frame, W, H, STAR, c * CW, r * CH, color);
    }

    private void tide(int[] frame, Palette p, float t, float bass, float mid, float high) {
        int bg = p.night;
        int[] levels = {p.inkA, p.inkB, p.both,
                Palette.mix(p.inkA, p.inkB, 0.5f), Palette.mix(p.inkB, p.both, 0.5f)};
        for (int i = 0; i < levels.length; i++) levels[i] = Palette.mix(bg, levels[i], 0.85f);
        float reach = 0.06f + bass * 0.09f;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float val = field(c / (float) COLS, r / (float) ROWS, t, bass, mid);
                float f = val - (float) Math.floor(val);
                float near = Math.min(f, 1 - f);
                if (near > reach) {
                    sparkle(frame, c, r, high, levels[2]);
                    continue;
                }
                if (grain(c, r, 1) < 0.12f) continue; // a little grain in the line itself
                int g = near < reach * 0.4f ? TILDE : DASH;
                Glyphs.draw(frame, W, H, g, c * CW, r * CH, levels[((int) Math.floor(val)) % levels.length]);
            }
        }
    }

    private void dust(int[] frame, Palette p, float t, float bass, float high) {
        int bg = p.night;
        int warm = Palette.mix(p.inkB, p.both, 0.5f), cool = p.dust;
        int[] glyphs = {DOT, APOS, DOT, COLON, STAR};
        float wide = 0.16f + bass * 0.14f;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float u = c / (float) COLS, v = r / (float) ROWS;
                float d = (u - 0.5f - (v - 0.5f) * 0.35f) / wide;
                float beam = (float) Math.exp(-d * d);
                float n = Noise.fbm(u * 14 - t * 0.08f, v * 9 + t * 0.01f);
                if (n < 0.66f - beam * (0.10f + bass * 0.12f) - bass * 0.05f) {
                    sparkle(frame, c, r, high * beam, warm);
                    continue;
                }
                if (grain(c, r, 1) < 0.25f) continue;
                int col = Palette.mix(bg, Palette.mix(cool, warm, beam), Math.min(1f, 0.3f + beam * (0.5f + bass * 0.4f)));
                int g = glyphs[(int) (Noise.hash(c, r) * glyphs.length)];
                Glyphs.draw(frame, W, H, g, c * CW, r * CH, col);
            }
        }
    }

    private void dusk(int[] frame, Palette p, float t, float bass) {
        int bg = p.night;
        int skyLo = Palette.mix(bg, p.inkA, 0.5f), skyHi = Palette.mix(p.inkB, p.both, 0.25f);
        int ground = Palette.mix(bg, p.dust, 0.55f);
        final float horizon = 0.62f; // fixed: the sky and ground answer the music, the line between them stays put
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float u = c / (float) COLS, v = r / (float) ROWS;
                boolean sky = v < horizon;
                float val = sky
                        ? (0.12f + v * 0.85f) * (0.65f + bass * 0.55f) + Noise.fbm(u * 3 + t * 0.01f, v * 4) * 0.18f
                        : 0.12f + bass * 0.08f + Noise.fbm(u * 6 + t * 0.015f, v * 12) * 0.2f;
                float threshold = BAYER[(r & 3) * 4 + (c & 3)] / 16f;
                float level = val * (DUSK_RAMP.length - 1) + threshold - 0.5f;
                int i = Math.max(0, Math.min(DUSK_RAMP.length - 1, Math.round(level)));
                if (DUSK_RAMP[i] < 0) continue;
                int col = sky ? Palette.mix(skyLo, skyHi, Math.min(1, val)) : ground;
                Glyphs.draw(frame, W, H, DUSK_RAMP[i], c * CW, r * CH, col);
            }
        }
    }

    private static final int SEEDS = 9;
    /** The blots' ragged edge, a fixed texture: worked out once per cell. */
    private float[] raggedEdge;

    private float ragged(int i, float u, float v) {
        if (raggedEdge == null) {
            raggedEdge = new float[COLS * ROWS];
            Arrays.fill(raggedEdge, Float.NaN);
        }
        if (Float.isNaN(raggedEdge[i])) raggedEdge[i] = (Noise.fbm(u * 9, v * 6) - 0.5f) * 0.09f;
        return raggedEdge[i];
    }

    /**
     * Nine ink blots with ragged rims. Each belongs to the bass, the mids or the highs and grows and shrinks with
     * it; where two overlap they print the third colour.
     */
    private void blots(int[] frame, Palette p, float t, float bass, float mid, float high) {
        int a = p.inkA, b = p.inkB, both = p.both;
        float[] sx = new float[SEEDS], sy = new float[SEEDS], rad = new float[SEEDS];
        for (int i = 0; i < SEEDS; i++) {
            sx[i] = 0.08f + 0.84f * Noise.hash(i * 13 + 1, 5) + 0.03f * (float) Math.sin(t * 0.05f + i);
            sy[i] = 0.10f + 0.80f * Noise.hash(i * 29 + 2, 9) + 0.03f * (float) Math.cos(t * 0.04f + i * 2);
            float level = i % 3 == 0 ? bass : i % 3 == 1 ? mid : high;
            rad[i] = (0.07f + 0.09f * Noise.hash(i, 77)) * (0.5f + level * 0.95f);
        }
        float aspect = W / (float) H;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float u = c / (float) COLS, v = r / (float) ROWS;
                float ragged = ragged(r * COLS + c, u, v);
                float pa = 0, pb = 0;
                for (int i = 0; i < SEEDS; i++) {
                    float dx = (u - sx[i]) * aspect, dy = v - sy[i];
                    // far outside this blot and its rim: it adds nothing here, skip the square root and exp
                    float reach = (rad[i] + 0.08f) * aspect / 1.6f;
                    if (Math.abs(dx) > reach || Math.abs(dy) > reach) continue;
                    float d = (float) Math.sqrt(dx * dx + dy * dy) / aspect * 1.6f + ragged;
                    float edge = (d - rad[i]) / 0.012f;
                    float rim = (float) Math.exp(-edge * edge);
                    float pr = rim * 0.75f + (d < rad[i] ? 0.26f + 0.34f * (1 - d / rad[i]) : 0);
                    if (i % 2 == 0) pa = Math.max(pa, pr);
                    else pb = Math.max(pb, pr);
                }
                boolean ia = grain(c, r, 1) < pa, ib = grain(c, r, 2) < pb;
                if (!ia && !ib) {
                    sparkle(frame, c, r, high, both);
                    continue;
                }
                float pr = Math.min(1f, Math.max(pa, pb));
                int g = RAMP[Math.min(RAMP.length - 1, (int) (pr * RAMP.length * (0.5f + grain(c, r, 3) * 0.7f)))];
                if (ia && ib) g = PLUS;
                Glyphs.draw(frame, W, H, g, c * CW, r * CH, ia && ib ? both : ia ? a : b);
            }
        }
    }

    /** Prints one cell of a shaded shape: brightness 0..1 picks the glyph, with the cell's own grain as dither. */
    private void shade(int[] frame, int c, int r, float lum, int color) {
        float level = lum * (LUM.length - 1) + grain(c, r, 1) - 0.5f;
        int i = Math.max(0, Math.min(LUM.length - 1, Math.round(level)));
        if (LUM[i] >= 0) Glyphs.draw(frame, W, H, LUM[i], c * CW, r * CH, color);
    }

    private static final float ASPECT = W / (float) H;
    /** Crater centres on the disc (x, y in moon radii) and their radius. */
    private static final float[][] CRATERS = {
        {-0.35f, -0.30f, 0.16f}, {0.30f, 0.38f, 0.12f}, {0.05f, -0.55f, 0.09f}, {-0.55f, 0.25f, 0.10f},
        {0.48f, -0.15f, 0.14f}, {-0.10f, 0.20f, 0.07f}, {0.20f, -0.05f, 0.06f},
    };

    /**
     * A shaded moon: a sphere lit from a slowly moving sun, with dark seas and craters. The bass brightens it and
     * swells the glow around it, the mids nudge the light, the highs make the stars twinkle.
     */
    private void moon(int[] frame, Palette p, float t, float bass, float mid, float high) {
        float radius = 0.36f, cx = 0.5f * ASPECT, cy = 0.5f;
        double a = 0.55 + 0.45 * Math.sin(t * 0.02) + mid * 0.15;
        float lx = (float) Math.sin(a), ly = -0.28f, lz = (float) Math.cos(a);
        float ln = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ln;
        ly /= ln;
        lz /= ln;
        int sea = Palette.mix(p.inkA, p.both, 0.35f), glow = Palette.mix(p.night, p.inkB, 0.8f);
        int star = Palette.mix(p.night, p.both, 0.7f);
        float pulse = 0.75f + bass * 0.4f;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float x = ((c + 0.5f) / COLS * ASPECT - cx) / radius, y = ((r + 0.5f) / ROWS - cy) / radius;
                float d2 = x * x + y * y;
                if (d2 >= 1) {
                    float out = (float) Math.sqrt(d2) - 1;
                    float halo = (float) Math.exp(-out / (0.04f + bass * 0.07f)) * (0.10f + bass * 0.30f);
                    if (halo > 0.05f) {
                        shade(frame, c, r, halo, glow);
                    } else if (grain(c, r, 7) < 0.012f) {
                        // a fixed star field; the highs light up more of it
                        boolean lit = Noise.hash(c, r) < 0.35f + high * 0.9f;
                        if (lit) {
                            int g = high > 0.5f && Noise.hash(r, c) < 0.3f ? STAR : DOT;
                            Glyphs.draw(frame, W, H, g, c * CW, r * CH, star);
                        }
                    }
                    continue;
                }
                float z = (float) Math.sqrt(1 - d2);
                float diffuse = Math.max(0, x * lx + y * ly + z * lz);
                float spin = t * 0.004f;
                float seas = Noise.fbm(x * 1.6f + spin + 11, y * 1.6f + 3);
                boolean dark = seas > 0.55f;
                float albedo = dark ? 0.45f : 0.8f + 0.2f * Noise.fbm(x * 6 + spin, y * 6);
                for (float[] k : CRATERS) {
                    float dx = x - k[0], dy = y - k[1], q = (float) Math.sqrt(dx * dx + dy * dy) / k[2];
                    if (q < 1.15f) {
                        // floor in shadow, far rim catching the light
                        float toward = (dx * lx + dy * ly) / k[2];
                        albedo *= q < 0.8f ? 0.7f : 1f + 0.5f * Math.max(0, toward);
                    }
                }
                // a little earthshine on the night side, and a lift so the lit face prints dense
                float lum = Math.min(1f, (float) Math.pow(diffuse * albedo * pulse, 0.65) + 0.10f * albedo);
                shade(frame, c, r, lum, dark ? sea : p.both);
            }
        }
    }

    // ---- the fine grid -------------------------------------------------------------------------------------

    /** One fine cell of a shaded scene: brightness 0..1 picks the small character, the cell's grain dithers it. */
    private void fineShade(int[] frame, int c, int r, float lum, int color) {
        float level = lum * (FINE_LUM.length - 1) + grain(c, r, 1) - 0.5f;
        int i = Math.max(0, Math.min(FINE_LUM.length - 1, Math.round(level)));
        if (FINE_LUM[i] >= 0) Glyphs.drawSmall(frame, W, H, FINE_LUM[i], c * FCW, r * FCH, color);
    }

    private void fineGlyph(int[] frame, int c, int r, int g, int color) {
        Glyphs.drawSmall(frame, W, H, g, c * FCW, r * FCH, color);
    }

    /** sqrt(x² + y²); Math.hypot is slow on Android and its care for overflow is not needed here. */
    private static float len(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float sq(float v) {
        return v * v;
    }

    private static float wrap(float v, float lo, float hi) {
        float span = hi - lo;
        return lo + (((v - lo) % span) + span) % span;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    // Per-frame buffers for the fine scenes: thin parts (tentacles, arms, fronds) are stamped along their path
    // into these, rather than every cell testing every part, which kept the TV under 15 frames a second.
    private final float[] bufLum = new float[FINE_CELLS];
    private final int[] bufCol = new int[FINE_CELLS];
    private final int[] bufGlyph = new int[FINE_CELLS];

    private void clearBuffers() {
        Arrays.fill(bufLum, 0);
        Arrays.fill(bufGlyph, -1);
    }

    /**
     * Stamps a soft ellipse of radii (rx, ry) at (px, py) into the buffers, keeping the brighter value per cell.
     * {@code fall} dims it towards its edge; {@code side} picks colour b for cells right of the centre (1) or
     * above it (2), for a rim of light; {@code glyph} >= 0 prints that glyph instead of a shade.
     */
    private void stamp(float px, float py, float rx, float ry, float lum, float fall, int a, int b, int side, int glyph) {
        float cell = ASPECT / FCOLS;
        int c0 = (int) Math.floor((px - rx) / cell), c1 = (int) Math.floor((px + rx) / cell);
        int r0 = (int) Math.floor((py - ry) * FROWS), r1 = (int) Math.floor((py + ry) * FROWS);
        for (int r = Math.max(0, r0); r <= Math.min(FROWS - 1, r1); r++) {
            for (int c = Math.max(0, c0); c <= Math.min(FCOLS - 1, c1); c++) {
                float x = (c + 0.5f) * cell, y = (r + 0.5f) / FROWS;
                float d = rx > 0 && ry > 0 ? sq((x - px) / rx) + sq((y - py) / ry) : 0;
                if (d > 1 && !(c == (int) (px / cell) && r == (int) (py * FROWS))) continue;
                float v = lum * (1 - fall * Math.min(1, d));
                int i = r * FCOLS + c;
                if (v <= bufLum[i]) continue;
                bufLum[i] = v;
                bufCol[i] = side == 1 && x > px || side == 2 && y < py ? b : a;
                bufGlyph[i] = glyph;
            }
        }
    }

    // ---- Deep sea ------------------------------------------------------------------------------------------

    private static final int SNOW = 140, DEEP_TENTACLES = 9, ORAL_ARMS = 4;
    /** A longer ramp with letters, for the volume of the bell: the denser the glyph, the brighter the skin. */
    private static final int[] DEEP_LUM = fine("\0.:'-=+otxX%S8#@".replace('x', '*'));
    /**
     * The jellyfish: where each starts (x as a share of the width, y of the height), its size, the angle it swims
     * at (radians from upright, positive leaning its bell left) and its speed along that heading.
     */
    private static final float[][] SCHOOL = {
        {0.13f, 0.30f, 0.075f, 0.35f, 0.010f},
        {0.47f, 0.16f, 0.085f, -0.10f, 0.008f},
        {0.84f, 0.45f, 0.095f, -0.45f, 0.007f},
        {0.50f, 0.74f, 0.125f, 0.15f, 0.006f},
        {0.25f, 0.82f, 0.060f, 0.55f, 0.011f},
    };
    /** The bass, averaged over a few seconds: the bells breathe with the music rather than jump on each kick. */
    private float pulse;
    /** Set by the view when the owner picked Lively: Deep sea then follows the music more closely. */
    boolean lively;

    private void deepShade(int[] frame, int c, int r, float lum, int color) {
        float level = lum * (DEEP_LUM.length - 1) + grain(c, r, 1) - 0.5f;
        int i = Math.max(0, Math.min(DEEP_LUM.length - 1, Math.round(level)));
        if (DEEP_LUM[i] >= 0) Glyphs.drawSmall(frame, W, H, DEEP_LUM[i], c * FCW, r * FCH, color);
    }

    /** A stroke glyph that follows a direction on screen (dx, dy). */
    private static int stroke(float dx, float dy) {
        double a = Math.toDegrees(Math.atan2(dy, dx));
        if (a < 0) a += 180;
        if (a < 22.5 || a >= 157.5) return F_DASH;
        if (a < 67.5) return F_BACK;
        if (a < 112.5) return F_PIPE;
        return F_SLASH;
    }

    /**
     * A few jellyfish swimming slowly through deep water, on the fine grid. Each bell is translucent: sparse in the
     * middle, a glowing rim, a warm core showing through; under it a frilled mass of oral arms and long tentacles
     * drawn as dash-dot strokes that follow their curve. The music is felt, not seen jumping: the bells breathe with
     * the bass averaged over seconds, the tentacles sway a little more with the mids, marine snow glints on the
     * highs, all over a faint grid.
     */
    private void deep(int[] frame, Palette p, float t, float bass, float mid, float high) {
        float cell = ASPECT / FCOLS;
        pulse += (Math.min(lively ? 2.1f : 1.2f, bass) - pulse) * (lively ? 0.07f : 0.03f);
        float sway = lively ? mid * 2f : mid;
        int rimC = p.inkA, coreC = Palette.mix(p.inkB, p.both, 0.35f), hiC = p.both;
        int armC = Palette.mix(p.inkB, p.dust, 0.25f), tentC = Palette.mix(p.inkA, p.both, 0.35f);
        int gridC = Palette.mix(p.night, p.dust, 0.30f);
        clearBuffers();
        for (int j = 0; j < SCHOOL.length; j++) {
            float[] o = SCHOOL[j];
            float size = o[2], tilt = o[3] + 0.04f * (float) Math.sin(t * 0.05f + j);
            float cs = (float) Math.cos(tilt), sn = (float) Math.sin(tilt);
            // swim along the heading, bell first; wrap round only far enough off screen that nothing pops
            float d = t * o[4];
            float cx = wrap(o[0] * ASPECT - sn * d, -0.45f, ASPECT + 0.45f);
            float cy = wrap(o[1] - cs * d, -0.35f, 1.0f + 0.85f);
            float breath = (float) Math.sin(t * 0.9f + j * 1.7f) * 0.5f + 0.5f;
            float squeeze = lively ? 0.45f * breath + 0.55f * pulse : 0.6f * breath + 0.4f * pulse;
            float bw = size * (1.04f - (lively ? 0.10f : 0.06f) * squeeze), bh = size * (0.86f + (lively ? 0.12f : 0.07f) * squeeze);
            jellyfish(cx, cy, cs, sn, bw, bh, t, j, sway, rimC, coreC, hiC, armC, tentC, cell);
        }
        for (int r = 0; r < FROWS; r++) {
            for (int c = 0; c < FCOLS; c++) {
                int i = r * FCOLS + c;
                if (bufLum[i] > 0.05f) {
                    if (bufGlyph[i] >= 0) Glyphs.drawSmall(frame, W, H, bufGlyph[i], c * FCW, r * FCH, bufCol[i]);
                    else deepShade(frame, c, r, Math.min(1f, bufLum[i]), bufCol[i]);
                } else if (c % 6 == 0 && r % 4 == 0) {
                    Glyphs.drawSmall(frame, W, H, F_DOT, c * FCW, r * FCH, gridC); // the faint grid
                }
            }
        }
        // marine snow, sinking; the highs light some of it
        int snowC = Palette.mix(p.night, p.dust, 0.75f);
        for (int i = 0; i < SNOW; i++) {
            float sx = frac(Noise.hash(i, 3) + 0.02f * (float) Math.sin(t * 0.2f + i)) * ASPECT;
            float sy = frac(Noise.hash(i, 7) + t * (0.004f + 0.006f * Noise.hash(i, 11)));
            int c = (int) (sx / cell), r = (int) (sy * FROWS);
            boolean glint = high > 0.3f && Noise.hash(i, 13) < high * 0.25f;
            fineGlyph(frame, c, r, glint ? F_STAR : F_DOT, glint ? p.both : snowC);
        }
    }

    /** Stamps one jellyfish into the buffers; u runs down its body from the bell's rim, v across it. */
    private void jellyfish(float cx, float cy, float cs, float sn, float bw, float bh, float t, int j, float mid,
                           int rimC, int coreC, int hiC, int armC, int tentC, float cell) {
        // the bell, cell by cell inside its own box
        float reach = len(bw, bh) * 1.05f;
        int c0 = Math.max(0, (int) ((cx - reach) / cell)), c1 = Math.min(FCOLS - 1, (int) ((cx + reach) / cell));
        int r0 = Math.max(0, (int) ((cy - reach) * FROWS)), r1 = Math.min(FROWS - 1, (int) ((cy + reach) * FROWS));
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                float ox = (c + 0.5f) * cell - cx, oy = (r + 0.5f) / FROWS - cy;
                float u = ox * sn + oy * cs, v = ox * cs - oy * sn;
                // the margin, seen a little from below, is a curve; scalloped
                float margin = 0.30f * bh * (1 - sq(v / bw)) + 0.05f * bh * Math.abs((float) Math.sin(v / bw * Math.PI * 4.5));
                float e = sq(v / bw) + sq(Math.min(u, 0) / bh);
                if (u > margin || e >= 1) continue;
                // translucent: little ink in the middle, a glowing rim, the clover-shaped core showing through
                float nz = (float) Math.sqrt(1 - e);
                float light = Math.max(0, 0.4f * nz - 0.15f * (v / bw) - 0.2f * (u / bh));
                float rim = e * e * e;
                float lobes = 0.6f + 0.4f * (float) Math.cos(4 * Math.atan2(u + 0.42f * bh, v));
                float core = (float) Math.exp(-(sq(v / (0.42f * bw)) + sq((u + 0.42f * bh) / (0.36f * bh))) * 1.6f) * lobes;
                int i = r * FCOLS + c;
                float lum = 0.06f + 0.16f * light + 0.50f * rim + 0.30f * core;
                if (lum <= bufLum[i]) continue;
                bufLum[i] = lum;
                bufCol[i] = core > 0.35f ? Palette.mix(coreC, hiC, Math.min(1, core - 0.35f)) : rimC;
                bufGlyph[i] = -1;
            }
        }
        float step = cell * 0.5f;
        // oral arms: a frilled, twisting mass under the bell
        for (int k = 0; k < ORAL_ARMS; k++) {
            for (float u = 0; u < bh * 3.0f; u += step) {
                float down = u / (bh * 3.0f);
                float off = (k - 1.5f) * 0.16f * bw * (1 + 0.3f * down);
                float v = off + (float) Math.sin(u / bh * 2.6f - t * 0.5f + k * 1.4f + j) * 0.18f * bw * (0.6f + 0.3f * mid) * down;
                float width = 0.11f * bw * (1.2f - down) * (0.6f + 0.6f * sq((float) Math.sin(u / bh * 6 + k))) + cell * 0.4f;
                float uu = u + 0.30f * bh * (1 - Math.min(1, sq(v / bw)));
                float frill = 0.7f + 0.3f * (float) Math.sin(u / bh * 40 + k);
                float lum = (0.50f - 0.34f * down) * frill;
                int col = Palette.mix(armC, hiC, 0.3f * frill * (1 - down));
                stamp(cx + uu * sn + v * cs, cy + uu * cs - v * sn, width, width, lum, 0.5f, col, col, 0, -1);
            }
        }
        // tentacles: long dash-dot strokes, each following its own curve
        for (int k = 0; k < DEEP_TENTACLES; k++) {
            float tv = (-0.95f + 1.9f * k / (DEEP_TENTACLES - 1)) * bw;
            float len = bh * (3.6f + 1.8f * Noise.hash(k + j * 17, 31));
            float wobble = (0.08f + 0.08f * Noise.hash(k, 3)) * bw * (0.6f + 0.3f * mid);
            float curl = (Noise.hash(k + j * 7, 5) - 0.4f) * 1.6f, turns = 0.6f + 0.5f * Noise.hash(k, 7);
            float prevX = Float.NaN, prevY = 0;
            for (float u = 0; u < len; u += step) {
                float down = u / len;
                float v = tv * (1 + 0.55f * down)
                        + wobble * (float) Math.pow(down, 1.2) * (float) Math.sin(u / bh * 1.5f - t * 0.6f + k * 0.9f + j)
                        + bw * curl * (float) Math.sin(down * Math.PI * turns) * down;
                float uu = u + 0.30f * bh * (1 - Math.min(1, sq(v / bw)));
                float x = cx + uu * sn + v * cs, y = cy + uu * cs - v * sn;
                if (!Float.isNaN(prevX) && frac(u / bh * 7 + k * 0.37f) <= 0.72f) {   // gaps make it dash-dot
                    float lum = 0.38f * (float) Math.pow(1 - down, 1.1) + 0.12f;
                    stamp(x, y, 0, 0, lum, 0, tentC, tentC, 0, stroke(x - prevX, y - prevY));
                }
                prevX = x;
                prevY = y;
            }
        }
    }

    // ---- Ocean Drive ---------------------------------------------------------------------------------------

    private static final float HORIZON = 0.56f, SAND = 0.665f, CURB = 0.705f;
    private static final float SUN_X = 0.68f * ASPECT, SUN_R = 0.17f;
    private static final float PALM_GAP = 0.62f;

    /**
     * A car parked by the sea at dawn, Miami style, on the fine grid, while palms scroll past. The bass is the sun:
     * it pulses the striped sun and its glow, lights the road so the palm shadows stand out, and brightens what
     * faces it: the car's front, bonnet and windscreen, the sunward sides of the towers. The car itself holds still.
     * The mids sway the palm fronds and light windows in the skyline; the highs glint on the sea.
     */
    private void drive(int[] frame, Palette p, float t, float bass, float mid, float high) {
        float cell = ASPECT / FCOLS;
        float sun = 0.75f + 0.35f * Math.min(2.1f, bass);
        float sunY = HORIZON - 0.015f;
        float scroll = t * 0.16f;
        int first = (int) Math.floor((scroll - 0.4f) / PALM_GAP);
        int palms = (int) Math.ceil((ASPECT + 0.8f) / PALM_GAP) + 1;
        float[] bx = new float[palms], by = new float[palms], ph = new float[palms], lean = new float[palms];
        for (int k = 0; k < palms; k++) {
            int i = first + k;
            bx[k] = i * PALM_GAP + Noise.hash(i, 1) * 0.28f - scroll;
            by[k] = CURB - 0.004f + Noise.hash(i, 2) * 0.008f;
            ph[k] = 0.38f + 0.12f * Noise.hash(i, 3);
            lean[k] = (Noise.hash(i, 4) - 0.5f) * 0.14f;
        }

        if (car == null) car = new Car(CAR_X, CAR_GROUND, cell, FCOLS, FROWS);
        float pulse = Math.max(0, Math.min(1.6f, (sun - 0.75f) / 0.45f)); // 0 in the quiet, 1 on a hard kick, more on Lively
        int grey = Palette.mix(p.night, p.dust, 0.85f);
        int silhouette = Palette.mix(p.night, p.inkA, 0.45f);
        int rim = Palette.mix(p.inkB, p.both, 0.6f);
        int skyTop = Palette.mix(p.night, p.inkA, 0.55f), skyLow = Palette.mix(p.inkB, p.both, 0.30f);
        int seaC = Palette.mix(p.inkA, p.both, 0.25f), sandC = Palette.mix(p.inkB, p.both, 0.55f);
        int roadC = Palette.mix(Palette.mix(p.night, p.dust, 0.9f), p.inkB, 0.3f), cityC = Palette.mix(p.night, p.inkA, 0.38f);

        // palms: trunk and fronds, dark against the dawn, lit at the edge by the sun; stamped along their shapes
        clearBuffers();
        int trunkLit = Palette.mix(silhouette, rim, 0.35f * sun), frondLit = Palette.mix(silhouette, rim, 0.25f * sun);
        for (int k = 0; k < palms; k++) {
            if (bx[k] < -0.4f || bx[k] > ASPECT + 0.4f) continue;
            for (float s = 0; s <= 1; s += cell * 0.5f / ph[k]) {
                float tx = bx[k] + lean[k] * s * s, ty = by[k] - ph[k] * s;
                float w = 0.015f * (1 - 0.35f * s) + cell * 0.4f;
                stamp(tx, ty, w, cell * 0.4f, 0.85f * (0.75f + 0.25f * (float) Math.sin(s * 70)), 0, silhouette, trunkLit, 1, -1);
            }
            float hx = bx[k] + lean[k], hy = by[k] - ph[k];
            for (int f = 0; f < 9; f++) {
                float a = (float) Math.PI * (0.04f + 0.92f * f / 8f) + (Noise.hash(f, k + first) - 0.5f) * 0.2f;
                float len = 0.20f + 0.06f * Noise.hash(f + 9, k + first);
                float droop = 0.14f + 0.04f * (float) Math.sin(t * 0.7f + f + k) * (0.4f + mid);
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                for (float q = 0; q <= 1; q += cell * 0.8f / len) {
                    float fx = hx + ca * len * q, fy = hy - sa * len * q * 0.55f + droop * q * q;
                    float w = 0.018f * (1 - 0.75f * q) * (0.5f + 0.5f * Math.abs((float) Math.sin(q * 26))) + cell * 0.35f;
                    stamp(fx, fy, w, w, 0.9f, 0, silhouette, frondLit, 2, -1);
                }
            }
        }

        // shadows on the road: the car's, and each palm's trunk and crown, marked row by row
        Arrays.fill(shadow, false);
        for (int r = (int) (CURB * FROWS); r < FROWS; r++) {
            float y = (r + 0.5f) / FROWS;
            float carHalf = 0.40f * (float) Math.sqrt(Math.max(0, 1 - sq((y - CAR_GROUND - 0.004f) / 0.022f)));
            if (carHalf > 0) markShadow(r, CAR_X - 0.02f - carHalf, CAR_X - 0.02f + carHalf, cell);
            for (int k = 0; k < palms; k++) {
                float slope = (bx[k] - SUN_X) * 1.6f, reach = ph[k] * 0.55f;
                float sx = bx[k] + (y - by[k]) * slope;
                if (y - by[k] < reach) markShadow(r, sx - 0.012f, sx + 0.012f, cell);
                float endX = bx[k] + reach * slope, endY = by[k] + reach;
                float half = 0.11f * (float) Math.sqrt(Math.max(0, 1 - sq((y - endY) / 0.035f)));
                if (half > 0) markShadow(r, endX - half, endX + half, cell);
            }
        }

        for (int r = 0; r < FROWS; r++) {
            for (int c = 0; c < FCOLS; c++) {
                float x = (c + 0.5f) * cell, y = (r + 0.5f) / FROWS;

                // 1. the car
                if (carCell(frame, c, r, p, t, pulse, grey)) continue;

                // 2. palms, stamped above
                int bi = r * FCOLS + c;
                if (bufLum[bi] > 0) {
                    fineShade(frame, c, r, bufLum[bi], bufCol[bi]);
                    continue;
                }

                // 3. the world behind: sky, sun, skyline, sea, sand, road
                if (y < HORIZON) {
                    float d = sunDistance(c, r, x, y, sunY);
                    if (d < SUN_R) {
                        // the striped sun: thin fixed bands cut its lower half, a little thicker towards the horizon
                        float rel = (y - (sunY - SUN_R * 0.2f)) / (SUN_R * 1.2f);
                        boolean cutOut = rel > 0 && frac(rel * 5.5f) < 0.10f + rel * 0.28f; // fixed stripes: none ever grows into a bar
                        if (!cutOut) {
                            float k = (y - (sunY - SUN_R)) / (2 * SUN_R);
                            fineShade(frame, c, r, Math.min(1f, 0.62f + 0.3f * sun), Palette.mix(p.both, p.inkB, k));
                            continue;
                        }
                    }
                    // the skyline to the left of the sun: towers with setbacks and antennas, windows, and
                    // sun-facing sides and glass that catch the sunrise with the beat
                    if (x < 0.50f * ASPECT) {
                        float bwid = 0.055f;
                        int block = (int) (x / bwid);
                        float fx = x / bwid - block;
                        float tall = Noise.hash(block, 17);
                        float bh = (0.07f + 0.25f * tall) * (1.05f - 0.55f * x / (0.50f * ASPECT));
                        boolean stepped = Noise.hash(block, 3) < 0.5f;
                        if (stepped && (fx < 0.22f || fx > 0.74f)) bh *= 0.8f;          // a setback
                        boolean gap = fx > 0.93f && Noise.hash(block, 9) < 0.6f;
                        float above = (HORIZON - y) - bh;                              // height over the roof
                        if (!gap && above < 0) {
                            boolean roof = above > -1.2f / FROWS;
                            boolean sunSide = fx > 0.80f || (stepped && fx > 0.66f && fx < 0.74f);
                            boolean pane = (c & 1) == 0 && (r & 1) == 0;
                            if (roof) {
                                fineGlyph(frame, c, r, F_DASH, Palette.mix(cityC, rim, 0.35f + 0.4f * (sun - 0.75f)));
                            } else if (sunSide) {
                                // the side facing the sun: a strip of light that swells on the beat
                                fineShade(frame, c, r, 0.25f + 1.3f * (sun - 0.75f), Palette.mix(cityC, rim, 0.75f));
                            } else if (pane && Noise.hash(c, r) < 0.10f + 0.25f * mid) {
                                fineGlyph(frame, c, r, F_DOT, p.both);                 // a lit window
                            } else if (pane && Noise.hash(r, c) < 0.5f) {
                                // glass picking up the sunrise, brighter on the beat
                                fineGlyph(frame, c, r, F_DOT, Palette.mix(cityC, rim, 0.15f + 1.1f * (sun - 0.75f)));
                            } else {
                                fineShade(frame, c, r, 0.62f, cityC);
                            }
                            continue;
                        }
                        // an antenna on some towers
                        if (!gap && above >= 0 && above < 0.035f && tall > 0.45f && Noise.hash(block, 5) < 0.5f
                                && Math.abs(fx - 0.45f) < 0.5f * cell / bwid) {
                            fineGlyph(frame, c, r, F_PIPE, Palette.mix(cityC, rim, 0.3f));
                            continue;
                        }
                    }
                    float glow = d > SUN_R ? (float) Math.exp(-(d - SUN_R) / (0.11f + 0.07f * bass)) : 0.6f;
                    float lum = 0.34f * glow * sun + 0.12f * (float) Math.pow(y / HORIZON, 3);
                    int col = Palette.mix(Palette.mix(skyTop, skyLow, y / HORIZON), p.inkB, glow * 0.5f);
                    if (lum > 0.10f) fineShade(frame, c, r, lum, col);
                    else if (y < 0.25f && grain(c, r, 9) < 0.01f) fineGlyph(frame, c, r, F_DOT, skyTop);
                } else if (y < SAND) {
                    // the sea: the sun's reflection, broken into shimmering dashes
                    float spread = 0.05f + 0.6f * (y - HORIZON);
                    float refl = (float) Math.exp(-sq((x - SUN_X) / spread)) * (0.45f + 0.35f * sun);
                    float wave = 0.08f + 0.06f * (float) Math.max(0, Math.sin(x * 38 + y * 260 - t * 1.4f));
                    float lum = wave + refl * (grain(c, r, 8) < 0.55f + 0.4f * high ? 1f : 0.3f);
                    if (lum > 0.1f) {
                        fineGlyph(frame, c, r, lum > 0.45f && grain(c, r, 3) < 0.5f ? F_TILDE : F_DASH,
                                Palette.mix(Palette.mix(p.night, seaC, Math.min(1f, lum * 1.6f)), p.both, refl));
                    }
                } else if (y < CURB) {
                    fineShade(frame, c, r, 0.18f + 0.10f * sun + 0.08f * Noise.hash(c, r), sandC);
                } else {
                    // the road: lit by the sun, crossed by the palm shadows and the car's own
                    if (y < CURB + 0.012f) {
                        fineShade(frame, c, r, 0.45f, Palette.mix(p.night, p.dust, 1f));
                        continue;
                    }
                    boolean inShade = shadow[r * FCOLS + c];
                    if (Math.abs(y - 0.845f) < 0.008f && frac((x + t * 0.9f) / 0.32f) < 0.5f) {
                        fineGlyph(frame, c, r, F_DASH, inShade ? Palette.mix(p.night, p.both, 0.4f) : p.both);
                        continue;
                    }
                    // warm, sunlit asphalt, brightest towards the horizon
                    float lum = 0.10f + 0.18f * sun * (1.2f - y) + 0.06f * Noise.hash(r, c);
                    if (inShade) lum *= 0.4f; // shadows dim the road rather than blank it
                    fineShade(frame, c, r, lum, roadC);
                }
            }
        }
    }

    /** Which road cells lie in shadow this frame. */
    private final boolean[] shadow = new boolean[FINE_CELLS];
    private float[] sunDist;

    private void markShadow(int r, float x0, float x1, float cell) {
        int c0 = Math.max(0, (int) Math.ceil(x0 / cell - 0.5f)), c1 = Math.min(FCOLS - 1, (int) Math.floor(x1 / cell - 0.5f));
        for (int c = c0; c <= c1; c++) shadow[r * FCOLS + c] = true;
    }

    /** The distance from each cell to the sun, which never moves: worked out once. */
    private float sunDistance(int c, int r, float x, float y, float sunY) {
        if (sunDist == null || sunDist.length != FCOLS * FROWS) {
            sunDist = new float[FCOLS * FROWS];
            Arrays.fill(sunDist, -1);
        }
        int i = r * FCOLS + c;
        if (sunDist[i] < 0) sunDist[i] = len(x - SUN_X, y - sunY);
        return sunDist[i];
    }

    // ---- the car ---------------------------------------------------------------------------------------

    /** Where the car stands: the middle of its footprint on the road. */
    private static final float CAR_X = 0.37f * ASPECT, CAR_GROUND = 0.94f;
    /** Towards the sun, in the car's space: ahead, low, and on the far side, where it hangs over the sea. */
    private static final float SUN_DX = 0.752f, SUN_DY = 0.221f, SUN_DZ = -0.621f;
    private Car car;

    /** A shade with a dither fixed per cell, so the car's characters only change when its light does. */
    private void carShade(int[] frame, int c, int r, float lum, int color) {
        float level = lum * (FINE_LUM.length - 1) + Noise.hash(c, r) - 0.5f;
        int i = Math.max(0, Math.min(FINE_LUM.length - 1, Math.round(level)));
        if (FINE_LUM[i] >= 0) Glyphs.drawSmall(frame, W, H, FINE_LUM[i], c * FCW, r * FCH, color);
    }

    /**
     * One cell of the car, lit as a solid thing: soft light from the sky on what faces up, and the sun on what
     * faces it. The sun is ahead and low, so its pulse runs over the front, the bonnet and the windscreen and
     * leaves the near side in shade, where the windows only reflect; edges against the sky get a rim of its light.
     * False where the car is not.
     */
    private boolean carCell(int[] frame, int c, int r, Palette p, float t, float pulse, int grey) {
        int cc = c - car.c0, cr = r - car.r0;
        if (cc < 0 || cr < 0 || cc >= car.cols || cr >= car.rows) return false;
        int i = cr * car.cols + cc;
        byte part = car.part[i];
        if (part == Car.NONE) return false;
        int body = Palette.mix(p.inkB, p.dust, 0.45f), chrome = Palette.mix(p.both, p.dust, 0.2f);
        int dark = Palette.mix(p.night, grey, 0.75f), rimLight = Palette.mix(p.inkB, p.both, 0.65f);
        float nx = car.nx[i], ny = car.ny[i], nz = car.nz[i];
        float towards = Math.max(0, nx * SUN_DX + ny * SUN_DY + nz * SUN_DZ);
        float light = 0.50f + 0.24f * Math.max(0, ny) + 0.06f * Math.max(0, nz) + (0.30f + 0.60f * pulse) * towards;
        float albedo;
        int col;
        switch (part) {
            case Car.BODY:
                albedo = car.a[i] > 0 ? 1.3f : 1f;
                col = body;
                break;
            case Car.SILL:
                albedo = 0.5f;
                col = Palette.mix(p.night, body, 0.7f);
                break;
            case Car.LINE:
                Glyphs.drawSmall(frame, W, H, F_PIPE, c * FCW, r * FCH, Palette.mix(p.night, body, 0.45f));
                return true;
            case Car.TRIM:
            case Car.PLATE:
                albedo = 1.3f;
                col = chrome;
                break;
            case Car.LIGHT:
                albedo = 1.6f;
                col = p.both;
                break;
            case Car.AMBER:
                albedo = 1.3f;
                col = Palette.mix(p.inkB, p.both, 0.4f);
                break;
            case Car.GRILLE:
                albedo = (r & 1) == 0 ? 0.75f : 0.25f;     // bars
                col = Palette.mix(dark, chrome, (r & 1) == 0 ? 0.5f : 0f);
                break;
            case Car.GLASS:
                albedo = 0.35f + 0.35f * car.a[i];         // the sky reflects brighter towards the top
                col = Palette.mix(p.inkA, p.both, 0.5f);
                break;
            case Car.FRAME:
                albedo = 0.7f;
                col = dark;
                break;
            case Car.WINDOW: {
                // a side window only reflects: the pale sky up high, a soft streak across it, the dark road below
                float up = car.a[i], along = car.b[i];
                float streak = Math.max(0, 1 - Math.abs(along - 0.35f - up * 0.35f) / 0.10f);
                albedo = 0.22f + 0.38f * up + 0.35f * streak;
                col = Palette.mix(p.inkA, p.both, 0.45f + 0.3f * streak);
                break;
            }
            case Car.TIRE:
                albedo = 0.75f;
                col = Palette.mix(p.night, p.dust, 0.85f);
                break;
            case Car.TREAD:
                albedo = 0.5f;
                col = Palette.mix(p.night, p.dust, 0.7f);
                break;
            case Car.RIM: {
                boolean spoke = car.b[i] < 0.2f || Math.abs(Math.sin((car.a[i] - t * 5) * 2.5)) < 0.4;  // five, turning
                albedo = spoke ? 1.5f : 0.3f;
                col = spoke ? chrome : dark;
                break;
            }
            case Car.ARCH:
                albedo = 0.1f;
                col = dark;
                break;
            default:
                albedo = 1f;
                col = body;
        }
        float lum = Math.min(1f, albedo * light);
        // against the sky the upper edges catch the low sun
        boolean edge = cr == 0 || car.part[i - car.cols] == Car.NONE;
        if (edge && part != Car.TIRE && part != Car.TREAD && part != Car.ARCH) {
            lum = Math.max(lum, 0.62f + 0.3f * pulse);
            col = Palette.mix(col, rimLight, 0.55f);
        }
        carShade(frame, c, r, lum, col);
        return true;
    }
}

package io.github.nottoobright.koipond;

import java.util.Arrays;
import java.util.Random;

/**
 * Draws the pond into a 480x270 ARGB buffer with integer maths: the refracted floor and caustics, koi with their
 * shadows, fry, lily pads with lotus and duckweed, fireflies and the breathing ring. The time-of-day grade is folded
 * into the colours before they are drawn, so there is no separate pass over the frame. The textures and tables are
 * built once in the constructor, and the pad sprites on the first frame, so render() then allocates nothing and
 * needs no Canvas.
 */
final class PondRenderer {

    private static final int W = PondModel.W, H = PondModel.H;
    private static final int TILE = 128, TILE_MASK = TILE - 1;
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
    private static final float[] PROFILE = {0.09f, 0.14f, 0.165f, 0.17f, 0.155f, 0.13f, 0.10f, 0.07f, 0.045f, 0.03f};
    private static final float TWO_PI = (float) (2 * Math.PI);

    private static final int SIN_N = 1024;
    private static final float SIN_SCALE = SIN_N / TWO_PI;
    private static final float[] SIN = new float[SIN_N];

    private static final int WHITE = 0xFFF2EEE4, RED = 0xFFD6301E, BLACK = 0xFF1C1C22;
    private static final int GOLD = 0xFFF0B02E, GOLD_MID = 0xFFF6C44C, GOLD_LINE = 0xFFFFD873;
    private static final int ASAGI = 0xFF7F96AB, ASAGI_NET = 0xFFA9BCCC, ASAGI_HEAD = 0xFFC6D0D6;
    private static final int ASAGI_RED = 0xFFD8602A;
    private static final int CHAGOI = 0xFF9C7B4F, CHAGOI_NET = 0xFF7A5E39;
    /** Fin and tail colour per variety, indexed by PondModel.KOHAKU..SANKE. */
    private static final int[] FIN = {0xFFF8F5EE, 0xFFCFCBC4, 0xFFFFD873, 0xFFE8875A, 0xFFB99C70, 0xFFF8F5EE};
    private static final int FIREFLY = 0xFFD8F27A, FIREFLY_CORE = 0xFFF2FFC4, RING = 0xFFEAF6F0;

    /** Side of the scratch square a koi body is rasterised into; a 40 px koi with its width and sway needs about 63. */
    private static final int MASK = 96;
    /** The breathing ring's radius runs from RING_MIN to RING_MIN + RING_GROW, inside the koi's orbit (58..72 px). */
    static final float RING_MIN = 14f, RING_GROW = 32f;
    private static final int RING_MAX = (int) (RING_MIN + RING_GROW) + 8, RING_SPAN = 2 * RING_MAX + 1;
    /** Body sway weight per spine point, (s / 9)^1.5: nothing at the head, most at the tail. */
    private static final float[] SWAY = new float[PondModel.SPINE];
    /** tan(17.5 degrees): half of the ~35 degree notch in a lily pad. */
    private static final float NOTCH_TAN = 0.3153f;
    private static final int[] GLOW = new int[49];

    /** Floor brightness levels (multiples of 18, the top one capped at 255) and caustic levels after dithering. */
    private static final int LUM_LEVELS = 16, CAUSTIC_LEVELS = 5;
    private static final int[] CAUSTIC_VALUE = {0, 44, 88, 132, 176};
    /** The ring's edge glow, a256 per 1/16 px of distance from the ring line, up to 5 px. */
    private static final int[] RING_EDGE = new int[80];

    /** Pad sprite classes; a sprite byte is class | (veins << 3). */
    private static final int PAD_OUTLINE = 1, PAD_RIM_LIGHT = 2, PAD_RIM_DARK = 3, PAD_DARK = 4, PAD_MID = 5,
            PAD_LIGHT = 6;
    /** Lotus sprite classes in the low 3 bits; bit 3 marks the lotus's drop shadow. */
    private static final int LOTUS_CORE = 1, LOTUS_INNER = 2, LOTUS_INNER_EDGE = 3, LOTUS_OUTER = 4,
            LOTUS_OUTER_EDGE = 5, LOTUS_SHADOW = 8;

    static {
        for (int i = 0; i < SIN_N; i++) SIN[i] = (float) Math.sin(i * 2 * Math.PI / SIN_N);
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                float f = Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy) / 3.8f);
                GLOW[(dy + 3) * 7 + dx + 3] = (int) (f * f * 256f);
            }
        }
        for (int e16 = 0; e16 < RING_EDGE.length; e16++) {
            float e = e16 / 16f, a = 0f;
            if (e < 1.7f) { float c = 1f - e / 1.7f; a += 0.42f * c * c * (3f - 2f * c); }
            if (e < 5f) { float s = 1f - e / 5f; a += 0.13f * s * s; }
            RING_EDGE[e16] = (int) (a * 256f);
        }
        for (int s = 0; s < SWAY.length; s++) SWAY[s] = (float) Math.pow(s / (double) (SWAY.length - 1), 1.5);
    }

    private final Ripples ripples = new Ripples(W / 2, H / 2);
    /** Floor brightness level 0..15 per pond pixel, already Bayer-quantised. */
    private final byte[] floorLum = new byte[W * H];
    /** Tileable net of light, 0..255. */
    private final byte[] caustic = new byte[TILE * TILE];
    /** Caustic product (0..255) to a dithered level 0..4, per Bayer cell: index (bayer << 8) | product. */
    private final byte[] causticQ = new byte[16 * 256];
    /** Precomputed distance from the pond centre in 1/16 px, for the breathing ring. */
    private final short[] ringDist = new short[RING_SPAN * RING_SPAN];

    /** Ungraded floor colour per level, and the graded floor plus caustic colour per (level, caustic level). */
    private final int[] floorCol = new int[LUM_LEVELS];
    private final int[] floorLut = new int[LUM_LEVELS * CAUSTIC_LEVELS];
    private final int[] tintR = new int[CAUSTIC_LEVELS], tintG = new int[CAUSTIC_LEVELS], tintB = new int[CAUSTIC_LEVELS];
    private final int[] rowDx = new int[W / 2], rowDy = new int[W / 2], rowHl = new int[W / 2];
    /** Whether the row arrays hold anything but zeros. */
    private boolean rowDirty = true;

    // The time-of-day grade (and the rain dimming): per channel multipliers out of 256, applied to every colour
    // before it is drawn. Scaling, blending and shadows are linear, so this matches grading the finished frame.
    private int gradeR = 256, gradeG = 256, gradeB = 256;
    private boolean graded;

    // Koi body scratch: per pixel the best normalised squared distance q (< 1 inside), the spine parameter t,
    // the lateral offset normalised to the half width, and the lateral offset in pixels.
    private final float[] mq = new float[MASK * MASK];
    private final float[] mt = new float[MASK * MASK];
    private final float[] ml = new float[MASK * MASK];
    private final float[] mp = new float[MASK * MASK];
    private int mox, moy, mw, mh;
    private int[] order = new int[32];
    /** The current koi's spine with its swimming sway, read by buildMask and drawKoi instead of the model's. */
    private final float[] swayX = new float[PondModel.SPINE], swayY = new float[PondModel.SPINE];

    // Pad and lotus sprites, rasterised once for the model's pads in pad-local pixels (only the position moves),
    // and their graded colours, rebuilt every frame from the palette.
    private PondModel.Pad[] spritePads;
    private byte[][] padSprite;
    private int[] padExt;
    private byte[][] lotusSprite;
    private int[] lotusExt, lotusDx, lotusDy;
    private final int[] padBase = new int[7];
    private final int[] padColour = new int[32];
    private final int[] lotusColour = new int[8];

    PondRenderer(long seed) {
        Random rnd = new Random(seed);
        buildFloor(rnd);
        buildCaustics(rnd);
        for (int b = 0; b < 16; b++) {
            for (int v = 0; v < 256; v++) {
                float c = Math.max(0f, Math.min(255f, (v - 18) * 1.7f));
                int lv = Math.min(4, (int) ((c * 4f + (BAYER[b] + 0.5f) * 255f / 16f) / 255f));
                causticQ[(b << 8) | v] = (byte) lv;
            }
        }
        for (int dy = -RING_MAX; dy <= RING_MAX; dy++) {
            for (int dx = -RING_MAX; dx <= RING_MAX; dx++) {
                ringDist[(dy + RING_MAX) * RING_SPAN + dx + RING_MAX] = (short) Math.round(Math.sqrt(dx * dx + dy * dy) * 16);
            }
        }
    }

    Ripples ripples() {
        return ripples;
    }

    void render(PondModel m, Palette p, float fireflies, float breathSize, long nowMs, int[] out) {
        render(m, p, fireflies, breathSize, 1f, nowMs, out);
    }

    /** ringAlpha (0..1) fades the breathing ring in and out; breathSize -1 leaves it out. */
    void render(PondModel m, Palette p, float fireflies, float breathSize, float ringAlpha, long nowMs, int[] out) {
        setGrade(p.ambient, m.rainLevel());
        PondModel.Pad[] pads = m.pads;
        if (spritePads != pads) buildSprites(pads);
        padColours(p);
        floorPass(p, nowMs, out);
        PondModel.Koi[] koi = m.koi;
        // Deepest first; each koi's shadow just before it, so a shallower koi shades the deeper ones below it.
        sortByDepth(koi);
        for (int n = 0; n < koi.length; n++) drawKoi(koi[order[n]], order[n], out);
        drawFry(m.fry, out);
        for (int i = 0; i < pads.length; i++) drawPadShadow(pads[i], i, out);
        drawDuckweed(m.duckweed, p.duckweed, out);
        for (int i = 0; i < pads.length; i++) drawPad(pads[i], i, out);
        if (fireflies > 0f) drawFireflies(m.fireflies, Math.min(1f, fireflies), nowMs, out);
        if (breathSize >= 0f && ringAlpha > 0f) drawRing(Math.min(1f, breathSize), Math.min(1f, ringAlpha), out);
    }

    // ---- Grade ----

    private void setGrade(int amb, float rain) {
        int dim = 256 - (int) (51f * clamp01(rain));
        int ar = (amb >> 16) & 255, ag = (amb >> 8) & 255, ab = amb & 255;
        gradeR = ((ar + (ar >> 7)) * dim) >> 8;
        gradeG = ((ag + (ag >> 7)) * dim) >> 8;
        gradeB = ((ab + (ab >> 7)) * dim) >> 8;
        graded = gradeR != 256 || gradeG != 256 || gradeB != 256;
    }

    /** The colour under the current grade, opaque. */
    private int grade(int c) {
        if (!graded) return c | 0xFF000000;
        return 0xFF000000 | ((((c >> 16) & 255) * gradeR >> 8) << 16) | ((((c >> 8) & 255) * gradeG >> 8) << 8)
                | ((c & 255) * gradeB >> 8);
    }

    // ---- 1. Floor, caustics and refraction (the hot path) ----

    private void floorPass(Palette p, long nowMs, int[] out) {
        int dr = (p.deep >> 16) & 255, dg = (p.deep >> 8) & 255, db = p.deep & 255;
        int fr = (p.floor >> 16) & 255, fg = (p.floor >> 8) & 255, fb = p.floor & 255;
        final int cr = (p.caustic >> 16) & 255, cg = (p.caustic >> 8) & 255, cb = p.caustic & 255;
        for (int q = 0; q < CAUSTIC_LEVELS; q++) {
            int cq = CAUSTIC_VALUE[q];
            tintR[q] = (cr * cq) >> 8;
            tintG[q] = (cg * cq) >> 8;
            tintB[q] = (cb * cq) >> 8;
        }
        for (int l = 0; l < LUM_LEVELS; l++) {
            int i = Math.min(255, l * 18);
            int a = i + (i >> 7);
            int r0 = dr + (((fr - dr) * a) >> 8), g0 = dg + (((fg - dg) * a) >> 8), b0 = db + (((fb - db) * a) >> 8);
            floorCol[l] = (r0 << 16) | (g0 << 8) | b0;
            for (int q = 0; q < CAUSTIC_LEVELS; q++) {
                int r = Math.min(255, r0 + tintR[q]), g = Math.min(255, g0 + tintG[q]), b = Math.min(255, b0 + tintB[q]);
                floorLut[l * CAUSTIC_LEVELS + q] = grade((r << 16) | (g << 8) | b);
            }
        }
        // Layer A scrolls in whole pixels, layer B is 0.75 scale in 8.8 fixed point; both wrap on the tile.
        final int offAx = (int) Math.floorMod(nowMs * 7 / 1000, (long) TILE);
        final int offAy = (int) Math.floorMod(nowMs * 3 / 1000, (long) TILE);
        final int offBx = (int) Math.floorMod(-nowMs * 5 * 256 / 1000, TILE * 256L);
        final int offBy = (int) Math.floorMod(nowMs * 4 * 256 / 1000, TILE * 256L);
        final int[] hts = ripples.heights();
        final int rw = ripples.width(), rh = ripples.height();
        final byte[] lumT = floorLum, causT = caustic, dq = causticQ;
        final int[] fc = floorCol, lut = floorLut, rdx = rowDx, rdy = rowDy, rhl = rowHl;
        final int[] tr = tintR, tg = tintG, tb = tintB;
        final int mr = gradeR, mg = gradeG, mb = gradeB;
        boolean dirty = false;

        for (int y = 0; y < H; y++) {
            if ((y & 1) == 0) dirty = gradientRow(y >> 1, hts, rw, rh);
            final int bRow = (y & 3) << 2;
            final int rowA = ((y + offAy) & TILE_MASK) << 7;
            final int rowB = (((y * 192 + offBy) >> 8) & TILE_MASK) << 7;
            int o = y * W;
            if (!dirty) {
                // No ripple on this row: no refraction, so the floor and caustic levels index the graded table.
                for (int x = 0; x < W; x++, o++) {
                    final int a = causT[rowA | ((x + offAx) & TILE_MASK)] & 255;
                    final int b = causT[rowB | (((x * 192 + offBx) >> 8) & TILE_MASK)] & 255;
                    out[o] = lut[lumT[o] * CAUSTIC_LEVELS + dq[((bRow | (x & 3)) << 8) | ((a * b) >> 8)]];
                }
                continue;
            }
            for (int x = 0; x < W; x++, o++) {
                final int c = x >> 1;
                final int ddx = rdx[c], ddy = rdy[c], hl = rhl[c];
                if ((ddx | ddy | hl) == 0) {
                    final int a = causT[rowA | ((x + offAx) & TILE_MASK)] & 255;
                    final int b = causT[rowB | (((x * 192 + offBx) >> 8) & TILE_MASK)] & 255;
                    out[o] = lut[lumT[o] * CAUSTIC_LEVELS + dq[((bRow | (x & 3)) << 8) | ((a * b) >> 8)]];
                    continue;
                }
                int sx = x + ddx, sy = y + ddy;
                if (sx < 0) sx = 0; else if (sx >= W) sx = W - 1;
                if (sy < 0) sy = 0; else if (sy >= H) sy = H - 1;
                final int a = causT[(((sy + offAy) & TILE_MASK) << 7) | ((sx + offAx) & TILE_MASK)] & 255;
                final int b = causT[((((sy * 192 + offBy) >> 8) & TILE_MASK) << 7)
                        | (((sx * 192 + offBx) >> 8) & TILE_MASK)] & 255;
                final int cq = dq[((bRow | (x & 3)) << 8) | ((a * b) >> 8)];
                final int col = fc[lumT[sy * W + sx]];
                int r = ((col >> 16) & 255) + tr[cq] + hl;
                int g = ((col >> 8) & 255) + tg[cq] + hl;
                int bl = (col & 255) + tb[cq] + hl;
                if (r < 0) r = 0; else if (r > 255) r = 255;
                if (g < 0) g = 0; else if (g > 255) g = 255;
                if (bl < 0) bl = 0; else if (bl > 255) bl = 255;
                out[o] = 0xFF000000 | ((r * mr >> 8) << 16) | ((g * mg >> 8) << 8) | (bl * mb >> 8);
            }
        }
    }

    /**
     * Refraction offsets and highlight for one row of ripple cells; shared by the two pixel rows of the cell.
     * Returns whether any cell of the row has a gradient.
     */
    private boolean gradientRow(int cy, int[] h, int rw, int rh) {
        if (rowDirty) {
            Arrays.fill(rowDx, 0);
            Arrays.fill(rowDy, 0);
            Arrays.fill(rowHl, 0);
            rowDirty = false;
        }
        if (cy <= 0 || cy >= rh - 1) return false;
        int n = Math.min(rw - 1, W / 2);
        int base = cy * rw;
        boolean any = false;
        for (int cx = 1; cx < n; cx++) {
            int i = base + cx;
            int gx = h[i + 1] - h[i - 1], gy = h[i + rw] - h[i - rw];
            if ((gx | gy) == 0) continue;
            any = true;
            rowDx[cx] = clamp(gx >> 2, -8, 8);
            rowDy[cx] = clamp(gy >> 2, -8, 8);
            rowHl[cx] = clamp((gx + gy) >> 1, -56, 56);
        }
        rowDirty = any;
        return any;
    }

    // ---- 2 and 3. Koi ----

    /**
     * The spine with a wave travelling from head to tail along the local normal, so the whole body bends as it
     * swims and not only in turns. Written to swayX, swayY; the model's spine is left alone.
     */
    private void sway(PondModel.Koi k) {
        float[] sx = k.spineX, sy = k.spineY;
        int n = Math.min(sx.length, PROFILE.length);
        float amp = k.length * 0.045f;
        for (int s = 0; s < n; s++) {
            int a = s == 0 ? 0 : s - 1, b = s == n - 1 ? n - 1 : s + 1;
            float tx = sx[b] - sx[a], ty = sy[b] - sy[a];
            float d = (float) Math.sqrt(tx * tx + ty * ty);
            float off = d > 1e-4f ? amp * SWAY[s] * sinF(k.wiggle - s * 0.7f) / d : 0f;
            swayX[s] = sx[s] - ty * off;
            swayY[s] = sy[s] + tx * off;
        }
    }

    /**
     * Rasterises the body (discs stamped every ~1 px along the swayed spine) into the scratch square. Returns false
     * when the koi, with margin extra pixels around it, is entirely off screen.
     */
    private boolean buildMask(PondModel.Koi k, int margin) {
        float[] sx = swayX, sy = swayY;
        int n = Math.min(sx.length, PROFILE.length);
        float len = k.length;
        float minX = sx[0], maxX = sx[0], minY = sy[0], maxY = sy[0];
        for (int i = 1; i < n; i++) {
            minX = Math.min(minX, sx[i]); maxX = Math.max(maxX, sx[i]);
            minY = Math.min(minY, sy[i]); maxY = Math.max(maxY, sy[i]);
        }
        float pad = len * 0.17f + 2f;
        mox = (int) Math.floor(minX - pad);
        moy = (int) Math.floor(minY - pad);
        mw = Math.min(MASK, (int) Math.ceil(maxX + pad) - mox + 1);
        mh = Math.min(MASK, (int) Math.ceil(maxY + pad) - moy + 1);
        if (mox >= W + margin || moy >= H + margin || mox + mw <= -margin || moy + mh <= -margin) return false;
        for (int j = 0; j < mh; j++) Arrays.fill(mq, j * MASK, j * MASK + mw, 2f);

        float tx = 1f, ty = 0f;
        for (int i = 0; i < n - 1; i++) {
            float x0 = sx[i], y0 = sy[i], dx = sx[i + 1] - x0, dy = sy[i + 1] - y0;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 1e-4f) { tx = dx / d; ty = dy / d; }
            float w0 = len * PROFILE[i], w1 = len * PROFILE[i + 1];
            int steps = Math.max(1, (int) Math.ceil(d));
            int end = i == n - 2 ? steps : steps - 1;
            for (int s = 0; s <= end; s++) {
                float f = s / (float) steps;
                stamp(x0 + dx * f, y0 + dy * f, Math.max(0.6f, w0 + (w1 - w0) * f), tx, ty, i + f);
            }
        }
        return true;
    }

    private void stamp(float cx, float cy, float r, float tx, float ty, float t) {
        float r2 = r * r, inv = 1f / r2, invR = 1f / r;
        int i0 = Math.max(0, (int) Math.floor(cx - r) - mox), i1 = Math.min(mw - 1, (int) Math.ceil(cx + r) - mox);
        int j0 = Math.max(0, (int) Math.floor(cy - r) - moy), j1 = Math.min(mh - 1, (int) Math.ceil(cy + r) - moy);
        for (int j = j0; j <= j1; j++) {
            float ddy = moy + j - cy;
            int row = j * MASK;
            for (int i = i0; i <= i1; i++) {
                float ddx = mox + i - cx;
                float d2 = ddx * ddx + ddy * ddy;
                if (d2 > r2) continue;
                float q = d2 * inv;
                int idx = row + i;
                if (q >= mq[idx]) continue;
                float lat = tx * ddy - ty * ddx;
                mq[idx] = q;
                mt[idx] = t;
                mp[idx] = lat;
                ml[idx] = lat * invR;
            }
        }
    }

    private void drawShadow(PondModel.Koi k, int[] out) {
        // The shadow falls on the floor: furthest from a koi at the surface, almost under one near the floor,
        // and a little darker and sharper there.
        float lift = 1f - clamp01(k.depth);
        int ox = Math.round(2f + 6f * lift), oy = Math.round(3f + 9f * lift);
        int rim = 200 + (int) (20f * lift), core = 165 + (int) (25f * lift);
        for (int j = 0; j < mh; j++) {
            int y = moy + j + oy;
            if (y < 0 || y >= H) continue;
            int row = j * MASK;
            for (int i = 0; i < mw; i++) {
                float q = mq[row + i];
                if (q >= 1f) continue;
                int x = mox + i + ox;
                if (x < 0 || x >= W) continue;
                int o = y * W + x;
                out[o] = scale(out[o], q > 0.62f ? rim : core);
            }
        }
    }

    private void sortByDepth(PondModel.Koi[] koi) {
        int n = koi.length;
        if (order.length < n) order = new int[n];
        for (int i = 0; i < n; i++) {
            int v = i, j = i - 1;
            while (j >= 0 && koi[order[j]].depth < koi[v].depth) { order[j + 1] = order[j]; j--; }
            order[j + 1] = v;
        }
    }

    /** The koi's shadow, then its fins and tail, then its body, all from one rasterised mask. */
    private void drawKoi(PondModel.Koi k, int idx, int[] out) {
        sway(k);
        boolean near = buildMask(k, 14);
        if (near) drawShadow(k, out);

        int alpha = 256 - (int) (0.45f * clamp01(k.depth) * 256f);
        float len = k.length;
        float[] sx = swayX, sy = swayY;
        int last = Math.min(sx.length, PROFILE.length) - 1;
        int variety = k.variety;
        int finCol = grade(FIN[variety >= 0 && variety < FIN.length ? variety : 0]);

        // Tail: two lobes beyond the last spine point, swaying with the wiggle.
        float tx = sx[last] - sx[last - 1], ty = sy[last] - sy[last - 1];
        float td = (float) Math.sqrt(tx * tx + ty * ty);
        if (td > 1e-4f) { tx /= td; ty /= td; } else { tx = -cosF(k.heading); ty = -sinF(k.heading); }
        float sway = 0.38f * sinF(k.wiggle);
        float cs = cosF(sway), sn = sinF(sway);
        float dx = tx * cs - ty * sn, dy = tx * sn + ty * cs;
        final float c45 = 0.9004f, s45 = 0.4350f;
        drawLobes(sx[last] - tx * 0.8f, sy[last] - ty * 0.8f,
                dx * c45 - dy * s45, dx * s45 + dy * c45, dx * c45 + dy * s45, -dx * s45 + dy * c45, 2,
                len * 0.30f, len * 0.13f, finCol, alpha, out);

        // Pectoral fins: lobes from the sides at spine[2], swept back and flapping.
        float fx = sx[3] - sx[1], fy = sy[3] - sy[1];
        float fd = (float) Math.sqrt(fx * fx + fy * fy);
        if (fd > 1e-4f) { fx /= fd; fy /= fd; } else { fx = tx; fy = ty; }
        float nx = -fy, ny = fx;
        float sweep = 0.75f + 0.35f * sinF(k.wiggle + 1.3f);
        float cw = cosF(sweep), sw = sinF(sweep);
        float w2 = len * PROFILE[2] * 0.55f;
        for (int s = -1; s <= 1; s += 2) {
            float ax = nx * s * cw + fx * sw, ay = ny * s * cw + fy * sw;
            drawLobes(sx[2] + nx * s * w2, sy[2] + ny * s * w2, ax, ay, 0f, 0f, 1,
                    len * 0.22f, len * 0.10f, finCol, alpha, out);
        }

        if (!near || mox >= W || moy >= H || mox + mw <= 0 || moy + mh <= 0) return;
        long pat = k.pattern;
        float spacing = len / last;
        float dorsalEnd = last - 1.2f;
        for (int j = 0; j < mh; j++) {
            int y = moy + j;
            if (y < 0 || y >= H) continue;
            int row = j * MASK;
            for (int i = 0; i < mw; i++) {
                int m = row + i;
                if (mq[m] >= 1f) continue;
                int x = mox + i;
                if (x < 0 || x >= W) continue;
                float t = mt[m], l = ml[m], lp = mp[m];
                int col = koiColour(variety, pat, t, l, lp, len, spacing, idx);
                boolean edge = outside(i - 1, j) || outside(i + 1, j) || outside(i, j - 1) || outside(i, j + 1);
                if (edge) {
                    col = scale(col, 141);
                } else {
                    col = scale(col, 256 - (int) (44f * l * l));
                    if (lp > -0.6f && lp < 0.6f && t > 0.6f && t < dorsalEnd) col = lighten(col, 56);
                }
                int o = y * W + x;
                out[o] = blend(out[o], grade(col), alpha);
            }
        }
    }

    private boolean outside(int i, int j) {
        return i < 0 || j < 0 || i >= mw || j >= mh || mq[j * MASK + i] >= 1f;
    }

    /**
     * One or two translucent elliptic lobes from a base point along unit directions (a) and (b), lighter at the
     * rim so fins keep a shape over a busy floor. col is already graded.
     */
    private static void drawLobes(float bx, float by, float ax, float ay, float bxDir, float byDir, int lobes,
                                  float len, float wid, int col, int alpha, int[] out) {
        float hl = Math.max(0.8f, len * 0.5f), hw = Math.max(0.6f, wid * 0.5f);
        // Scan only the lobes' own bounding boxes: centre base + dir * hl, half extents from both semi-axes.
        float ex = hl * Math.abs(ax) + hw * Math.abs(ay), ey = hl * Math.abs(ay) + hw * Math.abs(ax);
        float cxA = bx + ax * hl, cyA = by + ay * hl;
        float minX = cxA - ex, maxX = cxA + ex, minY = cyA - ey, maxY = cyA + ey;
        if (lobes == 2) {
            float ex2 = hl * Math.abs(bxDir) + hw * Math.abs(byDir), ey2 = hl * Math.abs(byDir) + hw * Math.abs(bxDir);
            float cxB = bx + bxDir * hl, cyB = by + byDir * hl;
            minX = Math.min(minX, cxB - ex2); maxX = Math.max(maxX, cxB + ex2);
            minY = Math.min(minY, cyB - ey2); maxY = Math.max(maxY, cyB + ey2);
        }
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(H - 1, (int) Math.ceil(maxY));
        float ihl2 = 1f / (hl * hl), ihw2 = 1f / (hw * hw);
        int aIn = (alpha * 140) >> 8, aEdge = (alpha * 175) >> 8;
        int edgeCol = scale(col, 220);
        for (int y = y0; y <= y1; y++) {
            float py = y - by;
            int o = y * W + x0;
            for (int x = x0; x <= x1; x++, o++) {
                float px = x - bx;
                float u = px * ax + py * ay - hl, v = py * ax - px * ay;
                float e = u * u * ihl2 + v * v * ihw2;
                if (lobes == 2) {
                    float u2 = px * bxDir + py * byDir - hl, v2 = py * bxDir - px * byDir;
                    e = Math.min(e, u2 * u2 * ihl2 + v2 * v2 * ihw2);
                }
                if (e > 1f) continue;
                out[o] = e > 0.6f ? blend(out[o], edgeCol, aEdge) : blend(out[o], col, aIn);
            }
        }
    }

    /**
     * Body colour at spine parameter t (0 head .. 9 tail end), lateral offset l (-1..1 of the half width) and lp
     * (pixels). Pattern bits: 0-9 centre band per segment, 10-19 left, 20-29 right, 30-39 black spots.
     */
    private static int koiColour(int variety, long pat, float t, float l, float lp, float len, float spacing,
                                 int idx) {
        switch (variety) {
            case PondModel.OGON:
                if (lp > -0.9f && lp < 0.9f && t > 0.4f) return GOLD_LINE;
                return l > -0.45f && l < 0.45f ? GOLD_MID : GOLD;
            case PondModel.ASAGI:
                if (t < 1.1f) return ASAGI_HEAD;
                if (l > 0.64f || l < -0.64f) return ASAGI_RED;
                return scaleNet(t * spacing, lp) ? ASAGI_NET : ASAGI;
            case PondModel.CHAGOI:
                return scaleNet(t * spacing, lp) ? CHAGOI_NET : CHAGOI;
            default:
                break;
        }
        // Wobble the band edges in fish-local coordinates so patches read as blobs, not blocks.
        float segScale = PondModel.SPINE / (float) (PondModel.SPINE - 1);
        int seg = (int) ((t + 0.35f * sinF(l * 2.4f + idx * 1.9f)) * segScale);
        if (seg < 0) seg = 0; else if (seg > 9) seg = 9;
        float th = 0.34f + 0.1f * sinF(t * 1.7f + idx);
        int band = l < -th ? 10 : (l > th ? 20 : 0);
        boolean patch = ((pat >>> (band + seg)) & 1L) != 0;
        switch (variety) {
            case PondModel.SHOWA:
                // Black markings wrap across the whole width (bit 30 is the black head); red patches on white
                // show between them.
                if (((pat >>> (30 + seg)) & 1L) != 0) return BLACK;
                return patch ? RED : WHITE;
            case PondModel.SANKE:
                if (spot(pat, t, l, len, spacing, idx)) return BLACK;
                return patch ? RED : WHITE;
            default:
                return patch ? RED : WHITE;
        }
    }

    private static boolean spot(long pat, float t, float l, float len, float spacing, int idx) {
        int seg = (int) (t * PondModel.SPINE / (PondModel.SPINE - 1));
        float rs = len * 0.055f + 0.6f, rs2 = rs * rs, halfW = len * 0.13f;
        for (int s = seg - 1; s <= seg + 1; s++) {
            if (s < 0 || s > 9 || ((pat >>> (30 + s)) & 1L) == 0) continue;
            float ts = (s + 0.5f) * 0.9f;
            float ls = ((s + idx) & 1) == 0 ? 0.4f : -0.45f;
            float da = (t - ts) * spacing, dc = (l - ls) * halfW;
            if (da * da + dc * dc < rs2) return true;
        }
        return false;
    }

    /** A diamond scale net about 3 px across, in fish-local pixels. */
    private static boolean scaleNet(float along, float across) {
        float a = (along + across) * 0.32f + 16f, b = (along - across) * 0.32f + 16f;
        return a - (int) a < 0.28f || b - (int) b < 0.28f;
    }

    // ---- 4. Fry ----

    private void drawFry(PondModel.Fry[] fry, int[] out) {
        for (int i = 0; i < fry.length; i++) {
            PondModel.Fry f = fry[i];
            int col = grade(f.color), tail = scale(col, 150), shade = scale(col, 60);
            int n = 4 + (i & 1);
            float c = cosF(f.heading), s = sinF(f.heading);
            // A soft shadow under the fish, then a body two pixels wide behind the head, tapering to a darker tail.
            for (int k = 0; k < n; k++) {
                int x = ri(f.x - c * k) + 1, y = ri(f.y - s * k) + 2;
                if (x < 0 || y < 0 || x >= W || y >= H) continue;
                int o = y * W + x;
                out[o] = blend(out[o], shade, 70);
            }
            for (int k = 0; k < n; k++) {
                float fx = f.x - c * k, fy = f.y - s * k;
                int colour = k == n - 1 ? tail : col;
                plotFry(out, ri(fx), ri(fy), colour);
                if (k >= 1 && k <= 2) plotFry(out, ri(fx - s * 0.9f), ri(fy + c * 0.9f), colour);
            }
        }
    }

    private static void plotFry(int[] out, int x, int y, int colour) {
        if (x < 0 || y < 0 || x >= W || y >= H) return;
        int o = y * W + x;
        out[o] = blend(out[o], colour, 235);
    }

    // ---- 5. Lily pads, lotus, duckweed ----

    private static boolean padInside(int dx, int dy, float r2, float nx, float ny) {
        if (dx * dx + dy * dy > r2) return false;
        float dot = dx * nx + dy * ny;
        if (dot <= 0f) return true;
        float cr = dx * ny - dy * nx;
        return Math.abs(cr) >= dot * NOTCH_TAN;
    }

    /** Rasterises every pad, and its lotus, into a sprite of colour classes. Only the pads' positions move. */
    private void buildSprites(PondModel.Pad[] pads) {
        int n = pads.length;
        padSprite = new byte[n][];
        padExt = new int[n];
        lotusSprite = new byte[n][];
        lotusExt = new int[n];
        lotusDx = new int[n];
        lotusDy = new int[n];
        for (int i = 0; i < n; i++) {
            PondModel.Pad pad = pads[i];
            float r = Math.max(2f, pad.r);
            float nx = cosF(pad.notch), ny = sinF(pad.notch);
            padExt[i] = (int) Math.ceil(r) + 1;
            padSprite[i] = padSprite(r, nx, ny, padExt[i], 5 + i % 3, pad.notch);
            if (pad.lotus) {
                float big = Math.max(3f, r * 0.45f);
                lotusExt[i] = (int) Math.ceil(big) + 1;
                lotusDx[i] = ri(-nx * r * 0.3f);
                lotusDy[i] = ri(-ny * r * 0.3f);
                lotusSprite[i] = lotusSprite(big, pad.phase, 8 + i % 3, lotusExt[i]);
            }
        }
        spritePads = pads;
    }

    private static byte[] padSprite(float r, float nx, float ny, int ext, int veins, float notch) {
        int side = 2 * ext + 1;
        byte[] map = new byte[side * side];
        float r2 = r * r, invR = 1f / r;
        for (int dy = -ext; dy <= ext; dy++) {
            for (int dx = -ext; dx <= ext; dx++) {
                if (!padInside(dx, dy, r2, nx, ny)) continue;
                int cls;
                if (!padInside(dx - 1, dy, r2, nx, ny) || !padInside(dx + 1, dy, r2, nx, ny)
                        || !padInside(dx, dy - 1, r2, nx, ny) || !padInside(dx, dy + 1, r2, nx, ny)) {
                    cls = PAD_OUTLINE;
                } else {
                    float d = (float) Math.sqrt(dx * dx + dy * dy) * invR;
                    float lf = -(dx + dy) * 0.7071f * invR;
                    if (d > 0.74f && lf > 0.2f) {
                        cls = PAD_RIM_LIGHT;
                    } else if (d > 0.78f && lf < -0.25f) {
                        cls = PAD_RIM_DARK;
                    } else {
                        int v = (int) ((lf * 0.8f + 1f) * 127.5f);
                        // Bayer in pad-local coordinates, so the dither moves with the pad instead of crawling.
                        int lv = (v * 2 + BAYER[((dy & 3) << 2) | (dx & 3)] * 16) >> 8;
                        cls = lv <= 0 ? PAD_DARK : (lv == 1 ? PAD_MID : PAD_LIGHT);
                    }
                }
                map[(dy + ext) * side + dx + ext] = (byte) cls;
            }
        }
        // Veins from the notch apex, spaced so none runs into the notch; each pass over a pixel blends once more.
        float inner = r - 1.8f, innerR2 = (r - 1.2f) * (r - 1.2f);
        for (int v = 0; v < veins; v++) {
            float ang = notch + (v + 0.5f) * TWO_PI / veins;
            float vx = cosF(ang), vy = sinF(ang);
            int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
            for (float s = 1.2f; s < inner; s += 0.7f) {
                int dx = ri(vx * s), dy = ri(vy * s);
                if (dx == lastX && dy == lastY) continue;
                lastX = dx;
                lastY = dy;
                if (!padInside(dx, dy, innerR2, nx, ny)) continue;
                addVein(map, (dy + ext) * side + dx + ext);
            }
        }
        addVein(map, ext * side + ext);
        return map;
    }

    private static void addVein(byte[] map, int i) {
        int v = map[i];
        if ((v & 7) != 0 && (v >> 3) < 3) map[i] = (byte) (v + 8);
    }

    private static byte[] lotusSprite(float big, float rot, int petals, int ext) {
        // The shadow sits one pixel down-right, so the sprite is one pixel wider and taller than the flower.
        int side = 2 * ext + 2;
        byte[] map = new byte[side * side];
        float halfN = petals * 0.5f, shift = (float) Math.PI / petals;
        for (int dy = -ext; dy <= ext; dy++) {
            for (int dx = -ext; dx <= ext; dx++) {
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > big + 0.5f) continue;
                float ang = (float) Math.atan2(dy, dx) + rot;
                float ro = big * (0.42f + 0.58f * (float) Math.sqrt(Math.abs(cosF(ang * halfN))));
                if (d > ro) continue;
                int cls;
                if (d < big * 0.24f) {
                    cls = LOTUS_CORE;
                } else {
                    float rin = big * 0.66f * (0.45f + 0.55f * (float) Math.sqrt(Math.abs(cosF((ang + shift) * halfN))));
                    if (d <= rin) cls = d > rin - 0.8f ? LOTUS_INNER_EDGE : LOTUS_INNER;
                    else cls = d > ro - 0.9f ? LOTUS_OUTER_EDGE : LOTUS_OUTER;
                }
                int i = (dy + ext) * side + dx + ext;
                map[i] = (byte) (map[i] | cls);
                map[i + side + 1] = (byte) (map[i + side + 1] | LOTUS_SHADOW);
            }
        }
        return map;
    }

    /** The graded pad and lotus colours for this frame's palette. */
    private void padColours(Palette p) {
        int dark = p.padDark, light = p.padLight;
        int[] base = padBase;
        base[PAD_OUTLINE] = scale(dark, 158);
        base[PAD_RIM_LIGHT] = lighten(light, 40);
        base[PAD_RIM_DARK] = scale(dark, 215);
        base[PAD_DARK] = dark;
        base[PAD_MID] = blend(dark, light, 128);
        base[PAD_LIGHT] = light;
        for (int cls = 1; cls < base.length; cls++) {
            int c = base[cls];
            for (int veins = 0; veins < 4; veins++) {
                padColour[cls | (veins << 3)] = grade(c);
                c = blend(c, p.padVein, 190);
            }
        }
        int outer = p.lotus;
        lotusColour[LOTUS_CORE] = grade(p.lotusCore);
        lotusColour[LOTUS_INNER] = grade(lighten(outer, 110));
        lotusColour[LOTUS_INNER_EDGE] = grade(lighten(outer, 70));
        lotusColour[LOTUS_OUTER] = grade(outer);
        lotusColour[LOTUS_OUTER_EDGE] = grade(scale(outer, 196));
    }

    private void drawPadShadow(PondModel.Pad pad, int i, int[] out) {
        int ext = padExt[i], side = 2 * ext + 1;
        byte[] map = padSprite[i];
        int ox = ri(pad.x) - ext + 3, oy = ri(pad.y) - ext + 4;
        int i0 = Math.max(0, -ox), i1 = Math.min(side, W - ox);
        int j0 = Math.max(0, -oy), j1 = Math.min(side, H - oy);
        for (int j = j0; j < j1; j++) {
            int m = j * side, o = (oy + j) * W + ox;
            for (int k = i0; k < i1; k++) {
                if (map[m + k] != 0) out[o + k] = scale(out[o + k], 166);
            }
        }
    }

    private void drawPad(PondModel.Pad pad, int i, int[] out) {
        int ext = padExt[i], side = 2 * ext + 1;
        byte[] map = padSprite[i];
        int[] colour = padColour;
        int cx = ri(pad.x), cy = ri(pad.y);
        int ox = cx - ext, oy = cy - ext;
        int i0 = Math.max(0, -ox), i1 = Math.min(side, W - ox);
        int j0 = Math.max(0, -oy), j1 = Math.min(side, H - oy);
        for (int j = j0; j < j1; j++) {
            int m = j * side, o = (oy + j) * W + ox;
            for (int k = i0; k < i1; k++) {
                int v = map[m + k];
                if (v != 0) out[o + k] = colour[v];
            }
        }
        byte[] lotus = lotusSprite[i];
        if (lotus == null) return;
        int lext = lotusExt[i], lside = 2 * lext + 2;
        ox = cx + lotusDx[i] - lext;
        oy = cy + lotusDy[i] - lext;
        i0 = Math.max(0, -ox);
        i1 = Math.min(lside, W - ox);
        j0 = Math.max(0, -oy);
        j1 = Math.min(lside, H - oy);
        for (int j = j0; j < j1; j++) {
            int m = j * lside, o = (oy + j) * W + ox;
            for (int k = i0; k < i1; k++) {
                if ((lotus[m + k] & LOTUS_SHADOW) != 0) out[o + k] = scale(out[o + k], 196);
            }
        }
        int[] lc = lotusColour;
        for (int j = j0; j < j1; j++) {
            int m = j * lside, o = (oy + j) * W + ox;
            for (int k = i0; k < i1; k++) {
                int cls = lotus[m + k] & 7;
                if (cls != 0) out[o + k] = lc[cls];
            }
        }
    }

    private void drawDuckweed(int[] pts, int colour, int[] out) {
        int col = grade(colour), dark = scale(col, 150), light = lighten(col, 60);
        for (int i = 0; i < pts.length; i++) {
            int v = pts[i];
            int x = v >>> 16, y = v & 0xFFFF;
            if (y >= H) continue;
            // A 2x2 frond with one corner left out (which one varies), and its shadow one pixel below-right.
            int skip = ((i * 0x9E3779B1) >>> 30) & 3;
            for (int q = 0; q < 4; q++) {
                if (q == skip) continue;
                int px = x + (q & 1), py = y + (q >> 1);
                if (px + 1 < W && py + 1 < H) {
                    int o = (py + 1) * W + px + 1;
                    out[o] = blend(out[o], dark, 110);
                }
            }
            for (int q = 0; q < 4; q++) {
                if (q == skip) continue;
                int px = x + (q & 1), py = y + (q >> 1);
                if (px < W && py < H) out[py * W + px] = q == 0 ? light : col;
            }
        }
    }

    // ---- 6. Fireflies (ungraded, so they glow) ----

    private static void drawFireflies(PondModel.Firefly[] flies, float level, long nowMs, int[] out) {
        float secs = nowMs * 0.001f;
        for (PondModel.Firefly f : flies) {
            float speed = 1.2f + 0.3f * sinF(f.phase * 3.1f);
            // On about 60% of the time: lit while the sine is above -0.3, with a smooth fade over 0.45.
            float b = (sinF(secs * speed + f.phase) + 0.3f) / 0.45f;
            if (b <= 0f) continue;
            if (b > 1f) b = 1f;
            b = b * b * (3f - 2f * b);
            int in = (int) (b * level * 256f);
            if (in <= 2) continue;
            int fx = ri(f.x), fy = ri(f.y);
            for (int dy = -3; dy <= 3; dy++) {
                int y = fy + dy;
                if (y < 0 || y >= H) continue;
                for (int dx = -3; dx <= 3; dx++) {
                    int x = fx + dx;
                    if (x < 0 || x >= W) continue;
                    int o = y * W + x;
                    if ((dx | dy) == 0 || (dx * dx + dy * dy == 1)) {
                        out[o] = blend(out[o], FIREFLY_CORE, (dx | dy) == 0 ? in : in >> 1);
                    } else {
                        int w = GLOW[(dy + 3) * 7 + dx + 3];
                        if (w > 0) out[o] = add(out[o], FIREFLY, (w * in * 330) >> 16);
                    }
                }
            }
        }
    }

    // ---- 7. Breathing ring ----

    private void drawRing(float size, float alpha, int[] out) {
        float radius = RING_MIN + size * RING_GROW;
        int fade = (int) (alpha * 256f);
        int r16 = (int) (radius * 16f);
        int ext = Math.min(RING_MAX, (int) radius + 7);
        // The faint fill inside, 0.07 * (d / radius)^2 in 1/256, as d16^2 * k >> 24.
        long k = (long) (0.07 * 256 * (1L << 24) / ((double) r16 * r16));
        // Inside this square the fill rounds to 0 (d < 0.236 * radius), so it is not visited.
        int hole = (int) (radius * 0.16f);
        int cx = W / 2, cy = H / 2;
        for (int dy = -ext; dy <= ext; dy++) {
            int y = cy + dy;
            if (y < 0 || y >= H) continue;
            int row = (dy + RING_MAX) * RING_SPAN + RING_MAX;
            boolean holeRow = dy >= -hole && dy <= hole;
            for (int dx = -ext; dx <= ext; dx++) {
                if (holeRow && dx == -hole) { dx = hole; continue; }
                int x = cx + dx;
                if (x < 0 || x >= W) continue;
                int d16 = ringDist[row + dx];
                int e16 = Math.abs(d16 - r16);
                int a256;
                if (d16 >= r16) {
                    if (e16 >= 80) continue;
                    a256 = RING_EDGE[e16];
                } else {
                    a256 = (e16 < 80 ? RING_EDGE[e16] : 0) + (int) ((d16 * (long) d16 * k) >> 24);
                }
                a256 = (a256 * fade) >> 8;
                if (a256 <= 0) continue;
                int o = y * W + x;
                out[o] = add(out[o], RING, a256);
            }
        }
    }

    // ---- Textures built once ----

    private void buildFloor(Random rnd) {
        float[] lum = new float[W * H];
        int g1 = 64, g2 = 26, g3 = 11;
        float[] n1 = grid(rnd, g1), n2 = grid(rnd, g2), n3 = grid(rnd, g3);
        float hw = W * 0.5f, hh = H * 0.5f;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float v = 0.55f * sample(n1, g1, x, y) + 0.30f * sample(n2, g2, x, y) + 0.15f * sample(n3, g3, x, y);
                float ex = (x - hw) / hw, ey = (y - hh) / hh;
                float vig = Math.min(1f, (ex * ex + ey * ey) * 0.55f);
                lum[y * W + x] = (0.22f + 0.62f * v) * (1f - 0.3f * vig);
            }
        }
        // A few darker silt patches.
        for (int k = 0; k < 6; k++) {
            float px = rnd.nextFloat() * W, py = rnd.nextFloat() * H;
            float rx = 24f + rnd.nextFloat() * 40f, ry = 16f + rnd.nextFloat() * 24f;
            float s = 0.12f + rnd.nextFloat() * 0.10f;
            for (int y = Math.max(0, (int) (py - ry)); y < Math.min(H, (int) (py + ry) + 1); y++) {
                for (int x = Math.max(0, (int) (px - rx)); x < Math.min(W, (int) (px + rx) + 1); x++) {
                    float qx = (x - px) / rx, qy = (y - py) / ry, d = qx * qx + qy * qy;
                    if (d >= 1f) continue;
                    float f = 1f - d;
                    lum[y * W + x] -= s * f * f * (3f - 2f * f);
                }
            }
        }
        // Sparse pebbles, lit from the upper left.
        for (int k = 0; k < 110; k++) {
            float px = rnd.nextFloat() * W, py = rnd.nextFloat() * H;
            float pr = 0.8f + rnd.nextFloat() * 1.6f;
            float tone = rnd.nextFloat() < 0.7f ? 0.10f : -0.10f;
            int e = (int) Math.ceil(pr) + 1;
            for (int y = (int) py - e; y <= (int) py + e; y++) {
                for (int x = (int) px - e; x <= (int) px + e; x++) {
                    if (x < 0 || y < 0 || x >= W || y >= H) continue;
                    float dx = x - px, dy = y - py, d2 = dx * dx + dy * dy;
                    float add;
                    if (d2 <= pr * pr) {
                        add = tone;
                        if (dx + dy < -pr * 0.5f) add += 0.06f;
                        else if (dx + dy > pr * 0.5f) add -= 0.05f;
                    } else if (d2 <= (pr + 1f) * (pr + 1f) && dx + dy > 0f) {
                        add = -0.06f;
                    } else {
                        continue;
                    }
                    lum[y * W + x] += add;
                }
            }
        }
        // Ordered dither into levels 18 apart: pixel-art steps instead of a smooth gradient.
        final float step = 18f;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float v = lum[y * W + x] * 255f + ((BAYER[((y & 3) << 2) | (x & 3)] + 0.5f) / 16f - 0.5f) * step;
                floorLum[y * W + x] = (byte) clamp(Math.round(v / step), 0, LUM_LEVELS - 1);
            }
        }
    }

    private static float[] grid(Random rnd, int cell) {
        int gw = W / cell + 2, gh = H / cell + 2;
        float[] g = new float[gw * gh];
        for (int i = 0; i < g.length; i++) g[i] = rnd.nextFloat();
        return g;
    }

    private static float sample(float[] g, int cell, int x, int y) {
        int gw = W / cell + 2;
        float fx = x / (float) cell, fy = y / (float) cell;
        int ix = (int) fx, iy = (int) fy;
        float tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3f - 2f * tx);
        ty = ty * ty * (3f - 2f * ty);
        int i = iy * gw + ix;
        float top = g[i] + (g[i + 1] - g[i]) * tx;
        float bot = g[i + gw] + (g[i + gw + 1] - g[i + gw]) * tx;
        return top + (bot - top) * ty;
    }

    /** Voronoi with wrapped points: bright where the nearest and second-nearest points are almost equally far. */
    private void buildCaustics(Random rnd) {
        final int n = 14;
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) { px[i] = rnd.nextFloat() * TILE; py[i] = rnd.nextFloat() * TILE; }
        final float width = 7f;
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float d1 = Float.MAX_VALUE, d2 = Float.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    float dx = Math.abs(x - px[i]), dy = Math.abs(y - py[i]);
                    if (dx > TILE / 2f) dx = TILE - dx;
                    if (dy > TILE / 2f) dy = TILE - dy;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
                }
                float v = 1f - (d2 - d1) / width;
                caustic[(y << 7) | x] = (byte) (v > 0f ? (int) (v * v * 255f) : 0);
            }
        }
    }

    // ---- Small helpers ----

    private static float sinF(float a) {
        return SIN[(int) (a * SIN_SCALE) & (SIN_N - 1)];
    }

    private static float cosF(float a) {
        return SIN[((int) (a * SIN_SCALE) + SIN_N / 4) & (SIN_N - 1)];
    }

    private static int ri(float v) {
        return (int) Math.floor(v + 0.5f);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** dst toward src by a/256, opaque. */
    private static int blend(int d, int s, int a) {
        int dr = (d >> 16) & 255, dg = (d >> 8) & 255, db = d & 255;
        int r = dr + ((((s >> 16) & 255) - dr) * a >> 8);
        int g = dg + ((((s >> 8) & 255) - dg) * a >> 8);
        int b = db + (((s & 255) - db) * a >> 8);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Each channel times f/256. */
    private static int scale(int c, int f) {
        return 0xFF000000 | ((((c >> 16) & 255) * f >> 8) << 16) | ((((c >> 8) & 255) * f >> 8) << 8)
                | ((c & 255) * f >> 8);
    }

    /** Each channel toward white by f/256. */
    private static int lighten(int c, int f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((r + ((255 - r) * f >> 8)) << 16) | ((g + ((255 - g) * f >> 8)) << 8)
                | (b + ((255 - b) * f >> 8));
    }

    /** Adds col times a/256, clamped. */
    private static int add(int d, int col, int a) {
        int r = ((d >> 16) & 255) + (((col >> 16) & 255) * a >> 8);
        int g = ((d >> 8) & 255) + (((col >> 8) & 255) * a >> 8);
        int b = (d & 255) + ((col & 255) * a >> 8);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}

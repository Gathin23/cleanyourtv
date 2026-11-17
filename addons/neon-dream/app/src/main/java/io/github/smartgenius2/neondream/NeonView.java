package io.github.smartgenius2.neondream;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/**
 * The scene: a neon city in the rain or a synthwave grid, with the clock drifting over it.
 * One View, one onDraw, drawn on a plain canvas at 30 fps. No network and no images.
 */
final class NeonView extends View {

    private static final long FRAME_MS = 33L;
    /** Per palette: accent A, accent B, three sky stops. */
    private static final int[][] PALETTES = {
            {0xFFFF2A6D, 0xFF05D9E8, 0xFF14052B, 0xFF3A0A45, 0xFF7A1A5E},     // magenta
            {0xFFFFD400, 0xFFFF3C28, 0xFF1A0A02, 0xFF3D1305, 0xFF7A2A0A},     // amber
            {0xFF39FF88, 0xFF00C2B8, 0xFF021A12, 0xFF05352C, 0xFF0A5A4A}      // green
    };

    private final boolean grid, clock;
    private final int colA, colB;
    private final int[] sky;
    private final Random rnd = new Random(7);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint additive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Calendar calendar = Calendar.getInstance();
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.FULL);
    private final StringBuilder time = new StringBuilder(5);
    private final long startMs = System.currentTimeMillis();
    private final Path[] mountains = {new Path(), new Path()};
    private volatile boolean running;

    private int w, h;
    private Tower[] towers;
    private float[] dropX, dropY, dropLen, dropSpeed;
    private float[] starX, starY, starPhase;
    private LinearGradient skyShader, floorShader, gridGlowShader, sunShader;
    private RadialGradient vignette;
    private long lastFrame;

    private static final class Tower {
        float x, w, h;
        int layer;
        float[] winX, winY, winPhase;
        boolean[] winOn;
        boolean sign, signUsesA;
        float signY, signPhase;
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    NeonView(Context context, String scene, String palette, boolean showClock) {
        super(context);
        grid = isGrid(scene);
        clock = showClock;
        int p = paletteIndex(palette);
        colA = PALETTES[p][0];
        colB = PALETTES[p][1];
        sky = new int[] {PALETTES[p][2], PALETTES[p][3], PALETTES[p][4]};
        line.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        additive.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        setBackgroundColor(Color.BLACK);
    }

    /** Index into PALETTES for a dream_palette value; anything unknown is magenta. */
    static int paletteIndex(String palette) {
        return "amber".equals(palette) ? 1 : "green".equals(palette) ? 2 : 0;
    }

    /** True for the synthwave grid; any other dream_scene value is the rain city. */
    static boolean isGrid(String scene) {
        return "neon_grid".equals(scene);
    }

    void start() {
        if (running) return;
        running = true;
        post(frame);
    }

    void stop() {
        running = false;
        removeCallbacks(frame);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw;
        h = nh;
        if (w == 0 || h == 0) return;
        skyShader = new LinearGradient(0, 0, 0, h, sky, new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP);
        float base = h * .92f;
        floorShader = new LinearGradient(0, base, 0, h, new int[] {withAlpha(colA, 90), 0xF2080414, 0xFF05030C},
                new float[] {0f, .4f, 1f}, Shader.TileMode.CLAMP);
        float hz = h * .46f;
        gridGlowShader = new LinearGradient(0, hz, 0, h, withAlpha(colA, 64), 0xFF05020C, Shader.TileMode.CLAMP);
        sunShader = new LinearGradient(0, hz - h * .3f, 0, hz, colA, colB, Shader.TileMode.CLAMP);
        vignette = new RadialGradient(w / 2f, h / 2f, h * .95f, new int[] {0x00000000, 0x00000000, 0x8C000000},
                new float[] {0f, .47f, 1f}, Shader.TileMode.CLAMP);
        float[][] m = {{.08f, .22f, .3f}, {.7f, .25f, .92f}};
        for (int i = 0; i < m.length; i++) {
            Path path = mountains[i];
            path.reset();
            path.moveTo(w * m[i][0], hz);
            path.lineTo(w * m[i][2] * .5f + w * m[i][0] * .5f, hz - h * m[i][1]);
            path.lineTo(w * m[i][2], hz);
            path.close();
        }
        buildCity();
    }

    private float rand(float a, float b) {
        return a + rnd.nextFloat() * (b - a);
    }

    private void buildCity() {
        float[][] layers = {{.05f, .09f, .25f, .5f}, {.06f, .11f, .3f, .65f}, {.07f, .13f, .4f, .8f}};
        ArrayList<Tower> list = new ArrayList<>();
        for (int z = 0; z < 3; z++) {
            float x = -w * .05f;
            while (x < w * 1.05f) {
                Tower t = new Tower();
                t.w = rand(layers[z][0], layers[z][1]) * w;
                t.h = rand(layers[z][2], layers[z][3]) * h;
                t.x = x;
                t.layer = z;
                int n = 28;
                t.winX = new float[n];
                t.winY = new float[n];
                t.winPhase = new float[n];
                t.winOn = new boolean[n];
                for (int i = 0; i < n; i++) {
                    t.winX[i] = rnd.nextFloat();
                    t.winY[i] = rnd.nextFloat();
                    t.winPhase[i] = rnd.nextFloat() * 9f;
                    t.winOn[i] = rnd.nextFloat() < .35f;
                }
                if (rnd.nextFloat() < .22f) {
                    t.sign = true;
                    t.signUsesA = rnd.nextBoolean();
                    t.signY = rand(.15f, .7f);
                    t.signPhase = rnd.nextFloat() * 9f;
                }
                list.add(t);
                x += t.w * rand(.85f, 1.02f);
            }
        }
        towers = list.toArray(new Tower[0]);
        int d = 220;
        dropX = new float[d]; dropY = new float[d]; dropLen = new float[d]; dropSpeed = new float[d];
        for (int i = 0; i < d; i++) {
            dropX[i] = rnd.nextFloat() * w;
            dropY[i] = rnd.nextFloat() * h;
            dropLen[i] = rand(.02f, .06f) * h;
            dropSpeed[i] = rand(.7f, 1.4f) * h;
        }
        int s = 60;
        starX = new float[s]; starY = new float[s]; starPhase = new float[s];
        for (int i = 0; i < s; i++) {
            starX[i] = rnd.nextFloat() * w;
            starY[i] = rnd.nextFloat() * h * .4f;
            starPhase[i] = rnd.nextFloat() * 9f;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (w == 0 || h == 0 || towers == null) return;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? FRAME_MS / 1000f : Math.min((now - lastFrame) / 1000f, .1f);
        lastFrame = now;
        float t = now - startMs;
        if (grid) drawGrid(c, t); else drawCity(c, t, dt);
        fill.setShader(vignette);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        if (clock) drawClock(c, now - startMs, now);
    }

    private void drawCity(Canvas c, float t, float dt) {
        fill.setShader(skyShader);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        for (int i = 0; i < starX.length; i++) {
            fill.setColor(Color.argb((int) (255 * (.25f + .25f * Math.sin(t / 700f + starPhase[i]))), 255, 255, 255));
            c.drawRect(starX[i], starY[i], starX[i] + 2, starY[i] + 2, fill);
        }
        float base = h * .92f, span = w * 1.1f;
        for (Tower b : towers) {
            float shade = b.layer == 0 ? .35f : b.layer == 1 ? .6f : 1f;
            float speed = b.layer == 0 ? 1.2f : b.layer == 1 ? 2.4f : 4f;
            float x = (((b.x - t / 1000f * speed) % span) + span) % span - w * .05f;
            float top = base - b.h;
            fill.setColor(Color.rgb((int) (10 * shade + 4), (int) (6 * shade + 3), (int) (22 * shade + 8)));
            c.drawRect(x, top, x + b.w, base, fill);
            fill.setColor(withAlpha(b.layer == 2 ? colB : colA, 25 + 20 * b.layer));
            c.drawRect(x, top, x + b.w, top + 2, fill);
            for (int i = 0; i < b.winX.length; i++) {
                if (!b.winOn[i]) continue;
                if (.5f + .5f * Math.sin(t / 2500f + b.winPhase[i]) <= .15f) continue;
                float cx = x + (b.winX[i] * .8f + .1f) * b.w, cy = top + (b.winY[i] * .85f + .08f) * b.h;
                fill.setColor(withAlpha(i % 3 != 0 ? colB : colA, 90 + 30 * b.layer));
                c.drawRect(cx, cy, cx + h * .012f, cy + h * .018f, fill);
            }
            if (b.sign) {
                float flick = .75f + .25f * (float) Math.sin(t / 130f + b.signPhase)
                        * (Math.sin(t / 1900f + b.signPhase) > .8 ? 1f : .2f);
                int col = b.signUsesA ? colA : colB;
                float sx = x + b.w * .12f, sy = top + b.h * b.signY, sw = b.w * .08f, sh = b.h * .2f;
                fill.setColor(withAlpha(col, (int) (50 * flick)));
                c.drawRect(sx - sw, sy - sw, sx + sw * 2f, sy + sh + sw, fill);   // halo
                fill.setColor(withAlpha(col, (int) (255 * flick)));
                c.drawRect(sx, sy, sx + sw, sy + sh, fill);
            }
        }
        fill.setShader(floorShader);
        c.drawRect(0, base, w, h, fill);
        fill.setShader(null);
        for (int k = 0; k < 6; k++) {
            fill.setColor(withAlpha(k % 2 != 0 ? colB : colA, 20));
            float rx = (k * .19f + .05f) * w + (float) Math.sin(t / 4000f + k) * 14f;
            c.drawRect(rx, base, rx + w * .02f, h, fill);
        }
        line.setStrokeWidth(2f);
        line.setColor(0x59BED2FF);
        float slant = .18f;
        for (int i = 0; i < dropX.length; i++) {
            dropY[i] += dropSpeed[i] * dt;
            dropX[i] -= dropSpeed[i] * dt * slant;
            if (dropY[i] > h) {
                dropY[i] = -dropLen[i];
                dropX[i] = rnd.nextFloat() * w * 1.2f;
            }
            c.drawLine(dropX[i], dropY[i], dropX[i] + dropLen[i] * slant, dropY[i] - dropLen[i], line);
        }
        long phase = (long) (t / 260f);
        float ph = (t / 260f) % 1f;
        fill.setColor(Color.argb((int) (90 * (1f - ph)), 190, 210, 255));
        for (int q = 0; q < 14; q++) {
            float px = (float) (Math.sin(q * 91.7 + phase * 1.3) * .5 + .5) * w;
            float py = base + (h - base) * (.2f + ((q * 37) % 70) / 100f);
            c.drawRect(px - ph * 14f, py, px + ph * 14f, py + 2, fill);
        }
    }

    private void drawGrid(Canvas c, float t) {
        float hz = h * .46f;
        fill.setColor(0xFF05020C);
        c.drawRect(0, 0, w, h, fill);
        c.save();
        c.clipRect(0, hz - h * .3f, w, hz);
        fill.setShader(sunShader);
        c.drawCircle(w / 2f, hz, h * .26f, fill);
        fill.setShader(null);
        fill.setColor(0xFF05020C);
        for (int i = 0; i < 7; i++) {
            float y = hz - h * .24f + i * i * h * .006f + i * h * .02f;
            c.drawRect(0, y, w, y + h * (.004f + i * .0035f), fill);
        }
        c.restore();
        fill.setShader(gridGlowShader);
        c.drawRect(0, hz, w, h, fill);
        fill.setShader(null);
        line.setStrokeWidth(3f);
        line.setColor(withAlpha(colB, 190));
        for (int v = -24; v <= 24; v++) c.drawLine(w / 2f + v * w * .012f, hz, w / 2f + v * w * .13f, h, line);
        float sp = (t / 2200f) % 1f;
        for (int i = 0; i < 14; i++) {
            float u = (i + sp) / 14f, y = hz + (h - hz) * u * u;
            c.drawLine(0, y, w, y, line);
        }
        fill.setColor(0xFF05020C);
        c.drawRect(0, hz - 2, w, hz + 2, fill);
        fill.setColor(withAlpha(colB, 230));
        c.drawRect(0, hz - 2, w, hz + 2, fill);
        line.setStrokeWidth(4f);
        line.setColor(withAlpha(colA, 230));
        fill.setColor(0xFF0B0420);
        for (Path path : mountains) {
            c.drawPath(path, fill);
            c.drawPath(path, line);
        }
        fill.setColor(0x2E000000);
        for (float y = 0; y < h; y += 4f) c.drawRect(0, y, w, y + 1f, fill);
    }

    private void drawClock(Canvas c, float t, long now) {
        calendar.setTimeInMillis(now);
        time.setLength(0);
        int hour = calendar.get(Calendar.HOUR_OF_DAY), min = calendar.get(Calendar.MINUTE);
        if (hour < 10) time.append('0');
        time.append(hour).append(((int) (t / 1000f) % 2 == 0) ? ':' : ' ');
        if (min < 10) time.append('0');
        time.append(min);
        long minute = now / 60000L;
        double s1 = Math.sin(minute * 12.9898) * 43758.5453, s2 = Math.sin(minute * 78.233) * 12345.6789;
        float px = (float) (.1 + .45 * (s1 - Math.floor(s1))), py = (float) (.12 + .38 * (s2 - Math.floor(s2)));
        float size = h * .2f, x = px * w, y = py * h + size;
        boolean glitch = ((int) (t / 100f)) % 37 == 0;
        float off = glitch ? 4f + rnd.nextFloat() * 8f : 1.5f;
        text.setTextSize(size);
        text.setShadowLayer(0, 0, 0, 0);
        additive.setTextSize(size);
        additive.setTypeface(text.getTypeface());
        additive.setColor(withAlpha(colA, 200));
        c.drawText(time, 0, time.length(), x - off, y, additive);
        additive.setColor(withAlpha(colB, 200));
        c.drawText(time, 0, time.length(), x + off, y, additive);
        text.setColor(0xFFF4F0FF);
        text.setShadowLayer(h * .025f, 0, 0, colB);
        c.drawText(time, 0, time.length(), x, y, text);
        text.setShadowLayer(0, 0, 0, 0);
        text.setTextSize(h * .028f);
        text.setLetterSpacing(.12f);
        text.setColor(withAlpha(colB, 230));
        c.drawText(dateFormat.format(calendar.getTime()).toUpperCase(Locale.getDefault()), x + 4f, y + h * .05f, text);
        text.setLetterSpacing(0f);
        if (glitch) {
            fill.setColor(withAlpha(colA, 130));
            float gy = y - size * (.2f + rnd.nextFloat() * .6f);
            c.drawRect(x, gy, x + size * 2.6f, gy + 3f + rnd.nextFloat() * 4f, fill);
        }
    }
}

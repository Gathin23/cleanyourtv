package com.gathin.winter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/** Original, locally drawn winter illustration for the preview and the Android screensaver. */
public final class WinterCabinView extends View {
    private static final String TAG = "WinterCabin";
    private static final int W = 1280, H = 720;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();
    private final Shader fireGlow = new RadialGradient(1000, 416, 170,
            new int[]{0x88FF9B39, 0x22F18A30, 0x00DA6529}, null, Shader.TileMode.CLAMP);
    private Bitmap artwork;
    private boolean requested, looping;
    private long frames;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!eligible()) { updateLoop(); return; }
            invalidate();
            postDelayed(this, CabinMotion.FRAME_MS);
        }
    };

    public WinterCabinView(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void start() { requested = true; updateLoop(); }

    public void stop() {
        requested = false;
        updateLoop();
        releaseArtwork();
    }

    public void releaseArtwork() {
        // Drop, don't recycle: a render-thread display list may still own the last frame.
        artwork = null;
    }

    private boolean eligible() {
        return CabinMotion.shouldRun(requested, isAttachedToWindow(), getWindowVisibility() == VISIBLE && isShown());
    }

    private void updateLoop() {
        if (tick == null) return; // visibility callbacks can arrive from View's constructor
        boolean run = eligible();
        if (!run) releaseArtwork(); // A pending draw can rebuild the cache between pause and hide.
        if (run == looping) return;
        looping = run;
        removeCallbacks(tick);
        if (run) {
            Log.i(TAG, "animation started (12 fps)");
            invalidate();
            postDelayed(tick, CabinMotion.FRAME_MS);
        } else {
            Log.i(TAG, "animation stopped; frames=" + frames);
        }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateLoop(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateLoop();
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateLoop();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        if (artwork == null) {
            artwork = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565); // 1.76 MiB, no video decoder
            drawRoom(new Canvas(artwork));
        }
        float scale = Math.max(getWidth() / (float) W, getHeight() / (float) H);
        canvas.save();
        canvas.translate((getWidth() - W * scale) / 2f, (getHeight() - H * scale) / 2f);
        canvas.scale(scale, scale);
        paint.setShader(null);
        paint.setAlpha(255);
        canvas.drawBitmap(artwork, 0, 0, paint);
        double time = SystemClock.elapsedRealtime() / 1000d;
        drawSnow(canvas, time);
        drawFire(canvas, time);
        drawLights(canvas, time);
        canvas.restore();
        frames++;
    }

    private void color(int color) { paint.setShader(null); paint.setColor(color); paint.setStyle(Paint.Style.FILL); }
    private void rect(Canvas c, float l, float t, float r, float b, int ink) {
        color(ink); c.drawRect(l, t, r, b, paint);
    }
    private void round(Canvas c, float l, float t, float r, float b, float radius, int ink) {
        color(ink); box.set(l, t, r, b); c.drawRoundRect(box, radius, radius, paint);
    }
    private void circle(Canvas c, float x, float y, float radius, int ink) {
        color(ink); c.drawCircle(x, y, radius, paint);
    }
    private void line(Canvas c, float x, float y, float x2, float y2, float width, int ink) {
        color(ink); paint.setStrokeWidth(width); paint.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x, y, x2, y2, paint);
    }
    private void polygon(Canvas c, int ink, float... points) {
        color(ink); path.reset(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.close(); c.drawPath(path, paint);
    }

    private void drawRoom(Canvas c) {
        color(0xFF162B31);
        paint.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0xFF0B1A26, 0xFF243A3C, 0xFF352920}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        for (int x = 4; x < W; x += 68) {
            line(c, x, 0, x, 470, 2, 0x305D7770);
            line(c, x + 3, 0, x + 3, 470, 1, 0x1800060B);
        }
        // Heavy timber rafters and warm, muted wood trim, clear of the header text.
        polygon(c, 0xFF382E27, 0, 0, 1280, 0, 1280, 31, 0, 31);
        line(c, 0, 30, 1280, 30, 2, 0xFF625047);
        rect(c, 0, 465, 1280, 720, 0xFF332925);
        rect(c, 0, 463, 1280, 477, 0xFF513C2F);
        for (int y = 510; y < 720; y += 52) line(c, 0, y, W, y, 2, 0xFF241E1D);
        for (int x = -800; x < 1900; x += 170) line(c, 640, 468, x, 720, 1, 0x705B4437);

        drawWindow(c);
        drawCurtain(c, 62, false);
        drawCurtain(c, 566, true);
        // Fir garland with warm fairy lights above the window.
        for (int i = 0; i < 36; i++) {
            float x = 70 + i * 15;
            circle(c, x, 149 + (float) Math.sin(i * .4) * 4, 9, i % 2 == 0 ? 0xFF224A3C : 0xFF1B392F);
            if (i % 3 == 0) circle(c, x, 154, 2.6f, 0xFFFFD99A);
        }
        drawTree(c);
        drawHearth(c);
        drawArmchair(c);
        // Woven oval rug, coffee table and a steaming mug.
        color(0xFF705449); box.set(385, 525, 1010, 750); c.drawOval(box, paint);
        color(0xFF8D6D59); box.set(415, 548, 980, 718); c.drawOval(box, paint);
        round(c, 455, 540, 727, 566, 16, 0xFF9E7755);
        polygon(c, 0xFF49372C, 480, 562, 500, 562, 486, 664, 473, 664);
        polygon(c, 0xFF49372C, 679, 562, 698, 562, 704, 664, 690, 664);
        round(c, 544, 506, 577, 544, 5, 0xFFD9CDB0);
        color(0xFFD9CDB0); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5);
        box.set(568, 515, 589, 534); c.drawOval(box, paint); paint.setStyle(Paint.Style.FILL);
        line(c, 553, 496, 550, 482, 2, 0x55E8D5B8);
        line(c, 566, 495, 570, 478, 2, 0x40E8D5B8);
        // Foreground shadows give the room depth.
        color(Color.BLACK);
        paint.setShader(new LinearGradient(0, 455, 0, 720, 0x0004070D, 0xB004070D, Shader.TileMode.CLAMP));
        c.drawRect(0, 455, W, H, paint);
        paint.setShader(null);
    }

    /** A compact wingback chair, not a long flat couch; the throw hangs over one arm. */
    private void drawArmchair(Canvas c) {
        color(0x660A0809); box.set(94, 649, 363, 690); c.drawOval(box, paint);
        // Exposed, tapered timber feet make the seat read as furniture rather than a bed base.
        polygon(c, 0xFF805A3D, 130, 636, 150, 636, 145, 678, 132, 678);
        polygon(c, 0xFF9C7049, 305, 639, 324, 639, 329, 678, 316, 678);
        line(c, 133, 676, 145, 676, 3, 0xFFD1A36A);
        line(c, 316, 676, 328, 676, 3, 0xFFD1A36A);

        // Curved upholstered shell and gently flared wings.
        color(0xFF985A50); path.reset(); path.moveTo(114, 591);
        path.cubicTo(118, 557, 108, 515, 126, 488);
        path.cubicTo(141, 467, 175, 473, 211, 472);
        path.cubicTo(251, 473, 289, 465, 307, 486);
        path.cubicTo(325, 509, 315, 560, 329, 593);
        path.lineTo(310, 624); path.lineTo(136, 624); path.close(); c.drawPath(path, paint);
        color(0xFFB77562); path.reset(); path.moveTo(125, 510);
        path.cubicTo(129, 483, 146, 481, 155, 489);
        path.cubicTo(146, 523, 146, 556, 154, 580);
        path.lineTo(131, 594); path.cubicTo(123, 564, 120, 535, 125, 510);
        path.close(); c.drawPath(path, paint);
        color(0xFFB16B5C); path.reset(); path.moveTo(281, 489);
        path.cubicTo(297, 478, 311, 494, 312, 519);
        path.cubicTo(311, 546, 313, 571, 321, 591);
        path.lineTo(293, 584); path.cubicTo(287, 551, 291, 518, 281, 489);
        path.close(); c.drawPath(path, paint);

        // Separate, softly shaded back cushion with two small upholstery tufts.
        round(c, 151, 490, 289, 583, 24, 0xFF78443F);
        color(0xFFBD7966);
        paint.setShader(new LinearGradient(0, 496, 0, 580, 0xFFC5836F, 0xFFA76055, Shader.TileMode.CLAMP));
        box.set(156, 495, 284, 578); c.drawRoundRect(box, 21, 21, paint);
        circle(c, 191, 536, 2.7f, 0xFF8E5149);
        circle(c, 248, 536, 2.7f, 0xFF8E5149);
        line(c, 188, 540, 193, 540, 1.5f, 0x60E6AF88);
        line(c, 245, 540, 250, 540, 1.5f, 0x60E6AF88);

        // Visible seat depth, rounded cushion and a darker front apron underneath.
        round(c, 127, 573, 316, 649, 22, 0xFF79423B);
        color(0xFFC38069); path.reset(); path.moveTo(150, 573);
        path.cubicTo(187, 565, 256, 567, 291, 575);
        path.quadTo(312, 580, 311, 599); path.lineTo(307, 613);
        path.quadTo(305, 626, 285, 628); path.lineTo(151, 623);
        path.quadTo(131, 620, 132, 603); path.lineTo(134, 589);
        path.quadTo(135, 578, 150, 573); path.close(); c.drawPath(path, paint);
        color(0xFFA45F50); path.reset(); path.moveTo(136, 607);
        path.cubicTo(180, 618, 263, 622, 309, 610); path.lineTo(307, 628);
        path.quadTo(306, 641, 287, 644); path.lineTo(151, 638);
        path.quadTo(137, 635, 136, 621); path.close(); c.drawPath(path, paint);
        path.reset(); path.moveTo(143, 607); path.cubicTo(187, 619, 267, 619, 301, 610);
        chairSeam(c, 0xFFD49A7B, 2);

        // Rounded arms stop above the feet; the right-hand arm catches the firelight.
        color(0xFFA86B59); path.reset(); path.moveTo(110, 549);
        path.quadTo(99, 550, 100, 569); path.lineTo(103, 627);
        path.quadTo(104, 647, 125, 650); path.lineTo(145, 649);
        path.lineTo(145, 582); path.quadTo(139, 550, 110, 549);
        path.close(); c.drawPath(path, paint);
        round(c, 99, 550, 143, 580, 14, 0xFFC28B70);
        color(0xFFB87861); path.reset(); path.moveTo(301, 554);
        path.quadTo(321, 543, 339, 554); path.quadTo(350, 561, 347, 582);
        path.lineTo(344, 630); path.quadTo(341, 651, 321, 654);
        path.lineTo(299, 651); path.lineTo(298, 590); path.quadTo(294, 568, 301, 554);
        path.close(); c.drawPath(path, paint);
        round(c, 300, 552, 344, 580, 13, 0xFFD29877);
        path.reset(); path.moveTo(333, 586); path.quadTo(333, 617, 328, 640);
        chairSeam(c, 0xFFD29675, 2);

        // A folded wool throw follows the left arm, with curved folds and a short fringe.
        color(0xFFE1C9A2); path.reset(); path.moveTo(106, 553);
        path.cubicTo(119, 549, 136, 553, 148, 560);
        path.quadTo(159, 575, 155, 596);
        path.cubicTo(151, 613, 153, 626, 155, 640);
        path.quadTo(137, 649, 115, 640); path.lineTo(110, 615);
        path.cubicTo(105, 598, 109, 576, 106, 553);
        path.close(); c.drawPath(path, paint);
        path.reset(); path.moveTo(137, 560); path.cubicTo(150, 586, 136, 609, 145, 639);
        chairSeam(c, 0xFFB89B75, 3);
        for (int y = 567; y <= 630; y += 10) {
            path.reset(); path.moveTo(113, y);
            path.cubicTo(124, y - 4, 137, y + 5, 151, y + 1);
            chairSeam(c, 0x608B7159, 1.2f);
        }
        for (int i = 0; i < 8; i++) line(c, 117 + i * 5, 640 + (i % 2) * 2,
                116 + i * 5, 649 + (i % 3), 1.6f, 0xFFDCC29A);
    }

    private void chairSeam(Canvas c, int ink, float width) {
        color(ink); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width);
        c.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
    }

    private void drawWindow(Canvas c) {
        round(c, 72, 160, 595, 451, 15, 0xFF59443A);
        round(c, 82, 169, 585, 438, 9, 0xFF0D2438);
        color(Color.BLACK);
        paint.setShader(new LinearGradient(0, 170, 0, 438, 0xFF11273D, 0xFF3F6472, Shader.TileMode.CLAMP));
        box.set(83, 170, 584, 438); c.drawRoundRect(box, 8, 8, paint);
        circle(c, 478, 218, 24, 0xFFC9DDDC);
        circle(c, 487, 212, 24, 0xFF142C42);
        for (int i = 0; i < 13; i++) {
            float x = 82 + i * 42;
            float h = 72 + (i * 47 % 79);
            pine(c, x, 405, h, 0xFF244A59);
        }
        for (int i = 0; i < 9; i++) pine(c, 95 + i * 64, 429, 87 + i * 31 % 91, 0xFF173C4B);
        color(0xFF88AEB9);
        path.reset(); path.moveTo(82, 420); path.cubicTo(230, 392, 335, 445, 584, 411);
        path.lineTo(584, 440); path.lineTo(82, 440); path.close(); c.drawPath(path, paint);
        rect(c, 326, 170, 336, 438, 0xFF59463B);
        rect(c, 83, 302, 584, 311, 0xFF59463B);
        line(c, 85, 174, 581, 174, 2, 0xFF8A7160);
        round(c, 63, 441, 606, 457, 5, 0xFF917156);
        // Candle lantern on the sill.
        round(c, 518, 400, 546, 441, 4, 0xFF372D28);
        round(c, 522, 406, 542, 436, 2, 0xFFE9B15F);
        polygon(c, 0xFF4C3E30, 514, 401, 532, 390, 550, 401);
    }

    private void pine(Canvas c, float x, float bottom, float height, int ink) {
        polygon(c, ink, x, bottom - height, x - height * .25f, bottom - height * .42f,
                x - height * .13f, bottom - height * .42f, x - height * .35f, bottom,
                x + height * .35f, bottom, x + height * .13f, bottom - height * .42f,
                x + height * .25f, bottom - height * .42f);
    }

    private void drawCurtain(Canvas c, float x, boolean right) {
        float end = right ? x + 44 : x - 30;
        color(0xFF6B373D); path.reset(); path.moveTo(x, 161); path.lineTo(x + 34, 161);
        path.cubicTo(x + 28, 251, end, 346, x + 32, 451);
        path.lineTo(x - 20, 451); path.cubicTo(end - 10, 342, x + 10, 264, x, 161);
        path.close(); c.drawPath(path, paint);
        line(c, x + 3, 183, end, 397, 2, 0xFF8C4D50);
        line(c, x + 3, 353, x + 26, 353, 5, 0xFFB38D55);
    }

    private void drawTree(Canvas c) {
        round(c, 707, 421, 738, 478, 5, 0xFF7B583D);
        for (int i = 0; i < 6; i++) {
            float top = 175 + i * 35, bottom = top + 104, half = 32 + i * 12;
            color(i % 2 == 0 ? 0xFF285747 : 0xFF214B3E);
            path.reset(); path.moveTo(723, top);
            path.cubicTo(714, top + 35, 723 - half + 18, bottom - 15, 723 - half, bottom);
            path.quadTo(723, bottom + 12, 723 + half, bottom);
            path.cubicTo(723 + half - 18, bottom - 15, 732, top + 35, 723, top);
            path.close(); c.drawPath(path, paint);
        }
        // Star and ornaments.
        star(c, 723, 178, 14, 0xFFE6C77C);
        for (int i = 0; i < 22; i++) {
            float y = 235 + i * 9.8f;
            float x = 723 + (float) Math.sin(i * 2.33) * ((y - 180) * .28f);
            circle(c, x, y, 4.6f, i % 3 == 0 ? 0xFFE5BE78 : i % 3 == 1 ? 0xFFAA4850 : 0xFFDBD5BB);
            circle(c, x - 1, y - 1, 1.3f, 0x66FFFFFF);
        }
        round(c, 648, 447, 699, 486, 3, 0xFFAE4B51);
        rect(c, 671, 447, 677, 486, 0xFFD9B777);
        round(c, 752, 457, 796, 489, 3, 0xFFC19A5C);
        rect(c, 770, 457, 776, 489, 0xFF8D3D47);
    }

    private void star(Canvas c, float x, float y, float radius, int ink) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI / 5 - Math.PI / 2;
            float r = i % 2 == 0 ? radius : radius * .43f;
            float px = x + (float) Math.cos(angle) * r, py = y + (float) Math.sin(angle) * r;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close(); color(ink); c.drawPath(path, paint);
    }

    private void drawHearth(Canvas c) {
        // Stone chimney, mortared blocks and oak mantel.
        round(c, 867, 166, 1135, 454, 10, 0xFF655D52);
        int[] stone = {0xFF797063, 0xFF6F685B, 0xFF888071, 0xFF5F5B52};
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 4; column++) {
                float l = 871 + column * 67 - (row % 2 == 0 ? 0 : 30);
                float r = Math.min(1131, l + 62);
                if (r > 873) round(c, Math.max(871, l), 171 + row * 34, r, 200 + row * 34, 4,
                        stone[(row + column) % stone.length]);
            }
        }
        round(c, 895, 289, 1108, 447, 38, 0xFF29201D);
        round(c, 910, 303, 1093, 443, 29, 0xFF140F13);
        round(c, 841, 273, 1161, 294, 6, 0xFF634635);
        line(c, 845, 276, 1155, 276, 3, 0xFFA07A54);
        round(c, 848, 443, 1158, 464, 6, 0xFF857561);
        // Evergreen wreath above the fire.
        color(0xFF244638); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(18);
        c.drawCircle(1000, 214, 34, paint); paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 11; i++) {
            double a = i * Math.PI * 2 / 11;
            circle(c, 1000 + (float) Math.cos(a) * 34, 214 + (float) Math.sin(a) * 34, 4,
                    i % 2 == 0 ? 0xFFD8B972 : 0xFFA74449);
        }
        polygon(c, 0xFFAA464B, 1000, 245, 976, 237, 979, 258, 1000, 250, 1021, 258, 1024, 237);
        // Stockings, candles, logs.
        round(c, 876, 295, 897, 336, 5, 0xFFAD5555);
        round(c, 876, 322, 914, 339, 7, 0xFFAD5555);
        rect(c, 875, 292, 899, 301, 0xFFE4D5BC);
        round(c, 1119, 295, 1140, 336, 5, 0xFF648679);
        round(c, 1119, 322, 1157, 339, 7, 0xFF648679);
        rect(c, 1118, 292, 1142, 301, 0xFFE4D5BC);
        for (int i = 0; i < 3; i++) {
            round(c, 870 + i * 16, 245 - i * 4, 880 + i * 16, 272, 2, 0xFFD5C5A5);
            circle(c, 875 + i * 16, 241 - i * 4, 3, 0xFFFFD091);
        }
        line(c, 937, 427, 1060, 438, 15, 0xFF513225);
        line(c, 950, 439, 1072, 419, 13, 0xFF6B4330);
        round(c, 1168, 409, 1230, 476, 12, 0xFF59402D);
        for (int i = 0; i < 4; i++) line(c, 1176, 421 + i * 12, 1220, 415 + i * 12, 9, 0xFF9A7550);
    }

    private void drawSnow(Canvas c, double seconds) {
        c.save();
        c.clipRect(85, 175, 582, 436);
        c.clipOutRect(326, 170, 336, 438);
        c.clipOutRect(83, 302, 584, 311);
        for (int i = 0; i < CabinMotion.FLAKES; i++) {
            float radius = CabinMotion.snowRadius(i);
            int alpha = 100 + (int) (radius * 42);
            circle(c, 85 + CabinMotion.snowX(i, seconds) * 497,
                    175 + CabinMotion.snowY(i, seconds) * 261, radius, (alpha << 24) | 0xDFEDF1);
        }
        c.restore();
    }

    private void drawFire(Canvas c, double seconds) {
        float strength = CabinMotion.fire(seconds);
        paint.setShader(fireGlow); paint.setAlpha((int) (160 + strength * 70));
        c.drawCircle(1000, 416, 166, paint); paint.setShader(null); paint.setAlpha(255);
        for (int i = 0; i < 5; i++) {
            float x = 945 + i * 26, h = 38 + strength * 26 + (float) Math.sin(seconds * 4.1 + i * 2) * 13;
            float sway = (float) Math.sin(seconds * 3.4 + i) * 9;
            flame(c, x, 432, 25, h, sway, 0xFFF08039);
            flame(c, x + 1, 432, 15, h * .72f, sway * .6f, 0xFFFFC267);
            flame(c, x + 1, 432, 7, h * .42f, sway * .3f, 0xFFFFE5A0);
        }
        for (int i = 0; i < 5; i++) {
            float phase = (float) ((seconds * .4 + i * .21) % 1);
            circle(c, 966 + i * 19 + (float) Math.sin(seconds + i) * 7, 415 - phase * 79,
                    1.2f, (((int) ((1 - phase) * 155)) << 24) | 0xFFD191);
        }
        // Dark log edges remain in front of the flames.
        line(c, 938, 435, 1068, 437, 8, 0xFF4F2E23);
        for (int i = 0; i < 8; i++) circle(c, 951 + i * 15, 432, 1.7f, 0xFFE69C50);
    }

    private void flame(Canvas c, float x, float bottom, float half, float height, float sway, int ink) {
        color(ink); path.reset(); path.moveTo(x - half, bottom);
        path.cubicTo(x - half * 1.2f, bottom - height * .55f,
                x + sway - 10, bottom - height * .63f, x + sway, bottom - height);
        path.cubicTo(x + sway + 3, bottom - height * .48f, x + half * 1.15f, bottom - height * .45f,
                x + half, bottom);
        path.close(); c.drawPath(path, paint);
    }

    private void drawLights(Canvas c, double seconds) {
        for (int i = 0; i < 42; i++) {
            float y = 220 + i * 5.6f;
            float x = 723 + (float) Math.sin(i * .71) * ((y - 175) * .28f);
            int alpha = (int) (CabinMotion.twinkle(i, seconds) * 255);
            circle(c, x, y, 4.2f, ((alpha / 4) << 24) | 0xFFCB7D);
            circle(c, x, y, 1.9f, (alpha << 24) | 0xFFDF9D);
        }
    }
}

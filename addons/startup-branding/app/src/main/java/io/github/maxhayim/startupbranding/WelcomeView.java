package io.github.maxhayim.startupbranding;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * Draws the welcome screen at any size: full screen, or small as the live preview. A 16:9 frame holds the
 * background, the picture and the text; on other screen shapes the bars around it are black.
 */
final class WelcomeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dest = new Rect();
    private Look look = new Look();
    private Bitmap bitmap;

    WelcomeView(Context c) {
        super(c);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setTextAlign(Paint.Align.CENTER);
    }

    void show(Look l, Bitmap b) {
        look = l;
        bitmap = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF000000);
        int[] f = Fit.frame(getWidth(), getHeight());
        if (look.mode == Look.Mode.BLACK) return;
        paint.setColor(look.background());
        c.drawRect(f[0], f[1], f[2], f[3], paint);
        if (look.mode == Look.Mode.OFF) return;
        int fw = f[2] - f[0], fh = f[3] - f[1];
        c.save();
        c.clipRect(f[0], f[1], f[2], f[3]);
        if (bitmap != null) {
            // Original size is relative to a 1080p screen, so the preview pane shows it to scale.
            float density = fh / 1080f;
            int[] r = Fit.image(bitmap.getWidth(), bitmap.getHeight(), f, look.scale, look.align, density);
            dest.set(r[0], r[1], r[2], r[3]);
            c.drawBitmap(bitmap, null, dest, paint);
        }
        String line1 = look.text, line2 = look.nickname;
        if (!line1.isEmpty() || !line2.isEmpty()) {
            float big = fh * 0.075f, small = fh * 0.04f;
            float block = (line1.isEmpty() ? 0 : big * 1.2f) + (line2.isEmpty() ? 0 : small * 1.4f);
            float top;
            switch (look.place) {
                case TOP: top = f[1] + fh * 0.1f; break;
                case CENTER: top = f[1] + (fh - block) / 2f; break;
                default: top = f[3] - fh * 0.1f - block;
            }
            text.setColor(look.textColor());
            // A soft shadow keeps the words readable over a busy picture.
            text.setShadowLayer(fh * 0.01f, 0, fh * 0.003f, look.textColor() == 0xFFFFFFFF ? 0x99000000 : 0x66FFFFFF);
            float y = top;
            if (!line1.isEmpty()) {
                text.setTextSize(big);
                y += big;
                c.drawText(TextUtils.ellipsize(line1, text, fw * 0.9f, TextUtils.TruncateAt.END).toString(), f[0] + fw / 2f, y, text);
                y += big * 0.2f;
            }
            if (!line2.isEmpty()) {
                text.setTextSize(small);
                y += small * 1.2f;
                c.drawText(TextUtils.ellipsize(line2, text, fw * 0.9f, TextUtils.TruncateAt.END).toString(), f[0] + fw / 2f, y, text);
            }
        }
        c.restore();
    }
}

package io.github.gagantk.kannadacalendar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import java.util.Random;

/**
 * What makes the page look hung rather than drawn: a warm wall, a nail with the hanging cord, the spiral binding
 * across the top of the sheet, and a paper texture. Everything is painted once per layout from a few gradients
 * and one small tiled bitmap; no image files ship with the app.
 */
final class Wall {
    private Wall() {}

    /** Margins of the sheet on the wall, in dp: room above for the nail and cord. */
    static final int SIDE = 36, TOP = 54, BOTTOM = 22;

    /** The wall behind the sheet, with the nail and the cord running down to the binding. */
    static final class Back extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Back(Context c) { super(c); }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            Palette pal = Palette.current;
            paint.setShader(new LinearGradient(0, 0, 0, h, pal.wallTop, pal.wallBottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new RadialGradient(w / 2, h * 0.35f, w * 0.7f, pal.dark ? 0x10FFE7C2 : 0x22FFE7C2, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            float top = Ui.dp(getContext(), TOP), side = Ui.dp(getContext(), SIDE);
            float nx = w / 2, ny = top * 0.28f;
            // The cord from the nail to two points of the binding.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setColor(pal.dark ? 0xFF5A4A3C : 0xFF1E1510);
            float left = side + (w - 2 * side) * 0.32f, right = side + (w - 2 * side) * 0.68f;
            float by = top - Ui.dp(getContext(), 6);
            canvas.drawLine(nx, ny, left, by, paint);
            canvas.drawLine(nx, ny, right, by, paint);
            // The nail: a shadow, then a small brass head.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x55000000);
            canvas.drawCircle(nx + 3, ny + 4, Ui.dp(getContext(), 7), paint);
            paint.setShader(new RadialGradient(nx - 3, ny - 3, Ui.dp(getContext(), 8), 0xFFF2D49A, 0xFF8A6A32, Shader.TileMode.CLAMP));
            canvas.drawCircle(nx, ny, Ui.dp(getContext(), 7), paint);
            paint.setShader(null);
        }
    }

    /** The spiral binding, drawn over the top edge of the sheet. */
    static final class Rings extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF loop = new RectF();

        Rings(Context c) {
            super(c);
            setFocusable(false);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float side = Ui.dp(getContext(), SIDE), top = Ui.dp(getContext(), TOP);
            float span = w - 2 * side - Ui.dp(getContext(), 80);
            int count = Math.max(8, (int) (span / Ui.dp(getContext(), 26)));
            float step = span / (count - 1), x0 = side + Ui.dp(getContext(), 40);
            float rw = Ui.dp(getContext(), 7), above = Ui.dp(getContext(), 11), below = Ui.dp(getContext(), 12);
            for (int i = 0; i < count; i++) {
                float x = x0 + i * step;
                // The punched hole in the paper.
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Palette.current.wallBottom);
                canvas.drawOval(x - rw * 0.55f, top + below - rw * 0.5f, x + rw * 0.55f, top + below + rw * 0.5f, paint);
                // The wire loop, lit from the left.
                loop.set(x - rw / 2, top - above, x + rw / 2, top + below + rw * 0.2f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Ui.dp(getContext(), 3));
                paint.setShader(new LinearGradient(x - rw, 0, x + rw, 0, 0xFFEDEDED, 0xFF6E6E6E, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(loop, rw, rw, paint);
            }
            paint.setShader(null);
        }
    }

    /** Paper: a faint warm gradient with a tiled grain over it. */
    static Drawable paper(Context c) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Palette.current.paperTop, Palette.current.paperBottom});
        base.setCornerRadius(Ui.dp(c, 4));
        Bitmap grain = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Random random = new Random(7);
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                int a = random.nextInt(14);
                grain.setPixel(x, y, (a << 24) | Palette.current.grain);
            }
        }
        BitmapDrawable texture = new BitmapDrawable(c.getResources(), grain);
        texture.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return new LayerDrawable(new Drawable[]{base, texture});
    }
}

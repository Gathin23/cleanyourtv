package com.gathin.hindipanchang;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.LruCache;
import android.view.View;

/**
 * Renders the full-screen seasonal/festival artwork for each of the
 * 12 Hindu Lunar Months (bg_month_0 .. bg_month_11) or the aged sacred Bhojpatra
 * parchment artwork for सुविचार (bg_suvichar), paired with a calibrated center
 * vignette scrim so the center calendar and typography always remain crisp
 * while the surrounding festival artwork stays vivid.
 */
final class SacredBackdropView extends View {

    private static final int[] MONTH_DRAWABLES = {
        R.drawable.bg_month_0,  // 0: Chaitra (Marigold & Mango Leaf Toran)
        R.drawable.bg_month_1,  // 1: Vaishakha (Akshaya Tritiya Golden Kalash)
        R.drawable.bg_month_2,  // 2: Jyeshtha (Ganga Dussehra River Ghat Sunset)
        R.drawable.bg_month_3,  // 3: Ashadha (Monsoon Paddy Fields & Rain)
        R.drawable.bg_month_4,  // 4: Shravana (Lord Shiva Meditating at Sunset)
        R.drawable.bg_month_5,  // 5: Bhadrapada (Janmashtami Bansuri, Morpankh & Matki)
        R.drawable.bg_month_6,  // 6: Ashwin (Sharad Navratri / Durga Puja & Dhakis)
        R.drawable.bg_month_7,  // 7: Kartik (Floating Diya & Flower Puja Thali)
        R.drawable.bg_month_8,  // 8: Margashirsha (Gita Jayanti Krishna & Arjuna Shankh)
        R.drawable.bg_month_9,  // 9: Pausha (Makar Sankranti Kites over Heritage Rooftops)
        R.drawable.bg_month_10, // 10: Magha (Basant Panchami Goddess Saraswati & Swan)
        R.drawable.bg_month_11  // 11: Phalguna (Holi Festival of Colors)
    };

    // Months with bright/light backgrounds where the central calendar needs a slightly deeper center scrim
    private static final boolean[] IS_BRIGHT_MONTH = {
        true,  // 0: Chaitra (bright yellow-orange marigold wall)
        false, // 1: Vaishakha (deep peacock teal)
        false, // 2: Jyeshtha (warm sunset ghats)
        false, // 3: Ashadha (monsoon green)
        false, // 4: Shravana (warm sunset orange)
        true,  // 5: Bhadrapada (light cream Janmashtami backdrop)
        true,  // 6: Ashwin (bright saffron Durga Puja backdrop)
        false, // 7: Kartik (river sunset)
        true,  // 8: Margashirsha (radiant golden sky)
        true,  // 9: Pausha (bright cyan kite sky)
        true,  // 10: Magha (pale pastel yellow Saraswati backdrop)
        true   // 11: Phalguna (bright Holi sky)
    };

    private static final LruCache<Integer, Bitmap> CACHE = new LruCache<>(4);

    private final boolean isSuvicharMode;
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topHeaderScrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private int hindiMonthIndex = 7;

    SacredBackdropView(Context context, boolean isSuvicharMode) {
        super(context);
        this.isSuvicharMode = isSuvicharMode;
    }

    void setHindiMonth(int monthIndex) {
        int idx = ((monthIndex % 12) + 12) % 12;
        if (this.hindiMonthIndex != idx) {
            this.hindiMonthIndex = idx;
            invalidate();
        }
    }

    private Bitmap getBitmap(int resId) {
        Bitmap cached = CACHE.get(resId);
        if (cached != null && !cached.isRecycled()) return cached;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap decoded = BitmapFactory.decodeResource(getResources(), resId, opts);
            if (decoded != null) {
                CACHE.put(resId, decoded);
            }
            return decoded;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int resId = isSuvicharMode ? R.drawable.bg_suvichar : MONTH_DRAWABLES[hindiMonthIndex];
        Bitmap bmp = getBitmap(resId);

        if (bmp != null) {
            float scale = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight());
            float dx = (w - bmp.getWidth() * scale) * 0.5f;
            float dy = (h - bmp.getHeight() * scale) * 0.5f;
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(dx, dy);
            canvas.drawBitmap(bmp, matrix, bmpPaint);
        } else {
            canvas.drawColor(isSuvicharMode ? 0xFF1E140B : 0xFF0E0907);
        }

        if (isSuvicharMode) {
            // Subtle warm center glaze that leaves the ornate lotus manuscript border 100% bright & untouched
            scrimPaint.setShader(new RadialGradient(
                    w * 0.5f, h * 0.5f, Math.max(w, h) * 0.42f,
                    new int[]{0x661C0E04, 0x40180B03, 0x00000000},
                    new float[]{0f, 0.65f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, scrimPaint);
        } else {
            boolean bright = IS_BRIGHT_MONTH[hindiMonthIndex];
            // Soft top-header gradient so the Gregorian & Vikram Samvat header text is always legible
            topHeaderScrim.setShader(new LinearGradient(
                    0, 0, 0, h * 0.24f,
                    bright ? 0x8808060A : 0x5508060A,
                    0x00000000,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h * 0.24f, topHeaderScrim);

            // Feathered center vignette behind the Moon Orbs that leaves the outer artwork vivid
            scrimPaint.setShader(new RadialGradient(
                    w * 0.5f, h * 0.46f, Math.max(w, h) * 0.40f,
                    bright
                            ? new int[]{0x9E0A080E, 0x660A080E, 0x00000000}
                            : new int[]{0x70060508, 0x3C060508, 0x00000000},
                    new float[]{0f, 0.64f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawRect(0, 0, w, h, scrimPaint);
        }
    }
}

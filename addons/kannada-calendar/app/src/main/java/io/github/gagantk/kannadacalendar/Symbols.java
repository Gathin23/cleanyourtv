package io.github.gagantk.kannadacalendar;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The marks a printed Karnataka calendar puts in a day's square for its main festivals: a Ganesha for Sankashta
 * Chaturthi and Ganesha Chaturthi and the yellow-and-red Kannada flag for Rajyotsava, both drawn here, and a symbol from the
 * TV's own emoji font for Deepavali, Sankranti, Holi and the national days. At most two per day.
 */
final class Symbols {
    static final String GANESHA = "ganesha", KARNATAKA = "karnataka", LAMP = "🪔";

    private static final Object[][] RULES = {
            // Sankashta Chaturthi each month and Ganesha Chaturthi once a year; not the monthly Vinayaki Chaturthi,
            // nor a Ganapati temple's car festival.
            {Pattern.compile(".*\\b(sankashta? chaturthi|ganesha? chaturthi)\\b.*"), GANESHA},
            {Pattern.compile(".*\\b(deepavali|diwali|naraka chaturdashi|bali padyami|balipadyami)\\b.*"), LAMP},
            {Pattern.compile(".*\\b(makara sankranti|sankranti|sankramana)\\b.*"), "☀️"},
            {Pattern.compile(".*\\b(holi|kamana habba)\\b.*"), "🎨"},
            {Pattern.compile(".*\\b(kannada rajyotsava)\\b.*"), KARNATAKA},
            {Pattern.compile(".*\\b(independence day|republic day)\\b.*"), "🇮🇳"},
    };

    private Symbols() {}

    /** The marks for a day, most important first. */
    static List<String> of(Day day) {
        List<String> out = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        for (String f : day.festivals) all.append(f.toLowerCase(Locale.ROOT)).append(" | ");
        String text = all.toString();
        for (Object[] rule : RULES) {
            if (out.size() == 2) break;
            if (((Pattern) rule[0]).matcher(text).matches() && !out.contains((String) rule[1])) out.add((String) rule[1]);
        }
        return out;
    }

    /** The yellow-over-red Kannada flag, {@code w} wide, centred on (cx, cy), with a thin outline. */
    static void karnataka(Canvas canvas, float cx, float cy, float w, int outline) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float h = w * 0.66f, left = cx - w / 2, top = cy - h / 2;
        p.setColor(0xFFF5C518);
        canvas.drawRect(left, top, left + w, cy, p);
        p.setColor(0xFFD32F2F);
        canvas.drawRect(left, cy, left + w, top + h, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, w / 16f));
        p.setColor(outline);
        canvas.drawRect(left, top, left + w, top + h, p);
    }

    /**
     * An oil lamp (diya), {@code w} wide, centred on (cx, cy): a shallow bowl with a flame over its spout. For TVs
     * whose emoji font has no 🪔 (Android 9 and older).
     */
    static void lamp(Canvas canvas, float cx, float cy, float w, int bowl, int flame) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = w / 2;
        p.setColor(bowl);
        canvas.drawArc(new RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.75f), 0, 180, true, p);
        Path f = new Path();
        float fx = cx + r * 0.15f, base = cy + r * 0.14f;
        f.moveTo(fx, base - r * 1.05f);
        f.quadTo(fx + r * 0.38f, base - r * 0.25f, fx, base);
        f.quadTo(fx - r * 0.38f, base - r * 0.25f, fx, base - r * 1.05f);
        f.close();
        p.setColor(flame);
        canvas.drawPath(f, p);
    }

    /**
     * A small Ganesha: crown, head, the two broad ears, a trunk curling to his left and a red tilaka, in a circle
     * of size {@code size} centred on (cx, cy). Drawn from shapes, so it is sharp at any size and needs no art file.
     */
    static void ganesha(Canvas canvas, float cx, float cy, float size, int body, int accent, int eyes) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(body);
        // Ears.
        canvas.drawOval(new RectF(cx - r, cy - r * 0.55f, cx - r * 0.25f, cy + r * 0.45f), p);
        canvas.drawOval(new RectF(cx + r * 0.25f, cy - r * 0.55f, cx + r, cy + r * 0.45f), p);
        // Head.
        canvas.drawCircle(cx, cy - r * 0.12f, r * 0.45f, p);
        // Crown.
        Path crown = new Path();
        crown.moveTo(cx - r * 0.32f, cy - r * 0.42f);
        crown.lineTo(cx, cy - r * 1.02f);
        crown.lineTo(cx + r * 0.32f, cy - r * 0.42f);
        crown.close();
        p.setColor(accent);
        canvas.drawPath(crown, p);
        // Trunk: down from the face, curling to one side.
        Path trunk = new Path();
        trunk.moveTo(cx, cy + r * 0.15f);
        trunk.cubicTo(cx - r * 0.05f, cy + r * 0.6f, cx - r * 0.35f, cy + r * 0.85f, cx - r * 0.1f, cy + r * 0.95f);
        trunk.quadTo(cx + r * 0.15f, cy + r * 1.0f, cx + r * 0.12f, cy + r * 0.78f);
        p.setColor(body);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(r * 0.26f);
        canvas.drawPath(trunk, p);
        // Eyes and tilaka.
        p.setStyle(Paint.Style.FILL);
        p.setColor(eyes);
        canvas.drawCircle(cx - r * 0.17f, cy - r * 0.12f, r * 0.06f, p);
        canvas.drawCircle(cx + r * 0.17f, cy - r * 0.12f, r * 0.06f, p);
        p.setColor(accent);
        canvas.drawRect(cx - r * 0.04f, cy - r * 0.45f, cx + r * 0.04f, cy - r * 0.25f, p);
    }
}

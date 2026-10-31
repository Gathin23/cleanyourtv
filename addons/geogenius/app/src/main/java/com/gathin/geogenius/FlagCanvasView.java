package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * High-precision 200-Country Vector Flag Renderer with authentic emblems
 * (South Korea Taegeuk & 4 Ba-Gwa Trigrams, Mexican Eagle on Cactus Crest,
 * 11-point Canadian Maple Leaf, 24-spoke Ashoka Chakra, 50-star USA canton,
 * Union Jack, South African Y-pall, Nepal double-pennant, etc.).
 */
final class FlagCanvasView extends View {

    static final int STAMP_NONE = 0;
    static final int STAMP_CORRECT = 1;
    static final int STAMP_WRONG = 2;
    static final int STAMP_LOCKED = 3;

    private GeoData.Country country;
    private boolean selectableCard = false;
    private int stampState = STAMP_NONE;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stampPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF tmpRect = new RectF();
    private final Path clipPath = new Path();
    private final Path shapePath = new Path();

    FlagCanvasView(Context context, boolean selectableCard) {
        super(context);
        this.selectableCard = selectableCard;
        if (selectableCard) {
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
        }
        borderPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    void bind(GeoData.Country c) {
        this.country = c;
        this.stampState = STAMP_NONE;
        invalidate();
    }

    GeoData.Country getCountry() {
        return country;
    }

    void setStampState(int state) {
        this.stampState = state;
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (selectableCard) {
            animate().scaleX(gainFocus ? 1.07f : 1.0f)
                    .scaleY(gainFocus ? 1.07f : 1.0f)
                    .setDuration(130)
                    .start();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (country == null) return;
        float d = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = selectableCard ? 6f * d : 3f * d;
        cardRect.set(pad, pad, w - pad, h - pad);
        float radius = 11f * d;

        boolean focused = selectableCard && isFocused();
        if (focused) {
            glowPaint.setColor(0x44FBBF24);
            tmpRect.set(cardRect.left - 3f * d, cardRect.top - 3f * d, cardRect.right + 3f * d, cardRect.bottom + 3f * d);
            canvas.drawRoundRect(tmpRect, radius + 3f * d, radius + 3f * d, glowPaint);
        }

        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        drawFlagPattern(canvas, cardRect, country, d);

        // Subtle diagonal fabric sheen
        sheenPaint.setShader(new LinearGradient(
                cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                new int[]{0x1EFFFFFF, 0x00FFFFFF, 0x1E000000},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(cardRect, sheenPaint);
        canvas.restoreToCount(save);

        // Border ring
        if (stampState == STAMP_CORRECT) {
            borderPaint.setStrokeWidth(3f * d);
            borderPaint.setColor(0xFF10B981);
        } else if (stampState == STAMP_WRONG) {
            borderPaint.setStrokeWidth(3f * d);
            borderPaint.setColor(0xFFEF4444);
        } else if (stampState == STAMP_LOCKED || focused) {
            borderPaint.setStrokeWidth(2.8f * d);
            borderPaint.setColor(0xFFFBBF24);
        } else {
            borderPaint.setStrokeWidth(1.3f * d);
            borderPaint.setColor(0x55FFFFFF);
        }
        canvas.drawRoundRect(cardRect, radius, radius, borderPaint);

        // Circular ✓ or ✕ Stamp Badge
        if (stampState == STAMP_CORRECT || stampState == STAMP_WRONG) {
            float sr = 16f * d;
            float scx = cardRect.centerX();
            float scy = cardRect.centerY();
            stampPaint.setStyle(Paint.Style.FILL);
            stampPaint.setColor(stampState == STAMP_CORRECT ? 0xEE059669 : 0xEEDC2626);
            canvas.drawCircle(scx, scy, sr, stampPaint);

            stampPaint.setStyle(Paint.Style.STROKE);
            stampPaint.setStrokeWidth(2f * d);
            stampPaint.setColor(Color.WHITE);
            canvas.drawCircle(scx, scy, sr, stampPaint);

            stampPaint.setStrokeWidth(2.6f * d);
            stampPaint.setStrokeCap(Paint.Cap.ROUND);
            stampPaint.setStrokeJoin(Paint.Join.ROUND);
            if (stampState == STAMP_CORRECT) {
                canvas.drawLine(scx - 6f * d, scy + 0.5f * d, scx - 1.5f * d, scy + 5f * d, stampPaint);
                canvas.drawLine(scx - 1.5f * d, scy + 5f * d, scx + 7f * d, scy - 5f * d, stampPaint);
            } else {
                float k = 5f * d;
                canvas.drawLine(scx - k, scy - k, scx + k, scy + k, stampPaint);
                canvas.drawLine(scx + k, scy - k, scx - k, scy + k, stampPaint);
            }
        }
    }

    private void drawFlagPattern(Canvas canvas, RectF r, GeoData.Country c, float d) {
        float fw = r.width();
        float fh = r.height();
        float cx = r.centerX();
        float cy = r.centerY();
        paint.setStyle(Paint.Style.FILL);

        switch (c.pattern) {
            case GeoData.PAT_H3: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, r.top + fh / 3f + 1, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh / 3f, r.right, r.top + 2f * fh / 3f + 1, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, r.top + 2f * fh / 3f, r.right, r.bottom, paint);
                if ("UZ".equals(c.code)) {
                    paint.setColor(0xFFCE1126);
                    canvas.drawRect(r.left, r.top + fh / 3f - 1.5f * d, r.right, r.top + fh / 3f + 1.5f * d, paint);
                    canvas.drawRect(r.left, r.top + 2f * fh / 3f - 1.5f * d, r.right, r.top + 2f * fh / 3f + 1.5f * d, paint);
                }
                break;
            }
            case GeoData.PAT_SPAIN: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh * 0.25f, r.right, r.top + fh * 0.75f, paint);
                break;
            }
            case GeoData.PAT_COLOMBIA: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, cy + 1, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, cy, r.right, cy + fh * 0.25f + 1, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, cy + fh * 0.25f, r.right, r.bottom, paint);
                break;
            }
            case GeoData.PAT_THAI: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh / 6f, r.right, r.bottom - fh / 6f, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, r.top + fh / 3f, r.right, r.bottom - fh / 3f, paint);
                break;
            }
            case GeoData.PAT_LATVIA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh * 0.28f, r.right, r.bottom - fh * 0.28f, paint);
                break;
            }
            case GeoData.PAT_V3: {
                if ("CA".equals(c.code)) {
                    paint.setColor(c.c1);
                    canvas.drawRect(r, paint);
                    paint.setColor(c.c2);
                    canvas.drawRect(r.left + fw * 0.25f, r.top, r.right - fw * 0.25f, r.bottom, paint);
                } else {
                    paint.setColor(c.c1);
                    canvas.drawRect(r.left, r.top, r.left + fw / 3f + 1, r.bottom, paint);
                    paint.setColor(c.c2);
                    canvas.drawRect(r.left + fw / 3f, r.top, r.left + 2f * fw / 3f + 1, r.bottom, paint);
                    paint.setColor(c.c3);
                    canvas.drawRect(r.left + 2f * fw / 3f, r.top, r.right, r.bottom, paint);
                }
                break;
            }
            case GeoData.PAT_PORTUGAL: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.left + fw * 0.4f + 1, r.bottom, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left + fw * 0.4f, r.top, r.right, r.bottom, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * d);
                paint.setColor(c.c3);
                canvas.drawCircle(r.left + fw * 0.4f, cy, fh * 0.21f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.PAT_H2: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, cy + 1, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, cy, r.right, r.bottom, paint);
                if ("KI".equals(c.code)) {
                    // Kiribati blue/white ocean waves in lower half
                    paint.setColor(Color.WHITE);
                    for (int i = 0; i < 3; i++) {
                        float wy = cy + (i * 2 + 1) * (fh * 0.08f);
                        canvas.drawRect(r.left, wy, r.right, wy + fh * 0.04f, paint);
                    }
                }
                break;
            }
            case GeoData.PAT_V2: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, cx + 1, r.bottom, paint);
                paint.setColor(c.c2);
                canvas.drawRect(cx, r.top, r.right, r.bottom, paint);
                break;
            }
            case GeoData.PAT_SOLID: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                break;
            }
            case GeoData.PAT_CHILE: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, cy + 1, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, cy, r.right, r.bottom, paint);
                paint.setColor(c.c3);
                float cw = "TW".equals(c.code) || "WS".equals(c.code) ? fw * 0.48f : fw * 0.35f;
                canvas.drawRect(r.left, r.top, r.left + cw, cy, paint);
                if ("CL".equals(c.code)) {
                    drawFivePointStar(canvas, r.left + cw * 0.5f, r.top + fh * 0.25f, fh * 0.13f, Color.WHITE);
                }
                break;
            }
            case GeoData.PAT_PAKISTAN: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, r.top, r.left + fw * 0.25f, r.bottom, paint);
                break;
            }
            case GeoData.PAT_NORDIC: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                float crossX = r.left + fw * 0.36f;
                float barW = fh * 0.20f;
                paint.setColor(c.c2);
                canvas.drawRect(crossX - barW / 2f, r.top, crossX + barW / 2f, r.bottom, paint);
                canvas.drawRect(r.left, cy - barW / 2f, r.right, cy + barW / 2f, paint);
                if (c.c3 != 0) {
                    float innerW = barW * 0.5f;
                    paint.setColor(c.c3);
                    canvas.drawRect(crossX - innerW / 2f, r.top, crossX + innerW / 2f, r.bottom, paint);
                    canvas.drawRect(r.left, cy - innerW / 2f, r.right, cy + innerW / 2f, paint);
                }
                break;
            }
            case GeoData.PAT_CENTER_DISK: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                float shift = "BD".equals(c.code) || "PW".equals(c.code) ? -fw * 0.05f : 0f;
                canvas.drawCircle(cx + shift, cy, fh * 0.28f, paint);
                break;
            }
            case GeoData.PAT_KOREA: {
                // Authentic South Korea Flag (Taegeukgi):
                // White background + tilted Red/Blue swirling Taegeuk + 4 distinct 3-bar black trigrams!
                paint.setColor(Color.WHITE);
                canvas.drawRect(r, paint);
                float diskR = fh * 0.25f;

                int skSave = canvas.save();
                canvas.rotate(-33.7f, cx, cy);
                tmpRect.set(cx - diskR, cy - diskR, cx + diskR, cy + diskR);
                paint.setColor(0xFFCD2E3A); // Red top half
                canvas.drawArc(tmpRect, 180, 180, true, paint);
                paint.setColor(0xFF0047A0); // Blue bottom half
                canvas.drawArc(tmpRect, 0, 180, true, paint);
                // Interlocking S-swirl lobes
                paint.setColor(0xFFCD2E3A);
                canvas.drawCircle(cx - diskR * 0.5f, cy, diskR * 0.5f, paint);
                paint.setColor(0xFF0047A0);
                canvas.drawCircle(cx + diskR * 0.5f, cy, diskR * 0.5f, paint);
                canvas.restoreToCount(skSave);

                // Draw the 4 authentic Ba-Gwa Trigrams (3 parallel bars each):
                // Top-Left: Geon (3 solid), Bottom-Right: Gon (3 broken),
                // Top-Right: Gam (broken, solid, broken), Bottom-Left: Li (solid, broken, solid)
                drawKoreanTrigram(canvas, cx - fw * 0.25f, cy - fh * 0.23f, -56.3f, false, false, false, fh);
                drawKoreanTrigram(canvas, cx + fw * 0.25f, cy + fh * 0.23f, -56.3f, true, true, true, fh);
                drawKoreanTrigram(canvas, cx + fw * 0.25f, cy - fh * 0.23f, 56.3f, true, false, true, fh);
                drawKoreanTrigram(canvas, cx - fw * 0.25f, cy + fh * 0.23f, 56.3f, false, true, false, fh);
                break;
            }
            case GeoData.PAT_NORTH_KOREA: {
                paint.setColor(c.c1); // Blue top & bottom stripes
                canvas.drawRect(r, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, r.top + fh * 0.16f, r.right, r.bottom - fh * 0.16f, paint);
                paint.setColor(c.c2); // Wide red center panel
                canvas.drawRect(r.left, r.top + fh * 0.20f, r.right, r.bottom - fh * 0.20f, paint);
                float kx = r.left + fw * 0.33f;
                paint.setColor(Color.WHITE);
                canvas.drawCircle(kx, cy, fh * 0.21f, paint);
                drawFivePointStar(canvas, kx, cy, fh * 0.19f, c.c2);
                break;
            }
            case GeoData.PAT_USA: {
                paint.setColor(Color.WHITE);
                canvas.drawRect(r, paint);
                paint.setColor(c.c1);
                int stripes = "LR".equals(c.code) ? 11 : ("MY".equals(c.code) ? 14 : 13);
                float sh = fh / stripes;
                for (int i = 0; i < stripes; i += 2) {
                    canvas.drawRect(r.left, r.top + i * sh, r.right, r.top + (i + 1) * sh, paint);
                }
                paint.setColor(c.c3);
                float cw = fw * 0.42f;
                float ch = sh * 7f;
                canvas.drawRect(r.left, r.top, r.left + cw, r.top + ch, paint);
                if ("LR".equals(c.code)) {
                    drawFivePointStar(canvas, r.left + cw * 0.5f, r.top + ch * 0.5f, ch * 0.28f, Color.WHITE);
                } else if (!"MY".equals(c.code)) {
                    // 50 staggered white stars (9 rows: 6-5-6-5-6-5-6-5-6)
                    paint.setColor(Color.WHITE);
                    for (int row = 0; row < 9; row++) {
                        int cols = (row % 2 == 0) ? 6 : 5;
                        float offset = (row % 2 == 0) ? 0.55f : 1.05f;
                        for (int col = 0; col < cols; col++) {
                            float sx = r.left + (col + offset) * (cw / 6.2f);
                            float sy = r.top + (row + 0.8f) * (ch / 9.6f);
                            canvas.drawCircle(sx, sy, Math.max(1.1f, fh * 0.014f), paint);
                        }
                    }
                }
                break;
            }
            case GeoData.PAT_UK: {
                drawUnionJack(canvas, r, c.c1, c.c3);
                break;
            }
            case GeoData.PAT_AUSTRALIA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                // Union Jack in top-left canton
                tmpRect.set(r.left, r.top, cx, cy);
                int auSave = canvas.save();
                canvas.clipRect(tmpRect);
                drawUnionJack(canvas, tmpRect, 0xFF012169, 0xFFC8102E);
                canvas.restoreToCount(auSave);
                if ("AU".equals(c.code)) {
                    // Commonwealth Star under canton + Southern Cross on right
                    drawFivePointStar(canvas, r.left + fw * 0.25f, r.top + fh * 0.74f, fh * 0.09f, Color.WHITE);
                    float rx = r.left + fw * 0.75f;
                    drawFivePointStar(canvas, rx, r.top + fh * 0.22f, fh * 0.055f, Color.WHITE);
                    drawFivePointStar(canvas, rx, r.top + fh * 0.78f, fh * 0.055f, Color.WHITE);
                    drawFivePointStar(canvas, rx - fw * 0.11f, cy - fh * 0.04f, fh * 0.055f, Color.WHITE);
                    drawFivePointStar(canvas, rx + fw * 0.10f, cy - fh * 0.08f, fh * 0.055f, Color.WHITE);
                    drawFivePointStar(canvas, rx + fw * 0.04f, cy + fh * 0.08f, fh * 0.035f, Color.WHITE);
                }
                break;
            }
            case GeoData.PAT_BRAZIL: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                shapePath.moveTo(cx, r.top + fh * 0.11f);
                shapePath.lineTo(r.right - fw * 0.09f, cy);
                shapePath.lineTo(cx, r.bottom - fh * 0.11f);
                shapePath.lineTo(r.left + fw * 0.09f, cy);
                shapePath.close();
                paint.setColor(c.c2);
                canvas.drawPath(shapePath, paint);
                paint.setColor(c.c3);
                canvas.drawCircle(cx, cy, fh * 0.23f, paint);
                // Curved white banner & stars
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(fh * 0.045f);
                paint.setColor(Color.WHITE);
                tmpRect.set(cx - fh * 0.26f, cy - fh * 0.08f, cx + fh * 0.26f, cy + fh * 0.36f);
                canvas.drawArc(tmpRect, 200, 140, false, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.PAT_SWISS: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                if ("GE".equals(c.code)) {
                    float bw = fh * 0.18f;
                    canvas.drawRect(cx - bw / 2f, r.top, cx + bw / 2f, r.bottom, paint);
                    canvas.drawRect(r.left, cy - bw / 2f, r.right, cy + bw / 2f, paint);
                } else {
                    float armLen = fh * 0.56f;
                    float armThk = fh * 0.18f;
                    canvas.drawRect(cx - armLen / 2f, cy - armThk / 2f, cx + armLen / 2f, cy + armThk / 2f, paint);
                    canvas.drawRect(cx - armThk / 2f, cy - armLen / 2f, cx + armThk / 2f, cy + armLen / 2f, paint);
                }
                break;
            }
            case GeoData.PAT_GREECE: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(Color.WHITE);
                float sh = fh / 9f;
                for (int i = 1; i < 9; i += 2) {
                    canvas.drawRect(r.left, r.top + i * sh, r.right, r.top + (i + 1) * sh, paint);
                }
                float cSize = sh * 5f;
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.left + cSize, r.top + cSize, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left + cSize * 0.4f, r.top, r.left + cSize * 0.6f, r.top + cSize, paint);
                canvas.drawRect(r.left, r.top + cSize * 0.4f, r.left + cSize, r.top + cSize * 0.6f, paint);
                break;
            }
            case GeoData.PAT_URUGUAY: {
                paint.setColor(Color.WHITE);
                canvas.drawRect(r, paint);
                paint.setColor(c.c1);
                float sh = fh / 9f;
                for (int i = 1; i < 9; i += 2) {
                    canvas.drawRect(r.left, r.top + i * sh, r.right, r.top + (i + 1) * sh, paint);
                }
                float cSize = sh * 5f;
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, r.top, r.left + cSize, r.top + cSize, paint);
                drawSunWithRays(canvas, r.left + cSize * 0.5f, r.top + cSize * 0.5f, cSize * 0.26f, c.c3);
                break;
            }
            case GeoData.PAT_CUBA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(Color.WHITE);
                float sh = fh / 5f;
                canvas.drawRect(r.left, r.top + sh, r.right, r.top + 2 * sh, paint);
                canvas.drawRect(r.left, r.top + 3 * sh, r.right, r.top + 4 * sh, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(r.left + fw * 0.44f, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                paint.setColor(c.c3);
                canvas.drawPath(shapePath, paint);
                drawFivePointStar(canvas, r.left + fw * 0.15f, cy, fh * 0.13f, Color.WHITE);
                break;
            }
            case GeoData.PAT_PANAMA: {
                paint.setColor(Color.WHITE);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2); // Red top-right
                canvas.drawRect(cx, r.top, r.right, cy, paint);
                paint.setColor(c.c3); // Blue bottom-left
                canvas.drawRect(r.left, cy, cx, r.bottom, paint);
                drawFivePointStar(canvas, r.left + fw * 0.25f, r.top + fh * 0.25f, fh * 0.12f, c.c3);
                drawFivePointStar(canvas, r.left + fw * 0.75f, r.top + fh * 0.75f, fh * 0.12f, c.c2);
                break;
            }
            case GeoData.PAT_LEFT_TRI: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, cy + 1, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, cy, r.right, r.bottom, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(r.left + fw * 0.46f, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                paint.setColor(c.c3);
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_LEFT_TRI_H3: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, r.top + fh / 3f + 1, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh / 3f, r.right, r.top + 2f * fh / 3f + 1, paint);
                paint.setColor("BS".equals(c.code) ? c.c1 : c.c3);
                canvas.drawRect(r.left, r.top + 2f * fh / 3f, r.right, r.bottom, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(r.left + fw * 0.44f, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                paint.setColor("BS".equals(c.code) || "KW".equals(c.code) ? Color.BLACK
                        : ("SD".equals(c.code) ? 0xFF007229 : ("SS".equals(c.code) ? 0xFF0F47AF : 0xFFCE1126)));
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_LEFT_TRI_H4: {
                int[] cols = {0xFFFFD100, 0xFFFFFFFF, 0xFFCE1126, 0xFF3A75C4};
                for (int i = 0; i < 4; i++) {
                    paint.setColor(cols[i]);
                    canvas.drawRect(r.left, r.top + i * (fh / 4f), r.right, r.top + (i + 1) * (fh / 4f) + 1, paint);
                }
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(r.left + fw * 0.45f, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                paint.setColor(0xFF3D8E33);
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_SALTIRE: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(cx, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                shapePath.moveTo(r.right, r.top);
                shapePath.lineTo(cx, cy);
                shapePath.lineTo(r.right, r.bottom);
                shapePath.close();
                paint.setColor(c.c3);
                canvas.drawPath(shapePath, paint);
                paint.setColor(c.c2);
                paint.setStrokeWidth(fh * 0.14f);
                canvas.drawLine(r.left, r.top, r.right, r.bottom, paint);
                canvas.drawLine(r.right, r.top, r.left, r.bottom, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.PAT_SALTIRE_DISK: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(cx, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                shapePath.moveTo(r.right, r.top);
                shapePath.lineTo(cx, cy);
                shapePath.lineTo(r.right, r.bottom);
                shapePath.close();
                paint.setColor(c.c2);
                canvas.drawPath(shapePath, paint);
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(fh * 0.14f);
                canvas.drawLine(r.left, r.top, r.right, r.bottom, paint);
                canvas.drawLine(r.right, r.top, r.left, r.bottom, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, fh * 0.25f, paint);
                break;
            }
            case GeoData.PAT_SOUTH_AFRICA: {
                paint.setColor(c.c2); // Red top
                canvas.drawRect(r.left, r.top, r.right, cy, paint);
                paint.setColor(c.c3); // Blue/black bottom
                canvas.drawRect(r.left, cy, r.right, r.bottom, paint);
                // White fimbriation + Green Y-pall
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(fh * 0.28f);
                canvas.drawLine(r.left, r.top, cx, cy, paint);
                canvas.drawLine(r.left, r.bottom, cx, cy, paint);
                canvas.drawLine(cx, cy, r.right, cy, paint);
                paint.setColor(c.c1); // Green Y
                paint.setStrokeWidth(fh * 0.18f);
                canvas.drawLine(r.left, r.top, cx, cy, paint);
                canvas.drawLine(r.left, r.bottom, cx, cy, paint);
                canvas.drawLine(cx, cy, r.right, cy, paint);
                // Black left triangle with gold border
                shapePath.reset();
                shapePath.moveTo(r.left, r.top + fh * 0.15f);
                shapePath.lineTo(r.left + fw * 0.32f, cy);
                shapePath.lineTo(r.left, r.bottom - fh * 0.15f);
                shapePath.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.BLACK);
                canvas.drawPath(shapePath, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(fh * 0.05f);
                paint.setColor(0xFFFFB81C);
                canvas.drawPath(shapePath, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.PAT_KENYA: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, r.top + fh * 0.30f, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, r.top + fh * 0.30f, r.right, r.bottom - fh * 0.30f, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh * 0.35f, r.right, r.bottom - fh * 0.35f, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, r.bottom - fh * 0.30f, r.right, r.bottom, paint);
                break;
            }
            case GeoData.PAT_MADAGASCAR: {
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top, r.right, cy + 1, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, cy, r.right, r.bottom, paint);
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.left + fw * 0.33f, r.bottom, paint);
                break;
            }
            case GeoData.PAT_UAE: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, r.top + fh / 3f + 1, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh / 3f, r.right, r.top + 2f * fh / 3f + 1, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, r.top + 2f * fh / 3f, r.right, r.bottom, paint);
                paint.setColor(0xFFFF0000);
                canvas.drawRect(r.left, r.top, r.left + fw * 0.26f, r.bottom, paint);
                break;
            }
            case GeoData.PAT_NEPAL: {
                // Dark navy studio background with Nepal's iconic double-pennant crimson & blue flag
                paint.setColor(0xFF0F1E36);
                canvas.drawRect(r, paint);
                float nl = r.left + fw * 0.28f;
                float nr = r.left + fw * 0.72f;
                shapePath.reset();
                shapePath.moveTo(nl, r.top + fh * 0.06f);
                shapePath.lineTo(nr, cy - fh * 0.02f);
                shapePath.lineTo(nl + fw * 0.14f, cy - fh * 0.02f);
                shapePath.lineTo(nr, r.bottom - fh * 0.06f);
                shapePath.lineTo(nl, r.bottom - fh * 0.06f);
                shapePath.close();
                paint.setColor(c.c1);
                canvas.drawPath(shapePath, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.4f * d);
                paint.setColor(c.c2);
                canvas.drawPath(shapePath, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(nl + fw * 0.12f, r.top + fh * 0.32f, fh * 0.07f, paint);
                canvas.drawCircle(nl + fw * 0.14f, r.top + fh * 0.72f, fh * 0.08f, paint);
                break;
            }
            case GeoData.PAT_SRI_LANKA: {
                paint.setColor(c.c2); // Gold border
                canvas.drawRect(r, paint);
                float pad = fh * 0.07f;
                paint.setColor(c.c3); // Teal left stripe
                canvas.drawRect(r.left + pad, r.top + pad, r.left + fw * 0.19f, r.bottom - pad, paint);
                paint.setColor(0xFFFF7722); // Orange stripe
                canvas.drawRect(r.left + fw * 0.19f, r.top + pad, r.left + fw * 0.31f, r.bottom - pad, paint);
                paint.setColor(c.c1); // Maroon main panel
                canvas.drawRect(r.left + fw * 0.34f, r.top + pad, r.right - pad, r.bottom - pad, paint);
                break;
            }
            case GeoData.PAT_ISRAEL: {
                paint.setColor(Color.WHITE);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh * 0.10f, r.right, r.top + fh * 0.25f, paint);
                canvas.drawRect(r.left, r.bottom - fh * 0.25f, r.right, r.bottom - fh * 0.10f, paint);
                break;
            }
            case GeoData.PAT_QATAR: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(Color.WHITE);
                float sw = fw * 0.30f;
                canvas.drawRect(r.left, r.top, r.left + sw, r.bottom, paint);
                shapePath.reset();
                int teeth = "QA".equals(c.code) ? 9 : 5;
                float th = fh / teeth;
                for (int i = 0; i < teeth; i++) {
                    shapePath.moveTo(r.left + sw, r.top + i * th);
                    shapePath.lineTo(r.left + sw + fw * 0.07f, r.top + (i + 0.5f) * th);
                    shapePath.lineTo(r.left + sw, r.top + (i + 1) * th);
                }
                shapePath.close();
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_DIAG_BAND: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                if (!"TT".equals(c.code) && !"BN".equals(c.code)) {
                    shapePath.reset();
                    shapePath.moveTo(r.right, r.top);
                    shapePath.lineTo(r.right, r.bottom);
                    shapePath.lineTo(r.left, r.bottom);
                    shapePath.close();
                    paint.setColor(c.c3);
                    canvas.drawPath(shapePath, paint);
                }
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(fh * 0.24f);
                if ("TT".equals(c.code) || "BN".equals(c.code)) {
                    canvas.drawLine(r.left, r.top, r.right, r.bottom, paint);
                    paint.setColor(c.c2);
                    paint.setStrokeWidth(fh * 0.15f);
                    canvas.drawLine(r.left, r.top, r.right, r.bottom, paint);
                } else {
                    paint.setColor("TZ".equals(c.code) || "KN".equals(c.code) || "CD".equals(c.code) ? 0xFFFCD116 : Color.WHITE);
                    canvas.drawLine(r.left, r.bottom, r.right, r.top, paint);
                    paint.setColor(c.c2);
                    paint.setStrokeWidth(fh * 0.15f);
                    canvas.drawLine(r.left, r.bottom, r.right, r.top, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.PAT_DIAG_SPLIT: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                if ("PG".equals(c.code)) {
                    shapePath.moveTo(r.left, r.top);
                    shapePath.lineTo(r.left, r.bottom);
                    shapePath.lineTo(r.right, r.bottom);
                } else {
                    shapePath.moveTo(r.right, r.top);
                    shapePath.lineTo(r.right, r.bottom);
                    shapePath.lineTo(r.left, r.bottom);
                }
                shapePath.close();
                paint.setColor(c.c2);
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_DOMINICAN:
            case GeoData.PAT_CROSS_CENTER: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                if (c.pattern == GeoData.PAT_DOMINICAN) {
                    paint.setColor(c.c2);
                    canvas.drawRect(cx, r.top, r.right, cy, paint);
                    canvas.drawRect(r.left, cy, cx, r.bottom, paint);
                }
                paint.setColor(c.c3);
                float bw = fh * 0.18f;
                canvas.drawRect(cx - bw / 2f, r.top, cx + bw / 2f, r.bottom, paint);
                canvas.drawRect(r.left, cy - bw / 2f, r.right, cy + bw / 2f, paint);
                break;
            }
            case GeoData.PAT_GUYANA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(r.right, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                paint.setColor(c.c2);
                canvas.drawPath(shapePath, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(cx, cy);
                shapePath.lineTo(r.left, r.bottom);
                shapePath.close();
                paint.setColor(c.c3);
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_SURINAME: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, r.top + fh * 0.2f, r.right, r.bottom - fh * 0.2f, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh * 0.3f, r.right, r.bottom - fh * 0.3f, paint);
                break;
            }
            case GeoData.PAT_ANTIGUA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                shapePath.moveTo(r.left, r.top);
                shapePath.lineTo(r.right, r.top);
                shapePath.lineTo(cx, r.bottom);
                shapePath.close();
                int aSave = canvas.save();
                canvas.clipPath(shapePath);
                paint.setColor(Color.BLACK);
                canvas.drawRect(r.left, r.top, r.right, r.top + fh * 0.38f, paint);
                drawSunWithRays(canvas, cx, r.top + fh * 0.38f, fh * 0.16f, 0xFFFCD116);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, r.top + fh * 0.38f, r.right, r.top + fh * 0.62f, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, r.top + fh * 0.62f, r.right, r.bottom, paint);
                canvas.restoreToCount(aSave);
                break;
            }
            case GeoData.PAT_ST_LUCIA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                shapePath.moveTo(cx, r.top + fh * 0.14f);
                shapePath.lineTo(cx + fw * 0.22f, r.bottom - fh * 0.14f);
                shapePath.lineTo(cx - fw * 0.22f, r.bottom - fh * 0.14f);
                shapePath.close();
                paint.setColor(Color.WHITE);
                canvas.drawPath(shapePath, paint);
                shapePath.reset();
                shapePath.moveTo(cx, r.top + fh * 0.22f);
                shapePath.lineTo(cx + fw * 0.18f, r.bottom - fh * 0.14f);
                shapePath.lineTo(cx - fw * 0.18f, r.bottom - fh * 0.14f);
                shapePath.close();
                paint.setColor(Color.BLACK);
                canvas.drawPath(shapePath, paint);
                shapePath.reset();
                shapePath.moveTo(cx, cy);
                shapePath.lineTo(cx + fw * 0.22f, r.bottom - fh * 0.14f);
                shapePath.lineTo(cx - fw * 0.22f, r.bottom - fh * 0.14f);
                shapePath.close();
                paint.setColor(c.c3);
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_MACEDONIA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                paint.setStrokeWidth(fh * 0.12f);
                canvas.drawLine(r.left, r.top, r.right, r.bottom, paint);
                canvas.drawLine(r.right, r.top, r.left, r.bottom, paint);
                canvas.drawLine(cx, r.top, cx, r.bottom, paint);
                canvas.drawLine(r.left, cy, r.right, cy, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, fh * 0.16f, paint);
                break;
            }
            case GeoData.PAT_BORDER: {
                paint.setColor(c.c2);
                canvas.drawRect(r, paint);
                float bp = fh * 0.12f;
                paint.setColor(c.c1);
                canvas.drawRect(r.left + bp, r.top + bp, r.right - bp, r.bottom - bp, paint);
                break;
            }
            case GeoData.PAT_BORDER_H:
            case GeoData.PAT_BELIZE: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top, r.right, r.top + fh * 0.14f, paint);
                canvas.drawRect(r.left, r.bottom - fh * 0.14f, r.right, r.bottom, paint);
                break;
            }
            case GeoData.PAT_BELARUS: {
                paint.setColor(c.c1);
                canvas.drawRect(r.left, r.top, r.right, r.top + fh * 0.67f, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, r.top + fh * 0.67f, r.right, r.bottom, paint);
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, r.top, r.left + fw * 0.12f, r.bottom, paint);
                break;
            }
            case GeoData.PAT_BOSNIA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                shapePath.reset();
                shapePath.moveTo(r.left + fw * 0.26f, r.top);
                shapePath.lineTo(r.left + fw * 0.78f, r.top);
                shapePath.lineTo(r.left + fw * 0.78f, r.bottom);
                shapePath.close();
                paint.setColor(c.c2);
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.PAT_BOTSWANA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, cy - fh * 0.14f, r.right, cy + fh * 0.14f, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, cy - fh * 0.09f, r.right, cy + fh * 0.09f, paint);
                break;
            }
            case GeoData.PAT_CABO_VERDE: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                float by = r.top + fh * 0.62f;
                paint.setColor(Color.WHITE);
                canvas.drawRect(r.left, by - fh * 0.08f, r.right, by + fh * 0.08f, paint);
                paint.setColor(c.c2);
                canvas.drawRect(r.left, by - fh * 0.03f, r.right, by + fh * 0.03f, paint);
                break;
            }
            case GeoData.PAT_CAR: {
                int[] cols = {0xFF003082, 0xFFFFFFFF, 0xFF289728, 0xFFFFCE00};
                for (int i = 0; i < 4; i++) {
                    paint.setColor(cols[i]);
                    canvas.drawRect(r.left, r.top + i * (fh / 4f), r.right, r.top + (i + 1) * (fh / 4f) + 1, paint);
                }
                paint.setColor(0xFFD21034);
                canvas.drawRect(cx - fw * 0.08f, r.top, cx + fw * 0.08f, r.bottom, paint);
                break;
            }
            case GeoData.PAT_H4: {
                int[] cols = {0xFFEA2839, 0xFF1A206D, 0xFFFFD500, 0xFF00A551};
                for (int i = 0; i < 4; i++) {
                    paint.setColor(cols[i]);
                    canvas.drawRect(r.left, r.top + i * (fh / 4f), r.right, r.top + (i + 1) * (fh / 4f) + 1, paint);
                }
                break;
            }
            case GeoData.PAT_SEYCHELLES: {
                paint.setColor(0xFF003F87);
                canvas.drawRect(r, paint);
                int[] scols = {0xFFFCD856, 0xFFD62828, 0xFFFFFFFF, 0xFF007A3D};
                float[] xTop = {r.left + fw * 0.33f, r.left + fw * 0.66f, r.right, r.right};
                float[] yRight = {r.top, r.top, r.top + fh * 0.33f, r.top + fh * 0.66f};
                for (int i = 0; i < 4; i++) {
                    shapePath.reset();
                    shapePath.moveTo(r.left, r.bottom);
                    shapePath.lineTo(xTop[i], yRight[i]);
                    shapePath.lineTo(r.right, r.bottom);
                    shapePath.close();
                    paint.setColor(scols[i]);
                    canvas.drawPath(shapePath, paint);
                }
                break;
            }
            case GeoData.PAT_TOGO: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                paint.setColor(c.c2);
                float sh = fh / 5f;
                canvas.drawRect(r.left, r.top + sh, r.right, r.top + 2 * sh, paint);
                canvas.drawRect(r.left, r.top + 3 * sh, r.right, r.top + 4 * sh, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.left, r.top, r.left + sh * 3f, r.top + sh * 3f, paint);
                break;
            }
            case GeoData.PAT_UGANDA: {
                int[] ucols = {c.c1, c.c2, c.c3, c.c1, c.c2, c.c3};
                for (int i = 0; i < 6; i++) {
                    paint.setColor(ucols[i]);
                    canvas.drawRect(r.left, r.top + i * (fh / 6f), r.right, r.top + (i + 1) * (fh / 6f) + 1, paint);
                }
                break;
            }
            case GeoData.PAT_ZAMBIA: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                float bw = fw * 0.11f;
                paint.setColor(c.c2);
                canvas.drawRect(r.right - 3 * bw, r.top + fh * 0.35f, r.right - 2 * bw, r.bottom, paint);
                paint.setColor(Color.BLACK);
                canvas.drawRect(r.right - 2 * bw, r.top + fh * 0.35f, r.right - bw, r.bottom, paint);
                paint.setColor(c.c3);
                canvas.drawRect(r.right - bw, r.top + fh * 0.35f, r.right, r.bottom, paint);
                break;
            }
            case GeoData.PAT_DIAG_X: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                float p = fh * 0.12f;
                shapePath.reset();
                shapePath.moveTo(r.left + p, r.top + p);
                shapePath.lineTo(r.right - p, r.top + p);
                shapePath.lineTo(cx, cy);
                shapePath.close();
                shapePath.moveTo(r.left + p, r.bottom - p);
                shapePath.lineTo(r.right - p, r.bottom - p);
                shapePath.lineTo(cx, cy);
                shapePath.close();
                paint.setColor(c.c2);
                canvas.drawPath(shapePath, paint);
                shapePath.reset();
                shapePath.moveTo(r.left + p, r.top + p);
                shapePath.lineTo(r.left + p, r.bottom - p);
                shapePath.lineTo(cx, cy);
                shapePath.close();
                shapePath.moveTo(r.right - p, r.top + p);
                shapePath.lineTo(r.right - p, r.bottom - p);
                shapePath.lineTo(cx, cy);
                shapePath.close();
                paint.setColor(c.c3);
                canvas.drawPath(shapePath, paint);
                break;
            }
            default: {
                paint.setColor(c.c1);
                canvas.drawRect(r, paint);
                break;
            }
        }

        drawEmblem(canvas, r, c, cx, cy, fw, fh, d);
    }

    private void drawUnionJack(Canvas canvas, RectF r, int navyColor, int redColor) {
        float fh = r.height();
        float cx = r.centerX();
        float cy = r.centerY();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(navyColor);
        canvas.drawRect(r, paint);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(fh * 0.16f);
        canvas.drawLine(r.left, r.top, r.right, r.bottom, paint);
        canvas.drawLine(r.right, r.top, r.left, r.bottom, paint);
        paint.setColor(redColor);
        paint.setStrokeWidth(fh * 0.07f);
        canvas.drawLine(r.left, r.top, r.right, r.bottom, paint);
        canvas.drawLine(r.right, r.top, r.left, r.bottom, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        float bw = fh * 0.24f;
        canvas.drawRect(cx - bw / 2f, r.top, cx + bw / 2f, r.bottom, paint);
        canvas.drawRect(r.left, cy - bw / 2f, r.right, cy + bw / 2f, paint);
        paint.setColor(redColor);
        float rw = fh * 0.14f;
        canvas.drawRect(cx - rw / 2f, r.top, cx + rw / 2f, r.bottom, paint);
        canvas.drawRect(r.left, cy - rw / 2f, r.right, cy + rw / 2f, paint);
    }

    private void drawKoreanTrigram(Canvas canvas, float tx, float ty, float angleDeg,
                                   boolean b1Broken, boolean b2Broken, boolean b3Broken, float fh) {
        int save = canvas.save();
        canvas.translate(tx, ty);
        canvas.rotate(angleDeg);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);

        float barLen = fh * 0.16f;
        float barThk = fh * 0.026f;
        float gap = fh * 0.018f;
        boolean[] broken = {b1Broken, b2Broken, b3Broken};

        for (int i = 0; i < 3; i++) {
            float y = (i - 1) * (barThk + gap);
            if (!broken[i]) {
                canvas.drawRect(-barLen / 2f, y - barThk / 2f, barLen / 2f, y + barThk / 2f, paint);
            } else {
                float split = barLen * 0.08f;
                canvas.drawRect(-barLen / 2f, y - barThk / 2f, -split, y + barThk / 2f, paint);
                canvas.drawRect(split, y - barThk / 2f, barLen / 2f, y + barThk / 2f, paint);
            }
        }
        canvas.restoreToCount(save);
    }

    private void drawEmblem(Canvas canvas, RectF r, GeoData.Country c, float cx, float cy, float fw, float fh, float d) {
        if (c.emblem == GeoData.EMB_NONE) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(c.emblemColor);

        switch (c.emblem) {
            case GeoData.EMB_MEXICO: {
                // Authentic Mexican Coat of Arms:
                // 1) Green oak & laurel half-wreath at base
                float er = fh * 0.15f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(fh * 0.032f);
                paint.setColor(0xFF006847);
                tmpRect.set(cx - er, cy - er * 0.8f, cx + er, cy + er * 1.1f);
                canvas.drawArc(tmpRect, 15, 150, false, paint);
                // 2) Blue lake pedestal + green prickly-pear cactus
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF0072C6);
                canvas.drawRect(cx - er * 0.45f, cy + er * 0.55f, cx + er * 0.45f, cy + er * 0.78f, paint);
                paint.setColor(0xFF169B62);
                canvas.drawOval(cx - er * 0.35f, cy + er * 0.15f, cx + er * 0.35f, cy + er * 0.60f, paint);
                // 3) Golden-brown Eagle with spread wings
                paint.setColor(0xFF7B481C);
                shapePath.reset();
                shapePath.moveTo(cx, cy + er * 0.25f);
                shapePath.lineTo(cx - er * 0.75f, cy - er * 0.35f);
                shapePath.lineTo(cx - er * 0.30f, cy - er * 0.75f);
                shapePath.lineTo(cx, cy - er * 0.30f);
                shapePath.lineTo(cx + er * 0.35f, cy - er * 0.75f);
                shapePath.lineTo(cx + er * 0.75f, cy - er * 0.25f);
                shapePath.close();
                canvas.drawPath(shapePath, paint);
                canvas.drawCircle(cx - er * 0.08f, cy - er * 0.45f, er * 0.24f, paint);
                // 4) Green serpent curved in beak
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(fh * 0.022f);
                paint.setColor(0xFF006847);
                canvas.drawLine(cx + er * 0.15f, cy - er * 0.45f, cx + er * 0.55f, cy - er * 0.05f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.EMB_SPAIN: {
                // Royal Spanish Coat of Arms off-center toward hoist
                float sx = r.left + fw * 0.32f;
                float sw = fh * 0.16f;
                float sh = fh * 0.22f;
                // White Pillars of Hercules on sides
                paint.setColor(0xFFE2E8F0);
                canvas.drawRect(sx - sw * 0.95f, cy - sh * 0.45f, sx - sw * 0.70f, cy + sh * 0.55f, paint);
                canvas.drawRect(sx + sw * 0.70f, cy - sh * 0.45f, sx + sw * 0.95f, cy + sh * 0.55f, paint);
                // Red & Gold quartered shield
                paint.setColor(0xFFAA151B);
                tmpRect.set(sx - sw * 0.55f, cy - sh * 0.4f, sx + sw * 0.55f, cy + sh * 0.55f);
                canvas.drawRoundRect(tmpRect, 3 * d, 3 * d, paint);
                paint.setColor(0xFFD4AF37);
                canvas.drawRect(sx - sw * 0.45f, cy - sh * 0.65f, sx + sw * 0.45f, cy - sh * 0.42f, paint);
                paint.setColor(0xFF003893);
                canvas.drawCircle(sx, cy + sh * 0.05f, sw * 0.24f, paint);
                break;
            }
            case GeoData.EMB_MAPLE: {
                // Authentic 11-point Canadian Maple Leaf
                float s = fh * 0.27f;
                shapePath.reset();
                shapePath.moveTo(cx, cy - s); // Top point
                shapePath.lineTo(cx + s * 0.18f, cy - s * 0.55f);
                shapePath.lineTo(cx + s * 0.42f, cy - s * 0.65f);
                shapePath.lineTo(cx + s * 0.35f, cy - s * 0.22f);
                shapePath.lineTo(cx + s * 0.82f, cy - s * 0.38f);
                shapePath.lineTo(cx + s * 0.68f, cy - s * 0.05f);
                shapePath.lineTo(cx + s * 0.90f, cy + s * 0.08f);
                shapePath.lineTo(cx + s * 0.42f, cy + s * 0.42f);
                shapePath.lineTo(cx + s * 0.48f, cy + s * 0.56f);
                shapePath.lineTo(cx + s * 0.07f, cy + s * 0.48f);
                shapePath.lineTo(cx + s * 0.07f, cy + s * 0.96f);
                shapePath.lineTo(cx - s * 0.07f, cy + s * 0.96f);
                shapePath.lineTo(cx - s * 0.07f, cy + s * 0.48f);
                shapePath.lineTo(cx - s * 0.48f, cy + s * 0.56f);
                shapePath.lineTo(cx - s * 0.42f, cy + s * 0.42f);
                shapePath.lineTo(cx - s * 0.90f, cy + s * 0.08f);
                shapePath.lineTo(cx - s * 0.68f, cy - s * 0.05f);
                shapePath.lineTo(cx - s * 0.82f, cy - s * 0.38f);
                shapePath.lineTo(cx - s * 0.35f, cy - s * 0.22f);
                shapePath.lineTo(cx - s * 0.42f, cy - s * 0.65f);
                shapePath.lineTo(cx - s * 0.18f, cy - s * 0.55f);
                shapePath.close();
                paint.setColor(c.emblemColor);
                canvas.drawPath(shapePath, paint);
                break;
            }
            case GeoData.EMB_CHAKRA: {
                float cr = fh * 0.135f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.8f * d);
                canvas.drawCircle(cx, cy, cr, paint);
                paint.setStrokeWidth(1.1f * d);
                for (int i = 0; i < 12; i++) {
                    double a = Math.toRadians(i * 15);
                    float dx = (float) Math.cos(a) * cr;
                    float dy = (float) Math.sin(a) * cr;
                    canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, cr * 0.22f, paint);
                break;
            }
            case GeoData.EMB_CHINA: {
                drawFivePointStar(canvas, r.left + fw * 0.18f, r.top + fh * 0.26f, fh * 0.14f, c.emblemColor);
                for (int i = 0; i < 4; i++) {
                    double a = Math.toRadians(-50 + i * 32);
                    float sx = r.left + fw * 0.18f + (float) Math.cos(a) * (fh * 0.22f);
                    float sy = r.top + fh * 0.26f + (float) Math.sin(a) * (fh * 0.22f);
                    drawFivePointStar(canvas, sx, sy, fh * 0.048f, c.emblemColor);
                }
                break;
            }
            case GeoData.EMB_STAR: {
                drawFivePointStar(canvas, cx, cy, fh * 0.18f, c.emblemColor);
                break;
            }
            case GeoData.EMB_STAR_LEFT: {
                drawFivePointStar(canvas, r.left + fw * 0.17f, cy, fh * 0.12f, c.emblemColor);
                break;
            }
            case GeoData.EMB_STAR_TL: {
                drawFivePointStar(canvas, r.left + fw * 0.18f, r.top + fh * 0.25f, fh * 0.11f, c.emblemColor);
                break;
            }
            case GeoData.EMB_TWO_STARS: {
                drawFivePointStar(canvas, cx - fw * 0.12f, cy, fh * 0.10f, c.emblemColor);
                drawFivePointStar(canvas, cx + fw * 0.12f, cy, fh * 0.10f, c.emblemColor);
                break;
            }
            case GeoData.EMB_THREE_STARS: {
                drawFivePointStar(canvas, cx, cy - fh * 0.10f, fh * 0.07f, c.emblemColor);
                drawFivePointStar(canvas, cx - fw * 0.08f, cy + fh * 0.07f, fh * 0.07f, c.emblemColor);
                drawFivePointStar(canvas, cx + fw * 0.08f, cy + fh * 0.07f, fh * 0.07f, c.emblemColor);
                break;
            }
            case GeoData.EMB_FOUR_STARS: {
                float rx = "FM".equals(c.code) ? cx : (r.left + fw * 0.75f);
                drawFivePointStar(canvas, rx, r.top + fh * 0.24f, fh * 0.06f, c.emblemColor);
                drawFivePointStar(canvas, rx, r.top + fh * 0.76f, fh * 0.06f, c.emblemColor);
                drawFivePointStar(canvas, rx - fw * 0.11f, cy, fh * 0.06f, c.emblemColor);
                drawFivePointStar(canvas, rx + fw * 0.11f, cy, fh * 0.06f, c.emblemColor);
                break;
            }
            case GeoData.EMB_FIVE_STARS: {
                drawFivePointStar(canvas, cx, cy, fh * 0.06f, c.emblemColor);
                drawFivePointStar(canvas, cx - fw * 0.11f, cy - fh * 0.07f, fh * 0.06f, c.emblemColor);
                drawFivePointStar(canvas, cx + fw * 0.11f, cy - fh * 0.07f, fh * 0.06f, c.emblemColor);
                drawFivePointStar(canvas, cx - fw * 0.11f, cy + fh * 0.07f, fh * 0.06f, c.emblemColor);
                drawFivePointStar(canvas, cx + fw * 0.11f, cy + fh * 0.07f, fh * 0.06f, c.emblemColor);
                break;
            }
            case GeoData.EMB_FIVE_STARS_TL: {
                float bx = r.left + fw * 0.22f;
                float by = r.top + fh * 0.26f;
                drawFivePointStar(canvas, bx, by, fh * 0.055f, c.emblemColor);
                drawFivePointStar(canvas, bx - fw * 0.09f, by - fh * 0.10f, fh * 0.055f, c.emblemColor);
                drawFivePointStar(canvas, bx + fw * 0.09f, by - fh * 0.10f, fh * 0.055f, c.emblemColor);
                drawFivePointStar(canvas, bx - fw * 0.09f, by + fh * 0.10f, fh * 0.055f, c.emblemColor);
                drawFivePointStar(canvas, bx + fw * 0.09f, by + fh * 0.10f, fh * 0.055f, c.emblemColor);
                break;
            }
            case GeoData.EMB_ARC_STARS: {
                for (int i = 0; i < 7; i++) {
                    double a = Math.toRadians(200 + i * 23);
                    float sx = cx + (float) Math.cos(a) * (fw * 0.18f);
                    float sy = cy + fh * 0.08f + (float) Math.sin(a) * (fh * 0.18f);
                    canvas.drawCircle(sx, sy, fh * 0.025f, paint);
                }
                break;
            }
            case GeoData.EMB_RING_STARS: {
                float rcx = "CV".equals(c.code) ? (r.left + fw * 0.36f) : (r.left + fw * 0.73f);
                float rcy = "CV".equals(c.code) ? (r.top + fh * 0.62f) : cy;
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(i * 36);
                    float sx = rcx + (float) Math.cos(a) * (fh * 0.17f);
                    float sy = rcy + (float) Math.sin(a) * (fh * 0.17f);
                    canvas.drawCircle(sx, sy, fh * 0.024f, paint);
                }
                break;
            }
            case GeoData.EMB_CRESCENT_STAR: {
                float shift = "PK".equals(c.code) ? fw * 0.10f : ("KM".equals(c.code) ? -fw * 0.32f : 0f);
                int cutCol = "PK".equals(c.code) || "MR".equals(c.code) ? 0xFF01411C
                        : ("DZ".equals(c.code) ? Color.WHITE
                        : ("LY".equals(c.code) ? Color.BLACK
                        : ("KM".equals(c.code) ? 0xFF3D8E33 : c.c1)));
                paint.setColor(c.emblemColor);
                canvas.drawCircle(cx + shift - fh * 0.04f, cy, fh * 0.21f, paint);
                paint.setColor(cutCol);
                canvas.drawCircle(cx + shift + fh * 0.03f, cy, fh * 0.16f, paint);
                drawFivePointStar(canvas, cx + shift + fh * 0.16f, cy, fh * 0.09f, c.emblemColor);
                break;
            }
            case GeoData.EMB_TUNISIA: {
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx, cy, fh * 0.25f, paint);
                paint.setColor(0xFFE70013);
                canvas.drawCircle(cx - fh * 0.02f, cy, fh * 0.18f, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx + fh * 0.03f, cy, fh * 0.14f, paint);
                drawFivePointStar(canvas, cx + fh * 0.05f, cy, fh * 0.09f, 0xFFE70013);
                break;
            }
            case GeoData.EMB_CRESCENT_TL: {
                float lx = r.left + fw * 0.20f;
                float ly = r.top + fh * 0.25f;
                paint.setColor(c.emblemColor);
                canvas.drawCircle(lx - fh * 0.03f, ly, fh * 0.14f, paint);
                paint.setColor("MY".equals(c.code) ? 0xFF010066 : c.c1);
                canvas.drawCircle(lx + fh * 0.02f, ly, fh * 0.11f, paint);
                drawFivePointStar(canvas, lx + fh * 0.12f, ly, fh * 0.07f, c.emblemColor);
                break;
            }
            case GeoData.EMB_CRESCENT_WHITE: {
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx - fh * 0.02f, cy, fh * 0.18f, paint);
                paint.setColor(c.c1);
                canvas.drawCircle(cx + fh * 0.04f, cy, fh * 0.15f, paint);
                break;
            }
            case GeoData.EMB_SUN: {
                drawSunWithRays(canvas, cx, cy, fh * 0.13f, c.emblemColor);
                break;
            }
            case GeoData.EMB_SUN_LEFT: {
                drawSunWithRays(canvas, r.left + fw * 0.16f, cy, fh * 0.11f, c.emblemColor);
                break;
            }
            case GeoData.EMB_SUN_TL: {
                drawSunWithRays(canvas, r.left + fw * 0.22f, r.top + fh * 0.25f, fh * 0.12f, c.emblemColor);
                break;
            }
            case GeoData.EMB_SUN_TR: {
                drawSunWithRays(canvas, r.right - fw * 0.20f, r.top + fh * 0.25f, fh * 0.11f, c.emblemColor);
                break;
            }
            case GeoData.EMB_SUN_TOP: {
                drawSunWithRays(canvas, cx, r.top + fh * 0.20f, fh * 0.11f, c.emblemColor);
                break;
            }
            case GeoData.EMB_SHIELD: {
                // Maasai / Swazi shield & crossed spears
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * d);
                paint.setColor(Color.WHITE);
                canvas.drawLine(cx - fh * 0.22f, cy + fh * 0.28f, cx + fh * 0.22f, cy - fh * 0.28f, paint);
                canvas.drawLine(cx + fh * 0.22f, cy + fh * 0.28f, cx - fh * 0.22f, cy - fh * 0.28f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFBB0000);
                tmpRect.set(cx - fh * 0.13f, cy - fh * 0.24f, cx + fh * 0.13f, cy + fh * 0.24f);
                canvas.drawOval(tmpRect, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.WHITE);
                canvas.drawOval(tmpRect, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.EMB_PENTAGRAM: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.4f * d);
                paint.setColor(c.emblemColor);
                float sr = fh * 0.22f;
                shapePath.reset();
                int[] order = {0, 2, 4, 1, 3, 0};
                for (int i = 0; i < order.length; i++) {
                    double a = Math.toRadians(-90 + order[i] * 72);
                    float x = cx + (float) Math.cos(a) * sr;
                    float y = cy + (float) Math.sin(a) * sr;
                    if (i == 0) shapePath.moveTo(x, y);
                    else shapePath.lineTo(x, y);
                }
                canvas.drawPath(shapePath, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.EMB_HEXAGRAM: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * d);
                paint.setColor(c.emblemColor);
                float hr = fh * 0.19f;
                for (int t = 0; t < 2; t++) {
                    shapePath.reset();
                    for (int i = 0; i < 3; i++) {
                        double a = Math.toRadians((t == 0 ? -90 : 90) + i * 120);
                        float x = cx + (float) Math.cos(a) * hr;
                        float y = cy + (float) Math.sin(a) * hr;
                        if (i == 0) shapePath.moveTo(x, y);
                        else shapePath.lineTo(x, y);
                    }
                    shapePath.close();
                    canvas.drawPath(shapePath, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.EMB_BLUE_DISK_STAR: {
                paint.setColor(0xFF0F47AF);
                canvas.drawCircle(cx, cy, fh * 0.22f, paint);
                drawFivePointStar(canvas, cx, cy, fh * 0.17f, 0xFFFCDD09);
                break;
            }
            case GeoData.EMB_SAUDI: {
                // White Arabic calligraphy band + horizontal sabre below
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(cx - fw * 0.26f, cy - fh * 0.16f, cx + fw * 0.26f, cy - fh * 0.02f, 3 * d, 3 * d, paint);
                canvas.drawRect(cx - fw * 0.22f, cy + fh * 0.08f, cx + fw * 0.22f, cy + fh * 0.13f, paint);
                break;
            }
            case GeoData.EMB_LION:
            case GeoData.EMB_DRAGON: {
                float lx = "LK".equals(c.code) ? (r.left + fw * 0.64f) : cx;
                paint.setColor(c.emblemColor);
                canvas.drawRoundRect(lx - fw * 0.15f, cy - fh * 0.15f, lx + fw * 0.15f, cy + fh * 0.15f, 6 * d, 6 * d, paint);
                break;
            }
            case GeoData.EMB_CEDAR: {
                paint.setColor(c.emblemColor);
                shapePath.reset();
                shapePath.moveTo(cx, cy - fh * 0.18f);
                shapePath.lineTo(cx + fw * 0.12f, cy + fh * 0.12f);
                shapePath.lineTo(cx - fw * 0.12f, cy + fh * 0.12f);
                shapePath.close();
                canvas.drawPath(shapePath, paint);
                canvas.drawRect(cx - fw * 0.025f, cy + fh * 0.10f, cx + fw * 0.025f, cy + fh * 0.19f, paint);
                break;
            }
            case GeoData.EMB_TEMPLE: {
                paint.setColor(Color.WHITE);
                canvas.drawRect(cx - fw * 0.18f, cy + fh * 0.04f, cx + fw * 0.18f, cy + fh * 0.16f, paint);
                canvas.drawRect(cx - fw * 0.14f, cy - fh * 0.08f, cx - fw * 0.08f, cy + fh * 0.04f, paint);
                canvas.drawRect(cx - fw * 0.03f, cy - fh * 0.15f, cx + fw * 0.03f, cy + fh * 0.04f, paint);
                canvas.drawRect(cx + fw * 0.08f, cy - fh * 0.08f, cx + fw * 0.14f, cy + fh * 0.04f, paint);
                break;
            }
            case GeoData.EMB_TRIDENT: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.4f * d);
                paint.setColor(Color.BLACK);
                float tr = fh * 0.16f;
                canvas.drawLine(cx, cy - tr, cx, cy + tr, paint);
                canvas.drawLine(cx - tr * 0.7f, cy - tr, cx - tr * 0.7f, cy + tr * 0.2f, paint);
                canvas.drawLine(cx + tr * 0.7f, cy - tr, cx + tr * 0.7f, cy + tr * 0.2f, paint);
                canvas.drawLine(cx - tr * 0.7f, cy + tr * 0.2f, cx + tr * 0.7f, cy + tr * 0.2f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case GeoData.EMB_COAT_LEFT: {
                float lx = r.left + fw * 0.30f;
                drawHeraldicShield(canvas, lx, cy, fh * 0.14f, c.emblemColor);
                break;
            }
            case GeoData.EMB_COAT_RIGHT: {
                float rx = r.left + fw * 0.74f;
                drawHeraldicShield(canvas, rx, cy, fh * 0.15f, c.emblemColor);
                break;
            }
            case GeoData.EMB_EAGLE: {
                float ex = "ZM".equals(c.code) ? (r.right - fw * 0.16f) : cx;
                float ey = "ZM".equals(c.code) ? (r.top + fh * 0.20f) : cy;
                drawEagleSilhouette(canvas, ex, ey, fh * 0.14f, c.emblemColor);
                break;
            }
            default: {
                drawHeraldicShield(canvas, cx, cy, fh * 0.13f, c.emblemColor);
                break;
            }
        }
    }

    private void drawSunWithRays(Canvas canvas, float sx, float sy, float rad, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(sx, sy, rad * 0.68f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, rad * 0.18f));
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float dx = (float) Math.cos(a) * rad;
            float dy = (float) Math.sin(a) * rad;
            canvas.drawLine(sx - dx, sy - dy, sx + dx, sy + dy, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawEagleSilhouette(Canvas canvas, float ex, float ey, float sz, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        shapePath.reset();
        shapePath.moveTo(ex, ey + sz * 0.65f);
        shapePath.lineTo(ex - sz * 0.85f, ey - sz * 0.35f);
        shapePath.lineTo(ex - sz * 0.25f, ey - sz * 0.15f);
        shapePath.lineTo(ex, ey - sz * 0.65f);
        shapePath.lineTo(ex + sz * 0.25f, ey - sz * 0.15f);
        shapePath.lineTo(ex + sz * 0.85f, ey - sz * 0.35f);
        shapePath.close();
        canvas.drawPath(shapePath, paint);
    }

    private void drawHeraldicShield(Canvas canvas, float sx, float sy, float sz, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        tmpRect.set(sx - sz * 0.75f, sy - sz * 0.85f, sx + sz * 0.75f, sy + sz * 0.85f);
        canvas.drawRoundRect(tmpRect, sz * 0.35f, sz * 0.35f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.2f, sz * 0.14f));
        paint.setColor(0xFFD4AF37);
        canvas.drawRoundRect(tmpRect, sz * 0.35f, sz * 0.35f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFivePointStar(Canvas canvas, float cx, float cy, float rOuter, int color) {
        float rInner = rOuter * 0.40f;
        shapePath.reset();
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            float rad = (i % 2 == 0) ? rOuter : rInner;
            float x = cx + (float) Math.cos(angle) * rad;
            float y = cy + (float) Math.sin(angle) * rad;
            if (i == 0) shapePath.moveTo(x, y);
            else shapePath.lineTo(x, y);
        }
        shapePath.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(shapePath, paint);
    }
}

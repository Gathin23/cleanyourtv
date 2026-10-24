package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 *
 * <p>When {@link #setDepthLayers(int)} is called with a value greater than 1 (glass_depth_layers
 * theme key), a second fill layer and a diagonal specular strip are added for the Apple Glass
 * multi-layer material look. The extra draws are still cached in the hardware layer, so the spring
 * moves the result cheaply on every frame.</p>
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Second fill layer for depth (glass_depth_layers ≥ 2); null until enabled. */
    private Paint fill2;
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Diagonal specular highlight for depth layers; null until enabled. */
    private Paint specular2;
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Inner rim — a faint bright line just inside the outer rim for material thickness (depth ≥ 2). */
    private Paint innerRim;
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Corner radial glow to simulate light catching the edge at the top-left (depth ≥ 2). */
    private Paint cornerGlow;
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: the white glow stays faint, a coloured one needs more alpha to read as warm light. */
    private int glowAlpha = 0x30;
    /** 1 = original single-layer glass; 2 = Apple Glass multi-layer material. */
    private int depthLayers = 1;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Sets the number of glass material layers (1 or 2).  Call once after construction; changing it
     * invalidates the shaders so do not animate it.  The default is 1, which is identical to the
     * original GlassView.  2 enables the Apple Glass multi-layer look.
     */
    void setDepthLayers(int layers) {
        if (depthLayers == layers) return;
        depthLayers = layers;
        if (layers >= 2) {
            fill2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            specular2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerRim = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerRim.setStyle(Paint.Style.STROKE);
            innerRim.setStrokeWidth(0.75f * density);
            innerRim.setColor(0x33FFFFFF);
            cornerGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        } else {
            fill2 = null;
            specular2 = null;
            innerRim = null;
            cornerGlow = null;
        }
        // Rebuild shaders that depend on size (will be done in onSizeChanged if already laid out).
        if (getWidth() > 0 && getHeight() > 0) buildShaders(getWidth(), getHeight());
        invalidate();
    }

    /** Tints the rim and the outer glow; 0 restores the plain white glass. */
    void setAccent(int argb) {
        if (argb == 0) {
            glowRgb = 0xFFFFFF;
            glowAlpha = 0x30;
            rim.setColor(0xCCFFFFFF);
        } else {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            int r = (((glowRgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((glowRgb >> 8) & 0xFF) + 255 * 2) / 3,
                    b = ((glowRgb & 0xFF) + 255 * 2) / 3;
            rim.setColor(0xE6000000 | (r << 16) | (g << 8) | b);
        }
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildShaders(w, h);
    }

    private void buildShaders(int w, int h) {
        // Primary fill: medium-dark translucent white top → lighter bottom (glass body).
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        // Primary specular: bright top 45%.
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));

        if (depthLayers >= 2) {
            // Second fill layer: a very subtle angled gradient from top-left to bottom-right,
            // simulating light transmission through a thicker material.
            fill2.setShader(new LinearGradient(0, 0, w, h, 0x18FFFFFF, 0x06FFFFFF, Shader.TileMode.CLAMP));
            // Diagonal highlight strip across the top-left quarter — like light catching a bevelled edge.
            specular2.setShader(new LinearGradient(w * 0.05f, 0, w * 0.55f, h * 0.35f,
                    new int[]{0x00FFFFFF, 0x2EFFFFFF, 0x14FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.3f, 0.6f, 1f},
                    Shader.TileMode.CLAMP));
            // Radial glow at top-left corner: simulates a point-light reflection on the glass edge.
            cornerGlow.setShader(new RadialGradient(w * 0.12f, h * 0.08f, h * 0.5f,
                    0x20FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // Outer glow: thin rings across the inset, each a little fainter, so it fades out smoothly. Three wide
        // strokes left visible bands once the glow took an app's colour at a stronger alpha. The hardware layer
        // caches the result, so the rings cost one draw, not one per frame of the spring.
        int rings = 12;
        float step = inset / rings;
        glow.setStrokeWidth(step + 0.5f);
        for (int i = 0; i < rings; i++) {
            float o = step * (i + 0.5f);
            float t = 1f - (float) i / rings;
            glow.setColor(Math.round(glowAlpha * t) << 24 | glowRgb);
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        // --- Glass body ---
        canvas.drawRoundRect(rect, radius, radius, fill);
        if (depthLayers >= 2 && fill2 != null) {
            // Second fill: angled transmission light on top of the base fill.
            canvas.drawRoundRect(rect, radius, radius, fill2);
        }

        // --- Specular highlights ---
        // Primary: bright sheen across the top 45%.
        // clipRect stays on the GPU (clipPath did not on the MT5895) and the round rect under it keeps
        // the top corners rounded with a flat bottom edge.
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        if (depthLayers >= 2 && specular2 != null) {
            // Diagonal strip highlight (no clip needed — gradient fades to transparent naturally).
            canvas.drawRoundRect(rect, radius, radius, specular2);
            // Corner radial glow — top-left light-catch.
            canvas.drawRoundRect(rect, radius, radius, cornerGlow);
        }

        // --- Rims ---
        canvas.drawRoundRect(rect, radius, radius, rim);
        if (depthLayers >= 2 && innerRim != null) {
            // Inner rim: 2dp inset from the outer rim, simulating material thickness.
            float ir = 2f * density;
            canvas.drawRoundRect(rect.left + ir, rect.top + ir, rect.right - ir, rect.bottom - ir,
                    Math.max(0, radius - ir), Math.max(0, radius - ir), innerRim);
        }
    }
}

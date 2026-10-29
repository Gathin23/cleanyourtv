package com.gathin.newsdream;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Draws a QR code with the four-module quiet zone, scaled to whole pixels per module: the screen's navy on a muted
 * blue-grey, the tone of the summary text, so it sits with the rest instead of glaring white. About 9:1 contrast,
 * which phones read easily.
 */
final class QrView extends View {
    private static final int QUIET = 4;
    private static final int LIGHT = 0xFFAEB6C6;
    private static final int DARK = 0xFF101A30;
    private final Paint paint = new Paint();
    private boolean[][] modules;

    QrView(Context context) {
        super(context);
        paint.setColor(DARK);
        paint.setAntiAlias(false);
        float radius = 10 * context.getResources().getDisplayMetrics().density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    /** The address to show; false when there is none or it is too long, and the view should be hidden. */
    boolean setText(String text) {
        modules = text == null || text.isEmpty() ? null : Qr.encode(text);
        invalidate();
        return modules != null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(LIGHT);
        if (modules == null) return;
        int n = modules.length + 2 * QUIET;
        int side = Math.min(getWidth(), getHeight());
        int scale = Math.max(1, side / n);
        // Centred, so the rounding leftover becomes extra light border.
        int left = (getWidth() - scale * n) / 2 + QUIET * scale;
        int top = (getHeight() - scale * n) / 2 + QUIET * scale;
        for (int y = 0; y < modules.length; y++) {
            for (int x = 0; x < modules.length; x++) {
                if (modules[y][x]) {
                    canvas.drawRect(left + x * scale, top + y * scale,
                            left + (x + 1) * scale, top + (y + 1) * scale, paint);
                }
            }
        }
    }
}

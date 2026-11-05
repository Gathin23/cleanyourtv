package io.github.nickld.frigateglance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Draws the detected objects' boxes, given as fractions of the frame, over the picture. */
final class BoxView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    private List<float[]> boxes = new ArrayList<>();

    BoxView(Context context) {
        super(context);
        float dp = context.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(0xFF3DDC84);
        radius = 4 * dp;
    }

    void setBoxes(List<float[]> b) {
        boxes = b == null ? new ArrayList<>() : b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        for (float[] b : boxes) {
            rect.set(b[0] * w, b[1] * h, b[2] * w, b[3] * h);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}

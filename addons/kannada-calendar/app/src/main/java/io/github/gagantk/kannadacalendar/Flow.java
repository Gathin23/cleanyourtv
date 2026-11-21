package io.github.gagantk.kannadacalendar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

/**
 * Lays its children out left to right and wraps them, like words. When they do not all fit, the ones that would
 * fall below the bottom are left out rather than drawn half, and a quiet "+N more" line says how many.
 */
final class Flow extends ViewGroup {
    private final int gapX, gapY, columns;
    private final Paint more = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int hidden;

    /** {@code columns} 0 lets each child take its own width; more gives every child an equal share of the row. */
    Flow(Context c, int gap, int columns) {
        super(c);
        this.columns = columns;
        gapX = Ui.dp(c, gap);
        gapY = Ui.dp(c, gap);
        more.setColor(Palette.current.muted);
        more.setTextSize(Ui.dp(c, 13));
        setWillNotDraw(false);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int spec = columns > 0
                    ? MeasureSpec.makeMeasureSpec((width - gapX * (columns - 1)) / columns, MeasureSpec.EXACTLY)
                    : MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
            child.measure(spec, MeasureSpec.UNSPECIFIED);
            if (x > 0 && x + child.getMeasuredWidth() > width + 1) {
                x = 0;
                y += rowH + gapY;
                rowH = 0;
            }
            x += child.getMeasuredWidth() + gapX;
            rowH = Math.max(rowH, child.getMeasuredHeight());
        }
        int wanted = y + rowH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(wanted, heightSpec));
    }

    /** Places the children in rows within {@code bottom}; returns how many did not fit. */
    private int place(int width, int bottom, boolean apply) {
        int x = 0, y = 0, rowH = 0, left = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int cw = child.getMeasuredWidth(), ch = child.getMeasuredHeight();
            if (x > 0 && x + cw > width + 1) {
                x = 0;
                y += rowH + gapY;
                rowH = 0;
            }
            boolean fits = y + ch <= bottom && left == 0;
            if (!fits) left++;
            if (apply) {
                if (fits) child.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + cw, getPaddingTop() + y + ch);
                else child.layout(0, 0, 0, 0);
            }
            x += cw + gapX;
            rowH = Math.max(rowH, ch);
        }
        return left;
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l - getPaddingLeft() - getPaddingRight();
        int height = b - t - getPaddingTop() - getPaddingBottom();
        hidden = place(width, height, false);
        if (hidden > 0) hidden = place(width, height - (int) (more.getFontSpacing() + gapY), true);
        else place(width, height, true);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (hidden > 0) {
            String text = "+" + hidden + " more";
            canvas.drawText(text, getPaddingLeft(), getHeight() - getPaddingBottom() - more.descent(), more);
        }
    }
}

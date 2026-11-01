package io.github.justinnewman.gameday;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

/**
 * The 36 colours as a 12 by 3 grid: Left and Right change the colour, Up and Down the shade, OK chooses.
 * A small dark dot marks colours other calendars already use.
 */
public final class ColourGrid extends View {
    interface Listener {
        void onMoved(int index);

        void onChosen(int index);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Set<Integer> used = new HashSet<>();
    private int selected = Palette.COLUMNS;
    private Listener listener;

    public ColourGrid(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setFocusable(true);
        ring.setStyle(Paint.Style.STROKE);
    }

    /** Where a D-pad key moves the selection; the grid's edges stop it. */
    static int move(int index, int keyCode) {
        int col = index % Palette.COLUMNS;
        int row = index / Palette.COLUMNS;
        int rows = Palette.COUNT / Palette.COLUMNS;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return col > 0 ? index - 1 : index;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return col < Palette.COLUMNS - 1 ? index + 1 : index;
            case KeyEvent.KEYCODE_DPAD_UP:
                return row > 0 ? index - Palette.COLUMNS : index;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return row < rows - 1 ? index + Palette.COLUMNS : index;
            default:
                return index;
        }
    }

    void set(int selectedIndex, Set<Integer> usedColours, Listener l) {
        selected = selectedIndex;
        used = usedColours;
        listener = l;
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (listener != null) listener.onChosen(selected);
            return true;
        }
        int next = move(selected, keyCode);
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (next != selected) {
                selected = next;
                invalidate();
                if (listener != null) listener.onMoved(selected);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        float cell = w / (float) Palette.COLUMNS;
        setMeasuredDimension(w, Math.round(cell * Palette.COUNT / Palette.COLUMNS));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = getWidth() / (float) Palette.COLUMNS;
        // Small enough that the focused circle and its ring stay inside the cell (the bottom row was clipped).
        float r = cell * 0.30f;
        ring.setStrokeWidth(cell * 0.06f);
        for (int i = 0; i < Palette.COUNT; i++) {
            float cx = (i % Palette.COLUMNS + 0.5f) * cell;
            float cy = (i / Palette.COLUMNS + 0.5f) * cell;
            boolean focused = i == selected && isFocused();
            float radius = focused ? r * 1.18f : r;
            fill.setColor(Palette.colour(i));
            canvas.drawCircle(cx, cy, radius, fill);
            if (i == selected) {
                ring.setColor(focused ? 0xFFFFFFFF : 0x80FFFFFF);
                canvas.drawCircle(cx, cy, radius + ring.getStrokeWidth() * 1.5f, ring);
            }
            if (used.contains(i) && i != selected) {
                fill.setColor(0xFF000000);
                canvas.drawCircle(cx + r * 0.62f, cy + r * 0.62f, r * 0.28f, fill);
            }
        }
    }
}

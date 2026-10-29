package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * row_titles: a small heading above each row of tiles, as Leanback's "Apps" and "Games" and Windows 8's tile
 * groups had. Every row gets the same room above it (AppAdapter.ROW_TITLE_DP, counted in the tile fit), so the
 * rows stay evenly spaced; rows past the list stay untitled. The heading sits over the row's first tile and
 * scrolls with it.
 */
final class RowTitles extends RecyclerView.ItemDecoration {

    private final List<String> titles;
    private final int columns;
    /** The heading's room above a row, and the gap between its baseline and the tile. */
    private final float room, gap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RowTitles(Context context, List<String> titles, int columns, float density) {
        this.titles = titles;
        this.columns = columns;
        this.room = AppAdapter.ROW_TITLE_DP * density;
        this.gap = 8 * density;
        paint.setColor(0xD9FFFFFF);
        paint.setTextSize(17 * context.getResources().getDisplayMetrics().scaledDensity);
        paint.setTypeface(UiFont.get(context, R.string.font_regular));
    }

    /** The row an adapter position sits in, or -1 for none (not laid out). */
    static int row(int position, int columns) {
        return position < 0 || columns <= 0 ? -1 : position / columns;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        outRect.top += (int) room;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos % columns != 0) continue;
            int row = row(pos, columns);
            if (row >= titles.size()) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float x = child.getLeft() + card.getLeft();
            float y = child.getTop() + child.getTranslationY() + card.getTop() - gap;
            c.drawText(titles.get(row), x, y, paint);
        }
    }
}

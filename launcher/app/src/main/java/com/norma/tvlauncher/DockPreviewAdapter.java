package com.norma.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the dock hover preview row.
 * Renders up to {@link DockPreview#MAX_CARDS} poster cards horizontally.
 * Cards are non-interactive; the whole RecyclerView is non-focusable.
 */
final class DockPreviewAdapter extends RecyclerView.Adapter<DockPreviewAdapter.VH> {

    private static final float CORNER_RADIUS_DP = 10f;

    private List<DockPreview.Item> items = new ArrayList<>();
    private final float density;
    private final LayoutInflater inflater;

    DockPreviewAdapter(Context ctx) {
        this.inflater = LayoutInflater.from(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    void setItems(List<DockPreview.Item> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    void clear() {
        items = new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_dock_preview, parent, false);
        return new VH(v, density);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DockPreview.Item item = items.get(position);
        h.bind(item, position < items.size() - 1 ? (int)(16 * density) : 0);
    }

    static final class VH extends RecyclerView.ViewHolder {
        private final ImageView poster;
        private final TextView title;
        private final float cornerPx;
        private final float density;

        VH(View v, float density) {
            super(v);
            this.density = density;
            poster = v.findViewById(R.id.previewPoster);
            title = v.findViewById(R.id.previewTitle);
            cornerPx = CORNER_RADIUS_DP * density;
            // Clip to rounded corners via outline.
            poster.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerPx);
                }
            });
            poster.setClipToOutline(true);
        }

        void bind(DockPreview.Item item, int marginEndPx) {
            // Right margin between cards (last card has none).
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (lp != null) {
                lp.rightMargin = marginEndPx;
                itemView.setLayoutParams(lp);
            }

            if (item.posterBitmap != null) {
                poster.setImageBitmap(item.posterBitmap);
            } else {
                poster.setImageDrawable(null);
                poster.setBackgroundColor(item.accent);
            }

            if (item.title != null && !item.title.isEmpty()) {
                title.setText(item.title);
                title.setVisibility(View.VISIBLE);
            } else {
                title.setVisibility(View.GONE);
            }
        }
    }
}

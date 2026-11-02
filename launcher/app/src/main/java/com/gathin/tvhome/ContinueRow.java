package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

/**
 * The cards under the top shelf's name (config.xml, continue_watching): 16:9 artwork with a thin progress bar, then
 * the title and a second line. The focus is the launcher's glass slab, one per card and shown only on the focused
 * one, so it scrolls with the row and needs no spring. Keys: Left/Right between cards, Up stays, Down and Back hand
 * focus back to the dock (Listener.onLeave), OK opens the card.
 */
final class ContinueRow {

    interface Listener {
        void onOpen(WatchNextShelf.Item item);

        /** Down or Back on a card: focus returns to the dock tile. */
        void onLeave();

        void onFocused(int index);
    }

    static final float GAP_DP = 12f;
    static final float TEXT_DP = 6 + 17 + 14 + 2;
    private static final float GLASS_PAD_DP = 6f;
    private static final float BAR_DP = 3f;

    private final Context context;
    private final RecyclerView view;
    private final ShelfImages images;
    private final Listener listener;
    private final float density;
    /** Config's glass_style, so the cards' highlight looks like the tiles'. */
    private final String glassStyle;
    private final boolean rtl;
    private final Adapter adapter = new Adapter();
    private List<WatchNextShelf.Item> items = Collections.emptyList();
    private int cardWidth;
    private boolean held;

    ContinueRow(Context context, RecyclerView view, ShelfImages images, Listener listener, String glassStyle) {
        this.glassStyle = glassStyle;
        this.context = context;
        this.view = view;
        this.images = images;
        this.listener = listener;
        density = context.getResources().getDisplayMetrics().density;
        rtl = context.getResources().getBoolean(R.bool.rtl_menu_bar);
        // The Hebrew shelf runs from the right (layout-iw), whatever direction the window resolves to.
        view.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
            @Override
            protected boolean isLayoutRTL() {
                return rtl;
            }
        });
        view.setItemAnimator(null);
        view.setClipChildren(false);
        view.setClipToPadding(false);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        view.setAdapter(adapter);
    }

    /** Height of a row of cards cardWidthPx wide, in px. */
    static int rowHeightPx(int cardWidthPx, float density) {
        return Math.round(cardWidthPx * 9f / 16f + TEXT_DP * density);
    }

    List<WatchNextShelf.Item> items() {
        return items;
    }

    void setItems(List<WatchNextShelf.Item> next, int cardWidthPx) {
        items = next;
        cardWidth = cardWidthPx;
        view.stopScroll();
        adapter.notifyDataSetChanged();
        view.scrollToPosition(0);
    }

    /** True while the key that moved the focus is being held (auto-repeat): the shelf then skips its fades. */
    boolean held() {
        return held;
    }

    /** True when v is one of the row's views. */
    boolean owns(View v) {
        for (android.view.ViewParent p = v == null ? null : v.getParent(); p != null; p = p.getParent()) {
            if (p == view) return true;
        }
        return false;
    }

    boolean hasFocus() {
        return view.hasFocus();
    }

    /** Focuses card index (clamped); it may need a scroll and a layout first. */
    void focus(int index) {
        if (items.isEmpty()) return;
        int at = Math.max(0, Math.min(index, items.size() - 1));
        RecyclerView.ViewHolder holder = view.findViewHolderForAdapterPosition(at);
        if (holder != null) {
            ((Holder) holder).art.requestFocus();
            return;
        }
        view.scrollToPosition(at);
        view.post(() -> {
            RecyclerView.ViewHolder h = view.findViewHolderForAdapterPosition(at);
            if (h != null) ((Holder) h).art.requestFocus();
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(build());
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            WatchNextShelf.Item item = items.get(position);
            int artH = Math.round(cardWidth * 9f / 16f);
            ViewGroup.LayoutParams wrapLp = h.wrap.getLayoutParams();
            if (wrapLp.width != cardWidth || wrapLp.height != artH) {
                wrapLp.width = cardWidth;
                wrapLp.height = artH;
                h.wrap.setLayoutParams(wrapLp);
            }
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) h.itemView.getLayoutParams();
            lp.width = cardWidth;
            int gap = position == items.size() - 1 ? 0 : Math.round(GAP_DP * density);
            lp.leftMargin = rtl ? gap : 0;
            lp.rightMargin = rtl ? 0 : gap;
            h.itemView.setLayoutParams(lp);
            float radius = AppAdapter.cornerRadius(cardWidth, density);
            if (h.radius != radius) {
                h.radius = radius;
                h.art.invalidateOutline();
                h.glass.setRadius(radius);
            }
            h.title.setText(item.title != null && !item.title.trim().isEmpty() ? item.title : item.episodeTitle);
            String second = item.subtitle();
            h.subtitle.setText(second);
            h.subtitle.setVisibility(second == null || second.equals(h.title.getText().toString())
                    ? View.GONE : View.VISIBLE);
            float progress = item.progress();
            h.bar.setProgress(progress);
            h.bar.setVisibility(progress > 0.005f ? View.VISIBLE : View.GONE);
            String uri = item.art();
            h.uri = uri;
            h.image.setImageDrawable(null);
            if (uri == null) return;
            android.graphics.Bitmap hit = images.cached(uri);
            if (hit != null) {
                h.image.setImageBitmap(hit);
                return;
            }
            images.load(uri, cardWidth, artH, bmp -> {
                if (uri.equals(h.uri)) h.image.setImageBitmap(bmp);
            });
        }

        @Override
        public void onViewRecycled(@NonNull Holder h) {
            h.uri = null;
            h.image.setImageDrawable(null);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class Holder extends RecyclerView.ViewHolder {
        final View wrap, art;
        final ImageView image;
        final Bar bar;
        final GlassView glass;
        final TextView title, subtitle;
        String uri;
        float radius = -1f;

        Holder(LinearLayout root) {
            super(root);
            wrap = root.getChildAt(0);
            FrameLayout wrapGroup = (FrameLayout) wrap;
            art = wrapGroup.getChildAt(0);
            FrameLayout artGroup = (FrameLayout) art;
            image = (ImageView) artGroup.getChildAt(0);
            bar = (Bar) artGroup.getChildAt(1);
            glass = (GlassView) wrapGroup.getChildAt(1);
            title = (TextView) root.getChildAt(1);
            subtitle = (TextView) root.getChildAt(2);

            art.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius < 0 ? 0 : radius);
                }
            });
            art.setClipToOutline(true);
            art.setOnClickListener(v -> {
                int at = getBindingAdapterPosition();
                if (at != RecyclerView.NO_POSITION) listener.onOpen(items.get(at));
            });
            art.setOnFocusChangeListener((v, focused) -> {
                glass.setVisibility(focused ? View.VISIBLE : View.GONE);
                int at = getBindingAdapterPosition();
                if (focused && at != RecyclerView.NO_POSITION) listener.onFocused(at);
            });
            art.setOnKeyListener((v, code, event) -> {
                boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
                if (down) held = event.getRepeatCount() > 0;
                int at = getBindingAdapterPosition();
                switch (code) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        return down && (rtl ? at >= items.size() - 1 : at <= 0);
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return down && (rtl ? at <= 0 : at >= items.size() - 1);
                    case KeyEvent.KEYCODE_DPAD_UP:
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (down) listener.onLeave();
                        return true;
                    case KeyEvent.KEYCODE_BACK:
                    case KeyEvent.KEYCODE_ESCAPE:
                        if (!down && !event.isCanceled()) listener.onLeave();
                        return true;
                    default:
                        return false;
                }
            });
        }
    }

    /** card (art with its glass) over title and second line. */
    private LinearLayout build() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout wrap = new FrameLayout(context);
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);
        root.addView(wrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        FrameLayout art = new FrameLayout(context);
        art.setFocusable(true);
        art.setClickable(true);
        art.setBackground(new ColorDrawable(0x33FFFFFF));
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        art.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        Bar bar = new Bar(context);
        art.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(BAR_DP * density), Gravity.BOTTOM));
        wrap.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        GlassView glass = new GlassView(context, null);
        glass.setStyle(glassStyle);
        glass.setVisibility(View.GONE);
        glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams glassLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        int pad = -Math.round(GLASS_PAD_DP * density);
        glassLp.setMargins(pad, pad, pad, pad);
        wrap.addView(glass, glassLp);

        TextView title = text(R.string.font_body, 13f, 0xFFFFFFFF);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Math.round(6 * density);
        root.addView(title, titleLp);
        TextView subtitle = text(R.string.font_light, 11f, 0xB3FFFFFF);
        root.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private TextView text(int font, float sp, int color) {
        TextView t = new TextView(context);
        t.setTypeface(UiFont.get(context, font));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(4 * density, 0, density, 0x99000000);
        return t;
    }

    /** The watched share along the card's bottom edge. */
    static final class Bar extends View {
        private final Paint paint = new Paint();
        private float progress;

        Bar(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setProgress(float p) {
            if (p == progress) return;
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            paint.setColor(0x59FFFFFF);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRect(0, 0, getWidth() * Math.max(0f, Math.min(1f, progress)), getHeight(), paint);
        }
    }
}

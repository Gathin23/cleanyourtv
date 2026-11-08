package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

final class HomeRail extends RecyclerView {
  interface Events {
    void selected(Object item);

    void clicked(Object item);

    void held(Object item);

    void vertical(boolean up);

    boolean motion();

    String sourceName(String pkg);
  }

  private final List<Object> items = new ArrayList<>();
  private final Events events;
  private final ArtworkLoader artwork;
  private final boolean media;
  private final Adapter<Tile> cards =
      new Adapter<Tile>() {
        @Override
        public Tile onCreateViewHolder(ViewGroup parent, int type) {
          return new Tile(parent.getContext());
        }

        @Override
        public void onBindViewHolder(Tile tile, int position) {
          tile.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
          return items.size();
        }

        @Override
        public void onViewRecycled(Tile holder) {
          holder.image.setTag(null);
          holder.image.setImageDrawable(null);
        }
      };
  private float scale = 1;
  private int lastPosition;

  HomeRail(Context c, boolean media, ArtworkLoader artwork, Events events) {
    super(c);
    this.media = media;
    this.artwork = artwork;
    this.events = events;
    setLayoutManager(new LinearLayoutManager(c, HORIZONTAL, false));
    setAdapter(cards);
    setItemAnimator(null);
    setClipToPadding(false);
    setClipChildren(false);
    setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    setFocusable(false);
    setHasFixedSize(true);
    setItemViewCacheSize(2);
  }

  void scale(float value) {
    if (Math.abs(value - scale) < .01f) return;
    scale = value;
    cards.notifyDataSetChanged();
  }

  void submit(List<?> values) {
    Object focused = lastPosition < items.size() ? items.get(lastPosition) : null;
    String id = identity(focused);
    boolean restore = hasFocus();
    items.clear();
    items.addAll(values);
    lastPosition = Math.min(lastPosition, Math.max(0, items.size() - 1));
    for (int i = 0; i < items.size(); i++)
      if (identity(items.get(i)).equals(id)) {
        lastPosition = i;
        break;
      }
    cards.notifyDataSetChanged();
    if (restore) post(this::focusLast);
  }

  private static String identity(Object item) {
    return item instanceof MediaEntry
        ? ((MediaEntry) item).key
        : item instanceof AppCatalog.App ? ((AppCatalog.App) item).pkg : "apps";
  }

  boolean empty() {
    return items.isEmpty();
  }

  void focusLast() {
    if (items.isEmpty()) return;
    scrollToPosition(lastPosition);
    post(
        () -> {
          ViewHolder h = findViewHolderForAdapterPosition(lastPosition);
          if (h != null) h.itemView.requestFocus();
        });
  }

  private int px(float units) {
    return Math.round(units * scale);
  }

  private final class Tile extends ViewHolder {
    final FrameLayout frame;
    final ImageView image;
    final TextView label;
    final TextView fallback;
    final View progress;
    Object item;

    Tile(Context c) {
      super(new FrameLayout(c));
      frame = (FrameLayout) itemView;
      frame.setFocusable(true);
      frame.setClickable(true);
      frame.setId(View.generateViewId());
      frame.setClipChildren(false);
      fallback = ElppaUi.text(c, 13);
      fallback.setMaxLines(3);
      fallback.setGravity(Gravity.CENTER);
      fallback.setEllipsize(android.text.TextUtils.TruncateAt.END);
      frame.addView(fallback);
      image = new ImageView(c);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      frame.addView(image);
      label = ElppaUi.text(c, 11);
      label.setMaxLines(1);
      label.setEllipsize(android.text.TextUtils.TruncateAt.END);
      label.setGravity(Gravity.CENTER_VERTICAL);
      frame.addView(label);
      progress = new View(c);
      progress.setBackgroundColor(ElppaUi.TEXT);
      frame.addView(progress);
      frame.setOnClickListener(v -> events.clicked(item));
      frame.setOnLongClickListener(
          v -> {
            events.held(item);
            return true;
          });
      frame.setOnFocusChangeListener(
          (v, on) -> {
            ElppaUi.focus(frame, on, events.motion(), px(10));
            label.setVisibility(on ? VISIBLE : INVISIBLE);
            if (on) {
              int p = getBindingAdapterPosition();
              if (p != NO_POSITION) lastPosition = p;
              events.selected(item);
            }
          });
      frame.setOnKeyListener(
          (v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
              if (event.getAction() == KeyEvent.ACTION_DOWN)
                events.vertical(key == KeyEvent.KEYCODE_DPAD_UP);
              return true;
            }
            int p = getBindingAdapterPosition();
            if ((key == KeyEvent.KEYCODE_DPAD_LEFT && p == 0)
                || (key == KeyEvent.KEYCODE_DPAD_RIGHT && p == items.size() - 1)) return true;
            return false;
          });
    }

    void bind(Object next) {
      item = next;
      int w = px(media ? 172 : 116), h = px(media ? 97 : 76);
      RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, h);
      lp.setMargins(px(3), px(5), px(10), px(4));
      frame.setLayoutParams(lp);
      frame.setBackground(ElppaUi.shape(ElppaUi.SURFACE, px(10), frame.hasFocus()));
      fallback.setLayoutParams(
          new FrameLayout.LayoutParams(w - px(24), h - px(24), Gravity.CENTER));
      ElppaUi.type(fallback, 13, scale);
      fallback.setVisibility(media ? VISIBLE : GONE);
      image.setLayoutParams(
          new FrameLayout.LayoutParams(
              w - px(6), media ? h - px(6) : h - px(17), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
      image.setTranslationY(px(3));
      image.setBackground(ElppaUi.shape(Color.TRANSPARENT, px(8), false));
      image.setClipToOutline(true);
      FrameLayout.LayoutParams textLp =
          new FrameLayout.LayoutParams(
              w - px(16), px(19), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      textLp.bottomMargin = px(media ? 5 : 0);
      label.setLayoutParams(textLp);
      ElppaUi.type(label, media ? 10 : 10.5f, scale);
      label.setVisibility(frame.hasFocus() ? VISIBLE : INVISIBLE);
      label.setBackgroundColor(media ? 0xBB090B10 : Color.TRANSPARENT);
      label.setPadding(px(4), 0, px(4), 0);
      if (next instanceof MediaEntry) {
        MediaEntry e = (MediaEntry) next;
        artwork.load(image, e.heroArt(), px(344), px(194));
        fallback.setText(e.title);
        label.setText(e.title + " · " + events.sourceName(e.source));
        frame.setContentDescription(
            e.title + ", " + events.sourceName(e.source) + ", " + e.subtitle);
        FrameLayout.LayoutParams bar =
            new FrameLayout.LayoutParams(
                Math.max(1, (w - px(12)) * e.progress() / 100),
                px(2),
                Gravity.BOTTOM | Gravity.LEFT);
        bar.leftMargin = px(6);
        bar.bottomMargin = px(3);
        progress.setLayoutParams(bar);
        progress.setVisibility(e.hasProgress() ? VISIBLE : GONE);
      } else {
        image.setTag(null);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(px(9), px(9), px(9), px(9));
        progress.setVisibility(GONE);
        if (next instanceof AppCatalog.App) {
          AppCatalog.App app = (AppCatalog.App) next;
          image.setImageDrawable(app.art(getContext().getPackageManager()));
          label.setText(app.label);
          frame.setContentDescription(app.label);
        } else {
          image.setImageResource(R.drawable.ic_apps);
          label.setText(R.string.elppa_all_apps);
          frame.setContentDescription(getContext().getString(R.string.elppa_all_apps));
        }
      }
      frame.setScaleX(frame.hasFocus() ? 1.035f : 1);
      frame.setScaleY(frame.hasFocus() ? 1.035f : 1);
    }
  }
}

package io.github.anhost77.salon;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nouveautés: what each streaming platform installed on the TV added lately in France (TMDB's watch-provider data),
 * films and series: the last months' releases, then the rest of the catalogue, most watched first, for one style or
 * all. The chosen title shows on top with its picture and summary, a trailer played in the app and a button that
 * opens the platform's app. D-pad: Rechercher and the platforms, the styles, then the Films and Séries rows.
 */
public final class ReleasesActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<>(80);
    private final Map<String, List<Tmdb.Title>[]> loaded = new HashMap<>();
    private final List<String[]> platforms = new ArrayList<>();
    private final List<View> chips = new ArrayList<>();
    private final List<View> styleChips = new ArrayList<>();
    /** -1 for every style, else an index into {@link Tmdb#STYLES}. */
    private int style = -1;
    private LinearLayout films, series;
    private TextView filmsTitle, seriesTitle, name, meta, overview, empty, open;
    private ImageView backdrop;
    private View detailView;
    private Tmdb.Title shown;
    private int platform = -1;
    private int request;
    private float u;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = getResources().getDisplayMetrics().heightPixels / 1080f;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B2230, 0xFF0D1118, 0xFF06080C}));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(80), px(40), px(80), px(30));
        root.addView(page);

        // Platforms installed on this TV, and those the CANAL+ offer carries inside myCANAL (Paramount+, Apple TV,
        // HBO Max): those open myCANAL. Entries: name, TMDB provider, package, the app the button names.
        PackageManager pm = getPackageManager();
        String canal = "com.canal.android.canal";
        boolean hasCanal = pm.getLeanbackLaunchIntentForPackage(canal) != null;
        for (String[] p : Tmdb.PLATFORMS) {
            if (Settings.off(Settings.PLATFORMS, p[1])) continue; // Réglages
            if (pm.getLeanbackLaunchIntentForPackage(p[2]) != null) platforms.add(new String[] {p[0], p[1], p[2], p[0]});
            else if (hasCanal && Tmdb.VIA_MYCANAL.contains(p[1])) platforms.add(new String[] {p[0], p[1], canal, "myCANAL"});
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipScroll.addView(chipRow);
        page.addView(chipScroll);
        TextView search = text(getString(R.string.search_chip), 27, Typeface.BOLD, Color.WHITE);
        search.setPadding(px(28), px(12), px(28), px(12));
        search.setBackground(pill(0x33FFFFFF));
        search.setFocusable(true);
        search.setClickable(true);
        search.setOnFocusChangeListener((v, has) -> {
            v.setBackground(pill(has ? Color.WHITE : 0x33FFFFFF));
            ((TextView) v).setTextColor(has ? Color.BLACK : Color.WHITE);
        });
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.rightMargin = px(24);
        chipRow.addView(search, sl);
        for (int i = 0; i < platforms.size(); i++) {
            TextView t = text(platforms.get(i)[0], 27, Typeface.BOLD, 0xCCFFFFFF);
            t.setPadding(px(28), px(12), px(28), px(12));
            t.setFocusable(true);
            t.setClickable(true);
            final int index = i;
            t.setOnFocusChangeListener((v, has) -> {
                paintChips();
                if (has) select(index);
            });
            t.setOnClickListener(v -> select(index));
            t.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN) styleChips.get(style + 1).requestFocus();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(10);
            chipRow.addView(t, lp);
            chips.add(t);
        }

        // The styles.
        HorizontalScrollView styleScroll = new HorizontalScrollView(this);
        styleScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setPadding(0, px(12), 0, 0);
        styleScroll.addView(styleRow);
        page.addView(styleScroll);
        for (int i = -1; i < Tmdb.STYLES.length; i++) {
            TextView t = text(i < 0 ? getString(R.string.styles_all) : Tmdb.STYLES[i][0], 22, Typeface.NORMAL, 0x99FFFFFF);
            t.setPadding(px(22), px(8), px(22), px(8));
            t.setFocusable(true);
            t.setClickable(true);
            final int index = i;
            t.setOnFocusChangeListener((v, has) -> {
                paintStyles();
                if (has && style != index) setStyle(index);
            });
            t.setOnClickListener(v -> setStyle(index));
            t.setOnKeyListener((v, code, e) -> {
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN && platform >= 0) chips.get(platform).requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // Top to bottom, as on screen: the shown title's buttons come before the posters.
                    if (e.getAction() == KeyEvent.ACTION_DOWN) {
                        if (open.getVisibility() == View.VISIBLE && shown != null) open.requestFocus();
                        else focusFirst(films.getChildCount() > 0 ? films : series);
                    }
                    return true;
                }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(8);
            styleRow.addView(t, lp);
            styleChips.add(t);
        }
        paintStyles();

        // The chosen title.
        LinearLayout detail = new LinearLayout(this);
        detail.setPadding(0, px(18), 0, px(12));
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackground(pill(0x22FFFFFF));
        backdrop.setClipToOutline(true);
        detail.addView(backdrop, new LinearLayout.LayoutParams(px(530), px(298)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(40), 0, 0, 0);
        name = text("", 40, Typeface.BOLD, Color.WHITE);
        words.addView(name);
        meta = text("", 24, Typeface.NORMAL, 0x99FFFFFF);
        meta.setPadding(0, px(8), 0, px(12));
        words.addView(meta);
        overview = text("", 24, Typeface.NORMAL, 0xE6FFFFFF);
        overview.setSingleLine(false);
        overview.setMaxLines(5);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(0, 1.12f);
        words.addView(overview, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout buttons = new LinearLayout(this);
        open = button("", true);
        open.setOnClickListener(v -> openPlatform());
        TextView trailer = button(getString(R.string.tonight_trailer), false);
        trailer.setOnClickListener(v -> {
            if (shown != null) startActivity(new Intent(this, TrailerActivity.class).putExtra("tmdb", shown.id)
                    .putExtra("tv", shown.tv).putExtra("title", shown.name));
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.rightMargin = px(18);
        buttons.addView(open, bl);
        buttons.addView(trailer);
        for (TextView b : new TextView[] {open, trailer}) {
            b.setOnKeyListener((v, code, e) -> {
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN) styleChips.get(style + 1).requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN) {
                        if (lastPoster != null && lastPoster.isAttachedToWindow()) lastPoster.requestFocus();
                        else focusFirst(films.getChildCount() > 0 ? films : series);
                    }
                    return true;
                }
                return Nav.lastInRow(v, code);
            });
        }
        words.addView(buttons);
        detail.addView(words, new LinearLayout.LayoutParams(0, -1, 1f));
        page.addView(detail, new LinearLayout.LayoutParams(-1, px(330)));
        detailView = detail;

        // The rows.
        filmsTitle = text(getString(R.string.releases_films), 24, Typeface.BOLD, 0xB3FFFFFF);
        page.addView(filmsTitle);
        films = row(page);
        seriesTitle = text(getString(R.string.releases_series), 24, Typeface.BOLD, 0xB3FFFFFF);
        seriesTitle.setPadding(0, px(6), 0, 0);
        page.addView(seriesTitle);
        series = row(page);

        empty = text(getString(R.string.releases_loading), 30, Typeface.NORMAL, 0xB3FFFFFF);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        if (!Tmdb.enabled() || platforms.isEmpty()) {
            empty.setText(R.string.releases_off);
            return;
        }
        chips.get(0).requestFocus();
        select(0);
    }

    private LinearLayout row(LinearLayout page) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        // Room for the focused poster, which grows 8 %, and its white frame: nothing in the row clips it.
        row.setPadding(px(14), px(14), px(14), px(14));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        scroll.setClipChildren(false);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        scroll.addView(row);
        page.addView(scroll);
        return row;
    }

    private void select(int index) {
        if (index == platform) return;
        platform = index;
        paintChips();
        open.setText(getString(R.string.guide_watch, platforms.get(index)[3]));
        reload();
    }

    private void setStyle(int index) {
        style = index;
        paintStyles();
        if (platform >= 0) reload();
    }

    private void paintStyles() {
        for (int i = 0; i < styleChips.size(); i++) {
            TextView t = (TextView) styleChips.get(i);
            boolean focused = t.hasFocus(), current = i - 1 == style;
            GradientDrawable d = pill(focused ? Color.WHITE : current ? 0x33FFFFFF : 0x00000000);
            if (!focused && !current) d.setStroke(Math.max(1, px(2)), 0x33FFFFFF);
            t.setBackground(d);
            t.setTextColor(focused ? Color.BLACK : current ? Color.WHITE : 0x99FFFFFF);
        }
    }

    private void reload() {
        final int index = platform;
        final int st = style;
        String id = platforms.get(index)[1];
        String key = id + ":" + st;
        List<Tmdb.Title>[] ready = loaded.get(key);
        if (ready != null) {
            fill(ready);
            return;
        }
        films.removeAllViews();
        series.removeAllViews();
        detailView.setVisibility(View.INVISIBLE);
        shown = null;
        empty.setText(R.string.releases_loading);
        empty.setVisibility(View.VISIBLE);
        io.execute(() -> {
            @SuppressWarnings("unchecked")
            List<Tmdb.Title>[] both = new List[2];
            try {
                String[] chosen = st < 0 ? null : Tmdb.STYLES[st];
                both[0] = Tmdb.catalogue(id, false, chosen);
                both[1] = Tmdb.catalogue(id, true, chosen);
            } catch (Exception offline) {
                both[0] = new ArrayList<>();
                both[1] = new ArrayList<>();
            }
            main.post(() -> {
                if (isFinishing()) return;
                if (!both[0].isEmpty() || !both[1].isEmpty()) loaded.put(key, both);
                if (platform == index && style == st) fill(both);
            });
        });
    }

    private void fill(List<Tmdb.Title>[] both) {
        boolean none = both[0].isEmpty() && both[1].isEmpty();
        empty.setText(R.string.releases_none);
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        detailView.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
        if (none) shown = null;
        posters(films, filmsTitle, both[0]);
        posters(series, seriesTitle, both[1]);
        if (!none) show(!both[0].isEmpty() ? both[0].get(0) : both[1].get(0));
    }

    /** The poster the remote left for the buttons above: Down goes back to it. */
    private View lastPoster;

    private void posters(LinearLayout row, TextView title, List<Tmdb.Title> list) {
        row.removeAllViews();
        title.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        ((View) row.getParent()).setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        for (Tmdb.Title t : list) {
            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(pill(0x22FFFFFF));
            poster.setClipToOutline(true);
            poster.setFocusable(true);
            poster.setClickable(true);
            poster.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(120).start();
                v.setElevation(has ? px(10) : 0);
                v.setForeground(has ? frame() : null);
                if (has) show(t);
            });
            poster.setOnHoverListener((v, e) -> {
                if (e.getAction() == android.view.MotionEvent.ACTION_HOVER_ENTER) v.setForeground(frame());
                else if (e.getAction() == android.view.MotionEvent.ACTION_HOVER_EXIT && !v.hasFocus()) v.setForeground(null);
                return false;
            });
            poster.setOnClickListener(v -> startActivity(new Intent(this, TitleActivity.class)
                    .putExtra("tmdb", t.id).putExtra("tv", t.tv)));
            poster.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_UP || row != films && ((View) films.getParent()).getVisibility() == View.VISIBLE) return false;
                // Up from the top row: the shown title's buttons (Regarder, Bande-annonce), then the styles.
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    lastPoster = v;
                    if (open.getVisibility() == View.VISIBLE) open.requestFocus();
                    else styleChips.get(style + 1).requestFocus();
                }
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(140), px(210));
            lp.rightMargin = px(16);
            row.addView(poster, lp);
            load(poster, Tmdb.image(t.poster, "w185"));
        }
    }

    private void show(Tmdb.Title t) {
        if (t == shown) return;
        shown = t;
        name.setText(t.name);
        String year = t.date.length() >= 10 ? formatDate(t.date) : t.date;
        meta.setText((t.tv ? getString(R.string.releases_serie) : getString(R.string.releases_film))
                + (year.isEmpty() ? "" : " · " + year) + (t.vote > 0 ? String.format(Locale.FRANCE, " · ★ %.1f", t.vote) : ""));
        overview.setText(t.overview.isEmpty() ? getString(R.string.tonight_no_desc) : t.overview);
        backdrop.setImageDrawable(null);
        load(backdrop, Tmdb.image(t.backdrop.isEmpty() ? t.poster : t.backdrop, "w780"));
    }

    private String formatDate(String iso) {
        try {
            return new SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private void load(ImageView view, String url) {
        if (url.isEmpty()) return;
        Bitmap hit = cache.get(url);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setTag(url);
        int mine = ++request;
        images.execute(() -> {
            Bitmap b = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8_000);
                c.setReadTimeout(10_000);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                try (InputStream in = c.getInputStream()) {
                    b = BitmapFactory.decodeStream(in, null, o);
                }
            } catch (Exception offline) {
                // The frame stays empty.
            } finally {
                if (c != null) c.disconnect();
            }
            Bitmap done = b;
            main.post(() -> {
                if (done == null || isFinishing()) return;
                cache.put(url, done);
                if (url.equals(view.getTag())) view.setImageBitmap(done);
            });
        });
    }

    /** "Regarder sur…": the platform's app, on the shown title where it allows it ({@link Platforms}). */
    private void openPlatform() {
        if (platform < 0) return;
        String[] p = platforms.get(platform);
        Tmdb.Title t = shown;
        io.execute(() -> {
            Intent i = Platforms.intentFor(this, p, t);
            main.post(() -> Platforms.start(this, i, p[2]));
        });
    }

    private void focusFirst(LinearLayout row) {
        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }

    private void paintChips() {
        for (int i = 0; i < chips.size(); i++) {
            TextView t = (TextView) chips.get(i);
            boolean focused = t.hasFocus(), current = i == platform;
            t.setBackground(focused ? pill(Color.WHITE) : current ? pill(0x33FFFFFF) : null);
            t.setTextColor(focused ? Color.BLACK : current ? Color.WHITE : 0x99FFFFFF);
        }
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 25, Typeface.BOLD, primary ? Color.BLACK : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(32), px(14), px(32), px(14));
        b.setBackground(pill(primary ? Color.WHITE : 0x33FFFFFF));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start());
        return b;
    }

    /** The white frame of the poster under the remote's focus or the mouse. */
    private GradientDrawable frame() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setStroke(px(5), Color.WHITE);
        d.setCornerRadius(px(16));
        return d;
    }

    private GradientDrawable pill(int colour) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(px(16));
        return d;
    }

    private TextView text(String s, float size, int style, int colour) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTypeface(Typeface.create("sans-serif", style));
        t.setTextColor(colour);
        t.setSingleLine(true);
        return t;
    }

    private int px(float at1080) {
        return Math.round(at1080 * u);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        images.shutdownNow();
        super.onDestroy();
    }
}

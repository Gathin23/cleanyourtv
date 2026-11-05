package io.github.anhost77.salon;

import android.app.Activity;
import android.content.Context;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search every platform at once: type a film or a series (the TV keyboard or the phone's), and for the chosen result
 * see on which of the household's platforms it streams in France (TMDB watch providers), with a button for each, and
 * its trailer played in the app. Platforms the CANAL+ offer carries (Paramount+, Apple TV, HBO Max) open myCANAL.
 */
public final class SearchActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<>(60);
    private final Map<Integer, Set<String>> where = new HashMap<>();
    /** The household's platforms: name, TMDB provider, package, app the button names. */
    private final List<String[]> platforms = new ArrayList<>();
    private EditText box;
    private LinearLayout results, buttons;
    private TextView name, meta, overview, available, empty;
    private ImageView backdrop;
    private View detail;
    private Tmdb.Title shown;
    private int request;
    private float u;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = getResources().getDisplayMetrics().heightPixels / 1080f;
        PackageManager pm = getPackageManager();
        String canal = "com.canal.android.canal";
        boolean hasCanal = pm.getLeanbackLaunchIntentForPackage(canal) != null;
        for (String[] p : Tmdb.PLATFORMS) {
            if (Settings.off(Settings.PLATFORMS, p[1])) continue; // Réglages
            if (pm.getLeanbackLaunchIntentForPackage(p[2]) != null) platforms.add(new String[] {p[0], p[1], p[2], p[0]});
            else if (hasCanal && Tmdb.VIA_MYCANAL.contains(p[1])) platforms.add(new String[] {p[0], p[1], canal, "myCANAL"});
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1B2230, 0xFF0D1118, 0xFF06080C}));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(80), px(40), px(80), px(30));
        root.addView(page);

        box = new EditText(this);
        box.setHint(R.string.search_hint);
        box.setHintTextColor(0x80FFFFFF);
        box.setTextColor(Color.WHITE);
        box.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32 * u);
        box.setSingleLine(true);
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.setPadding(px(30), px(16), px(30), px(16));
        box.setBackground(pill(0x26FFFFFF));
        box.setOnFocusChangeListener((v, has) -> v.setBackground(has ? framed(0x33FFFFFF) : pill(0x26FFFFFF)));
        box.setOnEditorActionListener((v, action, e) -> {
            search(box.getText().toString());
            return true;
        });
        page.addView(box, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout info = new LinearLayout(this);
        info.setPadding(0, px(26), 0, px(14));
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setBackground(pill(0x22FFFFFF));
        backdrop.setClipToOutline(true);
        info.addView(backdrop, new LinearLayout.LayoutParams(px(530), px(298)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(40), 0, 0, 0);
        name = text("", 38, Typeface.BOLD, Color.WHITE);
        words.addView(name);
        meta = text("", 24, Typeface.NORMAL, 0x99FFFFFF);
        meta.setPadding(0, px(6), 0, px(8));
        words.addView(meta);
        available = text("", 24, Typeface.BOLD, 0xFFFFD27A);
        available.setPadding(0, 0, 0, px(8));
        words.addView(available);
        overview = text("", 23, Typeface.NORMAL, 0xE6FFFFFF);
        overview.setSingleLine(false);
        overview.setMaxLines(3);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(overview, new LinearLayout.LayoutParams(-1, 0, 1f));
        HorizontalScrollView bs = new HorizontalScrollView(this);
        bs.setHorizontalScrollBarEnabled(false);
        buttons = new LinearLayout(this);
        bs.addView(buttons);
        words.addView(bs);
        info.addView(words, new LinearLayout.LayoutParams(0, -1, 1f));
        detail = info;
        detail.setVisibility(View.INVISIBLE);
        page.addView(info, new LinearLayout.LayoutParams(-1, px(340)));

        HorizontalScrollView rs = new HorizontalScrollView(this);
        rs.setHorizontalScrollBarEnabled(false);
        rs.setClipToPadding(false);
        results = new LinearLayout(this);
        results.setPadding(px(16), px(16), px(16), px(16));
        results.setClipChildren(false);
        results.setClipToPadding(false);
        rs.setClipChildren(false);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        rs.addView(results);
        page.addView(rs);

        empty = text(getString(R.string.search_start), 30, Typeface.NORMAL, 0xB3FFFFFF);
        empty.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(-1, px(300), Gravity.BOTTOM);
        root.addView(empty, el);
        setContentView(root);
        box.requestFocus();
        main.postDelayed(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(box, InputMethodManager.SHOW_IMPLICIT), 300);
    }

    private void search(String query) {
        if (query.trim().isEmpty()) return;
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(box.getWindowToken(), 0);
        empty.setText(R.string.search_running);
        empty.setVisibility(View.VISIBLE);
        results.removeAllViews();
        detail.setVisibility(View.INVISIBLE);
        int mine = ++request;
        io.execute(() -> {
            List<Tmdb.Title> found;
            try {
                found = Tmdb.search(query);
            } catch (Exception offline) {
                found = new ArrayList<>();
            }
            List<Tmdb.Title> list = found;
            main.post(() -> {
                if (mine != request || isFinishing()) return;
                empty.setText(R.string.search_nothing);
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                for (Tmdb.Title t : list) addPoster(t);
                if (!list.isEmpty()) results.getChildAt(0).requestFocus();
            });
        });
    }

    private void addPoster(Tmdb.Title t) {
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(pill(0x22FFFFFF));
        poster.setClipToOutline(true);
        poster.setFocusable(true);
        poster.setClickable(true);
        poster.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(120).start();
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
            if (code != KeyEvent.KEYCODE_DPAD_UP) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (buttons.getChildCount() > 0) buttons.getChildAt(0).requestFocus();
                else box.requestFocus();
            }
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(160), px(240));
        lp.rightMargin = px(18);
        results.addView(poster, lp);
        load(poster, Tmdb.image(t.poster, "w185"));
    }

    private void show(Tmdb.Title t) {
        if (t == shown) return;
        shown = t;
        detail.setVisibility(View.VISIBLE);
        name.setText(t.name);
        String year = t.date.length() >= 4 ? t.date.substring(0, 4) : "";
        meta.setText((t.tv ? getString(R.string.releases_serie) : getString(R.string.releases_film))
                + (year.isEmpty() ? "" : " · " + year) + (t.vote > 0 ? String.format(Locale.FRANCE, " · ★ %.1f", t.vote) : ""));
        overview.setText(t.overview.isEmpty() ? getString(R.string.tonight_no_desc) : t.overview);
        backdrop.setImageDrawable(null);
        load(backdrop, Tmdb.image(t.backdrop.isEmpty() ? t.poster : t.backdrop, "w780"));
        Set<String> known = where.get(t.id * (t.tv ? -1 : 1));
        if (known != null) {
            fillButtons(t, known);
            return;
        }
        available.setText(R.string.search_where);
        buttons.removeAllViews();
        io.execute(() -> {
            Set<String> p;
            try {
                p = Tmdb.providers(t);
            } catch (Exception offline) {
                p = new java.util.HashSet<>();
            }
            Set<String> found = p;
            main.post(() -> {
                where.put(t.id * (t.tv ? -1 : 1), found);
                if (shown == t) fillButtons(t, found);
            });
        });
    }

    /** "Disponible sur Netflix · Canal+", a Regarder button per platform of the household, then the trailer. */
    private void fillButtons(Tmdb.Title t, Set<String> providers) {
        buttons.removeAllViews();
        List<String> names = new ArrayList<>();
        for (String[] p : platforms) {
            if (!providers.contains(p[1])) continue;
            names.add(p[0]);
            TextView b = button(getString(R.string.guide_watch, p[0].equals(p[3]) ? p[0] : p[0] + " (myCANAL)"), true);
            b.setOnClickListener(v -> io.execute(() -> {
                Intent i = Platforms.intentFor(this, p, t);
                main.post(() -> Platforms.start(this, i, p[2]));
            }));
            buttons.addView(b);
        }
        available.setText(names.isEmpty() ? getString(R.string.search_nowhere)
                : getString(R.string.search_on, TextUtils.join(" · ", names)));
        TextView trailer = button(getString(R.string.tonight_trailer), false);
        trailer.setOnClickListener(v -> startActivity(new Intent(this, TrailerActivity.class)
                .putExtra("tmdb", t.id).putExtra("tv", t.tv).putExtra("title", t.name)));
        buttons.addView(trailer);
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 24, Typeface.BOLD, primary ? Color.BLACK : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(28), px(12), px(28), px(12));
        b.setBackground(pill(primary ? Color.WHITE : 0x33FFFFFF));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnFocusChangeListener((v, has) -> {
            v.setForeground(has ? frame() : null);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
        });
        b.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return Nav.lastInRow(v, code);
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                for (int i = 0; i < results.getChildCount(); i++) {
                    View p = results.getChildAt(i);
                    if (p.getForeground() != null || i == results.getChildCount() - 1) {
                        p.requestFocus();
                        break;
                    }
                }
            }
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = px(14);
        b.setLayoutParams(lp);
        return b;
    }

    private void load(ImageView view, String url) {
        if (url.isEmpty()) return;
        Bitmap hit = cache.get(url);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setTag(url);
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

    private GradientDrawable frame() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setStroke(px(5), Color.WHITE);
        d.setCornerRadius(px(16));
        return d;
    }

    private GradientDrawable framed(int colour) {
        GradientDrawable d = pill(colour);
        d.setStroke(px(3), Color.WHITE);
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

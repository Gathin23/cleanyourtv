package io.github.anhost77.salon;

import android.app.Activity;
import android.content.Intent;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A film's or a series' sheet (extras "tmdb" id and "tv"): the big picture, year, genres, length or seasons, the
 * cast and who made it, the summary, "Voir sur…" for each of the household's platforms that carries it (opening the
 * app on the title where the app allows it), the trailer played in the app, and "Vous aimerez aussi", whose posters
 * open their own sheet.
 */
public final class TitleActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<>(40);
    private Tmdb.Title title;
    private String trailerKey;
    private LinearLayout buttons, recs;
    private TextView name, meta, people, overview, where, recsTitle, status;
    private ImageView backdrop;
    private float u;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        u = getResources().getDisplayMetrics().heightPixels / 1080f;
        int id = getIntent().getIntExtra("tmdb", 0);
        boolean tv = getIntent().getBooleanExtra("tv", false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF06080C);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xF206080C, 0xD906080C, 0x6606080C, 0x2006080C}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0xFF06080C, 0xB306080C, 0x0006080C}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, px(460), Gravity.BOTTOM));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(90), px(70), px(90), px(30));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page);
        name = text("", 56, Typeface.BOLD, Color.WHITE);
        page.addView(name, new LinearLayout.LayoutParams(px(1100), -2));
        meta = text("", 26, Typeface.NORMAL, 0xCCFFFFFF);
        meta.setPadding(0, px(10), 0, px(10));
        page.addView(meta);
        people = text("", 23, Typeface.NORMAL, 0xFFFFD27A);
        people.setSingleLine(false);
        people.setMaxLines(2);
        people.setEllipsize(TextUtils.TruncateAt.END);
        page.addView(people, new LinearLayout.LayoutParams(px(1050), -2));
        overview = text("", 25, Typeface.NORMAL, 0xEEFFFFFF);
        overview.setSingleLine(false);
        overview.setMaxLines(5);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(0, 1.15f);
        overview.setPadding(0, px(14), 0, px(14));
        page.addView(overview, new LinearLayout.LayoutParams(px(1050), -2));
        where = text("", 23, Typeface.BOLD, 0xFFFFD27A);
        where.setPadding(0, 0, 0, px(12));
        page.addView(where);
        HorizontalScrollView bs = new HorizontalScrollView(this);
        bs.setHorizontalScrollBarEnabled(false);
        bs.setClipChildren(false);
        buttons = new LinearLayout(this);
        buttons.setPadding(px(6), px(6), px(6), px(6));
        buttons.setClipChildren(false);
        bs.addView(buttons);
        page.addView(bs);
        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        recsTitle = text(getString(R.string.channel_recs), 23, Typeface.BOLD, 0xB3FFFFFF);
        recsTitle.setVisibility(View.INVISIBLE);
        page.addView(recsTitle);
        HorizontalScrollView rs = new HorizontalScrollView(this);
        rs.setHorizontalScrollBarEnabled(false);
        rs.setClipChildren(false);
        recs = new LinearLayout(this);
        recs.setPadding(px(12), px(12), px(12), px(12));
        recs.setClipChildren(false);
        recs.setClipToPadding(false);
        rs.addView(recs);
        page.addView(rs);

        status = text(getString(R.string.releases_loading), 30, Typeface.NORMAL, 0xB3FFFFFF);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        io.execute(() -> {
            JSONObject d = null;
            try {
                d = Tmdb.details(id, tv);
            } catch (Exception offline) {
                // Shown below.
            }
            JSONObject done = d;
            main.post(() -> {
                if (isFinishing()) return;
                if (done == null) {
                    status.setText(R.string.title_offline);
                    return;
                }
                status.setVisibility(View.GONE);
                fill(done, tv);
            });
        });
    }

    private void fill(JSONObject d, boolean tv) {
        try {
            title = Tmdb.fromJson(d, tv);
        } catch (Exception e) {
            return;
        }
        name.setText(title.name);
        List<String> bits = new ArrayList<>();
        bits.add(getString(tv ? R.string.releases_serie : R.string.releases_film));
        if (title.date.length() >= 4) bits.add(title.date.substring(0, 4));
        JSONArray genres = d.optJSONArray("genres");
        List<String> g = new ArrayList<>();
        for (int i = 0; genres != null && i < genres.length(); i++) g.add(genres.optJSONObject(i).optString("name"));
        if (!g.isEmpty()) bits.add(TextUtils.join(", ", g));
        if (tv) {
            int seasons = d.optInt("number_of_seasons");
            if (seasons > 0) bits.add(getResources().getQuantityString(R.plurals.title_seasons, seasons, seasons));
        } else if (d.optInt("runtime") > 0) {
            int m = d.optInt("runtime");
            bits.add(m / 60 + " h " + String.format(Locale.FRANCE, "%02d", m % 60));
        }
        if (title.vote > 0) bits.add(String.format(Locale.FRANCE, "★ %.1f", title.vote));
        meta.setText(TextUtils.join("  ·  ", bits));

        JSONObject credits = d.optJSONObject("credits");
        List<String> cast = new ArrayList<>();
        JSONArray c = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; c != null && i < Math.min(6, c.length()); i++) cast.add(c.optJSONObject(i).optString("name"));
        List<String> makers = new ArrayList<>();
        if (tv) {
            JSONArray by = d.optJSONArray("created_by");
            for (int i = 0; by != null && i < Math.min(2, by.length()); i++) makers.add(by.optJSONObject(i).optString("name"));
        } else {
            JSONArray crew = credits == null ? null : credits.optJSONArray("crew");
            for (int i = 0; crew != null && i < crew.length() && makers.size() < 2; i++) {
                JSONObject p = crew.optJSONObject(i);
                if ("Director".equals(p.optString("job"))) makers.add(p.optString("name"));
            }
        }
        String who = (cast.isEmpty() ? "" : getString(R.string.channel_with, TextUtils.join(", ", cast)))
                + (makers.isEmpty() ? "" : (cast.isEmpty() ? "" : "   ")
                + getString(tv ? R.string.title_created : R.string.channel_by, TextUtils.join(", ", makers)));
        people.setText(who);
        people.setVisibility(who.isEmpty() ? View.GONE : View.VISIBLE);
        overview.setText(title.overview.isEmpty() ? getString(R.string.tonight_no_desc) : title.overview);
        load(backdrop, Tmdb.image(title.backdrop.isEmpty() ? title.poster : title.backdrop, "w1280"));

        // Where to watch it, among the household's platforms.
        Set<String> providers = new HashSet<>();
        JSONObject wp = d.optJSONObject("watch/providers");
        JSONObject fr = wp == null || wp.optJSONObject("results") == null ? null : wp.optJSONObject("results").optJSONObject("FR");
        for (String kind : new String[] {"flatrate", "free", "ads"}) {
            JSONArray a = fr == null ? null : fr.optJSONArray(kind);
            for (int i = 0; a != null && i < a.length(); i++) providers.add(String.valueOf(a.optJSONObject(i).optInt("provider_id")));
        }
        List<String> names = new ArrayList<>();
        View first = null;
        for (String[] p : Platforms.household(this)) {
            if (!providers.contains(p[1])) continue;
            names.add(p[0]);
            TextView b = button(getString(R.string.title_watch, p[0]), first == null);
            b.setOnClickListener(v -> openOn(p));
            buttons.addView(b);
            if (first == null) first = b;
        }
        where.setText(names.isEmpty() ? getString(R.string.search_nowhere)
                : getString(R.string.search_on, TextUtils.join(" · ", names)));

        // The trailer, straight from this sheet's videos.
        JSONObject videos = d.optJSONObject("videos");
        JSONArray v = videos == null ? null : videos.optJSONArray("results");
        for (int i = 0; v != null && i < v.length() && trailerKey == null; i++) {
            JSONObject o = v.optJSONObject(i);
            if ("YouTube".equals(o.optString("site")) && "Trailer".equals(o.optString("type"))) trailerKey = o.optString("key");
        }
        for (int i = 0; v != null && i < v.length() && trailerKey == null; i++) {
            JSONObject o = v.optJSONObject(i);
            if ("YouTube".equals(o.optString("site"))) trailerKey = o.optString("key");
        }
        TextView trailer = button(getString(R.string.tonight_trailer), first == null);
        trailer.setOnClickListener(x -> {
            Intent i = new Intent(this, TrailerActivity.class).putExtra("title", title.name);
            if (trailerKey != null) i.putExtra("key", trailerKey);
            startActivity(i);
        });
        buttons.addView(trailer);
        (first != null ? first : trailer).requestFocus();

        // Vous aimerez aussi.
        JSONObject r = d.optJSONObject("recommendations");
        JSONArray rr = r == null ? null : r.optJSONArray("results");
        for (int i = 0; rr != null && i < rr.length() && recs.getChildCount() < 14; i++) {
            try {
                JSONObject o = rr.getJSONObject(i);
                boolean isTv = "tv".equals(o.optString("media_type", tv ? "tv" : "movie"));
                Tmdb.Title t = Tmdb.fromJson(o, isTv);
                if (t.poster.isEmpty() || t.overview.isEmpty()) continue;
                addRec(t);
            } catch (Exception skip) {
                // Next one.
            }
        }
        recsTitle.setVisibility(recs.getChildCount() > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    private void openOn(String[] platform) {
        Tmdb.Title t = title;
        io.execute(() -> {
            Intent i = Platforms.intentFor(this, platform, t);
            main.post(() -> Platforms.start(this, i, platform[2]));
        });
    }

    private void addRec(Tmdb.Title t) {
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(pill(0x22FFFFFF));
        poster.setClipToOutline(true);
        poster.setFocusable(true);
        poster.setClickable(true);
        poster.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(120).start();
            v.setForeground(has ? frame() : null);
            if (has) recsTitle.setText(getString(R.string.channel_recs) + " — " + t.name);
        });
        poster.setOnHoverListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_HOVER_ENTER) v.setForeground(frame());
            else if (e.getAction() == MotionEvent.ACTION_HOVER_EXIT && !v.hasFocus()) v.setForeground(null);
            return false;
        });
        poster.setOnClickListener(v -> startActivity(new Intent(this, TitleActivity.class)
                .putExtra("tmdb", t.id).putExtra("tv", t.tv)));
        poster.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_UP) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN && buttons.getChildCount() > 0) buttons.getChildAt(0).requestFocus();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(130), px(195));
        lp.rightMargin = px(16);
        recs.addView(poster, lp);
        load(poster, Tmdb.image(t.poster, "w185"));
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 26, Typeface.BOLD, primary ? Color.BLACK : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(34), px(15), px(34), px(15));
        b.setBackground(pill(primary ? Color.WHITE : 0x40FFFFFF));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnFocusChangeListener((v, has) -> {
            v.setForeground(has ? frame() : null);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(120).start();
        });
        b.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return Nav.lastInRow(v, code);
            if (e.getAction() == KeyEvent.ACTION_DOWN && recs.getChildCount() > 0) recs.getChildAt(0).requestFocus();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = px(16);
        b.setLayoutParams(lp);
        return b;
    }

    private void load(ImageView view, String url) {
        if (url == null || url.isEmpty()) return;
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
                c.setReadTimeout(12_000);
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
        d.setStroke(px(4), Color.WHITE);
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
        t.setShadowLayer(8 * u, 0, 2 * u, 0x99000000);
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

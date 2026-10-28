package com.gathin.artframe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Search the open-access collections by any words. Pick the museum, type, press Search; scrolling to the end loads
 * the next page. OK on a result opens it large, to save it in 4K.
 */
public class SearchActivity extends Activity {
    private final List<Sources.Result> results = new ArrayList<>();
    private final List<TextView> sourceButtons = new ArrayList<>();
    private Sources.Source source;
    private EditText query;
    private TextView status;
    private GridView grid;
    private String lastQuery = "";
    private int page;
    private boolean loading;
    private boolean more;
    private int token;
    private Prefs prefs;
    private TextView shape;
    private double screenRatio;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        source = Sources.byId(getSharedPreferences("settings", MODE_PRIVATE).getString("source", "aic"));
        prefs = new Prefs(this);
        screenRatio = Images.screenRatio(this);
        int pad = Ui.dp(this, 40);
        LinearLayout root = Ui.column(this);
        root.setPadding(pad, Ui.dp(this, 28), pad, 0);
        root.addView(Ui.heading(this, getString(R.string.search_museums)), Ui.gap(this, 0, 14));

        LinearLayout sources = Ui.row(this);
        for (Sources.Source s : Sources.ALL) {
            TextView b = Ui.button(this, getString(s.name()), v -> {
                source = s;
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("source", s.id()).apply();
                markSource();
                if (!lastQuery.isEmpty()) search(lastQuery);
            });
            b.setTag(s.id());
            sourceButtons.add(b);
            sources.addView(b, Ui.gap(this, 12, 0));
        }
        android.widget.HorizontalScrollView sourceScroll = new android.widget.HorizontalScrollView(this);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        sourceScroll.addView(sources);
        root.addView(sourceScroll, Ui.gap(this, 0, 14));
        shape = Ui.button(this, "", v -> {
            prefs.edit().putBoolean("shape_filter", !prefs.shapeFilter()).apply();
            markShape();
            if (!lastQuery.isEmpty()) search(lastQuery);
        });
        markShape();

        LinearLayout bar = Ui.row(this);
        query = new EditText(this);
        query.setSingleLine(true);
        query.setHint(R.string.search_hint);
        query.setTextColor(Ui.TEXT);
        query.setHintTextColor(Ui.MUTED);
        query.setTextSize(20);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setBackground(Ui.pill(this));
        query.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 12));
        query.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                search(query.getText().toString());
                return true;
            }
            return false;
        });
        bar.addView(query, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView go = Ui.button(this, getString(R.string.search), v -> search(query.getText().toString()));
        LinearLayout.LayoutParams gp = Ui.gap(this, 20, 0);
        gp.leftMargin = Ui.dp(this, 14);
        bar.addView(go, gp);
        bar.addView(shape);
        root.addView(bar, Ui.gap(this, 0, 10));
        // The search's outcome (how many, nothing found, or why it failed) on its own line under the box.
        status = Ui.text(this, getString(R.string.search_intro), 16, Ui.MUTED);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(Ui.dp(this, 22), 0, 0, Ui.dp(this, 12));
        root.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(Ui.dp(this, 16));
        grid.setHorizontalSpacing(Ui.dp(this, 16));
        grid.setSelector(Ui.shape(this, 0x00000000, 10, Ui.ACCENT));
        grid.setDrawSelectorOnTop(true);
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 28));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            try {
                Intent i = new Intent(this, PreviewActivity.class);
                i.putExtra(PreviewActivity.EXTRA_RESULT, results.get(position).toJson().toString());
                startActivity(i);
            } catch (JSONException ignored) {
                // Cannot happen with plain strings.
            }
        });
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {}

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (more && !loading && total > 0 && first + visible >= total - 5) fetch();
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.screen(this, root);
        markSource();
        query.requestFocus();
    }

    private void markShape() {
        shape.setText(prefs.shapeFilter() ? getString(R.string.shape_fits) : getString(R.string.shape_any));
        shape.setSelected(prefs.shapeFilter());
    }

    private void markSource() {
        for (TextView b : sourceButtons) b.setSelected(source.id().equals(b.getTag()));
    }

    private void search(String words) {
        words = words == null ? "" : words.trim();
        if (words.isEmpty()) return;
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
        lastQuery = words;
        page = 0;
        results.clear();
        adapter.notifyDataSetChanged();
        more = true;
        token++;
        loading = false;
        fetch();
    }

    private void fetch() {
        loading = true;
        int mine = token;
        String words = lastQuery;
        int p = page;
        Sources.Source s = source;
        status.setText(getString(R.string.searching, getString(s.name())));
        new Thread(() -> {
            List<Sources.Result> found = null;
            String error = null;
            int pagesUsed = 0;
            int[] seen = new int[1];
            try {
                // With the shape filter, keep reading pages until a screenful suits the TV (at most four pages).
                found = new java.util.ArrayList<>();
                boolean filter = prefs.shapeFilter();
                while (pagesUsed < 4) {
                    List<Sources.Result> pageHits = s.search(words, p + pagesUsed);
                    pagesUsed++;
                    seen[0] += pageHits.size();
                    for (Sources.Result r : pageHits) {
                        if (!filter || Sources.suits(r.width, r.height, screenRatio)) found.add(r);
                    }
                    if (pageHits.isEmpty() || !filter || found.size() >= 10) {
                        if (pageHits.isEmpty()) pagesUsed = -pagesUsed;
                        break;
                    }
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            int used = pagesUsed;
            List<Sources.Result> got = found;
            String err = error;
            runOnUiThread(() -> {
                if (mine != token) return;
                loading = false;
                if (got == null) {
                    more = false;
                    status.setText(getString(R.string.search_failed, err == null ? "" : err));
                    return;
                }
                page += Math.abs(used);
                more = used > 0;
                results.addAll(got);
                adapter.notifyDataSetChanged();
                if (results.isEmpty()) {
                    // Hits that the shape filter hid are not "nothing found": say so, and how to see them.
                    status.setText(seen[0] > 0 && prefs.shapeFilter() ? getString(R.string.none_fit, seen[0])
                            : getString(R.string.no_results));
                } else {
                    status.setText(getString(R.string.results, results.size(), getString(s.name())));
                }
                if (p == 0 && !results.isEmpty()) grid.requestFocus();
            });
        }, "search").start();
    }

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public Object getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Context c = parent.getContext();
            LibraryActivity.Tile t = convert instanceof LibraryActivity.Tile
                    ? (LibraryActivity.Tile) convert : new LibraryActivity.Tile(c);
            Sources.Result r = results.get(position);
            t.title.setText(r.title.isEmpty() ? c.getString(R.string.untitled) : r.title);
            t.badge.setVisibility(View.GONE);
            Ui.thumb(t.image, "web:" + r.thumb, null, r.thumb);
            return t;
        }
    };
}

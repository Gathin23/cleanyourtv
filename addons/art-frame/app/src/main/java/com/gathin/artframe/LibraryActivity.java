package com.gathin.artframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The library: buttons for art mode, museum search, phone upload and settings; a row of collections (All first);
 * and the saved pieces. OK on a piece opens its page; holding OK on a collection offers it for the art button.
 */
public class LibraryActivity extends Activity {
    private Library lib;
    private TextView status;
    private LinearLayout chips;
    private GridView grid;
    private final List<Artwork> shown = new ArrayList<>();
    /** null: all pieces. */
    private String filter;
    private long seenVersion = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = Library.get(this);
        int pad = Ui.dp(this, 40);
        LinearLayout root = Ui.column(this);
        root.setPadding(pad, Ui.dp(this, 28), pad, 0);

        LinearLayout top = Ui.row(this);
        top.addView(Ui.heading(this, getString(R.string.app_name)), Ui.gap(this, 28, 0));
        status = Ui.text(this, "", 16, Ui.MUTED);
        top.addView(status);
        root.addView(top, Ui.gap(this, 0, 18));

        LinearLayout actions = Ui.row(this);
        TextView show = Ui.button(this, getString(R.string.show_art), v -> showArt());
        actions.addView(show, Ui.gap(this, 14, 0));
        actions.addView(Ui.button(this, getString(R.string.search_museums),
                v -> startActivity(new Intent(this, SearchActivity.class))), Ui.gap(this, 14, 0));
        actions.addView(Ui.button(this, getString(R.string.upload_phone),
                v -> startActivity(new Intent(this, UploadActivity.class))), Ui.gap(this, 14, 0));
        actions.addView(Ui.button(this, getString(R.string.settings),
                v -> startActivity(new Intent(this, SettingsActivity.class))), Ui.gap(this, 14, 0));
        root.addView(actions, Ui.gap(this, 0, 18));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        chips = Ui.row(this);
        scroller.addView(chips);
        root.addView(scroller, Ui.gap(this, 0, 14));

        grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(Ui.dp(this, 16));
        grid.setHorizontalSpacing(Ui.dp(this, 16));
        grid.setSelector(Ui.shape(this, 0x00000000, 10, Ui.ACCENT));
        grid.setDrawSelectorOnTop(true);
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 28));
        grid.setAdapter(adapter);
        // A piece opens on the wall, with its brightness, contrast and matte adjustable live; "More" there
        // leads to its page (collections, art button, delete).
        grid.setOnItemClickListener((parent, view, position, id) -> {
            Intent i = new Intent(this, FrameActivity.class);
            i.putExtra(FrameActivity.EXTRA_ADJUST, shown.get(position).id);
            startActivity(i);
        });
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Ui.screen(this, root);
        show.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lib.needsStarter()) {
            status.setText(R.string.preparing);
            new Thread(() -> {
                lib.importStarter(getApplicationContext());
                runOnUiThread(this::refresh);
            }, "starter").start();
        }
        refresh();
    }

    private void showArt() {
        Intent i = new Intent(this, FrameActivity.class);
        if (filter != null) i.putExtra(FrameActivity.EXTRA_COLLECTION, filter);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void refresh() {
        if (lib.version() == seenVersion) return;
        seenVersion = lib.version();
        if (filter != null && !lib.collections().contains(filter)) filter = null;
        status.setText(getString(R.string.status, lib.all().size(), lib.describeDefault(this)));
        buildChips();
        shown.clear();
        shown.addAll(filter == null ? lib.all() : lib.inCollection(filter));
        adapter.notifyDataSetChanged();
    }

    private void buildChips() {
        View focused = getCurrentFocus();
        String focusedTag = focused != null && focused.getParent() == chips ? (String) focused.getTag() : null;
        chips.removeAllViews();
        addChip(getString(R.string.all), null);
        for (String name : lib.collections()) addChip(name, name);
        TextView add = Ui.button(this, getString(R.string.new_collection), v -> newCollection(this, lib, name -> {
            filter = name;
            seenVersion = -1;
            refresh();
        }));
        add.setTag("+");
        chips.addView(add, Ui.gap(this, 12, 0));
        if (focusedTag != null) {
            for (int i = 0; i < chips.getChildCount(); i++) {
                if (focusedTag.equals(chips.getChildAt(i).getTag())) chips.getChildAt(i).requestFocus();
            }
        }
    }

    private void addChip(String label, String name) {
        TextView chip = Ui.button(this, label, v -> {
            filter = name;
            seenVersion = -1;
            refresh();
        });
        chip.setTag(name == null ? "*" : "c:" + name);
        chip.setSelected(name == null ? filter == null : name.equals(filter));
        chip.setOnLongClickListener(v -> {
            collectionMenu(name);
            return true;
        });
        chips.addView(chip, Ui.gap(this, 12, 0));
    }

    private void collectionMenu(String name) {
        if (name == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.all)
                    .setItems(new CharSequence[]{getString(R.string.use_on_button)}, (d, which) -> {
                        lib.setDefault(Library.DEFAULT_ALL, "");
                        refresh();
                    }).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new CharSequence[]{getString(R.string.use_on_button), getString(R.string.delete_collection)},
                        (d, which) -> {
                            if (which == 0) lib.setDefault(Library.DEFAULT_COLLECTION, name);
                            else lib.deleteCollection(name);
                            refresh();
                        }).show();
    }

    interface NameCallback {
        void named(String name);
    }

    /** A dialog with a text field for a new collection's name; shared with the piece page. */
    static void newCollection(Context c, Library lib, NameCallback done) {
        EditText field = new EditText(c);
        field.setSingleLine(true);
        field.setHint(R.string.collection_name);
        new AlertDialog.Builder(c)
                .setTitle(R.string.new_collection)
                .setView(field)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = Library.cleanName(field.getText().toString());
                    if (lib.addCollection(name)) done.named(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Tile t = convert instanceof Tile ? (Tile) convert : new Tile(parent.getContext());
            Artwork a = shown.get(position);
            t.title.setText(a.title.isEmpty() ? getString(R.string.untitled) : a.title);
            boolean isDefault = Library.DEFAULT_PIECE.equals(lib.defaultKind()) && a.id.equals(lib.defaultId());
            t.badge.setVisibility(isDefault ? View.VISIBLE : View.GONE);
            Ui.thumb(t.image, "lib:" + a.id, lib.thumb(a), null);
            return t;
        }
    };

    /** A grid tile: the thumbnail on a dark card, the title under it. */
    static final class Tile extends LinearLayout {
        final ImageView image;
        final TextView title;
        final TextView badge;

        Tile(Context c) {
            super(c);
            setOrientation(VERTICAL);
            image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Ui.PANEL);
            addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 150)));
            LinearLayout line = Ui.row(c);
            title = Ui.text(c, "", 15, Ui.TEXT);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            badge = Ui.text(c, c.getString(R.string.badge_default), 13, Ui.ACCENT);
            badge.setGravity(Gravity.END);
            line.addView(badge);
            line.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 4));
            addView(line);
        }
    }
}

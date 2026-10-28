package com.gathin.artframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * One piece: a live preview with its matte, brightness and contrast (Left and Right change a focused setting), and
 * the actions: show it now, use it for the art button, collections, delete. Changes are saved as they are made.
 */
public class ArtworkActivity extends Activity {
    static final String EXTRA_ID = "id";

    private Library lib;
    private Prefs prefs;
    private Artwork art;
    private ImageView preview;
    private FrameLayout mat;
    private TextView brightnessRow;
    private TextView contrastRow;
    private TextView matteRow;
    private TextView defaultButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = Library.get(this);
        prefs = new Prefs(this);
        art = lib.find(getIntent().getStringExtra(EXTRA_ID));
        if (art == null) {
            finish();
            return;
        }
        int pad = Ui.dp(this, 36);
        LinearLayout root = Ui.row(this);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.TOP);

        mat = new FrameLayout(this);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mat.addView(preview, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f);
        lp.setMargins(0, 0, Ui.dp(this, 32), 0);
        root.addView(mat, lp);

        LinearLayout side = Ui.column(this);
        TextView title = Ui.heading(this, art.title.isEmpty() ? getString(R.string.untitled) : art.title);
        title.setTextSize(26);
        side.addView(title, Ui.gap(this, 0, 4));
        side.addView(Ui.text(this, art.byline(), 17, Ui.MUTED), Ui.gap(this, 0, 4));
        if (!art.credit.isEmpty()) side.addView(Ui.text(this, art.credit, 14, Ui.MUTED), Ui.gap(this, 0, 16));

        side.addView(full(Ui.button(this, getString(R.string.adjust_live), v -> {
            Intent i = new Intent(this, FrameActivity.class);
            i.putExtra(FrameActivity.EXTRA_ADJUST, art.id);
            startActivity(i);
            finish();
        })), Ui.gap(this, 0, 10));
        side.addView(full(Ui.button(this, getString(R.string.show_now), v -> {
            Intent i = new Intent(this, FrameActivity.class);
            i.putExtra(FrameActivity.EXTRA_PIECE, art.id);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        })), Ui.gap(this, 0, 10));
        defaultButton = Ui.button(this, "", v -> {
            lib.setDefault(Library.DEFAULT_PIECE, art.id);
            HomeBackground.follow(this, art);
            updateRows();
        });
        side.addView(full(defaultButton), Ui.gap(this, 0, 10));
        brightnessRow = adjustRow(step -> {
            art.brightness = Artwork.clampAdjust(art.brightness + step * 5);
            changed();
        });
        side.addView(full(brightnessRow), Ui.gap(this, 0, 10));
        contrastRow = adjustRow(step -> {
            art.contrast = Artwork.clampAdjust(art.contrast + step * 5);
            changed();
        });
        side.addView(full(contrastRow), Ui.gap(this, 0, 10));
        matteRow = adjustRow(step -> {
            art.matte = Artwork.nextMatte(art.matte, step);
            changed();
        });
        side.addView(full(matteRow), Ui.gap(this, 0, 10));
        side.addView(full(Ui.button(this, getString(R.string.collections), v -> pickCollections())), Ui.gap(this, 0, 10));
        side.addView(full(Ui.button(this, getString(R.string.reset_adjust), v -> {
            art.brightness = 0;
            art.contrast = 0;
            art.matte = "auto";
            art.matteSize = "auto";
            art.matteTexture = "auto";
            art.fill = false;
            art.focus = 0.5f;
            changed();
        })), Ui.gap(this, 0, 10));
        side.addView(full(Ui.button(this, getString(R.string.delete), v -> confirmDelete())), Ui.gap(this, 0, 10));
        side.addView(Ui.text(this, getString(R.string.adjust_hint), 14, Ui.MUTED));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(side);
        root.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        Ui.screen(this, root);

        new Thread(() -> {
            try {
                Bitmap bm = Images.decodeFit(lib.picture(art), 1600, 900);
                runOnUiThread(() -> preview.setImageBitmap(bm));
            } catch (Exception ignored) {
                // Leaves the preview empty.
            }
        }, "preview").start();
        updateRows();
        defaultButton.requestFocus();
    }

    private static View full(View v) {
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    interface Step {
        void by(int step);
    }

    /** A row whose value Left and Right change while it has focus; OK steps it up. */
    private TextView adjustRow(Step step) {
        TextView row = Ui.button(this, "", v -> step.by(1));
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                step.by(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                step.by(1);
                return true;
            }
            return false;
        });
        return row;
    }

    private void changed() {
        lib.update(art);
        updateRows();
    }

    private void updateRows() {
        brightnessRow.setText(getString(R.string.brightness_row, signed(art.brightness)));
        contrastRow.setText(getString(R.string.contrast_row, signed(art.contrast)));
        String matteName = "auto".equals(art.matte)
                ? getString(R.string.matte_auto, matteLabel(prefs.matte() ? prefs.matteColour() : "none"))
                : matteLabel(art.matte);
        matteRow.setText(getString(R.string.matte_row, matteName));
        boolean isDefault = Library.DEFAULT_PIECE.equals(lib.defaultKind()) && art.id.equals(lib.defaultId());
        defaultButton.setText(isDefault ? R.string.is_default : R.string.use_on_button);
        preview.setColorFilter(Images.filter(art.brightness, art.contrast, 1f));
        String m = "auto".equals(art.matte) ? (prefs.matte() ? prefs.matteColour() : "none") : art.matte;
        int pad = "none".equals(m) ? 0 : Ui.dp(this, 34);
        mat.setBackgroundColor("none".equals(m) ? 0xFF000000 : Prefs.matteColor(m));
        mat.setPadding(pad, pad, pad, pad);
    }

    private String matteLabel(String m) {
        return Matte.label(this, m);
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    private void pickCollections() {
        List<String> names = lib.collections();
        CharSequence[] items = new CharSequence[names.size() + 1];
        boolean[] checked = new boolean[names.size() + 1];
        for (int i = 0; i < names.size(); i++) {
            items[i] = names.get(i);
            checked[i] = art.collections.contains(names.get(i));
        }
        items[names.size()] = getString(R.string.new_collection);
        new AlertDialog.Builder(this)
                .setTitle(R.string.collections)
                .setMultiChoiceItems(items, checked, (d, which, on) -> {
                    if (which == names.size()) {
                        d.dismiss();
                        LibraryActivity.newCollection(this, lib, name -> {
                            art.collections.add(name);
                            lib.update(art);
                        });
                        return;
                    }
                    if (on) art.collections.add(names.get(which));
                    else art.collections.remove(names.get(which));
                    lib.update(art);
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(art.title)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Ui.forget("lib:" + art.id);
                    lib.delete(art);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}

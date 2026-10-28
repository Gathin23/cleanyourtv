package com.gathin.artframe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/**
 * Art mode: what the remote's art button, the card and the tile open. It plays the art button's choice (or, from
 * the library, one piece or collection). Left and Right step through the pieces, Up shows the caption, OK opens the
 * library, Back leaves. In art hours it keeps the screen on; outside them it goes black, and with "let the TV sleep"
 * it stops holding the screen so the TV's own timers turn it off.
 *
 * Opened with {@link #EXTRA_ADJUST}, it shows that one piece as it hangs on the wall with a small panel: Up and Down
 * choose brightness, contrast, framing or the matte's colour, width and texture, Left and Right change it on the real picture and save it at once, OK hides
 * the panel for a clean look, Back is done. The panel also holds the art button choice, collections, reset and delete.
 */
public class FrameActivity extends Activity {
    static final String EXTRA_PIECE = "piece";
    static final String EXTRA_COLLECTION = "collection";
    static final String EXTRA_ADJUST = "adjust";

    private static final int ROW_BRIGHTNESS = 0;
    private static final int ROW_CONTRAST = 1;
    private static final int ROW_FRAMING = 2;
    private static final int ROW_POSITION = 3;
    private static final int ROW_MATTE = 4;
    private static final int ROW_SIZE = 5;
    private static final int ROW_TEXTURE = 6;
    private static final int ROW_DEPTH = 7;
    private static final int ROW_BUTTON = 8;
    private static final int ROW_COLLECTIONS = 9;
    private static final int ROW_RESET = 10;
    private static final int ROW_DELETE = 11;
    private static final int FIRST_ACTION = ROW_BUTTON;

    private FrameView frame;
    private TextView hint;
    private Prefs prefs;
    private long shownVersion = -1;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Artwork adjusting;
    private LinearLayout panel;
    private final TextView[] rows = new TextView[12];
    private TextView status;
    private int row;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        FrameLayout root = new FrameLayout(this);
        frame = new FrameView(this, false);
        root.addView(frame);
        hint = Ui.text(this, getString(R.string.preparing), 20, Ui.MUTED);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        root.addView(hint, lp);
        hint.setVisibility(View.GONE);
        root.addView(buildPanel(), panelParams());
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /**
     * Two columns: the piece's look on the left, what to do with it on the right. Up and Down walk through both in
     * order, Left and Right change the chosen value, OK runs an action (or, on a value, hides the panel).
     */
    private View buildPanel() {
        panel = Ui.column(this);
        panel.setBackground(Ui.shape(this, 0xE6111214, 18, 0));
        int pad = Ui.dp(this, 14);
        panel.setPadding(pad, pad, pad, pad);
        LinearLayout cols = Ui.row(this);
        cols.setGravity(Gravity.TOP);
        LinearLayout look = Ui.column(this);
        LinearLayout actions = Ui.column(this);
        for (int i = 0; i < rows.length; i++) {
            rows[i] = Ui.text(this, "", 17, Ui.TEXT);
            rows[i].setSingleLine(true);
            rows[i].setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
            (i < FIRST_ACTION ? look : actions).addView(rows[i], Ui.gap(this, 0, 3));
        }
        cols.addView(look, Ui.gap(this, 18, 0));
        cols.addView(actions);
        panel.addView(cols);
        status = Ui.text(this, getString(R.string.adjust_live_hint), 14, Ui.MUTED);
        status.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), 0, 0);
        panel.addView(status);
        panel.setVisibility(View.GONE);
        frame.setRenderListener(busy -> {
            status.setText(busy ? getString(R.string.applying) : getString(R.string.adjust_live_hint));
            status.setTextColor(busy ? Ui.ACCENT : Ui.MUTED);
        });
        return panel;
    }


    private FrameLayout.LayoutParams panelParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.bottomMargin = Ui.dp(this, 28);
        lp.leftMargin = Ui.dp(this, 28);
        return lp;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        shownVersion = -1;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Library lib = Library.get(this);
        if (lib.needsStarter()) {
            hint.setVisibility(View.VISIBLE);
            new Thread(() -> {
                lib.importStarter(getApplicationContext());
                main.post(() -> {
                    hint.setVisibility(View.GONE);
                    if (!isFinishing()) loadPlaylist();
                });
            }, "starter").start();
        } else {
            loadPlaylist();
        }
        frame.start();
        applySchedule.run();
        WakeService.sync(this);
    }

    @Override
    protected void onPause() {
        // Home's background follows the default piece, or else what was on the wall when art mode closed.
        HomeBackground.follow(this, adjusting != null ? adjusting : frame.currentArt());
        frame.stop();
        main.removeCallbacks(applySchedule);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        frame.release();
        super.onDestroy();
    }

    private void loadPlaylist() {
        Library lib = Library.get(this);
        Intent i = getIntent();
        String adjust = i.getStringExtra(EXTRA_ADJUST);
        adjusting = adjust == null ? null : lib.find(adjust);
        frame.setAdjusting(adjusting != null);
        panel.setVisibility(adjusting != null ? View.VISIBLE : View.GONE);
        if (adjusting != null) {
            updatePanel();
            if (shownVersion == -1) {
                shownVersion = lib.version();
                frame.setPlaylist(Collections.singletonList(adjusting), adjusting.id);
            }
            return;
        }
        if (lib.version() == shownVersion) return;
        shownVersion = lib.version();
        String piece = i.getStringExtra(EXTRA_PIECE);
        String collection = i.getStringExtra(EXTRA_COLLECTION);
        List<Artwork> list;
        String start = "";
        if (collection != null) {
            list = lib.inCollection(collection);
            if (piece != null) start = piece;
        } else if (piece != null && lib.find(piece) != null) {
            list = lib.all();
            start = piece;
        } else {
            list = lib.defaultPlaylist();
        }
        Artwork showing = frame.currentArt();
        if (start.isEmpty() && showing != null) start = showing.id;
        frame.setPlaylist(list, start);
    }

    /** Keeps the screen on in art hours only; re-checked every minute. */
    private final Runnable applySchedule = new Runnable() {
        @Override
        public void run() {
            boolean hold = adjusting != null || prefs.isArtTime() || "black".equals(prefs.outside());
            if (hold) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            main.postDelayed(this, 60000);
        }
    };

    // --- adjusting ------------------------------------------------------------------------------------------

    private void updatePanel() {
        if (adjusting == null) return;
        rows[ROW_BRIGHTNESS].setText(getString(R.string.brightness_row, signed(adjusting.brightness)));
        rows[ROW_CONTRAST].setText(getString(R.string.contrast_row, signed(adjusting.contrast)));
        rows[ROW_FRAMING].setText(getString(R.string.framing_row, getString(adjusting.fill ? R.string.framing_fill
                : adjusting.whole ? R.string.framing_whole : R.string.framing_even)));
        rows[ROW_POSITION].setText(getString(R.string.position_row, Math.round(adjusting.focus * 100)));
        rows[ROW_MATTE].setText(getString(R.string.matte_row, autoLabel(adjusting.matte,
                prefs.matte() ? prefs.matteColour() : "none")));
        rows[ROW_SIZE].setText(getString(R.string.matte_size_row, autoLabel(adjusting.matteSize, prefs.matteSize())));
        rows[ROW_TEXTURE].setText(getString(R.string.matte_texture_row,
                autoLabel(adjusting.matteTexture, prefs.matteTexture())));
        Library lib = Library.get(this);
        boolean onButton = Library.DEFAULT_PIECE.equals(lib.defaultKind()) && adjusting.id.equals(lib.defaultId());
        rows[ROW_BUTTON].setText(getString(onButton ? R.string.on_button_yes : R.string.on_button_no));
        rows[ROW_COLLECTIONS].setText(getString(R.string.collections_row, adjusting.collections.isEmpty()
                ? getString(R.string.none) : android.text.TextUtils.join(", ", adjusting.collections)));
        rows[ROW_RESET].setText(R.string.reset_row);
        rows[ROW_DELETE].setText(R.string.delete_row);
        // Position only means something when filling, the matte only when fitting (and when there is one).
        boolean matte = !adjusting.fill;
        boolean hasMatte = matte && !"none".equals("auto".equals(adjusting.matte)
                ? (prefs.matte() ? prefs.matteColour() : "none") : adjusting.matte);
        // Crop position matters when filling, and when a matted picture fills its opening.
        rows[ROW_POSITION].setVisibility(View.VISIBLE);
        rows[ROW_MATTE].setVisibility(matte ? View.VISIBLE : View.GONE);
        rows[ROW_SIZE].setVisibility(hasMatte ? View.VISIBLE : View.GONE);
        rows[ROW_TEXTURE].setVisibility(hasMatte ? View.VISIBLE : View.GONE);
        rows[ROW_DEPTH].setText(getString(R.string.depth_row, autoLabel(adjusting.depth, prefs.depth())));
        rows[ROW_DEPTH].setVisibility(hasMatte ? View.VISIBLE : View.GONE);
        if (rows[row].getVisibility() != View.VISIBLE) row = ROW_FRAMING;
        for (int i = 0; i < rows.length; i++) {
            boolean on = i == row;
            rows[i].setBackground(on ? Ui.shape(this, Ui.ACCENT, 22, 0) : null);
            rows[i].setTextColor(on ? Ui.INK : Ui.TEXT);
            rows[i].setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void change(int step) {
        switch (row) {
            case ROW_BRIGHTNESS:
                adjusting.brightness = Artwork.clampAdjust(adjusting.brightness + step * 5);
                break;
            case ROW_CONTRAST:
                adjusting.contrast = Artwork.clampAdjust(adjusting.contrast + step * 5);
                break;
            case ROW_FRAMING: {
                // fill the screen -> matte, even -> matte, whole picture -> ...
                int mode = adjusting.fill ? 0 : adjusting.whole ? 2 : 1;
                mode = Math.floorMod(mode + step, 3);
                adjusting.fill = mode == 0;
                adjusting.whole = mode == 2;
                break;
            }
            case ROW_POSITION:
                adjusting.focus = Artwork.clampFocus(Math.round(adjusting.focus * 20 + step) / 20f);
                break;
            case ROW_MATTE:
                adjusting.matte = Artwork.nextMatte(adjusting.matte, step);
                break;
            case ROW_SIZE:
                adjusting.matteSize = Matte.next(Artwork.SIZES, adjusting.matteSize, step);
                break;
            case ROW_TEXTURE:
                adjusting.matteTexture = Matte.next(Artwork.TEXTURES, adjusting.matteTexture, step);
                break;
            case ROW_DEPTH:
                adjusting.depth = Matte.next(Artwork.DEPTHS, adjusting.depth, step);
                break;
            case ROW_BUTTON:
                toggleButton();
                return;
            default:
                return;
        }
        Library.get(this).update(adjusting);
        updatePanel();
        frame.refresh();
    }

    /** A piece's own choice, or "as in settings (…)" when it follows Settings. */
    private String autoLabel(String own, String setting) {
        if ("auto".equals(own)) return getString(R.string.matte_auto, Matte.label(this, setting));
        return Matte.label(this, own);
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    private void toggleButton() {
        Library lib = Library.get(this);
        boolean onButton = Library.DEFAULT_PIECE.equals(lib.defaultKind()) && adjusting.id.equals(lib.defaultId());
        if (onButton) lib.setDefault(Library.DEFAULT_ALL, "");
        else lib.setDefault(Library.DEFAULT_PIECE, adjusting.id);
        HomeBackground.follow(this, adjusting);
        updatePanel();
    }

    private void action(int which) {
        Library lib = Library.get(this);
        switch (which) {
            case ROW_BUTTON:
                toggleButton();
                break;
            case ROW_COLLECTIONS: {
                List<String> names = lib.collections();
                CharSequence[] items = new CharSequence[names.size() + 1];
                boolean[] checked = new boolean[names.size() + 1];
                for (int i = 0; i < names.size(); i++) {
                    items[i] = names.get(i);
                    checked[i] = adjusting.collections.contains(names.get(i));
                }
                items[names.size()] = getString(R.string.new_collection);
                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.collections)
                        .setMultiChoiceItems(items, checked, (d, i, on) -> {
                            if (i == names.size()) {
                                d.dismiss();
                                LibraryActivity.newCollection(this, lib, name -> {
                                    adjusting.collections.add(name);
                                    lib.update(adjusting);
                                    updatePanel();
                                });
                                return;
                            }
                            if (on) adjusting.collections.add(names.get(i));
                            else adjusting.collections.remove(names.get(i));
                            lib.update(adjusting);
                            updatePanel();
                        })
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                break;
            }
            case ROW_RESET:
                adjusting.brightness = 0;
                adjusting.contrast = 0;
                adjusting.matte = "auto";
                adjusting.matteSize = "auto";
                adjusting.matteTexture = "auto";
                adjusting.depth = "auto";
                adjusting.focus = 0.5f;
                lib.update(adjusting);
                updatePanel();
                frame.refresh();
                break;
            case ROW_DELETE:
                new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.delete_title)
                        .setMessage(adjusting.title)
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            Ui.forget("lib:" + adjusting.id);
                            lib.delete(adjusting);
                            onBackPressed();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;
            default:
                break;
        }
    }

    /** The next shown row up or down, skipping the one that does not apply. */
    private int nextRow(int dir) {
        int r = row;
        do {
            r += dir;
        } while (r >= 0 && r < rows.length && rows[r].getVisibility() != View.VISIBLE);
        return r < 0 || r >= rows.length ? row : r;
    }

    private boolean adjustKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (panel.getVisibility() != View.VISIBLE) panel.setVisibility(View.VISIBLE);
                else row = nextRow(-1);
                updatePanel();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (panel.getVisibility() != View.VISIBLE) panel.setVisibility(View.VISIBLE);
                else row = nextRow(1);
                updatePanel();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                panel.setVisibility(View.VISIBLE);
                change(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                panel.setVisibility(View.VISIBLE);
                change(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (row >= FIRST_ACTION && panel.getVisibility() == View.VISIBLE) {
                    action(row);
                } else {
                    // A clean look at the piece; any arrow brings the panel back.
                    panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (adjusting != null && adjustKey(keyCode)) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                frame.step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                frame.step(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
                frame.showCaption();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MENU:
                startActivity(new Intent(this, LibraryActivity.class));
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onBackPressed() {
        if (adjusting != null) {
            // Done adjusting: back to the library, where the piece was chosen.
            startActivity(new Intent(this, LibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            setIntent(new Intent());
            adjusting = null;
            finish();
            return;
        }
        super.onBackPressed();
    }
}

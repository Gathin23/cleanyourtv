package io.github.kalt2212.tvtune;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** An offline foreground-only guide. It never writes system picture settings. */
public final class TuneActivity extends Activity {
    private static final int BG = 0xff0b1424, INK = 0xfff1f5fb, MUTED = 0xffa4b4cc, ACCENT = 0xff88cfff;
    private static final int[] TITLES = {R.string.edges_title, R.string.shadows_title,
        R.string.highlights_title, R.string.sharpness_title, R.string.neutral_title};
    private static final int[] BODIES = {R.string.edges_body, R.string.shadows_body,
        R.string.highlights_body, R.string.sharpness_body, R.string.neutral_body};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences saved;
    private TuneSession session;
    private PatternState fields;
    private TunePatternView pattern;
    private boolean showingPattern, showingFields, labels = true, resumed;
    private int page;
    private EditText activeNotes;
    private String activeNoteKey;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed || !showingPattern) return;
            fields.tick(SystemClock.uptimeMillis());
            updateScreenFlag();
            pattern.invalidate();
            if (!fields.dimmed()) handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saved = getSharedPreferences("picture-checklist", MODE_PRIVATE);
        session = TuneSession.restore(saved.getInt("step", 0), saved.getString("outcomes", null));
        fields = new PatternState(SystemClock.uptimeMillis());
        showWelcome();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent, CharSequence value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        view.setLineSpacing(dp(3), 1.06f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(size >= 30 ? 16 : 12);
        parent.addView(view, params);
        return view;
    }
    private TextView title(LinearLayout parent, int resource) {
        TextView view = text(parent, getText(resource), 36, INK);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }
    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }
    private LinearLayout screen(int nextPage) {
        captureNotes(); activeNotes = null; activeNoteKey = null;
        showingPattern = false; page = nextPage; handler.removeCallbacks(tick); updateScreenFlag();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = column(); root.setPadding(dp(44), dp(28), dp(44), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1)); setContentView(scroll);
        text(root, "TV Tune", 22, INK).setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return root;
    }
    private GradientDrawable background(int fill, int stroke) {
        GradientDrawable result = new GradientDrawable(); result.setColor(fill);
        result.setCornerRadius(dp(9)); result.setStroke(dp(1), stroke); return result;
    }
    private Button button(LinearLayout parent, int resource, Runnable action) {
        Button view = new Button(this); view.setId(View.generateViewId());
        view.setText(resource); view.setTextSize(17); view.setAllCaps(false);
        view.setTextColor(INK); view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setPadding(dp(20), dp(10), dp(20), dp(10)); view.setMinHeight(dp(52));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, background(ACCENT, ACCENT));
        bg.addState(new int[]{android.R.attr.state_pressed}, background(0xffb0dfff, ACCENT));
        bg.addState(new int[]{}, background(0xff172842, 0xff344d6b));
        view.setBackground(bg);
        view.setOnFocusChangeListener((v, focused) -> view.setTextColor(focused ? BG : INK));
        view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(9);
        parent.addView(view, params); return view;
    }
    private EditText notes(LinearLayout root, int hint, String key) {
        text(root, getText(hint), 16, MUTED);
        EditText edit = new EditText(this); edit.setTextColor(INK); edit.setTextSize(18);
        edit.setHintTextColor(MUTED); edit.setBackground(background(0xff111e34, 0xff536e90));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(240)}); edit.setMaxLines(3);
        edit.setPadding(dp(16), dp(10), dp(16), dp(10)); edit.setText(saved.getString(key, ""));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(16);
        root.addView(edit, params); activeNotes = edit; activeNoteKey = key; return edit;
    }
    private void captureNotes() {
        if (activeNotes != null && activeNoteKey != null) saved.edit().putString(activeNoteKey, activeNotes.getText().toString()).apply();
    }
    private void persist() {
        captureNotes();
        if (session.started()) saved.edit().putInt("step", session.step()).putString("outcomes", session.serialise()).apply();
    }

    private void showWelcome() {
        LinearLayout root = screen(0);
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL); root.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout copy = column(); hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.15f));
        text(copy, getText(R.string.eyebrow), 12, ACCENT).setLetterSpacing(0.1f);
        title(copy, R.string.welcome_title).setTextSize(43);
        text(copy, getText(R.string.welcome_body), 20, MUTED);
        Button primary = button(copy, session.started() ? R.string.resume : R.string.start,
            () -> { if (session.started()) { if (session.complete()) showSummary(); else showCheck(); } else showPreparation(); });
        button(copy, R.string.fields, this::showFieldIntroduction);
        button(copy, R.string.correction_lab, () -> startActivity(new Intent(this, CorrectionPreviewActivity.class)));
        if (session.started() || saved.contains("original") || saved.contains("revised")) button(copy, R.string.delete_session, this::deleteDialog);
        text(copy, getText(R.string.welcome_note), 13, MUTED);
        TunePatternView art = new TunePatternView(this, -2, fields, false);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(0, dp(290), 1f); artParams.setMarginStart(dp(32));
        hero.addView(art, artParams); primary.requestFocus();
    }
    private void showPreparation() {
        LinearLayout root = screen(1);
        title(root, R.string.prepare_title); text(root, getText(R.string.prepare_body), 21, MUTED);
        text(root, getText(R.string.scope), 16, 0xffffca9e);
        notes(root, R.string.original_values, "original");
        button(root, R.string.start_checks, () -> { session.begin(); persist(); showCheck(); }).requestFocus();
        button(root, R.string.back, this::showWelcome);
    }
    private void showCheck() {
        if (session.complete()) { showSummary(); return; }
        LinearLayout root = screen(2);
        text(root, getString(R.string.step, session.step() + 1, TuneSession.COUNT), 13, ACCENT).setLetterSpacing(0.1f);
        title(root, TITLES[session.step()]); text(root, getText(BODIES[session.step()]), 21, MUTED);
        text(root, getText(R.string.next_check_hint), 16, ACCENT);
        button(root, R.string.show_pattern, () -> beginPattern(false)).requestFocus();
        LinearLayout outcomes = new LinearLayout(this); root.addView(outcomes);
        int[] titles = {R.string.seen, R.string.adjusted, R.string.skip};
        int[] values = {TuneSession.SEEN, TuneSession.ADJUSTED, TuneSession.SKIPPED};
        for (int i = 0; i < titles.length; i++) {
            LinearLayout cell = column(); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(dp(10)); outcomes.addView(cell, lp);
            final int outcome = values[i];
            button(cell, titles[i], () -> { session.record(outcome); persist(); showCheck(); });
        }
        button(root, R.string.back, () -> { if (session.step() == 0) showPreparation(); else { session.back(); persist(); showCheck(); } });
    }
    private void showSummary() {
        LinearLayout root = screen(3); title(root, R.string.done_title);
        text(root, getText(R.string.done_body), 19, MUTED);
        int[] resultNames = {R.string.not_checked, R.string.seen_result, R.string.adjusted_result, R.string.skipped_result};
        for (int i = 0; i < TuneSession.COUNT; i++) {
            text(root, getString(TITLES[i]) + "  ·  " + getString(resultNames[session.outcome(i)]), 18, INK);
        }
        String original = saved.getString("original", "");
        text(root, getString(R.string.saved_original, original.isEmpty() ? getString(R.string.no_notes) : original), 16, MUTED);
        notes(root, R.string.revised_values, "revised");
        button(root, R.string.save, () -> { persist(); showWelcome(); }).requestFocus();
        button(root, R.string.new_session, () -> {
            activeNotes = null; activeNoteKey = null;
            saved.edit().remove("step").remove("outcomes").remove("original").remove("revised").apply();
            session = new TuneSession(); showPreparation();
        });
    }
    private void showFieldIntroduction() {
        LinearLayout root = screen(4); title(root, R.string.fields_title);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout copy = column(); row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        text(copy, getText(R.string.fields_body), 18, MUTED);
        text(copy, getText(R.string.companion_url), 15, ACCENT);
        ImageView qr = new ImageView(this); qr.setImageResource(R.drawable.companion_qr);
        qr.setContentDescription(getString(R.string.companion_qr_description)); qr.setFocusable(false);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(180), dp(180));
        qrParams.setMarginStart(dp(24)); row.addView(qr, qrParams);
        text(root, getText(R.string.fields_limit), 16, 0xffffca9e);
        button(root, R.string.open_fields, () -> beginPattern(true)).requestFocus();
        button(root, R.string.back, this::showWelcome);
    }
    private void deleteDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.delete_session).setMessage(R.string.delete_explanation)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (dialog, which) -> {
                activeNotes = null; activeNoteKey = null; saved.edit().clear().apply(); session = new TuneSession(); showWelcome();
            }).show();
    }
    private void beginPattern(boolean experimentFields) {
        captureNotes(); showingFields = experimentFields; showingPattern = true; labels = true;
        fields = new PatternState(SystemClock.uptimeMillis());
        pattern = new TunePatternView(this, experimentFields ? -1 : session.step(), fields, true);
        setContentView(pattern); updateScreenFlag(); handler.removeCallbacks(tick); handler.post(tick);
    }
    private void updateScreenFlag() {
        if (resumed && showingPattern && !fields.dimmed()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!showingPattern) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_BACK) { onBackPressed(); return true; }
        if (keyCode != KeyEvent.KEYCODE_DPAD_RIGHT && keyCode != KeyEvent.KEYCODE_DPAD_LEFT &&
            keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN &&
            keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) {
            return super.onKeyDown(keyCode, event);
        }
        if (event.getRepeatCount() > 0) return true;
        boolean wasDimmed = fields.dimmed();
        int delta = showingFields && !wasDimmed ? (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0) : 0;
        fields.move(delta, SystemClock.uptimeMillis());
        if (!wasDimmed && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) { labels = !labels; pattern.setLabels(labels); }
        pattern.invalidate(); updateScreenFlag(); handler.removeCallbacks(tick); handler.post(tick);
        return true;
    }
    @Override public void onBackPressed() {
        if (showingPattern) { if (showingFields) showFieldIntroduction(); else showCheck(); }
        else if (page != 0) showWelcome();
        else super.onBackPressed();
    }
    @Override public void onResume() { super.onResume(); resumed = true; updateScreenFlag(); if (showingPattern) handler.post(tick); }
    @Override public void onPause() {
        persist(); resumed = false; fields.pause(); handler.removeCallbacks(tick); updateScreenFlag(); super.onPause();
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}

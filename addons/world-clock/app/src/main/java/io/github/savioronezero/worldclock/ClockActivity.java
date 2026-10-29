package io.github.savioronezero.worldclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline foreground screen. The system supplies the clock, time zones and keyboard. */
public final class ClockActivity extends Activity {
    private static final int INK = Color.rgb(237, 245, 252), MUTED = Color.rgb(169, 190, 210);
    private static final int ACCENT = Color.rgb(131, 214, 222);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ClockViews> clocks = new ArrayList<>();
    private final List<View> focusNodes = new ArrayList<>();
    private List<String> ids, catalogue;
    private SharedPreferences preferences;
    private TextView reference;
    private AlertDialog dialog;
    private int selected, format;
    private boolean resumed;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh();
            handler.postDelayed(this, ClockModel.nextMinute(System.currentTimeMillis()));
        }
    };
    private final BroadcastReceiver changedTime = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (resumed) {
                handler.removeCallbacks(tick);
                tick.run();
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("world_clock", MODE_PRIVATE);
        List<String> saved = new ArrayList<>();
        try {
            String raw = preferences.getString("zones_v1", "[]");
            if (raw != null && raw.length() < 8192) {
                JSONArray values = new JSONArray(raw);
                for (int i = 0; i < Math.min(values.length(), 100); i++) {
                    Object id = values.get(i);
                    if (id instanceof String) saved.add((String) id);
                }
            }
            format = preferences.getInt("format_v1", 0);
        } catch (JSONException | ClassCastException ignored) {
            // Damaged local preferences fall back to the documented defaults; no external data is used.
        }
        if (format < 0 || format > 2) format = 0;
        ids = ClockModel.restore(saved);
        catalogue = ClockModel.catalogue();
        selected = state == null ? 0 : Math.max(0, Math.min(ids.size() - 1, state.getInt("selected", 0)));
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changedTime, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(changedTime, filter);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        if (dialog != null) dialog.dismiss();
        super.onPause();
    }

    @Override protected void onStop() {
        unregisterReceiver(changedTime);
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("selected", selected);
        super.onSaveInstanceState(state);
    }

    private void save() {
        preferences.edit().putString("zones_v1", new JSONArray(ids).toString())
                .putInt("format_v1", format).apply();
    }

    private void render() {
        clocks.clear(); focusNodes.clear();
        LinearLayout root = column();
        root.setPadding(dp(36), dp(22), dp(36), dp(20));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 19, 34), Color.rgb(18, 37, 55)}));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(getString(R.string.app_name), 28, INK);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button add = button(getString(R.string.add_city));
        add.setOnClickListener(v -> {
            if (ids.size() == ClockModel.LIMIT) Toast.makeText(this, R.string.city_limit, Toast.LENGTH_SHORT).show();
            else chooseCity(-1);
        });
        toolbar.addView(add, buttonParams()); focusNodes.add(add);
        Button clockFormat = button(getString(R.string.clock_format));
        clockFormat.setOnClickListener(v -> chooseFormat());
        toolbar.addView(clockFormat, buttonParams()); focusNodes.add(clockFormat);
        root.addView(toolbar);
        reference = text("", 15, MUTED);
        root.addView(reference, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout grid = column();
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        int columns = Math.min(3, ids.size());
        for (int start = 0; start < ids.size(); start += columns) {
            LinearLayout row = new LinearLayout(this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, 0, 1);
            if (start > 0) rowParams.topMargin = dp(14);
            grid.addView(row, rowParams);
            for (int col = 0; col < columns; col++) {
                final int index = start + col;
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -1, 1);
                if (col > 0) cardParams.leftMargin = dp(14);
                if (index >= ids.size()) { row.addView(new View(this), cardParams); continue; }
                ClockViews card = new ClockViews(ids.get(index));
                clocks.add(card);
                row.addView(card.root, cardParams);
                focusNodes.add(card.root);
                card.root.setOnFocusChangeListener((v, focused) -> { if (focused) selected = index; });
                card.root.setOnClickListener(v -> cityOptions(index));
            }
        }
        TextView help = text(getString(R.string.controls), 14, MUTED);
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, dp(42));
        helpParams.topMargin = dp(10);
        root.addView(help, helpParams);
        setContentView(root);
        for (View node : focusNodes) node.setId(View.generateViewId());
        for (int i = 0; i < focusNodes.size(); i++) {
            View node = focusNodes.get(i);
            node.setNextFocusLeftId(target(i, ClockNavigation.LEFT));
            node.setNextFocusRightId(target(i, ClockNavigation.RIGHT));
            node.setNextFocusUpId(target(i, ClockNavigation.UP));
            node.setNextFocusDownId(target(i, ClockNavigation.DOWN));
        }
        refresh();
        focusNodes.get(2 + Math.max(0, Math.min(selected, ids.size() - 1))).requestFocus();
    }

    private int target(int node, int direction) {
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        return focusNodes.get(ClockNavigation.next(node, ids.size(), direction, rtl)).getId();
    }

    private void refresh() {
        // One instant for every city: clocks cannot disagree when the minute/date rolls over mid-refresh.
        Instant instant = Instant.now();
        ZoneId local = ZoneId.systemDefault();
        Locale locale = Locale.getDefault();
        boolean twentyFour = format == 2 || (format == 0 && android.text.format.DateFormat.is24HourFormat(this));
        reference.setText(getString(R.string.local_reference, local.getDisplayName(TextStyle.FULL, locale)));
        for (ClockViews views : clocks) {
            ClockModel.Reading reading = ClockModel.at(views.id, instant, local, twentyFour, locale);
            if (reading.period.isEmpty()) views.time.setText(reading.time);
            else {
                SpannableString display = new SpannableString(reading.time + " " + reading.period);
                display.setSpan(new RelativeSizeSpan(0.4f), reading.time.length() + 1, display.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                views.time.setText(display);
            }
            views.date.setText(reading.date);
            views.offset.setText(reading.offset);
            views.day.setText(reading.dayDifference == 0 ? getString(R.string.same_day)
                    : reading.dayDifference == 1 ? getString(R.string.tomorrow)
                    : reading.dayDifference == -1 ? getString(R.string.yesterday)
                    : getResources().getQuantityString(R.plurals.day_difference,
                            Math.abs(reading.dayDifference), reading.dayDifference));
            views.day.setTextColor(reading.dayDifference == 0 ? MUTED : ACCENT);
        }
    }

    private void cityOptions(int index) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.change_city)); actions.add(() -> chooseCity(index));
        if (index > 0) {
            labels.add(getString(R.string.move_earlier));
            actions.add(() -> { ids = ClockModel.move(ids, index, index - 1); selected = index - 1; save(); render(); });
        }
        if (index + 1 < ids.size()) {
            labels.add(getString(R.string.move_later));
            actions.add(() -> { ids = ClockModel.move(ids, index, index + 1); selected = index + 1; save(); render(); });
        }
        if (ids.size() > 1) {
            labels.add(getString(R.string.remove_city));
            actions.add(() -> { ids = ClockModel.remove(ids, index); selected = Math.min(index, ids.size() - 1); save(); render(); });
        }
        show(new AlertDialog.Builder(this).setTitle(cityLabel(ids.get(index)))
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run()).create());
    }

    private String cityLabel(String id) { return ClockModel.LOCAL.equals(id) ? getString(R.string.local) : ClockModel.city(id); }

    private void chooseFormat() {
        show(new AlertDialog.Builder(this).setTitle(R.string.clock_format)
                .setSingleChoiceItems(new String[]{getString(R.string.format_system), getString(R.string.format_12),
                        getString(R.string.format_24)}, format, (d, which) -> {
                    format = which; save(); refresh(); d.dismiss();
                }).setNegativeButton(R.string.close, null).create());
    }

    private void chooseCity(int replacing) {
        LinearLayout panel = column(); panel.setPadding(dp(24), dp(8), dp(24), 0);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setHint(R.string.search_city);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        panel.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        panel.addView(text(getString(R.string.search_hint), 13, MUTED));
        TextView empty = text(getString(R.string.no_cities), 18, INK);
        empty.setPadding(0, dp(24), 0, dp(24));
        panel.addView(empty);
        ListView list = new ListView(this); list.setId(View.generateViewId());
        list.setItemsCanFocus(false);
        panel.addView(list, new LinearLayout.LayoutParams(-1, dp(230)));
        list.setEmptyView(empty);
        final List<String> matches = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        Runnable filter = () -> {
            matches.clear(); matches.addAll(ClockModel.search(catalogue, search.getText().toString(), ids, replacing));
            adapter.setNotifyOnChange(false);
            adapter.clear();
            for (String id : matches) adapter.add(cityLabel(id) + "  ·  " + ClockModel.area(id));
            adapter.notifyDataSetChanged();
            if (!matches.isEmpty()) list.setSelection(0);
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(Editable text) {}
        });
        search.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN && !matches.isEmpty()) {
                list.requestFocus(); list.setSelection(0); return true;
            }
            return false;
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            InputMethodManager ime = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (ime != null) ime.hideSoftInputFromWindow(search.getWindowToken(), 0);
            if (!matches.isEmpty()) { list.requestFocus(); list.setSelection(0); }
            return true;
        });
        AlertDialog picker = new AlertDialog.Builder(this).setTitle(replacing < 0 ? R.string.add_city : R.string.change_city)
                .setView(panel).setNegativeButton(R.string.close, null).create();
        list.setOnItemClickListener((parent, view, position, itemId) -> {
            String id = matches.get(position);
            List<String> updated = replacing < 0 ? ClockModel.add(ids, id) : ClockModel.replace(ids, replacing, id);
            if (updated.equals(ids)) {
                if (replacing >= 0 && id.equals(ids.get(replacing))) { picker.dismiss(); return; }
                Toast.makeText(this, R.string.already_shown, Toast.LENGTH_SHORT).show(); return;
            }
            ids = updated;
            selected = replacing < 0 ? ids.size() - 1 : replacing;
            save(); picker.dismiss(); render();
        });
        filter.run();
        show(picker);
        if (picker.getWindow() != null) picker.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        search.requestFocus();
    }

    private void show(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        next.setOnDismissListener(d -> { if (dialog == next) dialog = null; });
        next.show();
    }

    private final class ClockViews {
        final String id;
        final LinearLayout root;
        final TextView time, date, offset, day;
        ClockViews(String id) {
            this.id = id;
            root = column(); root.setGravity(Gravity.CENTER);
            root.setPadding(dp(12), dp(12), dp(12), dp(12));
            root.setFocusable(true); root.setClickable(true); root.setBackground(focusBackground());
            TextView city = text(cityLabel(id), 23, INK);
            city.setMaxLines(1); city.setGravity(Gravity.CENTER);
            city.setAutoSizeTextTypeUniformWithConfiguration(16, 23, 1, TypedValue.COMPLEX_UNIT_SP);
            root.addView(city, new LinearLayout.LayoutParams(-1, dp(34)));
            time = text("", 64, INK); time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            time.setGravity(Gravity.CENTER); time.setMaxLines(1); time.setTextDirection(View.TEXT_DIRECTION_LTR);
            time.setAutoSizeTextTypeUniformWithConfiguration(18, 64, 2, TypedValue.COMPLEX_UNIT_SP);
            root.addView(time, new LinearLayout.LayoutParams(-1, 0, 1));
            date = text("", 17, INK); date.setGravity(Gravity.CENTER); root.addView(date);
            day = text("", 13, MUTED); day.setGravity(Gravity.CENTER); root.addView(day);
            offset = text("", 13, MUTED); offset.setGravity(Gravity.CENTER); root.addView(offset);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private Button button(String label) {
        Button v = new Button(this); v.setText(label); v.setTextSize(14); v.setAllCaps(false); v.setTextColor(INK);
        v.setPadding(dp(16), 0, dp(16), 0); v.setBackground(focusBackground()); return v;
    }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48)); p.leftMargin = dp(10); return p;
    }
    private StateListDrawable focusBackground() {
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_focused}, shape(Color.rgb(28, 62, 80), ACCENT));
        state.addState(new int[]{}, shape(Color.rgb(19, 39, 57), Color.rgb(44, 64, 83)));
        return state;
    }
    private GradientDrawable shape(int fill, int border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(14));
        shape.setStroke(dp(2), border); return shape;
    }
}

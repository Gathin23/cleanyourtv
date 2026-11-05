package io.github.cleanyourtv.topicphotos;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The topics, full screen: type one (the suggestions complete it, so an existing one is picked rather than spelt
 * twice) or tick up to eight of the suggested ones, by group. Every change is saved at once; Back leaves.
 */
public final class TopicsActivity extends Activity {
    private static final int PER_LINE = 4;

    private final List<String> chosen = new ArrayList<>();
    private LinearLayout groups;
    private float dp;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        chosen.addAll(Settings.parseTopics(Settings.typedTopics(this)));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding((int) (96 * dp), (int) (56 * dp), (int) (96 * dp), (int) (56 * dp));
        page.addView(text(getString(R.string.topics), 32, 0xFFFFFFFF));
        TextView hint = text(getString(R.string.topics_screen_hint), 14, 0xFFACB8C1);
        hint.setPadding(0, (int) (6 * dp), 0, (int) (24 * dp));
        page.addView(hint);

        LinearLayout input = new LinearLayout(this);
        input.setGravity(Gravity.CENTER_VERTICAL);
        AutoCompleteTextView field = new AutoCompleteTextView(this);
        field.setSingleLine(true);
        field.setHint(R.string.topics_write);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0x80FFFFFF);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setThreshold(1);
        field.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, known()));
        field.setOnItemClickListener((list, v, position, id) -> add(field, (String) list.getItemAtPosition(position)));
        field.setOnEditorActionListener((v, action, event) -> {
            add(field, field.getText().toString());
            return true;
        });
        input.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView addButton = chip(getString(R.string.topics_add), null);
        addButton.setOnClickListener(v -> add(field, field.getText().toString()));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (52 * dp));
        blp.setMarginStart((int) (16 * dp));
        input.addView(addButton, blp);
        page.addView(input);

        groups = new LinearLayout(this);
        groups.setOrientation(LinearLayout.VERTICAL);
        page.addView(groups);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page);
        setContentView(scroll);
        showGroups();
        // The first topic, not the text field: the keyboard comes up only when the field is picked.
        View first = groups.findViewWithTag("topic");
        if (first != null) first.requestFocus();
    }

    /** Every suggestion, then the chosen topics that are not one: what the field completes. */
    private List<String> known() {
        List<String> all = new ArrayList<>();
        for (String[] group : Settings.SUGGESTED) all.addAll(Arrays.asList(group));
        for (String t : chosen) if (!all.contains(t)) all.add(t);
        return all;
    }

    private void add(AutoCompleteTextView field, String typed) {
        String topic = Settings.spelling(typed, known());
        field.setText("");
        field.dismissDropDown();
        if (topic.isEmpty() || chosen.contains(topic)) return;
        if (!Settings.toggle(chosen, topic)) {
            Toast.makeText(this, R.string.topics_full, Toast.LENGTH_SHORT).show();
            return;
        }
        Settings.setTopics(this, chosen);
        field.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, known()));
        showGroups();
    }

    /** "Your topics" (typed ones) when there are any, then the suggestions by group. */
    private void showGroups() {
        groups.removeAllViews();
        List<String> yours = new ArrayList<>(chosen);
        for (String[] group : Settings.SUGGESTED) yours.removeAll(Arrays.asList(group));
        if (!yours.isEmpty()) group(R.string.topics_yours, yours);
        int[] heads = {R.string.topics_holidays, R.string.topics_seasons, R.string.topics_more};
        for (int i = 0; i < heads.length; i++) group(heads[i], Arrays.asList(Settings.SUGGESTED[i]));
    }

    private void group(int head, List<String> topics) {
        TextView title = text(getString(head), 13, 0x99FFFFFF);
        title.setAllCaps(true);
        title.setLetterSpacing(0.08f);
        title.setPadding(0, (int) (28 * dp), 0, (int) (2 * dp));
        groups.addView(title);
        LinearLayout line = null;
        for (int i = 0; i < topics.size(); i++) {
            if (i % PER_LINE == 0) {
                line = new LinearLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = (int) (10 * dp);
                groups.addView(line, lp);
            }
            line.addView(chip(topics.get(i), topics.get(i)), cell());
        }
        // Spaces keep the last line's topics the same width as the others.
        for (int i = topics.size() % PER_LINE; i > 0 && i < PER_LINE; i++) line.addView(new Space(this), cell());
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (52 * dp), 1f);
        lp.setMarginEnd((int) (10 * dp));
        return lp;
    }

    /** A topic that OK ticks and unticks, or a plain button when {@code topic} is null. */
    private TextView chip(String label, String topic) {
        TextView chip = text(label, 16, 0xFFFFFFFF);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding((int) (18 * dp), 0, (int) (18 * dp), 0);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setContentDescription(label);
        chip.setOnFocusChangeListener((v, focused) -> paint(chip, label, topic));
        if (topic != null) {
            chip.setTag("topic");
            chip.setOnClickListener(v -> {
                if (!Settings.toggle(chosen, topic)) {
                    Toast.makeText(this, R.string.topics_full, Toast.LENGTH_SHORT).show();
                    return;
                }
                Settings.setTopics(this, chosen);
                paint(chip, label, topic);
            });
        }
        paint(chip, label, topic);
        return chip;
    }

    /** Ticked: light with dark text and a check. Focused: a white outline and a little larger. */
    private void paint(TextView chip, String label, String topic) {
        boolean on = topic != null && chosen.contains(topic), focused = chip.isFocused();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14 * dp);
        bg.setColor(on ? 0xFFF1F3F6 : 0x1AFFFFFF);
        if (focused) bg.setStroke((int) (3 * dp), on ? 0xFF5B8DEF : 0xFFFFFFFF);
        chip.setBackground(bg);
        chip.setTextColor(on ? 0xFF14171C : 0xFFFFFFFF);
        chip.setText(on ? "✓  " + label : label);
        chip.setSelected(on);
        chip.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(120).start();
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }
}

package io.github.deepakola.familyrenewals;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class RenewalsActivity extends Activity {
    private static final int BG = Color.rgb(11, 17, 23);
    private static final int PANEL = Color.rgb(22, 33, 42);
    private static final int TEXT = Color.rgb(239, 245, 247);
    private static final int MUTED = Color.rgb(164, 181, 190);
    private static final int TEAL = Color.rgb(67, 185, 165);
    private boolean revealReferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = column();
        root.setPadding(dp(34), dp(24), dp(34), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("FAMILY RENEWALS", 27, TEXT, true), lp(-1, -2, 4));
        root.addView(text("Recharge and subscription dates in one place", 16, MUTED, false),
                lp(-1, -2, 18));

        List<RenewalEntry> entries = RenewalStore.load(this);
        LocalDate today = LocalDate.now();
        entries.sort(Comparator.comparing((RenewalEntry e) -> DateRules.parse(e.dueDate)));

        int overdue = 0;
        int soon = 0;
        for (RenewalEntry e : entries) {
            long days = DateRules.daysUntil(e.dueDate, today);
            if (days < 0) overdue++;
            else if (days <= 7) soon++;
        }

        LinearLayout summary = row();
        summary.addView(summaryCell(String.valueOf(entries.size()), "TRACKED"), new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(summaryCell(String.valueOf(soon), "DUE IN 7 DAYS"), new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(summaryCell(String.valueOf(overdue), "OVERDUE"), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(summary, lp(-1, -2, 18));

        LinearLayout actions = row();
        actions.addView(button("Add renewal", this::showNewForm), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button(revealReferences ? "Hide references" : "Reveal references",
                () -> { revealReferences = !revealReferences; render(); }),
                new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions, lp(-1, -2, 8));

        CheckBox countdown = new CheckBox(this);
        countdown.setText("Show a generic next-renewal countdown on CleanYourTV Home");
        countdown.setTextColor(TEXT);
        countdown.setButtonTintList(android.content.res.ColorStateList.valueOf(TEAL));
        countdown.setChecked(RenewalStore.countdownEnabled(this));
        countdown.setOnCheckedChangeListener((buttonView, checked) -> {
            RenewalStore.setCountdownEnabled(this, checked);
            getContentResolver().notifyChange(RenewalsProvider.TURNS_URI, null);
            Toast.makeText(this, checked
                    ? "Generic countdown enabled. Configure game_day in the launcher theme."
                    : "Generic countdown disabled.", Toast.LENGTH_LONG).show();
        });
        root.addView(countdown, lp(-1, -2, 18));

        if (entries.isEmpty()) {
            LinearLayout empty = column();
            empty.setPadding(dp(22), dp(22), dp(22), dp(22));
            empty.setBackgroundColor(PANEL);
            empty.addView(text("No renewals added yet", 20, TEXT, true), lp(-1, -2, 8));
            empty.addView(text("Select Add renewal to record a mobile recharge or subscription.", 15, MUTED, false),
                    lp(-1, -2, 0));
            root.addView(empty, lp(-1, -2, 12));
        } else {
            for (RenewalEntry entry : entries) root.addView(entryCard(entry), lp(-1, -2, 12));
        }

        root.addView(text("Stored only on this TV. Dates are entered manually; no SMS or SIM data is read.",
                13, MUTED, false), lp(-1, -2, 6));
        setContentView(scroll);
    }

    private View summaryCell(String value, String caption) {
        LinearLayout cell = column();
        cell.setPadding(dp(15), dp(12), dp(15), dp(12));
        cell.setBackgroundColor(PANEL);
        cell.addView(text(value, 24, TEAL, true));
        cell.addView(text(caption, 11, MUTED, true));
        return cell;
    }

    private View entryCard(RenewalEntry entry) {
        LinearLayout card = column();
        card.setPadding(dp(18), dp(14), dp(18), dp(12));
        card.setBackgroundColor(PANEL);

        card.addView(text(entry.member + "  ·  " + entry.service, 19, TEXT, true), lp(-1, -2, 5));

        String detail = entry.category;
        if (!entry.amount.trim().isEmpty()) detail += "  ·  ₹" + entry.amount.trim();
        card.addView(text(detail, 14, MUTED, false), lp(-1, -2, 3));

        String reference = entry.reference.trim().isEmpty() ? ""
                : "Reference: " + (revealReferences ? entry.reference : mask(entry.reference));
        if (!reference.isEmpty()) card.addView(text(reference, 13, MUTED, false), lp(-1, -2, 3));

        long days = DateRules.daysUntil(entry.dueDate, LocalDate.now());
        String status = DateRules.status(entry.dueDate, LocalDate.now());
        int statusColor = days < 0 ? Color.rgb(255, 135, 120)
                : days <= 7 ? TEAL : MUTED;
        card.addView(text(status + "  ·  " + DateRules.display(entry.dueDate),
                15, statusColor, true), lp(-1, -2, 7));

        if (!entry.lastPaid.trim().isEmpty()) {
            card.addView(text("Last payment: " + DateRules.display(entry.lastPaid), 13, MUTED, false),
                    lp(-1, -2, 3));
        }
        if (!entry.notes.trim().isEmpty()) {
            card.addView(text(entry.notes, 13, MUTED, false), lp(-1, -2, 7));
        }

        LinearLayout actions = row();
        actions.addView(button("Edit", () -> showForm(entry)), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("Delete", () -> confirmDelete(entry)), new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(actions);
        return card;
    }

    private String mask(String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= 4) return "••••";
        return "••••" + trimmed.substring(trimmed.length() - 4);
    }

    private EditText field(String hint, String value, int inputType) {
        EditText edit = new EditText(this);
        edit.setSingleLine(inputType != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        edit.setHint(hint);
        edit.setText(value);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setInputType(inputType);
        edit.setSelectAllOnFocus(true);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        return edit;
    }

    private void pickDate(EditText target) {
        LocalDate initial = DateRules.parse(target.getText().toString());
        if (initial == null) initial = LocalDate.now();
        LocalDate chosen = initial;
        DatePickerDialog picker = new DatePickerDialog(this,
                (view, year, month, day) -> target.setText(
                        LocalDate.of(year, month + 1, day).toString()),
                chosen.getYear(), chosen.getMonthValue() - 1, chosen.getDayOfMonth());
        picker.show();
    }

    private void showNewForm() {
        showForm(null);
    }

    private void showForm(RenewalEntry existing) {
        RenewalEntry source = existing == null ? new RenewalEntry() : existing;
        LinearLayout form = column();
        form.setPadding(dp(12), dp(6), dp(12), dp(6));

        EditText member = field("Family member (e.g. Person 1)", source.member, InputType.TYPE_CLASS_TEXT);
        EditText service = field("Service / operator (e.g. Jio, Netflix)", source.service, InputType.TYPE_CLASS_TEXT);
        EditText reference = field("Phone or account reference (optional)", source.reference, InputType.TYPE_CLASS_TEXT);
        EditText amount = field("Amount in rupees (optional)", source.amount,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText lastPaid = field("Last payment date (YYYY-MM-DD)", source.lastPaid, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        EditText dueDate = field("Next due date (YYYY-MM-DD)", source.dueDate, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        EditText notes = field("Notes (optional)", source.notes,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setSingleLine(false);
        notes.setMinLines(2);
        notes.setGravity(Gravity.TOP);

        Spinner category = new Spinner(this);
        String[] categories = {"Mobile", "OTT", "Broadband", "DTH", "Electricity", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(adapter);
        int selected = 0;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equalsIgnoreCase(source.category)) selected = i;
        }
        category.setSelection(selected);

        addFormField(form, "Family member", member);
        addFormField(form, "Service / operator", service);
        addFormField(form, "Category", category);
        addFormField(form, "Phone / account reference", reference);
        addFormField(form, "Amount", amount);
        addDateField(form, "Last payment", lastPaid);
        addDateField(form, "Next expiry (required)", dueDate);
        addFormField(form, "Notes", notes);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add renewal" : "Edit renewal")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String memberValue = member.getText().toString().trim();
                    String serviceValue = service.getText().toString().trim();
                    String lastValue = lastPaid.getText().toString().trim();
                    String dueValue = dueDate.getText().toString().trim();
                    if (memberValue.isEmpty() || serviceValue.isEmpty()) {
                        Toast.makeText(this, "Enter the family member and service.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (DateRules.parse(dueValue) == null) {
                        Toast.makeText(this, "Choose a valid next expiry date.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!lastValue.isEmpty() && DateRules.parse(lastValue) == null) {
                        Toast.makeText(this, "Last payment date must use YYYY-MM-DD.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    source.member = memberValue;
                    source.service = serviceValue;
                    source.category = String.valueOf(category.getSelectedItem());
                    source.reference = reference.getText().toString().trim();
                    source.amount = amount.getText().toString().trim();
                    source.lastPaid = lastValue;
                    source.dueDate = dueValue;
                    source.notes = notes.getText().toString().trim();

                    List<RenewalEntry> current = RenewalStore.load(this);
                    if (existing == null) {
                        current.add(source);
                    } else {
                        for (int i = 0; i < current.size(); i++) {
                            if (current.get(i).id.equals(source.id)) {
                                current.set(i, source);
                                break;
                            }
                        }
                    }
                    if (!RenewalStore.save(this, current)) {
                        Toast.makeText(this, "Could not save this renewal.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    getContentResolver().notifyChange(RenewalsProvider.TURNS_URI, null);
                    dialog.dismiss();
                    render();
                }));
        dialog.show();
    }

    private void addFormField(LinearLayout parent, String label, View field) {
        parent.addView(text(label, 13, MUTED, true), lp(-1, -2, 2));
        parent.addView(field, lp(-1, -2, 8));
    }

    private void addDateField(LinearLayout parent, String label, EditText field) {
        field.setFocusable(true);
        field.setFocusableInTouchMode(true);
        field.setKeyListener(null);
        field.setCursorVisible(false);
        field.setClickable(true);
        field.setOnClickListener(v -> pickDate(field));
        addFormField(parent, label, field);
    }

    private void confirmDelete(RenewalEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Delete renewal?")
                .setMessage("Remove " + entry.service + " for " + entry.member + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    List<RenewalEntry> current = RenewalStore.load(this);
                    current.removeIf(item -> item.id.equals(entry.id));
                    RenewalStore.save(this, current);
                    getContentResolver().notifyChange(RenewalsProvider.TURNS_URI, null);
                    render();
                })
                .show();
    }
}

package io.github.justinnewman.gameday;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adding a calendar with the remote: 1 the link (fetched and checked before going on), 2 a name, 3 the sport,
 * 4 a colour, with a preview of the turn on Home. Opened with {@link #edit} it shows one step to change one thing.
 */
public final class AddCalendarActivity extends Activity {
    static final int STEP_LINK = 0;
    static final int STEP_NAME = 1;
    static final int STEP_SPORT = 2;
    static final int STEP_COLOUR = 3;
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_STEP = "step";

    private final Handler main = new Handler(Looper.getMainLooper());
    private Repository repo;
    private TextView note;
    private EditText field;
    private ScrollView sportScroll;
    private LinearLayout sports;
    private ColourGrid grid;
    private TextView colourName;
    private View preview;

    /** Null when adding. */
    private String editId;
    /** The one step to show when changing a calendar; -1 for the four steps. */
    private int only = -1;
    private int step;
    private String link;
    private String name = "";
    private Sport sport = Sport.OTHER;
    private int colour = Palette.COLUMNS;
    private int entries;
    private Ics.Event nextEvent;
    private boolean busy;

    static Intent edit(Context ctx, String id, int step) {
        return new Intent(ctx, AddCalendarActivity.class).putExtra(EXTRA_ID, id).putExtra(EXTRA_STEP, step);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_add);
        repo = new Repository(this);
        note = findViewById(R.id.note);
        field = findViewById(R.id.field);
        sportScroll = findViewById(R.id.sportScroll);
        sports = findViewById(R.id.sports);
        grid = findViewById(R.id.grid);
        colourName = findViewById(R.id.colourName);
        preview = findViewById(R.id.preview);
        findViewById(R.id.next).setOnClickListener(v -> next());
        field.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_DONE
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                next();
                return true;
            }
            return false;
        });
        buildSports();
        editId = getIntent().getStringExtra(EXTRA_ID);
        if (editId == null) {
            Background.read(() -> {
                Set<Integer> used = usedColours(null);
                colour = Palette.firstUnused(used);
                main.post(() -> show(STEP_LINK));
            });
            return;
        }
        only = getIntent().getIntExtra(EXTRA_STEP, STEP_NAME);
        Background.read(() -> {
            Calendar c = repo.find(editId);
            if (c == null) {
                main.post(this::finish);
                return;
            }
            link = c.link;
            name = c.name;
            sport = c.sport;
            colour = c.colour;
            nextEvent = repo.nextEvent(c, System.currentTimeMillis(), ZoneId.systemDefault());
            main.post(() -> {
                ((TextView) findViewById(R.id.title)).setText(c.name);
                findViewById(R.id.steps).setVisibility(View.GONE);
                show(only);
            });
        });
    }

    private void show(int s) {
        step = s;
        int[] labels = {R.id.step1, R.id.step2, R.id.step3, R.id.step4};
        for (int i = 0; i < labels.length; i++) {
            ((TextView) findViewById(labels[i])).setTextColor(i == s ? 0xFFFFFFFF : i < s ? 0xB3FFFFFF : 0x66FFFFFF);
        }
        boolean typing = s == STEP_LINK || s == STEP_NAME;
        field.setVisibility(typing ? View.VISIBLE : View.GONE);
        findViewById(R.id.next).setVisibility(typing ? View.VISIBLE : View.GONE);
        sportScroll.setVisibility(s == STEP_SPORT ? View.VISIBLE : View.GONE);
        grid.setVisibility(s == STEP_COLOUR ? View.VISIBLE : View.GONE);
        colourName.setVisibility(s == STEP_COLOUR ? View.VISIBLE : View.GONE);
        preview.setVisibility(s == STEP_SPORT || s == STEP_COLOUR ? View.VISIBLE : View.GONE);
        note.setTextColor(0x8CFFFFFF);
        switch (s) {
            case STEP_LINK:
                note.setText("");
                field.setText("");
                field.setHint(R.string.link_hint);
                field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                field.requestFocus();
                break;
            case STEP_NAME:
                note.setText(only < 0 ? getString(R.string.checked,
                        getResources().getQuantityString(R.plurals.entries, entries, entries)) : "");
                field.setText(name);
                field.setSelection(name.length());
                field.setHint("");
                field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                field.requestFocus();
                break;
            case STEP_SPORT:
                note.setText("");
                for (int i = 0; i < sports.getChildCount(); i++) {
                    View row = sports.getChildAt(i);
                    if (row.getTag() == sport) row.requestFocus();
                }
                updatePreview(sport);
                break;
            default:
                note.setText(R.string.colour_hint);
                Background.read(() -> {
                    Set<Integer> used = usedColours(editId);
                    main.post(() -> {
                        grid.set(colour, used, new ColourGrid.Listener() {
                            @Override
                            public void onMoved(int index) {
                                colour = index;
                                colourName.setText(Palette.name(index));
                                updatePreview(sport);
                            }

                            @Override
                            public void onChosen(int index) {
                                colour = index;
                                save();
                            }
                        });
                        colourName.setText(Palette.name(colour));
                        grid.requestFocus();
                        updatePreview(sport);
                    });
                });
                break;
        }
    }

    private void buildSports() {
        float d = getResources().getDisplayMetrics().density;
        for (Sport s : Sport.values()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundResource(R.drawable.row);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(Math.round(16 * d), Math.round(9 * d), Math.round(16 * d), Math.round(9 * d));
            row.setTag(s);
            TextView label = new TextView(this);
            label.setText(s.label());
            label.setTextSize(16);
            label.setTextColor(0xE6FFFFFF);
            TextView word = new TextView(this);
            word.setText(s.word());
            word.setTextSize(14);
            word.setTextColor(0x80FFFFFF);
            word.setGravity(Gravity.END);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(word, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused) updatePreview(s);
            });
            row.setOnClickListener(v -> {
                sport = s;
                if (only >= 0) save();
                else show(STEP_COLOUR);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Math.round(6 * d);
            sports.addView(row, lp);
        }
    }

    private void updatePreview(Sport s) {
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(Palette.colour(colour));
        bar.setCornerRadius(2 * getResources().getDisplayMetrics().density);
        findViewById(R.id.previewBar).setBackground(bar);
        TextView top = findViewById(R.id.previewTop);
        TextView word = findViewById(R.id.previewWord);
        if (nextEvent == null) {
            top.setText(name);
            word.setText(R.string.no_entry);
            return;
        }
        top.setText(Titles.topLine(nextEvent.title, name));
        long until = nextEvent.start.toEpochMilli() - System.currentTimeMillis();
        word.setText(until <= 0 ? getString(R.string.live_now) : Titles.word(s, nextEvent.title) + " in " + Countdown.text(until));
    }

    private void next() {
        if (busy) return;
        if (step == STEP_LINK) checkLink();
        else if (step == STEP_NAME) {
            String n = field.getText().toString().trim();
            if (n.isEmpty() || n.length() > CalendarStore.MAX_NAME) {
                error(getString(R.string.name_bad));
                return;
            }
            name = n;
            if (only >= 0) save();
            else show(STEP_SPORT);
        }
    }

    private void checkLink() {
        String l = Links.normalise(field.getText().toString());
        if (l == null) {
            error(getString(R.string.link_bad));
            return;
        }
        busy = true;
        note.setTextColor(0x8CFFFFFF);
        note.setText(R.string.checking);
        Background.execute(() -> {
            String id = Links.id(l);
            if (repo.find(id) != null && !id.equals(editId)) {
                main.post(() -> {
                    busy = false;
                    error(getString(R.string.link_twice));
                });
                return;
            }
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            Calendar probe = new Calendar(id, name, l, sport, colour);
            FetchStatus s = new Fetcher().fetch(probe, repo.dir(), null, now);
            if (s.error != null) {
                main.post(() -> {
                    busy = false;
                    error(s.error);
                });
                return;
            }
            repo.saveStatus(id, s);
            List<String> titles = repo.titles(probe, now, zone);
            Ics.Event next = repo.nextEvent(probe, now, zone);
            String team = Titles.likelyTeam(titles);
            Sport guess = null;
            for (String t : titles) {
                guess = Sport.fromEmoji(t);
                if (guess != null) break;
            }
            Sport guessed = guess;
            main.post(() -> {
                busy = false;
                link = l;
                entries = s.entries;
                nextEvent = next;
                if (only >= 0) {
                    save();
                    return;
                }
                if (team != null) name = team;
                if (guessed != null) sport = guessed;
                show(STEP_NAME);
            });
        });
    }

    /** Adds the new calendar, or puts the changed one back in its place. */
    private void save() {
        Calendar c = new Calendar(Links.id(link), name, link, sport, colour);
        Background.read(() -> {
            boolean ok = true;
            if (editId == null) ok = repo.add(c);
            else repo.replace(editId, c);
            GamesProvider.changed(getApplicationContext());
            boolean added = ok;
            main.post(() -> {
                if (!added) Toast.makeText(this, R.string.full, Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void error(String text) {
        note.setTextColor(0xFFFF8A80);
        note.setText(text);
    }

    /** Colours other calendars use (not the one being changed). */
    private Set<Integer> usedColours(String except) {
        Set<Integer> used = new HashSet<>();
        for (Calendar c : repo.calendars()) {
            if (!c.id.equals(except)) used.add(c.colour);
        }
        return used;
    }

    @Override
    public void onBackPressed() {
        if (only < 0 && step > STEP_LINK && !busy) show(step - 1);
        else super.onBackPressed();
    }
}

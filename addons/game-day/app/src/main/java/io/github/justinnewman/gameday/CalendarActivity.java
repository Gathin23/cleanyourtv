package io.github.justinnewman.gameday;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.time.ZoneId;

/** One calendar: its sport, colour, name and link to change, fetching it, moving it up, removing it. */
public final class CalendarActivity extends Activity {
    static final String EXTRA_ID = "id";

    private final Handler main = new Handler(Looper.getMainLooper());
    private Repository repo;
    private String id;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_calendar);
        repo = new Repository(this);
        id = getIntent().getStringExtra(EXTRA_ID);
        findViewById(R.id.sport).setOnClickListener(v -> startActivity(AddCalendarActivity.edit(this, id, AddCalendarActivity.STEP_SPORT)));
        findViewById(R.id.colour).setOnClickListener(v -> startActivity(AddCalendarActivity.edit(this, id, AddCalendarActivity.STEP_COLOUR)));
        findViewById(R.id.name).setOnClickListener(v -> startActivity(AddCalendarActivity.edit(this, id, AddCalendarActivity.STEP_NAME)));
        findViewById(R.id.link).setOnClickListener(v -> {
            startActivity(AddCalendarActivity.edit(this, id, AddCalendarActivity.STEP_LINK));
            // A new link is a new id: this screen's calendar is gone once it is saved.
            finish();
        });
        findViewById(R.id.fetch).setOnClickListener(v -> Background.execute(() -> {
            Calendar c = repo.find(id);
            if (c == null) return;
            repo.fetch(new Fetcher(), c, System.currentTimeMillis());
            GamesProvider.changed(getApplicationContext());
            main.post(this::load);
        }));
        findViewById(R.id.up).setOnClickListener(v -> Background.read(() -> {
            repo.moveUp(id);
            GamesProvider.changed(getApplicationContext());
        }));
        findViewById(R.id.remove).setOnClickListener(v -> confirmRemove());
        findViewById(R.id.sport).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        Background.read(() -> {
            Calendar c = repo.find(id);
            if (c == null) {
                main.post(this::finish);
                return;
            }
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            FetchStatus s = repo.status(c.id);
            String sub;
            if (s != null && s.error != null) {
                sub = s.lastSuccessMs > 0 ? getString(R.string.failed, Ago.text(now, s.lastSuccessMs), s.error)
                        : getString(R.string.failed_never, s.error);
            } else {
                Schedule.Turn t = repo.next(c, now, zone);
                String next = t == null ? Repository.NOTHING : Repository.when(t, zone) + ", " + t.topLine;
                int n = s == null ? 0 : s.entries;
                sub = getString(R.string.loaded, getResources().getQuantityString(R.plurals.entries, n, n), next);
            }
            main.post(() -> show(c, sub));
        });
    }

    private void show(Calendar c, String sub) {
        ((TextView) findViewById(R.id.title)).setText(c.name);
        ((TextView) findViewById(R.id.sub)).setText(sub);
        ((TextView) findViewById(R.id.sport)).setText(getString(R.string.sport) + "     " + c.sport.label() + " · " + c.sport.word());
        ((TextView) findViewById(R.id.colour)).setText(getString(R.string.colour) + "     " + Palette.name(c.colour));
        ((TextView) findViewById(R.id.name)).setText(getString(R.string.name) + "     " + c.name);
        ((TextView) findViewById(R.id.link)).setText(getString(R.string.link) + "     " + Links.mask(c.link));
    }

    private void confirmRemove() {
        Calendar c = repo.find(id);
        if (c == null) return;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.remove_title, c.name))
                .setMessage(R.string.remove_text)
                .setPositiveButton(R.string.keep, null)
                .setNegativeButton(R.string.remove_short, (x, w) -> Background.read(() -> {
                    repo.remove(id);
                    GamesProvider.changed(getApplicationContext());
                    main.post(this::finish);
                }))
                .show();
        // A stray OK keeps it.
        d.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
    }
}

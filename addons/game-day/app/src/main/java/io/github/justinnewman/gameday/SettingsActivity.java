package io.github.justinnewman.gameday;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Game Day's main screen: the calendars, and adding one, the seconds per turn, fetching, help. */
public final class SettingsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Repository repo;
    private LinearLayout list;
    private TextView empty;
    private TextView footer;
    private TextView seconds;
    private String focusId;

    private final ContentObserver changes = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            load();
        }
    };

    private static final class Row {
        Calendar calendar;
        String meta;
        boolean failed;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_settings);
        repo = new Repository(this);
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        footer = findViewById(R.id.footer);
        seconds = findViewById(R.id.seconds);
        empty.setText(getString(R.string.none_yet) + "\n" + getString(R.string.none_yet_more));
        findViewById(R.id.add).setOnClickListener(v -> Background.read(() -> {
            boolean full = repo.calendars().size() >= CalendarStore.MAX;
            main.post(() -> {
                if (full) Toast.makeText(this, R.string.full, Toast.LENGTH_SHORT).show();
                else startActivity(new Intent(this, AddCalendarActivity.class));
            });
        }));
        seconds.setOnClickListener(v -> startActivity(new Intent(this, SecondsActivity.class)));
        findViewById(R.id.fetch).setOnClickListener(v -> {
            Toast.makeText(this, R.string.fetching, Toast.LENGTH_SHORT).show();
            Background.execute(() -> {
                Fetcher f = new Fetcher();
                long now = System.currentTimeMillis();
                for (Calendar c : repo.calendars()) repo.fetch(f, c, now);
                GamesProvider.changed(getApplicationContext());
            });
        });
        findViewById(R.id.help).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.help_title).setMessage(R.string.help_text).setPositiveButton(R.string.ok, null).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(GamesProvider.TURNS, false, changes);
        load();
        Background.refresh(this, System.currentTimeMillis());
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(changes);
    }

    private void load() {
        Background.read(() -> {
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            List<Row> rows = new ArrayList<>();
            long updated = 0;
            for (Calendar c : repo.calendars()) {
                Row r = new Row();
                r.calendar = c;
                FetchStatus s = repo.status(c.id);
                if (s != null) updated = Math.max(updated, s.lastSuccessMs);
                if (s != null && s.error != null) {
                    r.failed = true;
                    r.meta = s.lastSuccessMs > 0 ? getString(R.string.failed, Ago.text(now, s.lastSuccessMs), s.error)
                            : getString(R.string.failed_never, s.error);
                } else {
                    Schedule.Turn t = repo.next(c, now, zone);
                    r.meta = c.sport.label() + " · " + (t == null ? Repository.NOTHING
                            : Repository.when(t, zone) + " · " + t.topLine);
                }
                rows.add(r);
            }
            int turn = repo.turnSeconds();
            String footerText = getString(R.string.footer, Ago.text(now, updated),
                    getResources().getQuantityString(R.plurals.calendars, rows.size(), rows.size()));
            main.post(() -> show(rows, turn, footerText));
        });
    }

    private void show(List<Row> rows, int turn, String footerText) {
        if (isFinishing()) return;
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) focusId = (String) focused.getTag();
        list.removeAllViews();
        LayoutInflater inf = getLayoutInflater();
        View again = null;
        for (Row r : rows) {
            View v = inf.inflate(R.layout.item_calendar, list, false);
            GradientDrawable bar = new GradientDrawable();
            bar.setCornerRadius(4 * getResources().getDisplayMetrics().density);
            bar.setColor(Palette.colour(r.calendar.colour));
            v.findViewById(R.id.bar).setBackground(bar);
            ((TextView) v.findViewById(R.id.name)).setText(r.calendar.name);
            TextView meta = v.findViewById(R.id.meta);
            meta.setText(r.meta);
            if (r.failed) meta.setTextColor(0xFFFF8A80);
            ((TextView) v.findViewById(R.id.link)).setText(Links.mask(r.calendar.link));
            v.setTag(r.calendar.id);
            v.setOnClickListener(x -> startActivity(new Intent(this, CalendarActivity.class)
                    .putExtra(CalendarActivity.EXTRA_ID, r.calendar.id)));
            list.addView(v);
            if (r.calendar.id.equals(focusId)) again = v;
        }
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        seconds.setText(getString(R.string.seconds) + "     " + turn);
        footer.setText(footerText);
        if (again != null) again.requestFocus();
        else if (getCurrentFocus() == null) findViewById(rows.isEmpty() ? R.id.add : R.id.list).requestFocus();
    }
}

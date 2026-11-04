package io.github.rahulk847.themelibrary;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The tile and module card open this: the theme on screen, the saved themes (OK switches, hold OK renames or
 * deletes), a row to save the current theme under a name, and the built-in look. Opening the app saves a theme it
 * has not seen yet by itself, and so does every switch before it replaces the theme on screen, so a theme pushed
 * with tools/theme.sh is never lost by switching away from it.
 */
public class MainActivity extends Activity {
    static final String LAUNCHER = "com.gathin.tvhome";
    private static final int ASK_STORAGE = 1;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout rows;
    private TextView status;
    private Library library;
    /** What the last scan found; the save row and the dialogs read it. */
    private Scan last;
    /** A switch or download is running: further OKs are ignored until it ends. */
    private boolean busy;
    /** The download list is showing instead of the library; Back returns. */
    private boolean storeMode;
    /** The repo folder last chosen in the download list, so focus comes back to it after a reload. */
    private String storeFocus;

    /** One look at the launcher's theme and the library, made off the main thread. */
    private static final class Scan {
        List<Library.Entry> entries = new ArrayList<>();
        String current;
        boolean unreadable;
        String note;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File own = getExternalFilesDir(null);
        library = new Library(new File(own != null ? own : getFilesDir(), "library"));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        page.addView(title);
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        busy = false;
        if (storeMode) openStore();
        else refresh();
    }

    @Override
    public void onBackPressed() {
        if (storeMode && !busy) {
            storeMode = false;
            storeFocus = null;
            refresh();
        } else if (!storeMode) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == ASK_STORAGE) refresh();
    }

    /** The launcher's theme folder, the one tools/theme.sh writes. */
    private static File launcherDir() {
        return new File(Environment.getExternalStorageDirectory(), "Android/data/" + LAUNCHER + "/files");
    }

    private void refresh() {
        rows.removeAllViews();
        if (Build.VERSION.SDK_INT >= 30) {
            status.setText("");
            add(new Row(this, getString(R.string.unsupported_title), getString(R.string.unsupported_detail), null));
            return;
        }
        if (!launcherInstalled()) {
            status.setText("");
            add(new Row(this, getString(R.string.no_launcher_title), getString(R.string.no_launcher_detail), null));
            return;
        }
        if (!storageAllowed()) {
            status.setText("");
            add(new Row(this, getString(R.string.allow_title), getString(R.string.allow_detail), this::askStorage)).requestFocus();
            return;
        }
        io.execute(() -> {
            Scan s = scan(true);
            main.post(() -> show(s));
        });
    }

    /**
     * Reads the theme on screen and the library. With {@code autoSave}, a theme on screen that the library lacks is
     * saved first, under a dated name the owner can change.
     */
    private Scan scan(boolean autoSave) {
        Scan s = new Scan();
        String fallback = getString(R.string.unnamed);
        try {
            s.current = ThemeFiles.fingerprint(launcherDir());
        } catch (IOException e) {
            s.unreadable = true;
        }
        s.entries = library.load(fallback);
        if (autoSave && s.current != null && Library.find(s.entries, s.current) == null) {
            long now = System.currentTimeMillis();
            String name = getString(R.string.auto_name, when(now));
            try {
                library.save(launcherDir(), name, now);
                s.note = getString(R.string.auto_saved, name);
            } catch (IOException e) {
                s.note = getString(R.string.save_failed, e.getMessage());
            }
            s.entries = library.load(fallback);
        }
        return s;
    }

    /** Rebuilds the list from a scan; focus stays on the row it was on. */
    private void show(Scan s) {
        last = s;
        int focused = rows.indexOfChild(rows.getFocusedChild());
        rows.removeAllViews();
        Library.Entry current = Library.find(s.entries, s.current);
        String now = s.unreadable ? getString(R.string.on_screen_unknown)
                : s.current == null ? getString(R.string.on_screen_builtin)
                : current != null ? "“" + current.name + "”" : getString(R.string.on_screen_unknown);
        status.setText(getString(R.string.on_screen, now));
        if (s.note != null) Toast.makeText(this, s.note, Toast.LENGTH_LONG).show();

        Row first = add(new Row(this, getString(R.string.save_title), getString(R.string.save_detail), this::saveCurrent));
        add(new Row(this, getString(R.string.store_title), getString(R.string.store_detail), this::openStore));

        heading(getString(R.string.heading_saved));
        if (s.entries.isEmpty()) {
            add(new Row(this, getString(R.string.none_saved), getString(R.string.none_saved_detail), null));
        }
        for (Library.Entry e : s.entries) {
            boolean on = e == current;
            Row r = add(new Row(this, e.name, getString(R.string.theme_detail, pictures(e.pictures), when(e.saved)),
                    () -> apply(e)));
            r.setValue(on ? getString(R.string.current) : "");
            r.setOnLongClickListener(v -> {
                manage(e);
                return true;
            });
            if (on && focused < 0) first = r;
        }

        Row builtin = add(new Row(this, getString(R.string.builtin_title), getString(R.string.builtin_detail),
                () -> apply(null)));
        builtin.setValue(s.current == null && !s.unreadable ? getString(R.string.current) : "");

        View keep = focused >= 0 && focused < rows.getChildCount() ? rows.getChildAt(focused) : null;
        (keep != null && keep.isFocusable() ? keep : first).requestFocus();
    }

    /** The download list: every theme folder in the CleanYourTV repository, with its README description. */
    private void openStore() {
        if (busy) return;
        storeMode = true;
        rows.removeAllViews();
        status.setText(R.string.store_loading);
        io.execute(() -> {
            List<String> folders = null;
            String error = null;
            try {
                folders = Download.folders();
            } catch (IOException e) {
                error = e.getMessage();
            }
            Map<String, String> about = folders == null ? null : Store.descriptions(Download.readme());
            List<Library.Entry> entries = library.load(getString(R.string.unnamed));
            List<String> f = folders;
            String err = error;
            main.post(() -> showStore(f, about, entries, err));
        });
    }

    private void showStore(List<String> folders, Map<String, String> about, List<Library.Entry> entries, String error) {
        if (!storeMode) return;
        rows.removeAllViews();
        if (folders == null) {
            status.setText(R.string.store_failed_status);
            add(new Row(this, getString(R.string.store_failed), error, this::openStore)).requestFocus();
            return;
        }
        status.setText(getString(R.string.store_status, Store.OWNER_REPO));
        Row first = null;
        Row chosen = null;
        for (String folder : folders) {
            String name = Store.prettyName(folder);
            Library.Entry have = Library.fromRepo(entries, folder, name);
            String detail = about.get(folder);
            Row r = add(new Row(this, name, detail != null ? detail : getString(R.string.store_no_description),
                    () -> {
                        storeFocus = folder;
                        if (have != null) apply(have);
                        else download(folder, name);
                    }));
            r.setValue(getString(have != null ? R.string.store_in_library : R.string.store_get));
            if (first == null) first = r;
            if (folder.equals(storeFocus)) chosen = r;
        }
        if (first == null) {
            add(new Row(this, getString(R.string.store_empty), null, null));
            return;
        }
        (chosen != null ? chosen : first).requestFocus();
    }

    /** Downloads one theme into the library, then offers to switch to it. A theme already saved is not doubled. */
    private void download(String folder, String name) {
        if (busy) return;
        busy = true;
        status.setText(getString(R.string.store_downloading, name));
        io.execute(() -> {
            File staging = new File(getCacheDir(), "download");
            Library.Entry got = null;
            String message;
            try {
                Download.theme(folder, staging);
                Library.Entry same = Library.find(library.load(getString(R.string.unnamed)), ThemeFiles.fingerprint(staging));
                if (same != null) {
                    Library.markSource(same, folder);
                    got = same;
                    message = getString(R.string.save_already, same.name);
                } else {
                    got = library.save(staging, name, System.currentTimeMillis(), folder);
                    message = null;
                }
            } catch (IOException e) {
                message = getString(R.string.store_download_failed, name, e.getMessage());
            } finally {
                try {
                    ThemeFiles.deleteTree(staging);
                } catch (IOException ignored) {
                    // Cache; Android clears it when space runs low.
                }
            }
            Library.Entry entry = got;
            String msg = message;
            main.post(() -> {
                busy = false;
                if (entry == null) {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    openStore();
                    return;
                }
                if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                status.setText(getString(R.string.store_status, Store.OWNER_REPO));
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(getString(R.string.store_added, entry.name))
                        .setMessage(R.string.store_switch_now)
                        .setPositiveButton(R.string.store_switch, (d, w) -> apply(entry))
                        .setNegativeButton(R.string.store_later, (d, w) -> openStore())
                        .setOnCancelListener(d -> openStore())
                        .show();
            });
        });
    }

    /** Switches to a saved theme, or with null to the built-in look, then restarts Home so the launcher reads it. */
    private void apply(Library.Entry e) {
        if (busy) return;
        busy = true;
        storeMode = false; // coming back to the app after the switch shows the library
        status.setText(getString(R.string.applying, e == null ? getString(R.string.builtin_title) : e.name));
        io.execute(() -> {
            String error = null;
            try {
                scan(true); // keep the theme on screen before it is replaced
                File dir = launcherDir();
                if (e == null) ThemeFiles.clear(dir);
                else ThemeFiles.copy(e.dir, dir);
                ThemeFiles.stampRevision(dir);
            } catch (IOException ex) {
                error = ex.getMessage();
            }
            String failed = error;
            main.post(() -> {
                if (failed != null) {
                    busy = false;
                    Toast.makeText(this, getString(R.string.apply_failed, failed), Toast.LENGTH_LONG).show();
                    refresh();
                } else {
                    restartHome();
                }
            });
        });
    }

    /**
     * The launcher reads its theme when it starts. It is in the background while this screen is open, so ending its
     * process and asking for Home brings it back with the new theme, as tools/theme.sh does with a force-stop.
     */
    private void restartHome() {
        ActivityManager am = getSystemService(ActivityManager.class);
        if (am != null) am.killBackgroundProcesses(LAUNCHER);
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void saveCurrent() {
        Scan s = last;
        if (s == null || busy) return;
        if (s.current == null) {
            Toast.makeText(this, R.string.save_nothing, Toast.LENGTH_LONG).show();
            return;
        }
        Library.Entry existing = Library.find(s.entries, s.current);
        if (existing != null) {
            Toast.makeText(this, getString(R.string.save_already, existing.name), Toast.LENGTH_LONG).show();
            return;
        }
        askName(getString(R.string.save_name_title), "Theme " + (s.entries.size() + 1), name -> io.execute(() -> {
            long now = System.currentTimeMillis();
            String msg;
            try {
                library.save(launcherDir(), name, now);
                msg = getString(R.string.saved_as, name);
            } catch (IOException ex) {
                msg = getString(R.string.save_failed, ex.getMessage());
            }
            String m = msg;
            main.post(() -> {
                Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                refresh();
            });
        }));
    }

    /** Hold OK on a saved theme: rename or delete it. */
    private void manage(Library.Entry e) {
        if (busy) return;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.name)
                .setItems(new CharSequence[]{getString(R.string.manage_rename), getString(R.string.manage_delete)},
                        (d, which) -> {
                            if (which == 0) {
                                askName(getString(R.string.manage_rename), e.name, name -> io.execute(() -> {
                                    try {
                                        Library.rename(e, name);
                                    } catch (IOException ignored) {
                                        // The old name stays; the list shows it.
                                    }
                                    main.post(this::refresh);
                                }));
                            } else {
                                confirmDelete(e);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(Library.Entry e) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage(getString(R.string.delete_confirm, e.name))
                .setPositiveButton(R.string.manage_delete, (d, w) -> io.execute(() -> {
                    try {
                        Library.delete(e);
                    } catch (IOException ignored) {
                        // A folder left behind without its config is skipped by the list.
                    }
                    main.post(this::refresh);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private interface NameCallback {
        void named(String name);
    }

    private void askName(String title, String suggestion, NameCallback done) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setText(suggestion);
        field.setSelection(field.getText().length());
        LinearLayout box = new LinearLayout(this);
        box.setPadding(px(24), px(8), px(24), 0);
        box.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) ->
                        done.named(ThemeFiles.cleanName(field.getText().toString(), suggestion)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private boolean launcherInstalled() {
        try {
            getPackageManager().getPackageInfo(LAUNCHER, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean storageAllowed() {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void askStorage() {
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, ASK_STORAGE);
    }

    private String pictures(int n) {
        return n == 0 ? getString(R.string.no_wallpaper)
                : n == 1 ? getString(R.string.one_wallpaper) : getString(R.string.n_wallpapers, n);
    }

    private String when(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void heading(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(13);
        h.setTextColor(0xFFB58CFF);
        h.setPadding(px(16), px(20), 0, px(4));
        rows.addView(h);
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

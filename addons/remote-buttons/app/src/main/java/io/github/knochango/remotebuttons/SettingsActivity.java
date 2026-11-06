package io.github.knochango.remotebuttons;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

/**
 * The module card on the launcher's Home (and the app's own tile): one row per button that opens an app (OK picks
 * another app or None), a row that learns a new button from the next press, and what the add-on needs to work.
 */
public class SettingsActivity extends Activity {
    /** How long "Add a button" waits for a press. */
    private static final long LEARN_MS = 20_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable giveUp = this::learnTimedOut;
    private LinearLayout buttons;
    private TextView add, service, logs, overlay, last;
    private boolean learning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.page(this);
        Ui.heading(root, R.string.app_name, R.string.hint);

        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(buttons);
        add = Ui.row(root);
        add.setOnClickListener(v -> startLearning());

        TextView gap = Ui.status(root);
        gap.setText("");
        service = Ui.status(root);
        logs = Ui.status(root);
        overlay = Ui.status(root);
        last = Ui.status(root);

        setContentView(root);
        add.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        stopLearning();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (learning) {
            stopLearning();
            refresh();
            return;
        }
        super.onBackPressed();
    }

    private void startLearning() {
        learning = true;
        add.setText(R.string.learning);
        KeyLogService.learner = keyCode -> {
            stopLearning();
            pick(keyCode);
        };
        main.postDelayed(giveUp, LEARN_MS);
    }

    private void stopLearning() {
        learning = false;
        KeyLogService.learner = null;
        main.removeCallbacks(giveUp);
    }

    private void learnTimedOut() {
        stopLearning();
        refresh();
        Toast.makeText(this, R.string.learn_timeout, Toast.LENGTH_LONG).show();
    }

    private void pick(int keyCode) {
        startActivity(new Intent(this, PickerActivity.class).putExtra(PickerActivity.EXTRA_KEY, keyCode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void refresh() {
        buttons.removeAllViews();
        for (Map.Entry<Integer, String> entry : Prefs.mappings(this).entrySet()) {
            int keyCode = entry.getKey();
            TextView row = Ui.row(buttons);
            row.setText(getString(R.string.mapping, keyLabel(keyCode), appName(entry.getValue())));
            row.setOnClickListener(v -> pick(keyCode));
        }
        if (!learning) add.setText(R.string.add);

        service.setText(getString(R.string.status_service, getString(KeyLogService.bound ? R.string.on : R.string.off)));
        boolean readable = checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
        logs.setText(getString(R.string.status_logs, getString(readable ? R.string.allowed : R.string.not_allowed)));
        overlay.setText(getString(R.string.status_overlay,
                getString(Settings.canDrawOverlays(this) ? R.string.allowed : R.string.not_allowed)));
        int seen = KeyLogService.lastKey;
        last.setText(getString(R.string.status_last, seen == 0 ? getString(R.string.none_yet) : keyLabel(seen)));
    }

    private String keyLabel(int keyCode) {
        return getString(R.string.key_label, keyCode, KeyLog.name(keyCode));
    }

    private String appName(String pkg) {
        if (Launch.LIBRARY.equals(pkg)) return getString(R.string.library);
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.missing, pkg);
        }
    }
}

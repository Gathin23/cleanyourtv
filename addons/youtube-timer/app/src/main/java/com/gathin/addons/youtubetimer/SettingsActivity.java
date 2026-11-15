package com.gathin.addons.youtubetimer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class SettingsActivity extends Activity {

    private static final int REQUEST_CHANGE_LIMIT = 101;
    private static final int REQUEST_RESET_TIME = 102;

    private TimerPreferences prefs;
    private TextView tvStats;
    private UsageGraphView usageGraph;
    private Button btnLimit;
    private Button btnPin;

    private static final int[] PRESET_MINUTES = {15, 30, 45, 60, 90, 120};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new TimerPreferences(this);

        tvStats = findViewById(R.id.tv_stats);
        usageGraph = findViewById(R.id.usage_graph);
        btnLimit = findViewById(R.id.btn_limit);
        btnPin = findViewById(R.id.btn_change_pin);
        Button btnReset = findViewById(R.id.btn_reset_today);
        Button btnTest = findViewById(R.id.btn_test_lock);

        updateUi();

        btnLimit.setOnClickListener(v -> {
            Intent prompt = new Intent(this, PinPromptActivity.class);
            prompt.putExtra(PinPromptActivity.EXTRA_TITLE, "Change Daily Limit");
            prompt.putExtra(PinPromptActivity.EXTRA_SUBTITLE, "Enter 4-digit PIN to change daily limit");
            startActivityForResult(prompt, REQUEST_CHANGE_LIMIT);
        });

        btnPin.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChangePinActivity.class);
            startActivity(intent);
        });

        btnReset.setOnClickListener(v -> {
            Intent prompt = new Intent(this, PinPromptActivity.class);
            prompt.putExtra(PinPromptActivity.EXTRA_TITLE, "Reset Watch Time");
            prompt.putExtra(PinPromptActivity.EXTRA_SUBTITLE, "Enter 4-digit PIN to reset today's watch time");
            startActivityForResult(prompt, REQUEST_RESET_TIME);
        });

        btnTest.setOnClickListener(v -> {
            Intent lock = new Intent(this, LockActivity.class);
            lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(lock);
        });

        btnLimit.requestFocus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CHANGE_LIMIT) {
                int current = prefs.getLimitMinutes();
                int next = 60;
                for (int i = 0; i < PRESET_MINUTES.length; i++) {
                    if (PRESET_MINUTES[i] == current) {
                        next = PRESET_MINUTES[(i + 1) % PRESET_MINUTES.length];
                        break;
                    }
                }
                prefs.setLimitMinutes(next);
                updateUi();
                Toast.makeText(this, "Daily limit set to " + next + " minutes", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQUEST_RESET_TIME) {
                prefs.resetToday();
                updateUi();
                Toast.makeText(this, "Today's watch time reset to 0 min", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    private void updateUi() {
        int usedSec = prefs.getSecondsToday();
        int usedMin = usedSec / 60;
        int limitMin = prefs.getLimitMinutes();
        int hours = usedMin / 60;
        int remMin = usedMin % 60;

        String timeStr;
        if (hours > 0) {
            timeStr = String.format(Locale.US, "%d min (%dh %dm) / %d min limit", usedMin, hours, remMin, limitMin);
        } else {
            timeStr = String.format(Locale.US, "%d min / %d min limit", usedMin, limitMin);
        }

        tvStats.setText("Today's Watch Time: " + timeStr);
        btnLimit.setText(String.format(Locale.US, "Daily Limit: %d Minutes", limitMin));
        btnPin.setText("Change 4-Digit PIN");

        if (usageGraph != null) {
            usageGraph.setData(prefs.getHalfHourUsageSecondsToday());
        }
    }
}

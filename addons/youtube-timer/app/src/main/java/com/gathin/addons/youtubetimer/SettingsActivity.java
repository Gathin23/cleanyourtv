package com.gathin.addons.youtubetimer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
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
                showLimitSelectionDialog();
            } else if (requestCode == REQUEST_RESET_TIME) {
                prefs.resetToday();
                updateUi();
                Toast.makeText(this, "Today's watch time reset to 0 min", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLimitSelectionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_select_limit);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int currentLimit = prefs.getLimitMinutes();

        int[][] options = {
            {R.id.btn_opt_15, 15},
            {R.id.btn_opt_30, 30},
            {R.id.btn_opt_45, 45},
            {R.id.btn_opt_60, 60},
            {R.id.btn_opt_90, 90},
            {R.id.btn_opt_120, 120},
            {R.id.btn_opt_180, 180},
            {R.id.btn_opt_240, 240}
        };

        Button initialFocusButton = null;

        for (int[] opt : options) {
            int viewId = opt[0];
            int minutes = opt[1];
            Button btn = dialog.findViewById(viewId);
            if (btn != null) {
                if (minutes == currentLimit) {
                    btn.setText("✓ " + btn.getText());
                    initialFocusButton = btn;
                }
                btn.setOnClickListener(v -> {
                    prefs.setLimitMinutes(minutes);
                    updateUi();
                    Toast.makeText(this, "Daily limit set to " + minutes + " minutes", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }
        }

        Button btnCancel = dialog.findViewById(R.id.btn_cancel_limit);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();

        if (initialFocusButton != null) {
            initialFocusButton.requestFocus();
        } else if (btnCancel != null) {
            btnCancel.requestFocus();
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

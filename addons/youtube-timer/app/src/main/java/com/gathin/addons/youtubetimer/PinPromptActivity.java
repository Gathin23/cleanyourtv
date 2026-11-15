package com.gathin.addons.youtubetimer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class PinPromptActivity extends Activity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SUBTITLE = "extra_subtitle";

    private TimerPreferences prefs;
    private StringBuilder enteredDigits = new StringBuilder();

    private TextView titleText;
    private TextView subtitleText;
    private TextView errorText;
    private ImageView[] dots = new ImageView[4];

    private final Handler lockoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable lockoutRunnable = new Runnable() {
        @Override
        public void run() {
            checkLockout();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_prompt);

        prefs = new TimerPreferences(this);

        titleText = findViewById(R.id.prompt_title);
        subtitleText = findViewById(R.id.prompt_subtitle);
        errorText = findViewById(R.id.prompt_error);

        Intent intent = getIntent();
        if (intent != null) {
            String customTitle = intent.getStringExtra(EXTRA_TITLE);
            if (customTitle != null) {
                titleText.setText(customTitle);
            }
            String customSubtitle = intent.getStringExtra(EXTRA_SUBTITLE);
            if (customSubtitle != null) {
                subtitleText.setText(customSubtitle);
            }
        }

        dots[0] = findViewById(R.id.dot_0);
        dots[1] = findViewById(R.id.dot_1);
        dots[2] = findViewById(R.id.dot_2);
        dots[3] = findViewById(R.id.dot_3);

        setupKeypad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLockout();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lockoutHandler.removeCallbacks(lockoutRunnable);
    }

    private boolean isLockedOut() {
        return prefs.getRemainingLockoutSeconds() > 0;
    }

    private void checkLockout() {
        long remaining = prefs.getRemainingLockoutSeconds();
        if (remaining > 0) {
            errorText.setText(String.format("Too many failed attempts. Try again in %ds", remaining));
            errorText.setVisibility(View.VISIBLE);
            enteredDigits.setLength(0);
            updateDots();
            lockoutHandler.postDelayed(lockoutRunnable, 1000);
        } else {
            lockoutHandler.removeCallbacks(lockoutRunnable);
            if (errorText.getVisibility() == View.VISIBLE && enteredDigits.length() == 0) {
                errorText.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void setupKeypad() {
        int[] numIds = {
                R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
                R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9
        };

        for (int i = 0; i <= 9; i++) {
            final String digit = String.valueOf(i);
            Button btn = findViewById(numIds[i]);
            if (btn != null) {
                btn.setOnClickListener(v -> appendDigit(digit));
            }
        }

        Button btnDel = findViewById(R.id.key_del);
        if (btnDel != null) {
            btnDel.setOnClickListener(v -> deleteDigit());
        }

        Button btnCancel = findViewById(R.id.key_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                setResult(RESULT_CANCELED);
                finish();
            });
        }

        Button key5 = findViewById(R.id.key_5);
        if (key5 != null) {
            key5.requestFocus();
        }
    }

    private void appendDigit(String digit) {
        if (isLockedOut()) {
            checkLockout();
            return;
        }

        if (enteredDigits.length() < 4) {
            enteredDigits.append(digit);
            updateDots();
            if (enteredDigits.length() == 4) {
                verifyPin();
            }
        }
    }

    private void deleteDigit() {
        if (isLockedOut()) {
            checkLockout();
            return;
        }

        if (enteredDigits.length() > 0) {
            enteredDigits.deleteCharAt(enteredDigits.length() - 1);
            errorText.setVisibility(View.INVISIBLE);
            updateDots();
        }
    }

    private void updateDots() {
        for (int i = 0; i < 4; i++) {
            dots[i].setImageResource(i < enteredDigits.length() ? R.drawable.pin_circle_filled : R.drawable.pin_circle_empty);
        }
    }

    private void verifyPin() {
        if (enteredDigits.toString().equals(prefs.getPin())) {
            prefs.resetFailedAttempts();
            setResult(RESULT_OK);
            finish();
        } else {
            prefs.recordFailedAttempt();
            long remaining = prefs.getRemainingLockoutSeconds();
            if (remaining > 0) {
                checkLockout();
            } else {
                int count = prefs.getFailedAttempts();
                errorText.setText(String.format("Incorrect PIN (%d/5 attempts)", count));
                errorText.setVisibility(View.VISIBLE);
                enteredDigits.setLength(0);
                updateDots();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendDigit(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            deleteDigit();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}


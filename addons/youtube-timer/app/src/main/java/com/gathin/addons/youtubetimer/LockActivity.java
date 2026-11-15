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
import android.widget.LinearLayout;
import android.widget.TextView;

public class LockActivity extends Activity {

    private TimerPreferences prefs;
    private StringBuilder enteredPin = new StringBuilder();

    private ImageView[] dots = new ImageView[4];
    private TextView errorText;
    private LinearLayout keypadContainer;
    private LinearLayout extensionContainer;

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
        setContentView(R.layout.activity_lock);

        prefs = new TimerPreferences(this);

        dots[0] = findViewById(R.id.dot_0);
        dots[1] = findViewById(R.id.dot_1);
        dots[2] = findViewById(R.id.dot_2);
        dots[3] = findViewById(R.id.dot_3);

        errorText = findViewById(R.id.pin_error);
        keypadContainer = findViewById(R.id.keypad_container);
        extensionContainer = findViewById(R.id.extension_container);

        setupKeypad();
        setupExtensions();
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
            enteredPin.setLength(0);
            updateDots();
            lockoutHandler.postDelayed(lockoutRunnable, 1000);
        } else {
            lockoutHandler.removeCallbacks(lockoutRunnable);
            if (errorText.getVisibility() == View.VISIBLE && enteredPin.length() == 0) {
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

        Button btnExit = findViewById(R.id.key_exit);
        if (btnExit != null) {
            btnExit.setOnClickListener(v -> exitToHome());
        }

        Button key5 = findViewById(R.id.key_5);
        if (key5 != null) {
            key5.requestFocus();
        }
    }

    private void setupExtensions() {
        Button btn30 = findViewById(R.id.btn_ext_30);
        if (btn30 != null) {
            btn30.setOnClickListener(v -> {
                prefs.addBonusMinutes(30);
                finish();
            });
        }

        Button btn60 = findViewById(R.id.btn_ext_60);
        if (btn60 != null) {
            btn60.setOnClickListener(v -> {
                prefs.addBonusMinutes(60);
                finish();
            });
        }

        Button btnAll = findViewById(R.id.btn_ext_all);
        if (btnAll != null) {
            btnAll.setOnClickListener(v -> {
                prefs.unlockForToday();
                finish();
            });
        }
    }

    private void appendDigit(String digit) {
        if (isLockedOut()) {
            checkLockout();
            return;
        }

        if (enteredPin.length() < 4) {
            enteredPin.append(digit);
            updateDots();
            if (enteredPin.length() == 4) {
                verifyPin();
            }
        }
    }

    private void deleteDigit() {
        if (isLockedOut()) {
            checkLockout();
            return;
        }

        if (enteredPin.length() > 0) {
            enteredPin.deleteCharAt(enteredPin.length() - 1);
            errorText.setVisibility(View.INVISIBLE);
            updateDots();
        }
    }

    private void updateDots() {
        for (int i = 0; i < 4; i++) {
            dots[i].setImageResource(i < enteredPin.length() ? R.drawable.pin_circle_filled : R.drawable.pin_circle_empty);
        }
    }

    private void verifyPin() {
        if (enteredPin.toString().equals(prefs.getPin())) {
            prefs.resetFailedAttempts();
            errorText.setVisibility(View.INVISIBLE);
            keypadContainer.setVisibility(View.GONE);
            extensionContainer.setVisibility(View.VISIBLE);

            Button btn30 = findViewById(R.id.btn_ext_30);
            if (btn30 != null) {
                btn30.requestFocus();
            }
        } else {
            prefs.recordFailedAttempt();
            long remaining = prefs.getRemainingLockoutSeconds();
            if (remaining > 0) {
                checkLockout();
            } else {
                int count = prefs.getFailedAttempts();
                errorText.setText(String.format("Incorrect PIN (%d/5 attempts)", count));
                errorText.setVisibility(View.VISIBLE);
                enteredPin.setLength(0);
                updateDots();
            }
        }
    }

    private void exitToHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
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
            exitToHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

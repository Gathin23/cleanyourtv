package com.gathin.addons.youtubetimer;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ChangePinActivity extends Activity {

    private enum Step {
        ENTER_CURRENT,
        ENTER_NEW,
        CONFIRM_NEW
    }

    private TimerPreferences prefs;
    private Step currentStep = Step.ENTER_CURRENT;
    private String newPinDraft = "";
    private StringBuilder enteredDigits = new StringBuilder();

    private TextView subtitleText;
    private TextView errorText;
    private ImageView[] dots = new ImageView[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_pin);

        prefs = new TimerPreferences(this);

        subtitleText = findViewById(R.id.pin_subtitle);
        errorText = findViewById(R.id.pin_error);

        dots[0] = findViewById(R.id.dot_0);
        dots[1] = findViewById(R.id.dot_1);
        dots[2] = findViewById(R.id.dot_2);
        dots[3] = findViewById(R.id.dot_3);

        setupKeypad();
        updateStepUi();
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
            btnCancel.setOnClickListener(v -> finish());
        }

        Button key5 = findViewById(R.id.key_5);
        if (key5 != null) {
            key5.requestFocus();
        }
    }

    private void appendDigit(String digit) {
        if (enteredDigits.length() < 4) {
            enteredDigits.append(digit);
            updateDots();
            if (enteredDigits.length() == 4) {
                processCompletedCode();
            }
        }
    }

    private void deleteDigit() {
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

    private void updateStepUi() {
        enteredDigits.setLength(0);
        updateDots();
        errorText.setVisibility(View.INVISIBLE);

        switch (currentStep) {
            case ENTER_CURRENT:
                subtitleText.setText("Enter current 4-digit PIN");
                break;
            case ENTER_NEW:
                subtitleText.setText("Enter new 4-digit PIN");
                break;
            case CONFIRM_NEW:
                subtitleText.setText("Confirm new 4-digit PIN");
                break;
        }
    }

    private void processCompletedCode() {
        String code = enteredDigits.toString();

        if (currentStep == Step.ENTER_CURRENT) {
            if (code.equals(prefs.getPin())) {
                currentStep = Step.ENTER_NEW;
                updateStepUi();
            } else {
                errorText.setText("Incorrect current PIN");
                errorText.setVisibility(View.VISIBLE);
                enteredDigits.setLength(0);
                updateDots();
            }
        } else if (currentStep == Step.ENTER_NEW) {
            newPinDraft = code;
            currentStep = Step.CONFIRM_NEW;
            updateStepUi();
        } else if (currentStep == Step.CONFIRM_NEW) {
            if (code.equals(newPinDraft)) {
                prefs.setPin(newPinDraft);
                Toast.makeText(this, "PIN successfully updated", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                errorText.setText("PINs do not match. Try again.");
                errorText.setVisibility(View.VISIBLE);
                currentStep = Step.ENTER_NEW;
                enteredDigits.setLength(0);
                updateDots();
                subtitleText.setText("Enter new 4-digit PIN");
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
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}


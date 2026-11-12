package io.github.abner97.remotebuttons;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The card's screen: whether the add-on works on this TV, which button opens what, and the place to change it.
 *
 * <p>Assigning works like a keyboard shortcut on a PC: pick what to open, then press the remote button that should
 * open it. The press is caught by {@link KeyLogService} and handed here through {@link ButtonCapture}, so it also
 * works for buttons the firmware swallows before any app sees them. Opened by the launcher's module card
 * (DOCK_CARD) and by the app's own tile.
 */
public class RemoteButtonsActivity extends Activity implements ButtonCapture.Listener {
    /** Logcat tag, shared with {@link KeyLogService}. */
    private static final String TAG = "RemoteButtons";
    /** How long the "press a button" dialog waits before concluding the TV reports no presses. */
    private static final long CAPTURE_TIMEOUT_MS = 15_000;
    /**
     * How soon after an assignment another screen must come up for the button to count as one the TV already
     * uses. Measured on a Hisense: its favourite (heart) button opened the firmware's screen about 300 ms after
     * the press; 1.5 s leaves room for a slower set without catching the owner's own next press.
     */
    private static final long FIRMWARE_REACTION_MS = 1_500;
    /** Large enough to read from the sofa. */
    private static final float TEXT_SIZE_SP = 22;
    /** The screen's title, a step above the body text. */
    private static final float TITLE_SIZE_SP = 32;
    /** Extra space between lines, so the status stays readable at a distance. */
    private static final float LINE_SPACING_MULTIPLIER = 1.3f;
    /** Margin around the content, inside the TV's overscan-safe area. */
    private static final int PADDING_DP = 48;
    /** Space above each line of text. */
    private static final int TEXT_TOP_PADDING_DP = 12;
    /** Space below each line of text. */
    private static final int TEXT_BOTTOM_PADDING_DP = 8;
    /** Rounded corners of the buttons. */
    private static final int BUTTON_CORNER_DP = 12;
    /** Inner space to the left and right of a button's text. */
    private static final int BUTTON_HORIZONTAL_PADDING_DP = 24;
    /** Inner space above and below a button's text. */
    private static final int BUTTON_VERTICAL_PADDING_DP = 10;
    /** Gap between buttons, so the focused one can grow without touching its neighbours. */
    private static final int BUTTON_GAP_DP = 8;
    /** How much the focused button grows, so focus is visible from across the room. */
    private static final float FOCUSED_BUTTON_SCALE = 1.06f;
    /** How long the grow and shrink take. */
    private static final long FOCUS_ANIMATION_MS = 120;
    /** An unfocused button: dim grey, with white text. */
    private static final int BUTTON_COLOR = Color.rgb(0x3A, 0x3A, 0x40);
    /** The focused button: white. */
    private static final int FOCUSED_BUTTON_COLOR = Color.WHITE;
    /** The focused button's text: near-black on the white. */
    private static final int FOCUSED_BUTTON_TEXT_COLOR = Color.rgb(0x12, 0x12, 0x14);

    /** Ends a capture nobody answered; see {@link #onCaptureTimedOut()}. */
    private final Handler captureTimeoutHandler = new Handler(Looper.getMainLooper());
    /** The timeout itself, kept so {@link #stopCapture()} can cancel it. */
    private final Runnable captureTimeout = this::onCaptureTimedOut;

    /** The column of status lines and buttons, rebuilt by {@link #render()}. */
    private LinearLayout content;
    /** The "press a button" dialog while it is showing, otherwise null. */
    private AlertDialog captureDialog;
    /** The choice being assigned while a capture is running, otherwise null. */
    private InstalledTargets.Target targetBeingAssigned;
    /** The assignment just saved, kept for {@link #FIRMWARE_REACTION_MS} in case it must be undone; else null. */
    private RecentAssignment recentAssignment;
    /** A message to show when the screen comes back, after the TV's own screen interrupted it; else null. */
    private String messageOnReturn;

    /** What is needed to undo an assignment: the button, the map before it, and when it was saved. */
    private static final class RecentAssignment {
        /** The button that was assigned. */
        final int keyCode;
        /** The complete map before the assignment. */
        final Map<Integer, String> mapBefore;
        /** Whether keys.txt existed before; if not, undoing deletes it again instead of writing the old map. */
        final boolean hadKeysFile;
        /** When it was saved, in {@link SystemClock#uptimeMillis()}. */
        final long savedAtUptimeMs;

        /**
         * Records an assignment about to be saved.
         *
         * @param keyCode the button being assigned
         * @param mapBefore the complete map before the assignment
         * @param hadKeysFile whether keys.txt existed before
         */
        RecentAssignment(int keyCode, Map<Integer, String> mapBefore, boolean hadKeysFile) {
            this.keyCode = keyCode;
            this.mapBefore = mapBefore;
            this.hadKeysFile = hadKeysFile;
            this.savedAtUptimeMs = SystemClock.uptimeMillis();
        }

        /**
         * Whether another screen coming up now means the TV reacted to this same press.
         *
         * @return true while the press is less than {@link #FIRMWARE_REACTION_MS} old
         */
        boolean isFresh() {
            return SystemClock.uptimeMillis() - savedAtUptimeMs < FIRMWARE_REACTION_MS;
        }
    }

    /** Created by Android when the card or the app's tile is opened; the screen is built in {@link #onCreate}. */
    public RemoteButtonsActivity() {
        super();
    }

    /** Builds the empty scrolling column; {@link #onResume()} fills it. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int paddingPx = dpToPx(PADDING_DP);
        content.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.BLACK);
        scrollView.addView(content);
        setContentView(scrollView);
    }

    /**
     * Refreshes on every return, since the owner may have just turned the service on in Settings, and explains an
     * assignment that the TV's own screen interrupted.
     */
    @Override
    protected void onResume() {
        super.onResume();
        render();
        if (messageOnReturn != null) {
            showMessage(messageOnReturn);
            messageOnReturn = null;
        }
    }

    /**
     * A capture never outlives the screen: the next press must open its app as usual. Leaving the screen during a
     * capture, or right after one, means the TV opened its own screen for that button, so it already has a job and
     * is not assigned (or the assignment is undone): it would otherwise open two things at once.
     */
    @Override
    protected void onPause() {
        boolean wasWaitingForButton = targetBeingAssigned != null;
        stopCapture();
        dismissCaptureDialog();
        if (wasWaitingForButton) {
            messageOnReturn = getString(R.string.taken_while_waiting);
        } else if (recentAssignment != null && recentAssignment.isFresh()) {
            undo(recentAssignment);
            messageOnReturn = getString(R.string.taken_by_tv, recentAssignment.keyCode);
        }
        recentAssignment = null;
        super.onPause();
    }

    /**
     * Saves the button the owner just pressed for the choice being assigned.
     *
     * @param keyCode the keycode of the press, already checked by {@link KeyMap#isAssignable}
     */
    @Override
    public void onButtonCaptured(int keyCode) {
        InstalledTargets.Target target = targetBeingAssigned;
        stopCapture();
        dismissCaptureDialog();
        if (target == null) return; // the capture was cancelled while this press was on its way
        KeysFile keysFile = new KeysFile(this);
        RecentAssignment assignment = new RecentAssignment(keyCode, keysFile.targetsByKeyCode(), keysFile.exists());
        try {
            keysFile.assign(keyCode, target.value);
            recentAssignment = assignment;
            Toast.makeText(this, getString(R.string.assigned, keyCode, target.label), Toast.LENGTH_LONG).show();
        } catch (IOException saveError) {
            showMessage(getString(R.string.save_failed, saveError.getMessage()));
        }
        render();
    }

    /**
     * Puts the button map back as it was before an assignment.
     *
     * @param assignment the assignment to take back
     */
    private void undo(RecentAssignment assignment) {
        KeysFile keysFile = new KeysFile(this);
        try {
            if (assignment.hadKeysFile) {
                keysFile.replaceWith(assignment.mapBefore);
            } else {
                keysFile.resetToDefaults();
            }
        } catch (IOException undoError) {
            Log.w(TAG, "could not undo the assignment of button " + assignment.keyCode, undoError);
        }
    }

    /** Fills the screen: title, status, the assign button, one row per button, and reset when there is a keys.txt. */
    private void render() {
        content.removeAllViews();
        addText(getString(R.string.app_name), TITLE_SIZE_SP);
        addText(getString(isServiceOn() ? R.string.status_on : R.string.status_off)
                + "\n" + getString(hasLogAccess() ? R.string.logs_ok : R.string.logs_missing), TEXT_SIZE_SP);

        Button assignButton = addButton(getString(R.string.assign_button), this::chooseTarget);

        KeysFile keysFile = new KeysFile(this);
        addText(getString(keysFile.exists() ? R.string.map_file : R.string.map_default), TEXT_SIZE_SP);
        for (Map.Entry<Integer, String> button : keysFile.targetsByKeyCode().entrySet()) {
            int keyCode = button.getKey();
            String targetName = InstalledTargets.describe(this, button.getValue());
            addButton(getString(R.string.button_row, keyCode, targetName), () -> confirmRemove(keyCode, targetName));
        }
        if (keysFile.exists()) addButton(getString(R.string.reset_buttons), this::confirmReset);

        String lastButton = KeyLogService.lastHandledButton;
        if (!lastButton.isEmpty()) addText(getString(R.string.last, lastButton), TEXT_SIZE_SP);
        assignButton.requestFocus();
    }

    /** Step 1 of assigning: pick what the button should open. Refused, with the reason, when it could not work. */
    private void chooseTarget() {
        if (!isServiceOn() || !hasLogAccess()) {
            showMessage(getString(R.string.not_ready));
            return;
        }
        List<InstalledTargets.Target> targets = InstalledTargets.all(this);
        String[] targetNames = new String[targets.size()];
        for (int index = 0; index < targets.size(); index++) targetNames[index] = targets.get(index).label;
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_target)
                .setItems(targetNames, (dialog, chosenIndex) -> waitForButton(targets.get(chosenIndex)))
                .show();
    }

    /**
     * Step 2 of assigning: wait for the remote button. Back or Cancel stops waiting; so does the timeout.
     *
     * @param target the choice the next press will open
     */
    private void waitForButton(InstalledTargets.Target target) {
        targetBeingAssigned = target;
        captureDialog = new AlertDialog.Builder(this)
                .setTitle(target.label)
                .setMessage(getString(R.string.press_button, target.label))
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialog -> stopCapture())
                .show();
        ButtonCapture.start(this);
        captureTimeoutHandler.postDelayed(captureTimeout, CAPTURE_TIMEOUT_MS);
    }

    /** Nothing arrived in time: either no button was pressed, or this TV does not log presses at all. */
    private void onCaptureTimedOut() {
        stopCapture();
        dismissCaptureDialog();
        showMessage(getString(R.string.no_press_seen));
    }

    /** Stops waiting for a press and forgets the choice being assigned. Safe to call more than once. */
    private void stopCapture() {
        ButtonCapture.cancel(this);
        captureTimeoutHandler.removeCallbacks(captureTimeout);
        targetBeingAssigned = null;
    }

    /** Closes the "press a button" dialog if it is showing. */
    private void dismissCaptureDialog() {
        if (captureDialog != null && captureDialog.isShowing()) captureDialog.dismiss();
        captureDialog = null;
    }

    /**
     * Asks before making a button do nothing again.
     *
     * @param keyCode the button
     * @param targetName what it opens now, as shown in its row
     */
    private void confirmRemove(int keyCode, String targetName) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.remove_question, keyCode, targetName))
                .setPositiveButton(R.string.remove, (dialog, which) -> {
                    try {
                        new KeysFile(this).remove(keyCode);
                    } catch (IOException saveError) {
                        showMessage(getString(R.string.save_failed, saveError.getMessage()));
                    }
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Asks before going back to the built-in Hisense map. */
    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.reset_question)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    try {
                        new KeysFile(this).resetToDefaults();
                    } catch (IOException deleteError) {
                        showMessage(getString(R.string.save_failed, deleteError.getMessage()));
                    }
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Whether {@link KeyLogService} is switched on in the TV's accessibility settings.
     *
     * @return true when this app's service is in {@code enabled_accessibility_services}
     */
    private boolean isServiceOn() {
        String enabledServices =
                Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(getPackageName() + "/");
    }

    /**
     * Whether READ_LOGS has been granted. Without it logcat shows this app only its own lines, so no button
     * press is ever seen.
     *
     * @return true once {@code pm grant ... READ_LOGS} has run (install.sh does it)
     */
    private boolean hasLogAccess() {
        return checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Adds a line of white text to the column.
     *
     * @param text what to show
     * @param sizeSp the text size
     */
    private void addText(String text, float sizeSp) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        textView.setLineSpacing(0, LINE_SPACING_MULTIPLIER);
        textView.setPadding(0, dpToPx(TEXT_TOP_PADDING_DP), 0, dpToPx(TEXT_BOTTOM_PADDING_DP));
        content.addView(textView);
    }

    /**
     * Adds a focusable button to the column, reachable with the D-pad.
     *
     * @param label the button's text
     * @param onClick what OK on it does
     * @return the button, so the caller can give it focus
     */
    private Button addButton(String label, Runnable onClick) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
        button.setPadding(dpToPx(BUTTON_HORIZONTAL_PADDING_DP), dpToPx(BUTTON_VERTICAL_PADDING_DP),
                dpToPx(BUTTON_HORIZONTAL_PADDING_DP), dpToPx(BUTTON_VERTICAL_PADDING_DP));
        button.setPivotX(0); // grow to the right, so the column's left edge stays straight
        button.setOnClickListener(view -> onClick.run());
        button.setOnFocusChangeListener((view, hasFocus) -> styleButton(button, hasFocus));
        styleButton(button, false);

        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.setMargins(0, dpToPx(BUTTON_GAP_DP), 0, dpToPx(BUTTON_GAP_DP));
        content.addView(button, layout);
        return button;
    }

    /**
     * Gives a button its look for one focus state. The theme's own focus highlight is too faint on a TV, so the
     * focused button turns white and grows a little instead.
     *
     * @param button the button to style
     * @param focused whether it has the D-pad focus
     */
    private void styleButton(Button button, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dpToPx(BUTTON_CORNER_DP));
        background.setColor(focused ? FOCUSED_BUTTON_COLOR : BUTTON_COLOR);
        button.setBackground(background);
        button.setTextColor(focused ? FOCUSED_BUTTON_TEXT_COLOR : Color.WHITE);
        float scale = focused ? FOCUSED_BUTTON_SCALE : 1f;
        button.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIMATION_MS).start();
    }

    /**
     * Shows a message with an OK button.
     *
     * @param message what to tell the owner
     */
    private void showMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton(android.R.string.ok, null).show();
    }

    /**
     * Converts density-independent pixels to pixels on this screen.
     *
     * @param dp the size in dp
     * @return the size in pixels
     */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}

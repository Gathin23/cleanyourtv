package io.github.callmejm.stayawake;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The card. Turn the watcher on or off. The choice is remembered. */
public class MainActivity extends Activity {
    private TextView state;
    private TextView on;
    private TextView off;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0E1A14);
        int pad = Ui.dp(this, 48);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, 34, Color.WHITE);
        title.setText(R.string.app_name);
        root.addView(title);
        TextView hint = Ui.text(this, 16, 0xB3FFFFFF);
        hint.setText(R.string.hint);
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        root.addView(hint);
        state = Ui.text(this, 18, 0xFF3DDC97);
        state.setPadding(0, 0, 0, Ui.dp(this, 16));
        root.addView(state);

        on = Ui.row(root, 280);
        on.setText(R.string.turn_on);
        on.setOnClickListener(v -> {
            Prefs.setEnabled(this, true);
            StayService.start(this);
            refresh();
        });
        off = Ui.row(root, 280);
        off.setText(R.string.turn_off);
        off.setOnClickListener(v -> {
            Prefs.setEnabled(this, false);
            StayService.stop(this);
            refresh();
        });
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        (Prefs.enabled(this) ? off : on).requestFocus();
    }

    private void refresh() {
        state.setText(Prefs.enabled(this) ? R.string.state_on : R.string.state_off);
    }
}

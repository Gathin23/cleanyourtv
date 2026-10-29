package io.github.luyzfernando08.sleeptimer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The last minute: a countdown box over whatever is playing. Nobody answering means sleep at zero. */
public class WarningActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView seconds;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long end = Timer.end(WarningActivity.this);
            if (end == 0) {                                    // cancelled or pushed back elsewhere
                finish();
                return;
            }
            long left = (end - System.currentTimeMillis() + 999) / 1000;
            if (left <= 0) {
                Timer.sleepNow(WarningActivity.this);
                finish();
                return;
            }
            seconds.setText(getString(R.string.warning_seconds, left));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(Ui.pill(this, 0xF2161826));
        int pad = Ui.dp(this, 36);
        box.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, 30, Color.WHITE);
        title.setText(R.string.warning_title);
        box.addView(title);
        seconds = Ui.text(this, 22, 0xFFB9B2FF);
        seconds.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        box.addView(seconds);
        TextView more = Ui.row(box, 420);
        more.setText(R.string.more);
        more.setOnClickListener(v -> {
            Timer.start(this, 15);
            finish();
        });
        TextView now = Ui.row(box, 420);
        now.setText(R.string.now);
        now.setOnClickListener(v -> {
            Timer.sleepNow(this);
            finish();
        });

        FrameLayout root = new FrameLayout(this);
        root.addView(box, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
        more.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        // Back or Home leaves the warning, not the timer: the sleep alarm still fires at the end.
        handler.removeCallbacks(tick);
        super.onPause();
    }
}

package io.github.luyzfernando08.sleeptimer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The card: pick when the TV goes to sleep, or cancel the timer. Choosing closes the screen. */
public class TimerActivity extends Activity {
    private TextView state, first, cancel, shizuku;
    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener permissionListener=(code,result)->refresh();
    private final rikka.shizuku.Shizuku.OnBinderReceivedListener binderListener=()->runOnUiThread(this::refresh);
    private final rikka.shizuku.Shizuku.OnBinderDeadListener deadListener=()->runOnUiThread(this::refresh);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(permissionListener);
        rikka.shizuku.Shizuku.addBinderReceivedListener(binderListener);
        rikka.shizuku.Shizuku.addBinderDeadListener(deadListener);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0B0D12);
        int pad = Ui.dp(this, 48);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, 34, Color.WHITE);
        title.setText(R.string.app_name);
        root.addView(title);
        TextView hint = Ui.text(this, 16, 0xB3FFFFFF);
        hint.setText(R.string.hint);
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        root.addView(hint);
        state = Ui.text(this, 18, 0xFFB9B2FF);
        state.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        root.addView(state);

        // The lengths side by side, so the whole screen fits a 1080p TV; Cancel underneath.
        LinearLayout lengths = new LinearLayout(this);
        lengths.setOrientation(LinearLayout.HORIZONTAL);
        lengths.setGravity(Gravity.CENTER_HORIZONTAL);
        android.widget.HorizontalScrollView choices=new android.widget.HorizontalScrollView(this);
        choices.setHorizontalScrollBarEnabled(false);
        choices.addView(lengths);
        root.addView(choices,new LinearLayout.LayoutParams(-1,-2));
        for (int minutes : Timer.MINUTES) {
            TextView row = Ui.row(lengths, 150);
            if (minutes != Timer.MINUTES[Timer.MINUTES.length - 1]) {
                ((LinearLayout.LayoutParams) row.getLayoutParams()).setMarginEnd(Ui.dp(this, 12));
            }
            row.setGravity(Gravity.CENTER);
            row.setText(getString(R.string.in_minutes, minutes));
            row.setOnClickListener(v -> {
                Timer.start(this, minutes);
                finish();
            });
            if (first == null) first = row;
        }
        cancel = Ui.row(root, 798);
        cancel.setText(R.string.cancel);
        cancel.setOnClickListener(v -> {
            Timer.cancel(this);
            refresh();
        });
        TextView schedule = Ui.row(root, 798);
        schedule.setText(R.string.schedule_row);
        schedule.setOnClickListener(v -> startActivity(new Intent(this, ScheduleActivity.class)));

        shizuku=Ui.row(root,798);
        shizuku.setOnClickListener(v->ShizukuPower.connect(this));
        for(TextView row:new TextView[]{cancel,schedule,shizuku})row.getLayoutParams().width=-1;
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root,new android.widget.ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);
    }

    /** Also when the screen comes back from the background, so it never shows a timer that has changed. */
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        (Timer.end(this) != 0 ? cancel : first).requestFocus();
    }

    private void refresh() {
        if(state==null||shizuku==null)return;
        shizuku.setText(ShizukuPower.ready()?R.string.shizuku_ready:ShizukuPower.running()?R.string.shizuku_allow:R.string.shizuku_start);
        long end = Timer.end(this);
        if (!Timer.canSleep()) state.setText(R.string.no_service);
        else if (end == 0) state.setText(R.string.state_off);
        else state.setText(getString(R.string.state_on, DateFormat.getTimeFormat(this).format(end)));
        String bed = Schedule.describe(this);
        if (!bed.isEmpty()) setTitle(getString(R.string.app_name) + " · " + bed);
    }
    @Override protected void onDestroy() {
        rikka.shizuku.Shizuku.removeRequestPermissionResultListener(permissionListener);
        rikka.shizuku.Shizuku.removeBinderReceivedListener(binderListener);
        rikka.shizuku.Shizuku.removeBinderDeadListener(deadListener);
        super.onDestroy();
    }
}

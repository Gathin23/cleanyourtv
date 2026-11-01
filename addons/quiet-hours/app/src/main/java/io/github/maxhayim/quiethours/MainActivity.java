package io.github.maxhayim.quiethours;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The tile and module card open this: one list worked with the remote. The first row switches Quiet Hours off,
 * on a schedule, or always; Left and Right change values. No text fields and nothing to type.
 */
public class MainActivity extends Activity {
    private Quiet.Settings s;
    private AudioManager audio;
    private TextView status;
    private Row modeRow, capRow, startRow, endRow, headphonesRow;
    /** The volume line follows the remote's volume keys while this screen is open. */
    private final BroadcastReceiver volume = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audio = getSystemService(AudioManager.class);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(px(48), px(32), px(48), px(24));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xFF8B949E);
        status.setPadding(0, 0, 0, px(16));
        page.addView(title);
        page.addView(status);

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = new TextView(this);
        hint.setText("◀ ▶ change    OK switch    Back closes, settings stay");
        hint.setTextSize(13);
        hint.setTextColor(0xFF8B949E);
        hint.setPadding(px(16), px(12), 0, 0);
        page.addView(hint);
        setContentView(page);

        s = Prefs.load(this);
        modeRow = add(rows, new Row(this, "Quiet hours", null, false, () -> {
            s.mode = Quiet.Mode.values()[(s.mode.ordinal() + 1) % 3];
            changed();
        }, d -> {
            s.mode = Quiet.Mode.values()[(s.mode.ordinal() + d + 3) % 3];
            changed();
        }));
        capRow = add(rows, new Row(this, "Volume cap", "The loudest the TV may play during quiet hours.", true, null,
                d -> {
                    s.cap += d * Quiet.CAP_STEP;
                    changed();
                }));
        startRow = add(rows, new Row(this, "Starts", "◀ ▶ moves 15 minutes.", false, null, d -> {
            s.start += d * Quiet.TIME_STEP;
            changed();
        }));
        endRow = add(rows, new Row(this, "Ends", null, false, null, d -> {
            s.end += d * Quiet.TIME_STEP;
            changed();
        }));
        headphonesRow = add(rows, new Row(this, "Headphones", "Headphones don't wake the room, so they can go louder.",
                false, () -> {
                    s.headphonesFree = !s.headphonesFree;
                    changed();
                }, null));
        modeRow.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        s = Prefs.load(this);
        registerReceiver(volume, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        refresh();
        CapService.start(this);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(volume);
        super.onPause();
    }

    private Row add(LinearLayout rows, Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    /** Saves, which the running service sees at once; starts it when Quiet Hours is on. */
    private void changed() {
        Prefs.save(this, s);
        refresh();
        CapService.start(this);
    }

    private void refresh() {
        boolean h24 = DateFormat.is24HourFormat(this);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int cap = Quiet.capIndex(s.cap, max);
        modeRow.setValue(s.mode == Quiet.Mode.OFF ? "Off" : s.mode == Quiet.Mode.ALWAYS ? "Always" : "Scheduled");
        modeRow.setDetail(s.mode == Quiet.Mode.SCHEDULED ? "Every day between the times below."
                : s.mode == Quiet.Mode.ALWAYS ? "The cap applies all day." : "No cap.");
        capRow.setValue(s.cap + " %");
        capRow.setDetail("The loudest the TV may play during quiet hours: step " + cap + " of " + max + ".");
        capRow.setBar(s.cap / 100f);
        startRow.setValue(Quiet.clock(s.start, h24));
        endRow.setValue(Quiet.clock(s.end, h24));
        boolean scheduled = s.mode == Quiet.Mode.SCHEDULED;
        startRow.setAlpha(scheduled ? 1f : 0.5f);
        endRow.setAlpha(scheduled ? 1f : 0.5f);
        headphonesRow.setValue(s.headphonesFree ? "No cap" : "Capped too");

        String now = "Volume " + vol + " of " + max + ". ";
        if (audio.isVolumeFixed()) {
            status.setText("This TV's volume is fixed (HDMI ARC or an amplifier), so Android can't cap it.");
        } else if (s.mode == Quiet.Mode.OFF) {
            status.setText(now + "Quiet hours are off.");
        } else if (Quiet.capNow(s, CapService.minuteNow())) {
            String until = scheduled ? " until " + Quiet.clock(s.end, h24) : "";
            String free = CapService.onHeadphones(audio) && s.headphonesFree ? " Headphones: no cap." : "";
            status.setText(now + "Capped at " + cap + until + "." + free);
        } else {
            status.setText(now + "Quiet hours from " + Quiet.clock(s.start, h24) + ".");
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

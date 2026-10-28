package io.github.maxhayim.sources;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The tile and module card open this: the TV's sources by the names the devices give themselves over HDMI-CEC
 * ("PlayStation 5", not "HDMI 2"), connected ones first in brightness, and OK switches to one. Then Live TV.
 * It reads the TV's own input list; nothing is stored and nothing runs when it is closed.
 */
public class MainActivity extends Activity {
    private LinearLayout rows;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        rows.removeAllViews();
        TvInputManager tim = getSystemService(TvInputManager.class);
        List<Sources.Input> inputs = new ArrayList<>();
        TvInputInfo tuner = null;
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    CharSequence custom = info.loadCustomLabel(this);
                    int state = tim.getInputState(info.getId());
                    inputs.add(new Sources.Input(info.getId(), info.getParentId(), String.valueOf(info.loadLabel(this)),
                            custom == null ? "" : custom.toString(), info.getType() == TvInputInfo.TYPE_HDMI,
                            state != TvInputManager.INPUT_STATE_DISCONNECTED,
                            state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY));
                } else if (info.getType() == TvInputInfo.TYPE_TUNER && tuner == null) {
                    tuner = info;
                }
            }
        }
        List<Sources.Row> list = Sources.rows(inputs, new String[] {getString(R.string.state_nothing),
                getString(R.string.state_standby), getString(R.string.state_connected)});
        Row first = null;
        int connected = 0;
        for (Sources.Row r : list) {
            Row row = add(new Row(this, r.name, r.detail, () -> open(new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(r.id)))));
            row.setAlpha(r.connected ? 1f : 0.55f);
            if (r.connected) connected++;
            if (first == null) first = row;
        }
        if (tuner != null) {
            final TvInputInfo live = tuner;
            Row row = add(new Row(this, getString(R.string.live_tv), getString(R.string.live_tv_detail), () -> open(
                    new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(live.getId())))));
            if (first == null) first = row;
        }
        if (first == null) {
            add(new Row(this, getString(R.string.no_sources), getString(R.string.no_sources_detail), null));
            status.setText("");
        } else {
            status.setText(getResources().getQuantityString(R.plurals.devices_connected, connected, connected));
            first.requestFocus();
        }
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            status.setText(R.string.cant_open);
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

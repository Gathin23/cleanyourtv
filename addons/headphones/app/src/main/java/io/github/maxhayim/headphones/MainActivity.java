package io.github.maxhayim.headphones;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tile and module card open this: where the sound goes now, the quiet guard, the paired headphones with their
 * state and battery, and what the TV can do for several listeners. Connecting and pairing happen in the TV's own
 * Bluetooth settings, which apps can't do for it; OK on a pair opens them.
 */
public class MainActivity extends Activity {
    private static final int ASK_BLUETOOTH = 1;

    private SharedPreferences prefs;
    private AudioManager audio;
    private BluetoothAdapter adapter;
    private BluetoothA2dp a2dp;
    private LinearLayout rows;
    private TextView status;
    /** Redraws when headphones connect or drop while the screen is open. */
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            build();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(QuietReceiver.PREFS, MODE_PRIVATE);
        audio = getSystemService(AudioManager.class);
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        adapter = bm == null ? null : bm.getAdapter();

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
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(changes, f);
        if (allowed() && adapter != null && a2dp == null) {
            adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    a2dp = (BluetoothA2dp) proxy;
                    build();
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    a2dp = null;
                }
            }, BluetoothProfile.A2DP);
        }
        build();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(changes);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (a2dp != null && adapter != null) adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == ASK_BLUETOOTH) recreate();
    }

    /** Rebuilds the list; focus stays on the row it was on. */
    private void build() {
        int focused = rows.indexOfChild(rows.getFocusedChild());
        rows.removeAllViews();
        status.setText(getString(R.string.plays_through, output()));
        Row first;

        if (!allowed()) {
            first = add(new Row(this, getString(R.string.allow_title),
                    getString(R.string.allow_detail), this::askBluetooth));
            first.setValue(getString(R.string.not_allowed));
        } else {
            first = null;
        }

        Row pair = add(new Row(this, getString(R.string.pair_title),
                getString(R.string.pair_detail), this::openBluetooth));
        if (first == null) first = pair;

        boolean fixed = audio.isVolumeFixed();
        Row guard = add(new Row(this, getString(R.string.guard_title),
                getString(fixed ? R.string.guard_detail_fixed : R.string.guard_detail),
                () -> {
                    prefs.edit().putBoolean(QuietReceiver.GUARD, !prefs.getBoolean(QuietReceiver.GUARD, true)).apply();
                    build();
                }));
        guard.setValue(getString(prefs.getBoolean(QuietReceiver.GUARD, true) ? R.string.on : R.string.off));
        if (prefs.getBoolean(QuietReceiver.MUTED_BY_US, false)) {
            Row unmute = add(new Row(this, getString(R.string.unmute_title), getString(R.string.unmute_detail), () -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                prefs.edit().putBoolean(QuietReceiver.MUTED_BY_US, false).apply();
                build();
            }));
            unmute.setValue(getString(R.string.muted));
        }

        heading(getString(R.string.heading_paired));
        if (adapter == null) {
            add(new Row(this, getString(R.string.no_bluetooth), null, null));
        } else if (!adapter.isEnabled()) {
            add(new Row(this, getString(R.string.bluetooth_off), getString(R.string.bluetooth_off_detail), this::openBluetooth));
        } else if (allowed()) {
            List<BluetoothDevice> paired = pairedAudio();
            if (paired.isEmpty()) add(new Row(this, getString(R.string.none_yet), getString(R.string.none_yet_detail), null));
            Set<String> connected = connected();
            for (BluetoothDevice d : paired) {
                boolean on = connected.contains(d.getAddress());
                String battery = on ? Quiet.battery(batteryLevel(d)) : "";
                Row r = add(new Row(this, name(d),
                        on ? (battery.isEmpty() ? getString(R.string.connected) : getString(R.string.connected_battery, battery))
                                : getString(R.string.not_connected_detail),
                        this::openBluetooth));
                r.setValue(on ? getString(R.string.connected) : "");
            }
        }

        heading(getString(R.string.heading_several));
        int[] sharing = {R.string.sharing_one_pair, R.string.sharing_auracast, R.string.sharing_no_broadcast};
        add(new Row(this, getString(sharing[Quiet.sharingCase(Build.VERSION.SDK_INT, broadcastSupport())]), null, null));

        View keep = focused >= 0 && focused < rows.getChildCount() ? rows.getChildAt(focused) : null;
        (keep != null && keep.isFocusable() ? keep : first).requestFocus();
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void heading(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(13);
        h.setTextColor(0xFF7C9CFF);
        h.setPadding(px(16), px(20), 0, px(4));
        rows.addView(h);
    }

    private boolean allowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void askBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, ASK_BLUETOOTH);
    }

    private void openBluetooth() {
        for (String action : new String[]{Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Try the next; TV firmware differs in which settings screens it has.
            }
        }
    }

    /** The output Android routes media to now, by the name the headphones give themselves. */
    private String output() {
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            boolean bt = t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || (Build.VERSION.SDK_INT >= 31 && (t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_SPEAKER));
            if (bt) return Quiet.clean(String.valueOf(d.getProductName()), getString(R.string.unnamed));
        }
        return getString(audio.isVolumeFixed() ? R.string.output_fixed : R.string.output_speakers);
    }

    private List<BluetoothDevice> pairedAudio() {
        List<BluetoothDevice> out = new ArrayList<>();
        try {
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                BluetoothClass c = d.getBluetoothClass();
                if (c != null && Quiet.isAudio(c.getMajorDeviceClass())) out.add(d);
            }
            Collections.sort(out, (a, b) -> name(a).compareToIgnoreCase(name(b)));
        } catch (SecurityException ignored) {
            // Access taken back while open: the permission row shows next time.
        }
        return out;
    }

    private Set<String> connected() {
        Set<String> out = new HashSet<>();
        if (a2dp == null) return out;
        try {
            for (BluetoothDevice d : a2dp.getConnectedDevices()) out.add(d.getAddress());
        } catch (SecurityException ignored) {
            // As above.
        }
        return out;
    }

    private String name(BluetoothDevice d) {
        try {
            return Quiet.clean(d.getName(), getString(R.string.unnamed));
        } catch (SecurityException e) {
            return getString(R.string.unnamed);
        }
    }

    /** Not public API: many TVs report it, some refuse the call. -1 hides it. */
    private static int batteryLevel(BluetoothDevice d) {
        try {
            Object v = BluetoothDevice.class.getMethod("getBatteryLevel").invoke(d);
            return v instanceof Integer ? (Integer) v : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private int broadcastSupport() {
        if (Build.VERSION.SDK_INT < 33 || adapter == null) return -1;
        try {
            return adapter.isLeAudioBroadcastSourceSupported();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

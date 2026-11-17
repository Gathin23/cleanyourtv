package io.github.kunalsharma.tvremote;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * TV Remote: the phone becomes a Bluetooth keyboard and media-key remote for the TV.
 *
 * <p>Flow: make the phone visible, add it as an accessory on the TV (Settings, Remote and accessories, Add
 * accessory), then tap the TV in "Paired devices" to connect. The pad sends arrow keys and Enter; Home, Back,
 * volume and play/pause go as consumer keys. Holding a key down holds it on the TV, which repeats it itself.
 *
 * <p>Needs Android 9 (API 28), the first version with the Bluetooth HID Device profile, and Bluetooth
 * permission on Android 12 and later. Nothing is sent anywhere but to the TV you connect; there is no
 * internet permission.
 */
@SuppressLint("MissingPermission") // Every Bluetooth call runs only after hasBluetoothPermission().
public class MainActivity extends android.app.Activity {
    private static final int REQUEST_BLUETOOTH = 1;
    private static final int DISCOVERABLE_SECONDS = 120;

    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private boolean registered;

    private TextView status;
    private LinearLayout deviceList;
    private EditText typeBox;

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean isRegistered) {
            registered = isRegistered;
            updateStatus();
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device;
            } else if (state == BluetoothProfile.STATE_DISCONNECTED && device.equals(host)) {
                host = null;
            }
            updateStatus();
        }
    };

    private final BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile != BluetoothProfile.HID_DEVICE) return;
            hid = (BluetoothHidDevice) proxy;
            BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                    getString(R.string.app_name), getString(R.string.sdp_description), "TV Remote",
                    BluetoothHidDevice.SUBCLASS1_COMBO, HidKeys.DESCRIPTOR);
            if (!hid.registerApp(sdp, null, null, getMainExecutor(), callback)) {
                Toast.makeText(MainActivity.this, R.string.toast_register_failed, Toast.LENGTH_LONG).show();
            }
            updateStatus();
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile != BluetoothProfile.HID_DEVICE) return;
            hid = null;
            host = null;
            registered = false;
            updateStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            status.setText(R.string.status_no_bluetooth);
            return;
        }
        if (hasBluetoothPermission()) {
            start();
        } else {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BLUETOOTH) return;
        if (hasBluetoothPermission()) {
            start();
        } else {
            status.setText(R.string.status_need_permission);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && hasBluetoothPermission()) refreshDevices();
    }

    @Override
    protected void onDestroy() {
        if (hid != null) {
            hid.unregisterApp();
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
            hid = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------------------------------

    private boolean hasBluetoothPermission() {
        // Before Android 12 the Bluetooth permissions are granted at install.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void start() {
        if (!adapter.isEnabled()) {
            status.setText(R.string.status_bluetooth_off);
            return;
        }
        if (!adapter.getProfileProxy(this, serviceListener, BluetoothProfile.HID_DEVICE)) {
            status.setText(R.string.status_no_hid);
        }
        refreshDevices();
    }

    private void updateStatus() {
        if (host != null) {
            status.setText(getString(R.string.status_connected, nameOf(host)));
        } else if (registered) {
            status.setText(R.string.status_ready);
        } else {
            status.setText(R.string.status_starting);
        }
        refreshDevices();
    }

    private String nameOf(BluetoothDevice device) {
        String name = device.getName();
        return name == null || name.isEmpty() ? device.getAddress() : name;
    }

    private void refreshDevices() {
        if (adapter == null || !hasBluetoothPermission() || deviceList == null) return;
        deviceList.removeAllViews();
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        List<BluetoothDevice> devices = new ArrayList<>(bonded == null ? Collections.<BluetoothDevice>emptySet() : bonded);
        Collections.sort(devices, new Comparator<BluetoothDevice>() {
            @Override
            public int compare(BluetoothDevice a, BluetoothDevice b) {
                return nameOf(a).compareToIgnoreCase(nameOf(b));
            }
        });
        if (devices.isEmpty()) {
            deviceList.addView(text(getString(R.string.no_paired), 16, 0xFF9AA7B4));
            return;
        }
        for (final BluetoothDevice device : devices) {
            boolean connected = device.equals(host);
            Button button = button((connected ? getString(R.string.disconnect_from, nameOf(device))
                    : getString(R.string.connect_to, nameOf(device))));
            button.setOnClickListener(v -> toggleConnection(device));
            deviceList.addView(button, matchWrap(0, 6));
        }
    }

    private void toggleConnection(BluetoothDevice device) {
        if (hid == null || !registered) {
            Toast.makeText(this, R.string.status_starting, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = device.equals(host) ? hid.disconnect(device) : hid.connect(device);
        if (!ok) Toast.makeText(this, R.string.toast_connect_failed, Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------------------------------------------------

    private boolean sendKeyboard(int modifiers, int key) {
        if (hid == null || host == null) return false;
        return hid.sendReport(host, HidKeys.REPORT_KEYBOARD, HidKeys.keyboardReport(modifiers, key));
    }

    private boolean sendConsumer(int usage) {
        if (hid == null || host == null) return false;
        return hid.sendReport(host, HidKeys.REPORT_CONSUMER, HidKeys.consumerReport(usage));
    }

    private void sendText() {
        CharSequence typed = typeBox.getText();
        if (typed.length() == 0) return;
        if (host == null) {
            Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean skipped = false;
        for (int i = 0; i < typed.length(); i++) {
            int[] key = HidKeys.forChar(typed.charAt(i));
            if (key == null) {
                skipped = true;
                continue;
            }
            sendKeyboard(key[0], key[1]);
            sendKeyboard(0, 0);
        }
        typeBox.setText("");
        if (skipped) Toast.makeText(this, R.string.toast_skipped, Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------------------------------------------------

    private void buildScreen() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(24), dp(20), dp(24));

        column.addView(text(getString(R.string.app_name), 28, 0xFFFFFFFF));
        status = text(getString(R.string.status_starting), 16, 0xFF4ADE80);
        column.addView(status, matchWrap(4, 12));

        Button visible = button(getString(R.string.make_visible));
        visible.setOnClickListener(v -> makeVisible());
        column.addView(visible, matchWrap(0, 8));

        column.addView(text(getString(R.string.paired_devices), 16, 0xFF9AA7B4), matchWrap(8, 4));
        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        column.addView(deviceList, matchWrap(0, 12));

        // Pad: up / left OK right / down.
        column.addView(row(spacer(), key("▲", HidKeys.KEY_UP), spacer()), matchWrap(0, 6));
        column.addView(row(key("◀", HidKeys.KEY_LEFT), key("OK", HidKeys.KEY_ENTER), key("▶", HidKeys.KEY_RIGHT)),
                matchWrap(0, 6));
        column.addView(row(spacer(), key("▼", HidKeys.KEY_DOWN), spacer()), matchWrap(0, 12));

        column.addView(row(consumer(getString(R.string.back), HidKeys.CONSUMER_BACK),
                consumer(getString(R.string.home), HidKeys.CONSUMER_HOME),
                consumer("⏯", HidKeys.CONSUMER_PLAY_PAUSE)), matchWrap(0, 6));
        column.addView(row(consumer("Vol −", HidKeys.CONSUMER_VOLUME_DOWN),
                consumer(getString(R.string.mute), HidKeys.CONSUMER_MUTE),
                consumer("Vol +", HidKeys.CONSUMER_VOLUME_UP)), matchWrap(0, 16));

        typeBox = new EditText(this);
        typeBox.setHint(R.string.type_hint);
        typeBox.setHintTextColor(0xFF6B7885);
        typeBox.setTextColor(0xFFFFFFFF);
        typeBox.setSingleLine(true);
        column.addView(typeBox, matchWrap(0, 4));
        LinearLayout typeButtons = row(button(getString(R.string.send_text)), key("⌫", HidKeys.KEY_BACKSPACE));
        ((Button) typeButtons.getChildAt(0)).setOnClickListener(v -> sendText());
        column.addView(typeButtons, matchWrap(0, 16));

        column.addView(text(getString(R.string.help_steps), 14, 0xFF9AA7B4));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);
        scroll.addView(column);
        setContentView(scroll);
    }

    private void makeVisible() {
        if (adapter == null) return;
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }
    }

    /** A keyboard key: pressed while the finger is down, released when it lifts. */
    private Button key(String label, final int usage) {
        Button button = button(label);
        button.setOnTouchListener(holdListener(new Runnable() {
            @Override
            public void run() {
                reportPress(sendKeyboard(0, usage));
            }
        }, new Runnable() {
            @Override
            public void run() {
                sendKeyboard(0, 0);
            }
        }));
        return button;
    }

    /** A consumer-control key (Home, Back, volume, play/pause). */
    private Button consumer(String label, final int usage) {
        Button button = button(label);
        button.setOnTouchListener(holdListener(new Runnable() {
            @Override
            public void run() {
                reportPress(sendConsumer(usage));
            }
        }, new Runnable() {
            @Override
            public void run() {
                sendConsumer(0);
            }
        }));
        return button;
    }

    private void reportPress(boolean sent) {
        if (!sent) Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show();
    }

    private View.OnTouchListener holdListener(final Runnable down, final Runnable up) {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        down.run();
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        up.run();
                        v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        up.run();
                        return true;
                    default:
                        return true;
                }
            }
        };
    }

    // ---------------------------------------------------------------------------------------------------------

    private LinearLayout row(View... views) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (View view : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(view, lp);
        }
        return row;
    }

    private View spacer() {
        return new View(this);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        button.setMinHeight(dp(56));
        return button;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}

package io.github.maxhayim.headphones;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

/**
 * The quiet guard: when headphones drop it pauses what plays and mutes the speakers, so the sound doesn't jump to
 * the room; when they come back it lifts its own mute. Android wakes it for these broadcasts; nothing runs between.
 */
public class QuietReceiver extends BroadcastReceiver {
    static final String PREFS = "headphones";
    static final String GUARD = "guard";
    static final String MUTED_BY_US = "muted_by_us";
    static final String TAG = "Headphones";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean disconnected = BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);
        if (!disconnected && !BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) return;
        BluetoothDevice device = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                : intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        boolean audio;
        try {
            BluetoothClass c = device == null ? null : device.getBluetoothClass();
            audio = c != null && Quiet.isAudio(c.getMajorDeviceClass());
        } catch (SecurityException e) {
            return; // Bluetooth access was taken back; nothing to judge by.
        }
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AudioManager am = context.getSystemService(AudioManager.class);
        if (disconnected) {
            Quiet.Action a = Quiet.onDisconnect(p.getBoolean(GUARD, true), audio, am.isVolumeFixed());
            if (a == Quiet.Action.NOTHING) return;
            pause(am);
            if (a == Quiet.Action.PAUSE_AND_MUTE) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                p.edit().putBoolean(MUTED_BY_US, true).apply();
            }
            Log.i(TAG, "headphones dropped: " + a);
        } else if (Quiet.unmuteOnConnect(audio, p.getBoolean(MUTED_BY_US, false))) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            p.edit().putBoolean(MUTED_BY_US, false).apply();
            Log.i(TAG, "headphones back: unmuted");
        }
    }

    /** A pause key to whatever media session is playing, as the remote's own pause button would send. */
    private static void pause(AudioManager am) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
    }
}

package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Holds a binding to Google's Cast receiver, so Android's cached-app freezer cannot stop it.
 *
 * <p>With the stock home disabled, nothing binds {@code com.google.android.apps.mediashell}'s
 * {@code .CastReceiverService}. The receiver then falls to the cached state and the freezer stops it, and phones lose
 * the TV until a reboot. A binding keeps the receiver's process at least as important as this launcher's, which is
 * never cached. No call is made on the binder, and a TV without the receiver, or with it disabled, is left alone.
 * {@code keep_cast_receiver} in config.xml turns it on. Everything here runs on the main thread.
 */
final class CastKeeper implements ServiceConnection {
    /** The binding that holds, or null. It keeps the application context only. */
    @SuppressLint("StaticFieldLeak")
    private static CastKeeper held;

    private final Context context;

    private CastKeeper(Context context) {
        this.context = context;
    }

    /** Binds the receiver unless keep_cast_receiver is off or a binding holds; Home calls it each time it shows. */
    static void ensure(Context context) {
        if (held != null || !context.getResources().getBoolean(R.bool.keep_cast_receiver)) return;
        CastKeeper keeper = new CastKeeper(context.getApplicationContext());
        boolean bound = false;
        try {
            bound = keeper.context.bindService(new Intent("com.google.cast.action.BIND")
                    .setPackage("com.google.android.apps.mediashell"), keeper, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ignored) {
            // Released below as for a false answer, since the connection can be registered before the denial.
        }
        if (bound) held = keeper;
        else keeper.release();
    }

    private void release() {
        try {
            context.unbindService(this);
        } catch (IllegalArgumentException ignored) {
            // Never registered.
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // The receiver died, and BIND_AUTO_CREATE brings it back with this binding.
    }

    @Override
    public void onBindingDied(ComponentName name) {
        // The receiver was updated or disabled and this connection never comes back. Let it go, and the next
        // time Home comes up, ensure() binds again.
        held = null;
        release();
    }
}

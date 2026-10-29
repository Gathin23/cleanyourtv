package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** An optional native panel for the remote's Settings key; false keeps the caller's usual settings route. */
final class SettingsKey {
    private SettingsKey() { }

    interface Starter {
        boolean service(String component, String action);
        void activity(String action);
    }

    static boolean open(Context context, String service, String action) {
        if (service.isEmpty() && action.isEmpty()) return false;
        return open(service, action, new Starter() {
            @Override public boolean service(String component, String action) {
                ComponentName target = ComponentName.unflattenFromString(component);
                if (target == null) return false;
                Intent intent = new Intent().setComponent(target);
                if (!action.isEmpty()) intent.setAction(action);
                return context.startService(intent) != null;
            }

            @Override public void activity(String action) {
                context.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
    }

    static boolean open(String service, String action, Starter starter) {
        try {
            if (!service.isEmpty()) {
                int slash = service.indexOf('/');
                if (slash <= 0 || slash == service.length() - 1 || service.indexOf('/', slash + 1) >= 0) return false;
                return starter.service(service, action);
            }
            if (action.isEmpty()) return false;
            starter.activity(action);
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException | IllegalStateException unavailable) {
            return false;
        }
    }

    static boolean released(int action, int keyCode, int flags, boolean nativePanel) {
        // Some firmware re-injects a short MENU release with flags 30001, including CANCELED.
        // Accept only that observed signature, and only for an explicitly configured native panel.
        return action == KeyEvent.ACTION_UP && ((flags & KeyEvent.FLAG_CANCELED) == 0
                || (nativePanel && keyCode == KeyEvent.KEYCODE_MENU && flags == 30001));
    }
}

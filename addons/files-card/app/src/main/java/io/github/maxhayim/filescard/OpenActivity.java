package io.github.maxhayim.filescard;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/** The Files card: opens the TV's file manager (Amaze when it is installed) and gets out of the way. */
public final class OpenActivity extends Activity {
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        PackageManager pm = getPackageManager();
        Map<String, Intent> openable = new HashMap<>();
        for (String pkg : FileManagers.KNOWN) {
            Intent open = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (open == null) open = pm.getLaunchIntentForPackage(pkg);
            if (open != null) openable.put(pkg, open);
        }
        String pkg = FileManagers.pick(openable.keySet());
        if (pkg != null) {
            startActivity(openable.get(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(24);
        t.setPadding(96, 96, 96, 96);
        t.setText(R.string.missing);
        setContentView(t);
    }
}

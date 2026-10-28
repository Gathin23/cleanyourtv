package no.cleanyourtv.nrkradioshortcut;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** TV launcher entry point for the phone/tablet NRK Radio app. */
public final class MainActivity extends Activity {
    private static final String TARGET_PACKAGE = "no.nrk.mobil.radio";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, "NRK Radio is not installed", Toast.LENGTH_LONG).show();
        } else {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launch);
        }
        finish();
    }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

public final class KillBackgroundActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        RamBooster.clean(this, true);
        finish();
    }
}

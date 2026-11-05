package io.github.anhost77.salon;

import android.app.Application;
import android.content.Context;

/** Makes {@link Settings} readable everywhere, providers included (they start before Application.onCreate). */
public final class App extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Settings.init(base);
    }
}

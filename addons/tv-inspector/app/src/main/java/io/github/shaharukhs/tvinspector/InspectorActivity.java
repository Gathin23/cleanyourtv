package io.github.shaharukhs.tvinspector;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

/** The card's screen: a choice between the key tester and the TV info sheet. */
public final class InspectorActivity extends Screen {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        layout();
        text(getString(R.string.app_name), 30);
        text(getString(R.string.intro), 20);
        Button keys = button(getString(R.string.keys), () -> startActivity(new Intent(this, KeysActivity.class)));
        button(getString(R.string.info), () -> startActivity(new Intent(this, InfoActivity.class)));
        button(getString(R.string.back), this::finish);
        keys.requestFocus();
    }
}

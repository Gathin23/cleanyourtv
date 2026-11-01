package io.github.maxhayim.startupbranding;

import android.app.Activity;
import android.os.Bundle;

/**
 * What works on this TV and what doesn't: from what this module does without root, to what only a system app or
 * the maker's firmware can change. Rows take focus only so the list scrolls with the D-pad.
 */
public class CompatActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Page page = new Page(this, "Compatibility", false);
        page.subtitle.setText(DeviceInfo.summary(this));
        Compat.Status last = null;
        for (Compat.Item item : Compat.items(DeviceInfo.facts(this))) {
            if (item.status != last) page.heading(item.status.heading);
            last = item.status;
            page.add(item.name, item.detail, () -> { }).setValue(item.status.label);
        }
        page.focus(0);
    }
}

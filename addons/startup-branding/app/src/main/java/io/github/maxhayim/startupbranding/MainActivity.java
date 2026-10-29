package io.github.maxhayim.startupbranding;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

/**
 * The tile and the card open this: every setting of the welcome screen, one row each. The picture, the
 * compatibility list and the reset have their own screens.
 */
public class MainActivity extends Activity {
    private Page page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        page = new Page(this, getString(R.string.app_name), false);
        page.subtitle.setText("Your own welcome screen. It never changes the maker's boot logo; see Compatibility.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(page.focused());
    }

    private void build(int focus) {
        page.rows.removeAllViews();
        Look l = Prefs.load(this);

        page.add("Welcome Screen", "Custom: your picture, colour and text. Black: a plain black screen. Off: nothing.",
                () -> change(x -> x.mode = Look.next(Look.Mode.values(), x.mode)))
                .setValue(cap(l.mode.name()));
        page.add("Preview", "Full screen, as it will look. Back or OK closes it.",
                () -> startActivity(new Intent(this, WelcomeActivity.class).putExtra(WelcomeActivity.EXTRA_PREVIEW, true)));

        page.heading("Look");
        page.add("Custom Image", "A picture from this TV's file picker or from a computer, scaled and placed.",
                () -> startActivity(new Intent(this, ImageActivity.class)))
                .setValue(l.image.isEmpty() ? "None" : l.image);
        page.add("Welcome Text", "A line of your own, up to " + Look.MAX_TEXT + " characters.",
                () -> page.ask("Welcome text", l.text, Look.MAX_TEXT, s -> change(x -> x.text = Look.clean(s, Look.MAX_TEXT))))
                .setValue(l.text.isEmpty() ? "None" : l.text);
        page.add("Text Position", "Where the text sits on the screen.",
                () -> change(x -> x.place = Look.next(Look.Place.values(), x.place)))
                .setValue(cap(l.place.name()));
        page.add("Background Color", "Behind the picture, or the whole screen without one.",
                () -> change(x -> x.color = (x.color + 1) % Look.COLORS.length))
                .setValue(Look.COLOR_NAMES[l.color]);
        page.add("Display Duration", "How long it stays up, unless Back or OK closes it sooner.",
                () -> change(x -> x.seconds = Look.nextDuration(x.seconds)))
                .setValue(Look.durationName(l.seconds));

        page.heading("Names");
        String system = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        page.add("Device Name", "The TV's real name, as phones and casting see it. OK opens the TV's own setting.",
                this::openDeviceName)
                .setValue(system == null ? "Unknown" : system);
        page.add("Nickname", "Shown under the welcome text, in this module only. It doesn't rename the TV.",
                () -> page.ask("Nickname (this module only)", l.nickname, Look.MAX_NICKNAME,
                        s -> change(x -> x.nickname = Look.clean(s, Look.MAX_NICKNAME))))
                .setValue(l.nickname.isEmpty() ? "None" : l.nickname);

        page.heading("More");
        page.add("Compatibility", "What works on this TV without root, and what doesn't.",
                () -> startActivity(new Intent(this, CompatActivity.class)));
        page.add("Restore Defaults", "Turn the welcome screen off, or clear every custom setting.",
                () -> startActivity(new Intent(this, ResetActivity.class)));
        page.focus(focus);
    }

    private interface Change {
        void apply(Look l);
    }

    private void change(Change c) {
        Look l = Prefs.load(this);
        c.apply(l);
        Prefs.save(this, l);
        build(page.focused());
    }

    private void openDeviceName() {
        for (Intent i : DeviceInfo.nameSettings()) {
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // The next, more general screen.
            }
        }
        page.subtitle.setText("This TV has no settings screen for its name.");
    }

    static String cap(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}

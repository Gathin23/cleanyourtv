package com.gathin.photodream;

import android.content.Context;
import android.content.SharedPreferences;

/** The switches in SettingsActivity. All off by default: then it is the plain full-screen slideshow. */
final class Options {
    static final String CLOCK = "clock";
    static final String DATES = "photo_dates";
    static final String SKY = "sky";
    static final String PRINTS = "prints";
    static final String WEATHER = "weather";
    static final String ANIMATION = "animation";
    static final String MINUTES_PER_PICTURE = "minutes_per_picture";
    static final String IDLE_MINUTES = "idle_minutes";

    static final int DEFAULT_MINUTES_PER_PICTURE = 1;
    static final int DEFAULT_IDLE_MINUTES = 10;

    final boolean clock;
    final boolean dates;
    final boolean sky;
    final boolean prints;
    final boolean weather;
    final boolean animation;
    final int minutesPerPicture;
    final int idleMinutes;

    private Options(SharedPreferences p) {
        clock = p.getBoolean(CLOCK, false);
        dates = p.getBoolean(DATES, false);
        sky = p.getBoolean(SKY, false);
        prints = p.getBoolean(PRINTS, false);
        weather = p.getBoolean(WEATHER, false);
        animation = p.getBoolean(ANIMATION, true);
        minutesPerPicture = p.getInt(MINUTES_PER_PICTURE, DEFAULT_MINUTES_PER_PICTURE);
        idleMinutes = p.getInt(IDLE_MINUTES, DEFAULT_IDLE_MINUTES);
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE);
    }

    static Options load(Context context) {
        return new Options(preferences(context));
    }
}

package com.gathin.photodream;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** The switches in SettingsActivity. All off by default: then it is the plain full-screen slideshow. */
final class Options {
    static final String CLOCK = "clock";
    static final String CLOCK_FADES = "clock_fades";
    static final String DATES = "photo_dates";
    static final String SKY = "sky";
    static final String PRINTS = "prints";
    static final String WEATHER = "weather";
    /** The town typed for the weather, as the geocoding named it, and where it is. */
    static final String PLACE = "weather_place";
    static final String LATITUDE = "weather_latitude";
    static final String LONGITUDE = "weather_longitude";
    static final String ANIMATION = "animation";
    static final String SECONDS_PER_PICTURE = "seconds_per_picture";
    static final String IDLE_MINUTES = "idle_minutes";

    static final int DEFAULT_IDLE_MINUTES = 10;

    final boolean clock;
    final boolean clockFades;
    final boolean dates;
    final boolean sky;
    final boolean prints;
    final boolean weather;
    /** "" when no town is set: then the weather uses the TV's own location. */
    final String place;
    final double latitude;
    final double longitude;
    final boolean animation;
    final int secondsPerPicture;
    final int idleMinutes;

    Options(SharedPreferences p, File dir) {
        clock = p.getBoolean(CLOCK, false);
        clockFades = p.getBoolean(CLOCK_FADES, false);
        dates = p.getBoolean(DATES, false);
        sky = p.getBoolean(SKY, false);
        prints = p.getBoolean(PRINTS, false);
        weather = p.getBoolean(WEATHER, false);
        place = p.getString(PLACE, "");
        latitude = p.getFloat(LATITUDE, 0f);
        longitude = p.getFloat(LONGITUDE, 0f);
        animation = p.getBoolean(ANIMATION, true);
        secondsPerPicture = p.getInt(SECONDS_PER_PICTURE,
                Media.seconds(Photos.read(dir == null ? null : new File(dir, Media.SECONDS))));
        idleMinutes = p.getInt(IDLE_MINUTES, DEFAULT_IDLE_MINUTES);
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE);
    }

    static Options load(Context context) {
        return new Options(preferences(context), context.getExternalFilesDir(null));
    }

    /** A town is typed for the weather, so the TV's own location is not needed. */
    boolean hasPlace() {
        return !place.isEmpty();
    }
}

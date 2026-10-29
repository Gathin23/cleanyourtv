package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/** Plain JVM tests for wallpaper_moods: the time of day's group and the pictures it picks from. */
public class WallpaperMoodTest {
    private static final int RISE = 7 * 60 + 20, SET = 18 * 60 + 20;

    private static String at(int hour, int minute) {
        return WallpaperMood.timeOfDay(hour * 60 + minute, RISE, SET);
    }

    @Test
    public void timeOfDayFollowsSunriseNoonAndDusk() {
        assertEquals(WallpaperMood.NIGHT, at(0, 30));
        assertEquals(WallpaperMood.NIGHT, at(7, 19));
        assertEquals(WallpaperMood.MORNING, at(7, 20));
        assertEquals(WallpaperMood.MORNING, at(11, 59));
        assertEquals(WallpaperMood.EVENING, at(12, 0));
        assertEquals(WallpaperMood.EVENING, at(18, 49));   // half an hour after sunset is still evening
        assertEquals(WallpaperMood.NIGHT, at(18, 50));
        assertEquals(WallpaperMood.NIGHT, at(23, 59));
    }

    @Test
    public void sunComesFromTheConfiguredPlaceOrTheDefaults() {
        LocalDate day = LocalDate.of(2026, 10, 22);
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        assertArrayEquals(Sun.riseAndSet(day, 37.35, -121.98, zone), WallpaperMood.sun(day, "37.35", " -121.98 ", zone));
        int[] defaults = {WallpaperMood.DEFAULT_SUNRISE, WallpaperMood.DEFAULT_SUNSET};
        assertArrayEquals(defaults, WallpaperMood.sun(day, "", "", zone));
        assertArrayEquals(defaults, WallpaperMood.sun(day, "37.35", "", zone));
        assertArrayEquals(defaults, WallpaperMood.sun(day, "north", "-121.98", zone));
        // the sun does not set at the pole in June: the defaults
        assertArrayEquals(defaults, WallpaperMood.sun(LocalDate.of(2026, 6, 21), "89.9", "0", ZoneId.of("UTC")));
    }

    private static List<File> files(String... names) {
        List<File> out = new ArrayList<>();
        for (String name : names) out.add(new File("/walls", name));
        return out;
    }

    private static List<String> names(List<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName());
        return out;
    }

    @Test
    public void selectKeepsTheCollectionsOrderAndFallsBackToAll() {
        List<File> all = files("a.jpg", "b.jpg", "c.jpg", "d.jpg");
        Map<String, Set<String>> groups = new HashMap<>();
        groups.put(WallpaperMood.NIGHT, new HashSet<>(Arrays.asList("d.jpg", "a.jpg")));
        groups.put(WallpaperMood.MORNING, new HashSet<>(Collections.singletonList("gone.jpg")));
        assertEquals(Arrays.asList("a.jpg", "d.jpg"), names(WallpaperMood.select(all, groups, WallpaperMood.NIGHT)));
        assertEquals(all, WallpaperMood.select(all, groups, WallpaperMood.MORNING));   // none of its pictures here
        assertEquals(all, WallpaperMood.select(all, groups, WallpaperMood.EVENING));   // no group at all
        assertEquals(all, WallpaperMood.select(all, Collections.emptyMap(), WallpaperMood.NIGHT));
    }
}

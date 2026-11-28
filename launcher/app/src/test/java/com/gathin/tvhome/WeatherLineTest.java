package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherLineTest {

    private static final WeatherLine.Words EN = new WeatherLine.Words(new String[]{
            "Clear", "Mainly clear", "Partly cloudy", "Overcast", "Fog", "Drizzle", "Rain", "Snow", "Showers",
            "Snow showers", "Thunderstorms"},
            "Rain likely around %1$s", "Snow likely around %1$s", "Storms likely around %1$s",
            "Up to %1$d° in the next hours", "Down to %1$d° in the next hours", "Steady for the next hours",
            "Low %1$d° tonight", "High %1$d° today");

    private static WeatherLine.Forecast at(int hour, double now, int code, double[] temps, int[] rain, int[] codes,
                                           double high) {
        return new WeatherLine.Forecast(now, code, hour, temps, rain, codes, high);
    }

    private static int[] fill(int n, int v) {
        int[] a = new int[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    @Test
    public void rainInTheNextHoursIsAnnouncedWithItsHour() {
        int[] rain = {10, 20, 30, 70, 80, 10, 0, 0};
        int[] codes = {2, 2, 3, 61, 63, 3, 3, 3};
        double[] temps = {22, 22, 21, 20, 19, 19, 18, 18};
        assertEquals("22° Partly cloudy  ·  Rain likely around 18:00",
                WeatherLine.sentence(at(15, 22.2, 2, temps, rain, codes, 24), EN));
    }

    @Test
    public void aStormHourSaysStorms() {
        int[] rain = {0, 60, 0, 0, 0, 0, 0};
        int[] codes = {0, 95, 0, 0, 0, 0, 0};
        double[] temps = {25, 25, 25, 25, 25, 25, 25};
        assertEquals("Storms likely around 14:00", WeatherLine.nextHours(at(13, 25, 0, temps, rain, codes, 27), EN));
    }

    @Test
    public void withoutRainTheTemperatureTrendShows() {
        double[] warming = {14, 16, 18, 20, 21, 21, 20};
        assertEquals("Up to 21° in the next hours",
                WeatherLine.nextHours(at(8, 14, 0, warming, fill(7, 0), fill(7, 0), 23), EN));
        double[] cooling = {20, 19, 17, 16, 15, 15, 15};
        assertEquals("Down to 15° in the next hours",
                WeatherLine.nextHours(at(19, 20, 1, cooling, fill(7, 5), fill(7, 1), 22), EN));
        double[] flat = {18, 18, 19, 19, 18, 18, 18};
        assertEquals("Steady for the next hours",
                WeatherLine.nextHours(at(10, 18, 3, flat, fill(7, 5), fill(7, 3), 19), EN));
    }

    @Test
    public void eveningGivesTheLowBeforeSeven() {
        double[] temps = new double[24];
        for (int i = 0; i < 24; i++) temps[i] = 20 - i * 0.5;   // 22:00 now, 07:00 is 9 hours on: 15.5
        assertEquals("Low 16° tonight", WeatherLine.laterToday(at(22, 20, 0, temps, fill(24, 0), fill(24, 0), 25), EN));
    }

    @Test
    public void aLowNoLowerThanNowGivesWayToTheDaysHigh() {
        // 03:00 in Sidi Daoud, 26 October 2026: 18.6 now, 18.5 at 06:00, 24.3 later.
        double[] temps = {18.6, 18.6, 18.6, 18.5, 18.8, 19.5, 20.9, 21.9, 23.0};
        assertEquals("High 24° today",
                WeatherLine.laterToday(at(3, 18.6, 1, temps, fill(9, 0), fill(9, 2), 24.3), EN));
        assertEquals("19° Mainly clear  ·  Steady for the next hours  ·  High 24° today",
                WeatherLine.sentence(at(3, 18.6, 1, temps, fill(9, 0), fill(9, 2), 24.3), EN));
        // Evening, a flat night and the high long past: nothing to add.
        assertEquals("", WeatherLine.laterToday(at(20, 18, 1, fill24(18), fill(24, 0), fill(24, 1), 24), EN));
    }

    private static double[] fill24(double v) {
        double[] a = new double[24];
        java.util.Arrays.fill(a, v);
        return a;
    }

    @Test
    public void morningGivesTheHighOnlyWhileItIsAhead() {
        double[] temps = fill(24, 0).length == 24 ? new double[24] : null;
        assertEquals("High 26° today", WeatherLine.laterToday(at(9, 19, 0, temps, fill(24, 0), fill(24, 0), 26.4), EN));
        assertEquals("", WeatherLine.laterToday(at(9, 25.6, 0, temps, fill(24, 0), fill(24, 0), 26.4), EN));
        assertEquals("", WeatherLine.laterToday(at(14, 19, 0, temps, fill(24, 0), fill(24, 0), 26.4), EN));
    }

    @Test
    public void anUnknownCodeLeavesTheWordOut() {
        assertEquals("7°", WeatherLine.sentence(at(14, 7.3, 42, new double[]{7}, new int[]{0}, new int[]{42}, 9), EN));
        assertEquals("", WeatherLine.sentence(at(14, Double.NaN, 0, new double[0], new int[0], new int[0], 9), EN));
    }

    @Test
    public void hourLabelWrapsAroundMidnight() {
        assertEquals("00:00", WeatherLine.hourLabel(24));
        assertEquals("03:00", WeatherLine.hourLabel(27));
    }

    @Test
    public void coordinatesMustBePlainNumbersInRange() {
        assertEquals("36.86389", WeatherLine.coordinate(" 36.86389 ", 90));
        assertEquals("-10.3", WeatherLine.coordinate("-10.3", 180));
        assertNull(WeatherLine.coordinate("91", 90));
        assertNull(WeatherLine.coordinate("36.8&hourly=x", 90));
        assertNull(WeatherLine.coordinate("", 90));
        assertNull(WeatherLine.coordinate(null, 90));
    }
}

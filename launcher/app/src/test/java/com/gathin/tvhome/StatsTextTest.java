package com.gathin.tvhome;
import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;
public class StatsTextTest {
    @Test public void preservesEnglishExactly() {
        String text = "CPU 8%  ·  Memory 1202 MB free  ·  Storage 0.8 GB free";
        assertEquals(text, StatsText.forLocale(text, Locale.ENGLISH));
    }
    @Test public void isolatesUnitsAndPercentAsWholeMeasurements() {
        String text = StatsText.forLocale("CPU 8%  ·  זיכרון 1202 MB פנוי  ·  אחסון 0.8 GB פנוי", new Locale("he"));
        assertTrue(text.contains("\u200E\u202A8%\u202C\u200E"));
        assertTrue(text.contains("\u200E\u202A1202 MB\u202C\u200E"));
        assertTrue(text.contains("\u200E\u202A0.8 GB\u202C\u200E"));
        assertEquals(3, text.chars().filter(c -> c == '\u202A').count());
    }
    @Test public void supportsAbsentCpuAndLargeValues() {
        String text = StatsText.forLocale("זיכרון 123456 MB פנוי  ·  אחסון 100 GB פנוי", new Locale("iw"));
        assertFalse(text.contains("CPU"));
        assertTrue(text.contains("\u200E\u202A123456 MB\u202C\u200E"));
        assertTrue(text.contains("\u200E\u202A100 GB\u202C\u200E"));
    }
    @Test public void keepsCapacityRatiosInOneDirectionRun() {
        String text = StatsText.forLocale("זיכרון 850/2850 MB פנוי  ·  אחסון 0.8/4.4 GB פנוי", new Locale("he"));
        assertTrue(text.contains("\u200E\u202A850/2850 MB\u202C\u200E"));
        assertTrue(text.contains("\u200E\u202A0.8/4.4 GB\u202C\u200E"));
    }
    @Test public void preservesUpstreamSpacedUsedTotalAsOneRun() {
        String text = StatsText.forLocale("זיכרון 1853 / 2915 MB", new Locale("he"));
        assertTrue(text.contains("\u200E\u202A1853 / 2915 MB\u202C\u200E"));
        assertEquals(1, text.chars().filter(c -> c == '\u202A').count());
    }
}

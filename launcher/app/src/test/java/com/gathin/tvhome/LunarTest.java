package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Known lunar dates (Vietnamese calendar, UTC+7 unless noted). Arrays are {day, month, year, leap}. */
public class LunarTest {

    @Test
    public void lunarNewYear() {
        assertArrayEquals(new int[]{1, 1, 2024, 0}, Lunar.fromSolar(10, 2, 2024, 7));
        assertArrayEquals(new int[]{1, 1, 2025, 0}, Lunar.fromSolar(29, 1, 2025, 7));
        assertArrayEquals(new int[]{1, 1, 2026, 0}, Lunar.fromSolar(17, 2, 2026, 7));
        assertArrayEquals(new int[]{1, 1, 2027, 0}, Lunar.fromSolar(6, 2, 2027, 7));
    }

    @Test
    public void dayBeforeNewYearBelongsToTheOldYear() {
        assertArrayEquals(new int[]{29, 12, 2025, 0}, Lunar.fromSolar(16, 2, 2026, 7));
    }

    @Test
    public void midAutumn() {
        assertArrayEquals(new int[]{15, 8, 2024, 0}, Lunar.fromSolar(17, 9, 2024, 7));
        assertArrayEquals(new int[]{15, 8, 2025, 0}, Lunar.fromSolar(6, 10, 2025, 7));
    }

    @Test
    public void leapSixthMonth2025() {
        assertArrayEquals(new int[]{1, 6, 2025, 1}, Lunar.fromSolar(25, 7, 2025, 7));
        assertArrayEquals(new int[]{1, 6, 2025, 0}, Lunar.fromSolar(25, 6, 2025, 7));
    }

    @Test
    public void lunarNewYear2023() {
        assertArrayEquals(new int[]{1, 1, 2023, 0}, Lunar.fromSolar(22, 1, 2023, 7));
        assertArrayEquals(new int[]{30, 12, 2022, 0}, Lunar.fromSolar(21, 1, 2023, 7));
    }

    @Test
    public void leapSecondMonth2023StartsOn22March() {
        // Month 2 runs from 20 February; the new moon of 21 March 17:23 UTC is 22 March in Vietnam.
        assertArrayEquals(new int[]{1, 2, 2023, 0}, Lunar.fromSolar(20, 2, 2023, 7));
        assertArrayEquals(new int[]{30, 2, 2023, 0}, Lunar.fromSolar(21, 3, 2023, 7));
        assertArrayEquals(new int[]{1, 2, 2023, 1}, Lunar.fromSolar(22, 3, 2023, 7));
        assertArrayEquals(new int[]{1, 3, 2023, 0}, Lunar.fromSolar(20, 4, 2023, 7));
    }

    @Test
    public void vietnamAndChinaDifferIn2007() {
        // The 2007 new year: 17 February in Vietnam, 18 February in China, where 17 February is still 30/12.
        assertArrayEquals(new int[]{1, 1, 2007, 0}, Lunar.fromSolar(17, 2, 2007, 7));
        assertArrayEquals(new int[]{30, 12, 2006, 0}, Lunar.fromSolar(17, 2, 2007, 8));
        assertArrayEquals(new int[]{1, 1, 2007, 0}, Lunar.fromSolar(18, 2, 2007, 8));
    }

    @Test
    public void yearNames() {
        assertEquals("Ất Tỵ", VI_STEMS[Lunar.stem(2025)] + " " + VI_BRANCHES[Lunar.branch(2025)]);
        assertEquals("Bính Ngọ", VI_STEMS[Lunar.stem(2026)] + " " + VI_BRANCHES[Lunar.branch(2026)]);
        assertEquals("Giáp Tý", VI_STEMS[Lunar.stem(1984)] + " " + VI_BRANCHES[Lunar.branch(1984)]);
    }

    @Test
    public void vietnameseText() {
        // The patterns of res/values-vi/lunar.xml.
        assertEquals("Âm lịch 13/9 · Bính Ngọ", vi(Lunar.fromSolar(22, 10, 2026, 7)));
        assertEquals("Âm lịch 29/12 · Ất Tỵ", vi(Lunar.fromSolar(16, 2, 2026, 7)));
        assertEquals("Âm lịch 1/1 · Bính Ngọ", vi(Lunar.fromSolar(17, 2, 2026, 7)));
        assertEquals("Âm lịch 1/2 nhuận · Quý Mão", vi(Lunar.fromSolar(22, 3, 2023, 7)));
        assertEquals("Âm lịch 1/6 nhuận · Ất Tỵ", vi(Lunar.fromSolar(25, 7, 2025, 7)));
    }

    private static final String[] VI_STEMS = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    private static final String[] VI_BRANCHES = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};

    private static String vi(int[] l) {
        return Lunar.format(l, "Âm lịch %1$d/%2$s · %3$s", "%1$d nhuận", "%1$s %2$s", VI_STEMS, VI_BRANCHES);
    }

    @Test
    public void vietnamAndChinaDifferIn1985() {
        // The 1985 new year: 21 January in Vietnam (UTC+7), 20 February in China (UTC+8).
        assertArrayEquals(new int[]{1, 1, 1985, 0}, Lunar.fromSolar(21, 1, 1985, 7));
        assertArrayEquals(new int[]{1, 1, 1985, 0}, Lunar.fromSolar(20, 2, 1985, 8));
    }
}

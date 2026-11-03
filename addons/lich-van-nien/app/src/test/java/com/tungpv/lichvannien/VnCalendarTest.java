package com.tungpv.lichvannien;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.GregorianCalendar;
import org.junit.Test;

/** Vietnamese calendar rules, UTC+7. */
public class VnCalendarTest {

    private static String canChi(int can, int chi) {
        return VnCalendar.CAN[can] + " " + VnCalendar.CHI[chi];
    }

    private static boolean[] hours(String... names) {
        boolean[] out = new boolean[12];
        for (String n : names) out[java.util.Arrays.asList(VnCalendar.CHI).indexOf(n)] = true;
        return out;
    }

    @Test
    public void today() {
        VnCalendar.Day d = VnCalendar.of(3, 11, 2026);
        assertEquals(25, d.lunarDay);
        assertEquals(9, d.lunarMonth);
        assertEquals(2026, d.lunarYear);
        assertFalse(d.leap);
        assertEquals("Tân Tỵ", canChi(d.dayCan, d.dayChi));
        assertEquals("Mậu Tuất", canChi(d.monthCan, d.monthChi));
        assertEquals("Bính Ngọ", canChi(d.yearCan, d.yearChi));
        assertEquals("Minh Đường", VnCalendar.STARS[d.star]);
        assertTrue(d.good);
        assertEquals(1, d.weekday);
        assertArrayEquals(hours("Sửu", "Thìn", "Ngọ", "Mùi", "Tuất", "Hợi"), VnCalendar.goodHours(d.dayChi));
    }

    @Test
    public void sixHourSets() {
        String[][] sets = {
                {"Tý", "Sửu", "Mão", "Ngọ", "Thân", "Dậu"},
                {"Dần", "Mão", "Tỵ", "Thân", "Tuất", "Hợi"},
                {"Tý", "Sửu", "Thìn", "Tỵ", "Mùi", "Tuất"},
                {"Tý", "Dần", "Mão", "Ngọ", "Mùi", "Dậu"},
                {"Dần", "Thìn", "Tỵ", "Thân", "Dậu", "Hợi"},
                {"Sửu", "Thìn", "Ngọ", "Mùi", "Tuất", "Hợi"},
        };
        for (int chi = 0; chi < 12; chi++) {
            assertArrayEquals("chi " + chi, hours(sets[chi % 6]), VnCalendar.goodHours(chi));
            assertEquals(chi % 6, VnCalendar.hourSet(chi));
        }
    }

    @Test
    public void dayCanChiAdvancesAndVariantInRange() {
        Calendar c = new GregorianCalendar(2025, Calendar.JANUARY, 1);
        VnCalendar.Day prev = null;
        while (c.get(Calendar.YEAR) < 2028) {
            VnCalendar.Day d = VnCalendar.of(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
            if (prev != null) {
                assertEquals((prev.dayCan + 1) % 10, d.dayCan);
                assertEquals((prev.dayChi + 1) % 12, d.dayChi);
                assertEquals((prev.weekday + 1) % 7, d.weekday);
            }
            int v = VnCalendar.variant(d);
            assertTrue(v >= 0 && v < 12);
            assertEquals(d.good, v < 6);
            int good = 0;
            for (boolean b : VnCalendar.goodHours(d.dayChi)) if (b) good++;
            assertEquals(6, good);
            prev = d;
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    @Test
    public void yearSwitchesAtTet() {
        VnCalendar.Day eve = VnCalendar.of(16, 2, 2026), tet = VnCalendar.of(17, 2, 2026);
        assertEquals("Ất Tỵ", canChi(eve.yearCan, eve.yearChi));
        assertEquals("Bính Ngọ", canChi(tet.yearCan, tet.yearChi));
        assertEquals(1, tet.lunarDay);
        assertEquals(1, tet.lunarMonth);
        assertEquals("Canh Dần", canChi(tet.monthCan, tet.monthChi));
    }

    @Test
    public void leapMonthHasTheBaseMonthsCanChi() {
        VnCalendar.Day leap = VnCalendar.of(25, 7, 2025), base = VnCalendar.of(25, 6, 2025);
        assertTrue(leap.leap);
        assertEquals(6, leap.lunarMonth);
        assertFalse(base.leap);
        assertEquals(6, base.lunarMonth);
        assertEquals("Quý Mùi", canChi(base.monthCan, base.monthChi));
        assertEquals("Quý Mùi", canChi(leap.monthCan, leap.monthChi));
    }

    @Test
    public void trucRepeatsOnTheDayATietBegins() {
        // Lập Đông (225°) 2026 falls on 7 November in Vietnam.
        assertTrue(VnCalendar.sunLongitudeDeg(VnCalendar.of(7, 11, 2026).jdn) < 225);
        assertTrue(VnCalendar.sunLongitudeDeg(VnCalendar.of(8, 11, 2026).jdn) >= 225);
        int found = 0;
        Calendar c = new GregorianCalendar(2026, Calendar.JANUARY, 2);
        VnCalendar.Day prev = VnCalendar.of(1, 1, 2026);
        while (c.get(Calendar.YEAR) == 2026) {
            VnCalendar.Day d = VnCalendar.of(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, 2026);
            if (d.truc == prev.truc) found++;
            else assertEquals((prev.truc + 1) % 12, d.truc);
            prev = d;
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        assertEquals(12, found);
    }

    /**
     * Published values, read 03/11/2026 from xemlicham.com (/am-lich/nam/Y/thang/M/ngay/D) and ngaydep.com
     * (/xem-ngay-D-thang-M-nam-Y.html); the two agree on every field here. The star is ngaydep's "Nhằm ngày …"
     * (xemlicham lists Ngọc Hạp stars, two on 03/11/2026 and 15/03/2027). Huyền Vũ is ngaydep's "Nguyên Vũ".
     * Columns: d, m, y, lunar day, lunar month, day can chi, month can chi, year can chi, star, trực, good hours.
     */
    private static final String[][] PUBLISHED = {
            {"29", "1", "2025", "1", "1", "Mậu Tuất", "Mậu Dần", "Ất Tỵ", "Tư Mệnh", "Thu", "Dần Thìn Tỵ Thân Dậu Hợi"},
            {"25", "7", "2025", "1", "6", "Ất Mùi", "Quý Mùi", "Ất Tỵ", "Huyền Vũ", "Kiến", "Dần Mão Tỵ Thân Tuất Hợi"},
            {"6", "10", "2025", "15", "8", "Mậu Thân", "Ất Dậu", "Ất Tỵ", "Bạch Hổ", "Bế", "Tý Sửu Thìn Tỵ Mùi Tuất"},
            {"17", "2", "2026", "1", "1", "Nhâm Tuất", "Canh Dần", "Bính Ngọ", "Tư Mệnh", "Thành", "Dần Thìn Tỵ Thân Dậu Hợi"},
            {"3", "11", "2026", "25", "9", "Tân Tỵ", "Mậu Tuất", "Bính Ngọ", "Minh Đường", "Nguy", "Sửu Thìn Ngọ Mùi Tuất Hợi"},
            {"6", "11", "2026", "28", "9", "Giáp Thân", "Mậu Tuất", "Bính Ngọ", "Kim Quỹ", "Khai", "Tý Sửu Thìn Tỵ Mùi Tuất"},
            // Lập Đông begins on 07/11/2026 (both sites' "Tiết khí"), so the trực repeats.
            {"7", "11", "2026", "29", "9", "Ất Dậu", "Mậu Tuất", "Bính Ngọ", "Kim Đường", "Khai", "Tý Dần Mão Ngọ Mùi Dậu"},
            {"8", "11", "2026", "30", "9", "Bính Tuất", "Mậu Tuất", "Bính Ngọ", "Bạch Hổ", "Bế", "Dần Thìn Tỵ Thân Dậu Hợi"},
            {"15", "3", "2027", "8", "2", "Quý Tỵ", "Quý Mão", "Đinh Mùi", "Chu Tước", "Mãn", "Sửu Thìn Ngọ Mùi Tuất Hợi"},
    };

    @Test
    public void matchesPublishedCalendars() {
        for (String[] p : PUBLISHED) {
            String date = p[0] + "/" + p[1] + "/" + p[2];
            VnCalendar.Day d = VnCalendar.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            assertEquals(date, Integer.parseInt(p[3]), d.lunarDay);
            assertEquals(date, Integer.parseInt(p[4]), d.lunarMonth);
            assertEquals(date, p[5], canChi(d.dayCan, d.dayChi));
            assertEquals(date, p[6], canChi(d.monthCan, d.monthChi));
            assertEquals(date, p[7], canChi(d.yearCan, d.yearChi));
            assertEquals(date, p[8], VnCalendar.STARS[d.star]);
            assertEquals(date, p[9], VnCalendar.TRUC[d.truc]);
            assertArrayEquals(date, hours(p[10].split(" ")), VnCalendar.goodHours(d.dayChi));
        }
    }

    @Test
    public void hourIndex() {
        assertEquals(0, VnCalendar.hourIndex(23));
        assertEquals(0, VnCalendar.hourIndex(0));
        assertEquals(1, VnCalendar.hourIndex(1));
        assertEquals(1, VnCalendar.hourIndex(2));
        assertEquals(7, VnCalendar.hourIndex(13));
        assertEquals(11, VnCalendar.hourIndex(22));
    }

    /**
     * Tiết that begin within half an hour after midnight in Vietnam (Lập Hạ 2027 ≈ 00:22 on 6 May, Lập Thu 2027
     * ≈ 00:25 on 8 August): ngaydep.com, read 03/11/2026, gives the tiết and the repeated trực on those days.
     */
    @Test
    public void tietJustAfterMidnight() {
        assertEquals("Định", VnCalendar.TRUC[VnCalendar.of(5, 5, 2027).truc]);
        assertEquals("Định", VnCalendar.TRUC[VnCalendar.of(6, 5, 2027).truc]);
        assertEquals("Bế", VnCalendar.TRUC[VnCalendar.of(7, 8, 2027).truc]);
        assertEquals("Bế", VnCalendar.TRUC[VnCalendar.of(8, 8, 2027).truc]);
        assertEquals("Kim Quỹ", VnCalendar.STARS[VnCalendar.of(5, 5, 2027).star]);
        assertEquals("Chu Tước", VnCalendar.STARS[VnCalendar.of(6, 5, 2027).star]);
        assertEquals("Bạch Hổ", VnCalendar.STARS[VnCalendar.of(7, 8, 2027).star]);
        assertEquals("Ngọc Đường", VnCalendar.STARS[VnCalendar.of(8, 8, 2027).star]);
        // Apparent longitude (with nutation and aberration, about 0.01° less than the geometric one) at 00:00 on
        // the tiết day: 22 minutes of the sun's motion short of 45°.
        assertEquals(44.9855, VnCalendar.sunLongitudeDeg(VnCalendar.of(6, 5, 2027).jdn), 0.0008);
    }

    @Test
    public void elevenPmIsTomorrowsTy() {
        // A Tý day: its own giờ Tý (00:00–01:00) is good (Kim Quỹ); from 23:00 it is the Sửu day's Tý, Thiên Hình.
        assertTrue(VnCalendar.goodAt(0, 0));
        assertFalse(VnCalendar.goodAt(0, 23));
        assertEquals(VnCalendar.goodHours(0)[6], VnCalendar.goodAt(0, 12));
    }

    @Test
    public void textsExist() {
        for (int t = 0; t < 12; t++) {
            assertTrue(VnCalendar.nen(t).length >= 2);
            assertTrue(VnCalendar.kieng(t).length >= 1);
        }
        assertEquals(12, VnCalendar.ANIMALS.length);
        for (int day = 1; day <= 30; day++) assertFalse(VnCalendar.moonWord(day).isEmpty());
    }
}

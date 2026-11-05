package com.tungpv.lichvannien;

/**
 * The Vietnamese calendar rules this add-on shows, for a civil date in UTC+7: lunar date, can chi of day, month and
 * year, the day's star (hoàng đạo or hắc đạo), the 12 double-hours and the day's trực. No Android imports, so the
 * whole of it is unit-tested on the JVM.
 */
final class VnCalendar {

    static final double TZ = 7;

    static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};
    /** The Vietnamese zodiac: Sửu is the buffalo, Mão the cat, Mùi the goat. Drawn with the TV's emoji font. */
    static final String[] ANIMALS = {"🐭", "🐃", "🐯", "🐱", "🐲", "🐍", "🐴", "🐐", "🐵", "🐔", "🐶", "🐷"};
    static final String[] STARS = {"Thanh Long", "Minh Đường", "Thiên Hình", "Chu Tước", "Kim Quỹ", "Kim Đường",
            "Bạch Hổ", "Ngọc Đường", "Thiên Lao", "Huyền Vũ", "Tư Mệnh", "Câu Trận"};
    /** Which of STARS are hoàng đạo. */
    static final boolean[] GOOD_STAR = {true, true, false, false, true, true, false, true, false, false, true, false};
    static final String[] TRUC = {"Kiến", "Trừ", "Mãn", "Bình", "Định", "Chấp", "Phá", "Nguy", "Thành", "Thu", "Khai", "Bế"};

    /** Chi where Thanh Long falls, by lunar month 1..12 (index month - 1); months six apart share it. */
    private static final int[] DAY_STAR_START = {0, 2, 4, 6, 8, 10, 0, 2, 4, 6, 8, 10};
    /** Chi of the hour where Thanh Long falls, by day chi; chi six apart share it. */
    private static final int[] HOUR_STAR_START = {8, 10, 0, 2, 4, 6};

    private static final String[][] NEN = {
            {"Xuất hành", "Khai trương", "Nhậm chức", "Cầu tài"},              // Kiến
            {"Chữa bệnh", "Dọn dẹp, tẩy uế", "Cúng giải hạn"},                 // Trừ
            {"Cầu tài", "Mở kho, nhập hàng", "Cúng tế", "Sửa nhà"},            // Mãn
            {"Sửa đường, sửa nhà", "Làm việc thường ngày"},                     // Bình
            {"Cưới hỏi", "Ký kết", "Nhập học", "Mua gia súc"},                 // Định
            {"Xây dựng", "Trồng cây", "Thuê người làm"},                       // Chấp
            {"Phá dỡ nhà cũ", "Chữa bệnh"},                                   // Phá
            {"Cúng tế, cầu an", "Việc nhỏ trong nhà"},                         // Nguy
            {"Cưới hỏi", "Khai trương", "Nhập trạch", "Xuất hành"},           // Thành
            {"Thu nợ", "Thu hoạch, nhập kho", "Mua bán"},                      // Thu
            {"Khai trương", "Cưới hỏi", "Động thổ", "Nhập học"},              // Khai
            {"Đắp đê, lấp hố", "Sửa tường rào", "Nghỉ ngơi"},                 // Bế
    };
    private static final String[][] KIENG = {
            {"Động thổ", "Đào giếng"},
            {"Cưới hỏi", "Xuất hành xa"},
            {"Kiện tụng", "Nhậm chức"},
            {"Cưới hỏi", "Khai trương"},
            {"Kiện tụng", "Xuất hành"},
            {"Xuất hành", "Mở kho, xuất tiền"},
            {"Cưới hỏi", "Khai trương", "Ký kết"},
            {"Đi sông nước", "Leo cao"},
            {"Kiện tụng"},
            {"An táng", "Xuất hành"},
            {"An táng", "Chôn cất"},
            {"Khai trương", "Chữa mắt", "Nhậm chức"},
    };

    private VnCalendar() {
    }

    /** One civil day in UTC+7. */
    static final class Day {
        final int solarDay, solarMonth, solarYear;
        final long jdn;
        final int lunarDay, lunarMonth, lunarYear;
        final boolean leap;
        final int dayCan, dayChi, monthCan, monthChi, yearCan, yearChi;
        /** Index into STARS. */
        final int star;
        final boolean good;
        /** Index into TRUC. */
        final int truc;
        /** 0 = Monday … 6 = Sunday. */
        final int weekday;

        private Day(int dd, int mm, int yy) {
            solarDay = dd;
            solarMonth = mm;
            solarYear = yy;
            jdn = Lunar.jdFromDate(dd, mm, yy);
            int[] l = Lunar.fromSolar(dd, mm, yy, TZ);
            lunarDay = l[0];
            lunarMonth = l[1];
            lunarYear = l[2];
            leap = l[3] == 1;
            dayCan = (int) ((jdn + 9) % 10);
            dayChi = (int) ((jdn + 1) % 12);
            monthChi = (lunarMonth + 1) % 12;
            monthCan = (lunarYear * 12 + lunarMonth + 3) % 10;
            yearCan = Math.floorMod(lunarYear + 6, 10);
            yearChi = Math.floorMod(lunarYear + 8, 12);
            star = Math.floorMod(dayChi - DAY_STAR_START[lunarMonth - 1], 12);
            good = GOOD_STAR[star];
            truc = Math.floorMod(dayChi - solarMonthChi(jdn), 12);
            weekday = (int) (jdn % 7);
        }
    }

    static Day of(int dd, int mm, int yyyy) {
        return new Day(dd, mm, yyyy);
    }

    /** The six good double-hours of a day with this chi; index 0 is giờ Tý (23:00–01:00). */
    static boolean[] goodHours(int dayChi) {
        boolean[] out = new boolean[12];
        int start = HOUR_STAR_START[dayChi % 6];
        for (int h = 0; h < 12; h++) out[h] = GOOD_STAR[Math.floorMod(h - start, 12)];
        return out;
    }

    /**
     * Whether the double-hour at this clock hour is good, on a day with this chi. Giờ Tý starts at 23:00, so from
     * 23:00 it is already the next day's Tý and takes that day's table.
     */
    static boolean goodAt(int dayChi, int hour24) {
        return hour24 == 23 ? goodHours((dayChi + 1) % 12)[0] : goodHours(dayChi)[hourIndex(hour24)];
    }

    static int hourSet(int dayChi) {
        return dayChi % 6;
    }

    /** 0..5 a good day with hour set 0..5, 6..11 a bad one: the alias and banner to show. */
    static int variant(Day d) {
        return (d.good ? 0 : 6) + hourSet(d.dayChi);
    }

    /** Double-hour of a clock hour: 23 and 0 are Tý (0), 1 and 2 Sửu (1), … 21 and 22 Hợi (11). */
    static int hourIndex(int hour24) {
        return ((hour24 + 1) / 2) % 12;
    }

    /**
     * The sun's apparent longitude in degrees at 00:00 UTC+7 of day number jdn: Meeus' low-precision geometric
     * longitude plus nutation and aberration, in terrestrial time (ΔT ≈ 69 s). Good to about a minute of time, which
     * puts a tiết beginning near midnight on the right day.
     */
    static double sunLongitudeDeg(long jdn) {
        double t = (jdn - 2451545.5 - TZ / 24 + 69 / 86400.0) / 36525, t2 = t * t, dr = Math.PI / 180;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double omega = 125.04 - 1934.136 * t;
        double l = (l0 + dl - 0.00569 - 0.00478 * Math.sin(dr * omega)) % 360;
        return l < 0 ? l + 360 : l;
    }

    /**
     * Chi of the solar (tiết) month the day belongs to: Dần from Lập Xuân (315°), Mão from Kinh Trập (345°), …
     * A tiết that begins during the day counts for that day, so the sun is read at the day's end.
     */
    static int solarMonthChi(long jdn) {
        double l = sunLongitudeDeg(jdn + 1);
        return (2 + (int) Math.floor(((l - 315 + 360) % 360) / 30)) % 12;
    }

    static String[] nen(int truc) {
        return NEN[truc];
    }

    static String[] kieng(int truc) {
        return KIENG[truc];
    }

    static String moonWord(int lunarDay) {
        if (lunarDay == 1) return "mùng một";
        if (lunarDay <= 7) return "trăng non";
        if (lunarDay == 8) return "thượng huyền";
        if (lunarDay <= 14) return "trăng gần tròn";
        if (lunarDay == 15) return "trăng rằm";
        if (lunarDay <= 22) return "trăng khuyết dần";
        if (lunarDay == 23) return "hạ huyền";
        return "trăng tàn";
    }
}

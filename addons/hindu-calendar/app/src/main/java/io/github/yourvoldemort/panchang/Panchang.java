package io.github.yourvoldemort.panchang;

import java.time.LocalDate;

/**
 * The five limbs a panchang gives for one day, read at sunrise as printed panchangs do, with the
 * moment each one ends.
 */
final class Panchang {
    static final String[] TITHI = {"Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami",
            "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi",
            "Trayodashi", "Chaturdashi"};
    static final String[] NAKSHATRA = {"Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira",
            "Ardra", "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni",
            "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
            "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha",
            "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"};
    static final String[] YOGA = {"Vishkambha", "Priti", "Ayushman", "Saubhagya", "Shobhana",
            "Atiganda", "Sukarma", "Dhriti", "Shula", "Ganda", "Vriddhi", "Dhruva", "Vyaghata",
            "Harshana", "Vajra", "Siddhi", "Vyatipata", "Variyana", "Parigha", "Shiva", "Siddha",
            "Sadhya", "Shubha", "Shukla", "Brahma", "Indra", "Vaidhriti"};
    /** Seven movable karanas, then the four fixed ones. */
    static final String[] KARANA = {"Bava", "Balava", "Kaulava", "Taitila", "Gara", "Vanija",
            "Vishti", "Kimstughna", "Shakuna", "Chatushpada", "Naga"};

    private static final double NAKSHATRA_ARC = 360 / 27.0;

    final long sunrise, sunset;
    final boolean hasSunrise;
    final int tithi, nakshatra, yoga, karana;
    final long tithiEnd, nakshatraEnd, yogaEnd, karanaEnd;

    private Panchang(long sunrise, long sunset, boolean hasSunrise, int tithi, long tithiEnd,
                     int nakshatra, long nakshatraEnd, int yoga, long yogaEnd,
                     int karana, long karanaEnd) {
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.hasSunrise = hasSunrise;
        this.tithi = tithi;
        this.tithiEnd = tithiEnd;
        this.nakshatra = nakshatra;
        this.nakshatraEnd = nakshatraEnd;
        this.yoga = yoga;
        this.yogaEnd = yogaEnd;
        this.karana = karana;
        this.karanaEnd = karanaEnd;
    }

    static Panchang at(LocalDate date, City city) {
        long[] riseSet = Astro.sunriseSunset(date, city);
        boolean hasSunrise = riseSet != null;
        long rise = hasSunrise ? riseSet[0]
                : date.atTime(12, 0).atZone(city.zone).toInstant().toEpochMilli();
        long set = hasSunrise ? riseSet[1] : 0;
        double jd = Astro.julianDay(rise);

        double elongation = Astro.elongation(jd);
        int tithiIndex = (int) Math.floor(elongation / 12);
        double tithiTarget = (tithiIndex + 1) * 12;

        double siderealMoon = Astro.siderealMoon(jd);
        int nakshatraIndex = (int) Math.floor(siderealMoon / NAKSHATRA_ARC);
        double nakshatraTarget = (nakshatraIndex + 1) * NAKSHATRA_ARC;

        double yogaAngle = Astro.norm(Astro.siderealSun(jd) + siderealMoon);
        int yogaIndex = (int) Math.floor(yogaAngle / NAKSHATRA_ARC);
        double yogaTarget = (yogaIndex + 1) * NAKSHATRA_ARC;

        int karanaIndex = (int) Math.floor(elongation / 6);
        double karanaTarget = (karanaIndex + 1) * 6;

        return new Panchang(rise, set, hasSunrise,
                tithiIndex + 1, Astro.millis(Astro.nextCrossing(jd, tithiTarget, Astro::elongation)),
                nakshatraIndex + 1, Astro.millis(Astro.nextCrossing(jd, nakshatraTarget, Astro::siderealMoon)),
                yogaIndex + 1, Astro.millis(Astro.nextCrossing(jd, yogaTarget, Panchang::yogaAngle)),
                karanaIndex, Astro.millis(Astro.nextCrossing(jd, karanaTarget, Astro::elongation)));
    }

    private static double yogaAngle(double jd) {
        return Astro.norm(Astro.siderealSun(jd) + Astro.siderealMoon(jd));
    }

    /** The tithi running at an instant, 1 (Shukla Pratipada) to 30 (Amavasya). */
    static int tithiAt(long millis) {
        return (int) Math.floor(Astro.elongation(Astro.julianDay(millis)) / 12) + 1;
    }

    static boolean krishna(int tithi) { return tithi > 15; }

    static int lunarDay(int tithi) { return tithi <= 15 ? tithi : tithi - 15; }

    static String tithiName(int tithi) {
        if (tithi == 15) return "Purnima";
        if (tithi == 30) return "Amavasya";
        return TITHI[(tithi <= 15 ? tithi : tithi - 15) - 1];
    }

    static String paksha(int tithi) { return krishna(tithi) ? "Krishna" : "Shukla"; }

    static String karanaName(int index) {
        if (index == 0) return KARANA[7];
        if (index <= 56) return KARANA[(index - 1) % 7];
        return KARANA[8 + (index - 57)];
    }
}

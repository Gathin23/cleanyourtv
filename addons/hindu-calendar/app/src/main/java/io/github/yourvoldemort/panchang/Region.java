package io.github.yourvoldemort.panchang;

import java.util.Locale;

/**
 * The regional calendars this add-on can show. Months, the New Year and even the era differ across
 * India on the same day, so the owner picks one; the astronomy behind them is the same.
 *
 * Amanta months run from new moon to new moon, Purnimanta from full moon to full moon. Tamil and
 * Bengali are solar: their months are the twelve sidereal signs, opened by a sankranti.
 */
enum Region {
    NORTH("North Indian", "Vikram Samvat", "विक्रम संवत्", false, true, '\u0966',
            new String[]{"चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद",
                    "आश्विन", "कार्तिक", "अग्रहायण", "पौष", "माघ", "फाल्गुन"},
            new String[]{"Chaitra", "Vaishakh", "Jyeshtha", "Ashadh", "Shravan", "Bhadrapad",
                    "Ashwin", "Kartik", "Agrahayan", "Paush", "Magh", "Phalgun"},
            new String[]{"शुक्ल", "कृष्ण"}, "अधिक "),

    GUJARATI("Gujarati", "Vikram Samvat", "વિક્રમ સંવત", false, false, '\u0AE6',
            new String[]{"ચૈત્ર", "વૈશાખ", "જેઠ", "અષાઢ", "શ્રાવણ", "ભાદરવો",
                    "આસો", "કારતક", "માગશર", "પોષ", "મહા", "ફાગણ"},
            new String[]{"Chaitra", "Vaishakh", "Jeth", "Ashadh", "Shravan", "Bhadarvo",
                    "Aso", "Kartak", "Magshar", "Posh", "Maha", "Fagan"},
            new String[]{"શુક્લ", "કૃષ્ણ"}, "અધિક "),

    MARATHI("Marathi", "Shaka Samvat", "शक संवत", false, false, '\u0966',
            new String[]{"चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ", "श्रावण", "भाद्रपद",
                    "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"},
            new String[]{"Chaitra", "Vaishakh", "Jyeshtha", "Ashadh", "Shravan", "Bhadrapad",
                    "Ashwin", "Kartik", "Margashirsha", "Paush", "Magh", "Phalgun"},
            new String[]{"शुक्ल", "कृष्ण"}, "अधिक "),

    TAMIL("Tamil", "Shaka Samvat", "சக ஆண்டு", true, false, '\0',
            new String[]{"சித்திரை", "வைகாசி", "ஆனி", "ஆடி", "ஆவணி", "புரட்டாசி",
                    "ஐப்பசி", "கார்த்திகை", "மார்கழி", "தை", "மாசி", "பங்குனி"},
            new String[]{"Chithirai", "Vaikasi", "Aani", "Aadi", "Aavani", "Purattasi",
                    "Aippasi", "Karthikai", "Margazhi", "Thai", "Maasi", "Panguni"},
            new String[]{"சுக்ல", "கிருஷ்ண"}, "அதிக "),

    BENGALI("Bengali", "Bangabda", "বঙ্গাব্দ", true, false, '\u09E6',
            new String[]{"বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
                    "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"},
            new String[]{"Boishakh", "Jyoishtho", "Asharh", "Srabon", "Bhadro", "Ashwin",
                    "Kartik", "Ogrohayon", "Poush", "Magh", "Falgun", "Choitro"},
            new String[]{"শুক্ল", "কৃষ্ণ"}, "অধিক ");

    final String label, era, eraNative;
    final boolean solar, purnimanta;
    /** Zero of the script's own digits, or 0 to keep the digits a TV is sure to have. */
    private final char zero;
    final String[] months, latin;
    final String[] paksha;
    final String adhika;

    Region(String label, String era, String eraNative, boolean solar, boolean purnimanta,
           char zero, String[] months, String[] latin, String[] paksha, String adhika) {
        this.label = label;
        this.era = era;
        this.eraNative = eraNative;
        this.solar = solar;
        this.purnimanta = purnimanta;
        this.zero = zero;
        this.months = months;
        this.latin = latin;
        this.paksha = paksha;
        this.adhika = adhika;
    }

    String month(MonthSpan span) { return months[span.name]; }

    String monthLatin(MonthSpan span) { return latin[span.name]; }

    /**
     * The year in this region's era. Vikram Samvat is Shaka plus 135, so both come from the
     * Chaitra that opened the lunar year; the Gujarati year turns at Kartik instead.
     */
    int year(MonthSpan span) {
        if (solar) return era.equals("Bangabda") ? span.solarYear - 593 : span.solarYear - 78;
        long vikram = span.chaitraYear + 57L;
        if (this == GUJARATI) return (int) (span.name < 7 ? vikram - 1 : vikram);
        if (this == MARATHI) return (int) vikram - 135;
        return (int) vikram;
    }

    String digits(String text) {
        if (zero == 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= '0' && c <= '9' ? (char) (zero + (c - '0')) : c);
        }
        return out.toString();
    }

    String digits(int number) { return digits(Integer.toString(number)); }

    static Region named(String name) {
        for (Region region : values()) if (region.name().equals(name)) return region;
        return NORTH;
    }

    /**
     * The calendar the TV's own language suggests, so a Tamil, Bengali, Gujarati or Marathi set
     * opens on the right one before the owner touches the picker. Every other language starts on
     * the north Indian calendar, as Hindi and English do; a saved choice always wins.
     */
    static Region forLocale() {
        return forLocale(Locale.getDefault());
    }

    static Region forLocale(Locale locale) {
        switch (locale.getLanguage()) {
            case "ta": return TAMIL;
            case "bn": return BENGALI;
            case "gu": return GUJARATI;
            case "mr": return MARATHI;
            default: return NORTH;
        }
    }
}

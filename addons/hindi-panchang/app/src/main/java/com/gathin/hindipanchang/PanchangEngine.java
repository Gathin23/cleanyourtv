package com.gathin.hindipanchang;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * High-precision Vedic Astronomical & Hindi Panchang Engine (Vikram Samvat · Purnimanta).
 * Computes:
 * - True Solar & Lunar Longitudes (Jean Meeus) + Lahiri Chitrapaksha Ayanamsa
 * - Vikram Samvat Year, Purnimanta Month (with Adhik Maas detection), Paksha
 * - All 5 Limbs (Tithi, Vara, Nakshatra, Yoga, Karana) with exact transition end times
 * - Kshaya / Vriddhi Tithi detection
 * - Local Sunrise, Sunset, Moonrise, Moonset for any (latitude, longitude, timezone)
 * - Auspicious Muhurats (Brahma, Pratah Sandhya, Abhijit, Vijay, Godhuli)
 * - Inauspicious Windows (Rahu Kaal, Yamaganda, Gulika Kaal)
 * - Astrological Alerts (Bhadra / Vishti, Panchak, Gandmool)
 * - Full 8 Daytime + 8 Nighttime Choghadiya Table
 * - Household Vrat / Fasting Tracker (Ekadashi, Pradosh, Sankashti, Purnima, Amavasya)
 * - Major Hindu Festivals & Daily Sanskrit Subhashita / Doha
 */
final class PanchangEngine {

    static final String[] HINDI_MONTHS_HI = {
        "चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद",
        "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"
    };
    static final String[] HINDI_MONTHS_EN = {
        "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
        "Ashwin", "Kartik", "Margashirsha", "Pausha", "Magha", "Phalguna"
    };

    static final String[] TITHIS_HI = {
        "प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी",
        "षष्ठी", "सप्तमी", "अष्टमी", "नवमी", "दशमी",
        "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा",
        "प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी",
        "षष्ठी", "सप्तमी", "अष्टमी", "नवमी", "दशमी",
        "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "अमावस्या"
    };
    static final String[] TITHIS_EN = {
        "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami",
        "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
        "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima",
        "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami",
        "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
        "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Amavasya"
    };

    static final String[] WEEKDAYS_HI = {
        "रविवार", "सोमवार", "मंगलवार", "बुधवार", "बृहस्पतिवार", "शुक्रवार", "शनिवार"
    };
    static final String[] WEEKDAYS_EN = {
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };
    static final String[] WEEKDAYS_SHORT = {
        "रवि · Sun", "सोम · Mon", "मंगल · Tue", "बुध · Wed", "गुरु · Thu", "शुक्र · Fri", "शनि · Sat"
    };

    static final String[] NAKSHATRAS_HI = {
        "अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशिरा", "आर्द्रा",
        "पुनर्वसु", "पुष्य", "अश्लेषा", "मघा", "पूर्वाफाल्गुनी", "उत्तराफाल्गुनी",
        "हस्त", "चित्रा", "स्वाति", "विशाखा", "अनुराधा", "ज्येष्ठा",
        "मूल", "पूर्वाषाढ़ा", "उत्तराषाढ़ा", "श्रवण", "धनिष्ठा", "शतभिषा",
        "पूर्वाभाद्रपद", "उत्तराभाद्रपद", "रेवती"
    };
    static final String[] NAKSHATRAS_EN = {
        "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
        "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni", "Uttara Phalguni",
        "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha", "Jyeshtha",
        "Mula", "Purva Ashadha", "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha",
        "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"
    };

    static final String[] YOGAS_HI = {
        "विष्कुम्भ", "प्रीति", "आयुष्मान", "सौभाग्य", "शोभन", "अतिगण्ड",
        "सुकर्मा", "धृति", "शूल", "गण्ड", "वृद्धि", "ध्रुव",
        "व्याघात", "हर्षण", "वज्र", "सिद्धि", "व्यतीपात", "वरीयान",
        "परिघ", "शिव", "सिद्ध", "साध्य", "शुभ", "शुक्ल",
        "ब्रह्म", "ऐन्द्र", "वैधृति"
    };
    static final String[] YOGAS_EN = {
        "Vishkumbha", "Priti", "Ayushman", "Saubhagya", "Shobhana", "Atiganda",
        "Sukarma", "Dhriti", "Shula", "Ganda", "Vriddhi", "Dhruva",
        "Vyaghata", "Harshana", "Vajra", "Siddhi", "Vyatipata", "Variyan",
        "Parigha", "Shiva", "Siddha", "Sadhya", "Shubha", "Shukla",
        "Brahma", "Indra", "Vaidhriti"
    };

    static final String[] KARANAS_HI = {
        "बव", "बालव", "कौलव", "तैतिल", "गर", "वणिज", "विष्टि (भद्रा)",
        "शकुनि", "चतुष्पाद", "नाग", "किंस्तुघ्न"
    };
    static final String[] KARANAS_EN = {
        "Bava", "Balava", "Kaulava", "Taitila", "Gara", "Vanija", "Vishti (Bhadra)",
        "Shakuni", "Chatushpada", "Naga", "Kimstughna"
    };

    static final String[] RASHIS_HI = {
        "मेष", "वृषभ", "मिथुन", "कर्क", "सिंह", "कन्या",
        "तुला", "वृश्चिक", "धनु", "मकर", "कुम्भ", "मीन"
    };
    static final String[] RASHIS_EN = {
        "Mesha (Aries)", "Vrishabha (Taurus)", "Mithuna (Gemini)", "Karka (Cancer)",
        "Simha (Leo)", "Kanya (Virgo)", "Tula (Libra)", "Vrishchika (Scorpio)",
        "Dhanu (Sagittarius)", "Makara (Capricorn)", "Kumbha (Aquarius)", "Meena (Pisces)"
    };

    // Choghadiya types: 0=Udveg, 1=Char, 2=Labh, 3=Amrit, 4=Kaal, 5=Shubh, 6=Rog
    private static final String[] CHOG_HI = {"उद्वेग", "चर", "लाभ", "अमृत", "काल", "शुभ", "रोग"};
    private static final String[] CHOG_EN = {"Udveg", "Char", "Labh", "Amrit", "Kaal", "Shubh", "Rog"};
    private static final String[] CHOG_NATURE_HI = {"अशुभ (चिता)", "सामान्य (यात्रा)", "शुभ (लाभ)", "सर्वोत्तम (अमृत)", "अशुभ (हानि)", "उत्तम (मांगलिक)", "अशुभ (कष्ट)"};
    private static final String[] CHOG_NATURE_EN = {"Avoid (Anxiety)", "Neutral (Travel)", "Good (Gain)", "Best (Amrit)", "Avoid (Loss)", "Auspicious", "Avoid (Conflict)"};
    private static final boolean[] CHOG_GOOD = {false, true, true, true, false, true, false};

    private static final int[][] DAY_CHOG_ORDER = {
        {0, 1, 2, 3, 4, 5, 6, 0}, // Sun
        {3, 4, 5, 6, 0, 1, 2, 3}, // Mon
        {6, 0, 1, 2, 3, 4, 5, 6}, // Tue
        {2, 3, 4, 5, 6, 0, 1, 2}, // Wed
        {5, 6, 0, 1, 2, 3, 4, 5}, // Thu
        {1, 2, 3, 4, 5, 6, 0, 1}, // Fri
        {4, 5, 6, 0, 1, 2, 3, 4}  // Sat
    };
    private static final int[][] NIGHT_CHOG_ORDER = {
        {5, 3, 1, 6, 4, 2, 0, 5}, // Sun night
        {1, 6, 4, 2, 0, 5, 3, 1}, // Mon night
        {4, 2, 0, 5, 3, 1, 6, 4}, // Tue night
        {0, 5, 3, 1, 6, 4, 2, 0}, // Wed night
        {3, 1, 6, 4, 2, 0, 5, 3}, // Thu night
        {6, 4, 2, 0, 5, 3, 1, 6}, // Fri night
        {2, 0, 5, 3, 1, 6, 4, 2}  // Sat night
    };

    private static final String[][] SUBHASHITAS = {
        {"ॐ सर्वे भवन्तु सुखिनः सर्वे सन्तु निरामयाः।", "सभी सुखी हों, सभी निरोगी हों और सभी का कल्याण हो।", "May all beings be happy, healthy, and blessed with peace."},
        {"कर्मण्येवाधिकारस्ते मा फलेषु कदाचन।", "तुम्हारा अधिकार केवल कर्म करने में है, फल की चिंता में नहीं।", "You have a right to perform your duty, without attachment to its fruits."},
        {"सत्यमेव जयते नानृतं सत्येन पन्था विततो देवयानः।", "सदैव सत्य की ही विजय होती है, असत्य की नहीं।", "Truth alone triumphs, never falsehood; by truth the divine path is laid."},
        {"उद्यमेन हि सिध्यन्ति कार्याणि न मनोरथैः।", "परिश्रम से ही सभी कार्य सिद्ध होते हैं, केवल इच्छा करने से नहीं।", "Success comes through diligent effort, not by mere wishing."},
        {"काल करे सो आज कर, आज करे सो अब। — कबीर दास", "शुभ कार्य को कल पर न टालें, उसे आज और अभी पूर्ण करें।", "Do tomorrow's noble work today, and today's work right now. — Kabir"},
        {"परहित सरिस धर्म नहिं भाई। पर पीड़ा सम नहिं अधमाई॥ — तुलसीदास", "दूसरों की भलाई के समान कोई धर्म नहीं है।", "There is no higher dharma than helping others. — Tulsidas"},
        {"विद्या ददाति विनयं विनयाद् याति पात्रताम्।", "विद्या से विनम्रता आती है और विनम्रता से योग्यता प्राप्त होती है।", "Knowledge brings humility, and from humility comes worthiness."}
    };

    static final class ChoghadiyaSlot {
        final String nameHi;
        final String nameEn;
        final String natureHi;
        final String natureEn;
        final boolean isAuspicious;
        final boolean isBest; // Amrit or Shubh or Labh
        final int startMin;
        final int endMin;
        final String timeRange;

        ChoghadiyaSlot(int type, int startMin, int endMin) {
            this.nameHi = CHOG_HI[type];
            this.nameEn = CHOG_EN[type];
            this.natureHi = CHOG_NATURE_HI[type];
            this.natureEn = CHOG_NATURE_EN[type];
            this.isAuspicious = CHOG_GOOD[type];
            this.isBest = (type == 3 || type == 5 || type == 2);
            this.startMin = startMin;
            this.endMin = endMin;
            this.timeRange = formatRange(startMin % 1440, endMin % 1440);
        }
    }

    static final class DayPanchang {
        final LocalDate gregorianDate;
        final LocationStore.Place place;
        final int vikramSamvat;
        final int monthIndex;
        final boolean isAdhikMaas;
        final String monthHi;
        final String monthEn;
        final boolean isShuklaPaksha;
        final String pakshaHi;
        final String pakshaEn;
        final int tithiIndex;       // 0..29
        final int pakshaTithiNum;   // 1..15
        final String tithiHi;
        final String tithiEn;
        final String tithiShortHi;
        final String tithiEndFormatted;
        final String nextTithiHi;
        final String nextTithiEn;
        final String tithiSpanNote; // Kshaya or Vriddhi Tithi note if any
        final int weekdayIndex;     // 0=Sun..6=Sat
        final String varaHi;
        final String varaEn;
        final int nakshatraIndex;   // 0..26
        final String nakshatraHi;
        final String nakshatraEn;
        final String nakshatraEndFormatted;
        final int yogaIndex;        // 0..26
        final String yogaHi;
        final String yogaEn;
        final String yogaEndFormatted;
        final int karanaIndex;      // 0..10
        final String karanaHi;
        final String karanaEn;
        final String karanaEndFormatted;
        final int moonRashiIndex;
        final String moonRashiHi;
        final String moonRashiEn;
        final int sunRashiIndex;
        final String sunRashiHi;
        final String sunRashiEn;

        // Solar & Lunar times (minutes from local midnight)
        final int sunriseMin;
        final int sunsetMin;
        final int moonriseMin; // -1 if no moonrise on this civil date
        final int moonsetMin;  // -1 if no moonset on this civil date
        final String sunriseFormatted;
        final String sunsetFormatted;
        final String dayLengthFormatted;
        final String moonriseFormatted;
        final String moonsetFormatted;
        final double moonPhaseAngle;       // 0..360 (0=New, 180=Full)
        final int moonIlluminationPercent; // 0..100

        // Muhurat & Asubha windows
        final int abhijitStartMin;
        final int abhijitEndMin;
        final String abhijitFormatted;
        final String brahmaMuhuratFormatted;
        final String pratahSandhyaFormatted;
        final String vijayMuhuratFormatted;
        final String godhuliMuhuratFormatted;
        final int rahuStartMin;
        final int rahuEndMin;
        final String rahuKaalFormatted;
        final String rahuDurationFormatted;
        final String yamagandaFormatted;
        final String gulikaFormatted;

        // Astrological Alerts (Bhadra, Panchak, Gandmool)
        final boolean hasBhadra;
        final String bhadraNoteHi;
        final String bhadraNoteEn;
        final boolean hasPanchak;
        final String panchakNoteHi;
        final String panchakNoteEn;
        final boolean hasGandmool;
        final String gandmoolNoteHi;
        final String gandmoolNoteEn;

        // Choghadiya
        final List<ChoghadiyaSlot> dayChoghadiya;
        final List<ChoghadiyaSlot> nightChoghadiya;
        final String shubhaChoghadiyaSummary;

        // Festivals & Sticky Vrat Status
        final List<String> festivalsHi;
        final List<String> festivalsEn;
        final boolean isMajorFestival;
        final boolean hasVratBanner;
        final String vratTitleHi;
        final String vratTitleEn;
        final String vratRuleHi;
        final String vratRuleEn;

        // Inspirational Subhashita
        final String subhashitaSanskrit;
        final String subhashitaHi;
        final String subhashitaEn;

        DayPanchang(
                LocalDate gregorianDate, LocationStore.Place place, int vikramSamvat,
                int monthIndex, boolean isAdhikMaas, String monthHi, String monthEn,
                boolean isShuklaPaksha, String pakshaHi, String pakshaEn,
                int tithiIndex, int pakshaTithiNum, String tithiHi, String tithiEn,
                String tithiShortHi, String tithiEndFormatted, String nextTithiHi, String nextTithiEn,
                String tithiSpanNote,
                int weekdayIndex, String varaHi, String varaEn,
                int nakshatraIndex, String nakshatraHi, String nakshatraEn, String nakshatraEndFormatted,
                int yogaIndex, String yogaHi, String yogaEn, String yogaEndFormatted,
                int karanaIndex, String karanaHi, String karanaEn, String karanaEndFormatted,
                int moonRashiIndex, String moonRashiHi, String moonRashiEn,
                int sunRashiIndex, String sunRashiHi, String sunRashiEn,
                int sunriseMin, int sunsetMin, int moonriseMin, int moonsetMin,
                String sunriseFormatted, String sunsetFormatted, String dayLengthFormatted,
                String moonriseFormatted, String moonsetFormatted,
                double moonPhaseAngle, int moonIlluminationPercent,
                int abhijitStartMin, int abhijitEndMin, String abhijitFormatted,
                String brahmaMuhuratFormatted, String pratahSandhyaFormatted,
                String vijayMuhuratFormatted, String godhuliMuhuratFormatted,
                int rahuStartMin, int rahuEndMin, String rahuKaalFormatted, String rahuDurationFormatted,
                String yamagandaFormatted, String gulikaFormatted,
                boolean hasBhadra, String bhadraNoteHi, String bhadraNoteEn,
                boolean hasPanchak, String panchakNoteHi, String panchakNoteEn,
                boolean hasGandmool, String gandmoolNoteHi, String gandmoolNoteEn,
                List<ChoghadiyaSlot> dayChoghadiya, List<ChoghadiyaSlot> nightChoghadiya,
                String shubhaChoghadiyaSummary,
                List<String> festivalsHi, List<String> festivalsEn, boolean isMajorFestival,
                boolean hasVratBanner, String vratTitleHi, String vratTitleEn,
                String vratRuleHi, String vratRuleEn,
                String subhashitaSanskrit, String subhashitaHi, String subhashitaEn) {
            this.gregorianDate = gregorianDate;
            this.place = place;
            this.vikramSamvat = vikramSamvat;
            this.monthIndex = monthIndex;
            this.isAdhikMaas = isAdhikMaas;
            this.monthHi = monthHi;
            this.monthEn = monthEn;
            this.isShuklaPaksha = isShuklaPaksha;
            this.pakshaHi = pakshaHi;
            this.pakshaEn = pakshaEn;
            this.tithiIndex = tithiIndex;
            this.pakshaTithiNum = pakshaTithiNum;
            this.tithiHi = tithiHi;
            this.tithiEn = tithiEn;
            this.tithiShortHi = tithiShortHi;
            this.tithiEndFormatted = tithiEndFormatted;
            this.nextTithiHi = nextTithiHi;
            this.nextTithiEn = nextTithiEn;
            this.tithiSpanNote = tithiSpanNote;
            this.weekdayIndex = weekdayIndex;
            this.varaHi = varaHi;
            this.varaEn = varaEn;
            this.nakshatraIndex = nakshatraIndex;
            this.nakshatraHi = nakshatraHi;
            this.nakshatraEn = nakshatraEn;
            this.nakshatraEndFormatted = nakshatraEndFormatted;
            this.yogaIndex = yogaIndex;
            this.yogaHi = yogaHi;
            this.yogaEn = yogaEn;
            this.yogaEndFormatted = yogaEndFormatted;
            this.karanaIndex = karanaIndex;
            this.karanaHi = karanaHi;
            this.karanaEn = karanaEn;
            this.karanaEndFormatted = karanaEndFormatted;
            this.moonRashiIndex = moonRashiIndex;
            this.moonRashiHi = moonRashiHi;
            this.moonRashiEn = moonRashiEn;
            this.sunRashiIndex = sunRashiIndex;
            this.sunRashiHi = sunRashiHi;
            this.sunRashiEn = sunRashiEn;
            this.sunriseMin = sunriseMin;
            this.sunsetMin = sunsetMin;
            this.moonriseMin = moonriseMin;
            this.moonsetMin = moonsetMin;
            this.sunriseFormatted = sunriseFormatted;
            this.sunsetFormatted = sunsetFormatted;
            this.dayLengthFormatted = dayLengthFormatted;
            this.moonriseFormatted = moonriseFormatted;
            this.moonsetFormatted = moonsetFormatted;
            this.moonPhaseAngle = moonPhaseAngle;
            this.moonIlluminationPercent = moonIlluminationPercent;
            this.abhijitStartMin = abhijitStartMin;
            this.abhijitEndMin = abhijitEndMin;
            this.abhijitFormatted = abhijitFormatted;
            this.brahmaMuhuratFormatted = brahmaMuhuratFormatted;
            this.pratahSandhyaFormatted = pratahSandhyaFormatted;
            this.vijayMuhuratFormatted = vijayMuhuratFormatted;
            this.godhuliMuhuratFormatted = godhuliMuhuratFormatted;
            this.rahuStartMin = rahuStartMin;
            this.rahuEndMin = rahuEndMin;
            this.rahuKaalFormatted = rahuKaalFormatted;
            this.rahuDurationFormatted = rahuDurationFormatted;
            this.yamagandaFormatted = yamagandaFormatted;
            this.gulikaFormatted = gulikaFormatted;
            this.hasBhadra = hasBhadra;
            this.bhadraNoteHi = bhadraNoteHi;
            this.bhadraNoteEn = bhadraNoteEn;
            this.hasPanchak = hasPanchak;
            this.panchakNoteHi = panchakNoteHi;
            this.panchakNoteEn = panchakNoteEn;
            this.hasGandmool = hasGandmool;
            this.gandmoolNoteHi = gandmoolNoteHi;
            this.gandmoolNoteEn = gandmoolNoteEn;
            this.dayChoghadiya = dayChoghadiya;
            this.nightChoghadiya = nightChoghadiya;
            this.shubhaChoghadiyaSummary = shubhaChoghadiyaSummary;
            this.festivalsHi = festivalsHi;
            this.festivalsEn = festivalsEn;
            this.isMajorFestival = isMajorFestival;
            this.hasVratBanner = hasVratBanner;
            this.vratTitleHi = vratTitleHi;
            this.vratTitleEn = vratTitleEn;
            this.vratRuleHi = vratRuleHi;
            this.vratRuleEn = vratRuleEn;
            this.subhashitaSanskrit = subhashitaSanskrit;
            this.subhashitaHi = subhashitaHi;
            this.subhashitaEn = subhashitaEn;
        }

        ChoghadiyaSlot activeChoghadiyaAt(int minOfDay) {
            if (minOfDay >= sunriseMin && minOfDay < sunsetMin) {
                for (ChoghadiyaSlot s : dayChoghadiya) {
                    if (minOfDay >= s.startMin && minOfDay < s.endMin) return s;
                }
            }
            int adjMin = minOfDay < sunriseMin ? minOfDay + 1440 : minOfDay;
            for (ChoghadiyaSlot s : nightChoghadiya) {
                if (adjMin >= s.startMin && adjMin < s.endMin) return s;
            }
            return dayChoghadiya.get(0);
        }

        boolean isInRahuKaal(int minOfDay) {
            return minOfDay >= rahuStartMin && minOfDay <= rahuEndMin;
        }

        boolean isInAbhijit(int minOfDay) {
            return minOfDay >= abhijitStartMin && minOfDay <= abhijitEndMin;
        }
    }

    static DayPanchang calculate(LocalDate date) {
        return calculate(date, LocationStore.PRESETS[0]);
    }

    static DayPanchang calculate(LocalDate date, LocationStore.Place place) {
        ZoneId zone = place.zoneId();
        int[] sunTimes = computeSunriseSunset(date, place.latitude, place.longitude, zone);
        int sunriseMin = sunTimes[0];
        int sunsetMin = sunTimes[1];
        int dayDurationMin = Math.max(120, sunsetMin - sunriseMin);

        ZonedDateTime sunriseZdt = date.atStartOfDay(zone).plusMinutes(sunriseMin);
        double jdSunrise = toJulianDay(sunriseZdt.toInstant().getEpochSecond());

        double sunLonTrop = solarLongitude(jdSunrise);
        double moonLonTrop = lunarLongitude(jdSunrise);
        double ayanamsa = lahiriAyanamsa(jdSunrise);

        double sunSidereal = norm360(sunLonTrop - ayanamsa);
        double moonSidereal = norm360(moonLonTrop - ayanamsa);
        double elongation = norm360(moonLonTrop - sunLonTrop);

        int tithiIndex = (int) Math.floor(elongation / 12.0);
        if (tithiIndex >= 30) tithiIndex = 29;
        boolean isShukla = tithiIndex < 15;
        int pakshaTithiNum = (tithiIndex % 15) + 1;

        String pakshaHi = isShukla ? "शुक्ल पक्ष" : "कृष्ण पक्ष";
        String pakshaEn = isShukla ? "Shukla Paksha" : "Krishna Paksha";
        String tithiHi = TITHIS_HI[tithiIndex];
        String tithiEn = TITHIS_EN[tithiIndex];
        int nextIdx = (tithiIndex + 1) % 30;
        String nextTithiHi = TITHIS_HI[nextIdx];
        String nextTithiEn = TITHIS_EN[nextIdx];

        String tithiShortHi;
        if (tithiIndex == 14) {
            tithiShortHi = "पूर्णिमा";
        } else if (tithiIndex == 29) {
            tithiShortHi = "अमावस्या";
        } else {
            tithiShortHi = (isShukla ? "शु." : "कृ.") + toDevanagariDigits(pakshaTithiNum);
        }

        double tithiEndJd = findTithiEndJd(jdSunrise, tithiIndex);
        String tithiEndFormatted = formatEndJd(tithiEndJd, date, zone);

        // Check Kshaya / Vriddhi Tithi against next day's sunrise
        double jdNextSunrise = jdSunrise + 1.0;
        int nextSunriseTithi = (int) Math.floor(norm360(lunarLongitude(jdNextSunrise) - solarLongitude(jdNextSunrise)) / 12.0);
        String tithiSpanNote = "";
        if (nextSunriseTithi == tithiIndex) {
            tithiSpanNote = "वृद्धि तिथि (Vriddhi Tithi)";
        } else if (((tithiIndex + 2) % 30) == nextSunriseTithi) {
            tithiSpanNote = "क्षय तिथि स्पर्श (Kshaya Tithi)";
        }

        int nakshatraIndex = (int) Math.floor(moonSidereal / (360.0 / 27.0));
        if (nakshatraIndex >= 27) nakshatraIndex = 26;
        double nakEndJd = findNakshatraEndJd(jdSunrise, nakshatraIndex);
        String nakshatraEndFormatted = formatEndJd(nakEndJd, date, zone);

        double yogaSum = norm360(sunSidereal + moonSidereal);
        int yogaIndex = (int) Math.floor(yogaSum / (360.0 / 27.0));
        if (yogaIndex >= 27) yogaIndex = 26;
        double yogaEndJd = findYogaEndJd(jdSunrise, yogaIndex);
        String yogaEndFormatted = formatEndJd(yogaEndJd, date, zone);

        int halfTithi = (int) Math.floor(elongation / 6.0);
        int karanaIndex = karanaFromHalfTithi(halfTithi);
        double karanaEndJd = findKaranaEndJd(jdSunrise, halfTithi);
        String karanaEndFormatted = formatEndJd(karanaEndJd, date, zone);

        int moonRashiIndex = (int) Math.floor(moonSidereal / 30.0) % 12;
        int sunRashiIndex = (int) Math.floor(sunSidereal / 30.0) % 12;

        // Purnimanta Month & Adhik Maas check
        int[] monthAndAdhik = computePurnimantaMonthAndAdhik(jdSunrise, elongation, ayanamsa);
        int monthIndex = monthAndAdhik[0];
        boolean isAdhikMaas = monthAndAdhik[1] == 1;
        String monthHi = (isAdhikMaas ? "अधिक " : "") + HINDI_MONTHS_HI[monthIndex];
        String monthEn = (isAdhikMaas ? "Adhik " : "") + HINDI_MONTHS_EN[monthIndex];

        int vikramSamvat = computeVikramSamvat(date, monthIndex);
        int weekdayIndex = date.getDayOfWeek().getValue() % 7;

        // Moonrise & Moonset across the Hindu Ahoratra (Sunrise to next Sunrise)
        int[] moonTimes = computeMoonriseMoonset(date, sunriseMin, place.latitude, place.longitude, zone);
        int moonriseMin = moonTimes[0];
        int moonsetMin = moonTimes[1];
        String moonriseFormatted = moonriseMin >= 0
                ? formatMinutes12h(moonriseMin) + (moonriseMin >= 1440 ? " (+1d)" : "")
                : "रात्रि में नहीं / None";
        String moonsetFormatted = moonsetMin >= 0
                ? formatMinutes12h(moonsetMin) + (moonsetMin >= 1440 ? " (+1d)" : "")
                : "—";

        int dayLenH = dayDurationMin / 60;
        int dayLenM = dayDurationMin % 60;
        String dayLengthFormatted = String.format(Locale.US, "%dh %02dm", dayLenH, dayLenM);

        double illumFraction = (1.0 - Math.cos(Math.toRadians(elongation))) / 2.0;
        int moonIllumPct = (int) Math.round(illumFraction * 100.0);

        // Muhurat calculations
        int brahmaStart = sunriseMin - 96;
        int brahmaEnd = sunriseMin - 48;
        int pratahStart = sunriseMin - 48;
        int pratahEnd = sunriseMin;

        int middayMin = sunriseMin + dayDurationMin / 2;
        int muhurtaLen = Math.max(24, dayDurationMin / 15);
        int abhijitStart = middayMin - muhurtaLen / 2;
        int abhijitEnd = middayMin + muhurtaLen / 2;

        int vijayStart = sunriseMin + (dayDurationMin * 11) / 15;
        int vijayEnd = sunriseMin + (dayDurationMin * 12) / 15;
        int godhuliStart = sunsetMin - 12;
        int godhuliEnd = sunsetMin + 12;

        // Rahu Kaal, Yamaganda, Gulika
        int partLen = dayDurationMin / 8;
        int[] rahuPart = {8, 2, 7, 5, 6, 4, 3};
        int[] yamaPart = {5, 4, 3, 2, 1, 7, 6};
        int[] guliPart = {7, 6, 5, 4, 3, 2, 1};

        int rahuStart = sunriseMin + (rahuPart[weekdayIndex] - 1) * partLen;
        int rahuEnd = rahuStart + partLen;
        int rahuDur = rahuEnd - rahuStart;
        String rahuDurationFormatted = (rahuDur / 60) + "h " + String.format(Locale.US, "%02dm", rahuDur % 60);

        int yamaStart = sunriseMin + (yamaPart[weekdayIndex] - 1) * partLen;
        int yamaEnd = yamaStart + partLen;
        int guliStart = sunriseMin + (guliPart[weekdayIndex] - 1) * partLen;
        int guliEnd = guliStart + partLen;

        // Day & Night Choghadiya
        List<ChoghadiyaSlot> dayChog = new ArrayList<>(8);
        StringBuilder shubhaSummary = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int sStart = sunriseMin + i * partLen;
            int sEnd = (i == 7) ? sunsetMin : (sStart + partLen);
            int type = DAY_CHOG_ORDER[weekdayIndex][i];
            ChoghadiyaSlot slot = new ChoghadiyaSlot(type, sStart, sEnd);
            dayChog.add(slot);
            if (slot.isBest) {
                if (shubhaSummary.length() > 0) shubhaSummary.append(" · ");
                shubhaSummary.append(slot.nameHi).append(" ").append(slot.timeRange);
            }
        }

        int nightDurationMin = 1440 - dayDurationMin;
        int nightPartLen = nightDurationMin / 8;
        List<ChoghadiyaSlot> nightChog = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            int sStart = sunsetMin + i * nightPartLen;
            int sEnd = sStart + nightPartLen;
            int type = NIGHT_CHOG_ORDER[weekdayIndex][i];
            nightChog.add(new ChoghadiyaSlot(type, sStart, sEnd));
        }

        // Astrological Alerts: Bhadra, Panchak, Gandmool
        boolean hasBhadra = (karanaIndex == 6);
        String bhadraNoteHi = hasBhadra ? "भद्रा सक्रिय (विष्टि करण — " + karanaEndFormatted + " तक)" : "भद्रा नहीं (No Bhadra)";
        String bhadraNoteEn = hasBhadra ? "Bhadra Active (Vishti till " + karanaEndFormatted + ")" : "No Bhadra";

        // Panchak: Moon in Aquarius/Pisces (300° to 360° sidereal)
        boolean hasPanchak = (moonSidereal >= 300.0 && moonSidereal < 360.0);
        String panchakNoteHi = hasPanchak ? "पंचक काल सक्रिय (" + NAKSHATRAS_HI[nakshatraIndex] + " नक्षत्र)" : "पंचक नहीं";
        String panchakNoteEn = hasPanchak ? "Panchak Active (" + NAKSHATRAS_EN[nakshatraIndex] + ")" : "No Panchak";

        // Gandmool: Ashwini(0), Ashlesha(8), Magha(9), Jyeshtha(17), Mula(18), Revati(26)
        boolean hasGandmool = (nakshatraIndex == 0 || nakshatraIndex == 8 || nakshatraIndex == 9
                || nakshatraIndex == 17 || nakshatraIndex == 18 || nakshatraIndex == 26);
        String gandmoolNoteHi = hasGandmool ? "गण्डमूल नक्षत्र (" + NAKSHATRAS_HI[nakshatraIndex] + " — " + nakshatraEndFormatted + " तक)" : "गण्डमूल नहीं";
        String gandmoolNoteEn = hasGandmool ? "Gandmool Nakshatra (" + NAKSHATRAS_EN[nakshatraIndex] + " till " + nakshatraEndFormatted + ")" : "No Gandmool";

        // Festivals & Sticky Vrat Status
        List<String> festivalsHi = new ArrayList<>();
        List<String> festivalsEn = new ArrayList<>();
        boolean isMajor = populateFestivals(
                date, monthIndex, isShukla, pakshaTithiNum, tithiIndex,
                sunSidereal, weekdayIndex, festivalsHi, festivalsEn
        );

        boolean hasVratBanner = false;
        String vratTitleHi = "";
        String vratTitleEn = "";
        String vratRuleHi = "";
        String vratRuleEn = "";

        if (pakshaTithiNum == 11) {
            hasVratBanner = true;
            vratTitleHi = (isShukla ? "शुक्ल " : "कृष्ण ") + "एकादशी व्रत (स्मार्त एवं वैष्णव)";
            vratTitleEn = (isShukla ? "Shukla " : "Krishna ") + "Ekadashi Vrat (Smarta & Vaishnava)";
            vratRuleHi = "उपवास नियम: दशमी युक्त एकादशी से रहित शुद्ध एकादशी व्रत · पारण अगले दिन प्रातः सूर्योदय पश्चात्। चन्द्रोदय: " + moonriseFormatted;
            vratRuleEn = "Fasting Rule: Observe Ekadashi fast today; Parana next morning after Sunrise. Moonrise: " + moonriseFormatted;
        } else if (tithiIndex == 14) {
            hasVratBanner = true;
            vratTitleHi = monthHi + " पूर्णिमा व्रत एवं सत्यनारायण पूजा";
            vratTitleEn = monthEn + " Purnima Vrat & Satyanarayan Puja";
            vratRuleHi = "सायं चन्द्रोदय (" + moonriseFormatted + ") पर चन्द्र अर्घ्य एवं सत्यनारायण कथा पूजन।";
            vratRuleEn = "Evening Moonrise at " + moonriseFormatted + " — Offer Chandra Arghya & Satyanarayan Puja.";
        } else if (tithiIndex == 29) {
            hasVratBanner = true;
            vratTitleHi = monthHi + " दर्श अमावस्या (पितृ तर्पण एवं दान)";
            vratTitleEn = monthEn + " Amavasya (Pitru Tarpan & Charity)";
            vratRuleHi = "प्रातः स्नान, पितृ तर्पण, पीपल वृक्ष पूजन एवं दीपदान।";
            vratRuleEn = "Morning sacred bath, Pitru Tarpan, Peepal worship & lamp lighting.";
        } else if (!isShukla && pakshaTithiNum == 4) {
            hasVratBanner = true;
            boolean karwa = (monthIndex == 7);
            vratTitleHi = karwa ? "करवा चौथ एवं संकष्टी चतुर्थी व्रत" : "संकष्टी गणेश चतुर्थी व्रत";
            vratTitleEn = karwa ? "Karwa Chauth & Sankashti Chaturthi Vrat" : "Sankashti Ganesh Chaturthi Vrat";
            vratRuleHi = "चन्द्रोदय समय: " + moonriseFormatted + " — रात्रि में चन्द्र दर्शन एवं अर्घ्य के पश्चात् व्रत पारण।";
            vratRuleEn = "Evening Moonrise at " + moonriseFormatted + " — Break fast after sighting the Moon and offering Arghya.";
        } else if (pakshaTithiNum == 13) {
            hasVratBanner = true;
            vratTitleHi = "प्रदोष व्रत (संध्या शिव पूजन)";
            vratTitleEn = "Pradosh Vrat (Evening Shiva Puja)";
            vratRuleHi = "प्रदोष काल पूजा मुहूर्त: " + formatRange(sunsetMin - 24, sunsetMin + 48) + " · चन्द्रोदय: " + moonriseFormatted;
            vratRuleEn = "Pradosh Puja Window: " + formatRange(sunsetMin - 24, sunsetMin + 48) + " · Moonrise: " + moonriseFormatted;
        } else if (!festivalsHi.isEmpty()) {
            hasVratBanner = true;
            vratTitleHi = "पर्व / विशेष योग: " + festivalsHi.get(0);
            vratTitleEn = "Festival / Special Yoga: " + festivalsEn.get(0);
            vratRuleHi = "अभिजीत मुहूर्त: " + formatRange(abhijitStart, abhijitEnd) + " · चन्द्रोदय: " + moonriseFormatted;
            vratRuleEn = "Abhijit Muhurat: " + formatRange(abhijitStart, abhijitEnd) + " · Moonrise: " + moonriseFormatted;
        }

        int subIdx = Math.abs(date.getDayOfYear()) % SUBHASHITAS.length;

        return new DayPanchang(
                date, place, vikramSamvat,
                monthIndex, isAdhikMaas, monthHi, monthEn,
                isShukla, pakshaHi, pakshaEn,
                tithiIndex, pakshaTithiNum, tithiHi, tithiEn,
                tithiShortHi, tithiEndFormatted, nextTithiHi, nextTithiEn,
                tithiSpanNote,
                weekdayIndex, WEEKDAYS_HI[weekdayIndex], WEEKDAYS_EN[weekdayIndex],
                nakshatraIndex, NAKSHATRAS_HI[nakshatraIndex], NAKSHATRAS_EN[nakshatraIndex], nakshatraEndFormatted,
                yogaIndex, YOGAS_HI[yogaIndex], YOGAS_EN[yogaIndex], yogaEndFormatted,
                karanaIndex, KARANAS_HI[karanaIndex], KARANAS_EN[karanaIndex], karanaEndFormatted,
                moonRashiIndex, RASHIS_HI[moonRashiIndex], RASHIS_EN[moonRashiIndex],
                sunRashiIndex, RASHIS_HI[sunRashiIndex], RASHIS_EN[sunRashiIndex],
                sunriseMin, sunsetMin, moonriseMin, moonsetMin,
                formatMinutes12h(sunriseMin), formatMinutes12h(sunsetMin), dayLengthFormatted,
                moonriseFormatted, moonsetFormatted,
                elongation, moonIllumPct,
                abhijitStart, abhijitEnd, formatRange(abhijitStart, abhijitEnd),
                formatRange(brahmaStart, brahmaEnd),
                formatRange(pratahStart, pratahEnd),
                formatRange(vijayStart, vijayEnd),
                formatRange(godhuliStart, godhuliEnd),
                rahuStart, rahuEnd, formatRange(rahuStart, rahuEnd), rahuDurationFormatted,
                formatRange(yamaStart, yamaEnd),
                formatRange(guliStart, guliEnd),
                hasBhadra, bhadraNoteHi, bhadraNoteEn,
                hasPanchak, panchakNoteHi, panchakNoteEn,
                hasGandmool, gandmoolNoteHi, gandmoolNoteEn,
                dayChog, nightChog, shubhaSummary.toString(),
                festivalsHi, festivalsEn, isMajor,
                hasVratBanner, vratTitleHi, vratTitleEn, vratRuleHi, vratRuleEn,
                SUBHASHITAS[subIdx][0], SUBHASHITAS[subIdx][1], SUBHASHITAS[subIdx][2]
        );
    }

    private static boolean populateFestivals(
            LocalDate date, int monthIndex, boolean isShukla, int pakshaTithi, int tithiIndex,
            double sunSidereal, int weekdayIndex, List<String> hi, List<String> en) {
        boolean major = false;

        if (date.getMonthValue() == 1 && (date.getDayOfMonth() == 14 || (date.getDayOfMonth() == 15 && sunSidereal >= 270.0 && sunSidereal < 271.2))) {
            hi.add("मकर संक्रांति / उत्तरायण");
            en.add("Makar Sankranti / Uttarayan");
            major = true;
        }
        if (date.getMonthValue() == 1 && date.getDayOfMonth() == 13) {
            hi.add("लोहड़ी");
            en.add("Lohri");
        }
        if (date.getMonthValue() == 4 && (date.getDayOfMonth() == 13 || date.getDayOfMonth() == 14) && sunSidereal < 2.0) {
            hi.add("बैसाखी / मेष संक्रांति");
            en.add("Baisakhi / Mesha Sankranti");
            major = true;
        }

        if (monthIndex == 0 && isShukla && pakshaTithi == 1) {
            hi.add("हिंदू नववर्ष · चैत्र नवरात्रि प्रारंभ");
            en.add("Hindu New Year · Chaitra Navratri Begins");
            major = true;
        }
        if (monthIndex == 0 && isShukla && pakshaTithi == 9) {
            hi.add("श्री राम नवमी");
            en.add("Sri Ram Navami");
            major = true;
        }
        if (monthIndex == 0 && tithiIndex == 14) {
            hi.add("हनुमान जयंती · चैत्र पूर्णिमा");
            en.add("Hanuman Jayanti · Chaitra Purnima");
            major = true;
        }
        if (monthIndex == 1 && isShukla && pakshaTithi == 3) {
            hi.add("अक्षय तृतीया (परशुराम जयंती)");
            en.add("Akshaya Tritiya");
            major = true;
        }
        if (monthIndex == 1 && tithiIndex == 14) {
            hi.add("बुद्ध पूर्णिमा · वैशाख पूर्णिमा");
            en.add("Buddha Purnima");
            major = true;
        }
        if (monthIndex == 2 && !isShukla && tithiIndex == 29) {
            hi.add("वट सावित्री व्रत · शनि जयंती");
            en.add("Vat Savitri Vrat · Shani Jayanti");
            major = true;
        }
        if (monthIndex == 2 && isShukla && pakshaTithi == 10) {
            hi.add("गंगा दशहरा");
            en.add("Ganga Dussehra");
            major = true;
        }
        if (monthIndex == 2 && isShukla && pakshaTithi == 11) {
            hi.add("निर्जला एकादशी");
            en.add("Nirjala Ekadashi");
            major = true;
        }
        if (monthIndex == 3 && isShukla && pakshaTithi == 2) {
            hi.add("जगन्नाथ रथयात्रा");
            en.add("Jagannath Rath Yatra");
            major = true;
        }
        if (monthIndex == 3 && isShukla && pakshaTithi == 11) {
            hi.add("देवशयनी आषाढ़ी एकादशी");
            en.add("Devshayani Ekadashi");
            major = true;
        }
        if (monthIndex == 3 && tithiIndex == 14) {
            hi.add("गुरु पूर्णिमा (व्यास पूर्णिमा)");
            en.add("Guru Purnima");
            major = true;
        }
        if (monthIndex == 4 && isShukla && pakshaTithi == 3) {
            hi.add("हरियाली तीज");
            en.add("Hariyali Teej");
            major = true;
        }
        if (monthIndex == 4 && isShukla && pakshaTithi == 5) {
            hi.add("नाग पंचमी");
            en.add("Nag Panchami");
            major = true;
        }
        if (monthIndex == 4 && tithiIndex == 14) {
            hi.add("रक्षा बंधन · श्रावण पूर्णिमा");
            en.add("Raksha Bandhan");
            major = true;
        }
        if (monthIndex == 4 && weekdayIndex == 1) {
            hi.add("श्रावण सोमवार व्रत");
            en.add("Shravan Somwar Vrat");
        }
        if (monthIndex == 5 && !isShukla && pakshaTithi == 8) {
            hi.add("श्री कृष्ण जन्माष्टमी");
            en.add("Sri Krishna Janmashtami");
            major = true;
        }
        if (monthIndex == 5 && isShukla && pakshaTithi == 3) {
            hi.add("हरितालिका तीज");
            en.add("Haritalika Teej");
            major = true;
        }
        if (monthIndex == 5 && isShukla && pakshaTithi == 4) {
            hi.add("गणेश चतुर्थी");
            en.add("Ganesh Chaturthi");
            major = true;
        }
        if (monthIndex == 5 && isShukla && pakshaTithi == 14) {
            hi.add("अनंत चतुर्दशी (गणेश विसर्जन)");
            en.add("Anant Chaturdashi");
            major = true;
        }
        if (monthIndex == 6 && !isShukla && pakshaTithi == 1) {
            hi.add("पितृ पक्ष (श्राद्ध) प्रारंभ");
            en.add("Pitru Paksha Begins");
        }
        if (monthIndex == 6 && tithiIndex == 29) {
            hi.add("सर्वपितृ अमावस्या (महालया)");
            en.add("Sarva Pitru Amavasya");
            major = true;
        }
        if (monthIndex == 6 && isShukla && pakshaTithi == 1) {
            hi.add("शारदीय नवरात्रि प्रारंभ (कलश स्थापना)");
            en.add("Sharad Navratri Begins");
            major = true;
        }
        if (monthIndex == 6 && isShukla && pakshaTithi == 8) {
            hi.add("दुर्गा महाष्टमी");
            en.add("Durga Ashtami");
            major = true;
        }
        if (monthIndex == 6 && isShukla && pakshaTithi == 9) {
            hi.add("महानवमी");
            en.add("Maha Navami");
            major = true;
        }
        if (monthIndex == 6 && isShukla && pakshaTithi == 10) {
            hi.add("विजयादशमी (दशहरा)");
            en.add("Vijayadashami (Dussehra)");
            major = true;
        }
        if (monthIndex == 6 && tithiIndex == 14) {
            hi.add("शरद पूर्णिमा (कोजागरी)");
            en.add("Sharad Purnima");
            major = true;
        }
        if (monthIndex == 7 && !isShukla && pakshaTithi == 4) {
            hi.add("करवा चौथ (संकष्टी चतुर्थी)");
            en.add("Karwa Chauth");
            major = true;
        }
        if (monthIndex == 7 && !isShukla && pakshaTithi == 8) {
            hi.add("अहोई अष्टमी");
            en.add("Ahoi Ashtami");
            major = true;
        }
        if (monthIndex == 7 && !isShukla && pakshaTithi == 13) {
            hi.add("धनतेरस (धन्वन्तरि जयंती)");
            en.add("Dhanteras");
            major = true;
        }
        if (monthIndex == 7 && !isShukla && pakshaTithi == 14) {
            hi.add("नरक चतुर्दशी (छोटी दीपावली)");
            en.add("Chhoti Diwali");
            major = true;
        }
        if (monthIndex == 7 && tithiIndex == 29) {
            hi.add("दीपावली (लक्ष्मी-गणेश पूजन)");
            en.add("Diwali · Lakshmi Puja");
            major = true;
        }
        if (monthIndex == 7 && isShukla && pakshaTithi == 1) {
            hi.add("गोवर्धन पूजा · अन्नकूट");
            en.add("Govardhan Puja · Annakut");
            major = true;
        }
        if (monthIndex == 7 && isShukla && pakshaTithi == 2) {
            hi.add("भाई दूज (यम द्वितीया)");
            en.add("Bhai Dooj");
            major = true;
        }
        if (monthIndex == 7 && isShukla && pakshaTithi == 6) {
            hi.add("छठ महापर्व (सूर्य षष्ठी)");
            en.add("Chhath Puja");
            major = true;
        }
        if (monthIndex == 7 && isShukla && pakshaTithi == 11) {
            hi.add("देवउठनी (प्रबोधिनी) एकादशी · तुलसी विवाह");
            en.add("Devuthani Ekadashi · Tulsi Vivah");
            major = true;
        }
        if (monthIndex == 7 && tithiIndex == 14) {
            hi.add("कार्तिक पूर्णिमा · देव दीपावली · गुरु नानक जयंती");
            en.add("Kartik Purnima · Dev Deepawali");
            major = true;
        }
        if (monthIndex == 8 && isShukla && pakshaTithi == 11) {
            hi.add("गीता जयंती · मोक्षदा एकादशी");
            en.add("Gita Jayanti · Mokshada Ekadashi");
            major = true;
        }
        if (monthIndex == 10 && tithiIndex == 29) {
            hi.add("मौनी अमावस्या");
            en.add("Mauni Amavasya");
            major = true;
        }
        if (monthIndex == 10 && isShukla && pakshaTithi == 5) {
            hi.add("बसंत पंचमी (सरस्वती पूजा)");
            en.add("Basant Panchami · Saraswati Puja");
            major = true;
        }
        if (monthIndex == 11 && !isShukla && (pakshaTithi == 13 || pakshaTithi == 14)) {
            if (pakshaTithi == 14) {
                hi.add("महाशिवरात्रि");
                en.add("Maha Shivaratri");
                major = true;
            }
        }
        if (monthIndex == 11 && tithiIndex == 14) {
            hi.add("होलिका दहन · फाल्गुन पूर्णिमा");
            en.add("Holika Dahan · Phalguna Purnima");
            major = true;
        }
        if (monthIndex == 0 && !isShukla && pakshaTithi == 1) {
            hi.add("होली (धुलेंडी / रंगोत्सव)");
            en.add("Holi (Dhulandi)");
            major = true;
        }

        // Recurring household Vrats
        if (pakshaTithi == 11 && hi.isEmpty()) {
            hi.add((isShukla ? "शुक्ल " : "कृष्ण ") + "एकादशी व्रत");
            en.add((isShukla ? "Shukla " : "Krishna ") + "Ekadashi Vrat");
        }
        if (pakshaTithi == 13) {
            hi.add("प्रदोष व्रत");
            en.add("Pradosh Vrat");
        }
        if (!isShukla && pakshaTithi == 4 && monthIndex != 7) {
            hi.add("संकष्टी गणेश चतुर्थी व्रत");
            en.add("Sankashti Chaturthi Vrat");
        }
        if (tithiIndex == 14 && hi.isEmpty()) {
            hi.add(HINDI_MONTHS_HI[monthIndex] + " पूर्णिमा व्रत");
            en.add(HINDI_MONTHS_EN[monthIndex] + " Purnima Vrat");
        }
        if (tithiIndex == 29 && hi.isEmpty()) {
            hi.add(HINDI_MONTHS_HI[monthIndex] + " अमावस्या");
            en.add(HINDI_MONTHS_EN[monthIndex] + " Amavasya");
        }

        return major;
    }

    /**
     * Computes Purnimanta Month index (0=Chaitra..11=Phalguna) and whether it is an Adhik Maas.
     */
    private static int[] computePurnimantaMonthAndAdhik(double jd, double elongation, double ayanamsa) {
        double daysSinceNewMoon = (elongation / 360.0) * 29.530588;
        double jdNewMoon = jd - daysSinceNewMoon;
        for (int iter = 0; iter < 5; iter++) {
            double diff = norm360(lunarLongitude(jdNewMoon) - solarLongitude(jdNewMoon));
            if (diff > 180.0) diff -= 360.0;
            jdNewMoon -= diff / 12.190749;
        }
        double jdNextNewMoon = jdNewMoon + 29.530588;
        for (int iter = 0; iter < 5; iter++) {
            double diff = norm360(lunarLongitude(jdNextNewMoon) - solarLongitude(jdNextNewMoon));
            if (diff > 180.0) diff -= 360.0;
            jdNextNewMoon -= diff / 12.190749;
        }

        int rashiStart = (int) Math.floor(norm360(solarLongitude(jdNewMoon) - ayanamsa) / 30.0);
        int rashiEnd = (int) Math.floor(norm360(solarLongitude(jdNextNewMoon) - ayanamsa) / 30.0);
        boolean isAdhik = (rashiStart == rashiEnd);

        int amantaMonth = (rashiStart + 1) % 12;
        int purnimantaMonth = (elongation >= 180.0) ? (amantaMonth + 1) % 12 : amantaMonth;
        return new int[]{purnimantaMonth, isAdhik ? 1 : 0};
    }

    private static int computeVikramSamvat(LocalDate date, int hindiMonthIndex) {
        int y = date.getYear();
        if (date.getMonthValue() <= 3 && hindiMonthIndex >= 9) {
            return y + 56;
        }
        if (date.getMonthValue() == 3 && hindiMonthIndex == 0 && date.getDayOfMonth() < 15) {
            return y + 57;
        }
        return y + 57;
    }

    private static int karanaFromHalfTithi(int halfTithi) {
        if (halfTithi <= 0) return 10;
        if (halfTithi >= 57) {
            int idx = 7 + (halfTithi - 57);
            return Math.min(10, idx);
        }
        return (halfTithi - 1) % 7;
    }

    private static double findTithiEndJd(double jdStart, int currentTithi) {
        double targetAngle = ((currentTithi + 1) % 30) * 12.0;
        double low = jdStart;
        double high = jdStart + 1.3;
        for (int i = 0; i < 22; i++) {
            double mid = 0.5 * (low + high);
            double el = norm360(lunarLongitude(mid) - solarLongitude(mid));
            double diff = norm360(targetAngle - el);
            if (diff < 180.0) low = mid;
            else high = mid;
        }
        return 0.5 * (low + high);
    }

    private static double findNakshatraEndJd(double jdStart, int currentNak) {
        double step = 360.0 / 27.0;
        double targetAngle = ((currentNak + 1) % 27) * step;
        double low = jdStart;
        double high = jdStart + 1.3;
        for (int i = 0; i < 22; i++) {
            double mid = 0.5 * (low + high);
            double mSid = norm360(lunarLongitude(mid) - lahiriAyanamsa(mid));
            double diff = norm360(targetAngle - mSid);
            if (diff < 180.0) low = mid;
            else high = mid;
        }
        return 0.5 * (low + high);
    }

    private static double findYogaEndJd(double jdStart, int currentYoga) {
        double step = 360.0 / 27.0;
        double targetAngle = ((currentYoga + 1) % 27) * step;
        double low = jdStart;
        double high = jdStart + 1.3;
        for (int i = 0; i < 20; i++) {
            double mid = 0.5 * (low + high);
            double ay = lahiriAyanamsa(mid);
            double sum = norm360((solarLongitude(mid) - ay) + (lunarLongitude(mid) - ay));
            double diff = norm360(targetAngle - sum);
            if (diff < 180.0) low = mid;
            else high = mid;
        }
        return 0.5 * (low + high);
    }

    private static double findKaranaEndJd(double jdStart, int currentHalfTithi) {
        double targetAngle = ((currentHalfTithi + 1) % 60) * 6.0;
        double low = jdStart;
        double high = jdStart + 0.75;
        for (int i = 0; i < 20; i++) {
            double mid = 0.5 * (low + high);
            double el = norm360(lunarLongitude(mid) - solarLongitude(mid));
            double diff = norm360(targetAngle - el);
            if (diff < 180.0) low = mid;
            else high = mid;
        }
        return 0.5 * (low + high);
    }

    /**
     * Computes local Sunrise and Sunset in minutes from midnight for (lat, lon, zone).
     */
    private static int[] computeSunriseSunset(LocalDate date, double lat, double lon, ZoneId zone) {
        int dayOfYear = date.getDayOfYear();
        double gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);

        double latRad = Math.toRadians(lat);
        double zenith = Math.toRadians(90.833);
        double cosHa = (Math.cos(zenith) / (Math.cos(latRad) * Math.cos(decl))) - Math.tan(latRad) * Math.tan(decl);
        cosHa = Math.max(-0.99, Math.min(0.99, cosHa));
        double haDeg = Math.toDegrees(Math.acos(cosHa));

        double tzOffsetHours = zone.getRules().getOffset(date.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC)).getTotalSeconds() / 3600.0;
        double solarNoonMin = 720.0 - 4.0 * lon - eqTime + tzOffsetHours * 60.0;
        int sunrise = (int) Math.round(solarNoonMin - 4.0 * haDeg);
        int sunset = (int) Math.round(solarNoonMin + 4.0 * haDeg);
        sunrise = Math.max(240, Math.min(540, sunrise));
        sunset = Math.max(960, Math.min(1260, sunset));
        return new int[]{sunrise, sunset};
    }

    /**
     * Computes local Moonrise and Moonset in minutes from civil midnight for (lat, lon, zone)
     * across the 24-hour Hindu Ahoratra (from today's Sunrise to next day's Sunrise)
     * by sampling lunar altitude and interpolating horizon crossings.
     */
    private static int[] computeMoonriseMoonset(LocalDate date, int sunriseMin, double lat, double lon, ZoneId zone) {
        long startEpochSec = date.atStartOfDay(zone).toEpochSecond();
        double jd0 = toJulianDay(startEpochSec);
        int riseMin = -1;
        int setMin = -1;

        int startMin = Math.max(0, sunriseMin);
        int endMin = startMin + 1440;
        double prevAlt = lunarAltitudeDeg(jd0 + (startMin / 1440.0), lat, lon) - 0.125;
        int stepMinutes = 10;
        for (int m = startMin + stepMinutes; m <= endMin; m += stepMinutes) {
            double jd = jd0 + (m / 1440.0);
            double currAlt = lunarAltitudeDeg(jd, lat, lon) - 0.125;
            if (prevAlt <= 0.0 && currAlt > 0.0 && riseMin < 0) {
                double frac = -prevAlt / (currAlt - prevAlt);
                riseMin = (int) Math.round((m - stepMinutes) + frac * stepMinutes);
            } else if (prevAlt > 0.0 && currAlt <= 0.0 && setMin < 0) {
                double frac = prevAlt / (prevAlt - currAlt);
                setMin = (int) Math.round((m - stepMinutes) + frac * stepMinutes);
            }
            prevAlt = currAlt;
        }
        return new int[]{riseMin, setMin};
    }

    private static double lunarAltitudeDeg(double jd, double lat, double lon) {
        double T = (jd - 2451545.0) / 36525.0;
        double lam = Math.toRadians(lunarLongitude(jd));
        double beta = Math.toRadians(lunarLatitude(jd));
        double eps = Math.toRadians(23.439291 - 0.0130042 * T);

        double sinDec = Math.sin(beta) * Math.cos(eps) + Math.cos(beta) * Math.sin(eps) * Math.sin(lam);
        double dec = Math.asin(Math.max(-1.0, Math.min(1.0, sinDec)));
        double y = Math.sin(lam) * Math.cos(eps) - Math.tan(beta) * Math.sin(eps);
        double x = Math.cos(lam);
        double ra = Math.atan2(y, x); // radians

        // Greenwich Mean Sidereal Time in degrees
        double gmst = norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0));
        double lstRad = Math.toRadians(norm360(gmst + lon));
        double ha = lstRad - ra;

        double latRad = Math.toRadians(lat);
        double sinAlt = Math.sin(latRad) * Math.sin(dec) + Math.cos(latRad) * Math.cos(dec) * Math.cos(ha);
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sinAlt))));
    }

    static double toJulianDay(long epochSecond) {
        return (epochSecond / 86400.0) + 2440587.5;
    }

    static long fromJulianDayToEpochSec(double jd) {
        return Math.round((jd - 2440587.5) * 86400.0);
    }

    static double solarLongitude(double jd) {
        double T = (jd - 2451545.0) / 36525.0;
        double L0 = 280.46646 + 36000.76983 * T + 0.0003032 * T * T;
        double M = 357.52911 + 35999.05029 * T - 0.0001537 * T * T;
        double Mrad = Math.toRadians(norm360(M));
        double C = (1.914602 - 0.004817 * T - 0.000014 * T * T) * Math.sin(Mrad)
                + (0.019993 - 0.000101 * T) * Math.sin(2 * Mrad)
                + 0.000289 * Math.sin(3 * Mrad);
        return norm360(L0 + C);
    }

    static double lunarLongitude(double jd) {
        double T = (jd - 2451545.0) / 36525.0;
        double Lp = norm360(218.3164477 + 481267.88123421 * T - 0.0015786 * T * T);
        double D = Math.toRadians(norm360(297.8501921 + 445267.1114034 * T - 0.0018819 * T * T));
        double M = Math.toRadians(norm360(357.5291092 + 35999.0502909 * T - 0.0001536 * T * T));
        double Mp = Math.toRadians(norm360(134.9633964 + 477198.8675055 * T + 0.0087414 * T * T));
        double F = Math.toRadians(norm360(93.2720950 + 483202.0175233 * T - 0.0036539 * T * T));

        double corr = 6.288774 * Math.sin(Mp)
                + 1.274027 * Math.sin(2 * D - Mp)
                + 0.658314 * Math.sin(2 * D)
                + 0.213618 * Math.sin(2 * Mp)
                - 0.185116 * Math.sin(M)
                - 0.114332 * Math.sin(2 * F)
                + 0.058793 * Math.sin(2 * D - 2 * Mp)
                + 0.057066 * Math.sin(2 * D - M - Mp)
                + 0.053322 * Math.sin(2 * D + Mp)
                + 0.045758 * Math.sin(2 * D - M)
                - 0.040923 * Math.sin(M - Mp)
                - 0.034720 * Math.sin(D)
                - 0.030383 * Math.sin(M + Mp);
        return norm360(Lp + corr);
    }

    static double lunarLatitude(double jd) {
        double T = (jd - 2451545.0) / 36525.0;
        double D = Math.toRadians(norm360(297.8501921 + 445267.1114034 * T));
        double Mp = Math.toRadians(norm360(134.9633964 + 477198.8675055 * T));
        double F = Math.toRadians(norm360(93.2720950 + 483202.0175233 * T));
        return 5.128122 * Math.sin(F)
                + 0.280602 * Math.sin(Mp + F)
                + 0.277693 * Math.sin(Mp - F)
                + 0.173237 * Math.sin(2 * D - F);
    }

    static double lahiriAyanamsa(double jd) {
        double T = (jd - 2451545.0) / 36525.0;
        return 23.853056 + 1.396042 * T;
    }

    private static double norm360(double deg) {
        double r = deg % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    static String formatEndJd(double endJd, LocalDate baseDate, ZoneId zone) {
        long sec = fromJulianDayToEpochSec(endJd);
        ZonedDateTime zdt = Instant.ofEpochSecond(sec).atZone(zone);
        int mins = zdt.getHour() * 60 + zdt.getMinute();
        String timeStr = formatMinutes12h(mins);
        if (!zdt.toLocalDate().equals(baseDate)) {
            return timeStr + " (+1d)";
        }
        return timeStr;
    }

    static String formatMinutes12h(int totalMinutes) {
        int m = ((totalMinutes % 1440) + 1440) % 1440;
        int hour24 = m / 60;
        int min = m % 60;
        String ampm = hour24 >= 12 ? "PM" : "AM";
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.US, "%d:%02d %s", hour12, min, ampm);
    }

    static String formatMinutes24h(int totalMinutes) {
        int m = ((totalMinutes % 1440) + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    static String formatRange(int startMin, int endMin) {
        int s = ((startMin % 1440) + 1440) % 1440;
        int e = ((endMin % 1440) + 1440) % 1440;
        boolean sPm = (s / 60) >= 12;
        boolean ePm = (e / 60) >= 12;
        int sh = (s / 60) % 12;
        if (sh == 0) sh = 12;
        int eh = (e / 60) % 12;
        if (eh == 0) eh = 12;
        if (sPm == ePm) {
            return String.format(Locale.US, "%d:%02d–%d:%02d %s", sh, s % 60, eh, e % 60, ePm ? "PM" : "AM");
        }
        return String.format(Locale.US, "%d:%02d %s–%d:%02d %s", sh, s % 60, sPm ? "PM" : "AM", eh, e % 60, ePm ? "PM" : "AM");
    }

    static String toDevanagariDigits(int number) {
        String s = Integer.toString(number);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append((char) ('०' + (c - '0')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}

package io.github.gagantk.kannadacalendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** The feed address, the iCalendar reader and the sorting of a day's free text, on made-up days. */
public class CalendarTest {
    private static final String ID = "abc123%40group.calendar.google.com";

    @Test public void theOldGoogleFeedAddressBecomesTheIcalOne() {
        String expected = "https://calendar.google.com/calendar/ical/" + ID + "/private-0f1e/basic.ics";
        assertEquals(expected, FeedUrl.normalise("https://www.google.com/calendar/feeds/" + ID + "/private-0f1e/basic"));
        assertEquals(expected, FeedUrl.normalise("http://www.google.com/calendar/feeds/" + ID + "/private-0f1e/basic"));
    }

    @Test public void theAddToCalendarLinkIsUnwrapped() {
        String expected = "https://calendar.google.com/calendar/ical/" + ID + "/private-0f1e/basic.ics";
        assertEquals(expected, FeedUrl.normalise("http://www.google.com/calendar/render?cid=https://www.google.com/calendar/feeds/"
                + ID + "/private-0f1e/basic"));
        assertEquals(expected, FeedUrl.normalise("https://www.google.com/calendar/render?cid="
                + "https%3A%2F%2Fwww.google.com%2Fcalendar%2Ffeeds%2Fabc123%2540group.calendar.google.com%2Fprivate-0f1e%2Fbasic"));
    }

    @Test public void anIcalAddressAndWebcalAreKeptAndAnythingElseIsRefused() {
        String ical = "https://calendar.google.com/calendar/ical/" + ID + "/private-0f1e/basic.ics";
        assertEquals(ical, FeedUrl.normalise(ical));
        assertEquals("https://example.org/a.ics", FeedUrl.normalise("webcal://example.org/a.ics"));
        assertNull(FeedUrl.normalise(""));
        assertNull(FeedUrl.normalise(null));
        assertNull(FeedUrl.normalise("ftp://example.org/a.ics"));
        assertNull(FeedUrl.normalise("https://example.org/a b.ics"));
        assertNull(FeedUrl.normalise("https://example.org/a'; rm -rf /"));
    }

    private static List<Entry> read(String ics, LocalDate from, LocalDate to) throws IOException {
        List<Entry> out = new ArrayList<>();
        IcsReader.read(new ByteArrayInputStream(ics.getBytes(StandardCharsets.UTF_8)), from, to, out);
        return out;
    }

    private static final String ICS = String.join("\r\n",
            "BEGIN:VCALENDAR",
            "BEGIN:VEVENT",
            "DTSTART;VALUE=DATE:20261121",
            "SUMMARY:Sharad Ritu\\, Karthika Maasa\\, Shukla Paksha",
            "DESCRIPTION:Ekadashi Upto 2:10 AM\\nUttarabhadra Upto 3:42 AM\\nRahukalam 9:12 AM-10:38 AM",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART;VALUE=DATE:20261121",
            "SUMMARY:First Festival\\, Second Festival\\, A very long third",
            "  festival name that was folded",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART:20130719T201500Z",
            "SUMMARY:Dwadasi tithi ends",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART;VALUE=DATE:20150101",
            "RRULE:FREQ=YEARLY",
            "SUMMARY:Recurring",
            "END:VEVENT",
            "BEGIN:VEVENT",
            "DTSTART;VALUE=DATE:20300101",
            "SUMMARY:Far away",
            "END:VEVENT",
            "END:VCALENDAR",
            "");

    @Test public void theReaderKeepsOnlyTheWindowAndUnfoldsLines() throws IOException {
        List<Entry> entries = read(ICS, LocalDate.of(2013, 1, 1), LocalDate.of(2027, 12, 31));
        assertEquals(3, entries.size());
        assertEquals("First Festival, Second Festival, A very long third festival name that was folded",
                entries.get(1).summary);
        assertEquals("Ekadashi Upto 2:10 AM\nUttarabhadra Upto 3:42 AM\nRahukalam 9:12 AM-10:38 AM",
                entries.get(0).description);
    }

    @Test public void aTimeInUtcLandsOnTheIndianDayAndClock() throws IOException {
        Entry timed = read(ICS, LocalDate.of(2013, 1, 1), LocalDate.of(2027, 12, 31)).get(2);
        assertEquals(LocalDate.of(2013, 7, 20), timed.date);
        assertEquals("01:45", timed.time);
    }

    @Test public void theWindowCutsBothEnds() throws IOException {
        assertEquals(2, read(ICS, LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21)).size());
        assertEquals(0, read(ICS, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)).size());
    }

    @Test public void aDaySplitsIntoItsFacts() throws IOException {
        Book book = new Book(read(ICS, LocalDate.of(2013, 1, 1), LocalDate.of(2027, 12, 31)));
        Day day = book.day(LocalDate.of(2026, 11, 21));
        assertEquals("Sharad Ritu · Karthika Maasa · Shukla Paksha", day.header);
        assertEquals("Ekadashi till 2:10 AM", day.tithi);
        assertEquals("Uttarabhadra till 3:42 AM", day.nakshatra);
        assertEquals("9:12 AM-10:38 AM", day.rahu);
        assertEquals(3, day.festivals.size());
        assertEquals("First Festival", day.festivals.get(0));
        assertTrue(book.covers(LocalDate.of(2026, 11, 21)));
        assertFalse(book.covers(LocalDate.of(2026, 11, 22)));
        assertTrue(book.day(LocalDate.of(2026, 11, 22)).isEmpty());
    }

    @Test public void theOlderShapesAreRecognisedToo() {
        LocalDate d = LocalDate.of(2017, 2, 7);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(d, "", "Shishira Rtu Magha Masa Shukla Paksha, Jaya Ekadashi, Some Aradhana", ""));
        entries.add(new Entry(d, "", "Ashtami tithi Upto 7.53 a.m.", ""));
        entries.add(new Entry(d, "", "Uttarashadha Star  Upto 7.07 a.m.", ""));
        entries.add(new Entry(d, "", "Rahukalam 10.37-12.04 p.m.", ""));
        entries.add(new Entry(d, "04:20", "Navami tithi ends", ""));
        Day day = Day.of(d, entries);
        assertEquals("Shishira Rtu Magha Masa Shukla Paksha", day.header);
        assertEquals("Ashtami till 7.53 a.m., Navami ends 04:20", day.tithi);
        assertEquals("Uttarashadha till 7.07 a.m.", day.nakshatra);
        assertEquals("10.37-12.04 p.m.", day.rahu);
        assertEquals(2, day.festivals.size());
        assertEquals("Jaya Ekadashi", day.festivals.get(0));
    }

    @Test public void aTimeSetWithAnOddSpaceIsStillRead() {
        LocalDate d = LocalDate.of(2019, 3, 10);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(d, "", "Ashwini Star Nxt.Day 12.36 AM", ""));
        entries.add(new Entry(d, "", "Navami Upto 3:10 PM", ""));
        Day day = Day.of(d, entries);
        assertEquals("Navami till 3:10 PM", day.tithi);
        assertTrue(day.nakshatra.startsWith("Ashwini"));
        assertTrue(day.festivals.isEmpty());
    }

    /** Android's regex engine refuses flags the desktop JVM takes, (?U) among them: keep to the common ones. */
    @Test public void patternsUseOnlyFlagsAndroidAccepts() throws Exception {
        java.nio.file.Path dir = java.nio.file.Paths.get("src/main/java/io/github/gagantk/kannadacalendar");
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
                String src = new String(java.nio.file.Files.readAllBytes(f), StandardCharsets.UTF_8);
                assertFalse(f.getFileName() + " uses a regex flag Android refuses",
                        src.matches("(?s).*\\(\\?[a-zA-Z]*U[a-zA-Z]*\\).*"));
            }
        }
    }

    @Test public void festivalShortFormsAreWrittenOut() {
        assertEquals("Kudligi Car Festival", Festival.expand("Kudligi CF"));
        assertEquals("Hubli Krishnendra Swamy Aradhana", Festival.expand("Hubli Krishnendra Swamy Ara."));
        assertEquals("International Day for Tolerance", Festival.expand("Intl. Day for Tolerance"));
        assertEquals("Ascent CFL", Festival.expand("Ascent CFL"));
    }

    @Test public void festivalsAreSortedByKind() {
        assertEquals(Festival.Kind.TEMPLE, Festival.kind("Kudligi CF"));
        assertEquals(Festival.Kind.TEMPLE, Festival.kind("Udupi Lakshadeepotsav"));
        assertEquals(Festival.Kind.SAINT, Festival.kind("Sant Namadev Jayanti"));
        assertEquals(Festival.Kind.SAINT, Festival.kind("Lakshmipatitirtha Aradhana"));
        assertEquals(Festival.Kind.VRATA, Festival.kind("Kaushika Dwadashi"));
        assertEquals(Festival.Kind.VRATA, Festival.kind("Krita Yugadi"));
        assertEquals(Festival.Kind.FESTIVAL, Festival.kind("Utthana Dwadashi"));
        assertEquals(Festival.Kind.FESTIVAL, Festival.kind("Chandramana Ugadi"));
        assertEquals(Festival.Kind.FESTIVAL, Festival.kind("Deepavali Amavasya"));
        assertEquals(Festival.Kind.FESTIVAL, Festival.kind("Republic Day"));
        assertEquals(Festival.Kind.DAY, Festival.kind("World Television Day"));
        assertEquals(Festival.Kind.OTHER, Festival.kind("4th Saturday"));
        assertEquals(Festival.Kind.OTHER, Festival.kind("Holy Saturday"));
    }

    @Test public void aDaysListedHolidayIsAFestivalAndComesFirst() {
        LocalDate d = LocalDate.of(2026, 11, 25);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(d, "", "Hejamadi Lakshminarayana, Nanjangud CF, Huttari", ""));
        entries.add(new Entry(d, "", "Huttari", "", "restricted"));
        Day day = Day.of(d, entries);
        assertEquals(Festival.Kind.FESTIVAL, Festival.kind("Huttari", day));

        LocalDate r = LocalDate.of(2027, 1, 26);
        entries.clear();
        entries.add(new Entry(r, "", "Thyagaraja Aradhana, Deepavali, International Customs Day", ""));
        entries.add(new Entry(r, "", "Republic Day", "", "public"));
        Festival.Ranked ranked = Festival.ranked(Day.of(r, entries));
        assertEquals(1, ranked.holidays);
        assertEquals("Republic Day", ranked.names.get(0));
        assertEquals("Deepavali", ranked.names.get(1));
        assertEquals("Thyagaraja Aradhana", ranked.names.get(2));
        assertEquals(4, ranked.names.size());
    }

    private static Entry panchanga(LocalDate d, String tithi, String star) {
        return new Entry(d, "", "Hemanta Ritu, Pushya Maasa, Shukla Paksha", tithi + "\n" + star + "\nRahukalam 9:12 AM-10:38 AM");
    }

    @Test public void theTithiOfTheDayIsTheOneInForceAtSunrise() {
        List<Entry> e = new ArrayList<>();
        LocalDate d20 = LocalDate.of(2026, 11, 20), d21 = d20.plusDays(1), d22 = d20.plusDays(2), d23 = d20.plusDays(3);
        e.add(panchanga(d20, "Dashami Upto 2:21 AM", "Poorvabhadra Upto 3:19 AM"));
        e.add(panchanga(d21, "Ekadashi Upto 2:10 AM", "Uttarabhadra Upto 3:42 AM"));
        e.add(panchanga(d22, "Dwadashi Upto 1:33 AM", "Revati Upto 3:41 AM"));
        e.add(panchanga(d23, "Trayodashi Upto 12:23 AM, Chaturdashi Upto 10:55 PM", "Ashwini Upto 3:07 AM"));
        e.add(panchanga(d23.plusDays(1), "Poornima Upto 9:04 PM", "Bharani Upto 2:17 AM"));
        Book book = new Book(e);

        // Ekadashi ended at 2:10 in the night: the 21st is Dwadashi, which runs to 1:33 AM on the 22nd.
        Sunrise.Span s = Sunrise.tithi(book, d21);
        assertEquals("Dwadashi", s.name);
        assertEquals("till 1:33 AM", s.till);
        assertEquals("next day", s.after);
        assertEquals("Revati", Sunrise.nakshatra(book, d21).name);
        // Two tithis end on the 23rd; the second is the day's, and Poornima follows it.
        s = Sunrise.tithi(book, d23);
        assertEquals("Chaturdashi", s.name);
        assertEquals("till 10:55 PM", s.till);
        assertEquals("then Poornima", s.after);
    }

    @Test public void aTithiLastingTheWholeDayIsThatDaysAndNotTheNext() {
        List<Entry> e = new ArrayList<>();
        LocalDate d7 = LocalDate.of(2027, 1, 7), d8 = d7.plusDays(1), d9 = d7.plusDays(2);
        e.add(panchanga(d7, "Amavasya Upto Whole Day", "Moola Upto 6:09 AM"));
        e.add(panchanga(d8, "Amavasya Upto 12:59 AM", "Poorvashadha Upto 8:52 AM"));
        e.add(panchanga(d9, "Pratipat Upto 3:12 AM", "Uttarashadha Upto 11:28 AM"));
        Book book = new Book(e);
        assertEquals("Amavasya", Sunrise.tithi(book, d7).name);
        assertEquals("Pratipat", Sunrise.tithi(book, d8).name);
        // Moola ends at 6:09, before the mid-January sunrise (about 6:47).
        assertEquals("Poorvashadha", Sunrise.nakshatra(book, d7).name);
    }

    @Test public void sunriseAndClockTimesAreRead() {
        assertEquals(java.time.LocalTime.of(1, 33), Sunrise.time("till 1:33 AM"));
        assertEquals(java.time.LocalTime.of(22, 55), Sunrise.time("till 10:55 PM"));
        assertEquals(java.time.LocalTime.of(0, 23), Sunrise.time("till 12:23 AM"));
        assertEquals(java.time.LocalTime.of(12, 5), Sunrise.time("till 12:05 PM"));
        assertEquals(null, Sunrise.time("till Whole Day"));
        assertTrue(Sunrise.at(LocalDate.of(2027, 1, 15)).isAfter(java.time.LocalTime.of(6, 40)));
        assertTrue(Sunrise.at(LocalDate.of(2026, 6, 5)).isBefore(java.time.LocalTime.of(6, 5)));
    }

    @Test public void aTileSplitsNameAndTime() {
        assertEquals("Ekadashi", Day.until("Ekadashi till 2:10 AM")[0]);
        assertEquals("till 2:10 AM", Day.until("Ekadashi till 2:10 AM")[1]);
        assertEquals("Navami", Day.until("Navami ends 04:20")[0]);
        assertEquals("Ashwini Star Nxt.Day 12.36 AM", Day.until("Ashwini Star Nxt.Day 12.36 AM")[0]);
    }

    @Test public void theHolidayListsAreReadFromTheLocation() throws IOException {
        String ics = String.join("\r\n",
                "BEGIN:VCALENDAR",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261101", "SUMMARY:Kannada Rajyotsava -", "LOCATION:Public Holidays", "END:VEVENT",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261125", "SUMMARY:Huttari", "LOCATION:Restricted Holiday", "END:VEVENT",
                "BEGIN:VEVENT", "DTSTART;VALUE=DATE:20261125", "SUMMARY:Some Utsav", "END:VEVENT",
                "END:VCALENDAR", "");
        Book book = new Book(read(ics, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        Day rajyotsava = book.day(LocalDate.of(2026, 11, 1));
        assertEquals(1, rajyotsava.publicHolidays.size());
        assertEquals("Kannada Rajyotsava", rajyotsava.publicHolidays.get(0));
        Day huttari = book.day(LocalDate.of(2026, 11, 25));
        assertTrue(huttari.publicHolidays.isEmpty());
        assertEquals("Huttari", huttari.restrictedHolidays.get(0));
        assertEquals("", IcsReader.holiday("Mysore"));
    }

    @Test public void mainFestivalsGetTheirMarks() {
        LocalDate d = LocalDate.of(2026, 11, 27);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(d, "", "Sankashta Chaturthi, Kaginele Utsav", ""));
        assertEquals(Symbols.GANESHA, Symbols.of(Day.of(d, entries)).get(0));
        entries.clear();
        entries.add(new Entry(d, "", "Sankasht Chaturthi (M.R 8:47 pm)", ""));
        assertEquals(Symbols.GANESHA, Symbols.of(Day.of(d, entries)).get(0));
        entries.clear();
        entries.add(new Entry(d, "", "Ganesh Chaturthi", ""));
        assertEquals(Symbols.GANESHA, Symbols.of(Day.of(d, entries)).get(0));
        // The monthly Vinayaki Chaturthi and Ganapati temples' festivals carry no Ganesha.
        entries.clear();
        entries.add(new Entry(d, "", "Vinayaki Chaturthi, Magadi Ganapati CF, B'lore RPC Sankashtahara Ganapati Utsav", ""));
        assertTrue(Symbols.of(Day.of(d, entries)).isEmpty());
        entries.clear();
        entries.add(new Entry(d, "", "Deepavali, Naraka Chaturdashi", ""));
        assertEquals("🪔", Symbols.of(Day.of(d, entries)).get(0));
        entries.clear();
        entries.add(new Entry(d, "", "Kudligi CF, Some Aradhana", ""));
        assertTrue(Symbols.of(Day.of(d, entries)).isEmpty());
        entries.clear();
        entries.add(new Entry(d, "", "Kannada Rajyotsava", ""));
        assertEquals(Symbols.KARNATAKA, Symbols.of(Day.of(d, entries)).get(0));
        entries.clear();
        entries.add(new Entry(d, "", "Utthana Ekadashi, Maha Shivaratri, Chandramana Ugadi, Sri Rama Navami", ""));
        assertTrue("these festivals carry no mark", Symbols.of(Day.of(d, entries)).isEmpty());
    }

    @Test public void theDesktopLinkMarkupIsDropped() {
        assertEquals("Pradosha", IcsReader.clean(IcsReader.unescape("Pradosha -\\\\n[[https://example.org/p][(for more, hold down shift)]]")));
    }

    @Test public void textSavedInTheWrongEncodingIsRepaired() {
        assertEquals("Children’s Day", IcsReader.repair("Childrenâ€™s Day"));
        assertEquals("Children’s Day", IcsReader.repair("Childrenâs Day"));
        assertEquals("Plain text", IcsReader.repair("Plain text"));
        assertEquals("Café â alone", IcsReader.repair("Café â alone"));
    }

    @Test public void tithisAreToldFromNakshatras() {
        for (String t : new String[]{"Pratipat", "Dwitiya", "Chaturthi", "Shashti", "Ashtami", "Ekadashi", "Chaturdashi",
                "Poornima", "Amavasya"}) assertTrue(t, Day.isTithi(t));
        for (String n : new String[]{"Ashwini", "Chitta", "Pushya", "Uttarabhadra", "Poorvabhadra", "Revati", "Ashlesha"}) {
            assertFalse(n, Day.isTithi(n));
        }
    }

    @Test public void timesAreMarkedIstOnlyOnATvInAnotherZone() {
        assertEquals("till 1:33 AM", CalendarActivity.zoned("till 1:33 AM", java.time.ZoneId.of("Asia/Kolkata")));
        assertEquals("till 1:33 AM", CalendarActivity.zoned("till 1:33 AM", java.time.ZoneId.of("Asia/Calcutta")));
        assertEquals("till 1:33 AM IST", CalendarActivity.zoned("till 1:33 AM", java.time.ZoneId.of("Europe/London")));
        assertEquals("to 10:38 AM IST", CalendarActivity.zoned("to 10:38 AM", java.time.ZoneId.of("UTC")));
        assertEquals("", CalendarActivity.zoned("", java.time.ZoneId.of("UTC")));
    }

    @Test public void festivalKindsAreFoundAnywhereInTheName() {
        assertEquals(Festival.Kind.TEMPLE, Festival.kind("Kudligi CF"));
        assertEquals(Festival.Kind.FESTIVAL, Festival.kind("Naraka Chaturdashi"));
        assertEquals(Festival.Kind.SAINT, Festival.kind("Hubli Swamy Ara."));
        assertEquals(Festival.Kind.DAY, Festival.kind("World Television Day"));
        assertEquals("Commencement of Uttarayana", Festival.expand("Com. of Uttarayana"));
    }

    @Test public void theCacheEscapesRoundTrip() {
        String text = "line one\nline\ttwo \\ end";
        assertEquals(text, Cache.unesc(Cache.esc(text)));
        assertFalse(Cache.esc(text).contains("\n"));
        assertFalse(Cache.esc(text).contains("\t"));
    }
}

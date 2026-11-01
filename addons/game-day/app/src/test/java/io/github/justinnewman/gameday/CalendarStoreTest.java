package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

public class CalendarStoreTest {
    @Test public void setupFile() throws Exception {
        List<Calendar> c = CalendarStore.parseSetupFile(
                "# comment\nHockey | Minnesota Wild | https://h.example/a.ics\n\nSoccer|Utah Royals|webcal://h.example/b.ics|Amber, light\n");
        assertEquals(2, c.size());
        assertEquals("Minnesota Wild", c.get(0).name);
        assertEquals(Sport.byLabel("Hockey"), c.get(0).sport);
        assertEquals("https://h.example/a.ics", c.get(0).link);
        assertEquals("Utah Royals", c.get(1).name);
        assertEquals("https://h.example/b.ics", c.get(1).link);
        assertEquals(Palette.byName("Amber, light"), c.get(1).colour);
        assertEquals(12, c.get(0).colour);
        assertEquals(Links.id("https://h.example/a.ics"), c.get(0).id);
    }

    @Test public void omittedColoursAreAllDifferent() throws Exception {
        List<Calendar> c = CalendarStore.parseSetupFile("Hockey|A|https://h.example/a.ics\r\nHockey|B|https://h.example/b.ics");
        assertEquals(12, c.get(0).colour);
        assertEquals(13, c.get(1).colour);
    }

    @Test public void unknownSportNamesTheChoices() {
        try {
            CalendarStore.parseSetupFile("Basketbal | Jazz | https://h.example/c.ics");
            fail();
        } catch (CalendarStore.BadLine e) {
            assertEquals(1, e.line);
            assertTrue(e.getMessage(), e.getMessage().contains("first column"));
            assertTrue(e.getMessage(), e.getMessage().contains("Choose one of: American football"));
        }
    }

    @Test public void badLinksAndColoursAreRefusedWithTheirLine() {
        assertBad("# x\nHockey | Wild | ftp://h.example/a.ics", 2, "link");
        assertBad("Hockey | Wild | https://h.example/a.ics | Chartreuse", 1, "colour");
        assertBad("Hockey | https://h.example/a.ics", 1, "sport | name | link");
        assertBad("Hockey |  | https://h.example/a.ics", 1, "name");
        assertBad("Hockey | " + "W".repeat(41) + " | https://h.example/a.ics", 1, "40");
    }

    @Test public void theSameLinkTwiceIsKeptOnce() throws Exception {
        assertEquals(1, CalendarStore.parseSetupFile("Hockey|A|https://h.example/a.ics\nHockey|B|webcal://h.example/a.ics").size());
    }

    @Test public void errorsNeverQuoteTheLink() {
        try {
            CalendarStore.parseSetupFile("Basketbal | Jazz | https://h.example/secret-token");
            fail();
        } catch (CalendarStore.BadLine e) {
            assertTrue(!e.getMessage().contains("secret-token"));
        }
    }

    @Test public void columnsInTheWrongOrderNeverQuoteTheLink() {
        assertNoLink("https://h.example/secret-token | Wild | Hockey");
        assertNoLink("Hockey | Wild | https://h.example/a.ics | https://h.example/secret-token");
    }

    private static void assertNoLink(String line) {
        try {
            CalendarStore.parseSetupFile(line);
            fail("accepted: " + line);
        } catch (CalendarStore.BadLine e) {
            assertTrue(e.getMessage(), !e.getMessage().contains("secret") && !e.getMessage().contains("://"));
        }
    }

    @Test public void jsonRoundTrip() throws Exception {
        List<Calendar> c = CalendarStore.parseSetupFile("Hockey|Minnesota Wild|https://h.example/a.ics\nGolf|Majors|https://h.example/b.ics|Grey, dark");
        List<Calendar> back = CalendarStore.fromJson(CalendarStore.toJson(c));
        assertEquals(2, back.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(c.get(i).id, back.get(i).id);
            assertEquals(c.get(i).name, back.get(i).name);
            assertEquals(c.get(i).link, back.get(i).link);
            assertEquals(c.get(i).sport, back.get(i).sport);
            assertEquals(c.get(i).colour, back.get(i).colour);
        }
        assertTrue(CalendarStore.fromJson("not json").isEmpty());
    }

    @Test public void moreThanTwentyIsRefused() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 21; i++) b.append("Hockey|T").append(i).append("|https://h.example/").append(i).append(".ics\n");
        assertBad(b.toString(), 21, "20");
    }

    private static void assertBad(String text, int line, String inMessage) {
        try {
            CalendarStore.parseSetupFile(text);
            fail("accepted: " + text);
        } catch (CalendarStore.BadLine e) {
            assertEquals(line, e.line);
            assertTrue(e.getMessage(), e.getMessage().contains(inMessage));
        }
    }
}

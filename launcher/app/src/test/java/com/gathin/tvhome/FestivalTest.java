package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class FestivalTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Test
    public void activeThroughTheLastDayInclusive() {
        Festival f = Festival.parse("Dussehra", "2026-10-21", "", "");
        assertTrue(f.active(DAY));
        assertTrue(f.active(DAY.plusDays(1)));
        assertFalse(f.active(DAY.plusDays(2)));
    }

    @Test
    public void noEndDateStaysOn() {
        assertTrue(Festival.parse("diwali", "", "", "").active(DAY.plusYears(1)));
    }

    @Test
    public void malformedValuesTurnTheSkinOff() {
        assertSame(Festival.NONE, Festival.parse("", "2026-10-21", "", ""));
        assertSame(Festival.NONE, Festival.parse("dussehra", "21/10/2026", "", ""));
        assertFalse(Festival.NONE.active(DAY));
    }

    @Test
    public void customGreetingIsSanitizedAndOverridesTheFestivalDefault() {
        assertEquals("Shubho Bijoya",
                Festival.parse("dussehra", "", "  Shubho\u202E Bijoya ", "").greetingText(null, DAY));
    }

    @Test
    public void unknownFestivalKeepsTheTimeOfDayGreeting() {
        assertEquals("", Festival.parse("onam", "", "", "").greetingText(null, DAY));
    }

    @Test
    public void accentFallsBackToDiyaGold() {
        assertEquals(Festival.DEFAULT_ACCENT, Festival.parse("dussehra", "", "", "").accentColor());
        assertEquals(0xFF102030, Festival.parse("dussehra", "", "", "#102030").accentColor());
        assertEquals(Festival.DEFAULT_ACCENT, Festival.parse("dussehra", "", "", "gold").accentColor());
    }

    @Test
    public void halloweenHasItsOwnSkinAndPumpkinAccent() {
        Festival f = Festival.parse("Halloween", "2026-11-01", "", "");
        assertTrue(f.isHalloween());
        assertTrue(f.active(LocalDate.of(2026, 10, 31)));
        assertTrue(f.active(LocalDate.of(2026, 11, 1)));
        assertFalse(f.active(LocalDate.of(2026, 11, 2)));
        assertEquals(Festival.PUMPKIN, f.accentColor());
        assertEquals(0xFFAA5500, Festival.parse("halloween", "", "", "#AA5500").accentColor());
    }

    @Test
    public void otherFestivalsKeepTheDiyaSkin() {
        assertFalse(Festival.parse("dussehra", "", "", "").isHalloween());
        assertFalse(Festival.parse("onam", "", "", "").isHalloween());
        assertFalse(Festival.NONE.isHalloween());
    }

    @Test
    public void christmasHasItsOwnSkinAndRedAccent() {
        Festival f = Festival.parse("Christmas", "2027-01-06", "", "");
        assertTrue(f.isChristmas());
        assertFalse(f.isHalloween());
        assertTrue(f.active(LocalDate.of(2027, 1, 6)));
        assertFalse(f.active(LocalDate.of(2027, 1, 7)));
        assertEquals(Festival.CHRISTMAS_RED, f.accentColor());
        assertEquals(0xFFAA0000, Festival.parse("christmas", "", "", "#AA0000").accentColor());
        assertFalse(Festival.parse("halloween", "", "", "").isChristmas());
        assertFalse(Festival.parse("dussehra", "", "", "").isChristmas());
        assertFalse(Festival.NONE.isChristmas());
    }

    @Test
    public void christmasGreetingFollowsTheDate() {
        assertEquals(0, Festival.christmasSlot(LocalDate.of(2026, 11, 26)));   // Thanksgiving 2026
        assertEquals(0, Festival.christmasSlot(LocalDate.of(2027, 11, 25)));   // Thanksgiving 2027
        assertEquals(-1, Festival.christmasSlot(LocalDate.of(2026, 11, 19)));  // a Thursday, but the third
        assertEquals(1, Festival.christmasSlot(LocalDate.of(2026, 12, 8)));
        assertEquals(2, Festival.christmasSlot(LocalDate.of(2026, 12, 25)));
        assertEquals(3, Festival.christmasSlot(LocalDate.of(2026, 12, 26)));
        assertEquals(4, Festival.christmasSlot(LocalDate.of(2026, 12, 31)));
        assertEquals(5, Festival.christmasSlot(LocalDate.of(2027, 1, 1)));
        assertEquals(6, Festival.christmasSlot(LocalDate.of(2027, 1, 6)));
        assertEquals(-1, Festival.christmasSlot(LocalDate.of(2026, 12, 24)));
        assertEquals(-1, Festival.christmasSlot(LocalDate.of(2027, 1, 7)));
    }

    private static List<Locale> locales(String... tags) {
        List<Locale> out = new ArrayList<>();
        for (String tag : tags) out.add(Locale.forLanguageTag(tag));
        return out;
    }

    private static final String IT =
            "|Buona Immacolata|Buon Natale|Buon Santo Stefano|Buon San Silvestro|Buon Capodanno|Buona Befana|it";
    private static final String EN = "Happy Thanksgiving||Merry Christmas|Happy Boxing Day|Happy New Year's Eve"
            + "|Happy New Year|Happy Epiphany|en";

    @Test
    public void emptyOrMissingSlotsKeepTheTimeOfDayGreeting() {
        assertEquals("Buon Natale", Festival.pick(IT, 2, locales("it-IT")));
        assertEquals("Buona Befana", Festival.pick(IT, 6, locales("it")));
        assertEquals("", Festival.pick(IT, 0, locales("it")));
        assertEquals("", Festival.pick(IT, -1, locales("it")));
        assertEquals("", Festival.pick("||||||", 3, locales("it")));
        assertEquals("", Festival.pick("", 2, locales("it")));
    }

    @Test
    public void aLanguageWithoutATranslationKeepsItsTimeOfDayGreeting() {
        assertEquals("Merry Christmas", Festival.pick(EN, 2, locales("en-GB")));
        assertEquals("", Festival.pick(EN, 2, locales("ru")));
        assertEquals("x", Festival.pick("||x|||||iw", 2, locales("he")));
        assertEquals("x", Festival.pick("||x|||||in", 2, locales("id")));
    }

    @Test
    public void theFirstLocaleWithATranslationWins() {
        assertEquals("Buon Natale", Festival.pick(IT, 2, locales("ru", "it")));
        assertEquals("", Festival.pick(IT, 2, locales("ru", "en")));
    }

    @Test
    public void thanksgivingIsOnlyForEnglishUs() {
        assertEquals("Happy Thanksgiving", Festival.pick(EN, 0, locales("en-US")));
        assertEquals("", Festival.pick(EN, 0, locales("en-GB")));
        assertEquals("", Festival.pick(EN, 0, locales("en-AU")));
        assertEquals("", Festival.pick(EN, 0, locales("en")));
        assertEquals("Happy Thanksgiving", Festival.pick(EN, 0, locales("de", "en-US")));
    }

    @Test
    public void aThemesOwnGreetingBeatsTheChristmasOnes() {
        assertEquals("Ciao", Festival.parse("christmas", "", "Ciao", "").greetingText(null, LocalDate.of(2026, 12, 25)));
    }
}

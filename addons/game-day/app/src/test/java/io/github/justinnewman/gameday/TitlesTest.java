package io.github.justinnewman.gameday;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class TitlesTest {
    @Test public void cleanRemovesLeadingEmojiOnly() {
        assertEquals("Dallas Stars @ Minnesota Wild", Titles.clean("🏒 Dallas Stars @ Minnesota Wild"));
        assertEquals("The Masters", Titles.clean("🏌️ The Masters"));
        assertEquals("2027 NHL Draft", Titles.clean("  🏒  2027 NHL Draft "));
        assertEquals("", Titles.clean("🏒"));
        assertEquals("Wild 🏒 night", Titles.clean("Wild 🏒 night"));
    }

    @Test public void separators() {
        assertEquals("Minnesota Wild at Dallas Stars", Titles.topLine("🏒 Minnesota Wild @ Dallas Stars", "NHL"));
        assertEquals("Minnesota Wild at Dallas Stars", Titles.topLine("Minnesota Wild at Dallas Stars", "NHL"));
        assertEquals("Portland Thorns at Utah Royals", Titles.topLine("Utah Royals vs Portland Thorns", "NWSL"));
        assertEquals("Portland Thorns at Utah Royals", Titles.topLine("Utah Royals v. Portland Thorns", "NWSL"));
        assertArrayEquals(new String[] {"Minnesota Wild", "Dallas Stars"}, Titles.matchup("Minnesota Wild @ Dallas Stars"));
        assertArrayEquals(new String[] {"Portland Thorns", "Utah Royals"}, Titles.matchup("Utah Royals VS Portland Thorns"));
    }

    @Test public void decoratedTitles() {
        assertEquals("Minnesota Wild at Dallas Stars (Preseason)", Titles.topLine("🏒 Minnesota Wild @ Dallas Stars (Preseason)", "NHL"));
        assertEquals("Portland Thorns at Utah Royals - NWSL", Titles.topLine("Utah Royals vs. Portland Thorns - NWSL", "x"));
        assertEquals("WILD at STARS", Titles.topLine("WILD AT STARS", "x"));
    }

    @Test public void notAMatchup() {
        assertEquals("2027 NHL Draft", Titles.topLine("🏒 2027 NHL Draft", "NHL"));
        assertEquals("Mexico City Grand Prix - Qualifying", Titles.topLine("Mexico City Grand Prix - Qualifying", "F1"));
        assertNull(Titles.matchup("Pat at the Ballpark Night"));
        assertNull(Titles.matchup("Welcome to the Minnesota Wild calendar!"));
    }

    @Test public void emojiOnlyTitleUsesCalendarName() {
        assertEquals("Minnesota Wild", Titles.topLine("🏒", "Minnesota Wild"));
        assertEquals("Minnesota Wild", Titles.topLine("", "Minnesota Wild"));
    }

    @Test public void words() {
        Sport hockey = Sport.byLabel("Hockey");
        Sport f1 = Sport.byLabel("Motor racing, light start");
        Sport golf = Sport.byLabel("Golf");
        Sport tennis = Sport.byLabel("Tennis");
        assertEquals("Puck drop", Titles.word(hockey, "🏒 Minnesota Wild @ Dallas Stars"));
        assertEquals("Starts", Titles.word(hockey, "🏒 2027 NHL Draft"));
        assertEquals("Lights out", Titles.word(f1, "Mexico City Grand Prix"));
        assertEquals("Starts", Titles.word(f1, "Mexico City Grand Prix - Qualifying"));
        assertEquals("Starts", Titles.word(f1, "FP2 Mexico City"));
        assertEquals("Starts", Titles.word(f1, "Sprint Qualifying, Austin"));
        assertEquals("Lights out", Titles.word(f1, "Sprint, Austin"));
        // Titles as a real F1 calendar writes them.
        assertEquals("Starts", Titles.word(f1, "🏎 FORMULA 1 GRAN PREMIO DE LA CIUDAD DE MÉXICO 2026 - Practice 1"));
        assertEquals("Starts", Titles.word(f1, "⏱️ FORMULA 1 MSC CRUISES GRANDE PRÊMIO DE SÃO PAULO 2026 - Sprint Qualification"));
        assertEquals("Lights out", Titles.word(f1, "🏁 FORMULA 1 MSC CRUISES GRANDE PRÊMIO DE SÃO PAULO 2026 - Sprint Race"));
        assertEquals("Lights out", Titles.word(f1, "🏁 FORMULA 1 GRAN PREMIO DE LA CIUDAD DE MÉXICO 2026 - Race"));
        assertEquals("Tee time", Titles.word(golf, "The Masters, Round 1"));
        assertEquals("First serve", Titles.word(tennis, "Sinner vs Alcaraz"));
    }

    @Test public void likelyTeam() {
        assertEquals("Minnesota Wild", Titles.likelyTeam(Arrays.asList(
                "🏒 Minnesota Wild @ Dallas Stars", "🏒 Colorado Avalanche @ Minnesota Wild", "🏒 2027 NHL Draft")));
        assertNull(Titles.likelyTeam(Collections.singletonList("🏒 2027 NHL Draft")));
    }
}

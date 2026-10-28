package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ScheduleTest {
    private static final Instant NOW = Instant.parse("2027-04-02T12:00:00Z");
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final Calendar WILD = cal("Minnesota Wild", "Hockey", 16);
    private static final Calendar STARS = cal("Dallas Stars", "Hockey", 28);
    private static final Calendar ROYALS = cal("Utah Royals", "Soccer", 14);

    private static Calendar cal(String name, String sport, int colour) {
        String link = "https://h.example/" + name.replace(' ', '-');
        return new Calendar(Links.id(link), name, link, Sport.byLabel(sport), colour);
    }

    private static Ics.Event ev(String title, Duration fromNow, Duration length) {
        Instant start = NOW.plus(fromNow);
        return new Ics.Event(title, start, length == null ? null : start.plus(length), false);
    }

    private static List<Schedule.Turn> turns(Map<String, List<Ics.Event>> events, Calendar... calendars) {
        return Schedule.turns(Arrays.asList(calendars), events, NOW, UTC);
    }

    private static Map<String, List<Ics.Event>> events(Object... calendarAndEvents) {
        Map<String, List<Ics.Event>> m = new HashMap<>();
        for (int i = 0; i < calendarAndEvents.length; i += 2) {
            @SuppressWarnings("unchecked") List<Ics.Event> list = (List<Ics.Event>) calendarAndEvents[i + 1];
            m.put(((Calendar) calendarAndEvents[i]).id, list);
        }
        return m;
    }

    @Test public void oneTurnPerCalendarItsNextEntry() {
        List<Schedule.Turn> t = turns(events(WILD, Arrays.asList(
                ev("🏒 Minnesota Wild @ Dallas Stars", Duration.ofDays(2), Duration.ofHours(3)),
                ev("🏒 Colorado Avalanche @ Minnesota Wild", Duration.ofHours(1), Duration.ofHours(3)))), WILD);
        assertEquals(1, t.size());
        Schedule.Turn turn = t.get(0);
        assertEquals("Colorado Avalanche at Minnesota Wild", turn.topLine);
        assertEquals("Puck drop", turn.word);
        assertEquals(NOW.plus(Duration.ofHours(1)).toEpochMilli(), turn.startMs);
        assertEquals(NOW.plus(Duration.ofHours(4)).toEpochMilli(), turn.endMs);
        assertEquals(Palette.colour(16), turn.colour);
        assertEquals("Minnesota Wild", turn.calendar);
    }

    @Test public void liveEntryStaysUntilItsEnd() {
        List<Schedule.Turn> t = turns(events(WILD, Arrays.asList(
                ev("🏒 Minnesota Wild @ Dallas Stars", Duration.ofHours(-1), Duration.ofHours(3)),
                ev("🏒 Minnesota Wild @ Utah Mammoth", Duration.ofDays(2), Duration.ofHours(3)))), WILD);
        assertEquals("Minnesota Wild at Dallas Stars", t.get(0).topLine);
    }

    @Test public void endedEntryGivesWayToTheNext() {
        List<Schedule.Turn> t = turns(events(WILD, Arrays.asList(
                ev("🏒 Minnesota Wild @ Dallas Stars", Duration.ofHours(-3).minusMinutes(5), Duration.ofHours(3)),
                ev("🏒 Minnesota Wild @ Utah Mammoth", Duration.ofDays(2), Duration.ofHours(3)))), WILD);
        assertEquals("Minnesota Wild at Utah Mammoth", t.get(0).topLine);
    }

    @Test public void eightDaysOutIsLeftOut() {
        assertTrue(turns(events(WILD, Arrays.asList(ev("🏒 A @ B", Duration.ofDays(8), Duration.ofHours(3)))), WILD).isEmpty());
        assertEquals(1, turns(events(WILD, Arrays.asList(ev("🏒 A @ B", Duration.ofDays(7).minusMinutes(1), Duration.ofHours(3)))), WILD).size());
    }

    @Test public void pastOnlyCalendarHasNoTurn() {
        assertTrue(turns(events(WILD, Arrays.asList(ev("🏒 A @ B", Duration.ofDays(-30), Duration.ofHours(3)))), WILD).isEmpty());
        assertTrue(turns(events(), WILD).isEmpty());
    }

    @Test public void defaultLengths() {
        Ics.Event noEnd = ev("x", Duration.ZERO, null);
        assertEquals(NOW.plus(Duration.ofHours(3)), Schedule.endOf(noEnd, Sport.byLabel("Hockey"), UTC));
        assertEquals(NOW.plus(Duration.ofHours(2)), Schedule.endOf(noEnd, Sport.byLabel("Motor racing, light start"), UTC));
        assertEquals(NOW.plus(Duration.ofHours(2)), Schedule.endOf(noEnd, Sport.byLabel("Motor racing, flag start"), UTC));
        assertEquals(NOW.plus(Duration.ofHours(3)), Schedule.endOf(noEnd, Sport.byLabel("Darts"), UTC));
        assertEquals(Instant.parse("2027-04-03T00:00:00Z"), Schedule.endOf(noEnd, Sport.byLabel("Golf"), UTC));
        assertEquals(Instant.parse("2027-04-03T06:00:00Z"), Schedule.endOf(noEnd, Sport.byLabel("Golf"), ZoneId.of("America/Denver")));
        Ics.Event withEnd = ev("x", Duration.ZERO, Duration.ofMinutes(150));
        assertEquals(NOW.plus(Duration.ofMinutes(150)), Schedule.endOf(withEnd, Sport.byLabel("Golf"), UTC));
    }

    @Test public void sameGameInTwoCalendarsShowsOnceInTheFirstColour() {
        Ics.Event game = ev("🏒 Minnesota Wild @ Dallas Stars", Duration.ofHours(5), Duration.ofHours(3));
        List<Schedule.Turn> t = turns(events(WILD, Arrays.asList(game), STARS, Arrays.asList(
                new Ics.Event("Minnesota Wild at Dallas Stars", game.start, game.end, false))), WILD, STARS);
        assertEquals(1, t.size());
        assertEquals(Palette.colour(16), t.get(0).colour);
        assertEquals("Minnesota Wild", t.get(0).calendar);
    }

    @Test public void sortedBySoonestStart() {
        List<Schedule.Turn> t = turns(events(
                WILD, Arrays.asList(ev("🏒 Minnesota Wild @ Dallas Stars", Duration.ofDays(3), Duration.ofHours(3))),
                ROYALS, Arrays.asList(ev("⚽ Utah Royals vs Portland Thorns", Duration.ofHours(6), Duration.ofHours(2)))),
                WILD, ROYALS);
        assertEquals(2, t.size());
        assertEquals("Portland Thorns at Utah Royals", t.get(0).topLine);
        assertEquals("Kickoff", t.get(0).word);
        assertEquals("Minnesota Wild at Dallas Stars", t.get(1).topLine);
    }

    @Test public void entriesNeedNotBeInOrder() {
        List<Schedule.Turn> t = turns(events(WILD, Arrays.asList(
                ev("🏒 Late @ Game", Duration.ofDays(4), Duration.ofHours(3)),
                ev("🏒 Early @ Game", Duration.ofDays(1), Duration.ofHours(3)))), WILD);
        assertEquals("Early at Game", t.get(0).topLine);
    }
}

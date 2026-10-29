package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SettingsLogicTest {
    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static Calendar cal(String name) {
        String link = "https://h.example/" + name;
        return new Calendar(Links.id(link), name, link, Sport.byLabel("Hockey"), 12);
    }

    @Test public void colourGridMoves() {
        assertEquals(11, ColourGrid.move(11, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(5, ColourGrid.move(4, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(16, ColourGrid.move(4, KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(4, ColourGrid.move(4, KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(24, ColourGrid.move(24, KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(35, ColourGrid.move(35, KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(23, ColourGrid.move(35, KeyEvent.KEYCODE_DPAD_UP));
    }

    @Test public void calendarEdits() {
        Calendar c = cal("Wild");
        assertEquals("Minnesota Wild", c.withName("Minnesota Wild").name);
        assertEquals(Sport.byLabel("Soccer"), c.withSport(Sport.byLabel("Soccer")).sport);
        assertEquals(28, c.withColour(28).colour);
        Calendar moved = c.withLink("https://h.example/new");
        assertEquals("https://h.example/new", moved.link);
        assertEquals(Links.id("https://h.example/new"), moved.id);
        assertEquals(c.name, moved.name);
    }

    @Test public void ago() {
        long now = 1_800_000_000_000L;
        assertEquals("just now", Ago.text(now, now - 20_000));
        assertEquals("1 minute ago", Ago.text(now, now - MIN));
        assertEquals("45 minutes ago", Ago.text(now, now - 45 * MIN));
        assertEquals("1 hour ago", Ago.text(now, now - HOUR));
        assertEquals("2 hours ago", Ago.text(now, now - 2 * HOUR - 5 * MIN));
        assertEquals("yesterday", Ago.text(now, now - 30 * HOUR));
        assertEquals("3 days ago", Ago.text(now, now - 3 * 24 * HOUR));
        assertEquals("never", Ago.text(now, 0));
    }

    @Test public void countdownLikeHome() {
        // As the launcher's RaceWeekend.countdown: rounded up to the minute, never 0.
        assertEquals("46 min", Countdown.text(45 * MIN + 20_000));
        assertEquals("1 min", Countdown.text(20_000));
        assertEquals("2h 18m", Countdown.text(2 * HOUR + 18 * MIN));
        assertEquals("1d 3h", Countdown.text(27 * HOUR + 10 * MIN));
        assertEquals("1 min", Countdown.text(-5 * MIN));
    }

    @Test public void addReplaceMoveRemove() throws Exception {
        File dir = tmp.newFolder();
        Repository repo = new Repository(dir);
        assertTrue(repo.add(cal("A")));
        assertTrue(repo.add(cal("B")));
        assertFalse("same link twice", repo.add(cal("A")));
        repo.replace(cal("B").id, cal("B").withName("Bee"));
        assertEquals("Bee", repo.calendars().get(1).name);
        repo.moveUp(cal("B").id);
        assertEquals(Arrays.asList("Bee", "A"), names(repo.calendars()));
        repo.moveUp(cal("B").id);
        assertEquals(Arrays.asList("Bee", "A"), names(repo.calendars()));
        repo.remove(cal("A").id);
        assertEquals(Arrays.asList("Bee"), names(repo.calendars()));
    }

    @Test public void atMostTwenty() throws Exception {
        Repository repo = new Repository(tmp.newFolder());
        for (int i = 0; i < CalendarStore.MAX; i++) assertTrue(repo.add(cal("T" + i)));
        assertFalse(repo.add(cal("T20")));
    }

    private static List<String> names(List<Calendar> list) {
        List<String> out = new ArrayList<>();
        for (Calendar c : list) out.add(c.name);
        return out;
    }
}

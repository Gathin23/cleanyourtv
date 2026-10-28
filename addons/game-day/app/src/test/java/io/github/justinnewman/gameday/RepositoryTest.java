package io.github.justinnewman.gameday;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RepositoryTest {
    private static final Instant NOW = Instant.parse("2027-04-02T12:00:00Z");
    private static final ZoneId UTC = ZoneOffset.UTC;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File dir;
    private Repository repo;

    @Before public void setUp() throws Exception {
        dir = tmp.newFolder();
        repo = new Repository(dir);
    }

    private static Calendar cal(String name, String sport, int colour) {
        String link = "https://h.example/" + name.replace(' ', '-');
        return new Calendar(Links.id(link), name, link, Sport.byLabel(sport), colour);
    }

    private void cache(Calendar c, String summary, String start) throws Exception {
        String ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:" + start + "\r\nSUMMARY:" + summary + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        Files.write(Fetcher.cacheFile(dir, c.id).toPath(), ics.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void turnSecondsClampAndStep() {
        assertEquals(30, repo.turnSeconds());
        repo.setTurnSeconds(7);
        assertEquals(10, repo.turnSeconds());
        repo.setTurnSeconds(31);
        assertEquals(30, repo.turnSeconds());
        repo.setTurnSeconds(33);
        assertEquals(35, repo.turnSeconds());
        repo.setTurnSeconds(500);
        assertEquals(120, new Repository(dir).turnSeconds());
    }

    @Test public void calendarsAreSavedAndRead() {
        assertTrue(repo.calendars().isEmpty());
        List<Calendar> list = Arrays.asList(cal("Minnesota Wild", "Hockey", 16), cal("Utah Royals", "Soccer", 14));
        repo.save(list);
        List<Calendar> back = new Repository(dir).calendars();
        assertEquals(2, back.size());
        assertEquals("Utah Royals", back.get(1).name);
    }

    @Test public void turnsComeFromCachedFiles() throws Exception {
        Calendar wild = cal("Minnesota Wild", "Hockey", 16);
        Calendar royals = cal("Utah Royals", "Soccer", 14);
        repo.save(Arrays.asList(wild, royals));
        cache(wild, "🏒 Minnesota Wild @ Dallas Stars", "20270403T000000Z");
        cache(royals, "⚽ Utah Royals vs Portland Thorns", "20270402T200000Z");
        List<Schedule.Turn> t = repo.turns(NOW.toEpochMilli(), UTC);
        assertEquals(2, t.size());
        assertEquals("Portland Thorns at Utah Royals", t.get(0).topLine);
        assertEquals(Palette.colour(14), t.get(0).colour);
        assertEquals(Palette.colour(16), t.get(1).colour);
    }

    @Test public void nextLineDescribesTheNextEntry() throws Exception {
        Calendar wild = cal("Minnesota Wild", "Hockey", 16);
        cache(wild, "🏒 Minnesota Wild @ Dallas Stars", "20270403T010000Z");
        assertEquals("Sat 1:00 AM", repo.nextLine(wild, NOW.toEpochMilli(), UTC));
        assertEquals("nothing in the next 7 days", repo.nextLine(cal("Nobody", "Hockey", 3), NOW.toEpochMilli(), UTC));
    }

    @Test public void titlesForSuggestions() throws Exception {
        Calendar wild = cal("Minnesota Wild", "Hockey", 16);
        cache(wild, "🏒 Minnesota Wild @ Dallas Stars", "20270403T010000Z");
        assertEquals(Arrays.asList("🏒 Minnesota Wild @ Dallas Stars"), repo.titles(wild, NOW.toEpochMilli(), UTC));
        assertTrue(repo.titles(cal("Nobody", "Hockey", 3), NOW.toEpochMilli(), UTC).isEmpty());
    }

    @Test public void nextEventIgnoresTheWeekLimit() throws Exception {
        Calendar wild = cal("Minnesota Wild", "Hockey", 16);
        cache(wild, "🏒 Minnesota Wild @ Dallas Stars", "20270420T010000Z");
        Ics.Event e = repo.nextEvent(wild, NOW.toEpochMilli(), UTC);
        assertEquals("🏒 Minnesota Wild @ Dallas Stars", e.title);
        assertEquals(null, repo.nextEvent(cal("Nobody", "Hockey", 3), NOW.toEpochMilli(), UTC));
    }

    @Test public void resultFileNeverHasALink() {
        String result = repo.applySetupFile("Hockey|Minnesota Wild|http://127.0.0.1:1/secret-token.ics\n"
                + "Soccer|Utah Royals|https://127.0.0.1:1/other-secret.ics", NOW.toEpochMilli(), new Fetcher(300, 300), UTC);
        assertTrue(result, result.contains("fail|Minnesota Wild|Hockey|No answer"));
        assertTrue(result, result.endsWith("done\n"));
        assertFalse(result, result.contains("http"));
        assertFalse(result, result.contains("secret"));
        assertEquals(2, repo.calendars().size());
    }

    @Test public void misorderedLineNeverPutsTheLinkInTheResult() {
        String result = repo.applySetupFile("https://h.example/secret-token.ics | Minnesota Wild | Hockey",
                NOW.toEpochMilli(), new Fetcher(300, 300), UTC);
        assertTrue(result, result.startsWith("error|1|"));
        assertFalse(result, result.contains("secret"));
        assertFalse(result, result.contains("://"));
    }

    @Test public void badSetupFileLeavesTheListAlone() {
        repo.save(Arrays.asList(cal("Minnesota Wild", "Hockey", 16)));
        String result = repo.applySetupFile("Basketbal|Jazz|https://h.example/j.ics", NOW.toEpochMilli(), new Fetcher(300, 300), UTC);
        assertTrue(result, result.startsWith("error|1|Line 1: the first column is not a sport"));
        assertTrue(result.endsWith("done\n"));
        assertEquals(1, repo.calendars().size());
        assertEquals("Minnesota Wild", repo.calendars().get(0).name);
    }

    @Test public void removedCalendarsLoseTheirCache() throws Exception {
        Calendar wild = cal("Minnesota Wild", "Hockey", 16);
        Calendar royals = cal("Utah Royals", "Soccer", 14);
        repo.save(Arrays.asList(wild, royals));
        cache(wild, "A @ B", "20270403T000000Z");
        cache(royals, "C @ D", "20270403T000000Z");
        repo.saveStatus(royals.id, new FetchStatus(1, 0, null, 1));
        repo.save(Arrays.asList(wild));
        assertTrue(Fetcher.cacheFile(dir, wild.id).exists());
        assertFalse(Fetcher.cacheFile(dir, royals.id).exists());
        assertEquals(null, repo.status(royals.id));
    }

    @Test public void aSaveNeverTripsOverAnotherSavesTemporaryFile() throws Exception {
        // Another save of the same file is half done: its temporary file is in the way.
        assertTrue(new File(dir, "calendars.json.tmp").mkdir());
        repo.save(Arrays.asList(cal("Minnesota Wild", "Hockey", 16)));
        assertEquals(1, repo.calendars().size());
    }

    @Test public void dueCalendars() {
        Calendar wild = cal("Minnesota Wild", "Hockey", 16);
        Calendar royals = cal("Utah Royals", "Soccer", 14);
        repo.save(Arrays.asList(wild, royals));
        repo.saveStatus(wild.id, new FetchStatus(NOW.toEpochMilli() - 3_600_000L, 0, null, 3));
        List<Calendar> due = repo.due(NOW.toEpochMilli());
        assertEquals(1, due.size());
        assertEquals("Utah Royals", due.get(0).name);
    }
}

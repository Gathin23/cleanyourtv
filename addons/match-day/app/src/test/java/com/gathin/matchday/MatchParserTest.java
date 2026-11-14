package com.gathin.matchday;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MatchParserTest {
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");

    @Test public void parsesScheduledLiveFinishedAndPostponedMatchesAndCrests() throws Exception {
        String response = """
                {"matches":[
                  {"utcDate":"2026-11-14T10:00:00Z","status":"TIMED",
                   "homeTeam":{"id":1,"name":"Home FC","shortName":"Home","crest":"https://crests.example/1.svg"},
                   "awayTeam":{"id":2,"name":"Away FC","crest":"https://crests.example/2.png"},
                   "score":{"fullTime":{"home":null,"away":null}}},
                  {"utcDate":"2026-11-14T12:00:00Z","status":"IN_PLAY",
                   "homeTeam":{"id":3,"name":"Live Home","crest":"https://crests.example/3.svg"},
                   "awayTeam":{"id":4,"name":"Live Away","crest":"https://crests.example/4.svg"},
                   "score":{"fullTime":{"home":2,"away":1}}},
                  {"utcDate":"2026-11-14T14:00:00Z","status":"FINISHED",
                   "homeTeam":{"id":5,"name":"Final Home","crest":"https://crests.example/5.svg"},
                   "awayTeam":{"id":6,"name":"Final Away","crest":"https://crests.example/6.svg"},
                   "score":{"fullTime":{"home":3,"away":0}}},
                  {"utcDate":"2026-11-14T16:00:00Z","status":"POSTPONED",
                   "homeTeam":{"id":7,"name":"Later Home","crest":"https://crests.example/7.svg"},
                   "awayTeam":{"id":8,"name":"Later Away","crest":"https://crests.example/8.svg"},
                   "score":{"fullTime":{"home":null,"away":null}}}
                ]}
                """;

        List<Match> matches = MatchParser.parse(response, LocalDate.of(2026, 11, 14), DUBAI);

        assertEquals(4, matches.size());
        assertEquals("TIMED", matches.get(0).status);
        assertEquals("https://crests.example/1.svg", matches.get(0).home.crestUrl);
        assertEquals("https://crests.example/2.png", matches.get(0).away.crestUrl);
        assertFalse(matches.get(0).showScore());
        assertEquals("IN_PLAY", matches.get(1).status);
        assertEquals(Integer.valueOf(2), matches.get(1).homeScore);
        assertEquals(Integer.valueOf(1), matches.get(1).awayScore);
        assertTrue(matches.get(1).showScore());
        assertEquals("FINISHED", matches.get(2).status);
        assertEquals("POSTPONED", matches.get(3).status);
        assertNull(matches.get(3).homeScore);
    }

    @Test public void filtersByTvLocalDayAndQueriesAllOverlappingUtcDates() throws Exception {
        String response = """
                {"matches":[
                  {"utcDate":"2026-11-13T19:59:59Z","status":"TIMED","homeTeam":{"id":1,"name":"Before"},"awayTeam":{"id":2,"name":"Before"}},
                  {"utcDate":"2026-11-13T20:00:00Z","status":"TIMED","homeTeam":{"id":1,"name":"Included"},"awayTeam":{"id":2,"name":"Included"}},
                  {"utcDate":"2026-11-14T19:59:59Z","status":"TIMED","homeTeam":{"id":1,"name":"Included"},"awayTeam":{"id":2,"name":"Included"}},
                  {"utcDate":"2026-11-14T20:00:00Z","status":"TIMED","homeTeam":{"id":1,"name":"After"},"awayTeam":{"id":2,"name":"After"}}
                ]}
                """;

        List<Match> matches = MatchParser.parse(response, LocalDate.of(2026, 11, 14), DUBAI);
        String[] range = MatchParser.utcDateRange(LocalDate.of(2026, 11, 14), DUBAI);

        assertEquals(2, matches.size());
        assertEquals("2026-11-13", range[0]);
        assertEquals("2026-11-15", range[1]);
    }

    @Test public void dubaiNov7QueryIncludesFixturesAcrossUtcDateBoundary() throws Exception {
        String response = """
                {"matches":[
                  {"utcDate":"2026-11-06T19:59:59Z","status":"FINISHED","homeTeam":{"id":1,"name":"Before"},"awayTeam":{"id":2,"name":"Before"}},
                  {"utcDate":"2026-11-06T20:00:00Z","status":"FINISHED","homeTeam":{"id":3,"name":"Included early"},"awayTeam":{"id":4,"name":"Included early"}},
                  {"utcDate":"2026-11-07T19:59:59Z","status":"FINISHED","homeTeam":{"id":5,"name":"Included late"},"awayTeam":{"id":6,"name":"Included late"}},
                  {"utcDate":"2026-11-07T20:00:00Z","status":"FINISHED","homeTeam":{"id":7,"name":"After"},"awayTeam":{"id":8,"name":"After"}}
                ]}
                """;

        LocalDate day = LocalDate.of(2026, 11, 7);
        List<Match> matches = MatchParser.parse(response, day, DUBAI);
        String[] range = MatchParser.utcDateRange(day, DUBAI);

        assertEquals(2, matches.size());
        assertEquals("Included early", matches.get(0).home.name);
        assertEquals("Included late", matches.get(1).home.name);
        assertEquals("2026-11-06", range[0]);
        assertEquals("2026-11-08", range[1]);
    }

    @Test public void dateRangeRoundsOutwardForUtcNegativeOffsetAndDstDay() {
        assertRange(LocalDate.of(2026, 11, 7), ZoneId.of("UTC"), "2026-11-07", "2026-11-08");
        assertRange(LocalDate.of(2026, 11, 7), ZoneId.of("America/Los_Angeles"),
                "2026-11-07", "2026-11-09");
        assertRange(LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York"),
                "2026-03-08", "2026-03-10");
    }

    @Test public void emptyMatchDayIsAValidEmptyResponse() throws Exception {
        assertTrue(MatchParser.parse("{\"matches\":[]}", LocalDate.now(DUBAI), DUBAI).isEmpty());
    }

    @Test public void parsesSportScoreFieldsAndMapsStatuses() throws Exception {
        String response = """
                {"matches":[
                  {"time":"2026-11-07T12:30:00Z","status":"finished","status_text":"Finished",
                   "home":"Home FC","away":"Away FC","home_logo":"https://img.example/home.png",
                   "away_logo":"https://img.example/away.png","home_score":"2","away_score":"1"},
                  {"time":"2026-11-07T15:00:00Z","status":"live","status_text":"Live",
                   "home":"Live Home","away":"Live Away","home_score":0,"away_score":0},
                  {"time":"2026-11-07T18:00:00Z","status":"upcoming","status_text":"Not Started",
                   "home":"Next Home","away":"Next Away"},
                  {"time":"2026-11-07T19:00:00Z","status":"postponed","status_text":"Postponed",
                   "home":"Later Home","away":"Later Away"}
                ]}
                """;

        List<Match> matches = MatchParser.parseSportScore(response, LocalDate.of(2026, 11, 7), DUBAI);

        assertEquals(4, matches.size());
        assertEquals("FINISHED", matches.get(0).status);
        assertEquals(Integer.valueOf(2), matches.get(0).homeScore);
        assertEquals("https://img.example/home.png", matches.get(0).home.crestUrl);
        assertEquals("IN_PLAY", matches.get(1).status);
        assertTrue(matches.get(1).showScore());
        assertEquals("SCHEDULED", matches.get(2).status);
        assertNull(matches.get(2).homeScore);
        assertNull(matches.get(2).home.crestUrl);
        assertEquals("POSTPONED", matches.get(3).status);
    }

    @Test public void sportScoreLocalDateIncludesBothDubaiUtcDatesAndFiltersNeighbors() throws Exception {
        String response = """
                {"matches":[
                  {"time":"2026-11-06T19:59:59Z","status":"finished","home":"Before","away":"X"},
                  {"time":"2026-11-06T20:00:00Z","status":"finished","home":"Included early","away":"X"},
                  {"time":"2026-11-07T19:59:59Z","status":"finished","home":"Included late","away":"X"},
                  {"time":"2026-11-07T20:00:00Z","status":"finished","home":"After","away":"X"}
                ]}
                """;
        LocalDate day = LocalDate.of(2026, 11, 7);

        List<Match> matches = MatchParser.parseSportScore(response, day, DUBAI);

        assertEquals(2, matches.size());
        assertEquals("Included early", matches.get(0).home.name);
        assertEquals("Included late", matches.get(1).home.name);
        assertEquals(2, MatchParser.utcCalendarDates(day, DUBAI).length);
        assertEquals("2026-11-06", MatchParser.utcCalendarDates(day, DUBAI)[0]);
        assertEquals("2026-11-07", MatchParser.utcCalendarDates(day, DUBAI)[1]);
    }

    @Test public void sportScoreAcceptsEmptyAndRejectsMalformedResponses() throws Exception {
        assertTrue(MatchParser.parseSportScore("{\"matches\":[]}", LocalDate.now(DUBAI), DUBAI).isEmpty());
        assertTrue(MatchParser.sportScoreHttpError(429).contains("rate limit"));
        assertTrue(MatchParser.sportScoreHttpError(403).contains("refused"));
        try {
            MatchParser.parseSportScore("{}", LocalDate.now(DUBAI), DUBAI);
            throw new AssertionError("missing matches must be rejected");
        } catch (org.json.JSONException expected) { }
    }

    @Test public void explainsAuthenticationRateLimitAndNetworkOutcomes() {
        assertTrue(MatchParser.httpError(401).contains("API key was rejected"));
        assertTrue(MatchParser.httpError(403).contains("cannot access match data"));
        assertTrue(MatchParser.httpError(429).contains("rate limit"));
        assertTrue(MatchParser.httpError(500).contains("HTTP 500"));
    }

    private static void assertRange(LocalDate date, ZoneId zone, String from, String to) {
        String[] range = MatchParser.utcDateRange(date, zone);
        assertEquals(from, range[0]);
        assertEquals(to, range[1]);
    }
}

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
        assertEquals("2026-11-14", range[1]);
    }

    @Test public void emptyMatchDayIsAValidEmptyResponse() throws Exception {
        assertTrue(MatchParser.parse("{\"matches\":[]}", LocalDate.now(DUBAI), DUBAI).isEmpty());
    }

    @Test public void explainsAuthenticationRateLimitAndNetworkOutcomes() {
        assertTrue(MatchParser.httpError(401).contains("API key was rejected"));
        assertTrue(MatchParser.httpError(403).contains("cannot access match data"));
        assertTrue(MatchParser.httpError(429).contains("rate limit"));
        assertTrue(MatchParser.httpError(500).contains("HTTP 500"));
    }
}

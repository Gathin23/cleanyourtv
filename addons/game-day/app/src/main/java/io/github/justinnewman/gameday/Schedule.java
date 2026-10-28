package io.github.justinnewman.gameday;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What Home rotates through: one turn per calendar, its next entry that has not ended, if that starts within a
 * week. A game in two calendars (following both teams) is one turn, in the colour of the calendar listed first.
 */
final class Schedule {
    static final Duration WINDOW = Duration.ofDays(7);

    static final class Turn {
        final String topLine;
        final String word;
        final String calendar;
        final long startMs;
        final long endMs;
        /** ARGB. */
        final int colour;

        Turn(String topLine, String word, String calendar, long startMs, long endMs, int colour) {
            this.topLine = topLine;
            this.word = word;
            this.calendar = calendar;
            this.startMs = startMs;
            this.endMs = endMs;
            this.colour = colour;
        }
    }

    private Schedule() {
    }

    static List<Turn> turns(List<Calendar> calendars, Map<String, List<Ics.Event>> eventsByCalendarId, Instant now,
                            ZoneId zone) {
        List<Turn> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Instant horizon = now.plus(WINDOW);
        for (Calendar c : calendars) {
            List<Ics.Event> events = eventsByCalendarId.get(c.id);
            if (events == null || events.isEmpty()) continue;
            List<Ics.Event> sorted = new ArrayList<>(events);
            sorted.sort(Comparator.comparing(e -> e.start));
            for (Ics.Event e : sorted) {
                Instant end = endOf(e, c.sport, zone);
                if (!end.isAfter(now)) continue;
                if (e.start.isBefore(horizon)) {
                    String top = Titles.topLine(e.title, c.name);
                    if (seen.add(top.toLowerCase(Locale.ROOT) + "|" + e.start.toEpochMilli())) {
                        out.add(new Turn(top, Titles.word(c.sport, e.title), c.name, e.start.toEpochMilli(),
                                end.toEpochMilli(), Palette.colour(c.colour)));
                    }
                }
                break;
            }
        }
        Collections.sort(out, Comparator.comparingLong(t -> t.startMs));
        return out;
    }

    /** The calendar's end, or a typical length: 3 hours, 2 for motor racing, the rest of the day for golf. */
    static Instant endOf(Ics.Event e, Sport sport, ZoneId zone) {
        if (e.end != null) return e.end;
        switch (sport) {
            case GOLF:
                return e.start.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
            case MOTOR_FLAG:
            case MOTOR_LIGHTS:
                return e.start.plus(Duration.ofHours(2));
            default:
                return e.start.plus(Duration.ofHours(3));
        }
    }
}

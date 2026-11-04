package com.gathin.tvhome;

import android.content.res.Resources;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Race-weekend mode. A theme's config.json can carry an F1 calendar ({@code race_calendar}, written by
 * tools/race_calendar.py); from shortly before a weekend's first session until the race is over, Home shows
 * a small panel with the next session and a countdown. Scheduling uses the clock against owner-pushed
 * data and is tested on the JVM; display labels use Android resources.
 */
final class RaceWeekend {

    /** The panel shows from this long before a weekend's first session... */
    static final Duration LEAD = Duration.ofHours(12);
    /** ...until this long after the race starts: a race runs about two hours, then the result settles. */
    static final Duration TAIL = Duration.ofHours(4);

    enum Status { DONE, LIVE, NEXT, LATER }

    static final class Session {
        /** As written in the calendar: FP1, FP2, FP3, SPRINT QUALI, SPRINT, QUALIFYING or RACE. */
        final String name;
        final Instant start;

        Session(String name, Instant start) {
            this.name = name.trim().toUpperCase(Locale.ROOT);
            this.start = start;
        }

        boolean isRace() {
            return name.equals("RACE");
        }

        /** Roughly how long the session is live; only used to tell live from done. */
        Duration length() {
            if (isRace()) return Duration.ofHours(2);
            if (name.equals("SPRINT QUALI")) return Duration.ofMinutes(45);
            return Duration.ofHours(1);
        }

        /** The name in the countdown line: "Qualifying in 2h 14m". */
        String title(Resources res) {
            switch (name) {
                case "FP1": return res.getString(R.string.race_practice_1);
                case "FP2": return res.getString(R.string.race_practice_2);
                case "FP3": return res.getString(R.string.race_practice_3);
                case "SPRINT QUALI": return res.getString(R.string.race_sprint_qualifying);
                case "SPRINT": return res.getString(R.string.race_sprint);
                case "QUALIFYING": return res.getString(R.string.race_qualifying);
                case "RACE": return res.getString(R.string.race_race);
                default: return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
            }
        }

        /** The name in the session row, kept short so a whole weekend fits on one line. */
        String shortName(Resources res) {
            switch (name) {
                case "FP1": return res.getString(R.string.race_practice_1_short);
                case "FP2": return res.getString(R.string.race_practice_2_short);
                case "FP3": return res.getString(R.string.race_practice_3_short);
                case "SPRINT QUALI": return res.getString(R.string.race_sprint_qualifying_short);
                case "SPRINT": return res.getString(R.string.race_sprint_short);
                case "QUALIFYING": return res.getString(R.string.race_qualifying_short);
                case "RACE": return res.getString(R.string.race_race_short);
                default: return name;
            }
        }
    }

    static final class Race {
        final int round;
        final String name;
        final String place;
        /** In start order; the last one is the race. */
        final List<Session> sessions;

        Race(int round, String name, String place, List<Session> sessions) {
            this.round = round;
            this.name = name;
            this.place = place;
            List<Session> sorted = new ArrayList<>(sessions);
            sorted.sort((a, b) -> a.start.compareTo(b.start));
            this.sessions = Collections.unmodifiableList(sorted);
        }

        /** "United States Grand Prix" becomes "United States GP", which fits the panel's top line. */
        String shortName() {
            return name.replace("Grand Prix", "GP").trim();
        }
    }

    /** What the panel shows at one moment of a race weekend. */
    static final class State {
        final Race race;
        /** One per session of {@link #race}, same order. */
        final List<Status> statuses;
        /** The session on track now, or null. */
        final Session live;
        /** The next session to start, or null once the race has started. */
        final Session next;
        /** Time until {@link #next} starts; null when there is no next session. */
        final Duration untilNext;

        State(Race race, List<Status> statuses, Session live, Session next, Duration untilNext) {
            this.race = race;
            this.statuses = Collections.unmodifiableList(statuses);
            this.live = live;
            this.next = next;
            this.untilNext = untilNext;
        }

        /** The race is over and the panel is only showing the weekend until {@link #TAIL} runs out. */
        boolean finished() {
            return live == null && next == null;
        }
    }

    private RaceWeekend() {
    }

    /** The weekend {@code now} falls in, or null when no race is near. */
    static State at(List<Race> calendar, Instant now) {
        for (Race race : calendar) {
            if (race.sessions.isEmpty()) continue;
            Instant opens = race.sessions.get(0).start.minus(LEAD);
            Instant closes = race.sessions.get(race.sessions.size() - 1).start.plus(TAIL);
            if (now.isBefore(opens) || !now.isBefore(closes)) continue;

            List<Status> statuses = new ArrayList<>();
            Session live = null, next = null;
            for (Session s : race.sessions) {
                if (now.isBefore(s.start)) {
                    statuses.add(next == null ? Status.NEXT : Status.LATER);
                    if (next == null) next = s;
                } else if (now.isBefore(s.start.plus(s.length()))) {
                    statuses.add(Status.LIVE);
                    live = s;
                } else {
                    statuses.add(Status.DONE);
                }
            }
            return new State(race, statuses, live, next, next == null ? null : Duration.between(now, next.start));
        }
        return null;
    }

    /**
     * "42 min", "2h 14m", "1d 3h". Rounded up to the minute, so a session 20 seconds away still reads
     * "1 min" and the countdown never says 0 before the session is live.
     */
    static String countdown(Resources res, Duration d) {
        long minutes = Math.max(1, (d.getSeconds() + 59) / 60);
        if (minutes < 60) return res.getString(R.string.race_countdown_minutes, minutes);
        long hours = minutes / 60;
        if (hours < 24) return res.getString(R.string.race_countdown_hours, hours, minutes % 60);
        return res.getString(R.string.race_countdown_days, hours / 24, hours % 24);
    }
}

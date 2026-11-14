package com.gathin.matchday;

import java.time.Instant;

final class Match {
    final Instant kickoff;
    final String status;
    final Team home;
    final Team away;
    final Integer homeScore;
    final Integer awayScore;

    Match(Instant kickoff, String status, Team home, Team away, Integer homeScore, Integer awayScore) {
        this.kickoff = kickoff;
        this.status = status;
        this.home = home;
        this.away = away;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }

    boolean hasScore() {
        return homeScore != null && awayScore != null;
    }

    boolean showScore() {
        return hasScore() && ("IN_PLAY".equals(status) || "PAUSED".equals(status)
                || "FINISHED".equals(status) || "AWARDED".equals(status));
    }

    static final class Team {
        final int id;
        final String name;
        final String shortName;
        final String crestUrl;

        Team(int id, String name, String shortName, String crestUrl) {
            this.id = id;
            this.name = name;
            this.shortName = shortName;
            this.crestUrl = crestUrl;
        }

        String displayName() {
            return shortName == null || shortName.isEmpty() ? name : shortName;
        }
    }
}

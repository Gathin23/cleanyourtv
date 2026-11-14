package com.gathin.matchday;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

final class MatchLoadState {
    final LocalDate date;
    private final EnumSet<Competition> completed = EnumSet.noneOf(Competition.class);
    private final EnumMap<Competition, MatchRepository.Result> results =
            new EnumMap<>(Competition.class);
    private int remaining = Competition.values().length;

    MatchLoadState(LocalDate date) {
        this.date = date;
    }

    boolean accept(LocalDate selectedDate, Competition competition, MatchRepository.Result result) {
        if (!completed.add(competition)) return false;
        remaining--;
        if (!date.equals(selectedDate)) return false;
        results.put(competition, result);
        return true;
    }

    boolean isComplete() {
        return remaining == 0;
    }

    int remaining() {
        return remaining;
    }

    MatchRepository.Result result(Competition competition) {
        return results.get(competition);
    }

    Map<Competition, MatchRepository.Result> results() {
        return new EnumMap<>(results);
    }
}

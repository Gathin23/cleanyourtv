package io.github.luyzfernando08.sleeptimer;

import java.time.*;
import java.util.*;

/** Pure local-calendar rules. Day bits run Monday (0) through Sunday (6). */
final class ScheduleRules {
    static final class Rule {
        int days, minute;
        boolean wake;
        Rule(int days, int minute, boolean wake) {
            this.days = days; this.minute = minute; this.wake = wake;
        }
    }
    static final class Pause {
        LocalDate start, end;
        Pause(LocalDate start, LocalDate end) { this.start = start; this.end = end; }
    }
    static final class Event {
        long at; boolean wake;
        Event(long at, boolean wake) { this.at = at; this.wake = wake; }
    }
    static boolean paused(LocalDate date, List<Pause> pauses) {
        for (Pause p : pauses) if (!date.isBefore(p.start) && !date.isAfter(p.end)) return true;
        return false;
    }
    static Event next(long now, ZoneId zone, List<Rule> rules, List<Pause> pauses) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        Event best = null;
        for (Rule r : rules) {
            if (r.days == 0) continue;
            LocalDate date = today;
            // Jump over exclusion periods rather than imposing a horizon on future events.
            while (true) {
                if (r.wake) {
                    boolean jumped = false;
                    for (Pause p : pauses) {
                        if (!date.isBefore(p.start) && !date.isAfter(p.end)) {
                            date = p.end.plusDays(1); jumped = true; break;
                        }
                    }
                    if (jumped) continue;
                }
                if ((r.days & (1 << (date.getDayOfWeek().getValue() - 1))) != 0) {
                    long at = date.atTime(r.minute / 60, r.minute % 60).atZone(zone).toInstant().toEpochMilli();
                    if (at > now) {
                        // Sleep wins if two rules share the same instant.
                        if (best == null || at < best.at || (at == best.at && !r.wake)) best = new Event(at, r.wake);
                        break;
                    }
                }
                date = date.plusDays(1);
            }
        }
        return best;
    }
}

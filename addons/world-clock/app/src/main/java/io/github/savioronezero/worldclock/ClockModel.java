package io.github.savioronezero.worldclock;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Clock calculations and small list edits: no Android objects, fixed offsets or background work. */
final class ClockModel {
    static final String LOCAL = "@local";
    static final int LIMIT = 6;
    static final List<String> DEFAULTS = Collections.unmodifiableList(
            Arrays.asList(LOCAL, "Europe/London", "Australia/Sydney"));

    static boolean valid(String id) {
        if (LOCAL.equals(id)) return true;
        if (id == null || id.length() > 100) return false;
        // Only platform-named zones: malformed IDs cannot silently become GMT as with TimeZone.getTimeZone.
        return "UTC".equals(id) || ZoneId.getAvailableZoneIds().contains(id);
    }

    static List<String> restore(List<String> saved) {
        List<String> result = new ArrayList<>();
        if (saved != null) for (String id : saved) {
            if (valid(id) && !result.contains(id)) result.add(id);
            if (result.size() == LIMIT) break;
        }
        return result.isEmpty() ? new ArrayList<>(DEFAULTS) : result;
    }

    static List<String> add(List<String> ids, String id) {
        List<String> result = new ArrayList<>(ids);
        if (valid(id) && !result.contains(id) && result.size() < LIMIT) result.add(id);
        return result;
    }

    static List<String> replace(List<String> ids, int index, String id) {
        List<String> result = new ArrayList<>(ids);
        if (index >= 0 && index < result.size() && valid(id)
                && (id.equals(result.get(index)) || !result.contains(id))) result.set(index, id);
        return result;
    }

    static List<String> remove(List<String> ids, int index) {
        List<String> result = new ArrayList<>(ids);
        if (result.size() > 1 && index >= 0 && index < result.size()) result.remove(index);
        return result;
    }

    static List<String> move(List<String> ids, int from, int to) {
        List<String> result = new ArrayList<>(ids);
        if (from >= 0 && from < result.size() && to >= 0 && to < result.size()) {
            String id = result.remove(from);
            result.add(to, id);
        }
        return result;
    }

    static String city(String id) {
        if (LOCAL.equals(id)) return "Local";
        String name = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        // Older Android/ICU catalogues use the legacy spelling; keep the familiar city searchable.
        return "Katmandu".equals(name) ? "Kathmandu" : name;
    }

    static String area(String id) {
        int slash = id.lastIndexOf('/');
        return slash < 0 ? id : id.substring(0, slash).replace('_', ' ').replace('/', ' ');
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    static List<String> search(List<String> catalogue, String query, List<String> existing, int replacing) {
        String q = normalized(query);
        String[] terms = q.isEmpty() ? new String[0] : q.split("\\s+");
        List<String> matches = new ArrayList<>();
        for (String id : catalogue) {
            if (existing.contains(id) && (replacing < 0 || !id.equals(existing.get(replacing)))) continue;
            String text = normalized(city(id) + " " + id.replace('/', ' '));
            boolean match = true;
            for (String term : terms) if (!text.contains(term)
                    && !text.replace(" ", "").contains(term)) { match = false; break; }
            if (match) matches.add(id);
        }
        return matches;
    }

    static List<String> catalogue() {
        Set<String> found = new HashSet<>();
        found.add(LOCAL); found.add("UTC");
        Set<String> available = ZoneId.getAvailableZoneIds();
        for (String id : available) {
            if (id.equals("Asia/Katmandu") && available.contains("Asia/Kathmandu")) continue;
            if (id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/")
                    && !id.startsWith("US/") && !id.startsWith("Canada/")
                    && !id.startsWith("Brazil/") && !id.startsWith("Mexico/")) found.add(id);
        }
        List<String> result = new ArrayList<>(found);
        result.sort((a, b) -> {
            if (a.equals(LOCAL)) return b.equals(LOCAL) ? 0 : -1;
            if (b.equals(LOCAL)) return 1;
            int name = city(a).compareToIgnoreCase(city(b));
            return name == 0 ? a.compareTo(b) : name;
        });
        return result;
    }

    static final class Reading {
        final String time, period, date, offset;
        final int dayDifference;
        Reading(String time, String period, String date, String offset, int dayDifference) {
            this.time = time; this.period = period; this.date = date;
            this.offset = offset; this.dayDifference = dayDifference;
        }
    }

    static Reading at(String id, Instant instant, ZoneId local, boolean twentyFour, Locale locale) {
        ZonedDateTime time = instant.atZone(LOCAL.equals(id) ? local : ZoneId.of(id));
        String offset = time.getOffset().getId();
        return new Reading(time.format(DateTimeFormatter.ofPattern(twentyFour ? "HH:mm" : "h:mm", locale)),
                twentyFour ? "" : time.format(DateTimeFormatter.ofPattern("a", locale)),
                time.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)),
                "Z".equals(offset) ? "UTC" : "UTC" + offset,
                (int) ChronoUnit.DAYS.between(instant.atZone(local).toLocalDate(), time.toLocalDate()));
    }

    static long nextMinute(long epochMillis) { return 60_000L - Math.floorMod(epochMillis, 60_000L); }
}

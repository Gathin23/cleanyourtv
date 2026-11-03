package io.github.deepakola.familyrenewals;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class DateRules {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.getDefault());

    private DateRules() {}

    static LocalDate parse(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static long daysUntil(String value, LocalDate today) {
        LocalDate due = parse(value);
        if (due == null) throw new IllegalArgumentException("A valid due date is required");
        return ChronoUnit.DAYS.between(today, due);
    }

    static String display(String value) {
        LocalDate date = parse(value);
        return date == null ? "Date not set" : DISPLAY.format(date);
    }

    static String status(String value, LocalDate today) {
        long days = daysUntil(value, today);
        if (days < 0) return "OVERDUE";
        if (days == 0) return "DUE TODAY";
        if (days == 1) return "DUE TOMORROW";
        if (days <= 7) return "DUE IN " + days + " DAYS";
        return "UPCOMING";
    }
}

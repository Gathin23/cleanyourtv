package io.github.gagantk.kannadacalendar;

import java.time.LocalDate;

/**
 * One calendar event on one day: an all-day line (time empty) or a timed one ("Dwadasi tithi ends" at 07:20).
 * {@code holiday} is "public" or "restricted" when the publisher files the event under its holiday lists, else "".
 */
final class Entry {
    final LocalDate date;
    final String time, summary, description, holiday;

    Entry(LocalDate date, String time, String summary, String description, String holiday) {
        this.date = date;
        this.time = time;
        this.summary = summary;
        this.description = description;
        this.holiday = holiday;
    }

    Entry(LocalDate date, String time, String summary, String description) {
        this(date, time, summary, description, "");
    }
}

package io.github.gagantk.kannadacalendar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads an iCalendar feed as a stream and keeps the events that fall between two dates, so a feed of
 * fifteen years and several megabytes costs the TV a few hundred kilobytes. Recurring events are skipped:
 * the calendars this is for list every day on its own.
 */
final class IcsReader {
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[.*?\\]\\]");

    private final LocalDate from, to;
    private final List<Entry> out;
    private boolean inEvent;
    private String start, summary, description, location;
    private boolean recurring;

    private IcsReader(LocalDate from, LocalDate to, List<Entry> out) {
        this.from = from;
        this.to = to;
        this.out = out;
    }

    static void read(InputStream in, LocalDate from, LocalDate to, List<Entry> out) throws IOException {
        IcsReader reader = new IcsReader(from, to, out);
        BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        StringBuilder current = null;
        String raw;
        while ((raw = lines.readLine()) != null) {
            // A line that starts with a space or tab continues the one before it.
            if (!raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t')) {
                if (current != null) current.append(raw, 1, raw.length());
                continue;
            }
            if (current != null) reader.line(current.toString());
            current = new StringBuilder(raw);
        }
        if (current != null) reader.line(current.toString());
    }

    private void line(String line) {
        if (line.equals("BEGIN:VEVENT")) {
            inEvent = true;
            start = null;
            summary = "";
            description = "";
            location = "";
            recurring = false;
            return;
        }
        if (line.equals("END:VEVENT")) {
            if (inEvent && !recurring) emit();
            inEvent = false;
            return;
        }
        if (!inEvent) return;
        int colon = line.indexOf(':');
        if (colon < 0) return;
        int semi = line.indexOf(';');
        String name = line.substring(0, semi >= 0 && semi < colon ? semi : colon);
        String value = line.substring(colon + 1);
        switch (name) {
            case "DTSTART": start = value.trim(); break;
            case "SUMMARY": summary = value; break;
            case "DESCRIPTION": description = value; break;
            case "LOCATION": location = value; break;
            case "RRULE": recurring = true; break;
            default: break;
        }
    }

    private void emit() {
        if (start == null) return;
        LocalDate date;
        String time = "";
        try {
            if (start.length() == 8) {
                date = LocalDate.parse(start, DateTimeFormatter.BASIC_ISO_DATE);
            } else if (start.endsWith("Z")) {
                LocalDateTime utc = LocalDateTime.parse(start.substring(0, start.length() - 1), STAMP);
                LocalDateTime local = utc.atZone(ZoneOffset.UTC).withZoneSameInstant(INDIA).toLocalDateTime();
                date = local.toLocalDate();
                time = local.format(CLOCK);
            } else {
                LocalDateTime local = LocalDateTime.parse(start, STAMP);
                date = local.toLocalDate();
                time = local.format(CLOCK);
            }
        } catch (RuntimeException e) {
            return;
        }
        if (date.isBefore(from) || date.isAfter(to)) return;
        out.add(new Entry(date, time, clean(repair(unescape(summary))), clean(repair(unescape(description))), holiday(location)));
    }

    /**
     * The publisher files the Karnataka government's holidays under the event's location: "Public Holidays" or
     * "Restricted Holiday". Anything else there is not a holiday.
     */
    static String holiday(String location) {
        String l = location.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("public holiday")) return "public";
        if (l.contains("restricted holiday")) return "restricted";
        return "";
    }

    /** iCalendar text escapes: \n and \N are line breaks, \, \; and \\ are the plain characters. */
    static String unescape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char n = text.charAt(++i);
                sb.append(n == 'n' || n == 'N' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Some lines in the feed were saved as UTF-8 read as Windows-1252 ("Childrenâ€™s Day" for "Children’s Day").
     * Such a line is turned back into bytes and read again; one that does not come out clean is left as it was.
     */
    static String repair(String text) {
        if (text.indexOf('â') < 0 && text.indexOf('Ã') < 0) return text;
        // Windows-1252 first, then Latin-1, which also covers the C1 control characters 1252 has no bytes for.
        for (java.nio.charset.Charset as : new java.nio.charset.Charset[]{
                java.nio.charset.Charset.forName("windows-1252"), StandardCharsets.ISO_8859_1}) {
            try {
                java.nio.ByteBuffer bytes = as.newEncoder().encode(java.nio.CharBuffer.wrap(text));
                return StandardCharsets.UTF_8.newDecoder().decode(bytes).toString();
            } catch (Exception e) {
                // not this one
            }
        }
        return text;
    }

    /**
     * The publisher's own markup out of the way: "[[link][(hold down shift key and click here)]]" is a
     * click-through for a desktop program and means nothing on a TV, and some lines carry a double-escaped
     * line break that reaches us as a backslash and an n.
     */
    static String clean(String text) {
        String out = WIKI_LINK.matcher(text.replace("\\n", "\n")).replaceAll("");
        StringBuilder sb = new StringBuilder();
        for (String line : out.split("\n")) {
            String trimmed = line.replaceAll("\\s*-\\s*$", "").trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(trimmed);
        }
        return sb.toString();
    }
}

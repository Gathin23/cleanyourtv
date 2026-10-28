package io.github.justinnewman.gameday;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The calendar list: the setup file the script sends ({@code sport | name | link}, optionally {@code | colour}, one
 * per line, # for comments) and the JSON the app keeps it in. Error messages name the line, never the link.
 */
final class CalendarStore {
    static final int MAX = 20;
    static final int MAX_NAME = 40;

    /** A line the setup file cannot use. {@link #line} counts from 1. */
    static final class BadLine extends Exception {
        final int line;

        BadLine(int line, String message) {
            super("Line " + line + ": " + message);
            this.line = line;
        }
    }

    private CalendarStore() {
    }

    static List<Calendar> parseSetupFile(String text) throws BadLine {
        List<Calendar> out = new ArrayList<>();
        Set<String> links = new HashSet<>();
        Set<Integer> colours = new HashSet<>();
        String[] lines = text.split("\r?\n|\r", -1);
        for (int n = 1; n <= lines.length; n++) {
            String line = lines[n - 1].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\|", -1);
            if (f.length < 3 || f.length > 4) throw new BadLine(n, "expected sport | name | link, optionally | colour");
            Sport sport = Sport.byLabel(f[0]);
            if (sport == null) {
                // The column is named, never quoted: in a line written in the wrong order it can be the link.
                throw new BadLine(n, "the first column is not a sport. Choose one of: " + Sport.labels());
            }
            String name = f[1].trim();
            if (name.isEmpty()) throw new BadLine(n, "the name is empty");
            if (name.length() > MAX_NAME) throw new BadLine(n, "the name is over " + MAX_NAME + " characters");
            String link = Links.normalise(f[2]);
            if (link == null) throw new BadLine(n, "not a calendar link (it starts with https://, http:// or webcal://)");
            int colour;
            if (f.length == 4 && !f[3].trim().isEmpty()) {
                colour = Palette.byName(f[3]);
                if (colour < 0) throw new BadLine(n, "the fourth column is not one of the 36 colours, for example \"Green, dark\"");
            } else {
                colour = Palette.firstUnused(colours);
            }
            if (!links.add(link)) continue;
            if (out.size() == MAX) throw new BadLine(n, "more than " + MAX + " calendars");
            colours.add(colour);
            out.add(new Calendar(Links.id(link), name, link, sport, colour));
        }
        return out;
    }

    static String toJson(List<Calendar> calendars) {
        JSONArray a = new JSONArray();
        try {
            for (Calendar c : calendars) {
                a.put(new JSONObject().put("id", c.id).put("name", c.name).put("link", c.link)
                        .put("sport", c.sport.label()).put("colour", c.colour));
            }
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return a.toString();
    }

    /** The saved list; entries it cannot read (an unknown sport after an update) are left out. */
    static List<Calendar> fromJson(String json) {
        List<Calendar> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Sport sport = Sport.byLabel(o.optString("sport"));
                String link = Links.normalise(o.optString("link"));
                int colour = o.optInt("colour", Palette.COLUMNS);
                if (sport == null || link == null || colour < 0 || colour >= Palette.COUNT) continue;
                out.add(new Calendar(o.optString("id", Links.id(link)), o.optString("name"), link, sport, colour));
            }
        } catch (JSONException unreadable) {
            return new ArrayList<>();
        }
        return out;
    }
}

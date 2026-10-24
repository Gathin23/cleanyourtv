package io.github.maxhayim.manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The project's add-ons, themes and switches, read from its own READMEs the way tools/manage.py reads them: the
 * table in addons/README.md, the table and the Keys in themes/README.md, and the screenshots in docs/switches.md.
 * No Android types and no network, so the JVM tests run it on this checkout's files.
 */
final class Catalog {
    enum Kind { ADDON, THEME, SWITCH }

    static final class Item {
        final Kind kind;
        final String id;
        final String name;
        final String what;
        final String author;
        final String tested;
        final String permissions;
        /** Repository path of the screenshot, or null. */
        final String screenshot;
        /** Switches: the value's type. */
        final String type;

        Item(Kind kind, String id, String name, String what, String author, String tested, String permissions,
             String screenshot, String type) {
            this.kind = kind;
            this.id = id;
            this.name = name;
            this.what = what;
            this.author = author;
            this.tested = tested;
            this.permissions = permissions;
            this.screenshot = screenshot;
            this.type = type;
        }
    }

    private static final Pattern IMG = Pattern.compile("<img src=\"([^\"]+)\"");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]*)\\)");
    private static final Pattern ADDON_LINK = Pattern.compile("^\\[([^\\]]+)\\]\\(([^)/]+)/\\)");
    private static final Pattern CODE = Pattern.compile("`([a-z_]+)`");
    private static final Pattern APP_ID = Pattern.compile("applicationId\\s*=?\\s*['\"]([^'\"]+)['\"]");

    private Catalog() {
    }

    /** Rows of the first table whose header line starts with header, as cells. */
    static List<String[]> rows(String text, String header) {
        List<String[]> out = new ArrayList<>();
        boolean inside = false;
        for (String line : text.split("\n")) {
            line = line.replace("\r", "");
            if (line.startsWith(header)) {
                inside = true;
                continue;
            }
            if (!inside) continue;
            if (!line.startsWith("|")) {
                if (!out.isEmpty()) break;
                continue;
            }
            if (line.matches("^\\|[\\s:|-]+\\|$")) continue;
            String body = line.trim();
            body = body.substring(1, body.endsWith("|") ? body.length() - 1 : body.length());
            String[] cells = body.split(" \\| ", -1);
            for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
            out.add(cells);
        }
        return out;
    }

    static String plain(String cell) {
        if (cell == null) return "";
        return LINK.matcher(cell).replaceAll("$1").replace("`", "").replace("&#124;", "|").trim();
    }

    static String img(String cell) {
        if (cell == null) return null;
        Matcher m = IMG.matcher(cell);
        return m.find() ? m.group(1) : null;
    }

    static List<Item> addons(String readme) {
        List<Item> out = new ArrayList<>();
        for (String[] c : rows(readme, "| Add-on |")) {
            Matcher m = ADDON_LINK.matcher(c[0]);
            if (!m.find()) continue;
            String shot = c.length > 5 ? img(c[5]) : null;
            out.add(new Item(Kind.ADDON, m.group(2), m.group(1), cell(c, 1), cell(c, 2), cell(c, 3), cell(c, 4),
                    shot == null ? null : "addons/" + shot, null));
        }
        return out;
    }

    static List<Item> themes(String readme) {
        List<Item> out = new ArrayList<>();
        for (String[] c : rows(readme, "| Theme |")) {
            String folder = plain(c[0]).replaceAll("/+$", "");
            if (folder.isEmpty()) continue;
            String shot = c.length > 2 ? img(c[2]) : null;
            out.add(new Item(Kind.THEME, folder, folder, cell(c, 1), null, null, null,
                    shot == null ? null : "themes/" + shot, null));
        }
        return out;
    }

    /** The keys in themes/README.md, with the screenshots docs/switches.md has for them. */
    static List<Item> switches(String themesReadme, String switchesMd) {
        Map<String, String> shots = new LinkedHashMap<>();
        if (switchesMd != null) {
            for (String[] c : rows(switchesMd, "| Key |")) {
                String src = c.length > 1 ? img(c[1]) : null;
                if (src == null) continue;
                Matcher k = CODE.matcher(c[0]);
                while (k.find()) shots.put(k.group(1), resolve("docs/", src));
            }
        }
        int keys = themesReadme.indexOf("## Keys");
        String part = keys >= 0 ? themesReadme.substring(keys) : themesReadme;
        List<Item> out = new ArrayList<>();
        for (String[] c : rows(part, "| Key |")) {
            Matcher k = CODE.matcher(c[0]);
            while (k.find()) {
                String key = k.group(1);
                out.add(new Item(Kind.SWITCH, key, key, cell(c, 2), null, null, null, shots.get(key), cell(c, 1)));
            }
        }
        return out;
    }

    /** "docs/" + "../themes/x.jpg" is "themes/x.jpg". */
    static String resolve(String base, String rel) {
        List<String> parts = new ArrayList<>();
        for (String p : (base + rel).split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else {
                parts.add(p);
            }
        }
        return String.join("/", parts);
    }

    static String packageOf(String gradle) {
        if (gradle == null) return null;
        Matcher m = APP_ID.matcher(gradle);
        return m.find() ? m.group(1) : null;
    }

    /** The command to install an item from a computer, the creator's way, for this TV. */
    static String installCommand(Item i, String tvIp) {
        String host = "TV_HOST=" + (tvIp == null || tvIp.isEmpty() ? "<tv ip>" : tvIp);
        switch (i.kind) {
            case THEME:
                return host + " tools/theme.sh themes/" + i.id;
            case SWITCH:
                return host + " tools/venv/bin/python -I tools/manage.py set " + i.id + " <value> --theme <your theme folder>";
            default:
                return host + " tools/venv/bin/python -I tools/manage.py install " + i.id;
        }
    }

    private static String cell(String[] c, int i) {
        return c.length > i ? plain(c[i]) : "";
    }
}

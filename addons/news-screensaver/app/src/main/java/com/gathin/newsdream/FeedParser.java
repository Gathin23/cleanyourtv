package com.gathin.newsdream;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * RSS 2.0, RSS 1.0 (RDF) and Atom, read as a stream with SAX: the feed's title and its first items. Element names
 * are matched without their prefix, so dc:date and atom:title count as date and title.
 */
final class FeedParser {
    static final int MAX_ITEMS = 20;
    private static final int MAX_TITLE = 200;
    private static final int MAX_SUMMARY = 280;
    private static final int MAX_SOURCE = 40;
    private static final int MAX_TEXT = 16 * 1024;
    private static final int MAX_LINK = 400;

    private FeedParser() {}

    static final class Feed {
        final String title;
        final List<Headline> items;

        Feed(String title, List<Headline> items) {
            this.title = title;
            this.items = items;
        }
    }

    /** Thrown to stop reading once there are enough items. */
    private static final class Enough extends SAXException {
        Enough() { super("enough"); }
    }

    static Feed parse(InputStream in, String fallbackSource) throws IOException {
        Handler handler = new Handler();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // No entities from outside the document. Not every parser knows every feature, so each is optional.
            for (String feature : new String[]{
                    "http://xml.org/sax/features/external-general-entities",
                    "http://xml.org/sax/features/external-parameter-entities",
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd"}) {
                try {
                    factory.setFeature(feature, false);
                } catch (Exception ignored) {
                    // Android's expat parser never loads them anyway.
                }
            }
            SAXParser parser = factory.newSAXParser();
            parser.parse(new InputSource(in), handler);
        } catch (Enough enough) {
            // Read as much as we show.
        } catch (SAXException | ParserConfigurationException e) {
            if (handler.items.isEmpty()) throw new IOException("not a feed: " + e.getMessage());
        }
        String source = clean(handler.feedTitle, MAX_SOURCE);
        if (source.isEmpty()) source = fallbackSource;
        List<Headline> items = new ArrayList<>();
        for (Item item : handler.items) {
            String title = clean(item.title, MAX_TITLE);
            if (title.isEmpty()) continue;
            String summary = clean(item.summary, MAX_SUMMARY);
            if (summary.equals(title)) summary = "";
            items.add(new Headline(source, title, summary, date(item.date), link(item.link)));
        }
        return new Feed(source, items);
    }

    private static final class Item {
        String title;
        String summary;
        String date;
        String link;
    }

    private static final class Handler extends DefaultHandler {
        final List<Item> items = new ArrayList<>();
        final List<String> path = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        String feedTitle;
        Item item;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String name = name(localName, qName);
            // Atom's type="xhtml" content is markup inside the element: keep its text together.
            if (insideText()) text.append(' ');
            else text.setLength(0);
            path.add(name);
            if ("item".equals(name) || "entry".equals(name)) item = new Item();
            // Atom: <link href="..."/>, the alternate one (rel left out means alternate).
            if ("link".equals(name) && item != null && path.size() >= 2 && "entry".equals(path.get(path.size() - 2))) {
                String rel = attributes.getValue("rel");
                String href = attributes.getValue("href");
                if (href != null && (rel == null || "alternate".equals(rel)) && item.link == null) item.link = href;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (text.length() < MAX_TEXT) text.append(ch, start, Math.min(length, MAX_TEXT - text.length()));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String name = name(localName, qName);
            if (!path.isEmpty()) path.remove(path.size() - 1);
            if (insideText()) text.append(' ');
            String parent = path.isEmpty() ? "" : path.get(path.size() - 1);
            boolean inItem = item != null && ("item".equals(parent) || "entry".equals(parent));
            if (inItem) {
                switch (name) {
                    case "title":
                        item.title = text.toString();
                        break;
                    case "description":
                    case "summary":
                        item.summary = text.toString();
                        break;
                    case "encoded":   // content:encoded
                    case "content":   // Atom
                        if (item.summary == null || item.summary.trim().isEmpty()) item.summary = text.toString();
                        break;
                    case "pubDate":
                    case "date":      // dc:date
                    case "published":
                        item.date = text.toString();
                        break;
                    case "link":      // RSS: the address as text
                        if (item.link == null && text.toString().trim().length() > 0) item.link = text.toString();
                        break;
                    case "updated":
                        if (item.date == null) item.date = text.toString();
                        break;
                    default:
                        break;
                }
            } else if ("title".equals(name) && feedTitle == null
                    && ("channel".equals(parent) || "feed".equals(parent))) {
                feedTitle = text.toString();
            }
            if (("item".equals(name) || "entry".equals(name)) && item != null) {
                items.add(item);
                item = null;
                if (items.size() >= MAX_ITEMS) throw new Enough();
            }
        }

        private boolean insideText() {
            if (item == null) return false;
            for (String p : path) {
                if ("content".equals(p) || "summary".equals(p) || "description".equals(p)) return true;
            }
            return false;
        }

        private static String name(String localName, String qName) {
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if (name == null) return "";
            int colon = name.indexOf(':');
            return colon >= 0 ? name.substring(colon + 1) : name;
        }
    }

    /** An http(s) address short enough for a QR code a phone reads across the room, or "". */
    static String link(String text) {
        if (text == null) return "";
        String link = text.trim();
        String lower = link.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://")) || link.length() > MAX_LINK) return "";
        for (int i = 0; i < link.length(); i++) if (Character.isWhitespace(link.charAt(i))) return "";
        return link;
    }

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    /** Plain text on one line: HTML tags dropped, entities decoded, at most {@code max} characters. */
    static String clean(String html, int max) {
        if (html == null) return "";
        String text = TAG.matcher(html).replaceAll(" ");
        // Twice: feeds often escape their HTML, so "&amp;amp;" and "&lt;b&gt;" come out of the XML parser.
        for (int pass = 0; pass < 2 && text.indexOf('&') >= 0; pass++) {
            text = TAG.matcher(entities(text)).replaceAll(" ");
        }
        text = SPACE.matcher(text.replace('\u00A0', ' ')).replaceAll(" ").trim();
        if (text.length() <= max) return text;
        int cut = text.lastIndexOf(' ', max - 1);
        return text.substring(0, cut > max / 2 ? cut : max - 1).trim() + "\u2026";
    }

    private static String entities(String text) {
        Matcher m = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            String e = m.group(1);
            String decoded;
            try {
                if (e.startsWith("#x") || e.startsWith("#X")) decoded = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                else if (e.startsWith("#")) decoded = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                else decoded = named(e);
            } catch (IllegalArgumentException bad) {
                decoded = null;
            }
            out.append(decoded != null ? decoded : m.group());
            last = m.end();
        }
        return out.append(text.substring(last)).toString();
    }

    private static String named(String name) {
        switch (name) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return " ";
            case "ndash": return "\u2013";
            case "mdash": return "\u2014";
            case "hellip": return "\u2026";
            case "lsquo": return "\u2018";
            case "rsquo": return "\u2019";
            case "ldquo": return "\u201C";
            case "rdquo": return "\u201D";
            case "aelig": return "\u00E6";
            case "AElig": return "\u00C6";
            case "oslash": return "\u00F8";
            case "Oslash": return "\u00D8";
            case "aring": return "\u00E5";
            case "Aring": return "\u00C5";
            default: return null;
        }
    }

    private static final String[] DATE_FORMATS = {
            "d MMM yyyy HH:mm:ss Z",           // RSS, without its weekday: 25 Oct 2026 14:05:00 +0200
            "d MMM yyyy HH:mm:ss z",           // ... GMT
            "d MMM yyyy HH:mm Z",
            "d MMM yyyy HH:mm z",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",    // Atom: 2026-10-25T14:05:00.000+02:00 or ...Z
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mmXXX",
    };

    /** A feed date in ms, or 0 when it is missing or in no format we know. */
    static long date(String text) {
        if (text == null) return 0;
        String s = SPACE.matcher(text.trim()).replaceAll(" ");
        if (s.isEmpty()) return 0;
        // The weekday is dropped: feeds get it wrong now and then, and the date says it anyway.
        s = s.replaceFirst("^[A-Za-z]+,\\s*", "");
        // Fractions of a second come in any length; keep three digits so one pattern reads them all.
        s = s.replaceFirst("(T\\d\\d:\\d\\d:\\d\\d)\\.(\\d{1,3})\\d*", "$1.$2")
                .replaceFirst("(T\\d\\d:\\d\\d:\\d\\d\\.)(\\d)(?!\\d)", "$1$200")
                .replaceFirst("(T\\d\\d:\\d\\d:\\d\\d\\.)(\\d\\d)(?!\\d)", "$1$20");
        for (String format : DATE_FORMATS) {
            SimpleDateFormat f = new SimpleDateFormat(format, Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            f.setLenient(false);
            try {
                return f.parse(s).getTime();
            } catch (ParseException ignored) {
                // next format
            }
        }
        return 0;
    }
}

package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.util.AtomicFile;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Module cards (docs/specs/card.md): an installed app can declare a card for its tile, with its own
 * label, caption and glow colour. Nothing is shown until the TV owner says so on the prompt; the
 * decision is pinned to the app's signing certificate in the launcher's private storage.
 *
 * Discovery, parsing, the signer digest and the decision file live here, with no UI code, so the
 * part that reads untrusted input can be read on its own. Every module input is bounded and every
 * card is read inside its own try: a bad card is dropped with one log line, never a crash.
 *
 * No regular expressions and nothing in a static initialiser that can throw: the launcher's regex
 * engine (ICU) differs from the desktop JVM's, and a failure while loading this class would take
 * Home down. Every check is a plain character loop; see {@link CardFeature} for the guard around
 * every call into this class.
 */
final class Cards {

    /** One place for the names a module declares; see docs/specs/card.md. */
    static final String CATEGORY = "com.gathin.tvhome.category.DOCK_CARD";
    static final String META_DATA = "com.gathin.tvhome.card";
    static final int SPEC_SUPPORTED = 1;
    /** logcat tag for every rejection: `logcat -s TvHomeCards`. */
    static final String TAG = "TvHomeCards";

    // Limits. Declared: cards read per resume. Shown: cards rendered as tiles. Records: decisions remembered.
    static final int MAX_DECLARED = 32;
    static final int MAX_SHOWN = 8;
    static final int MAX_RECORDS = 128;
    // XML: events walked and nesting depth before the card is rejected; attributes read on the root.
    static final int MAX_EVENTS = 64;
    static final int MAX_DEPTH = 2;
    static final int MAX_ATTRIBUTES = 16;
    static final int LABEL_MAX = 24;
    static final int CAPTION_MAX = 32;

    static final String STORE_FILE = "cards.json";
    static final String DECISION_SHOW = "show";
    static final String DECISION_HIDE = "hide";

    /** Label and caption punctuation; letters, digits and the space are the rest of the alphabet (ASCII only). */
    private static final String TEXT_PUNCTUATION = ".&+'-";

    /** Words that imply endorsement: rejected anywhere in a label (folded form). */
    private static final String[] RESERVED_ANYWHERE = {"verified", "official"};
    /** Names the launcher's own UI renders (pills, tiles, the Inputs menu): rejected as a whole label. */
    private static final Set<String> RESERVED_EXACT = new HashSet<>(Arrays.asList(
            "tvvibehack", "cleanyourtv", "settings", "inputs", "livetv", "home", "defaulthome", "wifi", "av"));
    /** "HDMI", "HDMI 1", "HDMI2"... */
    private static final String RESERVED_PREFIX = "hdmi";

    enum State {
        /** No record for this package: the prompt is due. */
        NEW,
        /** The record was made for a different signing key: it no longer applies and the prompt is due. */
        RESIGNED,
        /** The card's label changed since the owner decided: the decision was for another name, the prompt is due. */
        RELABELLED,
        SHOWN,
        HIDDEN
    }

    /** A valid card found on this TV, with the owner's decision for its current signer. */
    static final class Card {
        final String pkg;
        final ComponentName component;
        final String label;
        /** The app's own label, the one Settings shows. */
        final String appLabel;
        /** Null when the card has none. */
        final String caption;
        /** Opaque ARGB, or 0 when the card sets none. */
        final int accent;
        /** SHA-256 of the signing certificate, 64 lower case hex characters. */
        final String digest;
        final State state;

        Card(String pkg, ComponentName component, String label, String appLabel, String caption, int accent,
             String digest, State state) {
            this.pkg = pkg;
            this.component = component;
            this.label = label;
            this.appLabel = appLabel;
            this.caption = caption;
            this.accent = accent;
            this.digest = digest;
            this.state = state;
        }

        boolean pending() {
            return state == State.NEW || state == State.RESIGNED || state == State.RELABELLED;
        }
    }

    /**
     * The owner's decision for one package, valid only while the package is signed with this key and
     * its card keeps the name (folded) it had when the owner decided.
     */
    static final class Record {
        final String pkg;
        final String digest;
        /** The card's label as {@link #fold} wrote it at decision time. */
        final String label;
        final String decision;
        final long firstSeen;
        final long decidedAt;

        Record(String pkg, String digest, String label, String decision, long firstSeen, long decidedAt) {
            this.pkg = pkg;
            this.digest = digest;
            this.label = label;
            this.decision = decision;
            this.firstSeen = firstSeen;
            this.decidedAt = decidedAt;
        }
    }

    /** The attributes of a `<card>` element after the limits were checked. */
    static final class Declared {
        final int spec;
        final String label;
        final String caption;
        final int accent;
        /** live="true": the app also serves widget rows at content://<package>.card (docs/specs/widget-source.md). */
        final boolean live;

        Declared(int spec, String label, String caption, int accent, boolean live) {
            this.spec = spec;
            this.label = label;
            this.caption = caption;
            this.accent = accent;
            this.live = live;
        }
    }

    /** A card rejected for a stated reason; the reason goes to logcat under {@link #TAG}. */
    static final class CardException extends Exception {
        CardException(String reason) {
            super(reason);
        }
    }

    private Cards() {
    }

    // ---- discovery -------------------------------------------------------------------------------

    /**
     * Every valid card installed on this TV, with its state under the given records. One cheap
     * PackageManager query when no app declares a card; the app list is only walked when one does.
     * hidden is the launcher's hidden_apps as in force (config.xml or a theme overlay): no card for those.
     * The decision records are only read when some app declares a card, and leanback is the launcher's
     * own LEANBACK_LAUNCHER query, so the app list isn't queried twice.
     */
    static List<Card> discover(Context ctx, Set<String> hidden, Supplier<Map<String, Record>> recordSource,
                               List<ResolveInfo> leanback) {
        if ((ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) selfCheck();
        List<Card> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> declared;
        try {
            declared = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(CATEGORY),
                    PackageManager.GET_META_DATA);
        } catch (Exception e) {
            Log.w(TAG, "card query failed: " + e);
            return out;
        }
        if (declared.isEmpty()) return out;
        Map<String, Record> records = recordSource.get();

        // What the card must agree with: the activities shown as tiles, and the names already on screen.
        Set<String> tiles = new HashSet<>();
        Map<String, Set<String>> names = new HashMap<>();
        for (ResolveInfo ri : leanback) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            tiles.add(new ComponentName(pkg, ri.activityInfo.name).flattenToString());
            try {
                addName(names, String.valueOf(ri.loadLabel(pm)), pkg);
                addName(names, String.valueOf(ri.activityInfo.applicationInfo.loadLabel(pm)), pkg);
            } catch (Exception e) {
                Log.w(TAG, pkg + ": label unreadable, " + e);   // an app with broken resources; not a card problem
            }
        }
        Set<String> uiNames = uiNames(ctx.getResources());
        Set<String> seen = new HashSet<>();

        int n = 0;
        for (ResolveInfo ri : declared) {
            if (++n > MAX_DECLARED) {
                Log.w(TAG, "more than " + MAX_DECLARED + " cards declared; the rest are ignored");
                break;
            }
            String pkg = ri.activityInfo != null ? ri.activityInfo.packageName : "?";
            try {
                Card card = read(ctx, pm, ri, tiles, names, uiNames, hidden, seen, records);
                seen.add(card.pkg);
                out.add(card);
            } catch (CardException e) {
                Log.w(TAG, pkg + ": " + e.getMessage());
            } catch (Exception e) {
                // Package gone between the query and the read, a resource that is not XML, and anything else.
                Log.w(TAG, pkg + ": " + e);
            }
        }

        List<Card> kept = resolveClashes(out, records);
        for (Card c : out) {
            if (!kept.contains(c)) Log.w(TAG, c.pkg + ": another card is also named '" + c.label + "'");
        }
        return kept;
    }

    /**
     * Cards that share a folded label: the one the owner already decided on keeps its name (the
     * oldest decision wins, then the smaller package name), every other card in the group is
     * dropped. A group without a decided card is dropped entirely, so nobody is asked about a
     * contested name. A decision the owner made is never undone by a newer install.
     */
    static List<Card> resolveClashes(List<Card> cards, Map<String, Record> records) {
        Map<String, List<Card>> groups = new HashMap<>();
        for (Card c : cards) {
            String key = fold(c.label);
            List<Card> group = groups.get(key);
            if (group == null) groups.put(key, group = new ArrayList<>());
            group.add(c);
        }
        List<Card> kept = new ArrayList<>();
        for (Card c : cards) {
            List<Card> group = groups.get(fold(c.label));
            if (group.size() == 1) {
                kept.add(c);
                continue;
            }
            Card winner = null;
            for (Card g : group) {
                if (g.pending()) continue;
                if (winner == null || olderDecision(g, winner, records)) winner = g;
            }
            if (winner == c) kept.add(c);
        }
        return kept;
    }

    /** True when a's record was made before b's (tie: smaller package name). */
    private static boolean olderDecision(Card a, Card b, Map<String, Record> records) {
        Record ra = records.get(a.pkg), rb = records.get(b.pkg);
        long fa = ra != null ? ra.firstSeen : Long.MAX_VALUE, fb = rb != null ? rb.firstSeen : Long.MAX_VALUE;
        if (fa != fb) return fa < fb;
        return a.pkg.compareTo(b.pkg) < 0;
    }

    private static Card read(Context ctx, PackageManager pm, ResolveInfo ri, Set<String> tiles,
                             Map<String, Set<String>> names, Set<String> uiNames, Set<String> hidden,
                             Set<String> seen, Map<String, Record> records) throws Exception {
        ActivityInfo ai = ri.activityInfo;
        if (ai == null) throw new CardException("not an activity");
        String pkg = ai.packageName;
        if (pkg.equals(ctx.getPackageName())) throw new CardException("the launcher itself");
        if (hidden.contains(pkg)) throw new CardException("listed in hidden_apps");
        if (seen.contains(pkg)) throw new CardException("declares a second card; one per package is read");
        if (!ai.exported || !ai.enabled || !ai.applicationInfo.enabled) {
            throw new CardException("card activity is not exported and enabled");
        }
        ComponentName component = new ComponentName(pkg, ai.name);
        if (!tiles.contains(component.flattenToString())) {
            throw new CardException("card activity has no LEANBACK_LAUNCHER intent filter");
        }

        XmlResourceParser xml = ai.loadXmlMetaData(pm, META_DATA);
        if (xml == null) throw new CardException("no <meta-data android:name=\"" + META_DATA + "\" android:resource=...>");
        Declared d;
        try {
            d = parse(xml);
        } finally {
            xml.close();
        }
        if (d.spec != SPEC_SUPPORTED) throw new CardException("spec " + d.spec + " is not supported (this launcher reads spec " + SPEC_SUPPORTED + ")");

        String digest = digest(pm, pkg);
        String folded = fold(d.label);
        Record r = records.get(pkg);
        State state;
        if (r == null) state = State.NEW;
        else if (!r.digest.equals(digest)) state = State.RESIGNED;
        else if (!r.label.equals(folded)) state = State.RELABELLED;   // the owner said yes to another name
        else state = DECISION_SHOW.equals(r.decision) ? State.SHOWN : State.HIDDEN;
        boolean pending = state == State.NEW || state == State.RESIGNED || state == State.RELABELLED;

        // Reserved words are policy and always apply. Name collisions only matter while the prompt is
        // due: they stop a newcomer from borrowing a name, and never undo a decision the owner made.
        // A decided card that changes its label becomes pending again (RELABELLED), so it cannot take
        // another app's name on an update either.
        if (isReserved(folded, uiNames)) throw new CardException("label '" + d.label + "' is reserved");
        if (pending && isTaken(folded, pkg, names)) throw new CardException("label '" + d.label + "' is the name of another installed app");
        String appLabel = cleanLabel(String.valueOf(ai.applicationInfo.loadLabel(pm)), pkg);
        if (pending && isTaken(fold(appLabel), pkg, names)) throw new CardException("app label '" + appLabel + "' is the name of another installed app");

        return new Card(pkg, component, d.label, appLabel, d.caption, d.accent, digest, state);
    }

    private static void addName(Map<String, Set<String>> names, String label, String pkg) {
        String key = fold(label);
        if (key.isEmpty()) return;
        Set<String> pkgs = names.get(key);
        if (pkgs == null) names.put(key, pkgs = new HashSet<>());
        pkgs.add(pkg);
    }

    /** The launcher's own UI names in the TV's language, folded. */
    private static Set<String> uiNames(Resources res) {
        int[] ids = {R.string.app_name, R.string.settings, R.string.inputs, R.string.choose_input, R.string.wifi,
                R.string.live_tv, R.string.home_settings, R.string.app_library, R.string.dream_name};
        Set<String> out = new HashSet<>();
        for (int id : ids) out.add(fold(res.getString(id)));
        return out;
    }

    /** Shown cards in the order they take on screen: by label, at most {@link #MAX_SHOWN}. */
    static List<Card> shown(List<Card> cards) {
        List<Card> out = new ArrayList<>();
        for (Card c : cards) if (c.state == State.SHOWN) out.add(c);
        Collections.sort(out, Cards::byLabel);
        return out.size() > MAX_SHOWN ? new ArrayList<>(out.subList(0, MAX_SHOWN)) : out;
    }

    /** The card whose prompt is due next, or null. Package order, so repeated prompts come in one order. */
    static Card firstPending(List<Card> cards) {
        Card best = null;
        for (Card c : cards) {
            if (c.pending() && (best == null || c.pkg.compareTo(best.pkg) < 0)) best = c;
        }
        return best;
    }

    static int byLabel(Card a, Card b) {
        int c = a.label.compareToIgnoreCase(b.label);
        return c != 0 ? c : a.pkg.compareTo(b.pkg);
    }

    /**
     * {package, card label} of every app whose valid card says live="true": the home widget's add-on sources. No
     * Show prompt is needed here; a source shows only once the owner ticks it in Launcher settings.
     */
    static List<String[]> liveSources(Context ctx) {
        List<String[]> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        try {
            List<ResolveInfo> declared = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(CATEGORY),
                    PackageManager.GET_META_DATA);
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : declared) {
                ActivityInfo ai = ri.activityInfo;
                if (out.size() >= MAX_DECLARED || ai == null || !ai.exported || !ai.enabled) continue;
                if (ai.packageName.equals(ctx.getPackageName()) || !seen.add(ai.packageName)) continue;
                XmlResourceParser xml = ai.loadXmlMetaData(pm, META_DATA);
                if (xml == null) continue;
                try {
                    Declared d = parse(xml);
                    if (d.spec == SPEC_SUPPORTED && d.live) out.add(new String[]{ai.packageName, d.label});
                } catch (Exception e) {
                    Log.w(TAG, ai.packageName + ": " + e.getMessage());
                } finally {
                    xml.close();
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "live card query failed: " + e);
        }
        return out;
    }

    // ---- parser ----------------------------------------------------------------------------------

    /**
     * Reads one `<card spec="1" label=".." caption=".." accent="#RRGGBB"/>`. The document may hold at
     * most {@link #MAX_EVENTS} events and nest at most {@link #MAX_DEPTH} deep; child elements and
     * unknown attributes are ignored. Rejects, never guesses.
     */
    static Declared parse(XmlPullParser p) throws Exception {
        int events = 0;
        Declared found = null;
        for (int type = p.getEventType(); type != XmlPullParser.END_DOCUMENT; type = p.next()) {
            if (++events > MAX_EVENTS) throw new CardException("more than " + MAX_EVENTS + " XML events");
            if (type != XmlPullParser.START_TAG) continue;
            if (p.getDepth() > MAX_DEPTH) throw new CardException("XML nested deeper than " + MAX_DEPTH);
            if (p.getDepth() != 1) continue;
            if (found != null) throw new CardException("more than one root element");
            String name = p.getName();
            if (!"card".equals(name)) throw new CardException("root element is <" + name + ">, expected <card>");
            found = readCard(p);
        }
        if (found == null) throw new CardException("no <card> element");
        return found;
    }

    private static Declared readCard(XmlPullParser p) throws Exception {
        int count = p.getAttributeCount();
        if (count > MAX_ATTRIBUTES) throw new CardException("more than " + MAX_ATTRIBUTES + " attributes on <card>");
        String spec = null, label = null, caption = null, accent = null, live = null;
        for (int i = 0; i < count; i++) {
            String ns = p.getAttributeNamespace(i);
            if (ns != null && !ns.isEmpty()) continue;
            String name = p.getAttributeName(i);
            if (name == null || name.length() > 8) continue;
            switch (name) {
                case "spec":
                    spec = p.getAttributeValue(i);
                    break;
                case "label":
                    label = p.getAttributeValue(i);
                    break;
                case "caption":
                    caption = p.getAttributeValue(i);
                    break;
                case "accent":
                    accent = p.getAttributeValue(i);
                    break;
                case "live":
                    live = p.getAttributeValue(i);
                    break;
                default:
                    break;
            }
        }
        // Lengths first, before any pattern or comparison touches the value.
        // The compiler stores spec="1" as an integer; the parser hands it back as the decimal text "1".
        if (spec == null || spec.isEmpty() || spec.length() > 3 || !isDigits(spec)) throw new CardException("spec is missing or not a small integer");
        String reason = checkLabel(label);
        if (reason != null) throw new CardException(reason);
        reason = checkCaption(caption);
        if (reason != null) throw new CardException(reason);
        int colour = 0;
        if (accent != null && !accent.isEmpty()) {
            if (isReference(accent)) throw new CardException("accent is a resource reference; write #RRGGBB itself");
            colour = parseAccent(accent);
            if (colour == 0) throw new CardException("accent is not #RRGGBB");
        }
        return new Declared(Integer.parseInt(spec), label, caption == null || caption.isEmpty() ? null : caption, colour,
                "true".equals(live));
    }

    // ---- validators (pure Java, unit tested) -----------------------------------------------------

    /** Null when the label is acceptable, else the reason. */
    static String checkLabel(String label) {
        if (label == null || label.isEmpty()) return "label is missing";
        if (label.length() > LABEL_MAX) return "label is longer than " + LABEL_MAX + " characters";
        if (isReference(label)) return "label is a resource reference; write the text itself";
        if (!isText(label)) return "label has characters outside A-Z a-z 0-9 space . & + ' -";
        if (label.charAt(0) == ' ' || label.charAt(label.length() - 1) == ' ') return "label starts or ends with a space";
        if (fold(label).isEmpty()) return "label has no letter or digit";
        return null;
    }

    /** Null when the caption is acceptable (absent counts as acceptable), else the reason. */
    static String checkCaption(String caption) {
        if (caption == null || caption.isEmpty()) return null;
        if (caption.length() > CAPTION_MAX) return "caption is longer than " + CAPTION_MAX + " characters";
        if (isReference(caption)) return "caption is a resource reference; write the text itself";
        if (!isText(caption)) return "caption has characters outside A-Z a-z 0-9 space . & + ' -";
        if (caption.charAt(0) == ' ' || caption.charAt(caption.length() - 1) == ' ') return "caption starts or ends with a space";
        String folded = fold(caption);
        for (String word : RESERVED_ANYWHERE) if (folded.contains(word)) return "caption claims endorsement ('" + word + "')";
        return null;
    }

    /**
     * An app's own label as the prompt shows it: control, format (bidi overrides, zero width joiners),
     * line and paragraph separator characters become spaces, runs of spaces collapse, at most 40
     * characters (never cutting a surrogate pair), the fallback (package name) when nothing is left.
     */
    static String cleanLabel(String s, String fallback) {
        if (s == null) return fallback;
        StringBuilder b = new StringBuilder(Math.min(s.length(), 48));
        boolean space = true;   // leading spaces are dropped too
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int type = Character.getType(c);
            boolean blank = c == ' ' || Character.isISOControl(c) || Character.isWhitespace(c) || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.SPACE_SEPARATOR;
            if (blank) {
                if (!space) b.append(' ');
                space = true;
                continue;
            }
            boolean pair = Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1));
            if (b.length() + (pair ? 2 : 1) > 40) break;
            b.append(c);
            if (pair) b.append(s.charAt(++i));
            space = false;
        }
        String out = b.toString().trim();
        return out.isEmpty() ? fallback : out;
    }

    /**
     * A compiled `@string/x` or `?attr/x` reference: the resource compiler stores it as an id and the
     * parser hands back "@2131..." or "?2130...". Spec 1 takes literal text only.
     */
    static boolean isReference(String value) {
        return value.startsWith("@") || value.startsWith("?");
    }

    /** Lower case with everything but ASCII letters and digits removed: the form every name comparison uses. */
    static String fold(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
        }
        return b.toString();
    }

    /** True when a folded label claims endorsement or a name the launcher's UI uses (uiNames are folded too). */
    static boolean isReserved(String folded, Set<String> uiNames) {
        if (folded.isEmpty()) return true;
        for (String word : RESERVED_ANYWHERE) if (folded.contains(word)) return true;
        if (folded.startsWith(RESERVED_PREFIX)) return true;
        return RESERVED_EXACT.contains(folded) || uiNames.contains(folded);
    }

    /** True when another package than pkg already uses this folded name. */
    static boolean isTaken(String folded, String pkg, Map<String, Set<String>> names) {
        Set<String> pkgs = names.get(folded);
        if (pkgs == null) return false;
        for (String p : pkgs) if (!p.equals(pkg)) return true;
        return false;
    }

    /** Opaque ARGB for `#RRGGBB` (or the compiled `#AARRGGBB` form, alpha dropped), 0 when it is anything else. */
    static int parseAccent(String s) {
        if (s == null || (s.length() != 7 && s.length() != 9) || s.charAt(0) != '#') return 0;
        String hex = s.substring(1);
        if (!isHex(hex, false)) return 0;
        // `#RRGGBB` as written, or `#AARRGGBB` as the resource compiler stores a colour attribute (it
        // compiles `#E8A33D` to the colour value and the parser hands back `#ffe8a33d`). The alpha is dropped.
        return 0xFF000000 | Integer.parseInt(hex.substring(hex.length() - 6), 16);
    }

    // ---- character checks (no regular expressions, see the class comment) -----------------------

    /** Every character is an ASCII letter, digit, space or one of {@link #TEXT_PUNCTUATION}. */
    static boolean isText(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' '
                    || TEXT_PUNCTUATION.indexOf(c) >= 0;
            if (!ok) return false;
        }
        return true;
    }

    static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** Hex digits; lower case only when lowerOnly (the decision file keeps digests lower case). */
    static boolean isHex(String s, boolean lowerOnly) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (!lowerOnly && c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    /** A Java style package name: two or more dot separated segments, each a letter then letters, digits or _. */
    static boolean isPackageName(String s) {
        if (s == null || s.isEmpty() || s.length() > 255) return false;
        int segments = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
            i++;
            while (i < s.length() && s.charAt(i) != '.') {
                c = s.charAt(i);
                if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) return false;
                i++;
            }
            segments++;
            if (i < s.length()) i++;                      // skip the dot; a trailing dot leaves an empty segment below
            else break;
            if (i == s.length()) return false;
        }
        return segments >= 2;
    }

    static boolean isDigest(String s) {
        return s != null && s.length() == 64 && isHex(s, true);
    }

    /** A label exactly as {@link #fold} writes it: 1 to LABEL_MAX characters from a-z 0-9. */
    static boolean isFolded(String s) {
        if (s == null || s.isEmpty() || s.length() > LABEL_MAX) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = digits[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = digits[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** The first 12 hex characters in groups of four, as shown on the prompt: "7f3a 91c0 b2e4". */
    static String shortDigest(String digest) {
        return grouped(digest.length() >= 12 ? digest.substring(0, 12) : digest, 3);
    }

    /** The whole digest in groups of four, eight groups per line, as shown in the Module cards list. */
    static String longDigest(String digest) {
        return grouped(digest, 8);
    }

    private static String grouped(String hex, int groupsPerLine) {
        StringBuilder b = new StringBuilder(hex.length() + hex.length() / 4 + 2);
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) b.append(i / 4 % groupsPerLine == 0 ? '\n' : ' ');
            b.append(hex, i, Math.min(hex.length(), i + 4));
        }
        return b.toString();
    }

    // ---- signer ----------------------------------------------------------------------------------

    /**
     * SHA-256 of the DER certificate of the package's one signer: the value apksigner verify (with
     * print-certs) prints as the certificate SHA-256 digest. A package with more than one signer
     * is rejected, because the order of its certificates is not defined.
     */
    @SuppressWarnings("deprecation")
    static String digest(PackageManager pm, String pkg) throws Exception {
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo info = pi.signingInfo;
            if (info == null) throw new CardException("package has no signing certificate");
            if (info.hasMultipleSigners()) throw new CardException("package has more than one signer");
            sigs = info.getApkContentsSigners();
        } else {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            sigs = pi.signatures;
        }
        if (sigs == null || sigs.length != 1) {
            throw new CardException("package has " + (sigs == null ? 0 : sigs.length) + " signing certificates, expected one");
        }
        return hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()));
    }

    // ---- decision file (cards.json) --------------------------------------------------------------

    static File storeFile(Context ctx) {
        return new File(ctx.getFilesDir(), STORE_FILE);
    }

    /**
     * The records in cards.json, or an empty map. A file that does not decode is moved aside to
     * cards.json.bad (kept, not deleted) and the launcher continues as if no decision had been made.
     */
    static Map<String, Record> load(File file) {
        if (!file.exists()) return new TreeMap<>();
        String text;
        try {
            text = new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "cannot read " + file.getName() + ": " + e);
            return new TreeMap<>();
        }
        Map<String, Record> records = decode(text);
        if (records == null) {
            Log.w(TAG, file.getName() + " is not valid; moved to " + STORE_FILE + ".bad");
            quarantine(file);
            return new TreeMap<>();
        }
        return records;
    }

    /** Writes the records atomically. False (and a log line) when the write failed. */
    static boolean save(File file, Map<String, Record> records) {
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = af.startWrite();
            out.write(encode(records).getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "cannot write " + file.getName() + ": " + e);
            if (out != null) af.failWrite(out);
            return false;
        }
    }

    /** Records the owner's decision for the card's package under its current signer and saves. */
    static boolean decide(File file, Card card, boolean show) {
        Map<String, Record> records = load(file);
        Record old = records.get(card.pkg);
        long now = System.currentTimeMillis();
        long firstSeen = old != null && old.digest.equals(card.digest) ? old.firstSeen : now;
        records.put(card.pkg, new Record(card.pkg, card.digest, fold(card.label), show ? DECISION_SHOW : DECISION_HIDE, firstSeen, now));
        return save(file, records);
    }

    /** Moves a bad decision file to `<name>.bad`, replacing an older .bad. Returns true when the file is out of the way. */
    static boolean quarantine(File file) {
        File bad = new File(file.getParentFile(), file.getName() + ".bad");
        if (bad.exists() && !bad.delete()) return false;
        return file.renameTo(bad) || file.delete();
    }

    private static final String HEADER = "{\"version\":2,\"records\":[";
    private static final String FOOTER = "]}";
    private static final String F_PACKAGE = "{\"package\":\"";
    private static final String F_DIGEST = "\",\"signer_sha256\":\"";
    private static final String F_LABEL = "\",\"label\":\"";
    private static final String F_DECISION = "\",\"decision\":\"";
    private static final String F_FIRST_SEEN = "\",\"first_seen\":";
    private static final String F_DECIDED_AT = ",\"decided_at\":";

    /**
     * cards.json: one record per line, in the exact layout {@link #encode} writes. Anything else,
     * including more than {@link #MAX_RECORDS} records or a package listed twice, is invalid (null).
     */
    static Map<String, Record> decode(String text) {
        if (text == null || text.length() > 64 * 1024) return null;
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (lines.size() < 2 || !lines.get(0).equals(HEADER)) return null;
        int last = lines.size() - 1;
        if (lines.get(last).isEmpty()) last -= 1;           // a trailing newline is fine
        if (last < 1 || !lines.get(last).equals(FOOTER)) return null;
        int count = last - 1;
        if (count > MAX_RECORDS) return null;
        Map<String, Record> out = new TreeMap<>();
        for (int i = 1; i < last; i++) {
            String line = lines.get(i);
            boolean lastRecord = i == last - 1;
            if (!lastRecord) {
                if (!line.endsWith(",")) return null;
                line = line.substring(0, line.length() - 1);
            }
            Record r = decodeRecord(line);
            if (r == null || out.containsKey(r.pkg)) return null;
            out.put(r.pkg, r);
        }
        return out;
    }

    /** One record line, or null when it is not exactly what {@link #encode} writes. */
    static Record decodeRecord(String line) {
        int[] at = {0};
        if (!take(line, at, F_PACKAGE)) return null;
        String pkg = until(line, at, '"');
        if (pkg == null || !isPackageName(pkg) || !take(line, at, F_DIGEST)) return null;
        String digest = until(line, at, '"');
        if (digest == null || !isDigest(digest) || !take(line, at, F_LABEL)) return null;
        String label = until(line, at, '"');
        if (label == null || !isFolded(label) || !take(line, at, F_DECISION)) return null;
        String decision = until(line, at, '"');
        if (decision == null || !(DECISION_SHOW.equals(decision) || DECISION_HIDE.equals(decision))) return null;
        if (!take(line, at, F_FIRST_SEEN)) return null;
        String firstSeen = until(line, at, ',');
        if (firstSeen == null || firstSeen.length() > 18 || !isDigits(firstSeen)) return null;
        if (!take(line, at, F_DECIDED_AT)) return null;
        String decidedAt = until(line, at, '}');
        if (decidedAt == null || decidedAt.length() > 18 || !isDigits(decidedAt)) return null;
        if (!take(line, at, "}") || at[0] != line.length()) return null;
        return new Record(pkg, digest, label, decision, Long.parseLong(firstSeen), Long.parseLong(decidedAt));
    }

    /** Consumes the literal at the cursor, or returns false. */
    private static boolean take(String s, int[] at, String literal) {
        if (!s.startsWith(literal, at[0])) return false;
        at[0] += literal.length();
        return true;
    }

    /** The text from the cursor up to the next stop character, which stays for the next literal; null when there is none. */
    private static String until(String s, int[] at, char stop) {
        int end = s.indexOf(stop, at[0]);
        if (end < 0) return null;
        String out = s.substring(at[0], end);
        at[0] = end;
        return out;
    }

    /** The decision file text for these records, by package; the oldest decisions are dropped beyond {@link #MAX_RECORDS}. */
    static String encode(Map<String, Record> records) {
        List<Record> list = new ArrayList<>(records.values());
        if (list.size() > MAX_RECORDS) {
            Collections.sort(list, (a, b) -> Long.compare(b.decidedAt, a.decidedAt));
            list = new ArrayList<>(list.subList(0, MAX_RECORDS));
        }
        Collections.sort(list, (a, b) -> a.pkg.compareTo(b.pkg));
        StringBuilder b = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < list.size(); i++) {
            Record r = list.get(i);
            // Every field is validated before it gets here, so no JSON escaping is ever needed.
            if (!isPackageName(r.pkg) || !isDigest(r.digest) || !isFolded(r.label)
                    || !(DECISION_SHOW.equals(r.decision) || DECISION_HIDE.equals(r.decision))
                    || r.firstSeen < 0 || r.decidedAt < 0) {
                throw new IllegalArgumentException("record for " + r.pkg + " is not valid");
            }
            b.append(F_PACKAGE).append(r.pkg).append(F_DIGEST).append(r.digest).append(F_LABEL).append(r.label).append(F_DECISION).append(r.decision)
                    .append(F_FIRST_SEEN).append(r.firstSeen).append(F_DECIDED_AT).append(r.decidedAt).append('}');
            if (i < list.size() - 1) b.append(',');
            b.append('\n');
        }
        return b.append(FOOTER).append('\n').toString();
    }

    /** The folded forms of the English UI names, for callers without resources (tests). */
    static Set<String> englishUiNames() {
        return new HashSet<>(Arrays.asList(fold("Home"), fold("Settings"), fold("Inputs"), fold("Wi-Fi"),
                fold("Live TV"), fold("Default home"), fold("Clock")));
    }

    // ---- self-check ------------------------------------------------------------------------------

    private static boolean selfChecked;

    /**
     * Debuggable builds only, once per process: runs the validators and the decision file codec on fixed
     * input and logs "self-check ok" or what differs, so a device run shows at once whether this
     * class behaves on the TV's runtime the way the JVM tests say it does.
     */
    static void selfCheck() {
        if (selfChecked) return;
        selfChecked = true;
        List<String> bad = new ArrayList<>();
        if (checkLabel("Retro") != null) bad.add("label Retro rejected");
        if (checkLabel("Verified\u202E") == null) bad.add("control character accepted");
        if (checkCaption("NES games") != null) bad.add("caption rejected");
        if (parseAccent("#E8A33D") != 0xFFE8A33D || parseAccent("#ffe8a33d") != 0xFFE8A33D) bad.add("accent");
        if (!"livetv".equals(fold("Live TV")) || !isReserved("hdmi2", englishUiNames())) bad.add("fold or reserved");
        if (!"Netflix".equals(cleanLabel("Netflix\u202E\n", "x"))) bad.add("cleanLabel");
        if (!isPackageName("io.example.a") || isPackageName("example")) bad.add("package name");
        Map<String, Record> one = new TreeMap<>();
        String digest = hex(new byte[32]);
        one.put("io.example.a", new Record("io.example.a", digest, "a", DECISION_SHOW, 1, 2));
        Map<String, Record> back = decode(encode(one));
        if (back == null || back.size() != 1 || back.get("io.example.a").decidedAt != 2) bad.add("store round trip");
        if (decode("{}") != null) bad.add("bad store accepted");
        Log.w(TAG, bad.isEmpty() ? "self-check ok" : "self-check FAILED: " + bad);
    }
}

package com.gathin.tvhome;

import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Plain JVM tests for the module card validators, the parser limits and the decision file format. */
public class CardsTest {

    private static final String DIGEST_A = "7f3a91c0b2e4000000000000000000000000000000000000000000000000abcd";
    private static final String DIGEST_B = "0202281bb726dbc12b82622dee50a278656128a82204db3cf055f8f5c006e405";

    // ---- labels ----------------------------------------------------------------------------------

    @Test
    public void labelAcceptsTheSpecAlphabet() {
        assertNull(Cards.checkLabel("Retro"));
        assertNull(Cards.checkLabel("NES & SNES games"));
        assertNull(Cards.checkLabel("Kid's TV+ 2.0-beta"));
        assertNull(Cards.checkLabel("123456789012345678901234"));   // 24 characters
    }

    @Test
    public void labelRejectsLengthAndCharacters() {
        assertNotNull(Cards.checkLabel(null));
        assertNotNull(Cards.checkLabel(""));
        assertNotNull(Cards.checkLabel("1234567890123456789012345"));   // 25
        assertNotNull(Cards.checkLabel("Netflіx"));                // Cyrillic i look-alike
        assertNotNull(Cards.checkLabel("Retro\n"));
        assertNotNull(Cards.checkLabel("Retro\u0000"));
        assertNotNull(Cards.checkLabel("Retro\u202E"));                  // bidi override
        assertNotNull(Cards.checkLabel("Retro\u200D"));                  // zero width joiner
        assertNotNull(Cards.checkLabel("Retro!"));
        assertNotNull(Cards.checkLabel(" Retro"));
        assertNotNull(Cards.checkLabel("Retro "));
        assertNotNull(Cards.checkLabel("+.&"));                         // no letter or digit
        assertNotNull(Cards.checkLabel("@2131820545"));                 // a compiled @string reference
        assertNotNull(Cards.checkLabel("?2130968576"));                 // a compiled ?attr reference
    }

    @Test
    public void captionIsOptionalAndBounded() {
        assertNull(Cards.checkCaption(null));
        assertNull(Cards.checkCaption(""));
        assertNull(Cards.checkCaption("NES games"));
        assertNull(Cards.checkCaption("12345678901234567890123456789012"));      // 32
        assertNotNull(Cards.checkCaption("123456789012345678901234567890123"));  // 33
        assertNotNull(Cards.checkCaption("gamesé"));
        assertNotNull(Cards.checkCaption("NES\tgames"));
        assertNotNull(Cards.checkCaption("NES\u202Egames"));
        assertNotNull(Cards.checkCaption("@2131820545"));
    }

    @Test
    public void foldDropsCaseSpacesAndPunctuation() {
        assertEquals("retro", Cards.fold("Retro"));
        assertEquals("livetv", Cards.fold("Live TV"));
        assertEquals("tvvibehack", Cards.fold("tv-vibehack"));
        assertEquals("kidstv20", Cards.fold("Kid's TV+ 2.0"));
        assertEquals("", Cards.fold(null));
        assertEquals("netflx", Cards.fold("Netflіx"));   // non ASCII letters never match anything
    }

    @Test
    public void reservedWordsAreSubstringsOrWholeNames() {
        Set<String> ui = Cards.englishUiNames();
        assertTrue(Cards.isReserved(Cards.fold("Verified Retro"), ui));
        assertTrue(Cards.isReserved(Cards.fold("Retro (Official)"), ui));
        assertTrue(Cards.isReserved(Cards.fold("official"), ui));
        assertTrue(Cards.isReserved(Cards.fold("Settings"), ui));
        assertTrue(Cards.isReserved(Cards.fold("S E T T I N G S"), ui));
        assertTrue(Cards.isReserved(Cards.fold("Inputs"), ui));
        assertTrue(Cards.isReserved(Cards.fold("Live TV"), ui));
        assertTrue(Cards.isReserved(Cards.fold("Home"), ui));
        assertTrue(Cards.isReserved(Cards.fold("HDMI"), ui));
        assertTrue(Cards.isReserved(Cards.fold("HDMI 2"), ui));
        assertTrue(Cards.isReserved(Cards.fold("AV"), ui));
        assertTrue(Cards.isReserved(Cards.fold("tv-vibehack"), ui));
        assertFalse(Cards.isReserved(Cards.fold("Xbox"), ui));
        assertTrue(Cards.isReserved("", ui));
        assertFalse(Cards.isReserved(Cards.fold("Retro"), ui));
        assertFalse(Cards.isReserved(Cards.fold("Retro Settings Panel"), ui));   // equality, not substring
    }

    @Test
    public void translatedUiNamesCountToo() {
        Set<String> ui = new HashSet<>(Arrays.asList(Cards.fold("Einstellungen"), Cards.fold("Eingänge")));
        assertTrue(Cards.isReserved(Cards.fold("Einstellungen"), ui));
        assertFalse(Cards.isReserved(Cards.fold("Settings panel"), ui));
    }

    @Test
    public void labelTakenByAnotherPackageOnly() {
        Map<String, Set<String>> names = new HashMap<>();
        names.put("retro", new HashSet<>(Arrays.asList("io.github.udhaykumarbala.retro")));
        names.put("netflix", new HashSet<>(Arrays.asList("com.netflix.ninja")));
        assertFalse(Cards.isTaken("retro", "io.github.udhaykumarbala.retro", names));   // its own app label
        assertTrue(Cards.isTaken("retro", "io.example.spoof", names));
        assertTrue(Cards.isTaken("netflix", "io.example.spoof", names));
        assertFalse(Cards.isTaken("retrogames", "io.example.spoof", names));
    }

    // ---- accent and digests ----------------------------------------------------------------------

    @Test
    public void accentAcceptsWrittenAndCompiledForms() {
        assertEquals(0xFFE8A33D, Cards.parseAccent("#E8A33D"));
        assertEquals(0xFFE8A33D, Cards.parseAccent("#e8a33d"));
        assertEquals(0xFFE8A33D, Cards.parseAccent("#ffe8a33d"));   // what the resource compiler hands back
        assertEquals(0xFFE8A33D, Cards.parseAccent("#80E8A33D"));   // alpha is dropped
        assertEquals(0, Cards.parseAccent("E8A33D"));
        assertEquals(0, Cards.parseAccent("#E8A"));
        assertEquals(0, Cards.parseAccent("#GGGGGG"));
        assertEquals(0, Cards.parseAccent("#E8A33D "));
        assertEquals(0, Cards.parseAccent("@2131099648"));
        assertEquals(0, Cards.parseAccent(null));
        assertEquals(0, Cards.parseAccent("#ffffffffffffffffffffff"));
    }

    @Test
    public void digestFormatting() {
        assertEquals("00ff10", Cards.hex(new byte[]{0, (byte) 0xff, 0x10}));
        assertEquals("7f3a 91c0 b2e4", Cards.shortDigest(DIGEST_A));
        assertEquals("7f3a 91c0 b2e4 0000 0000 0000 0000 0000\n0000 0000 0000 0000 0000 0000 0000 abcd",
                Cards.longDigest(DIGEST_A));
        assertEquals("0202 281b b726", Cards.shortDigest(DIGEST_B));
        assertEquals("abc", Cards.shortDigest("abc"));
    }

    // ---- parser ----------------------------------------------------------------------------------

    @Test
    public void parsesTheReferenceCard() throws Exception {
        Cards.Declared d = Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "Retro",
                "caption", "NES games", "accent", "#E8A33D")).end()));
        assertEquals(1, d.spec);
        assertEquals("Retro", d.label);
        assertEquals("NES games", d.caption);
        assertEquals(0xFFE8A33D, d.accent);
    }

    @Test
    public void liveIsOnlyTheWordTrue() throws Exception {
        assertTrue(Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "News", "live", "true")).end())).live);
        assertFalse(Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "News", "live", "yes")).end())).live);
        assertFalse(Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "News")).end())).live);
    }

    @Test
    public void parsesTheCompiledForms() throws Exception {
        // aapt2 stores spec as an integer and accent as a colour; the parser hands back "1" and "#ffe8a33d".
        Cards.Declared d = Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "Retro",
                "accent", "#ffe8a33d", "colour", "blue")).end()));
        assertEquals(1, d.spec);
        assertNull(d.caption);
        assertEquals(0xFFE8A33D, d.accent);
    }

    @Test
    public void parserIgnoresChildrenAndUnknownAttributes() throws Exception {
        Cards.Declared d = Cards.parse(xml(doc().tag("card", attrs("spec", "1", "label", "Retro", "note", "x"))
                .tag("extra", attrs("value", "x")).close().text("hello").end()));
        assertEquals("Retro", d.label);
        assertEquals(0, d.accent);
    }

    @Test
    public void parserRejectsWrongRootMissingSpecAndReferences() {
        rejects(doc().tag("module", attrs("spec", "1", "label", "Retro")).end(), "root element");
        rejects(doc().tag("card", attrs("label", "Retro")).end(), "spec");
        rejects(doc().tag("card", attrs("spec", "one", "label", "Retro")).end(), "spec");
        rejects(doc().tag("card", attrs("spec", "1")).end(), "label is missing");
        rejects(doc().tag("card", attrs("spec", "1", "label", "@2131820545")).end(), "reference");
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro", "accent", "@2131099648")).end(), "reference");
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro", "accent", "orange")).end(), "accent");
        rejects(doc().end(), "no <card>");
    }

    @Test
    public void parserRejectsTwoRootElements() {
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro")).close()
                .tag("card", attrs("spec", "1", "label", "Other")).end(), "more than one root");
    }

    @Test
    public void unsupportedSpecIsParsedThenRejectedByTheCaller() throws Exception {
        Cards.Declared d = Cards.parse(xml(doc().tag("card", attrs("spec", "2", "label", "Retro")).end()));
        assertEquals(2, d.spec);
    }

    @Test
    public void parserRejectsDeepNesting() {
        Doc d = doc().tag("card", attrs("spec", "1", "label", "Retro"));
        for (int i = 0; i < 4; i++) d.tag("a", attrs());
        rejects(d.end(), "deeper");
    }

    @Test
    public void parserRejectsTooManyEvents() {
        Doc d = doc().tag("card", attrs("spec", "1", "label", "Retro"));
        for (int i = 0; i < 40; i++) d.tag("a", attrs()).close();
        rejects(d.end(), "events");
    }

    @Test
    public void parserRejectsLongValuesBeforeLookingAtThem() {
        char[] big = new char[40 * 1024];
        Arrays.fill(big, 'A');
        rejects(doc().tag("card", attrs("spec", "1", "label", new String(big))).end(), "longer than");
        rejects(doc().tag("card", attrs("spec", "1", "label", "Retro", "caption", new String(big))).end(), "longer than");
        rejects(doc().tag("card", attrs("spec", "1234", "label", "Retro")).end(), "spec");
    }

    @Test
    public void parserRejectsTooManyAttributes() {
        List<String> kv = new ArrayList<>(Arrays.asList("spec", "1", "label", "Retro"));
        for (int i = 0; i < 20; i++) kv.addAll(Arrays.asList("x" + i, "y"));
        rejects(doc().tag("card", attrs(kv.toArray(new String[0]))).end(), "attributes");
    }

    private static void rejects(List<Event> events, String reasonPart) {
        try {
            Cards.parse(xml(events));
            fail("expected rejection containing '" + reasonPart + "'");
        } catch (Cards.CardException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(reasonPart));
        } catch (Exception e) {
            fail("expected CardException, got " + e);
        }
    }

    // ---- app label, clashes, ordering ------------------------------------------------------------

    @Test
    public void cleanLabelStripsFormattingAndBounds() {
        assertEquals("Netflix", Cards.cleanLabel("Netflix\u202E", "io.example"));
        assertEquals("a b", Cards.cleanLabel("a\n\nb", "io.example"));
        assertEquals("a b c", Cards.cleanLabel("  a \t b\u2028c  ", "io.example"));
        assertEquals("Zero width", Cards.cleanLabel("Zero\u200D width\u200B", "io.example"));
        assertEquals("Retro", Cards.cleanLabel("Retro", "io.example"));
        assertEquals("io.example", Cards.cleanLabel("", "io.example"));
        assertEquals("io.example", Cards.cleanLabel("\u202E\n\u0000", "io.example"));
        assertEquals("io.example", Cards.cleanLabel(null, "io.example"));
        char[] big = new char[100];
        Arrays.fill(big, 'x');
        assertEquals(40, Cards.cleanLabel(new String(big), "io.example").length());
        // 39 letters then an emoji (a surrogate pair): the pair does not fit in 40 and is left out whole.
        String emoji = new String(Character.toChars(0x1F3AE));
        assertEquals(39, Cards.cleanLabel(new String(big, 0, 39) + emoji + "y", "io.example").length());
        assertEquals(new String(big, 0, 38) + emoji, Cards.cleanLabel(new String(big, 0, 38) + emoji + "y", "io.example"));
    }

    private static Cards.Card card(String pkg, String label, Cards.State state) {
        return new Cards.Card(pkg, null, label, label, null, 0, DIGEST_A, state);
    }

    private static Map<String, Cards.Record> recordsFor(Object... pkgAndFirstSeen) {
        Map<String, Cards.Record> m = new TreeMap<>();
        for (int i = 0; i < pkgAndFirstSeen.length; i += 2) {
            String pkg = (String) pkgAndFirstSeen[i];
            long seen = ((Number) pkgAndFirstSeen[i + 1]).longValue();
            m.put(pkg, new Cards.Record(pkg, DIGEST_A, "games", "show", seen, seen));
        }
        return m;
    }

    @Test
    public void resolveClashesKeepsTheOwnersDecision() {
        Cards.Card decided = card("io.example.retro", "Games", Cards.State.SHOWN);
        Cards.Card newcomer = card("io.example.late", "games", Cards.State.NEW);
        Cards.Card other = card("io.example.other", "Other", Cards.State.NEW);
        List<Cards.Card> kept = Cards.resolveClashes(Arrays.asList(newcomer, decided, other), recordsFor("io.example.retro", 100));
        assertEquals(Arrays.asList(decided, other), kept);
        // A hidden decision is still a decision.
        Cards.Card hidden = card("io.example.retro", "Games", Cards.State.HIDDEN);
        assertEquals(Arrays.asList(hidden), Cards.resolveClashes(Arrays.asList(hidden, newcomer), recordsFor("io.example.retro", 100)));
    }

    @Test
    public void resolveClashesOldestDecisionWins() {
        Cards.Card older = card("io.example.b", "Games", Cards.State.SHOWN);
        Cards.Card newer = card("io.example.a", "Games", Cards.State.SHOWN);
        List<Cards.Card> kept = Cards.resolveClashes(Arrays.asList(newer, older), recordsFor("io.example.a", 200, "io.example.b", 100));
        assertEquals(Arrays.asList(older), kept);
        // Same first seen: the smaller package name wins.
        kept = Cards.resolveClashes(Arrays.asList(older, newer), recordsFor("io.example.a", 100, "io.example.b", 100));
        assertEquals(Arrays.asList(newer), kept);
    }

    @Test
    public void resolveClashesDropsTwoPendingAndLeavesTheRest() {
        Cards.Card p1 = card("io.example.a", "Games", Cards.State.NEW);
        Cards.Card p2 = card("io.example.b", "GAMES", Cards.State.RESIGNED);
        Cards.Card alone = card("io.example.c", "Alone", Cards.State.NEW);
        assertEquals(Arrays.asList(alone), Cards.resolveClashes(Arrays.asList(p1, alone, p2), new TreeMap<>()));
        assertEquals(Arrays.asList(p1, alone), Cards.resolveClashes(Arrays.asList(p1, alone), new TreeMap<>()));
        assertTrue(Cards.resolveClashes(new ArrayList<>(), new TreeMap<>()).isEmpty());
    }

    @Test
    public void shownFiltersOrdersAndCaps() {
        List<Cards.Card> all = new ArrayList<>();
        all.add(card("io.example.hidden", "Aardvark", Cards.State.HIDDEN));
        all.add(card("io.example.new", "Aardvark", Cards.State.NEW));
        all.add(card("io.example.z", "beta", Cards.State.SHOWN));
        all.add(card("io.example.y", "Beta", Cards.State.SHOWN));
        for (int i = 0; i < 9; i++) all.add(card("io.example.p" + i, "Card " + (char) ('a' + i), Cards.State.SHOWN));
        List<Cards.Card> shown = Cards.shown(all);
        assertEquals(Cards.MAX_SHOWN, shown.size());
        assertEquals("io.example.y", shown.get(0).pkg);     // "Beta" and "beta" tie on label, package decides
        assertEquals("io.example.z", shown.get(1).pkg);
        assertEquals("Card a", shown.get(2).label);
        assertEquals("Card f", shown.get(7).label);
        for (Cards.Card c : shown) assertEquals(Cards.State.SHOWN, c.state);
    }

    @Test
    public void firstPendingIsByPackage() {
        assertNull(Cards.firstPending(new ArrayList<>()));
        List<Cards.Card> all = Arrays.asList(
                card("io.example.c", "C", Cards.State.NEW),
                card("io.example.a", "A", Cards.State.SHOWN),
                card("io.example.b", "B", Cards.State.RESIGNED),
                card("io.example.d", "D", Cards.State.HIDDEN));
        assertEquals("io.example.b", Cards.firstPending(all).pkg);
        assertNull(Cards.firstPending(Arrays.asList(all.get(1), all.get(3))));
    }

    // ---- character checks ------------------------------------------------------------------------

    @Test
    public void packageNames() {
        assertTrue(Cards.isPackageName("io.example.a"));
        assertTrue(Cards.isPackageName("com.netflix.ninja"));
        assertTrue(Cards.isPackageName("a_b.c9"));
        assertFalse(Cards.isPackageName("example"));
        assertFalse(Cards.isPackageName("io."));
        assertFalse(Cards.isPackageName(".io"));
        assertFalse(Cards.isPackageName("io..a"));
        assertFalse(Cards.isPackageName("io.1a"));
        assertFalse(Cards.isPackageName("1io.a"));
        assertFalse(Cards.isPackageName("io.a\""));
        assertFalse(Cards.isPackageName("io.a b"));
        assertFalse(Cards.isPackageName(""));
        assertFalse(Cards.isPackageName(null));
        char[] big = new char[260];
        Arrays.fill(big, 'a');
        assertFalse(Cards.isPackageName("io." + new String(big)));
    }

    @Test
    public void characterClasses() {
        assertTrue(Cards.isText(""));
        assertTrue(Cards.isText("Kid's TV+ 2.0-beta & more"));
        assertFalse(Cards.isText("a\tb"));
        assertFalse(Cards.isText("caf\u00e9"));
        assertTrue(Cards.isDigits("007"));
        assertFalse(Cards.isDigits(""));
        assertFalse(Cards.isDigits("1a"));
        assertTrue(Cards.isHex("0aF", false));
        assertFalse(Cards.isHex("0aF", true));
        assertFalse(Cards.isHex("0g", false));
        assertTrue(Cards.isDigest(DIGEST_A));
        assertFalse(Cards.isDigest(DIGEST_A.toUpperCase()));
        assertFalse(Cards.isDigest(DIGEST_A + "0"));
    }

    @Test
    public void decodeRecordIsExact() {
        String line = "{\"package\":\"io.example.a\",\"signer_sha256\":\"" + DIGEST_A
                + "\",\"label\":\"retro\",\"decision\":\"show\",\"first_seen\":1,\"decided_at\":2}";
        Cards.Record r = Cards.decodeRecord(line);
        assertNotNull(r);
        assertEquals("io.example.a", r.pkg);
        assertEquals("retro", r.label);
        assertEquals(1L, r.firstSeen);
        assertEquals(2L, r.decidedAt);
        assertNull(Cards.decodeRecord(line + " "));
        assertNull(Cards.decodeRecord(line.substring(0, line.length() - 1)));
        assertNull(Cards.decodeRecord(line.replace("\"first_seen\":1", "\"first_seen\":1 ")));
        assertNull(Cards.decodeRecord(line.replace("\"decided_at\":2", "\"decided_at\":")));
        assertNull(Cards.decodeRecord(line.replace("\"decided_at\":2", "\"decided_at\":1234567890123456789")));
        assertNull(Cards.decodeRecord(line.replace("signer_sha256", "signer")));
        assertNull(Cards.decodeRecord(line.replace("\"label\":\"retro\"", "\"label\":\"Retro\"")));   // not folded
        assertNull(Cards.decodeRecord(line.replace("\"label\":\"retro\"", "\"label\":\"\"")));
        assertNull(Cards.decodeRecord(line.replace("\"label\":\"retro\",", "")));
        assertNull(Cards.decodeRecord(""));
        assertNull(Cards.decodeRecord("{"));
    }

    // ---- decision file (cards.json) --------------------------------------------------------------

    @Test
    public void storeRoundTrip() {
        Map<String, Cards.Record> in = new TreeMap<>();
        in.put("io.github.udhaykumarbala.retro", new Cards.Record("io.github.udhaykumarbala.retro", DIGEST_B, "retro", "show", 1700000000000L, 1700000001000L));
        in.put("io.example.spoof", new Cards.Record("io.example.spoof", DIGEST_A, "netflix", "hide", 1700000002000L, 1700000003000L));
        String text = Cards.encode(in);
        assertEquals("{\"version\":2,\"records\":[\n"
                + "{\"package\":\"io.example.spoof\",\"signer_sha256\":\"" + DIGEST_A + "\",\"label\":\"netflix\",\"decision\":\"hide\",\"first_seen\":1700000002000,\"decided_at\":1700000003000},\n"
                + "{\"package\":\"io.github.udhaykumarbala.retro\",\"signer_sha256\":\"" + DIGEST_B + "\",\"label\":\"retro\",\"decision\":\"show\",\"first_seen\":1700000000000,\"decided_at\":1700000001000}\n"
                + "]}\n", text);
        Map<String, Cards.Record> out = Cards.decode(text);
        assertNotNull(out);
        assertEquals(2, out.size());
        assertEquals("show", out.get("io.github.udhaykumarbala.retro").decision);
        assertEquals("retro", out.get("io.github.udhaykumarbala.retro").label);
        assertEquals(DIGEST_B, out.get("io.github.udhaykumarbala.retro").digest);
        assertEquals(1700000000000L, out.get("io.github.udhaykumarbala.retro").firstSeen);
        assertEquals(1700000003000L, out.get("io.example.spoof").decidedAt);
    }

    @Test
    public void emptyStoreRoundTrip() {
        String text = Cards.encode(new TreeMap<>());
        assertEquals("{\"version\":2,\"records\":[\n]}\n", text);
        Map<String, Cards.Record> out = Cards.decode(text);
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void decodeRejectsAnythingElse() {
        String good = Cards.encode(one("io.example.a", DIGEST_A, "show"));
        assertNull(Cards.decode(null));
        assertNull(Cards.decode(""));
        assertNull(Cards.decode("{}"));
        assertNull(Cards.decode("[]"));
        assertNull(Cards.decode(good.replace("show", "yes")));
        assertNull(Cards.decode(good.replace(DIGEST_A, DIGEST_A.substring(1))));
        assertNull(Cards.decode(good.replace(DIGEST_A, DIGEST_A.toUpperCase())));
        assertNull(Cards.decode(good.replace("io.example.a", "io.example.a\",\"x\":\"")));
        assertNull(Cards.decode(good.replace("io.example.a", "example")));          // no dot
        assertNull(Cards.decode(good.replace("io.example.a", "io.1example.a")));   // bad segment
        assertNull(Cards.decode(good.replace("\"version\":2", "\"version\":1")));   // the old format, without labels
        assertNull(Cards.decode(good.replace("]}", "")));
        assertNull(Cards.decode(good.replace("1700", "-170")));
        assertNull(Cards.decode(good + "garbage"));
        // Missing comma between records, and the same package twice.
        Map<String, Cards.Record> two = one("io.example.a", DIGEST_A, "show");
        two.put("io.example.b", new Cards.Record("io.example.b", DIGEST_B, "b", "hide", 1, 2));
        String pair = Cards.encode(two);
        assertNotNull(Cards.decode(pair));
        assertNull(Cards.decode(pair.replace("},\n", "}\n")));
        assertNull(Cards.decode(pair.replace("io.example.b", "io.example.a")));
    }

    @Test
    public void decodeCapsSizeAndCount() {
        Map<String, Cards.Record> many = new TreeMap<>();
        for (int i = 0; i < Cards.MAX_RECORDS + 1; i++) {
            String pkg = "io.example.p" + i;
            many.put(pkg, new Cards.Record(pkg, DIGEST_A, "card" + i, "hide", i, i));
        }
        String text = Cards.encode(many);                 // encode drops the oldest beyond the cap
        Map<String, Cards.Record> out = Cards.decode(text);
        assertNotNull(out);
        assertEquals(Cards.MAX_RECORDS, out.size());
        assertFalse(out.containsKey("io.example.p0"));    // decidedAt 0 was the oldest
        // A file with one record too many cannot come from encode; it is invalid.
        String[] lines = text.split("\n");
        StringBuilder forged = new StringBuilder(lines[0]).append('\n');
        for (int i = 1; i < lines.length - 1; i++) forged.append(lines[i]).append(i == lines.length - 2 ? ",\n" : "\n");
        forged.append("{\"package\":\"io.example.extra\",\"signer_sha256\":\"").append(DIGEST_A)
                .append("\",\"decision\":\"hide\",\"first_seen\":1,\"decided_at\":1}\n]}\n");
        assertNull(Cards.decode(forged.toString()));
        char[] huge = new char[70 * 1024];
        Arrays.fill(huge, ' ');
        assertNull(Cards.decode(new String(huge)));
    }

    @Test
    public void encodeRefusesAnInvalidRecord() {
        try {
            Cards.encode(one("io.example.a", "nothex", "show"));
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            Cards.encode(one("io.example.a\"", DIGEST_A, "show"));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void quarantineMovesTheBadFileAside() throws Exception {
        File dir = File.createTempFile("cards", "dir");
        assertTrue(dir.delete() && dir.mkdir());
        File store = new File(dir, Cards.STORE_FILE);
        try (FileOutputStream out = new FileOutputStream(store)) {
            out.write("not json".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(Cards.quarantine(store));
        File bad = new File(dir, Cards.STORE_FILE + ".bad");
        assertFalse(store.exists());
        assertTrue(bad.exists());
        // A second bad file replaces the first; the evidence is the latest one.
        try (FileOutputStream out = new FileOutputStream(store)) {
            out.write("still not json".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(Cards.quarantine(store));
        assertEquals(14, bad.length());
        assertTrue(bad.delete() && dir.delete());
    }

    @Test
    public void captionCannotClaimEndorsement() {
        assertNull(Cards.checkCaption("NES games"));
        assertNotNull(Cards.checkCaption("Official Netflix app"));
        assertNotNull(Cards.checkCaption("VERIFIED by Google"));
        assertNotNull(Cards.checkCaption("ver-ified"));   // folding removes the hyphen
    }

    @Test
    public void relabelledCardIsPendingAgain() {
        // A card the owner decided on, whose label then changed, is treated like a new card: it does
        // not win a name clash, and the clash rule drops both undecided cards.
        Cards.Card renamed = card("io.example.retro", "Netflix", Cards.State.RELABELLED);
        Cards.Card other = card("io.example.other", "netflix", Cards.State.NEW);
        assertTrue(renamed.pending());
        assertTrue(Cards.resolveClashes(Arrays.asList(renamed, other), recordsFor("io.example.retro", 100)).isEmpty());
        assertEquals(renamed, Cards.firstPending(Arrays.asList(card("io.example.a", "A", Cards.State.SHOWN), renamed)));
    }

    @Test
    public void isFoldedIsStrict() {
        assertTrue(Cards.isFolded("retro"));
        assertTrue(Cards.isFolded("nes2"));
        assertFalse(Cards.isFolded(""));
        assertFalse(Cards.isFolded("Retro"));
        assertFalse(Cards.isFolded("re tro"));
        assertFalse(Cards.isFolded("abcdefghijklmnopqrstuvwxy"));   // 25
    }

    private static Map<String, Cards.Record> one(String pkg, String digest, String decision) {
        Map<String, Cards.Record> m = new TreeMap<>();
        m.put(pkg, new Cards.Record(pkg, digest, "a", decision, 1700000000000L, 1700000000000L));
        return m;
    }

    // ---- a scripted XmlPullParser ----------------------------------------------------------------

    /** One parser event: type, depth, name and attributes (START_TAG only). */
    private static final class Event {
        final int type;
        final int depth;
        final String name;
        final Map<String, String> attrs;

        Event(int type, int depth, String name, Map<String, String> attrs) {
            this.type = type;
            this.depth = depth;
            this.name = name;
            this.attrs = attrs;
        }
    }

    private static final class Doc {
        final List<Event> events = new ArrayList<>();
        int depth = 0;

        Doc tag(String name, Map<String, String> attrs) {
            events.add(new Event(XmlPullParser.START_TAG, ++depth, name, attrs));
            return this;
        }

        Doc close() {
            events.add(new Event(XmlPullParser.END_TAG, depth, null, null));
            depth -= 1;
            return this;
        }

        Doc text(String s) {
            events.add(new Event(XmlPullParser.TEXT, depth, s, null));
            return this;
        }

        List<Event> end() {
            while (depth > 0) close();
            events.add(new Event(XmlPullParser.END_DOCUMENT, 0, null, null));
            return events;
        }
    }

    private static Doc doc() {
        Doc d = new Doc();
        d.events.add(new Event(XmlPullParser.START_DOCUMENT, 0, null, null));
        return d;
    }

    private static Map<String, String> attrs(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Replays the events through the XmlPullParser interface, the way the compiled resource parser would. */
    private static XmlPullParser xml(final List<Event> events) {
        final int[] at = {0};
        InvocationHandler h = (proxy, method, args) -> {
            Event e = events.get(at[0]);
            List<String> keys = e.attrs == null ? new ArrayList<>() : new ArrayList<>(e.attrs.keySet());
            switch (method.getName()) {
                case "getEventType":
                    return e.type;
                case "next":
                    if (e.type == XmlPullParser.END_DOCUMENT) return XmlPullParser.END_DOCUMENT;
                    at[0]++;
                    return events.get(at[0]).type;
                case "getDepth":
                    return e.depth;
                case "getName":
                    return e.name;
                case "getAttributeCount":
                    return keys.size();
                case "getAttributeNamespace":
                    return "";
                case "getAttributeName":
                    return keys.get((Integer) args[0]);
                case "getAttributeValue":
                    if (args.length == 1) return e.attrs.get(keys.get((Integer) args[0]));
                    return e.attrs.get(args[1]);
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        };
        return (XmlPullParser) Proxy.newProxyInstance(XmlPullParser.class.getClassLoader(),
                new Class<?>[]{XmlPullParser.class}, h);
    }
}

package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteKeysTest {
    // Key names resolved by a stand-in: android.view.KeyEvent isn't available in JVM tests.
    private static int keyCode(String name) {
        if (name.equals("KEYCODE_BUTTON_3")) return 190;
        if (name.equals("KEYCODE_BUTTON_8")) return 195;
        return 0;
    }

    private static Map<Integer, String> parse(String... entries) {
        return RemoteKeys.parseAppKeys(Arrays.asList(entries), RemoteKeysTest::keyCode);
    }

    private static Map<Integer, String> parseHold(String... entries) {
        return RemoteKeys.parseHoldAppKeys(Arrays.asList(entries), RemoteKeysTest::keyCode);
    }

    @Test public void inFrontMatchesTheTargetsPackageOnly() {
        assertTrue(RemoteKeys.inFront("com.vt.tv.tvcenter", "com.vt.tv.tvcenter"));
        assertTrue(RemoteKeys.inFront("io.github.maxhayim.brightness/.QuickActivity", "io.github.maxhayim.brightness"));
        assertFalse(RemoteKeys.inFront("com.vt.tv.tvcenter", "com.gathin.tvhome"));
        assertFalse(RemoteKeys.inFront("com.vt.tv.tvcenter", ""));
        assertFalse(RemoteKeys.inFront(null, "com.vt.tv.tvcenter"));
        assertFalse(RemoteKeys.inFront("input:com.vt.source.external/.HdmiInputService/HW2", "com.vt.source.external"));
    }

    @Test public void frontEntriesAreReadApartFromTheRest() {
        List<String> entries = Arrays.asList("172:com.vt.tv.tvcenter", "front:172:guide:com.vt.tv.tvcenter",
                "front:scan:490:com.wbd.hbomax", "hold:172:a.b");
        assertEquals("com.vt.tv.tvcenter", RemoteKeys.parseAppKeys(entries, RemoteKeysTest::keyCode).get(172));
        Map<Integer, String> front = RemoteKeys.parseFrontAppKeys(entries, RemoteKeysTest::keyCode);
        assertEquals(1, front.size());
        assertEquals("guide:com.vt.tv.tvcenter", front.get(172));
        assertEquals("com.wbd.hbomax", RemoteKeys.parseFrontAppScancodes(entries).get(490));
        assertTrue(RemoteKeys.parseAppScancodes(entries).isEmpty());
    }

    @Test public void guideTargetNamesItsPackage() {
        assertEquals("com.vt.tv.tvcenter", RemoteKeys.guidePackage("guide:com.vt.tv.tvcenter"));
        assertNull(RemoteKeys.guidePackage("guide:"));
        assertNull(RemoteKeys.guidePackage("com.vt.tv.tvcenter"));
        assertNull(RemoteKeys.guidePackage(null));
    }

    @Test public void keyNamesWorkLikeNumbers() {
        Map<Integer, String> keys = parse("KEYCODE_BUTTON_3:com.google.android.youtube.tv", "KEYCODE_NOT_A_KEY:a.b");
        assertEquals(1, keys.size());
        assertEquals("com.google.android.youtube.tv", keys.get(190));
    }

    @Test public void keycodeAndPackage() {
        Map<Integer, String> keys = parse(
                "190:com.google.android.youtube.tv", " 193 : com.netflix.ninja ");
        assertEquals(2, keys.size());
        assertEquals("com.google.android.youtube.tv", keys.get(190));
        assertEquals("com.netflix.ninja", keys.get(193));
    }

    @Test public void badEntriesAreSkipped() {
        Map<Integer, String> keys = parse(
                "com.google.android.youtube.tv", "youtube:com.google.android.youtube.tv", "190:", "");
        assertTrue(keys.isEmpty());
    }

    @Test public void theLastEntryForAKeycodeWins() {
        Map<Integer, String> keys = parse("190:a.b", "190:c.d");
        assertEquals("c.d", keys.get(190));
    }

    @Test public void scancodeEntries() {
        java.util.List<String> entries = Arrays.asList(
                "scan:490:com.wbd.hbomax", " scan : 491 : a.b ", "scan:x:a.b", "scan:12:", "190:c.d", "scan:491:e.f");
        Map<Integer, String> scans = RemoteKeys.parseAppScancodes(entries);
        assertEquals(2, scans.size());
        assertEquals("com.wbd.hbomax", scans.get(490));
        assertEquals("e.f", scans.get(491));
        // and the keycode parser leaves scancode entries alone
        assertEquals(1, parse(entries.toArray(new String[0])).size());
    }
    @Test public void inputTargets() {
        String hdmi = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104";
        Map<Integer, String> keys = parse("4062:input:" + hdmi);
        assertEquals(hdmi, RemoteKeys.inputId(keys.get(4062)));
        assertEquals(hdmi, RemoteKeys.inputId(RemoteKeys.parseAppScancodes(
                java.util.Arrays.asList("scan:586:input:" + hdmi)).get(586)));
        assertEquals(null, RemoteKeys.inputId("com.netflix.ninja"));
        assertEquals(null, RemoteKeys.inputId("input: "));
    }

    @Test public void holdEntriesGetTheirOwnMap() {
        Map<Integer, String> holds = parseHold(
                "hold:KEYCODE_BUTTON_8:org.xbmc.kodi", " hold:190:a.b ", "hold:KEYCODE_NOT_A_KEY:x.y",
                "hold:195:", "190:c.d", "hold:scan:490:d.e", "hold:");
        assertEquals(2, holds.size());
        assertEquals("org.xbmc.kodi", holds.get(195));
        assertEquals("a.b", holds.get(190));
        // the normal parser leaves hold entries alone
        assertTrue(parse("hold:190:a.b").isEmpty());
    }

    @Test public void holdScancodeEntries() {
        Map<Integer, String> scans = RemoteKeys.parseHoldAppScancodes(Arrays.asList(
                "hold:scan:490:com.wbd.hbomax", "hold:scan:491:", "hold:190:a.b", "scan:492:c.d"));
        assertEquals(1, scans.size());
        assertEquals("com.wbd.hbomax", scans.get(490));
    }

    @Test public void holdTimePicksTheHoldApp() {
        assertEquals("hold.app", RemoteKeys.target("app", "hold.app", RemoteKeys.HOLD_MS));
        assertEquals("hold.app", RemoteKeys.target("app", "hold.app", 5000));
        assertEquals("app", RemoteKeys.target("app", "hold.app", RemoteKeys.HOLD_MS - 1));
        assertEquals("app", RemoteKeys.target("app", null, 5000));
        assertNull(RemoteKeys.target(null, "hold.app", 0));
    }

    @Test public void inputsTargetKeptForAScancode() {
        Map<Integer, String> scans = RemoteKeys.parseAppScancodes(Arrays.asList("scan:365:inputs"));
        assertEquals(RemoteKeys.INPUTS, scans.get(365));
        assertNull(RemoteKeys.inputId(RemoteKeys.INPUTS));
    }

    @Test public void actionWordsAreTargets() {
        Map<Integer, String> keys = parse("190:mute", "KEYCODE_BUTTON_8:screensaver");
        assertEquals("mute", keys.get(190));
        assertEquals("screensaver", keys.get(195));
        assertEquals("sleep", parseHold("hold:190:sleep").get(190));
        for (String word : new String[] {"back", "home", "recents", "mute", "sleep", "screensaver", "settings", "wifi", "ignore"}) {
            assertTrue(word, RemoteKeys.isAction(word));
        }
        assertFalse(RemoteKeys.isAction("com.netflix.ninja"));
        assertFalse(RemoteKeys.isAction("input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1"));
        assertFalse(RemoteKeys.isAction("Mute"));
    }

    @Test public void globalActions() {
        assertEquals(AccessibilityService.GLOBAL_ACTION_BACK, RemoteKeys.globalAction("back", 26));
        assertEquals(AccessibilityService.GLOBAL_ACTION_HOME, RemoteKeys.globalAction("home", 26));
        assertEquals(AccessibilityService.GLOBAL_ACTION_RECENTS, RemoteKeys.globalAction("recents", 26));
        assertEquals(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, RemoteKeys.globalAction("sleep", 28));
        // No lock-screen action before Android 9: the key is consumed and nothing happens.
        assertEquals(0, RemoteKeys.globalAction("sleep", 27));
        assertEquals(0, RemoteKeys.globalAction("mute", 34));
        assertEquals(0, RemoteKeys.globalAction("com.netflix.ninja", 34));
    }

    @Test public void anInputTargetIsNotAnActivity() {
        // Its id has slashes, but it must reach the passthrough branch, not ComponentName.unflattenFromString().
        assertFalse(RemoteKeys.isActivity("input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640"));
        assertTrue(RemoteKeys.isActivity("io.github.maxhayim.brightness/.QuickActivity"));
        assertFalse(RemoteKeys.isActivity("com.netflix.ninja"));
        assertFalse(RemoteKeys.isActivity("/.QuickActivity"));
        assertFalse(RemoteKeys.isActivity(null));
    }

    /** Presses with a timer the test runs by hand, and the targets they open. */
    private static final class Remote {
        final List<Runnable> holds = new ArrayList<>();
        final List<String> opened = new ArrayList<>();
        final RemoteKeys.Presses presses = new RemoteKeys.Presses(new RemoteKeys.Presses.Timer() {
            @Override public void post(Runnable hold, long delayMs) { holds.add(hold); }
            @Override public void cancel(Runnable hold) { holds.remove(hold); }
        });

        void down(String button, long at, String holdApp) {
            presses.down(button, at, 0, holdApp, this, opened::add);
        }

        /** A DOWN as one of the two helpers sees it; what it opens is noted with its name. */
        void down(String helper, String button, long at, String holdApp) {
            presses.down(button, at, 0, holdApp, helper, target -> opened.add(helper + ": " + target));
        }

        void up(String button, long at, long downTime, String app, String holdApp) {
            presses.up(button, at, downTime, false, app, holdApp, opened::add);
        }

        /** HOLD_MS has passed: every hold still pending fires. */
        void holdTimeout() {
            for (Runnable hold : new ArrayList<>(holds)) hold.run();
            holds.clear();
        }
    }

    @Test public void chordedKeysKeepTheirOwnHold() {
        Remote remote = new Remote();
        remote.down("1:190:0", 1000, "a.hold");
        remote.down("1:191:0", 1100, "b.hold");
        // A is released reporting B's down time; it is still A's tap, and only A's hold is cancelled.
        remote.up("1:190:0", 1200, 1100, "a.tap", "a.hold");
        remote.holdTimeout();
        remote.up("1:191:0", 1700, 1100, "b.tap", "b.hold");
        assertEquals(Arrays.asList("a.tap", "b.hold"), remote.opened);
    }

    @Test public void holdsOnTwoDevicesStayApart() {
        Remote remote = new Remote();
        remote.down("1:190:0", 1000, "hold");
        remote.holdTimeout();
        remote.down("2:190:0", 1000, "hold");
        remote.holdTimeout();
        remote.up("1:190:0", 1800, 1000, "tap", "hold");
        remote.up("2:190:0", 1900, 1000, "tap", "hold");
        assertEquals(Arrays.asList("hold", "hold"), remote.opened);
    }

    @Test public void bothServicesAnswerEachPressOnce() {
        Remote remote = new Remote();
        // A tap, each event delivered to both services, in either order.
        remote.down("1:190:0", 1000, "hold");
        remote.down("1:190:0", 1000, "hold");
        remote.up("1:190:0", 1100, 1000, "tap", "hold");
        remote.up("1:190:0", 1100, 1000, "tap", "hold");
        // A hold, with the second service's DOWN arriving after the hold fired.
        remote.down("1:190:0", 2000, "hold");
        remote.holdTimeout();
        remote.down("1:190:0", 2000, "hold");
        remote.up("1:190:0", 2800, 2000, "tap", "hold");
        remote.up("1:190:0", 2800, 2000, "tap", "hold");
        assertEquals(Arrays.asList("tap", "hold"), remote.opened);
        assertTrue(remote.holds.isEmpty());
    }

    @Test public void aCanceledUpOpensNothingAndDropsTheHold() {
        Remote remote = new Remote();
        remote.down("1:190:0", 1000, "hold");
        remote.presses.up("1:190:0", 1200, 1000, true, "tap", "hold", remote.opened::add);
        remote.holdTimeout();
        assertTrue(remote.opened.isEmpty());
    }

    @Test public void aHelperUnboundBeforeTheHoldCancelsIt() {
        Remote remote = new Remote();
        remote.down("home", "1:190:0", 1000, "hold");
        remote.presses.release("home");
        remote.presses.release("home");   // onUnbind, then onDestroy
        remote.holdTimeout();
        assertTrue(remote.opened.isEmpty());
        assertTrue(remote.holds.isEmpty());
    }

    @Test public void losingOneHelperLeavesTheHoldWithTheOther() {
        Remote remote = new Remote();
        remote.down("home", "1:190:0", 1000, "hold");
        remote.down("input", "1:190:0", 1000, "hold");
        remote.presses.release("home");
        remote.holdTimeout();
        remote.up("1:190:0", 1800, 1000, "tap", "hold");
        assertEquals(Arrays.asList("input: hold"), remote.opened);
    }

    @Test public void losingOneHelperKeepsTheOthersTap() {
        Remote remote = new Remote();
        remote.down("home", "1:190:0", 1000, "hold");
        remote.down("input", "1:190:0", 1000, "hold");
        remote.presses.release("input");
        remote.up("1:190:0", 1200, 1000, "tap", "hold");
        remote.holdTimeout();
        assertEquals(Arrays.asList("tap"), remote.opened);
    }

    @Test public void aHelperThatStopsFilteringReleasesItsHold() {
        // gamepad_passthrough drops the key filter when a controller connects: the UP never reaches that helper.
        Remote remote = new Remote();
        remote.down("home", "1:190:0", 1000, "hold");
        remote.down("input", "1:190:0", 1000, "hold");
        remote.presses.release("home");
        remote.presses.release("input");
        remote.holdTimeout();
        assertTrue(remote.opened.isEmpty());
    }

    @Test public void anUpAfterTheHoldWasReleasedOpensNothing() {
        Remote remote = new Remote();
        remote.down("home", "1:190:0", 1000, "hold");
        remote.presses.release("home");
        remote.up("1:190:0", 1200, 1000, "tap", "hold");
        remote.holdTimeout();
        // The next press is answered as usual.
        remote.down("home", "1:190:0", 2000, "hold");
        remote.up("1:190:0", 2100, 2000, "tap", "hold");
        assertEquals(Arrays.asList("tap"), remote.opened);
    }

    @Test public void releasingAHelperLeavesAnotherButtonsHoldAlone() {
        Remote remote = new Remote();
        remote.down("home", "1:190:0", 1000, "a.hold");
        remote.down("input", "1:191:0", 1100, "b.hold");
        remote.presses.release("home");
        remote.holdTimeout();
        assertEquals(Arrays.asList("input: b.hold"), remote.opened);
    }

    @Test public void anUpWithoutItsDownUsesTheEventsDownTime() {
        Remote remote = new Remote();
        remote.up("1:190:0", 1100, 1000, "tap", "hold");
        remote.up("1:191:0", 1700, 1000, "tap", "hold");
        assertEquals(Arrays.asList("tap", "hold"), remote.opened);
    }

    @Test public void menuReleasedTakesXiaomisInjectedShortPress() {
        // ACTION_DOWN 0, ACTION_UP 1; FLAG_CANCELED 0x20, FLAG_CANCELED_LONG_PRESS 0x100, FLAG_FROM_SYSTEM 0x8.
        assertTrue(RemoteKeys.menuReleased(1, 0));
        assertTrue(RemoteKeys.menuReleased(1, 0x8));
        assertTrue(RemoteKeys.menuReleased(1, RemoteKeys.XIAOMI_SHORT_MENU_FLAGS));
        assertFalse(RemoteKeys.menuReleased(0, 0));
        assertFalse(RemoteKeys.menuReleased(0, RemoteKeys.XIAOMI_SHORT_MENU_FLAGS));
        // A cancelled press elsewhere, long press or not, still opens nothing.
        assertFalse(RemoteKeys.menuReleased(1, 0x20));
        assertFalse(RemoteKeys.menuReleased(1, 0x20 | 0x100 | 0x8));
        assertFalse(RemoteKeys.menuReleased(1, RemoteKeys.XIAOMI_SHORT_MENU_FLAGS & ~0x4000));
    }
}

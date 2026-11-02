package io.github.raqs31.nowplaying;

import static android.media.session.PlaybackState.STATE_BUFFERING;
import static android.media.session.PlaybackState.STATE_NONE;
import static android.media.session.PlaybackState.STATE_PAUSED;
import static android.media.session.PlaybackState.STATE_PLAYING;
import static android.media.session.PlaybackState.STATE_STOPPED;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
import static android.view.KeyEvent.KEYCODE_MEDIA_STOP;
import static android.view.KeyEvent.KEYCODE_VOLUME_UP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RulesTest {
    private static final String HOME = "com.gathin.tvhome";

    @Test
    public void pickPrefersAPlayingSessionOverAPausedOneAheadOfIt() {
        assertEquals(1, SessionPicker.pick(new int[] {STATE_PAUSED, STATE_PLAYING}, new boolean[] {true, true}));
    }

    @Test
    public void pickNeverChoosesAnUntitledSessionOverATitledOne() {
        assertEquals(1, SessionPicker.pick(new int[] {STATE_PLAYING, STATE_PAUSED}, new boolean[] {false, true}));
    }

    @Test
    public void pickFallsBackToAStoppedTitledSessionAndOtherwiseToNone() {
        assertEquals(0, SessionPicker.pick(new int[] {STATE_STOPPED}, new boolean[] {true}));
        assertEquals(-1, SessionPicker.pick(new int[] {STATE_PLAYING}, new boolean[] {false}));
        assertEquals(-1, SessionPicker.pick(new int[0], new boolean[0]));
    }

    @Test
    public void progressRunsOnOnlyWhilePlayingAndStaysInsideTheTrack() {
        assertEquals(15_000, Progress.position(STATE_PLAYING, 10_000, 1_000, 1f, 6_000, 60_000));
        assertEquals(10_000, Progress.position(STATE_PAUSED, 10_000, 1_000, 1f, 6_000, 60_000));
        assertEquals(60_000, Progress.position(STATE_PLAYING, 59_000, 0, 1f, 9_000, 60_000));
        assertEquals(-1, Progress.position(STATE_PLAYING, -1, 0, 1f, 9_000, 60_000));
    }

    @Test
    public void progressFormatsMinutesAndHours() {
        assertEquals("0:05", Progress.format(5_400));
        assertEquals("3:07", Progress.format(187_000));
        assertEquals("1:02:03", Progress.format(3_723_000));
    }

    @Test
    public void onlyActivitiesDecideWhetherHomeIsInFront() {
        assertEquals(CardRules.HOME, CardRules.classify(HOME, true, HOME));
        assertEquals(CardRules.AWAY, CardRules.classify("com.spotify.tv.android", true, HOME));
        assertEquals(CardRules.IGNORE, CardRules.classify("com.android.systemui", false, HOME));
        assertEquals(CardRules.IGNORE, CardRules.classify(null, true, HOME));
    }

    @Test
    public void cardShowsOnHomeWhilePlayingOrPausedAndGoesOnStop() {
        assertTrue(CardRules.visible(true, true, STATE_PLAYING, true, false));
        assertTrue(CardRules.visible(true, true, STATE_BUFFERING, true, false));
        assertTrue(CardRules.visible(true, true, STATE_PAUSED, true, false));
        assertFalse(CardRules.visible(true, true, STATE_STOPPED, true, false));
        assertFalse(CardRules.visible(true, true, STATE_NONE, true, false));
        assertFalse(CardRules.visible(true, true, STATE_PLAYING, false, false));
        assertFalse(CardRules.visible(true, false, STATE_PLAYING, true, false));
        assertFalse(CardRules.visible(false, true, STATE_PLAYING, true, false));
    }

    @Test
    public void stopHidesTheCardEvenWhenTheAppOnlyPausesUntilMusicPlaysAgain() {
        assertFalse(CardRules.visible(true, true, STATE_PAUSED, true, true));
        assertTrue(CardRules.visible(true, true, STATE_PLAYING, true, true));
    }

    @Test
    public void navigationAndVolumeKeysAlwaysPass() {
        KeyRules k = new KeyRules();
        for (int code : new int[] {KEYCODE_DPAD_LEFT, KEYCODE_DPAD_CENTER, KEYCODE_BACK, KEYCODE_VOLUME_UP}) {
            assertEquals(KeyRules.PASS, k.decide(code, ACTION_DOWN, 0, true));
            assertEquals(KeyRules.PASS, k.decide(code, ACTION_UP, 0, true));
        }
    }

    @Test
    public void mediaKeysActOnceWhileTheCardShowsAndPassOtherwise() {
        KeyRules k = new KeyRules();
        assertEquals(KeyRules.PASS, k.decide(KEYCODE_MEDIA_NEXT, ACTION_DOWN, 0, false));
        assertEquals(KeyRules.PASS, k.decide(KEYCODE_MEDIA_NEXT, ACTION_UP, 0, false));
        assertEquals(KeyRules.TOGGLE, k.decide(KEYCODE_MEDIA_PLAY_PAUSE, ACTION_DOWN, 0, true));
        assertEquals(KeyRules.SWALLOW, k.decide(KEYCODE_MEDIA_PLAY_PAUSE, ACTION_DOWN, 1, true));
        assertEquals(KeyRules.SWALLOW, k.decide(KEYCODE_MEDIA_PLAY_PAUSE, ACTION_UP, 0, true));
        assertEquals(KeyRules.NEXT, k.decide(KEYCODE_MEDIA_FAST_FORWARD, ACTION_DOWN, 0, true));
        k.decide(KEYCODE_MEDIA_FAST_FORWARD, ACTION_UP, 0, true);
        assertEquals(KeyRules.PREV, k.decide(KEYCODE_MEDIA_PREVIOUS, ACTION_DOWN, 0, true));
        k.decide(KEYCODE_MEDIA_PREVIOUS, ACTION_UP, 0, true);
        assertEquals(KeyRules.STOP, k.decide(KEYCODE_MEDIA_STOP, ACTION_DOWN, 0, true));
    }

    @Test
    public void aCaughtKeysUpIsCaughtEvenAfterTheCardHasGone() {
        KeyRules k = new KeyRules();
        assertEquals(KeyRules.STOP, k.decide(KEYCODE_MEDIA_STOP, ACTION_DOWN, 0, true));
        assertTrue(k.holding());
        assertEquals(KeyRules.SWALLOW, k.decide(KEYCODE_MEDIA_STOP, ACTION_UP, 0, false));
        assertFalse(k.holding());
    }
}

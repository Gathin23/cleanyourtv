package io.github.justinnewman.bedtimelock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The ntfy request's buttons and reading the answer back for the one request that is waiting. */
public class PhoneUnlockTest {
    private static final String TOKEN = "0a1b2c3d4e5f60718293a4b5c6d7e8f9";

    @Test
    public void channelsAreLongRandomAndPrivate() {
        String a = PhoneUnlock.newChannel();
        assertTrue(a.matches("bedtime-[a-z0-9]{20}"));
        assertNotEquals(a, PhoneUnlock.newChannel());
        assertEquals(a + "-reply", PhoneUnlock.replyChannel(a));
    }

    @Test
    public void bothButtonsPostTheTokenToTheReplyChannel() {
        String actions = PhoneUnlock.actions("https://ntfy.sh", "bedtime-x-reply", TOKEN);
        assertEquals("http, Unlock, https://ntfy.sh/bedtime-x-reply, body=unlock " + TOKEN + ", clear=true; "
                + "http, Not now, https://ntfy.sh/bedtime-x-reply, body=no " + TOKEN + ", clear=true", actions);
    }

    @Test
    public void onlyAnAnswerForThisRequestCounts() {
        String unlock = "{\"id\":\"a\",\"event\":\"message\",\"topic\":\"r\",\"message\":\"unlock " + TOKEN + "\"}";
        String no = "{\"id\":\"b\",\"event\":\"message\",\"topic\":\"r\",\"message\":\"no " + TOKEN + "\"}";
        String old = "{\"id\":\"c\",\"event\":\"message\",\"topic\":\"r\",\"message\":\"unlock ffff\"}";
        assertEquals("unlock", PhoneUnlock.answerIn(old + "\n" + unlock + "\n", TOKEN));
        assertEquals("no", PhoneUnlock.answerIn(no, TOKEN));
        assertNull(PhoneUnlock.answerIn(old, TOKEN));
        assertNull(PhoneUnlock.answerIn("", TOKEN));
        assertNull(PhoneUnlock.answerIn(unlock, null));
    }

    @Test
    public void noCooldownBeforeTheFirstRequest() {
        assertEquals(0, PhoneUnlock.cooldownLeft(System.currentTimeMillis()));
    }

    @Test
    public void theCountdownReadsMinutesAndSeconds() {
        assertEquals("5:00", PhoneUnlock.clock(PhoneUnlock.WAIT_MS));
        assertEquals("4:59", PhoneUnlock.clock(299_000));
        assertEquals("0:09", PhoneUnlock.clock(8_500));
        assertEquals("0:01", PhoneUnlock.clock(1));
    }
}

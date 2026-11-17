package io.github.pbanddev.tatami;

import static io.github.pbanddev.tatami.LookSchedule.Mode.BOTH;
import static io.github.pbanddev.tatami.LookSchedule.Mode.NAGAYA;
import static io.github.pbanddev.tatami.LookSchedule.Mode.YOJOHAN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class EffectsTest {

    @Test
    public void lookIsReadFromItsStoredName() {
        assertSame(YOJOHAN, Effects.look("yojohan"));
        assertSame(NAGAYA, Effects.look("nagaya"));
        assertSame(BOTH, Effects.look("both"));
        for (LookSchedule.Mode mode : LookSchedule.Mode.values()) assertSame(mode, LookSchedule.Mode.of(mode.key));
    }

    @Test
    public void anUnknownLookGivesBoth() {
        for (String stored : new String[] {null, "", "Nagaya", "default", "yojōhan"}) {
            assertSame(stored, BOTH, Effects.look(stored));
            assertNull(stored, LookSchedule.Mode.of(stored));
        }
    }

    @Test
    public void swapMinutesFrom1To60AreKept() {
        for (int minutes : new int[] {1, 5, 7, 10, 30, 60}) assertEquals(minutes, Effects.swapMinutes(minutes));
    }

    @Test
    public void otherSwapMinutesGiveTen() {
        for (int minutes : new int[] {0, -10, 61, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertEquals(10, Effects.swapMinutes(minutes));
        }
    }

    @Test
    public void okStepsThroughTheRowsChoices() {
        assertSame(NAGAYA, BOTH.next());
        assertSame(YOJOHAN, NAGAYA.next());
        assertSame(BOTH, YOJOHAN.next());
        int[][] steps = {{5, 10}, {10, 30}, {30, 60}, {60, 5}, {1, 5}, {7, 10}, {45, 60}};
        for (int[] step : steps) assertEquals("from " + step[0], step[1], Effects.nextSwapMinutes(step[0]));
    }
}

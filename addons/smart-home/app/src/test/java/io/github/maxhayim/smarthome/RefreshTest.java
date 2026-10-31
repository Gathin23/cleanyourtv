package io.github.maxhayim.smarthome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** ReadGate and Targets: the screen's refresh queue and held thermostat steps, without Android. */
public class RefreshTest {

    @Test
    public void triggersWhileAReadIsOutBecomeOneMoreRead() {
        ReadGate g = new ReadGate();
        int first = g.start();
        assertTrue(first >= 0);
        for (int i = 0; i < 5; i++) assertEquals(-1, g.start());
        assertTrue(g.finish(first));
        assertTrue(g.takeAgain());
        assertFalse(g.takeAgain());
        assertTrue(g.start() >= 0);
    }

    @Test
    public void aSingleReadAsksForNoOther() {
        ReadGate g = new ReadGate();
        int gen = g.start();
        assertTrue(g.finish(gen));
        assertFalse(g.takeAgain());
    }

    @Test
    public void leavingDropsQueuedAndRunningReads() {
        ReadGate g = new ReadGate();
        int old = g.start();
        g.start();
        g.reset();
        assertFalse(g.current(old));
        assertFalse(g.finish(old));
        assertFalse(g.takeAgain());
        int now = g.start();
        assertTrue(now >= 0);
        assertTrue(g.current(now));
    }

    @Test
    public void anOldReadDoesntFreeTheNewOne() {
        ReadGate g = new ReadGate();
        int old = g.start();
        g.reset();
        int now = g.start();
        assertFalse(g.finish(old));
        assertEquals(-1, g.start());   // the new read is still the one out
        assertTrue(g.finish(now));
    }

    private static Ha.Entity thermostat(double target) {
        return new Ha.Entity("climate.nursery", Ha.Kind.CLIMATE, "Nursery", "heat", -1, 21.4, target, "°C");
    }

    @Test
    public void heldStepsCountFromTheLastTargetSent() {
        Targets t = new Targets();
        Ha.Entity stale = thermostat(20);
        double last = 0;
        for (int i = 0; i < 4; i++) {
            last = t.next(stale, 1);
            t.sent();
        }
        assertEquals(22.0, last, 0.0001);
        assertEquals("{\"entity_id\":\"climate.nursery\",\"temperature\":22}", Ha.temperature(stale, last).body);
        assertEquals(21.5, t.next(stale, -1), 0.0001);
    }

    @Test
    public void aReadFromBeforeTheRepliesKeepsTheSentTarget() {
        Targets t = new Targets();
        Ha.Entity e = thermostat(20);
        int since = t.epoch();
        t.next(e, 1);
        t.sent();
        t.replied();
        t.read(since);                 // started before the command: still the old state
        assertTrue(t.has(e.id));
        t.read(t.epoch());             // started after every reply
        assertFalse(t.has(e.id));
    }

    @Test
    public void aReadWhileACommandIsOutKeepsTheSentTarget() {
        Targets t = new Targets();
        Ha.Entity e = thermostat(20);
        t.next(e, 1);
        t.sent();
        t.read(t.epoch());
        assertTrue(t.has(e.id));
        t.replied();
        t.read(t.epoch());
        assertFalse(t.has(e.id));
    }
}

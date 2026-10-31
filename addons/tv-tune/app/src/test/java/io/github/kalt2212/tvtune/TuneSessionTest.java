package io.github.kalt2212.tvtune;

import org.junit.Test;
import static org.junit.Assert.*;

public class TuneSessionTest {
    @Test public void openingTheWelcomeOrFieldsDoesNotCreateAGuideSession() {
        assertFalse(new TuneSession().started());
    }
    @Test public void onlyAnExplicitBeginStartsAnEmptyGuide() {
        TuneSession session = new TuneSession();
        assertFalse(session.started());
        session.begin();
        assertTrue(session.started());
        assertEquals(0, session.step());
        assertEquals("0,0,0,0,0", session.serialise());
    }
    @Test public void validRestoredSessionsAreStartedButInvalidOnesAreNot() {
        assertTrue(TuneSession.restore(2, "1,3,0,0,0").started());
        assertFalse(TuneSession.restore(5, "0,0,0,0,0").started());
    }
    @Test public void recordingAnOutcomeAdvancesExactlyOneCheck() {
        TuneSession session = new TuneSession();
        session.record(TuneSession.ADJUSTED);
        assertEquals(TuneSession.ADJUSTED, session.outcome(0));
        assertEquals(1, session.step());
        assertEquals(TuneSession.UNCHECKED, session.outcome(1));
    }
    @Test public void finalCheckCompletesAndBackReturnsToTheLastCheck() {
        TuneSession session = new TuneSession();
        for (int i = 0; i < 5; i++) session.record(TuneSession.SEEN);
        assertTrue(session.complete());
        session.back();
        assertFalse(session.complete());
        assertEquals(4, session.step());
    }
    @Test public void backAtTheFirstCheckDoesNotLeaveTheArray() {
        TuneSession session = new TuneSession(); session.back(); assertEquals(0, session.step());
    }
    @Test public void invalidRestoreCannotInventCompletedChecks() {
        TuneSession session = TuneSession.restore(900, "2,3,BOGUS,1,2");
        assertEquals(0, session.step());
        assertEquals("0,0,0,0,0", session.serialise());
    }
    @Test public void savedSessionPreservesSkippedAndUnfinishedChecks() {
        TuneSession session = TuneSession.restore(2, "1,3,0,0,0");
        assertEquals(2, session.step());
        assertEquals(TuneSession.SKIPPED, session.outcome(1));
        assertEquals(TuneSession.UNCHECKED, session.outcome(2));
    }
    @Test public void invalidOutcomeCannotAdvanceTheSession() {
        TuneSession session = new TuneSession();
        try { session.record(900); fail("Expected rejected outcome"); }
        catch (IllegalArgumentException expected) { assertEquals(0, session.step()); }
    }
    @Test public void idleTimeoutRequiresAnExplicitResumeAndDoesNotChangePatch() {
        PatternState state = new PatternState(0);
        state.tick(120001); assertTrue(state.dimmed());
        state.move(1, 120002); assertFalse(state.dimmed()); assertEquals(4, state.index());
        state.move(1, 120003); assertEquals(5, state.index());
    }
    @Test public void fieldsUseAppFullRangeAndStayWithinBounds() {
        PatternState state = new PatternState(0);
        state.move(-100, 1); assertEquals("black", state.id()); assertEquals(0, state.rgb()[0]);
        state.move(7, 2); assertEquals("white", state.id()); assertEquals(255, state.rgb()[0]);
        state.move(100, 3); assertEquals("blue", state.id());
        int[] rgb = state.rgb(); rgb[0] = 199;
        assertEquals(0, state.rgb()[0]);
    }
}

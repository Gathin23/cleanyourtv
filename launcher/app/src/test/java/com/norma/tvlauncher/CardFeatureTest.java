package com.norma.tvlauncher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The guard that keeps a failing module card feature from taking the home screen down. */
public class CardFeatureTest {

    private final List<Throwable> reported = new ArrayList<>();

    @Before
    public void setUp() {
        CardFeature.reset();
        CardFeature.reporter = reported::add;
    }

    @After
    public void tearDown() {
        CardFeature.reset();
    }

    @Test
    public void aWorkingStepReturnsItsValue() {
        assertEquals("ok", CardFeature.run(() -> "ok", "fallback"));
        assertTrue(CardFeature.working());
        assertTrue(reported.isEmpty());
    }

    @Test
    public void anExceptionGivesTheFallbackAndSwitchesCardsOff() {
        List<String> none = Collections.emptyList();
        List<String> tiles = CardFeature.run(() -> {
            throw new IllegalStateException("bad store");
        }, none);
        assertEquals(none, tiles);                    // the adapter gets no cards: ordinary tiles only
        assertFalse(CardFeature.working());
        assertEquals(1, reported.size());
        // From now on every step is skipped, even one that would work, and nothing more is logged.
        assertEquals("fallback", CardFeature.run(() -> "would work", "fallback"));
        assertEquals(1, reported.size());
    }

    @Test
    public void aClassThatFailsToInitialiseIsCaught() {
        assertEquals("fallback", CardFeature.run(() -> {
            throw new ExceptionInInitializerError(new RuntimeException("regex rejected by this runtime"));
        }, "fallback"));
        assertFalse(CardFeature.working());
        CardFeature.reset();
        assertEquals("fallback", CardFeature.run(() -> {
            throw new NoClassDefFoundError("com.norma.tvlauncher.Cards");
        }, "fallback"));
        assertFalse(CardFeature.working());
        assertEquals(2, reported.size());
    }

    @Test
    public void outOfMemoryIsNotSwallowed() {
        try {
            CardFeature.run(() -> {
                throw new OutOfMemoryError();
            }, "fallback");
            fail();
        } catch (OutOfMemoryError expected) {
        }
        assertTrue(CardFeature.working());
    }

    @Test
    public void aFailingReporterDoesNotMatter() {
        CardFeature.reporter = t -> {
            throw new RuntimeException("log unavailable");
        };
        assertEquals("fallback", CardFeature.run(() -> {
            throw new RuntimeException();
        }, "fallback"));
        assertFalse(CardFeature.working());
    }
}

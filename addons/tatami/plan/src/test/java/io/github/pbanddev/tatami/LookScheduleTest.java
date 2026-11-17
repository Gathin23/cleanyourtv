package io.github.pbanddev.tatami;

import static io.github.pbanddev.tatami.LookSchedule.Mode.BOTH;
import static io.github.pbanddev.tatami.LookSchedule.Mode.NAGAYA;
import static io.github.pbanddev.tatami.LookSchedule.Mode.YOJOHAN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LookScheduleTest {
    private static final double DIP = LookSchedule.DIP_SECONDS, WAIT = LookSchedule.WAIT_SECONDS;
    private static final double NEVER = Double.POSITIVE_INFINITY;
    private static final double LONG_AGO = 0;
    /** A wall-clock time near today's. */
    private static final double NOW = 1_793_500_000.0;
    private static final int[] PERIODS = {1, 5, 10, 30, 60};

    private final LookSchedule s = new LookSchedule();

    private void check(double t, LookSchedule.Mode mode, double swapSeconds, double readySince, double startedAt,
            Look look, float fade) {
        s.at(t, mode, swapSeconds, readySince, startedAt);
        String at = "t=" + t + " mode=" + mode + " swap=" + swapSeconds;
        assertSame(at, look, s.look);
        assertEquals(at, fade, s.fade, 1e-5f);
    }

    /** Started long ago, Nagaya ready long ago. */
    private void settled(double t, LookSchedule.Mode mode, double swapSeconds, Look look, float fade) {
        check(t, mode, swapSeconds, LONG_AGO, LONG_AGO, look, fade);
    }

    @Test
    public void yojohanAndNagayaModesHoldTheirLook() {
        for (double t = NOW; t < NOW + 7200; t += 37.3) {
            settled(t, YOJOHAN, 600, Look.YOJOHAN, 1f);
            settled(t, NAGAYA, 600, Look.NAGAYA, 1f);
        }
    }

    @Test
    public void bothShowsNagayaInEvenPeriodsAndYojohanInOddOnes() {
        for (int minutes : PERIODS) {
            double p = minutes * 60.0;
            long first = (long) Math.floor(NOW / p);
            for (long n = first; n < first + 4; n++) {
                settled((n + 0.5) * p, BOTH, p, n % 2 == 0 ? Look.NAGAYA : Look.YOJOHAN, 1f);
            }
        }
    }

    @Test
    public void everySwapDipsToDarkAndChangesLookInTheDark() {
        for (int minutes : PERIODS) {
            double p = minutes * 60.0;
            long first = (long) Math.ceil(NOW / p);
            for (long k = first; k < first + 2; k++) {   // one swap into each look
                double b = k * p;
                Look before = k % 2 == 0 ? Look.YOJOHAN : Look.NAGAYA, after = k % 2 == 0 ? Look.NAGAYA : Look.YOJOHAN;
                settled(b - DIP - 0.01, BOTH, p, before, 1f);
                settled(b - DIP / 2, BOTH, p, before, 0.5f);
                settled(b, BOTH, p, after, 0f);
                settled(b + DIP / 2, BOTH, p, after, 0.5f);
                settled(b + DIP + 0.01, BOTH, p, after, 1f);
                s.at(b - 1 / 60.0, BOTH, p, LONG_AGO, LONG_AGO);
                assertTrue("one frame before the swap it is all but black", s.fade < 1e-3f);
                s.at(b + 1 / 60.0, BOTH, p, LONG_AGO, LONG_AGO);
                assertTrue("one frame after the swap it is all but black", s.fade < 1e-3f);
            }
        }
    }

    @Test
    public void fadeIsOneOutsideSwaps() {
        for (int minutes : PERIODS) {
            double p = minutes * 60.0;
            double b = Math.ceil(NOW / p) * p;
            for (double t = b; t < b + 2 * p; t += p / 997) {
                double fromSwap = Math.min(t - b, Math.min(Math.abs(b + p - t), b + 2 * p - t));
                if (fromSwap <= DIP) continue;
                s.at(t, BOTH, p, LONG_AGO, LONG_AGO);
                assertEquals("t=" + t, 1f, s.fade, 0f);
            }
        }
    }

    @Test
    public void aStartWhileNagayaBuildsWaitsInTheDarkThenFadesNagayaIn() {
        double start = NOW, ready = NOW + 0.2;
        for (LookSchedule.Mode mode : new LookSchedule.Mode[] {NAGAYA, BOTH}) {
            double p = 600, t0 = Math.floor(NOW / (2 * p)) * 2 * p + 100;   // in an even period: Nagaya's
            double s0 = mode == BOTH ? t0 : start, r0 = s0 + (ready - start);
            check(s0, mode, p, r0, s0, Look.YOJOHAN, 0f);
            check(r0, mode, p, r0, s0, Look.NAGAYA, 0f);
            check(r0 + DIP / 2, mode, p, r0, s0, Look.NAGAYA, 0.5f);
            check(r0 + DIP + 0.01, mode, p, r0, s0, Look.NAGAYA, 1f);
        }
    }

    @Test
    public void aStartInYojohansPeriodShowsItAtOnce() {
        double p = 600, t0 = Math.floor(NOW / (2 * p)) * 2 * p + p + 100;   // an odd period
        check(t0, BOTH, p, t0 + 0.2, t0, Look.YOJOHAN, 1f);
        check(t0 + 1, BOTH, p, t0 + 0.2, t0, Look.YOJOHAN, 1f);
    }

    @Test
    public void aLateNagayaLetsYojohanInThenDipsOver() {
        double start = NOW, ready = NOW + WAIT + 10;
        check(start + WAIT / 2, NAGAYA, 600, ready, start, Look.YOJOHAN, 0f);
        check(start + WAIT + DIP / 2, NAGAYA, 600, ready, start, Look.YOJOHAN, 0.5f);
        check(start + WAIT + DIP + 0.01, NAGAYA, 600, ready, start, Look.YOJOHAN, 1f);
        check(ready + DIP / 2, NAGAYA, 600, ready, start, Look.YOJOHAN, 0.5f);
        check(ready + DIP, NAGAYA, 600, ready, start, Look.NAGAYA, 0f);
        check(ready + 1.5 * DIP, NAGAYA, 600, ready, start, Look.NAGAYA, 0.5f);
        check(ready + 2 * DIP + 0.01, NAGAYA, 600, ready, start, Look.NAGAYA, 1f);
    }

    @Test
    public void aNagayaThatNeverComesLeavesYojohanWithoutDips() {
        for (int minutes : PERIODS) {
            double p = minutes * 60.0;
            double b = Math.ceil(NOW / p) * p;
            for (double t = b; t < b + 3 * p; t += 0.3) check(t, BOTH, p, NEVER, LONG_AGO, Look.YOJOHAN, 1f);
        }
        check(NOW + WAIT + DIP + 1, NAGAYA, 600, NEVER, NOW, Look.YOJOHAN, 1f);
    }

    @Test
    public void aLateNagayaWaitsForItsOwnPeriodInBoth() {
        double p = 600, odd = (Math.floor(NOW / (2 * p)) * 2 + 1) * p;   // Yojōhan's period
        double ready = odd + 100;
        check(ready + DIP, BOTH, p, ready, LONG_AGO, Look.YOJOHAN, 1f);   // no dip in Yojōhan's period
        check(odd + p - DIP / 2, BOTH, p, ready, LONG_AGO, Look.YOJOHAN, 0.5f);
        check(odd + p, BOTH, p, ready, LONG_AGO, Look.NAGAYA, 0f);
        check(odd + p + DIP + 0.01, BOTH, p, ready, LONG_AGO, Look.NAGAYA, 1f);
    }
}

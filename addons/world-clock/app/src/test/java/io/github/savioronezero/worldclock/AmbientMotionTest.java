package io.github.savioronezero.worldclock;

import org.junit.Test;
import static org.junit.Assert.*;

public class AmbientMotionTest {
    @Test public void everySavedClockFitsInOneOrTwoRows() {
        for (int count=1;count<=6;count++) {
            assertEquals(Math.min(count,3),AmbientMotion.columns(count));
            assertEquals(count<=3?1:2,AmbientMotion.rows(count));
            assertTrue(AmbientMotion.rows(count)*AmbientMotion.columns(count)>=count);
        }
        assertEquals(2,AmbientMotion.rows(6));
        assertEquals(3,AmbientMotion.columns(6));
    }
    @Test public void driftStaysInsideTheReservedInsetEvenAfterLongUptime() {
        for(long time:new long[]{0,1,34000,Long.MAX_VALUE,Long.MIN_VALUE,-1})
            for(long period:new long[]{137000,173000})
                assertTrue(Math.abs(AmbientMotion.drift(time,period)*AmbientMotion.DRIFT_DP)<=12);
    }
    @Test public void driftIsContinuousAcrossThePeriodBoundary() {
        for(long period:new long[]{137000,173000}) {
            assertEquals(AmbientMotion.drift(0,period),AmbientMotion.drift(period,period),0);
            assertTrue(Math.abs(AmbientMotion.drift(period-1,period)-AmbientMotion.drift(period,period))<0.001);
        }
    }
    @Test public void brightnessChoicesStayReadableAndOrdered() {
        assertTrue(AmbientMotion.clockAlpha(0)>0);
        assertTrue(AmbientMotion.clockAlpha(0)<AmbientMotion.clockAlpha(1));
        assertTrue(AmbientMotion.clockAlpha(1)<AmbientMotion.clockAlpha(2));
        assertTrue(AmbientMotion.clockAlpha(2)<=1);
    }
    @Test(expected=IllegalArgumentException.class) public void noEmptyScreen() { AmbientMotion.rows(0); }
    @Test(expected=IllegalArgumentException.class) public void noHiddenSeventhClock() { AmbientMotion.columns(7); }
}

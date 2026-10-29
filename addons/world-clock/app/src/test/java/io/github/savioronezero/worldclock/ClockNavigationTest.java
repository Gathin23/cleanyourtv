package io.github.savioronezero.worldclock;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashSet;
import java.util.Set;

public class ClockNavigationTest {
    @Test public void everyClockCountAndDirectionStaysOnAnExistingNode() {
        for(boolean rtl:new boolean[]{false,true}) for(int count=1;count<=6;count++)
            for(int node=0;node<count+2;node++) for(int d=0;d<4;d++) {
                int next=ClockNavigation.next(node,count,d,rtl);
                assertTrue(next>=0 && next<count+2);
            }
    }
    @Test public void everyControlCanBeReachedWithOnlyTheDpad() {
        for(boolean rtl:new boolean[]{false,true}) for(int count=1;count<=6;count++) {
            Set<Integer> seen=new HashSet<>();seen.add(2);
            boolean changed;
            do {
                changed=false;
                for(int node:new HashSet<>(seen)) for(int d=0;d<4;d++)
                    changed|=seen.add(ClockNavigation.next(node,count,d,rtl));
            } while(changed);
            assertEquals(count+2,seen.size());
        }
    }
    @Test public void partialLastRowClampsDownAndCanReturnUp() {
        assertEquals(5,ClockNavigation.next(4,4,ClockNavigation.DOWN));
        assertEquals(2,ClockNavigation.next(5,4,ClockNavigation.UP));
        assertEquals(5,ClockNavigation.next(5,4,ClockNavigation.RIGHT));
    }
    @Test public void rtlMirrorsHorizontalMovementWithoutChangingVerticalTargets() {
        assertEquals(3,ClockNavigation.next(2,3,ClockNavigation.LEFT,true));
        assertEquals(2,ClockNavigation.next(2,3,ClockNavigation.RIGHT,true));
        assertEquals(ClockNavigation.next(4,6,ClockNavigation.DOWN,false),ClockNavigation.next(4,6,ClockNavigation.DOWN,true));
    }
}

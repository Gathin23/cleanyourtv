package com.lionlev.tv.capacity;
import org.junit.Test;
import static org.junit.Assert.*;
public class StatsCapacityTest {
    @Test public void memoryUsesAvailableThenTotal() {
        assertEquals("850/2850",StatsCapacity.memory(850,2850));
        assertEquals("0/2850",StatsCapacity.memory(0,2850));
    }
    @Test public void missingValuesAreNotPresentedAsRealCapacity() {
        assertEquals("850",StatsCapacity.memory(850,-1));
        assertEquals("",StatsCapacity.memory(-1,2850));
        assertEquals("",StatsCapacity.storage(-1,1024));
    }
    @Test public void storageUsesOneUnitAndSupportsZeroAvailable() {
        long gb=1024L*1024*1024;
        assertEquals("0.8/4.0 GB",StatsCapacity.storage(gb*8/10,gb*4));
        assertEquals("0.0/4.0 GB",StatsCapacity.storage(0,gb*4));
        assertEquals("1.0 GB",StatsCapacity.storage(gb,0));
    }
}

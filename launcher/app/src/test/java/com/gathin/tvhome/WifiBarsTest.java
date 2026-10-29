package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WifiBarsTest {

    @Test
    public void strongFromMinus60() {
        assertEquals(3, WifiBars.count(-40));
        assertEquals(3, WifiBars.count(-60));
        assertEquals(R.drawable.ic_wifi, WifiBars.icon(-60));
    }

    @Test
    public void moderateFromMinus70() {
        assertEquals(2, WifiBars.count(-61));
        assertEquals(2, WifiBars.count(-70));
        assertEquals(R.drawable.ic_wifi_2, WifiBars.icon(-65));
    }

    @Test
    public void weakBelowAndUnknown() {
        assertEquals(1, WifiBars.count(-71));
        assertEquals(1, WifiBars.count(-127));   // WifiInfo's INVALID_RSSI
        assertEquals(R.drawable.ic_wifi_1, WifiBars.icon(-80));
    }
}

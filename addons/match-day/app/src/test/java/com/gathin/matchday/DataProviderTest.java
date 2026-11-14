package com.gathin.matchday;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DataProviderTest {
    @Test public void sportScoreIsDefaultAndSelectionsRoundTrip() {
        assertEquals(DataProvider.SPORT_SCORE, DataProvider.fromPreference(null));
        assertEquals(DataProvider.SPORT_SCORE,
                DataProvider.fromPreference(DataProvider.SPORT_SCORE.preferenceValue));
        assertEquals(DataProvider.FOOTBALL_DATA,
                DataProvider.fromPreference(DataProvider.FOOTBALL_DATA.preferenceValue));
    }
}

package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HebrewTest {

    @Test
    public void singleDigitsGetGeresh() {
        assertEquals("א׳", Hebrew.numeral(1));
        assertEquals("י׳", Hebrew.numeral(10));
    }

    @Test
    public void tensPairLettersWithGershayim() {
        assertEquals("כ״ט", Hebrew.numeral(29));
        assertEquals("ל׳", Hebrew.numeral(30));
    }

    @Test
    public void divineNamePairsAreSpelledDifferently() {
        assertEquals("ט״ו", Hebrew.numeral(15));
        assertEquals("ט״ז", Hebrew.numeral(16));
    }

    @Test
    public void hundredsChainDown() {
        assertEquals("תשפ״ז", Hebrew.numeral(787));
        assertEquals("שע״ה", Hebrew.numeral(375));
    }

    @Test
    public void outOfRangePrintsTheNumber() {
        assertEquals("0", Hebrew.numeral(0));
        assertEquals("1000", Hebrew.numeral(1000));
    }

    @Test
    public void minuteStringPadsZeroes() {
        assertEquals("16:34", Hebrew.minuteString(994));
        assertEquals("00:07", Hebrew.minuteString(7));
        assertEquals("23:59", Hebrew.minuteString(1439));
    }
}

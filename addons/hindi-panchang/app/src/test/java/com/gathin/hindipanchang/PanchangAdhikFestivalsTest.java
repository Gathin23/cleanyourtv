package com.gathin.hindipanchang;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.Test;

public class PanchangAdhikFestivalsTest {
    private static final LocationStore.Place DELHI = new LocationStore.Place(
            "New Delhi", "Delhi, India", 28.6139, 77.2090, "Asia/Kolkata");

    private static List<String> festivals(int year, int month, int day) {
        return PanchangEngine.calculate(LocalDate.of(year, month, day), DELHI).festivalsEn;
    }

    @Test
    public void adhikJyeshthaHasNoNirjalaEkadashi() {
        assertFalse(festivals(2026, 5, 26).contains("Nirjala Ekadashi"));
        assertFalse(festivals(2026, 5, 27).contains("Nirjala Ekadashi"));
    }

    @Test
    public void nirjalaEkadashiOnRealDate() {
        assertTrue(festivals(2026, 6, 25).contains("Nirjala Ekadashi"));
    }

    @Test
    public void adhikShravanHasNoRakshaBandhan() {
        assertFalse(festivals(2023, 8, 1).contains("Raksha Bandhan"));
        assertTrue(festivals(2023, 8, 31).contains("Raksha Bandhan"));
    }

    @Test
    public void adhikShravanKeepsShravanSomwarVrat() {
        assertTrue(festivals(2023, 7, 24).contains("Shravan Somwar Vrat"));
    }

    @Test
    public void adhikPurnimaStillListsPurnimaVrat() {
        boolean found = false;
        for (String f : festivals(2023, 8, 1)) {
            if (f.endsWith("Purnima Vrat")) {
                found = true;
            }
        }
        assertTrue(found);
    }
}

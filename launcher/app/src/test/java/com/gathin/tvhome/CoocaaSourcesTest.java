package com.gathin.tvhome;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CoocaaSourcesTest {

    private static Object[] row(String name, int index, String label) {
        return new Object[]{name, index, label};
    }

    @Test public void primaryEmptyFallsBackToSdkRows() {
        // The Coocaa 70A9 answers nothing on the primary authority; the list must come from the
        // sdk.provider rows instead of being dropped (#1351).
        List<Object[]> sdk = Arrays.asList(row("HDMI", 0, "STB"), row("AV", -1, "Video"));
        List<CoocaaSources.Source> out =
                CoocaaSources.parseAndSort(CoocaaSources.firstNonEmpty(Collections.emptyList(), sdk));
        assertEquals(2, out.size());
        assertEquals("HDMI", out.get(0).name);
        assertEquals("AV", out.get(1).name);
    }

    @Test public void nullPrimaryFallsBackToSdkRows() {
        List<Object[]> sdk = Collections.singletonList(row("VOD", -1, "TV"));
        assertSame(sdk, CoocaaSources.firstNonEmpty(null, sdk));
    }

    @Test public void primaryRowsWinWhenPresent() {
        List<Object[]> primary = Collections.singletonList(row("HDMI", 1, "Console"));
        List<Object[]> sdk = Collections.singletonList(row("AV", -1, "Video"));
        assertSame(primary, CoocaaSources.firstNonEmpty(primary, sdk));
    }

    @Test public void bothEmptyIsEmptyNotNull() {
        assertTrue(CoocaaSources.firstNonEmpty(null, null).isEmpty());
        assertTrue(CoocaaSources.parseAndSort(CoocaaSources.firstNonEmpty(null, null)).isEmpty());
    }

    @Test public void parseAndSortOrdersHdmiThenAvThenTuner() {
        List<Object[]> rows = new ArrayList<>(Arrays.asList(
                row("VOD", -1, "TV"),
                row("HDMI", 2, "HDMI2"),
                row("AV", -1, "Video"),
                row("HDMI", 1, "HDMI1")));
        List<CoocaaSources.Source> out = CoocaaSources.parseAndSort(rows);
        assertEquals(Arrays.asList("HDMI", "HDMI", "AV", "VOD"),
                Arrays.asList(out.get(0).name, out.get(1).name, out.get(2).name, out.get(3).name));
        // HDMI ports stay in port order.
        assertEquals(1, out.get(0).index);
        assertEquals(2, out.get(1).index);
    }

    @Test public void namelessRowsAreSkipped() {
        List<Object[]> rows = Arrays.asList(row(null, 0, "ignored"), row("HDMI", 0, "HDMI1"));
        List<CoocaaSources.Source> out = CoocaaSources.parseAndSort(rows);
        assertEquals(1, out.size());
        assertEquals("HDMI", out.get(0).name);
    }

    @Test public void emptyOrNullLabelDefaultsToName() {
        List<CoocaaSources.Source> out = CoocaaSources.parseAndSort(
                Arrays.asList(row("HDMI", 0, ""), row("AV", -1, null)));
        assertEquals("HDMI", out.get(0).label);
        assertEquals("AV", out.get(1).label);
    }
}

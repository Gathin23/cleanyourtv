package com.gathin.tvhome;

/**
 * date_below_clock: the arithmetic that keeps the header's gaps even, as plain numbers so it can be tested without a
 * TV. Text views carry empty room above the capitals and below the baseline; the gaps are measured between the ink.
 */
final class HeaderLayout {

    private HeaderLayout() {
    }

    /**
     * Top margin for a line under another so the ink gap is {@code gap}: the line above leaves {@code belowInk} px
     * under its ink (descent), the line below has {@code aboveInk} px over its ink. Negative pulls the line up.
     */
    static int marginForGap(int gap, int belowInk, int aboveInk) {
        return gap - belowInk - aboveInk;
    }

    /** Top of a view of {@code height} centred on the span from {@code inkTop} to {@code inkBottom}. */
    static int centredTop(int inkTop, int inkBottom, int height) {
        return (inkTop + inkBottom - height) / 2;
    }
}

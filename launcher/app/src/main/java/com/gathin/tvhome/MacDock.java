package com.gathin.tvhome;

/** mac_dock: square icons packed in the middle of the dock row, a dot under the focused one. */
final class MacDock {

    private MacDock() {
    }

    /** The icon's side: well under a grid tile's width, so the dock reads compact and light. */
    static int iconPx(int tileWidthPx) {
        return Math.round(tileWidthPx * 0.6f);
    }

    /** Centre to centre: the icons stay their size, so a fifth of a side between them is enough. */
    static float pitchPx(int iconPx) {
        return iconPx * 1.2f;
    }

    /** The squircle-like corner of a macOS icon. */
    static float radiusPx(int iconPx) {
        return iconPx * 0.225f;
    }

    /**
     * How far dock slot {@code slot} of {@code slots} moves sideways from the middle of its grid cell so the icons sit
     * {@code pitch} apart, centred on the row.
     */
    static int shiftPx(int slot, int slots, float cellPx, float pitchPx) {
        return Math.round((slot - (slots - 1) / 2f) * (pitchPx - cellPx));
    }
}

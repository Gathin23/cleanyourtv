package io.github.gagantk.kannadacalendar;

/** The colours of the light (paper) and dark (night) looks. The current one is picked when the screen is built. */
final class Palette {
    final boolean dark;
    /**
     * {@code red} is kept for Sundays and public holidays alone, so they stand out from across the room;
     * {@code accent} is the saffron dark enough to read as text; {@code today} fills the badge behind today's date.
     */
    final int wallTop, wallBottom, paperTop, paperBottom, grain, ink, muted, line, red, bandTop, bandBottom, bandText,
            bandSub, saffron, focusFill, sundayTint, tile, moonLight, accent, today;

    private Palette(boolean dark, int... c) {
        this.dark = dark;
        wallTop = c[0]; wallBottom = c[1]; paperTop = c[2]; paperBottom = c[3]; grain = c[4]; ink = c[5]; muted = c[6];
        line = c[7]; red = c[8]; bandTop = c[9]; bandBottom = c[10]; bandText = c[11]; bandSub = c[12]; saffron = c[13];
        focusFill = c[14]; sundayTint = c[15]; tile = c[16]; moonLight = c[17]; accent = c[18]; today = c[19];
    }

    static final Palette LIGHT = new Palette(false,
            0xFF5B4636, 0xFF3A2B21, 0xFFFBF6EA, 0xFFF3E9D5, 0x5A4636, 0xFF2A1E14, 0xFF7A6A58,
            0xFFDCCDB4, 0xFFD0201A, 0xFFC0302A, 0xFFA2211B, 0xFFFFF6E8, 0xFFF6D9B8, 0xFFE8793A,
            0xFFFBE0BC, 0x29D0201A, 0xFFF1E6D1, 0xFFFFFFFF, 0xFFB4561C, 0xFFB4561C);

    static final Palette DARK = new Palette(true,
            0xFF1A1411, 0xFF0B0907, 0xFF2A241E, 0xFF201B16, 0x000000, 0xFFF1E6D2, 0xFFA8998A,
            0xFF443A30, 0xFFFF7468, 0xFF8E1F19, 0xFF6A1510, 0xFFFFEBD6, 0xFFE9BFA0, 0xFFF08A4B,
            0xFF4A3423, 0x38FF7468, 0xFF332B24, 0xFFF1E6D2, 0xFFF5A262, 0xFFC8571C);

    static Palette current = LIGHT;
}

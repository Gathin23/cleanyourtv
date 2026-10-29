package io.github.justinnewman.gameday;

import java.util.Locale;
import java.util.Set;

/**
 * The 36 colours a calendar's bar can take: 12 colours in three shades, laid out as the picker shows them, one row
 * per shade (index = shade * 12 + colour). The dark shades are lifted a little so the thin bar still shows on black.
 */
final class Palette {
    static final int COUNT = 36;
    static final int COLUMNS = 12;

    private static final String[] COLOURS = {
        "Red", "Orange", "Amber", "Olive", "Green", "Teal", "Cyan", "Blue", "Indigo", "Purple", "Pink", "Grey"
    };
    private static final String[] SHADES = {"light", "medium", "dark"};
    private static final int[] ARGB = {
        0xFFE57373, 0xFFFFB74D, 0xFFFFE082, 0xFFD4E157, 0xFF81C784, 0xFF4DB6AC,
        0xFF4DD0E1, 0xFF64B5F6, 0xFF9FA8DA, 0xFFCE93D8, 0xFFF48FB1, 0xFFECEFF1,
        0xFFC62828, 0xFFEF6C00, 0xFFF9A825, 0xFF9E9D24, 0xFF2E7D32, 0xFF00897B,
        0xFF0097A7, 0xFF1565C0, 0xFF283593, 0xFF6A1B9A, 0xFFAD1457, 0xFF90A4AE,
        0xFF8E1B1B, 0xFFA84A00, 0xFF9A7400, 0xFF646414, 0xFF1B5E20, 0xFF00574F,
        0xFF006370, 0xFF0D3F86, 0xFF1A237E, 0xFF4A148C, 0xFF880E4F, 0xFF546E7A,
    };
    /** firstUnused offers medium first, then light, then dark. */
    private static final int[] ROW_ORDER = {1, 0, 2};

    private Palette() {
    }

    static int colour(int index) {
        return ARGB[index];
    }

    /** "Green, dark". */
    static String name(int index) {
        return COLOURS[index % COLUMNS] + ", " + SHADES[index / COLUMNS];
    }

    static int byName(String name) {
        if (name == null) return -1;
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < COUNT; i++) {
            if (name(i).toLowerCase(Locale.ROOT).equals(wanted)) return i;
        }
        return -1;
    }

    /** The first colour no calendar uses yet; when all are used, medium red. */
    static int firstUnused(Set<Integer> used) {
        for (int row : ROW_ORDER) {
            for (int c = 0; c < COLUMNS; c++) {
                int i = row * COLUMNS + c;
                if (!used.contains(i)) return i;
            }
        }
        return COLUMNS;
    }
}

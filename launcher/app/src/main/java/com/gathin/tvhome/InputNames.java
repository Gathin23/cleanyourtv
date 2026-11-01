package com.gathin.tvhome;

import java.util.Locale;

/**
 * Philips (TP Vision) saves an input's name from its Sources menu as "Name&TYPE", the type picking the icon there
 * ("Xbox&GAME_CONSOLE", "Ordinateur&COMPUTER"), and an unnamed port as the text "null". Shown as is, the Inputs
 * menu read "Xbox&GAME_CONSOLE · HDMI 1" and "null · HDMI 4". This gives "🎮 Xbox" and no name; the TV's own
 * setting is left as it is.
 */
final class InputNames {
    private InputNames() {
    }

    static String tidy(CharSequence raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty() || s.equals("null")) return null;
        int amp = s.lastIndexOf('&');
        // Only a type in Philips' form (three or more capitals, digits or underscores) is taken off, so "Tom & Jerry" stays.
        if (amp < 0 || !s.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}")) return s;
        String name = s.substring(0, amp).trim();
        String emoji = emoji(s.substring(amp + 1));
        if (name.isEmpty()) return emoji;
        return emoji == null ? name : emoji + " " + name;
    }

    private static String emoji(String type) {
        String t = type.toUpperCase(Locale.ROOT);
        if (t.contains("GAME")) return "🎮";                                                   // 🎮
        if (t.contains("COMPUTER") || t.equals("PC")) return "💻";                             // 💻
        if (t.contains("BLU") || t.contains("DVD") || t.contains("DISC")) return "📀";         // 📀
        if (t.contains("BOX") || t.contains("STB") || t.contains("SAT") || t.contains("CABLE")) return "📡"; // 📡
        if (t.contains("AUDIO") || t.contains("SOUND") || t.contains("AMP")) return "🔊";      // 🔊
        if (t.contains("CAM")) return "📷";                                                    // 📷
        return null;
    }
}

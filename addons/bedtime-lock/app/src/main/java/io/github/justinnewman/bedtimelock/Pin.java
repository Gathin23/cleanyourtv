package io.github.justinnewman.bedtimelock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * The code: six digits, typed with the number keys or with the arrows and OK on the phone-keypad layout (Up 2,
 * Left 4, OK 5, Right 6, Down 8), so a remote without number keys can enter it and the screen shows only dots.
 * It is kept only as a salted SHA-256, never as typed, and tries are rate-limited.
 */
final class Pin {
    static final int LENGTH = 6;
    /** Wrong tries in a row before the wait, and the wait. */
    static final int MAX_TRIES = 5;
    static final long WAIT_MS = 60_000L;

    private Pin() { }

    static boolean valid(String pin) {
        return pin != null && pin.length() == LENGTH && pin.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    static String hash(String salt, String pin) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return hex(salt);
    }

    /**
     * The digit a key types, or -1: the number row, the numeric keypad, and the arrows and OK where a phone keypad
     * has them (Up 2, Left 4, OK 5, Right 6, Down 8).
     */
    static int digitOf(int keyCode) {
        if (keyCode >= 7 && keyCode <= 16) return keyCode - 7;          // KEYCODE_0 .. KEYCODE_9
        if (keyCode >= 144 && keyCode <= 153) return keyCode - 144;     // KEYCODE_NUMPAD_0 .. KEYCODE_NUMPAD_9
        switch (keyCode) {
            case 19: return 2;                                          // KEYCODE_DPAD_UP
            case 21: return 4;                                          // KEYCODE_DPAD_LEFT
            case 23: case 66: case 160: return 5;                       // DPAD_CENTER, ENTER, NUMPAD_ENTER
            case 22: return 6;                                          // KEYCODE_DPAD_RIGHT
            case 20: return 8;                                          // KEYCODE_DPAD_DOWN
            default: return -1;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return out.toString();
    }
}

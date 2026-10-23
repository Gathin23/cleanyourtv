package io.github.justinnewman.bedtimelock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** The PIN is kept only as a salted SHA-256, never as typed. Four digits are few, so tries are rate-limited too. */
final class Pin {
    static final int LENGTH = 4;
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

    /** The digit a key types, or -1: the number row and the numeric keypad. */
    static int digitOf(int keyCode) {
        if (keyCode >= 7 && keyCode <= 16) return keyCode - 7;          // KEYCODE_0 .. KEYCODE_9
        if (keyCode >= 144 && keyCode <= 153) return keyCode - 144;     // KEYCODE_NUMPAD_0 .. KEYCODE_NUMPAD_9
        return -1;
    }

    /** Up on a digit box: empty starts at 0, then counts up and wraps. */
    static int up(int digit) {
        return digit < 0 ? 0 : (digit + 1) % 10;
    }

    /** Down on a digit box: empty starts at 9, then counts down and wraps. */
    static int down(int digit) {
        return digit < 0 ? 9 : (digit + 9) % 10;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return out.toString();
    }
}

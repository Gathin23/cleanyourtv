package io.github.maxhayim.kidsmode;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * The PIN or password, with no Android types, so the JVM tests cover it. Only a salted PBKDF2 hash is kept, and
 * wrong tries are slowed down: after five, each further try waits, 30 seconds doubling up to 5 minutes.
 */
final class Lock {
    static final int MIN_PIN = 4, MAX_PIN = 8, MIN_PASSWORD = 6, MAX_PASSWORD = 64;
    static final int FREE_TRIES = 5;
    static final long FIRST_WAIT_MS = 30_000, MAX_WAIT_MS = 5 * 60_000;
    private static final int ITERATIONS = 20_000;

    private Lock() {
    }

    static boolean validPin(String s) {
        return s != null && s.length() >= MIN_PIN && s.length() <= MAX_PIN && s.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    static boolean validPassword(String s) {
        return s != null && s.length() >= MIN_PASSWORD && s.length() <= MAX_PASSWORD;
    }

    static String newSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    static String hash(String secret, String salt) {
        try {
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of a try against the stored hash. */
    static boolean matches(String attempt, String salt, String stored) {
        if (attempt == null || salt == null || stored == null || stored.isEmpty()) return false;
        return MessageDigest.isEqual(hash(attempt, salt).getBytes(), stored.getBytes());
    }

    /** How long to wait before the next try, after this many wrong ones in a row. */
    static long waitAfter(int wrong) {
        if (wrong < FREE_TRIES) return 0;
        long w = FIRST_WAIT_MS << Math.min(10, wrong - FREE_TRIES);
        return Math.min(MAX_WAIT_MS, w);
    }
}

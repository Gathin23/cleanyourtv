package io.github.cleanyourtv.staticfm;

/** Smooth value noise and a three-octave sum of it, deterministic for the same inputs. */
final class Noise {
    private Noise() {}

    static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static float value(float x, float y) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y);
        float f = x - i, g = y - j;
        float u = f * f * (3 - 2 * f), v = g * g * (3 - 2 * g);
        float a = hash(i, j), b = hash(i + 1, j), c = hash(i, j + 1), d = hash(i + 1, j + 1);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    /** About 0..1, with the detail of three octaves. */
    static float fbm(float x, float y) {
        return value(x, y) * 0.6f + value(x * 2.1f, y * 2.1f) * 0.3f + value(x * 4.3f, y * 4.3f) * 0.1f;
    }
}

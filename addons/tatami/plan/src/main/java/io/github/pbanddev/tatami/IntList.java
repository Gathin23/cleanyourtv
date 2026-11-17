package io.github.pbanddev.tatami;

import java.util.Arrays;

/** A growing list of ints, for build time only. */
final class IntList {
    private int[] values = new int[16];
    int size;

    void add(int v) {
        if (size == values.length) values = Arrays.copyOf(values, size * 2);
        values[size++] = v;
    }

    int get(int i) {
        return values[i];
    }

    void set(int i, int v) {
        values[i] = v;
    }

    int[] array() {
        return Arrays.copyOf(values, size);
    }
}

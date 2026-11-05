package io.github.cleanyourtv.topicphotos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The photos a carousel has shown, in order, so Left goes back through them and Right goes forward through them
 * again before a new photo is picked. Photos no longer in the carousel's list are skipped and dropped.
 */
final class PhotoHistory {
    /** Enough to page back through an evening; the oldest are dropped past it. */
    static final int MAX = 200;

    private final List<String> shown = new ArrayList<>();
    private int at = -1;

    /** A newly picked photo, now the last one. */
    void push(String key) {
        while (shown.size() > at + 1) shown.remove(shown.size() - 1);
        shown.add(key);
        if (shown.size() > MAX) shown.remove(0);
        at = shown.size() - 1;
    }

    /** The photo before the current one, or null at the start of the history. */
    String back(Predicate<String> present) {
        while (at > 0) {
            String key = shown.get(at - 1);
            if (present.test(key)) {
                at--;
                return key;
            }
            shown.remove(at - 1);
            at--;
        }
        return null;
    }

    /** The photo after the current one when Left went back earlier, or null at the end: then pick a new one. */
    String forward(Predicate<String> present) {
        while (at + 1 < shown.size()) {
            String key = shown.get(at + 1);
            if (present.test(key)) {
                at++;
                return key;
            }
            shown.remove(at + 1);
        }
        return null;
    }
}

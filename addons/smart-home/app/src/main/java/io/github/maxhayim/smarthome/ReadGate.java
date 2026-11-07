package io.github.maxhayim.smarthome;

/**
 * One state read at a time. Triggers while a read is out combine into one more read after it, and leaving the
 * screen or changing the connection drops reads already queued or running. Main thread only, except current().
 */
final class ReadGate {
    private volatile int generation;
    private boolean reading;
    private boolean again;

    /** A trigger: the generation to read under, or -1 when it joins the read already out. */
    int start() {
        if (reading) {
            again = true;
            return -1;
        }
        reading = true;
        return generation;
    }

    /** Whether a read started under this generation should still go out or be applied (any thread). */
    boolean current(int gen) {
        return gen == generation;
    }

    /** A read finished: true to apply it, false when it belongs to an older generation. */
    boolean finish(int gen) {
        if (gen != generation) return false;
        reading = false;
        return true;
    }

    /** Whether a trigger came in while the last read was out; asking clears it. */
    boolean takeAgain() {
        boolean a = again;
        again = false;
        return a;
    }

    /** Leaving the screen or a new connection: whatever is queued or running is ignored. */
    void reset() {
        generation++;
        reading = false;
        again = false;
    }
}

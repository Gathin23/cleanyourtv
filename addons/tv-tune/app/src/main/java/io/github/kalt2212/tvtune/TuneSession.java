package io.github.kalt2212.tvtune;

public final class TuneSession {
    public static final int UNCHECKED = 0, SEEN = 1, ADJUSTED = 2, SKIPPED = 3;
    public static final int COUNT = 5;
    private final int[] outcomes = new int[COUNT];
    private int current;
    private boolean begun;
    public boolean started() { return begun; }
    public void begin() { begun = true; }

    public void record(int outcome) {
        if (outcome < SEEN || outcome > SKIPPED) throw new IllegalArgumentException("Invalid outcome");
        if (complete()) throw new IllegalStateException("Session is complete");
        begun = true;
        outcomes[current++] = outcome;
    }
    public int outcome(int check) { return outcomes[check]; }
    public int step() { return current; }
    public void back() { current = Math.max(0, current - 1); }
    public boolean complete() { return current == COUNT; }
    public String serialise() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < COUNT; i++) { if (i > 0) result.append(','); result.append(outcomes[i]); }
        return result.toString();
    }
    public static TuneSession restore(int step, String saved) {
        TuneSession result = new TuneSession();
        if (step < 0 || step > COUNT || saved == null || !saved.matches("[0-3](,[0-3]){4}")) return result;
        String[] tokens = saved.split(",");
        for (int i = 0; i < COUNT; i++) {
            int outcome = Integer.parseInt(tokens[i]);
            if (i < step && outcome == UNCHECKED) return new TuneSession();
            result.outcomes[i] = outcome;
        }
        result.current = step;
        result.begun = true;
        return result;
    }
}

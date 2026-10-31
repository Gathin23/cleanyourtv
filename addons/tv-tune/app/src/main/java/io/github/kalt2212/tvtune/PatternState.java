package io.github.kalt2212.tvtune;

public final class PatternState {
    private static final String[] IDS = {"black", "grey-8", "grey-32", "grey-64", "grey-128", "grey-192", "grey-240", "white", "red", "green", "blue"};
    private static final int[][] RGB = {{0,0,0}, {8,8,8}, {32,32,32}, {64,64,64}, {128,128,128}, {192,192,192}, {240,240,240}, {255,255,255}, {255,0,0}, {0,255,0}, {0,0,255}};
    private int selected = 4;
    private long lastInput;
    private boolean dim;
    public PatternState(long now) { lastInput = now; }
    public void move(int delta, long now) {
        if (dim) dim = false;
        else selected = (int) Math.max(0L, Math.min(IDS.length - 1L, (long) selected + delta));
        lastInput = now;
    }
    public void tick(long now) { if (now - lastInput >= 120000) dim = true; }
    public void pause() { dim = true; }
    public boolean dimmed() { return dim; }
    public int index() { return selected; }
    public String id() { return IDS[selected]; }
    public int[] rgb() { return RGB[selected].clone(); }
}

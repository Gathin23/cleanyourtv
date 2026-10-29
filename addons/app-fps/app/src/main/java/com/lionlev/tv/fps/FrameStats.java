package com.lionlev.tv.fps;

import java.util.*;

/** Pure parsing and rate logic; never substitutes a display rate for an app counter. */
final class FrameStats {
    static final class Reading {
        final String layer;
        final long frames;
        Reading(String layer, long frames) { this.layer = layer; this.frames = frames; }
    }
    static Reading select(String component, String liveText, String statsText) {
        Set<String> live = new HashSet<>();
        boolean hasSurface = false;
        for (String line : liveText.split("\n")) {
            line = line.trim().replaceFirst("^[0-9a-f]+ ", "");
            live.add(line);
            if (line.startsWith("SurfaceView[" + component + "]")) hasSurface = true;
        }
        List<Reading> surfaces = new ArrayList<>(), mains = new ArrayList<>();
        String layer = "";
        for (String line : statsText.split("\n")) {
            if (line.startsWith("layerName = ")) layer = line.substring(12).trim();
            if (line.startsWith("totalFrames = ") && live.contains(layer)) {
                long count;
                try { count = Long.parseLong(line.substring(14).trim()); }
                catch (NumberFormatException error) { continue; }
                if (count < 0) continue;
                Reading reading = new Reading(layer, count);
                if (layer.startsWith("SurfaceView[" + component + "]")) surfaces.add(reading);
                else if (layer.startsWith(component + "#")) mains.add(reading);
            }
        }
        if (surfaces.size() == 1) return surfaces.get(0);
        return !hasSurface && mains.size() == 1 ? mains.get(0) : null;
    }
    private Reading previous;
    private long previousTime;
    double sample(Reading reading, long nowNanos) {
        Reading old = previous;
        long oldTime = previousTime;
        previous = reading; previousTime = nowNanos;
        if (reading == null || old == null || !reading.layer.equals(old.layer)
                || reading.frames < old.frames || nowNanos <= oldTime) return -1;
        double value = (reading.frames - old.frames) * 1_000_000_000d / (nowNanos - oldTime);
        return value <= 1000 ? value : -1;
    }
    void reset() { previous = null; previousTime = 0; }
}

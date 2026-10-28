package io.github.maxhayim.startupbranding;

/** Where things go on the welcome screen, as {left, top, right, bottom}. Plain Java, tested on the computer. */
final class Fit {
    private Fit() {
    }

    /** The largest 16:9 frame centered in a {@code w} by {@code h} screen (black bars on other shapes). */
    static int[] frame(int w, int h) {
        if (w <= 0 || h <= 0) return new int[]{0, 0, 0, 0};
        long fw = w, fh = (long) w * 9 / 16;
        if (fh > h) {
            fh = h;
            fw = (long) h * 16 / 9;
        }
        int l = (int) ((w - fw) / 2), t = (int) ((h - fh) / 2);
        return new int[]{l, t, l + (int) fw, t + (int) fh};
    }

    /**
     * The picture's place inside {@code frame}. Fit shows all of it, Fill covers the frame (cropped by the frame),
     * Stretch ignores its shape, Original keeps its pixel size at {@code density} (shrunk to fit if bigger).
     */
    static int[] image(int iw, int ih, int[] frame, Look.Scale scale, Look.Align align, float density) {
        int fw = frame[2] - frame[0], fh = frame[3] - frame[1];
        if (iw <= 0 || ih <= 0 || fw <= 0 || fh <= 0) return new int[]{frame[0], frame[1], frame[0], frame[1]};
        double w, h;
        switch (scale) {
            case STRETCH:
                return frame.clone();
            case FILL: {
                double k = Math.max((double) fw / iw, (double) fh / ih);
                w = iw * k;
                h = ih * k;
                break;
            }
            case ORIGINAL: {
                w = iw * (double) density;
                h = ih * (double) density;
                double k = Math.min(1, Math.min(fw / w, fh / h));
                w *= k;
                h *= k;
                break;
            }
            default: {
                double k = Math.min((double) fw / iw, (double) fh / ih);
                w = iw * k;
                h = ih * k;
            }
        }
        int rw = (int) Math.round(w), rh = (int) Math.round(h);
        int l = frame[0] + (fw - rw) / 2, t = frame[1] + (fh - rh) / 2;
        switch (align) {
            case TOP: t = frame[1]; break;
            case BOTTOM: t = frame[3] - rh; break;
            case LEFT: l = frame[0]; break;
            case RIGHT: l = frame[2] - rw; break;
            default: break;
        }
        return new int[]{l, t, l + rw, t + rh};
    }

    /** How much to shrink a picture while decoding so it is no bigger than needed (a power of two). */
    static int sampleSize(int iw, int ih, int maxW, int maxH) {
        int s = 1;
        while (iw / (s * 2) >= maxW && ih / (s * 2) >= maxH) s *= 2;
        return s;
    }

    /** PNG, JPEG or WebP by the file's first bytes, or null for anything else. */
    static String kind(byte[] head) {
        if (head == null) return null;
        if (head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') return "png";
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) return "jpg";
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return "webp";
        return null;
    }
}

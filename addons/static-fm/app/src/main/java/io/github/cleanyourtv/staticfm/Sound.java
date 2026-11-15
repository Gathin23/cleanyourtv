package io.github.cleanyourtv.staticfm;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.util.Log;

/**
 * The TV's own sound, through Android's Visualizer on the output mix (session 0): the spectrum of what is playing,
 * never the microphone. Needs RECORD_AUDIO all the same, as Android asks it of every Visualizer. Without it, or on
 * firmware that refuses session 0, {@link #read} returns false and the picture breathes on its own.
 */
final class Sound {
    private static final String TAG = "StaticFM";
    private Visualizer visualizer;
    private byte[] fft;
    private int rate;
    float bass, mid, high;

    void open(Context context) {
        if (visualizer != null) return;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Visualizer v = new Visualizer(0);
            v.setEnabled(false);
            int[] range = Visualizer.getCaptureSizeRange();
            v.setCaptureSize(Math.min(1024, range[1]));
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            v.setEnabled(true);
            fft = new byte[v.getCaptureSize()];
            rate = v.getSamplingRate(); // milliHertz
            visualizer = v;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            Log.w(TAG, "no Visualizer on the output mix: " + e);
            close();
        }
    }

    /** Reads one spectrum into {@link #bass}, {@link #mid} and {@link #high} (raw energies); false when there is no sound source. */
    boolean read() {
        Visualizer v = visualizer;
        if (v == null) return false;
        try {
            if (v.getFft(fft) != Visualizer.SUCCESS) return false;
        } catch (IllegalStateException e) {
            return false;
        }
        float hzPerBin = (rate / 1000f) / fft.length; // n/2 bins over 0..rate/2
        bass = band(fft, hzPerBin, 40, 180);
        mid = band(fft, hzPerBin, 250, 2500);
        high = band(fft, hzPerBin, 4000, 10000);
        return true;
    }

    /** Mean magnitude of the bins between lo and hi Hz; fft is Visualizer's packed format (re, im pairs from bin 1). */
    static float band(byte[] fft, float hzPerBin, float lo, float hi) {
        int n = fft.length / 2;
        int from = Math.max(1, (int) (lo / hzPerBin)), to = Math.min(n - 1, Math.max(from, (int) (hi / hzPerBin)));
        float sum = 0;
        for (int k = from; k <= to; k++) {
            float re = fft[2 * k], im = fft[2 * k + 1];
            sum += (float) Math.sqrt(re * re + im * im);
        }
        return sum / (to - from + 1);
    }

    void close() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        visualizer = null;
    }
}

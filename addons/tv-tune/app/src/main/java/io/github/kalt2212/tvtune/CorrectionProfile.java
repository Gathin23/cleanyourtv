package io.github.kalt2212.tvtune;

/** Experimental balancing of processed RGB codes, never a display calibration. */
public final class CorrectionProfile {
    private final double[] observed;
    private final double[] gains;

    private CorrectionProfile(double[] observed, double[] gains) {
        this.observed = observed; this.gains = gains;
    }

    public static CorrectionProfile fromNeutral(double red, double green, double blue) {
        double[] observed = {red, green, blue};
        for (double value : observed) {
            if (!Double.isFinite(value) || value < 16 || value > 239)
                throw new IllegalArgumentException("A neutral sample must have finite RGB codes between 16 and 239.");
        }
        double average = (red + green + blue) / 3.0;
        double[] gains = new double[3];
        for (int channel = 0; channel < 3; channel++) {
            gains[channel] = average / observed[channel];
            if (gains[channel] < 0.75 || gains[channel] > 4.0 / 3.0)
                throw new IllegalArgumentException("The sample requires gains outside this preview's bounded range.");
        }
        return new CorrectionProfile(observed, gains);
    }

    public double[] observed() { return observed.clone(); }
    public double[] gains() { return gains.clone(); }

    public int apply(int argb) {
        int result = argb & 0xff000000;
        for (int channel = 0; channel < 3; channel++) {
            int shift = 16 - channel * 8;
            int code = (argb >> shift) & 255;
            int corrected = (int) Math.max(0, Math.min(255, Math.round(code * gains[channel])));
            result |= corrected << shift;
        }
        return result;
    }
}

package androidx.dynamicanimation.animation;

/**
 * Test helper in the library's own package, for the package-private stepping SpringAnimation does on each frame:
 * counts the frames a spring needs to come to rest after a move, with the threshold SpringAnimation derives from
 * setMinimumVisibleChange.
 */
public final class SpringFrames {

    private SpringFrames() {
    }

    public static int toSettle(float stiffness, float dampingRatio, float minimumVisibleChange, float distancePx) {
        SpringForce spring = new SpringForce(0f).setStiffness(stiffness).setDampingRatio(dampingRatio);
        spring.setValueThreshold(minimumVisibleChange * 0.75f);   // DynamicAnimation.THRESHOLD_MULTIPLIER
        double value = -distancePx, velocity = 0;
        for (int frame = 1; frame <= 120; frame++) {
            DynamicAnimation.MassState s = spring.updateValues(value, velocity, frame % 3 == 0 ? 16 : 17);   // 60 Hz
            value = s.mValue;
            velocity = s.mVelocity;
            if (spring.isAtEquilibrium((float) value, (float) velocity)) return frame;
        }
        return Integer.MAX_VALUE;
    }
}

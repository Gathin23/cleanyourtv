package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Shared motion constants and helpers used across the launcher.
 *
 * <h3>Reduced motion</h3>
 * {@code config.xml reduced_motion}: the one spring and the one time limit every animation uses then.
 *
 * <h3>Apple-quality spring presets</h3>
 * Three named spring personalities that match the feel of Apple TV OS focus motion:
 * <ul>
 *   <li>{@link #applyGentle} — smooth glide for large moves (glass highlight cross-screen)</li>
 *   <li>{@link #applySnappy} — fast settle for short hops (adjacent-tile movement)</li>
 *   <li>{@link #applyBouncy} — gentle overshoot for the tile scale animation when
 *       {@code focus_overshoot} is true</li>
 * </ul>
 *
 * <h3>Focus anticipation</h3>
 * {@link #ANTICIPATION_SQUASH} is the scale factor a tile briefly shrinks to before springing up to
 * full focus size.  This micro-animation (50 ms, decelerate) gives the motion a physical "coiling"
 * feel.  It is only applied when {@code focus_anticipation} is enabled in the theme.
 */
final class Motion {

    /** The longest any reduced-motion animation runs, in milliseconds. */
    static final int REDUCED_MS = 90;
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    static final float REDUCED_STIFFNESS = 15000f;
    /** The spring stops once it is within this many pixels of its target. */
    static final float REDUCED_MIN_CHANGE = 2f;

    // ---- Apple-quality spring personalities ----

    /**
     * Gentle: smooth, non-bouncy settle for large moves.  Matches Apple TV's glass-highlight
     * glide across a full row.
     */
    static final float GENTLE_STIFFNESS     = 300f;
    static final float GENTLE_DAMPING       = SpringForce.DAMPING_RATIO_NO_BOUNCY;

    /**
     * Snappy: quick settle for short moves (one tile hop).  Stiffer spring so it lands fast
     * without feeling sluggish.
     */
    static final float SNAPPY_STIFFNESS     = 800f;
    static final float SNAPPY_DAMPING       = SpringForce.DAMPING_RATIO_LOW_BOUNCY;

    /**
     * Bouncy: tile scale-up animation when {@code focus_overshoot} is on.  Medium stiffness with
     * a touch of bounce — the tile springs slightly past 1.10× then settles.
     */
    static final float BOUNCY_STIFFNESS     = 450f;
    static final float BOUNCY_DAMPING       = 0.65f;

    // ---- Focus anticipation ----

    /**
     * Scale a tile briefly shrinks to before expanding on focus (focus_anticipation).
     * 50 ms decelerate squash → then the normal focus-scale spring.
     */
    static final float ANTICIPATION_SQUASH  = 0.96f;
    /** Duration of the anticipation squash phase, in milliseconds. */
    static final int   ANTICIPATION_MS      = 50;
    /** Duration of the focus-scale expand phase after anticipation, in milliseconds. */
    static final int   ANTICIPATION_EXPAND_MS = 220;

    // ---- Durations (non-spring ViewPropertyAnimator paths) ----

    /** Standard focus scale-up duration when using ViewPropertyAnimator (non-reduced, no overshoot). */
    static final int FOCUS_IN_MS    = 240;
    /** Focus scale-down duration (unfocus). */
    static final int FOCUS_OUT_MS   = 170;
    /** Name label slide-in/fade-in. */
    static final int NAME_IN_MS     = 220;

    private Motion() {
    }

    /** Apply reduced-motion spring: critically damped, settles in ≤ 5 frames. */
    static void reduce(SpringAnimation spring) {
        spring.setSpring(new SpringForce()
                .setStiffness(REDUCED_STIFFNESS)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE);
    }

    /**
     * Apply the Gentle preset: smooth, non-bouncy glide.  Best for the glass highlight moving a
     * long distance.
     */
    static void applyGentle(SpringAnimation spring) {
        spring.setSpring(new SpringForce()
                .setStiffness(GENTLE_STIFFNESS)
                .setDampingRatio(GENTLE_DAMPING));
        spring.setMinimumVisibleChange(1f);
    }

    /**
     * Apply the Snappy preset: fast settle for short hops.
     */
    static void applySnappy(SpringAnimation spring) {
        spring.setSpring(new SpringForce()
                .setStiffness(SNAPPY_STIFFNESS)
                .setDampingRatio(SNAPPY_DAMPING));
        spring.setMinimumVisibleChange(1f);
    }

    /**
     * Apply the Bouncy preset: gentle overshoot for the tile scale spring.
     */
    static void applyBouncy(SpringAnimation spring) {
        spring.setSpring(new SpringForce()
                .setStiffness(BOUNCY_STIFFNESS)
                .setDampingRatio(BOUNCY_DAMPING));
        spring.setMinimumVisibleChange(0.5f);
    }

    /**
     * Choose between Gentle and Snappy based on how far the focus is moving, and apply that preset.
     * Short distance (≤ 2 tiles wide) → Snappy; longer → Gentle.
     *
     * @param spring   the spring to configure
     * @param deltaPx  absolute pixel distance the highlight is travelling
     * @param tilePx   width of one tile in pixels
     */
    static void applyAdaptive(SpringAnimation spring, float deltaPx, float tilePx) {
        if (tilePx > 0 && deltaPx / tilePx <= 2.5f) {
            applySnappy(spring);
        } else {
            applyGentle(spring);
        }
    }
}

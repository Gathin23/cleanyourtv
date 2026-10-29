package com.gathin.tvhome;

import android.graphics.drawable.Animatable;

/**
 * The network pill's icon while a festive skin is on: a light that follows the connection. The diya's flame and
 * the jack-o'-lantern's candle both take the same calls from the Wi-Fi label code.
 */
interface FestiveLamp extends Animatable {

    /** 0 puts the light out (offline); 1 to 4 is the signal strength. */
    void setFlame(int level);

    /** A steady light (Ethernet) does not flicker. */
    void setSteady(boolean steady);

    /** With reduced motion the light is still and changes are instant. */
    void setReducedMotion(boolean reducedMotion);
}

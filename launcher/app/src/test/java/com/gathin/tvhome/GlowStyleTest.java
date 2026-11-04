package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** glow_style from config.xml or a theme: horizon by name, radial for anything else. */
public class GlowStyleTest {

    @Test
    public void horizonByNameAnyCase() {
        assertEquals(Config.GLOW_HORIZON, Config.glowStyle("horizon"));
        assertEquals(Config.GLOW_HORIZON, Config.glowStyle(" Horizon "));
    }

    @Test
    public void anythingElseIsRadial() {
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle("radial"));
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle(""));
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle(null));
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle("aurora"));
    }
}

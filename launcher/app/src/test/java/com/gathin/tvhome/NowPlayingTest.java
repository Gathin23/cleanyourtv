package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NowPlayingTest {
    @Test
    public void titleOverArtistAndApp() {
        assertEquals("Keep The Faith\nBon Jovi  \u00b7  Spotify", NowPlaying.lines("Keep The Faith", "Bon Jovi", "Spotify"));
    }

    @Test
    public void titleOverAppWithoutAnArtist() {
        assertEquals("VTM Nieuws\nVTM GO", NowPlaying.lines(" VTM Nieuws ", " ", "VTM GO"));
    }

    @Test
    public void theAppAloneWithoutATitle() {
        assertEquals("SmartTube", NowPlaying.lines(null, "x", "SmartTube"));
        assertEquals("SmartTube", NowPlaying.lines("  ", null, "SmartTube"));
    }
}

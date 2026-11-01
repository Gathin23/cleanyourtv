package io.github.shaharukhs.tvinspector;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.view.Display;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class LabelsTest {
    @Test public void refreshRatesDropTrailingZeros() {
        assertEquals("60", Labels.refresh(60f));
        assertEquals("59.94", Labels.refresh(59.94f));
        assertEquals("23.98", Labels.refresh(23.976f));
        assertEquals("100", Labels.refresh(100f));
    }
    @Test public void modesGroupByResolutionLargestAndFastestFirst() {
        int[] w = {1920, 3840, 3840, 1920, 3840};
        int[] h = {1080, 2160, 2160, 1080, 2160};
        float[] hz = {60f, 24f, 60f, 50f, 60.0001f};
        assertEquals(Arrays.asList("3840 × 2160: 60, 24 Hz", "1920 × 1080: 60, 50 Hz"), Labels.modes(w, h, hz));
        assertTrue(Labels.modes(new int[0], new int[0], new float[0]).isEmpty());
    }
    @Test public void decoderLinesPreferHardware() {
        assertEquals("HEVC: hardware, up to 4K", Labels.decoder("HEVC", 1, 0));
        assertEquals("AV1: software only, up to 1080p", Labels.decoder("AV1", Labels.MISSING, 2));
        assertEquals("VP9: hardware, below 720p", Labels.decoder("VP9", Labels.SIZES.length, Labels.MISSING));
        assertEquals("MPEG-2: no decoder", Labels.decoder("MPEG-2", Labels.MISSING, Labels.MISSING));
    }
    @Test public void betterKeepsTheLargestSizeAndIgnoresMissing() {
        assertEquals(1, Labels.better(Labels.MISSING, 1));
        assertEquals(1, Labels.better(1, Labels.MISSING));
        assertEquals(0, Labels.better(2, 0));
        assertEquals(Labels.MISSING, Labels.better(Labels.MISSING, Labels.MISSING));
    }
    @Test public void androidSoftwareDecodersAreRecognised() {
        assertTrue(Labels.softwareCodec("c2.android.hevc.decoder"));
        assertTrue(Labels.softwareCodec("OMX.google.vp9.decoder"));
        assertFalse(Labels.softwareCodec("OMX.amlogic.hevc.decoder.awesome2"));
    }
    @Test public void constantsGetNames() {
        assertEquals("HDR10+", Labels.hdr(Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS));
        assertEquals("HDR type 99", Labels.hdr(99));
        assertEquals("Dolby Atmos (DD+ JOC)", Labels.encoding(AudioFormat.ENCODING_E_AC3_JOC));
        assertEquals("encoding 999", Labels.encoding(999));
        assertEquals("HDMI ARC", Labels.output(AudioDeviceInfo.TYPE_HDMI_ARC));
        assertEquals("Headphone jack", Labels.output(AudioDeviceInfo.TYPE_WIRED_HEADSET));
    }
    @Test public void keyLinesNameTheDeviceOrSayItIsVirtual() {
        assertEquals("KEYCODE_G (35), scan code 34, from Realtek Remote", Labels.key("KEYCODE_G", 35, 34, "Realtek Remote"));
        assertEquals("KEYCODE_DPAD_UP (19), scan code 0, virtual device: injected or sent by the system",
                Labels.key("KEYCODE_DPAD_UP", 19, 0, null));
    }
}

package io.github.nickld.frigateglance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LiveVideoTest {
    @Test
    public void rtspUrlWithoutCredentials() {
        assertEquals("rtsp://192.168.1.11:8554/porch_1", LiveVideo.rtspUrl("192.168.1.11", "", "", "porch_1"));
    }

    @Test
    public void rtspUrlEncodesCredentials() {
        assertEquals("rtsp://us%40r:p%3Ass@h:8554/s", LiveVideo.rtspUrl("h", "us@r", "p:ss", "s"));
    }

    @Test
    public void streamNameIsEncoded() {
        assertEquals("rtsp://h:8554/front%20door", LiveVideo.rtspUrl("h", "", "", "front door"));
    }
}

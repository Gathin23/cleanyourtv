package com.lionlev.tv.fps;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameStatsTest {
    private final String activity = "example.app/example.app.Player";
    private String video() { return "SurfaceView[" + activity + "](BLAST)#0"; }
    private String record(String name, long frames) { return "layerName = " + name + "\ntotalFrames = " + frames + "\n"; }
    @Test public void videoWinsOverUiAndGlobalCounters() {
        FrameStats.Reading result = FrameStats.select(activity, video()+"\n"+activity+"#0",
                "totalFrames = 90000\n"+record(video(),100)+record(activity+"#0",500));
        assertEquals(video(),result.layer); assertEquals(100,result.frames);
    }
    @Test public void removedLayerAndUnmeasurableVideoAreHidden() {
        assertNull(FrameStats.select(activity,"",record(video(),100)));
        assertNull(FrameStats.select(activity,video()+"\n"+activity+"#0",record(activity+"#0",500)));
    }
    @Test public void ambiguousLayersAreHidden() {
        assertNull(FrameStats.select(activity,video(),record(video(),100)+record(video(),200)));
    }
    @Test public void prefixedUiWindowIsSupported() {
        assertEquals(42,FrameStats.select(activity,"abc123 "+activity+"#0",record(activity+"#0",42)).frames);
    }
    @Test public void elapsedTimeIdleResetAndLayerChange() {
        FrameStats rates = new FrameStats();
        assertEquals(-1,rates.sample(new FrameStats.Reading("a",100),1_000_000_000L),0);
        assertEquals(30,rates.sample(new FrameStats.Reading("a",160),3_000_000_000L),0);
        assertEquals(0,rates.sample(new FrameStats.Reading("a",160),4_000_000_000L),0);
        assertEquals(-1,rates.sample(new FrameStats.Reading("a",1),5_000_000_000L),0);
        assertEquals(-1,rates.sample(new FrameStats.Reading("b",100),6_000_000_000L),0);
        assertEquals(-1,rates.sample(null,7_000_000_000L),0);
        assertEquals(-1,rates.sample(new FrameStats.Reading("b",130),8_000_000_000L),0);
    }
    @Test public void invalidClockAndImplausibleRatesAreHidden() {
        FrameStats rates = new FrameStats();
        rates.sample(new FrameStats.Reading("a",1),1);
        assertEquals(-1,rates.sample(new FrameStats.Reading("a",2),1),0);
        assertEquals(-1,rates.sample(new FrameStats.Reading("a",10000),2),0);
    }
}

package io.github.pbanddev.tatami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ScenesTest {
    private final List<Throwable> reported = new ArrayList<>();
    private int nagayaTries;

    private Scenes failing(Throwable failure) {
        return new Scenes(look -> {
            if (look == Look.YOJOHAN) return new Scene(look);
            nagayaTries++;
            if (failure instanceof Error) throw (Error) failure;
            throw (RuntimeException) failure;
        });
    }

    @Test
    public void eachLookBuildsAsItself() {
        Scenes scenes = new Scenes(Scene::new);
        assertSame(Look.YOJOHAN, scenes.make(Look.YOJOHAN, reported::add).look);
        assertSame(Look.NAGAYA, scenes.make(Look.NAGAYA, reported::add).look);
        assertTrue(reported.isEmpty());
        assertTrue(scenes.usable(Look.NAGAYA));
    }

    @Test
    public void aFailedBuildGivesYojohanAndIsNotTriedAgain() {
        IllegalStateException boom = new IllegalStateException("boom");
        Scenes scenes = failing(boom);
        assertSame(Look.YOJOHAN, scenes.make(Look.NAGAYA, reported::add).look);
        assertSame(Look.YOJOHAN, scenes.make(Look.NAGAYA, reported::add).look);
        assertEquals(1, nagayaTries);
        assertEquals(List.of(boom), reported);
        assertFalse(scenes.usable(Look.NAGAYA));
        assertTrue(scenes.usable(Look.YOJOHAN));
    }

    @Test
    public void errorsAreCaughtToo() {
        Scenes scenes = failing(new OutOfMemoryError("test"));
        assertSame(Look.YOJOHAN, scenes.make(Look.NAGAYA, reported::add).look);
        assertSame(Look.YOJOHAN, failing(new StackOverflowError()).make(Look.NAGAYA, reported::add).look);
        assertEquals(2, reported.size());
    }

    @Test
    public void aLookThatFailsToDrawIsNotUsedAgain() {
        Scenes scenes = new Scenes(Scene::new);
        RuntimeException boom = new RuntimeException("draw");
        scenes.failed(Look.NAGAYA, boom, reported::add);
        assertFalse(scenes.usable(Look.NAGAYA));
        assertSame(Look.YOJOHAN, scenes.make(Look.NAGAYA, reported::add).look);
        assertEquals(List.of(boom), reported);
    }
}

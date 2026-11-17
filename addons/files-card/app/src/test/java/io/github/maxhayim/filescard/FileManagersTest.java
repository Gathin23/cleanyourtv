package io.github.maxhayim.filescard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Test;

public class FileManagersTest {
    @Test
    public void amazeWinsWhenInstalled() {
        assertEquals("com.amaze.filemanager", FileManagers.pick(new HashSet<>(Arrays.asList(
                "com.google.android.apps.nbu.files", "com.amaze.filemanager", "com.lonelycatgames.Xplore"))));
    }

    @Test
    public void otherwiseTheFirstKnownOne() {
        assertEquals("com.lonelycatgames.Xplore", FileManagers.pick(new HashSet<>(Arrays.asList(
                "com.google.android.apps.nbu.files", "com.lonelycatgames.Xplore"))));
    }

    @Test
    public void unknownAppsAreIgnored() {
        assertNull(FileManagers.pick(new HashSet<>(Arrays.asList("com.example.notes", "com.estrongs.android.pop"))));
    }

    @Test
    public void noneInstalled() {
        assertNull(FileManagers.pick(Collections.emptySet()));
    }

    @Test
    public void amazeComesFirst() {
        assertEquals("com.amaze.filemanager", FileManagers.KNOWN.get(0));
    }
}

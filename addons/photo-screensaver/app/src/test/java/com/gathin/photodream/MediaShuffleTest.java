package com.gathin.photodream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The shuffle option's order. */
public class MediaShuffleTest {

    @Test public void shuffledKeepsEveryFileAndNeverOpensWithTheLastOneShown() {
        List<File> files = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"), new File("d.jpg"));
        for (int seed = 0; seed < 200; seed++) {
            List<File> round = Media.shuffled(files, "c.jpg", new Random(seed));
            assertEquals(4, round.size());
            assertTrue(round.containsAll(files));
            assertNotEquals("c.jpg", round.get(0).getName());
        }
        assertEquals(1, Media.shuffled(Collections.singletonList(new File("a.jpg")), "a.jpg", new Random(1)).size());
        assertTrue(Media.shuffled(Collections.<File>emptyList(), "", new Random(1)).isEmpty());
    }

    @Test public void shuffledOrdersDifferFromRoundToRound() {
        List<File> files = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"), new File("d.jpg"),
                new File("e.jpg"), new File("f.jpg"));
        Random random = new Random(3);
        List<File> first = Media.shuffled(files, "", random);
        boolean differs = false;
        for (int i = 0; i < 5 && !differs; i++) differs = !first.equals(Media.shuffled(files, "", random));
        assertTrue(differs);
    }
}

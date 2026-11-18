package com.gathin.photodream;

import static org.junit.Assert.assertEquals;

import android.content.SharedPreferences;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class OptionsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void untouchedSettingsFollowSyncUpdates() throws Exception {
        File dir = tmp.newFolder();
        SharedPreferences preferences = preferences(null);
        writeSeconds(dir, "30");
        assertEquals(30, new Options(preferences, dir).secondsPerPicture);
        writeSeconds(dir, "45");
        assertEquals(45, new Options(preferences, dir).secondsPerPicture);
    }

    @Test public void savedSettingOverridesSyncEvenWhenItIsTwenty() throws Exception {
        File dir = tmp.newFolder();
        writeSeconds(dir, "30");
        assertEquals(20, new Options(preferences(20), dir).secondsPerPicture);
        writeSeconds(dir, "45");
        assertEquals(20, new Options(preferences(20), dir).secondsPerPicture);
        assertEquals(60, new Options(preferences(60), dir).secondsPerPicture);
    }

    @Test public void missingOrInvalidSyncValueDefaultsToTwenty() throws Exception {
        SharedPreferences preferences = preferences(null);
        assertEquals(20, new Options(preferences, null).secondsPerPicture);
        File dir = tmp.newFolder();
        assertEquals(20, new Options(preferences, dir).secondsPerPicture);
        for (String value : new String[]{"", "abc", "4", "3601"}) {
            writeSeconds(dir, value);
            assertEquals(20, new Options(preferences, dir).secondsPerPicture);
        }
        for (int seconds : new int[]{5, 3600}) {
            writeSeconds(dir, String.valueOf(seconds));
            assertEquals(seconds, new Options(preferences, dir).secondsPerPicture);
        }
    }

    private static void writeSeconds(File dir, String value) throws Exception {
        Files.write(new File(dir, Media.SECONDS).toPath(), value.getBytes(StandardCharsets.US_ASCII));
    }

    private static SharedPreferences preferences(Integer seconds) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBoolean")) return args[1];
                    if (method.getName().equals("getInt")) {
                        return Options.SECONDS_PER_PICTURE.equals(args[0]) && seconds != null
                                ? seconds : args[1];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}

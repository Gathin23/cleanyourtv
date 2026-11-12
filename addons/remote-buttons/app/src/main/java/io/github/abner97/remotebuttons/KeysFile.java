package io.github.abner97.remotebuttons;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** The owner's button map in keys.txt, or the built-in Hisense map when there is none. */
final class KeysFile {
    private static final String TAG = "RemoteButtons";
    private static final String FILE_NAME = "keys.txt";

    private final File keysFile;
    private Map<Integer, String> cachedTargetsByKeyCode;
    private long cachedLastModified = -1;

    KeysFile(Context context) {
        keysFile = new File(context.getExternalFilesDir(null), FILE_NAME);
    }

    boolean exists() {
        return keysFile.isFile();
    }

    /** keycode → target. The file is read again only when it changes, so a new one needs no restart. */
    Map<Integer, String> targetsByKeyCode() {
        long lastModified = keysFile.lastModified(); // 0 when there is no file
        if (cachedTargetsByKeyCode == null || lastModified != cachedLastModified) {
            cachedTargetsByKeyCode = readButtonMap();
            cachedLastModified = lastModified;
        }
        return cachedTargetsByKeyCode;
    }

    private Map<Integer, String> readButtonMap() {
        if (exists()) {
            try {
                byte[] fileBytes = Files.readAllBytes(keysFile.toPath());
                return KeyMap.parseButtonMap(new String(fileBytes, StandardCharsets.UTF_8));
            } catch (IOException readError) {
                Log.w(TAG, "could not read " + keysFile + ", using the built-in map", readError);
            }
        }
        return KeyMap.parseButtonMap(KeyMap.HISENSE_DEFAULT_MAP);
    }
}

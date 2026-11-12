package io.github.abner97.remotebuttons;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * The owner's button map in keys.txt, or the built-in Hisense map when there is none.
 *
 * <p>The file lives in the app's external files directory
 * ({@code /sdcard/Android/data/io.github.abner97.remotebuttons/files/keys.txt}), where the ADB shell can write it
 * without root; {@code install.sh keys.txt} puts it there. Not thread-safe: each user keeps its own instance.
 */
final class KeysFile {
    /** Logcat tag, shared with {@link KeyLogService}. */
    private static final String TAG = "RemoteButtons";
    /** The file's name in the external files directory, as the README and install.sh name it. */
    private static final String FILE_NAME = "keys.txt";

    /** Where keys.txt is, whether or not it exists yet. */
    private final File keysFile;
    /** The map last read, or null before the first read. */
    private Map<Integer, String> cachedTargetsByKeyCode;
    /** keys.txt's modification time when the cached map was read; 0 stands for "no file". */
    private long cachedLastModified = -1;

    /**
     * Points at keys.txt for this app. Nothing is read until {@link #targetsByKeyCode()} is called.
     *
     * @param context any context of this app, used to find its external files directory
     */
    KeysFile(Context context) {
        keysFile = new File(context.getExternalFilesDir(null), FILE_NAME);
    }

    /**
     * Whether the owner has put a keys.txt on the TV.
     *
     * @return true when keys.txt is a file; false means the built-in map is in use
     */
    boolean exists() {
        return keysFile.isFile();
    }

    /**
     * The button map in use. The file is read again only when its modification time changes, so pushing a new
     * keys.txt takes effect at the next button press, with no restart.
     *
     * @return keycode → target from keys.txt, or from {@link KeyMap#HISENSE_DEFAULT_MAP} without one
     */
    Map<Integer, String> targetsByKeyCode() {
        long lastModified = keysFile.lastModified(); // 0 when there is no file
        if (cachedTargetsByKeyCode == null || lastModified != cachedLastModified) {
            cachedTargetsByKeyCode = readButtonMap();
            cachedLastModified = lastModified;
        }
        return cachedTargetsByKeyCode;
    }

    /**
     * Reads keys.txt now. A file that cannot be read is logged and treated as missing, so the remote keeps its
     * built-in buttons rather than losing all of them.
     *
     * @return keycode → target from the file, or the built-in map when there is no readable file
     */
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

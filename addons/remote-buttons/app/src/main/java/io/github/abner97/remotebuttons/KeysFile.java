package io.github.abner97.remotebuttons;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
     * Makes a button open a target, keeping every other button as it is. Starts from the built-in map when there
     * is no keys.txt yet, so assigning one button does not drop the others.
     *
     * @param keyCode the button, as the window manager logged it
     * @param target a package name, {@link KeyMap#SETTINGS_TARGET} or {@link KeyMap#HOME_TARGET}
     * @throws IOException when keys.txt cannot be written
     */
    void assign(int keyCode, String target) throws IOException {
        write(KeyMap.withButton(targetsByKeyCode(), keyCode, target));
    }

    /**
     * Makes a button do nothing again, keeping every other button as it is.
     *
     * @param keyCode the button to forget
     * @throws IOException when keys.txt cannot be written
     */
    void remove(int keyCode) throws IOException {
        write(KeyMap.withoutButton(targetsByKeyCode(), keyCode));
    }

    /**
     * Replaces the whole map, e.g. to put back the map from before an assignment that had to be undone.
     *
     * @param targetsByKeyCode the complete map to keep from now on
     * @throws IOException when keys.txt cannot be written
     */
    void replaceWith(Map<Integer, String> targetsByKeyCode) throws IOException {
        write(targetsByKeyCode);
    }

    /**
     * Deletes keys.txt, so the built-in Hisense map applies again.
     *
     * @throws IOException when the file exists but cannot be deleted
     */
    void resetToDefaults() throws IOException {
        Files.deleteIfExists(keysFile.toPath());
        cachedTargetsByKeyCode = null;
    }

    /**
     * Replaces keys.txt with this map. It is written to a temporary file first and then renamed over keys.txt, so
     * {@link KeyLogService}, which may read the file at any moment, never sees half of it.
     *
     * @param targetsByKeyCode the complete new map
     * @throws IOException when the temporary file cannot be written or renamed
     */
    private void write(Map<Integer, String> targetsByKeyCode) throws IOException {
        File temporaryFile = new File(keysFile.getParentFile(), FILE_NAME + ".tmp");
        byte[] fileBytes = KeyMap.formatButtonMap(targetsByKeyCode).getBytes(StandardCharsets.UTF_8);
        Files.write(temporaryFile.toPath(), fileBytes);
        Files.move(temporaryFile.toPath(), keysFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        cachedTargetsByKeyCode = null;
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

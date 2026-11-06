package io.github.vitorcamillo.wolfgames;

import android.app.Activity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * One-time provisioning of the optional Wolf session controller. The owner pushes
 * {@code wolf-control.json} to the add-on's external files directory with adb; on the first open the
 * file is validated, copied into private storage and the external copy is deleted, so the bearer
 * token never stays on shared storage. An invalid file is discarded, not copied.
 */
final class Provisioning {
    private static final int MAX_BYTES = 16384;

    static boolean accept(Activity activity) {
        File external = activity.getExternalFilesDir(null);
        if (external == null) return false;
        File incoming = new File(external, "wolf-control.json");
        if (!incoming.isFile()) return false;
        File target = new File(activity.getFilesDir(), "wolf-control.json");
        try {
            if (incoming.length() > MAX_BYTES) throw new IOException("Config too large");
            WolfControlClient.load(incoming); // Validates everything before anything is copied.
            copyAtomic(incoming, target);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            // The token must not stay readable in the app's external directory.
            if (!incoming.delete()) incoming.deleteOnExit();
        }
    }

    private static void copyAtomic(File source, File target) throws IOException {
        File temp = File.createTempFile(".wolf-control-", ".tmp", target.getParentFile());
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.flush();
            if (!temp.renameTo(target)) throw new IOException("Cannot replace config");
        } finally {
            temp.delete();
        }
    }
}

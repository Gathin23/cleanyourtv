package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;

/** null confirms absence/invalid size; IOException means the theme must be retried, not reset. */
final class ThemeRead {
    private ThemeRead() { }

    static String read(File file) throws IOException {
        try {
            File parent = file == null ? null : file.getParentFile();
            if (parent == null || !parent.isDirectory() || !parent.canRead() || !parent.canExecute()) throw new IOException();
            if (!file.isFile() && file.exists()) throw new IOException();
            long size = file.length();
            return read(Files.newInputStream(file.toPath()), size);
        } catch (NoSuchFileException e) {
            return null;   // accessible directory, and the filesystem confirms no file
        } catch (SecurityException e) {
            throw new IOException(e);
        }
    }

    /** Complete bounded reads, including stream closure, are required before acknowledging a theme. */
    static String read(InputStream stream, long size) throws IOException {
        try (InputStream in = stream) {
            if (size < 0 || size > 64 * 1024) return null;
            byte[] bytes = new byte[(int) size];
            int n = 0;
            while (n < bytes.length) {
                int count = in.read(bytes, n, bytes.length - n);
                if (count <= 0) throw new IOException();
                n += count;
            }
            if (in.read() != -1) throw new IOException();   // changed while being read
            String text = new String(bytes, 0, n, StandardCharsets.UTF_8);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
    }
}

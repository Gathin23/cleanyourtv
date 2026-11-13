package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

final class LocalProfile {
    private static final String PREFS = "preferences";
    private LocalProfile() {}

    static File file(Context context, int slot) {
        return new File(context.getFilesDir(), slot == 0 ? "launcher-profile.zip" : "launcher-profile-" + slot + ".zip");
    }

    private static Map<String, File> files(Context context) throws IOException {
        File theme = Config.dir(context);
        if (theme == null) throw new IOException("Theme storage unavailable");
        Map<String, File> result = new LinkedHashMap<>();
        result.put("order.txt", Order.file(context));
        for (String name : new String[]{"library_apps.txt", "library_home_removed.txt"})
            result.put(name, new File(context.getFilesDir(), name));
        for (String name : new String[]{Config.FILE_NAME, Config.WALLPAPER_NAME})
            result.put("theme/" + name, new File(theme, name));
        File collection = new File(theme, ThemeFiles.WALLPAPERS);
        File[] images = collection.listFiles((dir, name) ->
                ThemeFiles.collectionImage(name) || WallpaperCaptions.FILE_NAME.equals(name));
        if (images != null) for (File image : images)
            result.put("theme/wallpapers/" + image.getName(), image);
        return result;
    }

    static void save(Context context, SharedPreferences choices, int slot, String summary, String dock) throws IOException {
        AtomicFile profile = new AtomicFile(file(context, slot));
        FileOutputStream output = profile.startWrite();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("summary"));
            DataOutputStream metadata = new DataOutputStream(zip);
            metadata.writeUTF(summary);
            metadata.writeUTF(dock);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(PREFS));
            DataOutputStream data = new DataOutputStream(zip);
            Map<String, ?> values = choices.getAll();
            data.writeInt(values.size());
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                data.writeUTF(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof String) { data.writeByte(0); data.writeUTF((String) value); }
                else if (value instanceof Integer) { data.writeByte(1); data.writeInt((Integer) value); }
                else if (value instanceof Boolean) { data.writeByte(2); data.writeBoolean((Boolean) value); }
                else if (value instanceof Long) { data.writeByte(3); data.writeLong((Long) value); }
                else throw new IOException("Unsupported preference");
            }
            zip.closeEntry();
            for (Map.Entry<String, File> entry : files(context).entrySet()) {
                if (!entry.getValue().isFile()) continue;
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                Files.copy(entry.getValue().toPath(), zip);
                zip.closeEntry();
            }
            zip.finish();
            profile.finishWrite(output);
        } catch (IOException | RuntimeException failure) {
            profile.failWrite(output);
            throw failure;
        }
    }

    static String[] summary(Context context, int slot) {
        try (ZipFile zip = new ZipFile(file(context, slot))) {
            ZipEntry entry = zip.getEntry("summary");
            if (entry != null) {
                try (DataInputStream data = new DataInputStream(zip.getInputStream(entry))) {
                    return new String[]{data.readUTF(), data.readUTF()};
                }
            }
        } catch (IOException unavailable) {
            return new String[]{"", ""};
        }
        return new String[]{"", ""};
    }

    static void restore(Context context, SharedPreferences choices, int slot) throws IOException {
        Map<String, File> targets = files(context);
        File theme = Config.dir(context);
        try (ZipFile zip = new ZipFile(file(context, slot))) {
            ZipEntry prefs = zip.getEntry(PREFS);
            if (prefs == null) throw new IOException("Missing preferences");
            SharedPreferences.Editor editor = choices.edit().clear();
            try (DataInputStream data = new DataInputStream(zip.getInputStream(prefs))) {
                int count = data.readInt();
                if (count < 0 || count > 1000) throw new IOException("Invalid preferences");
                for (int index = 0; index < count; index++) {
                    String key = data.readUTF();
                    switch (data.readByte()) {
                        case 0: editor.putString(key, data.readUTF()); break;
                        case 1: editor.putInt(key, data.readInt()); break;
                        case 2: editor.putBoolean(key, data.readBoolean()); break;
                        case 3: editor.putLong(key, data.readLong()); break;
                        default: throw new IOException("Invalid preference");
                    }
                }
            }
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (name.startsWith("theme/wallpapers/")) {
                    String image = name.substring("theme/wallpapers/".length());
                    if (ThemeFiles.collectionImage(image) || WallpaperCaptions.FILE_NAME.equals(image))
                        targets.put(name, new File(theme, "wallpapers/" + image));
                }
            }
            for (Map.Entry<String, File> target : targets.entrySet()) {
                ZipEntry entry = zip.getEntry(target.getKey());
                File destination = target.getValue();
                if (entry == null) {
                    Files.deleteIfExists(destination.toPath());
                    continue;
                }
                Files.createDirectories(destination.getParentFile().toPath());
                AtomicFile output = new AtomicFile(destination);
                try (InputStream input = zip.getInputStream(entry)) {
                    FileOutputStream stream = output.startWrite();
                    try {
                        byte[] buffer = new byte[8192];
                        int size;
                        while ((size = input.read(buffer)) != -1) stream.write(buffer, 0, size);
                        output.finishWrite(stream);
                    } catch (IOException failure) {
                        output.failWrite(stream);
                        throw failure;
                    }
                }
            }
            Config.markRestoredTheme(editor, theme);
            if (!editor.commit()) throw new IOException("Could not restore preferences");
        }
    }
}

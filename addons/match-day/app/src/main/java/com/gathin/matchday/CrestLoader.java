package com.gathin.matchday;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.RectF;

import com.caverock.androidsvg.SVG;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

final class CrestLoader {
    private static final int SIZE = 128;
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private final Context context;
    private final ConcurrentHashMap<Integer, Bitmap> memory = new ConcurrentHashMap<>();

    CrestLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    Bitmap load(Match.Team team) throws IOException {
        if (team.id < 0 || team.crestUrl == null || team.crestUrl.isEmpty()) return null;
        Bitmap inMemory = memory.get(team.id);
        if (inMemory != null) return inMemory;

        File cache = cacheFile(team.id);
        if (cache.isFile()) {
            Bitmap fromDisk = decode(readFile(cache), null);
            if (fromDisk != null) {
                memory.put(team.id, fromDisk);
                return fromDisk;
            }
            cache.delete();
        }

        if (!team.crestUrl.startsWith("https://")) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(team.crestUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "image/svg+xml,image/png,image/*;q=0.8");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            String type = connection.getContentType();
            byte[] bytes = readLimited(connection.getInputStream());
            Bitmap decoded = decode(bytes, type);
            if (decoded == null) return null;
            writeFile(cache, bytes);
            memory.put(team.id, decoded);
            return decoded;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private File cacheFile(int teamId) {
        File directory = new File(context.getFilesDir(), "team-crests");
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, teamId + ".img");
    }

    private static Bitmap decode(byte[] bytes, String contentType) {
        if (isSvg(bytes, contentType)) {
            try {
                SVG svg = SVG.getFromInputStream(new ByteArrayInputStream(bytes));
                Picture picture = svg.renderToPicture(SIZE, SIZE);
                Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawPicture(picture, new RectF(0, 0, SIZE, SIZE));
                return bitmap;
            } catch (Exception ignored) {
                return null;
            }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = longest > 512 ? Math.max(1, longest / 256) : 1;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static boolean isSvg(byte[] bytes, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("svg")) return true;
        int length = Math.min(bytes.length, 256);
        String prefix = new String(bytes, 0, length, StandardCharsets.UTF_8).trim().toLowerCase();
        return prefix.startsWith("<svg") || (prefix.startsWith("<?xml") && prefix.contains("<svg"));
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return readLimited(input);
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) != -1) {
                if (output.size() + read > MAX_IMAGE_BYTES) throw new IOException("Crest is too large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void writeFile(File cache, byte[] bytes) {
        File temporary = new File(cache.getPath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
            if (cache.exists() && !cache.delete()) return;
            if (!temporary.renameTo(cache)) temporary.delete();
        } catch (IOException ignored) {
            temporary.delete();
        }
    }
}

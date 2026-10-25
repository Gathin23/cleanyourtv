package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/** Reads playable Continue watching items. Call from a worker, only while Home is opening. */
final class WatchNextReader {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final int MAX_ART_BYTES = 1024 * 1024;
    private static final LruCache<String, Bitmap> ART_CACHE = new LruCache<String, Bitmap>(4096) {
        @Override protected int sizeOf(String key, Bitmap image) { return image.getByteCount() / 1024 + 1; }
    };
    static final class Item {
        final long id, engagement;
        final String title, episode, packageName;
        final Intent intent;
        final int progress;
        final String artUri;
        Bitmap artwork;

        Item(long id, long engagement, String title, String episode, String packageName,
             Intent intent, int progress, String artUri, Bitmap artwork) {
            this.id = id;
            this.engagement = engagement;
            this.title = title;
            this.episode = episode;
            this.packageName = packageName;
            this.intent = intent;
            this.progress = progress;
            this.artUri = artUri == null ? "" : artUri;
            this.artwork = artwork;
        }

        boolean sameContent(Item other) {
            return other != null && id == other.id && title.equals(other.title)
                    && episode.equals(other.episode) && progress == other.progress
                    && packageName.equals(other.packageName)
                    && artUri.equals(other.artUri)
                    && intent.toUri(0).equals(other.intent.toUri(0));
        }
    }

    private WatchNextReader() { }

    static List<Item> items(Context context) {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED)
            return Collections.emptyList();
        List<Item> found = new ArrayList<>();
        // TvProvider rejects a SQL selection for an ordinary launcher. Read its visible rows and filter here.
        try (Cursor rows = context.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,
                null, null, null, null)) {
            if (rows == null) return found;
            while (rows.moveToNext()) {
                if (number(rows, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, -1)
                        != TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                        || number(rows, TvContract.WatchNextPrograms.COLUMN_BROWSABLE, 1) == 0) continue;
                String candidateTitle = value(rows, TvContract.WatchNextPrograms.COLUMN_TITLE);
                String candidatePkg = value(rows, TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME);
                String candidateIntent = value(rows, TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                long when = number(rows, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, 0);
                if (candidateTitle == null || candidateTitle.trim().isEmpty() || candidatePkg == null
                        || candidateIntent == null) continue;
                Intent parsed = parseIntent(candidateIntent, candidatePkg);
                if (parsed == null) continue;
                long id = number(rows, BaseColumns._ID, -1);
                if (id < 0) continue;
                String episode = value(rows, TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE);
                String artUri = value(rows, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
                long duration = number(rows, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, -1);
                long position = number(rows, TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, -1);
                int progress = duration > 0 && position >= 0
                        ? (int) Math.min(100, position * 100.0 / duration) : -1;
                found.add(new Item(id, when, candidateTitle.trim(), episode == null ? "" : episode,
                        candidatePkg, parsed, progress, artUri, localArtwork(context, artUri)));
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return Collections.emptyList();
        }
        found.sort(Comparator.comparingLong((Item item) -> item.engagement).reversed()
                .thenComparingLong(item -> item.id));
        return found;
    }

    private static Intent parseIntent(String raw, String owner) {
        try {
            Intent intent = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME);
            if (intent.getComponent() != null && !owner.equals(intent.getComponent().getPackageName())) return null;
            if (intent.getPackage() != null && !owner.equals(intent.getPackage())) return null;
            if (intent.getPackage() == null && intent.getComponent() == null) intent.setPackage(owner);
            return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } catch (URISyntaxException | IllegalArgumentException error) {
            return null;
        }
    }

    private static String value(Cursor rows, String column) {
        int at = rows.getColumnIndex(column);
        return at < 0 || rows.isNull(at) ? null : rows.getString(at);
    }

    private static long number(Cursor rows, String column, long otherwise) {
        int at = rows.getColumnIndex(column);
        return at < 0 || rows.isNull(at) ? otherwise : rows.getLong(at);
    }

    private static Bitmap localArtwork(Context context, String raw) {
        if (raw == null) return null;
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme();
        if (!"content".equals(scheme) && !"android.resource".equals(scheme)) return null;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > 192 || bounds.outHeight / sample > 108) sample *= 2;
            try (InputStream image = context.getContentResolver().openInputStream(uri)) {
                if (image == null) return null;
                BitmapFactory.Options scaled = new BitmapFactory.Options();
                scaled.inSampleSize = sample;
                scaled.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeStream(image, null, scaled);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Fetch only HTTPS poster URLs published by Watch Next, from a worker thread. */
    static Bitmap remoteArtwork(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        Bitmap cached = ART_CACHE.get(raw);
        if (cached != null) return cached;
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(raw);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()
                    || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)) return null;
            connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(3000);
            if (connection.getResponseCode() != HttpsURLConnection.HTTP_OK
                    || connection.getContentLength() > MAX_ART_BYTES
                    || connection.getContentType() == null
                    || !connection.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("image/")) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    if (bytes.size() + count > MAX_ART_BYTES) return null;
                    bytes.write(chunk, 0, count);
                }
            }
            byte[] data = bytes.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > 4096 || bounds.outHeight > 4096) return null;
            int sample = 1;
            while (bounds.outWidth / sample > 192 || bounds.outHeight / sample > 108) sample *= 2;
            BitmapFactory.Options scaled = new BitmapFactory.Options();
            scaled.inSampleSize = sample;
            scaled.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, scaled);
            if (image != null) ART_CACHE.put(raw, image);
            return image;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

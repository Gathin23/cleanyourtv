package io.github.jain05vaibhav.previewrows;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.provider.BaseColumns;

import io.github.jain05vaibhav.previewrows.PreviewModels.ChannelRecord;
import io.github.jain05vaibhav.previewrows.PreviewModels.ChannelRow;
import io.github.jain05vaibhav.previewrows.PreviewModels.ProgramRecord;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads TvContract.Channels and TvContract.PreviewPrograms without selection and with explicit
 * named projections (never null). Handles SecurityException, empty tables and missing columns
 * without crashing.
 */
final class TvPreviewRepository {

    static final String PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";

    // Explicit named projections: never pass null (Philips TPM191E fails on default split_id).
    // Note: preview_video_uri is deliberately omitted; this add-on shows still cards only.
    private static final String[][] CHANNEL_PROJECTIONS = {
            {
                    BaseColumns._ID,
                    TvContract.Channels.COLUMN_DISPLAY_NAME,
                    TvContract.Channels.COLUMN_PACKAGE_NAME,
                    TvContract.Channels.COLUMN_BROWSABLE,
                    TvContract.Channels.COLUMN_TYPE,
                    TvContract.Channels.COLUMN_APP_LINK_INTENT_URI
            },
            {
                    BaseColumns._ID,
                    TvContract.Channels.COLUMN_DISPLAY_NAME,
                    TvContract.Channels.COLUMN_PACKAGE_NAME
            },
            {
                    BaseColumns._ID,
                    TvContract.Channels.COLUMN_DISPLAY_NAME
            }
    };

    private static final String[][] PROGRAM_PROJECTIONS = {
            {
                    BaseColumns._ID,
                    TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                    TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                    TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                    TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
                    TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                    TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
                    TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                    TvContract.PreviewPrograms.COLUMN_WEIGHT,
                    TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                    TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
                    TvContract.PreviewPrograms.COLUMN_SEARCHABLE
            },
            {
                    BaseColumns._ID,
                    TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                    TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                    TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.PreviewPrograms.COLUMN_INTENT_URI
            },
            {
                    BaseColumns._ID,
                    TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                    TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_INTENT_URI
            }
    };

    enum Status {
        OK,
        EMPTY,
        PERMISSION_DENIED,
        ERROR
    }

    static final class LoadResult {
        final Status status;
        final List<ChannelRow> rows;
        final int totalItems;

        LoadResult(Status status, List<ChannelRow> rows) {
            this.status = status;
            this.rows = Collections.unmodifiableList(rows);
            int count = 0;
            for (ChannelRow row : rows) {
                count += row.items.size();
            }
            this.totalItems = count;
        }
    }

    private TvPreviewRepository() {
    }

    static LoadResult load(Context context) {
        ContentResolver resolver = context.getContentResolver();
        List<ChannelRecord> channels = Collections.emptyList();
        try {
            channels = queryChannels(resolver);
        } catch (RuntimeException ignored) {
            // A Channels failure (SecurityException, missing column, etc.) must not discard PreviewPrograms.
        }

        List<ProgramRecord> programs;
        try {
            programs = queryPrograms(resolver);
        } catch (SecurityException e) {
            return new LoadResult(Status.PERMISSION_DENIED, Collections.emptyList());
        } catch (RuntimeException e) {
            return new LoadResult(Status.ERROR, Collections.emptyList());
        }

        if (programs.isEmpty()) {
            return new LoadResult(Status.EMPTY, Collections.emptyList());
        }

        Set<String> packages = new HashSet<>();
        for (ChannelRecord ch : channels) {
            if (!ch.packageName.isEmpty()) packages.add(ch.packageName);
        }
        for (ProgramRecord pr : programs) {
            if (!pr.packageName.isEmpty()) packages.add(pr.packageName);
        }
        Map<String, String> appLabels = resolveAppLabels(context.getPackageManager(), packages);

        List<ChannelRow> rows = PreviewLogic.groupAndFilter(channels, programs, appLabels);
        if (rows.isEmpty()) {
            return new LoadResult(Status.EMPTY, Collections.emptyList());
        }
        return new LoadResult(Status.OK, rows);
    }

    private static List<ChannelRecord> queryChannels(ContentResolver resolver) {
        try (Cursor cursor = queryWithFallback(resolver, TvContract.Channels.CONTENT_URI, CHANNEL_PROJECTIONS)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return Collections.emptyList();
            }
            List<ChannelRecord> out = new ArrayList<>();
            int order = 0;
            do {
                long id = optLong(cursor, BaseColumns._ID, -1L);
                if (id <= 0) continue;
                String pkg = optString(cursor, TvContract.Channels.COLUMN_PACKAGE_NAME);
                String name = optString(cursor, TvContract.Channels.COLUMN_DISPLAY_NAME);
                String appLink = optString(cursor, TvContract.Channels.COLUMN_APP_LINK_INTENT_URI);
                boolean browsable = optInt(cursor, TvContract.Channels.COLUMN_BROWSABLE, 0) != 0;
                String type = optString(cursor, TvContract.Channels.COLUMN_TYPE);
                out.add(new ChannelRecord(id, pkg, name, appLink, browsable, type, order++));
            } while (cursor.moveToNext());
            return out;
        }
    }

    private static List<ProgramRecord> queryPrograms(ContentResolver resolver) {
        try (Cursor cursor = queryWithFallback(resolver, TvContract.PreviewPrograms.CONTENT_URI, PROGRAM_PROJECTIONS)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return Collections.emptyList();
            }
            List<ProgramRecord> out = new ArrayList<>();
            int order = 0;
            do {
                long id = optLong(cursor, BaseColumns._ID, -1L);
                long channelId = optLong(cursor, TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, -1L);
                String pkg = optString(cursor, TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME);
                String title = optString(cursor, TvContract.PreviewPrograms.COLUMN_TITLE);
                String desc = optString(cursor, TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION);
                String epTitle = optString(cursor, TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE);
                String season = optString(cursor, TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
                String epNum = optString(cursor, TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
                String poster = optString(cursor, TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI);
                String thumb = optString(cursor, TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI);
                String intentUri = optString(cursor, TvContract.PreviewPrograms.COLUMN_INTENT_URI);
                int weight = optInt(cursor, TvContract.PreviewPrograms.COLUMN_WEIGHT, 0);
                long pos = optLong(cursor, TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, 0L);
                long dur = optLong(cursor, TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS, 0L);
                // If searchable column is absent on a vendor table, keep the row; only filter explicit 0.
                int searchable = optInt(cursor, TvContract.PreviewPrograms.COLUMN_SEARCHABLE, 1);
                out.add(new ProgramRecord(
                        id, channelId, pkg, title, desc, epTitle, season, epNum,
                        poster, thumb, intentUri, weight, pos, dur, searchable != 0, order++));
            } while (cursor.moveToNext());
            return out;
        }
    }

    /**
     * Queries with no selection (selection=null, selectionArgs=null, sortOrder=null) and a named
     * projection. Falls back to smaller named projections if a vendor table omits optional columns.
     */
    private static Cursor queryWithFallback(ContentResolver resolver, Uri uri, String[][] projections) {
        RuntimeException lastColumnError = null;
        for (String[] projection : projections) {
            try {
                return resolver.query(uri, projection, null, null, null);
            } catch (SQLiteException | IllegalArgumentException e) {
                lastColumnError = e;
            }
        }
        if (lastColumnError != null) {
            throw lastColumnError;
        }
        return null;
    }

    private static Map<String, String> resolveAppLabels(PackageManager pm, Set<String> packages) {
        if (pm == null || packages.isEmpty()) return Collections.emptyMap();
        Map<String, String> labels = new HashMap<>();
        for (String pkg : packages) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(info);
                if (label != null && label.length() > 0) {
                    labels.put(pkg, label.toString());
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Keep package name fallback when the package is hidden or uninstalled.
            }
        }
        return labels;
    }

    /**
     * Decodes a local content:// or android.resource:// image bounded to maxWidth x maxHeight.
     * Returns null for network URIs or unreadable images.
     */
    static Bitmap loadLocalBitmap(ContentResolver resolver, String rawUri, int maxWidth, int maxHeight) {
        if (resolver == null || !PreviewLogic.isLocalImageUri(rawUri)) {
            return null;
        }
        Uri uri;
        try {
            uri = Uri.parse(rawUri);
        } catch (RuntimeException e) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream probe = resolver.openInputStream(uri)) {
            if (probe == null) return null;
            BitmapFactory.decodeStream(probe, null, bounds);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            return null;
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) {
            return null;
        }
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = PreviewLogic.computeSampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight);
        decode.inPreferredConfig = Bitmap.Config.RGB_565;

        Bitmap bitmap;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) return null;
            bitmap = BitmapFactory.decodeStream(stream, null, decode);
        } catch (IOException | SecurityException | IllegalArgumentException | OutOfMemoryError e) {
            return null;
        }
        if (bitmap == null) {
            return null;
        }
        return capBitmapDimensions(bitmap, maxWidth * 2, maxHeight * 2);
    }

    private static Bitmap capBitmapDimensions(Bitmap bitmap, int maxW, int maxH) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w <= maxW && h <= maxH) {
            return bitmap;
        }
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        int targetW = Math.max(1, Math.round(w * scale));
        int targetH = Math.max(1, Math.round(h * scale));
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            return scaled;
        } catch (OutOfMemoryError e) {
            return bitmap;
        }
    }

    private static String optString(Cursor cursor, String column) {
        int idx = cursor.getColumnIndex(column);
        if (idx < 0 || cursor.isNull(idx)) return "";
        try {
            String value = cursor.getString(idx);
            return value == null ? "" : value;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static long optLong(Cursor cursor, String column, long fallback) {
        int idx = cursor.getColumnIndex(column);
        if (idx < 0 || cursor.isNull(idx)) return fallback;
        try {
            return cursor.getLong(idx);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int optInt(Cursor cursor, String column, int fallback) {
        int idx = cursor.getColumnIndex(column);
        if (idx < 0 || cursor.isNull(idx)) return fallback;
        try {
            return cursor.getInt(idx);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}


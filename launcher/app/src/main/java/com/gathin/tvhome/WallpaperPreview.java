package com.gathin.tvhome;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * wallpaper_preview (#3907): the Wallpaper picker shows the focused row's picture above the list. One
 * 320x180 decode per focused row (ClockScene.decodePreview's steps) on a single worker thread; the
 * bitmap is freed when the dialog closes. No rows, no adapter: the list is the picker's own, and the
 * picture lives in the custom title view.
 */
final class WallpaperPreview {
    private WallpaperPreview() {
    }

    /** The file the picker's focused row shows, or null for the None row and for anything out of range. */
    static File image(List<File> images, int position) {
        if (images == null || position < 0 || position >= images.size()) return null;
        return images.get(position);
    }

    /** Builds the picker with the preview on, shows it, and wires focus moves to the decode. The picture
     *  takes the title view's place; the list, the buttons and every other part of the picker stay as they
     *  were, and the picture follows the row the D-pad moves to. */
    static void attach(final LauncherSettingsActivity activity, AlertDialog.Builder builder, final List<File> images) {
        float density = activity.getResources().getDisplayMetrics().density;
        final ImageView picture = new ImageView(activity);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setPadding((int) (20 * density), (int) (20 * density), (int) (20 * density), (int) (8 * density));
        picture.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (110 * density)));
        builder.setCustomTitle(picture);

        final ExecutorService pool = Executors.newSingleThreadExecutor();
        final boolean[] closed = {false};
        final AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            closed[0] = true;
            pool.shutdown();
            if (picture.getDrawable() instanceof BitmapDrawable) {
                Bitmap shown = ((BitmapDrawable) picture.getDrawable()).getBitmap();
                if (shown != null && !shown.isRecycled()) shown.recycle();
            }
        });
        dialog.show();
        ListView list = dialog.getListView();
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                show(activity, pool, closed, picture, image(images, position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** Decodes the row's picture off the UI thread and shows it; nothing runs once the dialog is closed. */
    private static void show(final LauncherSettingsActivity activity, ExecutorService pool,
                             final boolean[] closed, final ImageView picture, File file) {
        if (closed[0]) return;
        pool.execute(() -> {
            final Bitmap decoded = ClockScene.decodePreview(file);
            activity.runOnUiThread(() -> {
                if (!closed[0]) picture.setImageBitmap(decoded);
            });
        });
    }
}

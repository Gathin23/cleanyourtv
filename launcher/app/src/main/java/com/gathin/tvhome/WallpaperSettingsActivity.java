package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public final class WallpaperSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    private static final int REQUEST_PICK_CUSTOM = 3001;

    private static class WallpaperItem {
        final int cardId;
        final int imageId;
        final String assetName;
        final String title;

        WallpaperItem(int cardId, int imageId, String assetName, String title) {
            this.cardId = cardId;
            this.imageId = imageId;
            this.assetName = assetName;
            this.title = title;
        }
    }

    private static final WallpaperItem[] PRESETS = new WallpaperItem[] {
        // Waves & Coasts
        new WallpaperItem(R.id.card_waves_1, R.id.img_waves_1, "02-golden-coast.jpg", "Golden Coast"),
        new WallpaperItem(R.id.card_waves_2, R.id.img_waves_2, "03-blue-horizons.jpg", "Blue Horizons"),
        new WallpaperItem(R.id.card_waves_3, R.id.img_waves_3, "13-lighthouse-coast.jpg", "Lighthouse Coast"),

        // Peaks & Mountains
        new WallpaperItem(R.id.card_peaks_1, R.id.img_peaks_1, "01-alps-switzerland.jpg", "Swiss Alps"),
        new WallpaperItem(R.id.card_peaks_2, R.id.img_peaks_2, "01-dusk-peaks.jpg", "Dusk Peaks"),
        new WallpaperItem(R.id.card_peaks_3, R.id.img_peaks_3, "07-mount-baker.jpg", "Mount Baker"),

        // Aurora & Dunes
        new WallpaperItem(R.id.card_aurora_1, R.id.img_aurora_1, "04-aurora.jpg", "Aurora Borealis"),
        new WallpaperItem(R.id.card_dunes_1, R.id.img_dunes_1, "05-dunes.jpg", "Desert Dunes"),
        new WallpaperItem(R.id.card_dunes_2, R.id.img_dunes_2, "02-atacama-chile.jpg", "Atacama Sunset"),

        // Dark & Night
        new WallpaperItem(R.id.card_dark_1, R.id.img_dark_1, "03-moon-lake.jpg", "Moon Lake"),
        new WallpaperItem(R.id.card_dark_2, R.id.img_dark_2, "10-city-lights.jpg", "City Lights"),
        new WallpaperItem(R.id.card_dark_3, R.id.img_dark_3, "06-above-the-clouds.jpg", "Above Clouds")
    };

    private SharedPreferences choices;
    private ImageView previewBackground;
    private View previewDockBand;
    private TextView txtCurrentWpName;
    private TextView txtDockOpacityVal;
    private int dockOpacity = 100;

    private final Map<String, Bitmap> thumbnailCache = new HashMap<>();
    private Bitmap currentPreviewBitmap = null;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_wallpaper_settings);

        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        dockOpacity = choices.getInt(Config.DOCK_OPACITY_KEY, 100);

        previewBackground = findViewById(R.id.previewBackground);
        previewDockBand = findViewById(R.id.previewDockBand);
        txtCurrentWpName = findViewById(R.id.txtCurrentWpName);
        txtDockOpacityVal = findViewById(R.id.txtDockOpacityVal);

        updateDockOpacityUi();

        // 1. Copy presets into internal storage in background if not present
        new Thread(this::copyAssetWallpapersIfNeeded).start();

        // 2. Load current wallpaper name and preview
        String currentChoice = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        String currentTitle = "Blue Horizons";
        String currentAsset = "03-blue-horizons.jpg";
        if (currentChoice != null) {
            for (WallpaperItem item : PRESETS) {
                if (item.assetName.equals(currentChoice)) {
                    currentTitle = item.title;
                    currentAsset = item.assetName;
                    break;
                }
            }
            if (currentChoice.startsWith("custom_")) {
                currentTitle = "Custom Photo";
                currentAsset = currentChoice;
            }
        }
        if (txtCurrentWpName != null) {
            txtCurrentWpName.setText("In use: " + currentTitle);
        }
        showPreview(currentAsset);

        // 3. Custom picker button
        View btnCustomPicker = findViewById(R.id.btnCustomPicker);
        if (btnCustomPicker != null) {
            btnCustomPicker.setOnClickListener(v -> pickCustomWallpaper());
        }

        // 4. Dock opacity row
        View rowDockOpacity = findViewById(R.id.rowDockOpacity);
        if (rowDockOpacity != null) {
            rowDockOpacity.setOnClickListener(v -> cycleDockOpacity());
        }

        // 5. Setup preset cards & thumbnails
        FrameLayout initialFocusCard = null;
        for (WallpaperItem item : PRESETS) {
            FrameLayout card = findViewById(item.cardId);
            ImageView img = findViewById(item.imageId);

            if (img != null) {
                Bitmap thumb = loadThumbnail(item.assetName);
                if (thumb != null) {
                    img.setImageBitmap(thumb);
                }
            }

            if (card != null) {
                if (item.assetName.equals(currentChoice)) {
                    initialFocusCard = card;
                }

                card.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                        v.setElevation(8f);
                        showPreview(item.assetName);
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                        v.setElevation(2f);
                    }
                });

                card.setOnClickListener(v -> applyPreset(item));
            }
        }

        if (initialFocusCard != null) {
            initialFocusCard.requestFocus();
        } else if (btnCustomPicker != null) {
            btnCustomPicker.requestFocus();
        }
    }

    private void updateDockOpacityUi() {
        if (txtDockOpacityVal != null) {
            txtDockOpacityVal.setText(dockOpacity + "%");
        }
        if (previewDockBand != null) {
            previewDockBand.setAlpha(dockOpacity / 100.0f);
        }
    }

    private void cycleDockOpacity() {
        if (dockOpacity == 100) dockOpacity = 70;
        else if (dockOpacity == 70) dockOpacity = 50;
        else if (dockOpacity == 50) dockOpacity = 30;
        else if (dockOpacity == 30) dockOpacity = 0;
        else dockOpacity = 100;

        choices.edit().putInt(Config.DOCK_OPACITY_KEY, dockOpacity).apply();
        changed();
        updateDockOpacityUi();
    }

    private void applyPreset(WallpaperItem item) {
        File dir = Config.dir(this);
        if (dir == null) dir = getFilesDir();
        File wallpapersDir = new File(dir, "wallpapers");
        if (!wallpapersDir.exists()) wallpapersDir.mkdirs();
        File dest = new File(wallpapersDir, item.assetName);
        if (!dest.exists() || dest.length() == 0) {
            try (InputStream in = getAssets().open("wallpapers/" + item.assetName);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } catch (Exception ignored) {}
        }

        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, item.assetName).apply();
        changed();
        if (txtCurrentWpName != null) {
            txtCurrentWpName.setText("In use: " + item.title);
        }
        showPreview(item.assetName);
        Toast.makeText(this, "Wallpaper applied: " + item.title, Toast.LENGTH_SHORT).show();
    }

    private void pickCustomWallpaper() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Choose Wallpaper with File Manager / Gallery"), REQUEST_PICK_CUSTOM);
        } catch (Exception e) {
            Toast.makeText(this, "No file manager or gallery found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_CUSTOM && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importCustomWallpaper(data.getData());
        }
    }

    private void importCustomWallpaper(Uri uri) {
        try {
            File dir = Config.dir(this);
            if (dir == null) dir = getFilesDir();
            File wallpapersDir = new File(dir, "wallpapers");
            if (!wallpapersDir.exists()) wallpapersDir.mkdirs();
            String name = "custom_" + System.currentTimeMillis() + ".jpg";
            File dest = new File(wallpapersDir, name);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, name).apply();
            changed();
            if (txtCurrentWpName != null) {
                txtCurrentWpName.setText("In use: Custom Photo");
            }
            showPreview(name);
            Toast.makeText(this, "Custom wallpaper applied", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPreview(String assetOrFileName) {
        if (assetOrFileName == null || previewBackground == null) return;
        try {
            File dir = Config.dir(this);
            File localFile = new File(new File(dir, "wallpapers"), assetOrFileName);
            InputStream is;
            if (localFile.exists() && localFile.length() > 0) {
                is = new java.io.FileInputStream(localFile);
            } else {
                is = getAssets().open("wallpapers/" + assetOrFileName);
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2; // Downscale to 540p for crisp, lightweight TV frame preview
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
            is.close();

            if (bmp != null) {
                if (currentPreviewBitmap != null && currentPreviewBitmap != bmp) {
                    currentPreviewBitmap.recycle();
                }
                currentPreviewBitmap = bmp;
                previewBackground.setImageBitmap(bmp);
            }
        } catch (Exception ignored) {}
    }

    private Bitmap loadThumbnail(String assetName) {
        Bitmap cached = thumbnailCache.get(assetName);
        if (cached != null) return cached;
        try (InputStream in = getAssets().open("wallpapers/" + assetName)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 4; // Downscale to 270p thumbnail (~100 KB RAM)
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp != null) {
                thumbnailCache.put(assetName, bmp);
                return bmp;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void copyAssetWallpapersIfNeeded() {
        try {
            File dir = Config.dir(this);
            if (dir == null) dir = getFilesDir();
            File wallpapersDir = new File(dir, "wallpapers");
            if (!wallpapersDir.exists()) wallpapersDir.mkdirs();

            for (WallpaperItem item : PRESETS) {
                File dest = new File(wallpapersDir, item.assetName);
                if (!dest.exists() || dest.length() == 0) {
                    try (InputStream in = getAssets().open("wallpapers/" + item.assetName);
                         OutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Bitmap bmp : thumbnailCache.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        thumbnailCache.clear();
        if (currentPreviewBitmap != null && !currentPreviewBitmap.isRecycled()) {
            currentPreviewBitmap.recycle();
            currentPreviewBitmap = null;
        }
    }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    static final String EXTRA_LIBRARY = "com.gathin.tvhome.extra.LIBRARY_FROM_SETTINGS";
    private static final int REQUEST_PICK_WALLPAPER = 2001;

    private SharedPreferences choices;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;

    private TextView txtColumnsVal;
    private TextView txtDockOpacityVal;
    private TextView txtRowsBelowVal;
    private TextView txtClockFormatVal;
    private TextView txtWallpaperVal;
    private TextView txtRamFreeBadge;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);

        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;

        txtColumnsVal = findViewById(R.id.txtColumnsVal);
        txtDockOpacityVal = findViewById(R.id.txtDockOpacityVal);
        txtRowsBelowVal = findViewById(R.id.txtRowsBelowVal);
        txtClockFormatVal = findViewById(R.id.txtClockFormatVal);
        txtWallpaperVal = findViewById(R.id.txtWallpaperVal);
        txtRamFreeBadge = findViewById(R.id.txtRamFreeBadge);

        // Row 1: Columns
        View rowColumns = findViewById(R.id.rowColumns);
        if (rowColumns != null) {
            rowColumns.setOnClickListener(v -> {
                // Cycle 4 -> 5 -> 6 -> 7 -> 4
                columns = (columns >= 7) ? 4 : (columns + 1);
                choices.edit().putInt("columns", columns).apply();
                changed();
                refresh();
            });
        }

        // Row 2: Dock Opacity
        View rowDockOpacity = findViewById(R.id.rowDockOpacity);
        if (rowDockOpacity != null) {
            rowDockOpacity.setOnClickListener(v -> {
                // Cycle 100 -> 70 -> 50 -> 30 -> 100
                if (dockOpacity == 100) dockOpacity = 70;
                else if (dockOpacity == 70) dockOpacity = 50;
                else if (dockOpacity == 50) dockOpacity = 30;
                else dockOpacity = 100;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, dockOpacity).apply();
                changed();
                refresh();
            });
        }

        // Row 3: Rows Below Dock
        View rowRowsBelow = findViewById(R.id.rowRowsBelow);
        if (rowRowsBelow != null) {
            rowRowsBelow.setOnClickListener(v -> {
                // Cycle 0 -> 1 -> 2 -> 3 -> 0
                rowsBelowDock = (rowsBelowDock >= 3) ? 0 : (rowsBelowDock + 1);
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, rowsBelowDock).apply();
                changed();
                refresh();
            });
        }

        // Row 4: Clock Format
        View rowClockFormat = findViewById(R.id.rowClockFormat);
        if (rowClockFormat != null) {
            rowClockFormat.setOnClickListener(v -> {
                if ("system".equals(clockFormat)) clockFormat = "12";
                else if ("12".equals(clockFormat)) clockFormat = "24";
                else clockFormat = "system";
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, clockFormat).apply();
                changed();
                refresh();
            });
        }

        // Row 5: Wallpaper Studio
        View rowWallpaper = findViewById(R.id.rowWallpaper);
        if (rowWallpaper != null) {
            rowWallpaper.setOnClickListener(v -> {
                startActivity(new Intent(this, WallpaperSettingsActivity.class));
            });
        }

        // Row 6: Pick custom wallpaper (Gallery / File Manager)
        View rowPickWallpaper = findViewById(R.id.rowPickWallpaper);
        if (rowPickWallpaper != null) {
            rowPickWallpaper.setOnClickListener(v -> {
                startActivity(new Intent(this, WallpaperSettingsActivity.class));
            });
        }

        // Row 7: Close All Background Apps / Free RAM
        View rowKillApps = findViewById(R.id.rowKillApps);
        if (rowKillApps != null) {
            rowKillApps.setOnClickListener(v -> closeAllBackgroundApps());
        }

        // Row 8: App Reorder
        View rowAppReorder = findViewById(R.id.rowAppReorder);
        if (rowAppReorder != null) {
            rowAppReorder.setOnClickListener(v -> {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
                finish();
            });
        }

        // Row 9: App Library
        View rowAppLibrary = findViewById(R.id.rowAppLibrary);
        if (rowAppLibrary != null) {
            rowAppLibrary.setOnClickListener(v -> {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_LIBRARY, true));
                finish();
            });
        }

        // Row 10: Android System Settings
        View rowTvSettings = findViewById(R.id.rowTvSettings);
        if (rowTvSettings != null) {
            rowTvSettings.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(this, "Opening settings", Toast.LENGTH_SHORT).show();
                }
            });
        }

        refresh();
        if (rowColumns != null) rowColumns.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        refresh();
    }

    private void refresh() {
        if (txtColumnsVal != null) txtColumnsVal.setText(columns + " apps");
        if (txtDockOpacityVal != null) txtDockOpacityVal.setText(dockOpacity + "%");
        if (txtRowsBelowVal != null) txtRowsBelowVal.setText(rowsBelowDock + " rows");
        if (txtClockFormatVal != null) {
            if ("12".equals(clockFormat)) txtClockFormatVal.setText("12-hour");
            else if ("24".equals(clockFormat)) txtClockFormatVal.setText("24-hour");
            else txtClockFormatVal.setText("System");
        }
        if (txtWallpaperVal != null) {
            String wp = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
            txtWallpaperVal.setText(wp == null ? "Default" : (wp.startsWith("custom_") ? "Custom" : wp));
        }
        updateRamBadge();
    }

    private void updateRamBadge() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null && txtRamFreeBadge != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long availMb = mi.availMem / (1024 * 1024);
                txtRamFreeBadge.setText(availMb + " MB free");
            }
        } catch (Exception ignored) {}
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size() : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    else choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, images.get(which).getName()).apply();
                    changed();
                    refresh();
                })
                .setPositiveButton("Gallery / File Manager", (dialog, which) -> {
                    dialog.dismiss();
                    pickCustomWallpaper();
                })
                .setNeutralButton("Default", (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickCustomWallpaper() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Choose Wallpaper with File Manager / Gallery"), REQUEST_PICK_WALLPAPER);
        } catch (Exception e) {
            Toast.makeText(this, "No file manager or gallery found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importWallpaper(data.getData());
        }
    }

    private void importWallpaper(Uri uri) {
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
            Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
            refresh();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to import wallpaper: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void closeAllBackgroundApps() {
        RamBooster.clean(this, true);
        refresh();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }
}

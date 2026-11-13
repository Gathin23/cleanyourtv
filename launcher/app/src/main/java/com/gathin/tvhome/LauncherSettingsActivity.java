package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        bindSlider(R.id.columnsSlider, R.string.columns_label, 4, 7, value -> {
            columns = value;
            choices.edit().putInt("columns", value).apply();
        });
        bindSlider(R.id.rowsSlider, R.string.rows_below_dock_label, 0, 3, value -> {
            rowsBelowDock = value;
            choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
        });
        bindSlider(R.id.opacitySlider, R.string.dock_opacity_label, 30, 100, value -> {
            dockOpacity = value;
            choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
        });
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        bindSlider(R.id.effectsSlider, R.string.effect_strength, 0, 3, value ->
                choices.edit().putInt(AppAdapter.EFFECT_STRENGTH_KEY, value).apply());
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            new AlertDialog.Builder(this)
                    .setMessage(R.string.reset_launcher_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> resetLauncherChoices())
                    .show();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.saveProfileChoice).setOnClickListener(v -> chooseProfile(false));
        findViewById(R.id.restoreProfileChoice).setOnClickListener(v -> chooseProfile(true));
        bindSection(R.id.displaySection, R.id.wallpaperChoice);
        bindSection(R.id.systemSection, R.id.defaultHomeChoice);
        bindSection(R.id.profilesSection, R.id.saveProfileChoice);
        findViewById(R.id.reloadHomeChoice).setOnClickListener(v -> {
            changed();
            finish();
        });
        findViewById(R.id.repairHomeChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.storeBadgeChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        TextView language = findViewById(R.id.languageChoice);
        String[] languages = {"", "fr", "en"};
        CharSequence[] labels = {getText(R.string.language_tv), "Français", "English"};
        int selectedLanguage = java.util.Arrays.asList(languages)
                .indexOf(choices.getString(LauncherLocale.LANGUAGE_KEY, ""));
        int currentLanguage = Math.max(0, selectedLanguage);
        language.setText(getString(R.string.language_current, labels[currentLanguage]));
        language.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, currentLanguage, (dialog, index) -> {
                    dialog.dismiss();
                    if (index == currentLanguage) return;
                    choices.edit().putString(LauncherLocale.LANGUAGE_KEY, languages[index]).apply();
                    changed();
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else findViewById(R.id.columnsSlider).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        android.app.role.RoleManager roles = android.os.Build.VERSION.SDK_INT >= 29
                ? getSystemService(android.app.role.RoleManager.class) : null;
        CharSequence label;
        if (roles != null && roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
            label = getText(R.string.app_name);
        } else {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = getPackageManager();
            ResolveInfo resolved = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo == null
                    || "android".equals(resolved.activityInfo.packageName)) return;
            label = resolved.loadLabel(pm);
        }
        ((TextView) findViewById(R.id.defaultHomeChoice)).setText(getString(R.string.home_settings_current, label));
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void resetLauncherChoices() {
        choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                .remove("advanced_effects").remove(AppAdapter.EFFECT_STRENGTH_KEY).remove(Config.WALLPAPER_CHOICE_KEY)
                .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
        Config current = Config.load(this);
        columns = current.columns;
        rowsBelowDock = current.rowsBelowDock;
        dockOpacity = current.dockOpacity;
        clockFormat = current.clockFormat;
        animations = current.animations;
        advancedEffects = current.animations && current.advancedEffects;
        changed();
        refresh();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 2];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        labels[images.size() + 1] = getString(R.string.wallpaper_import);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        AlertDialog.Builder picker = new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(wallpaperRows(labels, images), checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size() + 1) {
                        openWallpaperImport();
                        return;
                    }
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (!images.isEmpty()) {
            picker.setPositiveButton(R.string.wallpaper_delete, (dialog, which) ->
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.wallpaper_delete)
                            .setAdapter(wallpaperRows(java.util.Arrays.copyOf(labels, images.size()), images),
                                    (list, index) -> confirmWallpaperDeletion(images.get(index)))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show());
        }
        picker.show();
    }

    private android.widget.ListAdapter wallpaperRows(String[] labels, List<File> images) {
        int width = Math.round(96 * getResources().getDisplayMetrics().density);
        int height = Math.round(54 * getResources().getDisplayMetrics().density);
        android.util.LruCache<Integer, android.graphics.Bitmap> previews = new android.util.LruCache<>(12);
        return new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, labels) {
            @Override
            public View getView(int position, View recycled, android.view.ViewGroup parent) {
                android.widget.CheckedTextView row =
                        (android.widget.CheckedTextView) super.getView(position, recycled, parent);
                android.graphics.drawable.Drawable preview = null;
                if (position < images.size()) {
                    android.graphics.Bitmap bitmap = previews.get(position);
                    if (bitmap == null) {
                        String path = images.get(position).getPath();
                        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        android.graphics.BitmapFactory.decodeFile(path, options);
                        options.inSampleSize = 1;
                        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > width * 2)
                            options.inSampleSize *= 2;
                        options.inJustDecodeBounds = false;
                        bitmap = android.graphics.BitmapFactory.decodeFile(path, options);
                        if (bitmap != null) previews.put(position, bitmap);
                    }
                    if (bitmap != null) {
                        preview = new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
                        float scale = Math.min((float) width / bitmap.getWidth(), (float) height / bitmap.getHeight());
                        preview.setBounds(0, 0, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                                Math.max(1, Math.round(bitmap.getHeight() * scale)));
                    }
                }
                row.setCompoundDrawablePadding(width / 8);
                row.setCompoundDrawablesRelative(preview, null, null, null);
                row.setMinHeight(height + width / 8);
                return row;
            }
        };
    }

    private static final int IMPORT_WALLPAPER_REQUEST = 301;

    @SuppressWarnings("deprecation")
    private void openWallpaperImport() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try {
            startActivityForResult(picker, IMPORT_WALLPAPER_REQUEST);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT_WALLPAPER_REQUEST || result != RESULT_OK
                || data == null || data.getData() == null) return;
        android.net.Uri source = data.getData();
        new Thread(() -> {
            String name = importWallpaper(source);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (name == null) {
                    Toast.makeText(this, R.string.wallpaper_import_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, name).apply();
                changed();
                refresh();
            });
        }, "WallpaperImport").start();
    }

    private String importWallpaper(android.net.Uri source) {
        File pending = null;
        try {
            File theme = Config.dir(this);
            if (theme == null) return null;
            File gallery = new File(theme, ThemeFiles.WALLPAPERS);
            if (!gallery.isDirectory() && !gallery.mkdirs()) return null;
            pending = File.createTempFile(".import-", ".tmp", gallery);
            try (java.io.InputStream input = getContentResolver().openInputStream(source);
                 java.io.OutputStream output = new java.io.FileOutputStream(pending)) {
                if (input == null) return null;
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 20 * 1024 * 1024) return null;
                    output.write(buffer, 0, count);
                }
            }
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(pending.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            String extension;
            switch (bounds.outMimeType == null ? "" : bounds.outMimeType) {
                case "image/jpeg": extension = ".jpg"; break;
                case "image/png": extension = ".png"; break;
                case "image/webp": extension = ".webp"; break;
                default: return null;
            }
            File image = new File(gallery, "USB-" + java.util.UUID.randomUUID() + extension);
            return pending.renameTo(image) ? image.getName() : null;
        } catch (java.io.IOException | RuntimeException failure) {
            return null;
        } finally {
            if (pending != null) pending.delete();
        }
    }


    private void confirmWallpaperDeletion(File image) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_delete)
                .setMessage(getString(R.string.wallpaper_delete_confirm, image.getName()))
                .setPositiveButton(R.string.wallpaper_delete, (dialog, which) -> {
                    if (!image.delete()) {
                        Toast.makeText(this, R.string.wallpaper_delete_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (image.getName().equals(choices.getString(Config.WALLPAPER_CHOICE_KEY, null))) {
                        choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    }
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }


    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void bindSection(int buttonId, int targetId) {
        View button = findViewById(buttonId);
        button.setNextFocusRightId(targetId);
        button.setOnFocusChangeListener((view, focused) -> {
            if (!focused) return;
            findViewById(R.id.displayPanel).setVisibility(buttonId == R.id.displaySection ? View.VISIBLE : View.GONE);
            findViewById(R.id.systemPanel).setVisibility(buttonId == R.id.systemSection ? View.VISIBLE : View.GONE);
            findViewById(R.id.profilesPanel).setVisibility(buttonId == R.id.profilesSection ? View.VISIBLE : View.GONE);
        });
        button.setOnClickListener(v -> findViewById(targetId).requestFocus());
    }

    static final String EXTRA_DOCK_LABELS = "dock_labels";

    private void chooseProfile(boolean restore) {
        String[] dockOrders = new String[3];
        SharedPreferences descriptions = getSharedPreferences("profile_descriptions", MODE_PRIVATE);
        String[] labels = new String[3];
        for (int slot = 0; slot < labels.length; slot++) {
            File profile = LocalProfile.file(this, slot);
            String description = descriptions.getString(Integer.toString(slot), "");
            String detail = profile.isFile()
                    ? java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(profile.lastModified()))
                    : getString(R.string.profile_missing);
            String[] summary = profile.isFile() ? LocalProfile.summary(this, slot) : new String[]{"", ""};
            dockOrders[slot] = summary[1];
            if (!summary[0].isEmpty()) detail += "\n" + summary[0];
            labels[slot] = (slot + 1) + " · " + (description.isEmpty() ? "" : description + " · ") + detail;
        }
        new AlertDialog.Builder(this).setTitle(restore ? R.string.profile_restore : R.string.profile_save)
                .setItems(labels, (dialog, slot) -> {
                    if (restore) {
                        if (!LocalProfile.file(this, slot).isFile()) return;
                        new AlertDialog.Builder(this).setMessage(getString(R.string.profile_restore_confirm, labels[slot]) + (dockOrders[slot].isEmpty() ? "" : "\n\n" + dockOrders[slot]))
                                .setPositiveButton(android.R.string.ok, (confirm, which) -> transferProfile(true, slot, ""))
                                .setNegativeButton(android.R.string.cancel, null).show();
                        return;
                    }
                    android.widget.EditText description = new android.widget.EditText(this);
                    description.setSingleLine(true);
                    description.setHint(R.string.profile_description);
                    description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
                    description.setText(descriptions.getString(Integer.toString(slot), ""));
                    new AlertDialog.Builder(this).setTitle(R.string.profile_save)
                            .setMessage(R.string.profile_save_confirm).setView(description)
                            .setPositiveButton(android.R.string.ok, (confirm, which) ->
                                    transferProfile(false, slot, description.getText().toString().trim()))
                            .setNegativeButton(android.R.string.cancel, null).show();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void transferProfile(boolean restore, int slot, String description) {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setMessage(getText(restore ? R.string.profile_restore : R.string.profile_save));
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            boolean success;
            try {
                if (restore) LocalProfile.restore(this, choices, slot);
                else {
                    Config config = Config.load(this);
                    String wallpaper = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
                    if ("none".equals(wallpaper)) wallpaper = getString(R.string.wallpaper_none);
                    else if (wallpaper == null) wallpaper = config.wallpaper == null
                            ? getString(R.string.wallpaper_theme_default) : config.wallpaper.getName();
                    String summary = getString(R.string.profile_summary, config.columns, config.rowsBelowDock, wallpaper,
                            getString(config.animations ? R.string.profile_on : R.string.profile_off));
                    List<String> dock = getIntent().getStringArrayListExtra(EXTRA_DOCK_LABELS);
                    String dockOrder = dock == null || dock.isEmpty() || config.columns != dock.size()
                            ? "" : getString(R.string.profile_dock_order, String.join(" → ", dock));
                    LocalProfile.save(this, choices, slot, summary, dockOrder);
                    getSharedPreferences("profile_descriptions", MODE_PRIVATE).edit()
                            .putString(Integer.toString(slot), description).apply();
                }
                success = true;
            } catch (java.io.IOException | RuntimeException failure) {
                success = false;
            }
            boolean completed = success;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                progress.dismiss();
                Toast.makeText(this, completed ? R.string.profile_done : R.string.profile_failed,
                        Toast.LENGTH_SHORT).show();
                if (restore) {
                    changed();
                    finish();
                }
            });
        }, "LocalProfile").start();
    }

    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void bindSlider(int id, int label, int minimum, int maximum, java.util.function.IntConsumer save) {
        android.widget.SeekBar slider = findViewById(id);
        slider.setMin(minimum);
        slider.setMax(maximum);
        slider.setKeyProgressIncrement(id == R.id.opacitySlider ? 5 : 1);
        slider.setContentDescription(getText(label));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) {}
            @Override public void onProgressChanged(android.widget.SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                save.accept(value);
                changed();
                refresh();
            }
        });
    }

    private void updateSlider(int id, int valueId, int value, boolean percentage) {
        ((android.widget.SeekBar) findViewById(id)).setProgress(value);
        ((TextView) findViewById(valueId)).setText(Integer.toString(value) + (percentage ? "%" : ""));
    }

    private String previewKey = "";

    private void updateWallpaperPreview(String choice) {
        if (java.util.Objects.equals(previewKey, choice)) return;
        previewKey = choice;
        File wallpaper = Config.load(this).wallpaper;
        android.widget.ImageView preview = findViewById(R.id.wallpaperPreview);
        android.graphics.Bitmap bitmap = null;
        if (wallpaper != null) {
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(wallpaper.getPath(), options);
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 960) {
                options.inSampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            bitmap = android.graphics.BitmapFactory.decodeFile(wallpaper.getPath(), options);
        }
        preview.setImageBitmap(bitmap);
        preview.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
    }

    private void refresh() {
        updateSlider(R.id.columnsSlider, R.id.columnsValue, columns, false);
        updateSlider(R.id.rowsSlider, R.id.rowsValue, rowsBelowDock, false);
        updateSlider(R.id.opacitySlider, R.id.opacityValue, dockOpacity, true);
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        View effectControls = findViewById(R.id.effectControls);
        effectControls.setVisibility(advancedEffects ? View.VISIBLE : View.GONE);
        int strength = AppAdapter.effectStrength(choices);
        ((android.widget.SeekBar) findViewById(R.id.effectsSlider)).setProgress(strength);
        ((TextView) findViewById(R.id.effectsValue)).setText(
                getResources().getStringArray(R.array.effect_levels)[strength]);
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        updateWallpaperPreview(wallpaperCurrent);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains(AppAdapter.EFFECT_STRENGTH_KEY) || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}

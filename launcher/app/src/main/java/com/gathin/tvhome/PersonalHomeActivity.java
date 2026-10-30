package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Personal fork UI: responsive touch/D-pad home, no account access or network requests of its own. */
public final class PersonalHomeActivity extends Activity {
    private static final int CYAN = 0xff40d9ff, TEXT = 0xfff0f7ff, MUTED = 0xffa4bcd6;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout page, header, actions;
    private GridLayout grid;
    private TextView userName, time, date, networkStatus;
    private ImageView avatar;
    private Bitmap avatarBitmap;
    private SkyClock sky;
    private ConnectivityManager connectivity;
    private boolean networkRegistered, resumed, tv;
    private int pageWidth;
    private List<App> cachedApps;
    private AlertDialog panel, profile;
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { handler.post(() -> updateNetwork()); }
        @Override public void onLost(Network network) { handler.post(() -> updateNetwork()); }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            handler.post(() -> updateNetwork());
        }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            updateClock();
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("personal_home", MODE_PRIVATE);
        UiModeManager modes = getSystemService(UiModeManager.class);
        tv = modes != null && modes.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        connectivity = getSystemService(ConnectivityManager.class);
        buildHome();
        loadAvatar();
        if (!prefs.getBoolean("setup", false)) handler.post(() -> editProfile());
        else handleHomeAction(getIntent());
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable surface(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(focused ? 0xff153d5b : 0xef0a2036);
        shape.setCornerRadius(dp(16));
        shape.setStroke(dp(focused ? 3 : 1), focused ? CYAN : 0xff28435e);
        return shape;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(14);
        view.setTextColor(TEXT);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setMinimumWidth(0);
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        view.setBackground(surface(false));
        view.setOnFocusChangeListener((v, focus) -> v.setBackground(surface(focus)));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void addAction(LinearLayout parent, String title, Runnable action) {
        Button view = button(title, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1);
        p.setMargins(dp(3), dp(5), dp(3), dp(5));
        parent.addView(view, p);
    }

    private void buildHome() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xff031126);
        sky = new SkyClock();
        root.addView(sky, new ViewGroup.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int inset = dp(tv ? 32 : 16);
        page.setPadding(inset, dp(14), inset, dp(28));
        scroll.addView(page, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new ViewGroup.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            scroll.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        avatar = new ImageView(this);
        avatar.setContentDescription("Edit profile photo");
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(surface(false));
        avatar.setClipToOutline(true);
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> editProfile());
        avatar.setOnFocusChangeListener((v, focus) -> v.setBackground(surface(focus)));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout identity = new LinearLayout(this);
        identity.setPadding(dp(12), 0, dp(4), 0);
        identity.setOrientation(LinearLayout.VERTICAL);
        userName = text("", tv ? 24 : 20, TEXT);
        userName.setMaxLines(2);
        userName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(userName);
        identity.addView(text("Personal Home", 12, MUTED));
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        networkStatus = text("", 13, MUTED);
        networkStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        networkStatus.setCompoundDrawablePadding(dp(5));
        networkStatus.setFocusable(true);
        networkStatus.setOnFocusChangeListener((v, focus) -> {
            if (focus) v.setBackground(surface(true));
            else v.setBackgroundColor(Color.TRANSPARENT);
        });
        networkStatus.setPadding(dp(8), dp(8), dp(8), dp(8));
        networkStatus.setOnClickListener(v -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        header.addView(networkStatus, new LinearLayout.LayoutParams(-2, dp(48)));
        page.addView(header);
        actions = new LinearLayout(this);
        addAction(actions, "Search apps", () -> searchApps());
        addAction(actions, "✦ Gemini", () -> openGemini());
        addAction(actions, "Settings", () -> showSettings());
        page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(0, dp(tv ? 22 : 30), 0, dp(tv ? 24 : 32));
        time = text("", tv ? 66 : 56, TEXT);
        time.setGravity(Gravity.CENTER);
        time.setSingleLine(true);
        time.setAutoSizeTextTypeUniformWithConfiguration(26, tv ? 66 : 56, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        date = text("", tv ? 14 : 13, MUTED);
        date.setGravity(Gravity.CENTER);
        clock.addView(time, new LinearLayout.LayoutParams(-1, dp(tv ? 90 : 80)));
        clock.addView(date);
        page.addView(clock);
        LinearLayout shortcuts = new LinearLayout(this);
        if (tv) addAction(shortcuts, "Inputs", () -> showInputs());
        addAction(shortcuts, "Play Store", () -> openStore(""));
        if (tv) addAction(shortcuts, "Screensaver", () -> launch(new Intent(this, ClockActivity.class)));
        else addAction(shortcuts, "Default Home", () -> launch(new Intent(this, HomeSettingsActivity.class)));
        page.addView(shortcuts);
        TextView heading = text("Your apps", 20, TEXT);
        heading.setPadding(dp(4), dp(20), 0, dp(12));
        page.addView(heading);
        grid = new GridLayout(this);
        page.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        page.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = page.getWidth() - page.getPaddingLeft() - page.getPaddingRight();
            if (width > 0 && width != pageWidth) {
                pageWidth = width;
                if (cachedApps != null) renderApps(cachedApps);
                else loadApps();
            }
        });
        refreshProfile();
        updateClock();
        updateNetwork();
        applyBrightness(prefs.getInt("brightness", 80));
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        time.setText(PersonalHomeModel.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
        date.setText(new java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(now.getTime()));
        sky.minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        sky.invalidate();
    }

    private void updateNetwork() {
        if (networkStatus == null) return;
        Network active = connectivity == null ? null : connectivity.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        networkStatus.setText(online ? (wifi ? "Wi-Fi · Online" : "Online") : "No internet");
        int color = online ? 0xff59e493 : 0xffff6c78;
        networkStatus.setTextColor(color);
        int iconId = wifi || caps == null ? R.drawable.ic_wifi
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? R.drawable.ic_ethernet : R.drawable.ic_internet;
        android.graphics.drawable.Drawable icon = getDrawable(iconId).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, dp(22), dp(22));
        networkStatus.setCompoundDrawables(icon, null, null, null);
        networkStatus.setContentDescription(online ? "Internet available. Open Wi-Fi settings" : "No validated internet access. Open Wi-Fi settings");
    }

    private int appGeneration;
    private void loadApps() {
        int generation = ++appGeneration;
        worker.execute(() -> {
            List<ResolveInfo> found = new ArrayList<>();
            if (tv) found.addAll(getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
            found.addAll(getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
            Set<String> packages = new HashSet<>();
            List<App> apps = new ArrayList<>();
            java.util.Map<String, App> previous = new java.util.HashMap<>();
            if (cachedApps != null) for (App app : cachedApps) previous.put(app.info.activityInfo.packageName, app);
            for (ResolveInfo info : found) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !packages.add(pkg)) continue;
                try {
                    String label = info.loadLabel(getPackageManager()).toString();
                    App old = previous.get(pkg);
                    android.graphics.drawable.Drawable icon = old != null && old.name.equals(label)
                            && java.util.Objects.equals(old.info.activityInfo.applicationInfo.sourceDir, info.activityInfo.applicationInfo.sourceDir)
                            ? old.icon : info.loadIcon(getPackageManager());
                    // Icon resource access/decoding can be expensive on low-memory TVs. Never do it on UI.
                    apps.add(new App(label, info, icon));
                } catch (RuntimeException removedDuringQuery) {
                    // A package can be replaced or removed between queryIntentActivities and loading its resources.
                }
            }
            Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
            handler.post(() -> {
                if (!isDestroyed() && generation == appGeneration && !sameApps(cachedApps, apps)) renderApps(apps);
            });
        });
    }

    private static final class App {
        final String name;
        final ResolveInfo info;
        final android.graphics.drawable.Drawable icon;
        App(String name, ResolveInfo info, android.graphics.drawable.Drawable icon) {
            this.name = name; this.info = info; this.icon = icon;
        }
    }

    private static boolean sameApps(List<App> before, List<App> after) {
        if (before == null || before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            App a = before.get(i), b = after.get(i);
            if (a.icon != b.icon || !a.name.equals(b.name) || !a.info.activityInfo.packageName.equals(b.info.activityInfo.packageName)
                    || !a.info.activityInfo.name.equals(b.info.activityInfo.name)) return false;
        }
        return true;
    }

    private void renderApps(List<App> apps) {
        cachedApps = apps;
        grid.removeAllViews();
        int columns = PersonalHomeModel.columns((int) (pageWidth / getResources().getDisplayMetrics().density),
                prefs.getInt(tv ? "tv_columns" : "phone_columns", 0));
        grid.setColumnCount(columns);
        int width = pageWidth / columns;
        for (App app : apps) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(8), dp(12), dp(8), dp(10));
            tile.setBackground(surface(false));
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setContentDescription(app.name);
            tile.setOnFocusChangeListener((v, focus) -> v.setBackground(surface(focus)));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            tile.addView(icon, new LinearLayout.LayoutParams(dp(tv ? 58 : 44), dp(tv ? 58 : 44)));
            TextView name = text(app.name, 14, TEXT);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(0, dp(8), 0, 0);
            tile.addView(name, new LinearLayout.LayoutParams(-1, -2));
            tile.setOnClickListener(v -> launch(new Intent(Intent.ACTION_MAIN)
                    .setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
            tile.setOnLongClickListener(v -> {
                launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.info.activityInfo.packageName)));
                return true;
            });
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = width - dp(8);
            p.height = dp(tv ? 128 : 112);
            p.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(tile, p);
        }
    }

    private void searchApps() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("App name, e.g. YouTube");
        new AlertDialog.Builder(this).setTitle("Search Google Play apps").setView(input)
                .setPositiveButton("Search", (d, which) -> openStore(input.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void openStore(String query) {
        if (query.isEmpty()) {
            Intent store = getPackageManager().getLaunchIntentForPackage("com.android.vending");
            if (store != null && tryLaunch(store)) return;
        }
        String suffix = query.isEmpty() ? "" : "search?q=" + Uri.encode(query) + "&c=apps";
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse(query.isEmpty() ? "market://apps" : "market://" + suffix))
                .setPackage("com.android.vending");
        if (!tryLaunch(market)) launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/" + (suffix.isEmpty() ? "apps" : suffix))));
    }

    private void openGemini() {
        Intent gemini = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.bard");
        if (gemini != null && tryLaunch(gemini)) return;
        Intent market = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.apps.bard")).setPackage("com.android.vending");
        if (!tryLaunch(market)) launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.bard")));
    }

    private boolean tryLaunch(Intent target) {
        try {
            startActivity(target);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) { return false; }
    }

    private void launch(Intent target) {
        if (!tryLaunch(target)) Toast.makeText(this, "This app or setting is unavailable on this device", Toast.LENGTH_LONG).show();
    }

    private void openSetting(String action) { launch(SystemSettings.intent(this, action)); }

    private void showInputs() {
        android.media.tv.TvInputManager manager = getSystemService(android.media.tv.TvInputManager.class);
        List<android.media.tv.TvInputInfo> sources = manager == null ? Collections.emptyList() : manager.getTvInputList();
        List<String> labels = new ArrayList<>();
        List<Intent> targets = new ArrayList<>();
        for (android.media.tv.TvInputInfo source : sources) {
            if (source.isHidden(this)) continue;
            labels.add(source.loadLabel(this).toString());
            targets.add(new Intent(Intent.ACTION_VIEW, source.isPassthroughInput()
                    ? android.media.tv.TvContract.buildChannelUriForPassthroughInput(source.getId())
                    : android.media.tv.TvContract.Channels.CONTENT_URI));
        }
        if (targets.isEmpty()) {
            launch(new Intent("com.android.tv.action.VIEW_INPUTS"));
            return;
        }
        new AlertDialog.Builder(this).setTitle("Inputs").setItems(labels.toArray(new String[0]),
                (d, which) -> launch(targets.get(which))).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showSettings() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(8), dp(4), dp(8), dp(8));
        addPanelRow(rows, "Profile · name & photo", () -> editProfile());
        addPanelRow(rows, "Launcher settings", () -> launcherSettings());
        addPanelRow(rows, "Wi-Fi", () -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        addPanelRow(rows, "Bluetooth", () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS));
        addPanelRow(rows, "TV / device brightness", () -> openSetting(Settings.ACTION_DISPLAY_SETTINGS));
        TextView hint = text("Launcher brightness (this app only)", 13, MUTED);
        hint.setPadding(dp(12), dp(10), 0, 0);
        rows.addView(hint);
        SeekBar brightness = new SeekBar(this);
        brightness.setMax(90);
        brightness.setProgress(prefs.getInt("brightness", 80) - 10);
        brightness.setContentDescription("Launcher brightness");
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser) { prefs.edit().putInt("brightness", value + 10).apply(); applyBrightness(value + 10); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        rows.addView(brightness, new LinearLayout.LayoutParams(-1, dp(48)));
        if (tv) addPanelRow(rows, "Input · HDMI / Live TV", () -> showInputs());
        addPanelRow(rows, "Choose default Home", () -> launch(new Intent(this, HomeSettingsActivity.class)));
        addPanelRow(rows, "TV / device settings", () -> openSetting(Settings.ACTION_SETTINGS));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(rows);
        panel = new AlertDialog.Builder(this).setTitle("Quick settings").setView(scroll)
                .setNegativeButton("Close", null).create();
        panel.show();
        if (tv && panel.getWindow() != null) {
            panel.getWindow().setGravity(Gravity.END);
            panel.getWindow().setLayout(dp(370), -1);
        }
    }

    private void addPanelRow(LinearLayout parent, String label, Runnable action) {
        Button row = button(label, () -> { if (panel != null) panel.dismiss(); action.run(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(3), 0, dp(3));
        parent.addView(row, p);
    }

    private void launcherSettings() {
        String[] labels = {"App columns", "Profile name & photo", "Choose default Home"};
        new AlertDialog.Builder(this).setTitle("Launcher settings").setItems(labels, (d, index) -> {
            if (index == 0) {
                String[] options = {"Automatic", "2", "3", "4", "5", "6"};
                new AlertDialog.Builder(this).setTitle("App columns").setItems(options, (choice, which) -> {
                    prefs.edit().putInt(tv ? "tv_columns" : "phone_columns", which == 0 ? 0 : which + 1).apply();
                    if (cachedApps != null) renderApps(cachedApps);
                    else loadApps();
                }).show();
            } else if (index == 1) editProfile();
            else launch(new Intent(this, HomeSettingsActivity.class));
        }).show();
    }

    private void applyBrightness(int percent) {
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.screenBrightness = Math.max(.1f, Math.min(1f, percent / 100f));
        getWindow().setAttributes(p);
    }

    private void editProfile() {
        if (profile != null && profile.isShowing()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(8));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Your name");
        name.setText(prefs.getString("name", ""));
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(name);
        form.addView(button("Choose profile photo", () -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 7);
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, "No photo picker available on this device", Toast.LENGTH_LONG).show();
            }
        }));
        form.addView(button("Remove photo", () -> {
            File photo = new File(getFilesDir(), "personal-avatar.png");
            if (photo.exists() && !photo.delete()) {
                Toast.makeText(this, "Could not remove photo", Toast.LENGTH_SHORT).show();
                return;
            }
            avatarBitmap = null;
            refreshProfile();
        }));
        form.addView(text("Profile stays on this device. Google login is not used.", 13, MUTED));
        profile = new AlertDialog.Builder(this).setTitle(prefs.getBoolean("setup", false) ? "Your profile" : "Welcome · set up your Home")
                .setView(form).setPositiveButton("Save", null).setNegativeButton("Later", null).create();
        profile.setOnShowListener(d -> profile.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = PersonalHomeModel.name(name.getText().toString());
            if (value.isEmpty()) { name.setError("Enter your name"); return; }
            prefs.edit().putString("name", value).putBoolean("setup", true).apply();
            refreshProfile();
            profile.dismiss();
        }));
        profile.show();
    }

    private void refreshProfile() {
        String name = prefs.getString("name", "");
        userName.setText(name.isEmpty() ? "Your name" : name);
        if (avatarBitmap != null) avatar.setImageBitmap(avatarBitmap);
        else avatar.setImageResource(R.drawable.ic_person);
    }

    private void loadAvatar() {
        worker.execute(() -> {
            Bitmap image = BitmapFactory.decodeFile(new File(getFilesDir(), "personal-avatar.png").getPath());
            handler.post(() -> { if (!isDestroyed()) { avatarBitmap = image; refreshProfile(); } });
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 7 || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        worker.execute(() -> {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, options);
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) throw new java.io.IOException("Invalid image");
                options.inJustDecodeBounds = false;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
                Bitmap decoded;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    decoded = BitmapFactory.decodeStream(input, null, options);
                }
                if (decoded == null) throw new java.io.IOException("Invalid image");
                android.util.AtomicFile destination = new android.util.AtomicFile(new File(getFilesDir(), "personal-avatar.png"));
                FileOutputStream out = destination.startWrite();
                try {
                    if (!decoded.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new java.io.IOException("Cannot save image");
                    destination.finishWrite(out);
                } catch (Exception error) { destination.failWrite(out); throw error; }
                handler.post(() -> { if (!isDestroyed()) { avatarBitmap = decoded; refreshProfile(); } });
            } catch (Exception error) {
                handler.post(() -> { if (!isDestroyed()) Toast.makeText(this, "Could not read this photo. Choose another image.", Toast.LENGTH_LONG).show(); });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(tick);
        tick.run();
        updateNetwork();
        if (pageWidth > 0) loadApps();
        if (connectivity != null && !networkRegistered) {
            try { connectivity.registerDefaultNetworkCallback(networkCallback); networkRegistered = true; }
            catch (RuntimeException unavailable) { /* Snapshot above remains available. */ }
        }
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tick);
        if (connectivity != null && networkRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (panel != null) panel.dismiss();
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
        handleHomeAction(intent);
    }

    private void handleHomeAction(Intent intent) {
        if (intent == null) return;
        if (MainActivity.ACTION_VIEW_INPUTS.equals(intent.getAction())) handler.post(() -> showInputs());
        else if (MainActivity.ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) handler.post(() -> launcherSettings());
    }

    @Override public void onBackPressed() {
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        super.onDestroy();
    }

    /** One static draw when the minute changes; no frame loop, location permission or network. */
    private final class SkyClock extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap backdrop = BitmapFactory.decodeResource(getResources(), R.drawable.personal_wallpaper);
        int minute;
        SkyClock() { super(PersonalHomeActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float day = PersonalHomeModel.daylight(minute);
            int top = blend(0xff030b1c, 0xff145d85, day);
            int bottom = blend(0xff071a32, 0xff092b4a, day);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);
            if (backdrop != null) {
                float scale = Math.max(getWidth() / (float) backdrop.getWidth(), getHeight() / (float) backdrop.getHeight());
                float left = (getWidth() - backdrop.getWidth() * scale) / 2f;
                float height = backdrop.getHeight() * scale;
                canvas.drawBitmap(backdrop, null, new android.graphics.RectF(left, 0,
                        left + backdrop.getWidth() * scale, height), paint);
                canvas.drawColor(((int) (35 + 125 * (1 - day)) << 24) | 0x00051327);
            }
            float x = getWidth() / 2f, y = dp(tv ? 178 : 190), radius = dp(tv ? 40 : 36);
            boolean sun = minute >= 360 && minute < 1080;
            int glow = sun ? 0x60ffd78a : 0x504ac8ff;
            paint.setShader(new RadialGradient(x, y, radius * 2.5f, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, radius * 2.5f, paint);
            paint.setShader(null);
            paint.setColor(sun ? 0x90ffd68a : 0x9adcefff);
            if (sun) canvas.drawCircle(x, y, radius, paint);
            else {
                android.graphics.Path moon = new android.graphics.Path();
                moon.addCircle(x, y, radius, android.graphics.Path.Direction.CW);
                android.graphics.Path cut = new android.graphics.Path();
                cut.addCircle(x + radius * .45f, y - radius * .2f, radius * .86f, android.graphics.Path.Direction.CW);
                moon.op(cut, android.graphics.Path.Op.DIFFERENCE);
                canvas.drawPath(moon, paint);
            }
        }
    }

    private static int blend(int a, int b, float part) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * part),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * part),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * part));
    }
}

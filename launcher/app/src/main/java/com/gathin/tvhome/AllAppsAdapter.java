package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AllAppsAdapter extends RecyclerView.Adapter<AllAppsAdapter.Holder> {

    public interface OnAppSelectedListener {
        void onAppSelected(AppItem item);
    }

    public static class AppItem {
        public final String pkg;
        public final String label;
        public final ComponentName component;
        public final Drawable banner;
        public final Drawable icon;
        public final Bitmap smallIcon;
        public final boolean isLeanback;

        public AppItem(String pkg, String label, ComponentName component, Drawable banner,
                       Drawable icon, Bitmap smallIcon, boolean isLeanback) {
            this.pkg = pkg;
            this.label = label;
            this.component = component;
            this.banner = banner;
            this.icon = icon;
            this.smallIcon = smallIcon;
            this.isLeanback = isLeanback;
        }
    }

    private final Context ctx;
    private final float density;
    private final List<AppItem> items = new ArrayList<>();
    private final View tabApp;
    private final Map<String, Bitmap> smallIconsCache = new HashMap<>();
    private RecyclerView recyclerView;
    private boolean isAddToDockMode = false;
    private final Set<String> dockPackages = new HashSet<>();
    private OnAppSelectedListener onAppSelectedListener;

    public void setAddToDockMode(boolean addToDockMode) {
        this.isAddToDockMode = addToDockMode;
    }

    public boolean isAddToDockMode() {
        return isAddToDockMode;
    }

    public void setDockPackages(java.util.Collection<String> pkgs) {
        this.dockPackages.clear();
        if (pkgs != null) {
            this.dockPackages.addAll(pkgs);
        }
    }

    public void setOnAppSelectedListener(OnAppSelectedListener listener) {
        this.onAppSelectedListener = listener;
    }

    public int getFirstAvailablePosition() {
        for (int i = 0; i < items.size(); i++) {
            if (!dockPackages.contains(items.get(i).pkg)) {
                return i;
            }
        }
        return 0;
    }

    public AllAppsAdapter(Context ctx) {
        this(ctx, null);
    }

    public AllAppsAdapter(Context ctx, View tabApp) {
        this.ctx = ctx;
        this.tabApp = tabApp;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    public void focusPosition(int targetPos) {
        if (recyclerView == null || targetPos < 0 || targetPos >= items.size()) return;
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        View view = lm != null ? lm.findViewByPosition(targetPos) : null;
        if (view != null) {
            View card = view.findViewById(R.id.appCard);
            if (card != null) card.requestFocus();
            else view.requestFocus();
        } else {
            recyclerView.scrollToPosition(targetPos);
            recyclerView.post(() -> {
                RecyclerView.LayoutManager lmPost = recyclerView.getLayoutManager();
                View v = lmPost != null ? lmPost.findViewByPosition(targetPos) : null;
                if (v != null) {
                    View card = v.findViewById(R.id.appCard);
                    if (card != null) card.requestFocus();
                    else v.requestFocus();
                }
            });
        }
    }

    public int getAppCount() {
        return items.size();
    }

    public void reload() {
        smallIconsCache.clear();
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> leanback = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<ResolveInfo> standard = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);

        Set<String> leanbackPackages = new HashSet<>();
        for (ResolveInfo ri : leanback) {
            if (ri.activityInfo != null) leanbackPackages.add(ri.activityInfo.packageName);
        }

        Map<String, ResolveInfo> combined = new HashMap<>();
        for (ResolveInfo ri : leanback) {
            if (ri.activityInfo != null) combined.put(ri.activityInfo.packageName, ri);
        }
        for (ResolveInfo ri : standard) {
            if (ri.activityInfo != null && !combined.containsKey(ri.activityInfo.packageName)) {
                combined.put(ri.activityInfo.packageName, ri);
            }
        }

        List<AppItem> list = new ArrayList<>();
        for (ResolveInfo ri : combined.values()) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName())) continue;

            String label = String.valueOf(ri.loadLabel(pm));
            String lowerPkg = pkg.toLowerCase();
            String lowerLabel = label.toLowerCase();

            // Exclude unwanted OEM/system packages: copyDataToTV, Initial Setup, OTA, TCL_BT.., TV Settings, TVKitServer, User Center
            if (lowerPkg.startsWith("com.tcl.")
                    || lowerPkg.contains("copydatato")
                    || lowerPkg.contains("initsetup")
                    || lowerPkg.contains(".ota")
                    || lowerPkg.contains("tcl_bt")
                    || lowerPkg.contains("tvkit")
                    || lowerPkg.contains("usercenter")
                    || lowerPkg.contains("settings")
                    || lowerLabel.contains("copydatato")
                    || lowerLabel.contains("initial setup")
                    || lowerLabel.contains("ota")
                    || lowerLabel.contains("tcl_bt")
                    || lowerLabel.contains("tv settings")
                    || lowerLabel.contains("tvkit")
                    || lowerLabel.contains("user center")) {
                continue;
            }

            // Only apps installed from Google Play Store (or Play Store itself)
            String installer = null;
            try {
                installer = pm.getInstallerPackageName(pkg);
            } catch (Exception ignored) {}

            boolean isPlayStoreInstalled = "com.android.vending".equals(installer);
            boolean isPlayStoreItself = "com.android.vending".equals(pkg);
            boolean isUserInstalled = (ri.activityInfo.applicationInfo != null &&
                    (ri.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0);

            if (!isPlayStoreInstalled && !isPlayStoreItself && !isUserInstalled) {
                continue;
            }

            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner != null && banner.getIntrinsicWidth() <= banner.getIntrinsicHeight()) {
                banner = null;
            }
            Drawable icon = ri.loadIcon(pm);
            Bitmap smallIcon = getSmallIcon(pkg, label);
            boolean isLeanback = leanbackPackages.contains(pkg);

            list.add(new AppItem(pkg, label, component, banner, icon, smallIcon, isLeanback));
        }

        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));

        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    private Bitmap getSmallIcon(String pkg, String label) {
        if (smallIconsCache.containsKey(pkg)) return smallIconsCache.get(pkg);

        String pkgLower = pkg.toLowerCase();
        String labelLower = (label != null) ? label.toLowerCase() : "";
        List<String> keywords = new ArrayList<>();
        if (pkgLower.contains("youtube") || labelLower.contains("youtube")) keywords.add("youtube");
        else if (pkgLower.contains("apple") || labelLower.contains("apple")) {
            keywords.add("apple-tv-white");
            keywords.add("apple-tv-small");
            keywords.add("apple");
        }
        else if (pkgLower.contains("netflix") || labelLower.contains("netflix")) keywords.add("netflix");
        else if (pkgLower.contains("wbd.stream") || pkgLower.contains("hbo") || labelLower.contains("hbo") || labelLower.contains("max")) keywords.add("hbo");
        else if (pkgLower.contains("amazonvideo") || pkgLower.contains("prime") || labelLower.contains("prime")) keywords.add("prime");
        else if (pkgLower.contains("disney") || labelLower.contains("disney")) keywords.add("disney");
        else if (pkgLower.contains("appnovatica") || pkgLower.contains("ott") || labelLower.contains("ott")) keywords.add("ott");
        else if (pkgLower.contains("vending") || labelLower.contains("play store") || labelLower.contains("google play")) {
            keywords.add("play-store");
            keywords.add("play");
        }
        else if (pkgLower.contains("vidio") || labelLower.contains("vidio")) keywords.add("vidio");

        if (!keywords.isEmpty()) {
            File[] searchDirs = new File[] {
                    new File("/sdcard/Icons"),
                    new File("/sdcard/Pictures/Icons"),
                    new File(Environment.getExternalStorageDirectory(), "Icons"),
                    new File(ctx.getFilesDir(), "icons")
            };
            for (String kw : keywords) {
                for (File iconsDir : searchDirs) {
                    if (iconsDir != null && iconsDir.exists() && iconsDir.isDirectory()) {
                        File[] files = iconsDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                String name = f.getName().toLowerCase();
                                if (name.contains(kw) && (name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg"))) {
                                    try {
                                        Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath());
                                        if (bm != null) {
                                            smallIconsCache.put(pkg, bm);
                                            return bm;
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }
            }

            // Fallback to bundled assets/icons/
            try {
                String[] assetFiles = ctx.getAssets().list("icons");
                if (assetFiles != null) {
                    for (String kw : keywords) {
                        for (String af : assetFiles) {
                            String name = af.toLowerCase();
                            if (name.contains(kw) && (name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg"))) {
                                InputStream is = ctx.getAssets().open("icons/" + af);
                                Bitmap bm = BitmapFactory.decodeStream(is);
                                is.close();
                                if (bm != null) {
                                    smallIconsCache.put(pkg, bm);
                                    return bm;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        smallIconsCache.put(pkg, null);
        return null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_grid, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppItem item = items.get(position);
        h.name.setText(item.label);
        boolean inDock = isAddToDockMode && dockPackages.contains(item.pkg);

        if (item.smallIcon != null) {
            h.icon.setImageBitmap(item.smallIcon);
            h.icon.setVisibility(View.VISIBLE);
            h.banner.setVisibility(View.GONE);
        } else if (item.icon != null) {
            h.icon.setImageDrawable(item.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.banner.setVisibility(View.GONE);
        } else if (item.banner != null) {
            h.banner.setImageDrawable(item.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
        } else {
            h.banner.setVisibility(View.GONE);
            h.icon.setVisibility(View.GONE);
        }

        if (inDock) {
            h.card.setAlpha(0.35f);
            h.name.setTextColor(0x88FFFFFF);
        } else {
            h.card.setAlpha(1.0f);
            h.name.setTextColor(0xCCFFFFFF);
        }

        h.card.setOnFocusChangeListener((v, hasFocus) -> {
            boolean currentInDock = isAddToDockMode && dockPackages.contains(item.pkg);
            float s = hasFocus ? 1.08f : 1.0f;
            float tz = hasFocus ? 6f * density : 0f;
            h.card.animate().scaleX(s).scaleY(s).translationZ(tz).setDuration(120).start();
            if (currentInDock) {
                h.card.setAlpha(hasFocus ? 0.55f : 0.35f);
                h.name.setTextColor(hasFocus ? 0xCCFFFFFF : 0x88FFFFFF);
            } else {
                h.card.setAlpha(1.0f);
                h.name.setTextColor(hasFocus ? 0xFFFFFFFF : 0xCCFFFFFF);
            }
            if (hasFocus) {
                SoundManager.getInstance(ctx).playNavigation();
            }
        });

        h.card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || items.isEmpty()) return false;

            final int COLUMNS = 5;
            int row = pos / COLUMNS;
            int rowStart = row * COLUMNS;
            int rowEnd = Math.min(rowStart + COLUMNS - 1, items.size() - 1);

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (pos >= rowEnd) {
                        focusPosition(rowStart);
                    } else {
                        focusPosition(pos + 1);
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (pos <= rowStart) {
                        focusPosition(rowEnd);
                    } else {
                        focusPosition(pos - 1);
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_UP:
                    int upTarget = pos - COLUMNS;
                    if (upTarget >= 0) {
                        focusPosition(upTarget);
                    } else {
                        // At the top row: wrap to bottom in the same column
                        int col = pos % COLUMNS;
                        int totalRows = (items.size() + COLUMNS - 1) / COLUMNS;
                        int bottomTarget = (totalRows - 1) * COLUMNS + col;
                        if (bottomTarget >= items.size()) {
                            bottomTarget -= COLUMNS;
                        }
                        if (bottomTarget >= 0 && bottomTarget < items.size()) {
                            focusPosition(bottomTarget);
                        }
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    int downTarget = pos + COLUMNS;
                    if (downTarget < items.size()) {
                        focusPosition(downTarget);
                    } else if (row < (items.size() - 1) / COLUMNS) {
                        // There is a row below, but this column is past the end
                        focusPosition(items.size() - 1);
                    } else {
                        // At the end of apps / bottom row: wrap back to the top (row 0) in the same column!
                        int col = pos % COLUMNS;
                        if (col < items.size()) {
                            focusPosition(col);
                        }
                    }
                    return true;
            }
            return false;
        });

        h.card.setOnClickListener(v -> {
            if (isAddToDockMode) {
                if (dockPackages.contains(item.pkg)) {
                    Toast.makeText(ctx, item.label + " is already in the dock", Toast.LENGTH_SHORT).show();
                    return;
                }
                SoundManager.getInstance(ctx).playEnter();
                if (onAppSelectedListener != null) {
                    onAppSelectedListener.onAppSelected(item);
                }
                return;
            }

            SoundManager.getInstance(ctx).playEnter();
            Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(item.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (item.isLeanback) intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            try {
                ctx.startActivity(intent);
                if (ctx instanceof MainActivity) {
                    ((MainActivity) ctx).hideAllAppsModal();
                    ((MainActivity) ctx).setLastOpenedApp(item.pkg);
                }
            } catch (Exception e) {
                Toast.makeText(ctx, "Cannot open " + item.label, Toast.LENGTH_SHORT).show();
            }
        });

        h.card.setOnLongClickListener(v -> {
            if (isAddToDockMode) return true;
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + item.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { ctx.startActivity(i); } catch (Exception ignored) {}
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View card;
        final FrameLayout container;
        final ImageView banner;
        final ImageView icon;
        final TextView name;

        Holder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.appCard);
            container = itemView.findViewById(R.id.cardContainer);
            banner = itemView.findViewById(R.id.appBanner);
            icon = itemView.findViewById(R.id.appIcon);
            name = itemView.findViewById(R.id.appName);

            final float density = itemView.getResources().getDisplayMetrics().density;
            final float radius = 14f * density;
            container.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            container.setClipToOutline(true);
        }
    }
}

package com.norma.tvlauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.util.Log;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.SoundEffectConstants;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    private static final int LAUNCHER_SETTINGS_REQUEST = 42;
    /** Sent by QuickSettingsActivity: open Launcher settings from here so Home rebuilds when they close. */
    static final String ACTION_LAUNCHER_SETTINGS = "com.norma.tvlauncher.action.LAUNCHER_SETTINGS";
    static final String ACTION_TOGGLE_SETTINGS = "com.norma.tvlauncher.action.TOGGLE_SETTINGS";
    static final String ACTION_TOGGLE_INPUTS = "com.norma.tvlauncher.action.TOGGLE_INPUTS";

    private final BroadcastReceiver remoteKeyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            setResultCode(Activity.RESULT_OK);
            String action = intent.getAction();
            if (ACTION_TOGGLE_SETTINGS.equals(action)) {
                if (openPopup != null) {
                    openPopup.dismiss();
                    openPopup = null;
                } else {
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                    }
                    showSettingsMenu();
                }
            } else if (ACTION_TOGGLE_INPUTS.equals(action)) {
                if (openPopup != null) {
                    openPopup.dismiss();
                    openPopup = null;
                } else {
                    if (btnInputs != null) {
                        btnInputs.requestFocus();
                    }
                    showInputs();
                }
            }
        }
    };
    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private boolean rtlAppGrid;
    private static final long MINUTE_MS = 60_000L;
    private static final long STATS_MS = 10_000L;
    /**
     * 2025-01-01. TVs without a clock battery boot at the epoch (08:00 in China) and keep it until network
     * time arrives; the clock, date and greeting stay hidden before then.
     */
    private static final long TIME_VALID_AFTER_MS = 1_735_689_600_000L;
    private String liveTvPkg;
    /** Idle time on Home before ClockActivity; 0 is off (config.xml, home_idle_clock_minutes). */
    private long idleClockMs;
    private final Runnable showIdleClock = () -> startActivity(new Intent(this, ClockActivity.class));
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private WallpaperPlayer wallpaperPlayer;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    /** The top shelf: heroName, plus the cinematic shelf's "Last opened" line and dock position dots. */
    private View hero;
    private TextView heroMeta;
    private LinearLayout heroDots;
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    private boolean cinematic;
    /** config.xml ambient_glow, overridable by a theme. Off: the glow view is gone and no fade or colour animator
     *  is started for it, so a focus move costs nothing extra on a launcher that runs all day. */
    private boolean ambientGlow;
    private ImageView heroArt;
    private static final float HERO_ART_ALPHA = 0.5f;
    /** Softened artwork per app (HeroArt, about 5 KB each), made the first time its dock tile is focused. */
    private final Map<String, Bitmap> heroArtCache = new HashMap<>();
    /** The app whose artwork heroArt shows, so focusing it again only fades it back in. */
    private String heroArtKey;
    /** The focus glow's colour while the cinematic shelf tints it, 0 for plain white glass. */
    private int glassAccent;
    private int ambientColor = -1;
    private ValueAnimator ambientAnimator;
    private boolean heroShown;
    private boolean animations;
    private boolean advancedEffects;
    private TextView greeting;
    private View clockBlock;
    private TextView tabHome;
    private TextView tabApp;
    private RecyclerView appsGrid;
    private AllAppsAdapter allAppsAdapter;
    private boolean isAppsTab = false;
    private View weatherPill;
    private ImageView weatherIcon;
    private TextView weatherTemp;
    private TextView weatherDot;
    private TextView weatherCity;
    private View btnInputs;
    private TextView btnWifi;
    private View btnSettings;
    /** The network pill's icon as last set, so a refresh that changes nothing does not lay the top bar out again. */
    private int wifiIcon;
    /** The pills have been filled in once; later resumes refresh them after the first frame. */
    private boolean pillsShown;
    private TextView shamsiDate;
    private TextView stats;
    private List<RaceWeekend.Race> raceCalendar;
    private View racePanel;
    private TextView raceKicker, raceHeadline, raceSessions;
    /** config.xml show_stats, overridable by a theme. Off: the line is gone and nothing is read. */
    private boolean showStats;
    private boolean shamsi;
    /** greeting_name: said after the greeting, which is shown in capitals. */
    private String greetingName = "";
    /** Optional time-of-day backdrop (config.xml, sky_intensity); 0 keeps it hidden. */
    private SkyView sky;
    private String vpnApp;

    // ── App statistics on bottom right ────────────────────────────────────────
    private static class AppStats {
        final String totalTime;
        final String weeklyTime;
        final String storage;

        AppStats(String totalTime, String weeklyTime, String storage) {
            this.totalTime = totalTime;
            this.weeklyTime = weeklyTime;
            this.storage = storage;
        }
    }

    private View appStats;
    private TextView statWeeklyTime;
    private TextView statStorage;
    private String currentStatsPkg = "";
    private final Map<String, AppStats> appStatsCache = new HashMap<>();
    private final ExecutorService statsExecutor = Executors.newSingleThreadExecutor();

    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
    private int glassCardW;   // width of the tile the glass was last fitted to
    private boolean glassShown;
    private final Rect tileRect = new Rect();
    private final Rect revealRect = new Rect();
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** config settings_key_keycodes / settings_key_panel, so the Settings button works on Home without HomeKeyService. */
    private int[] settingsKeycodes = new int[0];
    private boolean settingsKeyPanel;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** config.xml dock_row: the first row is a separate dock (band, parked low, top shelf) rather than plain grid. */
    private boolean dockRow;
    /** Festive skin from the theme (see Festival); applied while it is active today. */
    private Festival festival = Festival.NONE;
    private boolean festive;
    private DiyaDrawable diya;
    private BellDrawable bell;
    private MarigoldDrawable marigold;
    private int greetingColor;
    /** The day the petal shower last played, so it greets the first Home of each day only. */
    private static LocalDate lastPetalShower;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private Dialog cardPrompt;
    private Dialog defaultLauncherDialog;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** Rebuild the tiles on resume: Home was fully hidden by an app, or Home was pressed. Not after a side panel such as Wi-Fi settings. */
    private boolean rebuildOnResume = true;
    /** Between onStop and onStart: Home was fully covered by an app, not just by the translucent quick panel. */
    private boolean stopped;
    /** Between onStart and onStop: Home is (at least partly) on screen. */
    private boolean started;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;
    /** Reorder mode is on (top-bar button). */
    private boolean reordering;
    /** Key of the tile being carried in reorder mode, or null. */
    private String movingKey;
    /** Tile order when reorder mode started, to save only when something moved. */
    private List<String> orderAtStart;
    private View btnReorder;
    /** dock_apps as loaded (config.xml or a theme): a saved Order only applies while these are unchanged. */
    private List<String> dockApps;
    /** fill_dock false: the dock can have empty slots, which reorder mode leaves out and restores afterwards. */
    private boolean fillDockLeavesSlots;
    /** Edit-dock mode (Settings menu): a reorder session that swaps one chosen app into one dock place. */
    private boolean dockEditing;
    /** Dock place being changed in edit-dock mode, or -1 while the place is still being chosen. */
    private int dockEditSlot = -1;
    private boolean dockRemoving;
    private AppLibraryDialog appLibrary;
    private boolean libraryFocusPending;
    private RecyclerView.OnScrollListener pendingFocusScroll;

    private FrameLayout allAppsModalOverlay;
    private ImageView allAppsBlurredBackdrop;
    private LinearLayout allAppsModalPanel;
    private TextView allAppsModalTitle;
    private TextView allAppsCountBadge;
    private RecyclerView allAppsModalGrid;
    private AllAppsAdapter allAppsModalAdapter;

    private FrameLayout fileBrowserOverlay;
    private ImageView fileBrowserBlurredBackdrop;
    private TextView fileBrowserTitle;
    private TextView fileBrowserPath;
    private TextView fileBrowserItemCount;
    private TextView fileBrowserEmptyText;
    private RecyclerView fileBrowserList;
    private FileBrowserAdapter fileBrowserAdapter;
    private String wallpaperTargetPkg;
    private String wallpaperTargetLabel;

    // ── Focused dock app wallpapers ───────────────────────────────────────────
    private static final Map<String, Integer> DOCK_WALLPAPERS = new HashMap<>();
    static {
        DOCK_WALLPAPERS.put("com.google.android.youtube.tv", R.drawable.wallpaper_youtube);
        DOCK_WALLPAPERS.put("com.apple.atve.androidtv.appletv", R.drawable.wallpaper_appletv);
        DOCK_WALLPAPERS.put("com.netflix.ninja", R.drawable.wallpaper_netflix);
        DOCK_WALLPAPERS.put("com.wbd.stream", R.drawable.wallpaper_hbomax);
        DOCK_WALLPAPERS.put("com.amazon.amazonvideo.livingroom", R.drawable.wallpaper_prime);
        DOCK_WALLPAPERS.put("com.disney.disneyplus", R.drawable.wallpaper_disney);
        DOCK_WALLPAPERS.put("com.vidio.android.tv", R.drawable.wallpaper_vidio);
        DOCK_WALLPAPERS.put("com.android.vending", R.drawable.wallpaper_googleplay);
        DOCK_WALLPAPERS.put("appnovatica.tv", R.drawable.wallpaper_ottnavigator);
        DOCK_WALLPAPERS.put("__all_apps__", R.drawable.wallpaper_top_bar);
    }
    private ImageView dockWallpaperA;
    private ImageView dockWallpaperB;
    private ValueAnimator kbAnimatorA;
    private ValueAnimator kbAnimatorB;
    private boolean dockHidden = false;
    private View wallpaperScrim;
    private View editModeScrim;
    private boolean wallpaperShowingA = false;
    private String currentWallpaperPkg = "";
    private String lastOpenedAppPackage = null;

    public void setLastOpenedApp(String pkg) {
        this.lastOpenedAppPackage = pkg;
    }

    private static final long WEATHER_REFRESH_INTERVAL_MS = 15 * 60 * 1000L; // 15 minutes (900 seconds)

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
            handler.post(() -> fetchWeatherAsync(false));
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadApps(false);
            if (appLibrary != null) appLibrary.refresh();
        }
    };
    // Right after boot the clock is often wrong until network time arrives; don't wait a minute to catch up.
    private final IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClockVisibility();
        }
    };

    private static boolean coldBootStartupPlayed = false;
    private static boolean wasScreenOff = false;

    private final IntentFilter screenFilter = new IntentFilter();
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            Log.d("TvHome", "screenStateReceiver action: " + action);
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                wasScreenOff = true;
            } else if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                triggerStartupSoundIfWaking();
            }
        }
    };

    private void triggerStartupSoundIfWaking() {
        if (wasScreenOff) {
            wasScreenOff = false;
            Log.i("TvHome", "TV turned on / waking from standby -> triggering startup sound");
            handler.postDelayed(() -> SoundManager.getInstance(MainActivity.this).playStartup(), 350);
        }
    }
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateRace();
            updateWifiLabel();
            checkWeatherAutoRefresh();
            sky.update();
            wallpaperPlayer.update();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    private void checkWeatherAutoRefresh() {
        SharedPreferences sp = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        long lastUpdate = sp.getLong("weather_last_ts", 0L);
        long now = System.currentTimeMillis();
        if (now - lastUpdate >= WEATHER_REFRESH_INTERVAL_MS) {
            fetchWeatherAsync(false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        settingsKeycodes = cfg.settingsKeycodes;
        settingsKeyPanel = cfg.settingsKeyPanel;
        COLUMNS = cfg.columns;
        animations = cfg.animations;
        advancedEffects = cfg.animations && cfg.advancedEffects;
        reducedMotion = cfg.reducedMotion;
        dockRow = cfg.dockRow;
        festival = cfg.festival;
        festive = festival.active(LocalDate.now());
        liveTvPkg = cfg.liveTvPackage;
        idleClockMs = cfg.homeIdleClockMinutes * MINUTE_MS;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        SoundManager.getInstance(this); // Pre-warm low-latency PS5 sound effects
        silenceSystemSoundEffects();
        getWindow().getDecorView().setSoundEffectsEnabled(false);
        disableViewSoundEffects(getWindow().getDecorView());

        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, screenFilter);
        }

        if (savedInstanceState == null && !coldBootStartupPlayed) {
            coldBootStartupPlayed = true;
            handler.postDelayed(() -> SoundManager.getInstance(MainActivity.this).playStartup(), 400);
        }

        root = findViewById(R.id.root);
        root.setBackgroundColor(cfg.backgroundColor);
        // root fills the window with an opaque colour (Config keeps background_color opaque), so the window's own
        // black under it is a second full-screen fill on every frame for nothing. The starting window still uses
        // the theme's background, so launch looks the same.
        getWindow().setBackgroundDrawable(null);
        wallpaperPlayer = new WallpaperPlayer(this, root, cfg);
        sky = findViewById(R.id.sky);
        sky.setIntensity(cfg.skyIntensity);
        glass = findViewById(R.id.glass);
        if (festive) glass.setAccent(festival.accentColor());
        ambient = findViewById(R.id.ambient);
        // The glow is one bitmap, so its alpha can be applied while drawing it. Otherwise every frame of
        // a fade renders it into a full-screen offscreen buffer first.
        ambient.forceHasOverlappingRendering(false);
        ambientGlow = cfg.ambientGlow;
        if (!ambientGlow) ambient.setVisibility(View.GONE);
        heroName = findViewById(R.id.heroName);
        hero = findViewById(R.id.hero);
        // The hero is now a group (name, and the cinematic lines): without this every fade of the default
        // shelf would render it through an offscreen buffer first, as the glow above notes.
        hero.forceHasOverlappingRendering(false);
        heroMeta = findViewById(R.id.heroMeta);
        heroDots = findViewById(R.id.heroDots);
        heroArt = findViewById(R.id.heroArt);
        cinematic = cfg.cinematicHome;
        if (cinematic) setUpCinematicShelf();
        // dock_title false: the name above the dock (and the cinematic lines under it) never shows; the glow
        // still follows the dock.
        if (!cfg.dockTitle) hero.setVisibility(View.GONE);

        // ── Focused dock app wallpaper setup ────────────────────────────────────
        dockWallpaperA = findViewById(R.id.dockWallpaperA);
        dockWallpaperB = findViewById(R.id.dockWallpaperB);
        wallpaperScrim = findViewById(R.id.wallpaperScrim);
        editModeScrim = findViewById(R.id.editModeScrim);

        appStats = findViewById(R.id.appStats);
        statWeeklyTime = findViewById(R.id.statWeeklyTime);
        statStorage = findViewById(R.id.statStorage);

        greeting = findViewById(R.id.greeting);
        clockBlock = findViewById(R.id.leftBlock);
        greetingColor = greeting.getCurrentTextColor();
        btnWifi = findViewById(R.id.btnWifi);
        shamsiDate = findViewById(R.id.shamsiDate);
        stats = findViewById(R.id.stats);
        raceCalendar = cfg.raceCalendar;
        racePanel = findViewById(R.id.racePanel);
        raceKicker = findViewById(R.id.raceKicker);
        raceHeadline = findViewById(R.id.raceHeadline);
        raceSessions = findViewById(R.id.raceSessions);
        showStats = cfg.showStats;
        shamsi = cfg.shamsiDate;
        greetingName = cfg.greetingName;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        rtlAppGrid = getResources().getBoolean(R.bool.rtl_app_grid);
        // Horizontal layout for the PS5 dock row on the left side
        grid.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        grid.setItemAnimator(null);
        // Tiles that scroll out while one is carried come back from the view cache without a rebind; set their
        // dimming again whenever one attaches, so none stays dim after the drop.
        grid.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View v) {
                v.setAlpha(tileAlpha(grid.getChildAdapterPosition(v)));
                disableViewSoundEffects(v);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View v) {
            }
        });
        if (dockRow) grid.addItemDecoration(new DockDecoration(COLUMNS, density, cinematic, cfg.alwaysShowNames));
        if (animations) {
            int enterAnim = reducedMotion ? R.anim.tile_enter_reduced : R.anim.tile_enter;
            float enterDelay = reducedMotion ? 0f : 0.06f;
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, enterAnim), enterDelay));
        }
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        dockApps = cfg.dockApps;
        fillDockLeavesSlots = !cfg.fillDock;
        adapter.setSavedOrder(Order.ranks(Order.load(Order.file(this)), dockApps));
        grid.setAdapter(adapter);

        FrameLayout.LayoutParams gridLp = (FrameLayout.LayoutParams) grid.getLayoutParams();
        if (gridLp != null) {
            gridLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            gridLp.gravity = Gravity.START | Gravity.TOP;
            grid.setLayoutParams(gridLp);
        }

        // PS5 layout: row is at the top under the first layer container
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int want = (int) (14 * density);
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
            grid.post(this::refitGlass);
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                if (!dockRow || lastFocused >= COLUMNS) {
                    glassTargetX -= dx;
                    glass.setTranslationX(glass.getTranslationX() - dx);
                }
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glass.setTranslationY(glass.getTranslationY() - dy);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (!dockRow || lastFocused >= COLUMNS) {
                        refitGlass();
                    }
                }
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (reducedMotion) {
                // Stiff and almost critically damped: settles in a few frames instead of oscillating,
                // and stops once the highlight is within 1.5 px of its tile.
                s.setSpring(new SpringForce().setStiffness(1400f).setDampingRatio(0.95f));
                s.setMinimumVisibleChange(1.5f);
            } else {
                s.setSpring(new SpringForce().setStiffness(advancedEffects ? 320f : 260f)
                        .setDampingRatio(advancedEffects ? 0.76f : 0.58f));
            }
        }

        tabHome = findViewById(R.id.tabHome);
        tabApp = findViewById(R.id.tabApp);
        updateTabStyles();
        appsGrid = findViewById(R.id.appsGrid);
        if (appsGrid != null) {
            appsGrid.setLayoutManager(new GridLayoutManager(this, 5));
            final float gridDensity = getResources().getDisplayMetrics().density;
            final int hGap = Math.round(14 * gridDensity);
            final int vGap = Math.round(14 * gridDensity);
            appsGrid.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    outRect.set(hGap, vGap, hGap, vGap);
                }
            });
            allAppsAdapter = new AllAppsAdapter(this, tabApp);
            appsGrid.setAdapter(allAppsAdapter);
            allAppsAdapter.reload();
        }

        allAppsModalOverlay = findViewById(R.id.allAppsModalOverlay);
        allAppsBlurredBackdrop = findViewById(R.id.allAppsBlurredBackdrop);
        allAppsModalPanel = findViewById(R.id.allAppsModalPanel);
        allAppsModalTitle = findViewById(R.id.allAppsModalTitle);
        allAppsCountBadge = findViewById(R.id.allAppsCountBadge);
        allAppsModalGrid = findViewById(R.id.allAppsModalGrid);
        if (allAppsModalGrid != null) {
            allAppsModalGrid.setLayoutManager(new GridLayoutManager(this, 5));
            final float gDensity = getResources().getDisplayMetrics().density;
            final int hGap = Math.round(8 * gDensity);
            final int vGap = Math.round(8 * gDensity);
            allAppsModalGrid.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    outRect.set(hGap, vGap, hGap, vGap);
                }
            });
            allAppsModalAdapter = new AllAppsAdapter(this);
            allAppsModalGrid.setAdapter(allAppsModalAdapter);
        }
        if (allAppsModalOverlay != null) {
            allAppsModalOverlay.setOnClickListener(v -> hideAllAppsModal());
        }

        fileBrowserOverlay = findViewById(R.id.fileBrowserOverlay);
        fileBrowserBlurredBackdrop = findViewById(R.id.fileBrowserBlurredBackdrop);
        fileBrowserTitle = findViewById(R.id.fileBrowserTitle);
        fileBrowserPath = findViewById(R.id.fileBrowserPath);
        fileBrowserItemCount = findViewById(R.id.fileBrowserItemCount);
        fileBrowserEmptyText = findViewById(R.id.fileBrowserEmptyText);
        fileBrowserList = findViewById(R.id.fileBrowserList);
        if (fileBrowserList != null) {
            fileBrowserList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
            fileBrowserAdapter = new FileBrowserAdapter(this);
            fileBrowserList.setAdapter(fileBrowserAdapter);
            fileBrowserAdapter.setListener(new FileBrowserAdapter.OnFileBrowserListener() {
                @Override
                public void onImageSelected(File imageFile) {
                    applyCustomDockWallpaper(wallpaperTargetPkg, wallpaperTargetLabel, imageFile);
                    hideFileBrowser();
                }

                @Override
                public void onRestoreDefault() {
                    restoreDefaultDockWallpaper(wallpaperTargetPkg, wallpaperTargetLabel);
                    hideFileBrowser();
                }

                @Override
                public void onDirectoryChanged(File dir, int itemCount) {
                    if (fileBrowserPath != null) {
                        fileBrowserPath.setText(dir != null ? dir.getAbsolutePath() : "/sdcard");
                    }
                    if (fileBrowserItemCount != null) {
                        fileBrowserItemCount.setText(itemCount + " " + (itemCount == 1 ? "item" : "items"));
                    }
                    if (fileBrowserEmptyText != null) {
                        fileBrowserEmptyText.setVisibility(itemCount == 0 ? View.VISIBLE : View.GONE);
                    }
                }
            });
        }
        if (fileBrowserOverlay != null) {
            fileBrowserOverlay.setOnClickListener(v -> handleFileBrowserBack());
        }

        if (tabHome != null) {
            tabHome.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) selectHomeTab();
            });
            tabHome.setOnClickListener(v -> selectHomeTab());
            tabHome.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusTile(dockReturnPosition());
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (tabApp != null) tabApp.requestFocus();
                    return true;
                }
                return false;
            });
        }

        if (tabApp != null) {
            tabApp.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) selectAppsTab();
            });
            tabApp.setOnClickListener(v -> {
                selectAppsTab();
                if (appsGrid != null && appsGrid.getChildCount() > 0) {
                    appsGrid.getChildAt(0).requestFocus();
                }
            });
            tabApp.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (appsGrid != null && appsGrid.getChildCount() > 0) {
                        appsGrid.getChildAt(0).requestFocus();
                    } else if (appsGrid != null) {
                        appsGrid.requestFocus();
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (tabHome != null) tabHome.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    btnInputs.requestFocus();
                    return true;
                }
                return false;
            });
        }

        btnInputs = findViewById(R.id.btnInputs);
        btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> showWifiMenu());
        btnWifi.setOnLongClickListener(v -> {
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> showSettingsMenu());
        btnReorder = findViewById(R.id.btnReorder);
        btnReorder.setOnClickListener(v -> toggleReorder());
        btnReorder.setVisibility(cfg.reorderButton ? View.VISIBLE : View.GONE);
        if (!cfg.showNetworkPill) btnWifi.setVisibility(View.GONE);
        btnLibraryTop = findViewById(R.id.btnLibraryTop);
        btnLibraryTop.setOnClickListener(v -> {
            onLibraryRequested();
            libraryFromTopBar = true;   // closing it goes back to this button, not to the pill after the last row
        });
        btnLibraryTop.setVisibility(cfg.libraryButton && cfg.appLibrary ? View.VISIBLE : View.GONE);
        makeIconOnly((TextView) btnInputs);
        makeIconOnly(btnWifi);
        makeIconOnly((TextView) btnSettings);
        wifiIconOnly = true;
        btnSettings.setOnLongClickListener(v -> {
            showSettingsMenu();
            return true;
        });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        weatherPill = findViewById(R.id.weatherPill);
        weatherIcon = findViewById(R.id.weatherIcon);
        weatherTemp = findViewById(R.id.weatherTemp);
        weatherDot = findViewById(R.id.weatherDot);
        weatherCity = findViewById(R.id.weatherCity);

        boolean rtlMenuBar = getResources().getBoolean(R.bool.rtl_menu_bar);
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                SoundManager.getInstance(MainActivity.this).playBack();
                focusTile(dockReturnPosition());
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                focusTile(dockReturnPosition());
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (v.getId() == R.id.weatherPill) {
                    int lastDockPos = Math.max(0, Math.min(adapter.dockFilled() - 1, COLUMNS - 1));
                    focusTile(lastDockPos);
                    return true;
                }
                if (v.getId() == R.id.btnInputs && weatherPill != null && weatherPill.getVisibility() == View.VISIBLE) {
                    weatherPill.requestFocus();
                    return true;
                }
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (v.getId() == R.id.weatherPill) {
                    View rightTarget = btnInputs != null && btnInputs.getVisibility() == View.VISIBLE ? btnInputs : firstTopButton();
                    if (rightTarget != null) rightTarget.requestFocus();
                    return true;
                }
                if (v.getId() == R.id.btnSettings) return true;
                return false;
            }
            return false;
        };
        if (weatherPill != null) {
            weatherPill.setOnKeyListener(pillKeys);
            weatherPill.setOnClickListener(v -> showWeatherModal());
        }
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        btnReorder.setOnKeyListener(pillKeys);
        btnLibraryTop.setOnKeyListener(pillKeys);

        SharedPreferences spWeather = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        String cachedTz = spWeather.getString("weather_timezone", "Asia/Jakarta");
        syncTimezone(cachedTz);

        fetchWeatherAsync(false);
        for (View button : new View[]{weatherPill, btnInputs, btnWifi, btnReorder, btnLibraryTop, btnSettings}) {
            if (button == null) continue;
            button.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    setPillHighlighted(view, true);
                } else {
                    boolean popupOpen = (openPopup != null && view.isSelected());
                    if (!popupOpen) {
                        view.setSelected(false);
                        setPillHighlighted(view, false);
                    }
                }
            });
        }
        if (festive) applyFestiveSkin((TextView) btnInputs, (TextView) btnSettings);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
        // Not after recreate(): the launch intent survives it and would open the settings again.
        if (savedInstanceState == null) openLauncherSettingsIfAsked(getIntent());
        grid.post(() -> focusTile(adapter.appCount() > 1 ? 1 : 0));
        checkAndPromptDefaultLauncher();
    }

    private boolean openLauncherSettingsIfAsked(Intent intent) {
        if (intent == null || !ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) return false;
        setIntent(new Intent(Intent.ACTION_MAIN));
        startActivityForResult(new Intent(this, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST);
        return true;
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            if (ACTION_TOGGLE_SETTINGS.equals(intent.getAction())) {
                if (openPopup != null) {
                    openPopup.dismiss();
                    openPopup = null;
                } else {
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                    }
                    showSettingsMenu();
                }
                return;
            }
            if (ACTION_TOGGLE_INPUTS.equals(intent.getAction())) {
                if (openPopup != null) {
                    openPopup.dismiss();
                    openPopup = null;
                } else {
                    if (btnInputs != null) {
                        btnInputs.requestFocus();
                    }
                    showInputs();
                }
                return;
            }
        }
        if (openLauncherSettingsIfAsked(intent)) return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        currentWallpaperPkg = "";

        // Return focus to the latest opened app if available!
        if (lastOpenedAppPackage != null) {
            int pos = adapter.positionOf(lastOpenedAppPackage);
            if (pos >= 0) {
                lastFocused = pos;
                lastFocusedKey = lastOpenedAppPackage;
            }
        }

        if (stopped) {
            rebuildOnResume = true;
        } else {
            final int posToFocus = lastFocused;
            grid.post(() -> focusTile(posToFocus));
        }
    }

    void scrollToTop() {
        grid.scrollToPosition(0);
        grid.post(() -> focusTile(0));
    }

    private void updateTabStyles() {
        if (tabHome != null) {
            tabHome.setSelected(!isAppsTab);
        }
        if (tabApp != null) {
            tabApp.setSelected(isAppsTab);
        }
    }

    private void selectHomeTab() {
        if (!isAppsTab) return;
        isAppsTab = false;
        updateTabStyles();
        if (grid != null) grid.setVisibility(View.VISIBLE);
        if (appsGrid != null) appsGrid.setVisibility(View.GONE);
        if (hero != null) hero.setVisibility(View.VISIBLE);
        if (appStats != null) appStats.setVisibility(View.VISIBLE);
        View wallpaperScrim = findViewById(R.id.wallpaperScrim);
        if (wallpaperScrim != null) wallpaperScrim.setAlpha(1.0f);
        currentWallpaperPkg = "";
        int pos = lastFocused % COLUMNS;
        updateDockWallpaper(adapter.packageAt(pos), pos);
        updateAppStats(adapter.packageAt(pos), false);
        hideGlass();
        focusTile(dockReturnPosition());
    }

    private void selectAppsTab() {
        if (isAppsTab) return;
        isAppsTab = true;
        updateTabStyles();
        if (grid != null) grid.setVisibility(View.GONE);
        if (appsGrid != null) appsGrid.setVisibility(View.VISIBLE);
        if (hero != null) hero.setVisibility(View.GONE);
        if (appStats != null) appStats.setVisibility(View.GONE);
        hideGlass();
        setSpecificWallpaper(R.drawable.wallpaper_list_of_apps);
        if (allAppsAdapter != null) allAppsAdapter.reload();
    }

    private void setSpecificWallpaper(int resId) {
        if (dockWallpaperA == null || dockWallpaperB == null) return;
        currentWallpaperPkg = "__specific__" + resId;
        final ImageView incoming = wallpaperShowingA ? dockWallpaperB : dockWallpaperA;
        final ImageView outgoing = wallpaperShowingA ? dockWallpaperA : dockWallpaperB;
        wallpaperShowingA = !wallpaperShowingA;

        incoming.setImageResource(resId);
        incoming.animate().cancel();
        outgoing.animate().cancel();
        if (wallpaperScrim != null) wallpaperScrim.animate().cancel();

        incoming.setAlpha(0f);
        incoming.setVisibility(View.VISIBLE);
        if (wallpaperScrim != null) {
            wallpaperScrim.setVisibility(View.VISIBLE);
            wallpaperScrim.setAlpha(0.65f);
        }

        if (animations && !reducedMotion) {
            incoming.animate().alpha(1f).setDuration(450)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            outgoing.animate().alpha(0f).setDuration(450).start();
        } else {
            incoming.setAlpha(1f);
            outgoing.setAlpha(0f);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        stopped = false;
        started = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(timeReceiver, timeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
            registerReceiver(timeReceiver, timeFilter);
        }
        updateClockVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        triggerStartupSoundIfWaking();
        silenceSystemSoundEffects();
        disableViewSoundEffects(getWindow().getDecorView());
        // A festival that started or ended while Home was in the background: rebuild with or without it.
        if (festive != festival.active(LocalDate.now())) {
            recreate();
            return;
        }
        restartIdleClock();
        promptsPaused = false;
        if (grid.getAdapter() == null) grid.setAdapter(adapter);   // dropped by onTrimMemory
        if (rebuildOnResume) {
            tilesReady = false;
            reloadApps(true);
        }
        rebuildOnResume = false;
        updateGreeting();
        updateRace();
        if (allAppsAdapter != null) allAppsAdapter.reload();
        // The network and Inputs pills keep their state while Home is in the background, and callbacks update them
        // when the network or the inputs change, so this refresh only catches anything missed. Its binder calls
        // (about 7 ms on a TCL C755, most of it WifiManager.getConnectionInfo) would hold up the first frame back
        // on Home, so after the first resume it runs once that frame is drawn.
        if (pillsShown) {
            refreshPillsAfterNextDraw();
        } else {
            updateWifiLabel();
            updateInputsButton();
            pillsShown = true;
        }
        if (showStats) startStats();
        sky.update();
        wallpaperPlayer.start();
        if (isDefaultLauncher()) {
            if (defaultLauncherDialog != null && defaultLauncherDialog.isShowing()) {
                defaultLauncherDialog.dismiss();
                defaultLauncherDialog = null;
                Toast.makeText(this, R.string.default_launcher_success, Toast.LENGTH_SHORT).show();
            }
        }
        // Ensure all top pills are clean and unfocused when returning to Home
        for (View b : new View[]{weatherPill, btnInputs, btnWifi, btnReorder, btnLibraryTop, btnSettings}) {
            if (b != null) {
                b.setSelected(false);
                setPillHighlighted(b, false);
            }
        }
        // Force wallpaper sync with the currently focused dock app on resume
        if (dockRow && adapter != null && adapter.appCount() > 0) {
            int pos = lastFocused % COLUMNS;
            String pkg = adapter.packageAt(pos);
            if (pkg != null) {
                updateDockWallpaper(pkg, pos, true);
            }
            grid.post(() -> focusTile(pos));
        }
        handler.postDelayed(minuteTick, MINUTE_MS);
        IntentFilter remoteKeysFilter = new IntentFilter();
        remoteKeysFilter.addAction(ACTION_TOGGLE_SETTINGS);
        remoteKeysFilter.addAction(ACTION_TOGGLE_INPUTS);
        registerReceiver(remoteKeyReceiver, remoteKeysFilter);
        if (festive && diya != null) {
            diya.start();
            LocalDate today = LocalDate.now();
            if (!reducedMotion && !today.equals(lastPetalShower)) {
                lastPetalShower = today;
                handler.postDelayed(this::playPetalShower, 450);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LAUNCHER_SETTINGS_REQUEST && resultCode == RESULT_OK) recreate();
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        cancelPendingFocusScroll();
        RecyclerView.ViewHolder footer = grid.findViewHolderForAdapterPosition(adapter.appCount());
        final boolean restoreLibrary = adapter.isLibraryAt(adapter.appCount()) && footer != null
                && footer.itemView.findViewById(R.id.btnLibrary) != null
                && footer.itemView.findViewById(R.id.btnLibrary).hasFocus();
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = appLibrary == null && (animate || grid.hasFocus());
        if (restoreFocus) {
            glassShown = false;
            glass.setAlpha(0f);
            heroShown = false;
            hero.setAlpha(0f);
            ambient.setAlpha(0f);
            heroArt.animate().cancel();
            heroArt.setAlpha(0f);
        }
        reloadTiles();
        prewarmAppStats();
        if (animate && animations && !reducedMotion) grid.scheduleLayoutAnimation();

        if (restoreFocus) {
            // Keep a focused footer at the end even when the app count changes.
            if (restoreLibrary) grid.post(this::focusLibrary);
            else restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.appCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private void reloadTiles() {
        if (reordering) endReorder();   // an app installed or removed while reordering: keep the order so far
        Config cfg = Config.load(this);
        if (cfg != null && cfg.dockApps != null) {
            dockApps = cfg.dockApps;
            adapter.setFavourites(dockApps);
        }
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        // An updated app may bring new artwork; the backdrop is cheap to make again.
        heroArtCache.clear();
        heroArtKey = null;
        // One LEANBACK query for both the tiles and the card checks.
        final List<ResolveInfo> leanback = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps,
                () -> Cards.load(Cards.storeFile(this)), leanback), none) : none;
        adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none), leanback);
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final boolean returnToLibrary = libraryFocusPending;
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            if (returnToLibrary || libraryFocusPending) focusLibrary();
            else focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            showCardPrompt();
        });
    }

    /** Any key on Home restarts the countdown to the idle clock. */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        restartIdleClock();
    }

    private void restartIdleClock() {
        root.removeCallbacks(showIdleClock);
        if (idleClockMs > 0) root.postDelayed(showIdleClock, idleClockMs);
    }

    @Override
    protected void onPause() {
        super.onPause();
        root.removeCallbacks(showIdleClock);
        endReorder();   // Home or another app while reordering keeps the order so far
        endDockRemove();
        wallpaperPlayer.stop();
        stopAmbientAnimation();
        hero.animate().cancel();
        ambient.animate().cancel();
        heroArt.animate().cancel();
        if (dockWallpaperA != null) dockWallpaperA.animate().cancel();
        if (dockWallpaperB != null) dockWallpaperB.animate().cancel();
        handler.removeCallbacks(minuteTick);
        stopStats();
        stopKenBurnsEffect();
        if (diya != null) diya.stop();
        cancelPendingFocusScroll();
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (appLibrary != null) appLibrary.close();
        if (openPopup != null) openPopup.dismiss();
        try {
            unregisterReceiver(remoteKeyReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        unregisterReceiver(packageReceiver);
        unregisterReceiver(timeReceiver);
        handler.removeCallbacks(clockCheck);
        stopped = true;
        rebuildOnResume = true;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            wasScreenOff = true;
        }
    }

    /**
     * Another app needs the memory while Home is in the background: let go of every tile's artwork, and
     * rebuild it when Home comes back. Not at UI_HIDDEN, the ordinary trip to an app, where keeping the
     * tiles is what makes the return instant.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // RUNNING_LOW and CRITICAL also arrive while Home is visible when an accessibility service keeps the
        // process in the foreground; never blank the screen the owner is looking at.
        boolean due = level >= TRIM_MEMORY_BACKGROUND
                || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL;
        if (!due || started) return;
        adapter.clear();
        // Detach the adapter too: with Home stopped no layout pass runs, so the tile views (and the bitmaps
        // they hold) would stay attached until Home comes back. setAdapter(null) recycles them now.
        grid.setAdapter(null);
        grid.getRecycledViewPool().clear();
        heroArtCache.clear();
        heroArtKey = null;
        rebuildOnResume = true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (libraryFocusPending && appLibrary == null) focusLibrary();
            showCardPrompt();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (Exception ignored) {}
        SoundManager.getInstance(this).release();
        statsExecutor.shutdown();
        wallpaperPlayer.close();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    /**
     * A Settings button sent as a keycode (settings_key_keycodes, e.g. 83 on TCL Google TV remotes) opens the same
     * thing HomeKeyService would. While that service runs it consumes the key first; without it (not enabled,
     * or switched off by a force-stop) the stock launcher that used to handle the key is gone, and the button did
     * nothing even on Home.
     */
    private boolean handleSettingsKey(KeyEvent event) {
        for (int code : settingsKeycodes) {
            if (event.getKeyCode() != code) continue;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                open(settingsKeyPanel ? new Intent(this, QuickSettingsActivity.class)
                        : systemIntent(Settings.ACTION_SETTINGS));
            }
            return true;
        }
        return false;
    }

    // ---- reorder ---------------------------------------------------------------------------------

    /** Blue the glass turns while reorder mode is on. */
    private static final int REORDER_ACCENT = 0xFF6EC1FF;

    /**
     * Reorder mode (top-bar button). While it is on, every key comes here first:
     * - nothing carried: arrows move focus as usual; OK on a tile picks it up instead of opening the app;
     *   Back ends the mode and saves the order.
     * - a tile carried: arrows move it; Back (or OK) puts it down and the mode stays on for the next one.
     * Other keys (volume, Home) pass through; leaving the launcher ends the mode and saves.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        SoundManager.getInstance(this).handleKeyEvent(event);
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_TV_INPUT || code == KeyEvent.KEYCODE_TV || code == 178 || code == 170) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (openPopup != null) {
                    openPopup.dismiss();
                    openPopup = null;
                } else {
                    if (btnInputs != null) {
                        btnInputs.requestFocus();
                    }
                    showInputs();
                }
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_SETTINGS
                || code == KeyEvent.KEYCODE_MENU
                || code == 83 /* TCL Google TV remote settings button */
                || code == 4077 /* TCL/Realtek remote settings button */
                || code == 4020
                || code == 4056) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (openPopup != null) {
                    openPopup.dismiss();
                    openPopup = null;
                } else {
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                    }
                    showSettingsMenu();
                }
            }
            return true;
        }
        if (fileBrowserOverlay != null && fileBrowserOverlay.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    handleFileBrowserBack();
                }
                return true;
            }
        }
        if (allAppsModalOverlay != null && allAppsModalOverlay.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    hideAllAppsModal();
                }
                return true;
            }
        }
        if (dockRemoving) {
            return dispatchDockRemove(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if ((code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) && isAppsTab) {
                selectHomeTab();
                return true;
            }
        }
        if (handleSettingsKey(event)) return true;
        if (!reordering) return super.dispatchKeyEvent(event);
        if (dockEditing) return dispatchDockEdit(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (movingKey != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) moveBy(code);
                    return true;
                default:
                    if (ok || back) {
                        if (!down) drop();
                        return true;
                    }
                    return super.dispatchKeyEvent(event);
            }
        }
        if (back) {
            if (!down) endReorder();
            return true;
        }
        int pos = focusedTilePosition();
        if (ok && grid.hasFocus()) {
            // RecyclerView can briefly report NO_POSITION during a move or rebind. Still consume
            // OK on the grid: falling through would let the focused card open its app in reorder mode.
            if (!down && pos >= 0) pickUp(pos);
            return true;
        }
        return super.dispatchKeyEvent(event);   // arrows navigate; OK on a top-bar pill works as usual
    }

    /** Adapter position of the focused tile, or -1 when focus is not on a tile. */
    private int focusedTilePosition() {
        View f = getCurrentFocus();
        if (f == null || f.getId() != R.id.card || !(f.getParent() instanceof View)) return -1;
        return grid.getChildAdapterPosition((View) f.getParent());
    }

    /** The reorder button: turns the mode on (focus goes back to the last tile), or off again. */
    private void toggleReorder() {
        if (reordering) {
            endReorder();
            return;
        }
        if (!beginReorder()) return;
        focusTile(Math.max(0, Math.min(lastFocused, adapter.movableCount() - 1)));
        Toast.makeText(this, R.string.reorder_hint, Toast.LENGTH_LONG).show();
    }

    private void showEditModeScrim() {
        if (editModeScrim == null) return;
        editModeScrim.setVisibility(View.VISIBLE);
        if (animations && !reducedMotion) {
            editModeScrim.animate().cancel();
            editModeScrim.animate().alpha(1f).setDuration(240).start();
        } else {
            editModeScrim.setAlpha(1f);
        }
    }

    private void hideEditModeScrim() {
        if (editModeScrim == null) return;
        if (animations && !reducedMotion) {
            editModeScrim.animate().cancel();
            editModeScrim.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                editModeScrim.setVisibility(View.GONE);
            }).start();
        } else {
            editModeScrim.setAlpha(0f);
            editModeScrim.setVisibility(View.GONE);
        }
    }

    /** The start shared by reorder and edit-dock mode. False when there is nothing to move. */
    private boolean beginReorder() {
        if (adapter.movableCount() < 2) return false;
        adapter.setReordering(true);
        if (adapter.hasEmptySlots()) reloadTiles();   // every position must hold a tile that can move
        reordering = true;
        orderAtStart = adapter.movableKeys();
        glass.setAccent(REORDER_ACCENT);
        btnReorder.setActivated(true);
        showEditModeScrim();
        return true;
    }

    /** Edit dock (Settings menu): focus goes to the first dock place, which OK then chooses. */
    private void startDockEdit() {
        if (reordering) endReorder();
        if (!beginReorder()) return;
        dockEditing = true;
        dockEditSlot = -1;
        focusTile(0);
        Toast.makeText(this, R.string.dock_edit_hint, Toast.LENGTH_LONG).show();
    }

    /**
     * Keys in edit-dock mode. First OK on a dock tile chooses that place (the other dock tiles dim) and focus moves
     * to the grid; then OK on any tile swaps that app into the place, saves the order and ends the mode. Back goes
     * one step back: from choosing the app to choosing the place, and from there out of the mode.
     */
    private boolean dispatchDockEdit(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (back) {
            if (!down) {
                if (dockEditSlot >= 0) {
                    int slot = dockEditSlot;
                    dockEditSlot = -1;
                    dimOthers();
                    focusTile(slot);
                } else {
                    endReorder();
                }
            }
            return true;
        }
        int pos = focusedTilePosition();
        if (ok && pos >= 0) {
            if (!down) {
                int n = adapter.movableCount();
                if (dockEditSlot < 0) {
                    if (pos < Math.min(COLUMNS, n)) {
                        dockEditSlot = pos;
                        dimOthers();
                        focusTile(Math.min(COLUMNS, n - 1));
                        Toast.makeText(this, R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show();
                    }
                } else if (pos < n) {
                    swapIntoDock(dockEditSlot, pos);
                }
            }
            return true;   // never opens the app while editing
        }
        return super.dispatchKeyEvent(event);   // arrows navigate as usual
    }

    /** Puts the app at pos into dock place slot, and the app that was there where the chosen one was; then saves. */
    private void swapIntoDock(int slot, int pos) {
        String slotKey = adapter.keyAt(slot);
        if (pos != slot) {
            keepScroll(() -> {
                adapter.move(pos, slot);
                adapter.move(adapter.positionOf(slotKey), pos);
            });
        }
        endReorder();
        refocusAfterLayout(slot);
    }

    private void pickUp(int pos) {
        if (pos >= adapter.movableCount()) return;   // the "Default home" tile stays put
        movingKey = adapter.keyAt(pos);
        dimOthers();
    }

    /** Puts the carried tile down where it is; the mode stays on. */
    private void drop() {
        movingKey = null;
        dimOthers();
    }

    /** While a tile is carried, every other tile is dimmed so the carried one stands out; otherwise all are full. */
    private void dimOthers() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View item = grid.getChildAt(i);
            item.setAlpha(tileAlpha(grid.getChildAdapterPosition(item)));
        }
    }

    /** A tile's alpha: dim unless it is the carried one, or, in edit-dock mode, a dock tile other than the chosen place. */
    private float tileAlpha(int pos) {
        if (movingKey != null) {
            return pos != RecyclerView.NO_POSITION && movingKey.equals(adapter.keyAt(pos)) ? 1f : 0.45f;
        }
        if (dockEditSlot >= 0 && pos >= 0 && pos < COLUMNS && pos != dockEditSlot) return 0.45f;
        return 1f;
    }

    /** Ends the mode and saves the order if it changed since the mode started. */
    private void endReorder() {
        if (!reordering) return;
        dockEditing = false;
        dockEditSlot = -1;
        drop();
        reordering = false;
        adapter.setReordering(false);
        glass.setAccent(festive ? festival.accentColor() : 0);
        btnReorder.setActivated(false);
        hideEditModeScrim();
        List<String> keys = adapter.movableKeys();
        List<String> before = orderAtStart;
        orderAtStart = null;
        if (!keys.equals(before)) {
            Order order = new Order(Order.dockKey(dockApps), keys);
            if (Order.save(Order.file(this), order)) {
                adapter.setSavedOrder(Order.ranks(order, dockApps));
            } else {
                Toast.makeText(this, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show();
            }
        }
        if (fillDockLeavesSlots) reloadTiles();   // the empty dock slots left out while reordering come back
    }

    /** Remove app from dock (Settings menu): focus goes to the dock, user chooses an app, OK removes it. */
    private void startDockRemove() {
        if (openPopup != null) {
            openPopup.dismiss();
            openPopup = null;
        }
        if (reordering) endReorder();
        if (adapter.dockAppCount() <= 1) {
            Toast.makeText(this, R.string.dock_remove_min_error, Toast.LENGTH_SHORT).show();
            return;
        }
        dockRemoving = true;
        glassAccent = 0xFFE53935;
        glass.setAccent(0xFFE53935);
        showEditModeScrim();
        focusTile(0);
        Toast.makeText(this, R.string.dock_remove_hint, Toast.LENGTH_LONG).show();
    }

    private void endDockRemove() {
        if (!dockRemoving) return;
        dockRemoving = false;
        glassAccent = 0;
        glass.setAccent(festive ? festival.accentColor() : 0);
        hideEditModeScrim();
    }

    private boolean dispatchDockRemove(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (back) {
            if (!down) {
                endDockRemove();
            }
            return true;
        }
        int pos = focusedTilePosition();
        if (ok && pos >= 0) {
            if (!down) {
                removeAppFromDock(pos);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void removeAppFromDock(int pos) {
        if (!dockRemoving) return;
        if (adapter.isAllAppsAt(pos)) {
            Toast.makeText(this, "Cannot remove All Apps shortcut", Toast.LENGTH_SHORT).show();
            return;
        }
        if (adapter.dockAppCount() <= 1) {
            Toast.makeText(this, R.string.dock_remove_min_error, Toast.LENGTH_SHORT).show();
            endDockRemove();
            return;
        }
        String pkgToRemove = adapter.packageAt(pos);
        String label = adapter.labelAt(pos);
        if (pkgToRemove == null || pkgToRemove.startsWith("__")) {
            return;
        }
        List<String> updated = new ArrayList<>(dockApps);
        updated.remove(pkgToRemove);
        dockApps = updated;
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        choices.edit().putString("dock_apps", TextUtils.join(",", updated)).apply();
        try {
            File orderFile = Order.file(this);
            if (orderFile != null && orderFile.exists()) {
                orderFile.delete();
            }
        } catch (Throwable ignored) {}
        adapter.setFavourites(dockApps);
        adapter.setSavedOrder(Collections.emptyMap());
        endDockRemove();
        reloadApps(false);
        Toast.makeText(this, "Removed " + label + " from dock", Toast.LENGTH_SHORT).show();
        focusTile(Math.max(0, Math.min(pos, adapter.getItemCount() - 1)));
    }

    public void addAppToDock(String pkgToAdd, String label) {
        if (pkgToAdd == null || pkgToAdd.isEmpty()) return;
        List<String> updated = new ArrayList<>(dockApps);
        if (updated.contains(pkgToAdd)) {
            Toast.makeText(this, label + " is already in the dock", Toast.LENGTH_SHORT).show();
            return;
        }
        updated.add(pkgToAdd);
        dockApps = updated;
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        choices.edit().putString("dock_apps", TextUtils.join(",", updated)).apply();
        try {
            File orderFile = Order.file(this);
            if (orderFile != null && orderFile.exists()) {
                orderFile.delete();
            }
        } catch (Throwable ignored) {}
        adapter.setFavourites(dockApps);
        adapter.setSavedOrder(Collections.emptyMap());
        reloadApps(false);
        Toast.makeText(this, "Added " + label + " to dock", Toast.LENGTH_SHORT).show();
        int newPos = updated.indexOf(pkgToAdd);
        if (newPos >= 0) {
            lastFocused = newPos;
        }
    }

    public void showAddAppsOnDockModal() {
        if (allAppsModalOverlay == null) return;
        if (allAppsModalTitle != null) {
            allAppsModalTitle.setText(R.string.dock_add);
        }
        if (allAppsModalAdapter != null) {
            allAppsModalAdapter.setFilterPackages(null);
            allAppsModalAdapter.setAddToDockMode(true);
            allAppsModalAdapter.setDockPackages(dockApps);
            allAppsModalAdapter.setOnAppSelectedListener(item -> {
                addAppToDock(item.pkg, item.label);
                hideAllAppsModal();
            });
            allAppsModalAdapter.reload();
            if (allAppsCountBadge != null) {
                int count = allAppsModalAdapter.getAppCount();
                allAppsCountBadge.setText(count + " " + (count == 1 ? "app" : "apps"));
            }
        }
        showAllAppsModalInternal(true);
    }

    public void showChangeWallpaperDockAppsPicker() {
        if (allAppsModalOverlay == null) return;
        if (allAppsModalTitle != null) {
            allAppsModalTitle.setText(R.string.dock_wallpaper_change);
        }
        if (allAppsModalAdapter != null) {
            allAppsModalAdapter.setAddToDockMode(false);
            allAppsModalAdapter.setDockPackages(Collections.emptySet());
            allAppsModalAdapter.setFilterPackages(dockApps);
            allAppsModalAdapter.setOnAppSelectedListener(item -> {
                hideAllAppsModalDirect();
                showFileBrowserModal(item.pkg, item.label);
            });
            allAppsModalAdapter.reload();
            if (allAppsCountBadge != null) {
                int count = allAppsModalAdapter.getAppCount();
                allAppsCountBadge.setText(count + " " + (count == 1 ? "app" : "apps"));
            }
        }
        showAllAppsModalInternal(false);
    }

    public void showAllAppsModal() {
        if (allAppsModalOverlay == null) return;
        if (allAppsModalTitle != null) {
            allAppsModalTitle.setText(R.string.all_apps);
        }
        if (allAppsModalAdapter != null) {
            allAppsModalAdapter.setFilterPackages(null);
            allAppsModalAdapter.setAddToDockMode(false);
            allAppsModalAdapter.setDockPackages(Collections.emptySet());
            allAppsModalAdapter.setOnAppSelectedListener(null);
            allAppsModalAdapter.reload();
            if (allAppsCountBadge != null) {
                int count = allAppsModalAdapter.getAppCount();
                allAppsCountBadge.setText(count + " " + (count == 1 ? "app" : "apps"));
            }
        }
        showAllAppsModalInternal(false);
    }

    private void showAllAppsModalInternal(boolean isAddToDock) {
        // Generate blurred backdrop from active wallpaper
        Drawable bgDrawable = null;
        if (dockWallpaperA != null && dockWallpaperA.getVisibility() == View.VISIBLE && dockWallpaperA.getAlpha() > 0.3f) {
            bgDrawable = dockWallpaperA.getDrawable();
        } else if (dockWallpaperB != null && dockWallpaperB.getVisibility() == View.VISIBLE && dockWallpaperB.getAlpha() > 0.3f) {
            bgDrawable = dockWallpaperB.getDrawable();
        }
        if (bgDrawable == null && findViewById(R.id.root) != null) {
            bgDrawable = findViewById(R.id.root).getBackground();
        }
        if (bgDrawable != null && allAppsBlurredBackdrop != null) {
            try {
                Bitmap blurred = HeroArt.from(bgDrawable);
                if (blurred != null) {
                    allAppsBlurredBackdrop.setImageBitmap(blurred);
                }
            } catch (Throwable ignored) {}
        }
        allAppsModalOverlay.setVisibility(View.VISIBLE);
        if (animations && !reducedMotion) {
            allAppsModalOverlay.setAlpha(0f);
            allAppsModalOverlay.animate().alpha(1f).setDuration(220).start();
            if (allAppsModalPanel != null) {
                allAppsModalPanel.setScaleX(0.92f);
                allAppsModalPanel.setScaleY(0.92f);
                allAppsModalPanel.animate().scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(new DecelerateInterpolator(1.8f)).start();
            }
        } else {
            allAppsModalOverlay.setAlpha(1f);
            if (allAppsModalPanel != null) {
                allAppsModalPanel.setScaleX(1f);
                allAppsModalPanel.setScaleY(1f);
            }
        }
        if (allAppsModalGrid != null) {
            allAppsModalGrid.post(() -> {
                int targetFocus = 0;
                if (isAddToDock && allAppsModalAdapter != null) {
                    targetFocus = allAppsModalAdapter.getFirstAvailablePosition();
                }
                if (allAppsModalAdapter != null) {
                    allAppsModalAdapter.focusPosition(targetFocus);
                }
            });
        }
    }

    public void hideAllAppsModal() {
        if (allAppsModalOverlay == null || allAppsModalOverlay.getVisibility() != View.VISIBLE) return;
        SoundManager.getInstance(this).playBack();
        if (animations && !reducedMotion) {
            allAppsModalOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                allAppsModalOverlay.setVisibility(View.GONE);
                focusDockAllAppsOrLast();
            }).start();
        } else {
            allAppsModalOverlay.setVisibility(View.GONE);
            focusDockAllAppsOrLast();
        }
    }

    private void focusDockAllAppsOrLast() {
        if (adapter != null && grid != null) {
            if (allAppsModalAdapter != null && allAppsModalAdapter.isAddToDockMode()) {
                focusTile(Math.max(0, Math.min(lastFocused, adapter.getItemCount() - 1)));
                return;
            }
            int allAppsPos = -1;
            for (int i = 0; i < adapter.getItemCount(); i++) {
                if (adapter.isAllAppsAt(i)) {
                    allAppsPos = i;
                    break;
                }
            }
            if (allAppsPos >= 0) {
                focusTile(allAppsPos);
            } else {
                focusTile(Math.max(0, Math.min(lastFocused, adapter.getItemCount() - 1)));
            }
        }
    }

    private void hideAllAppsModalDirect() {
        if (allAppsModalOverlay != null) {
            allAppsModalOverlay.setVisibility(View.GONE);
            allAppsModalOverlay.setAlpha(1f);
        }
    }

    public void showFileBrowserModal(String pkg, String label) {
        if (fileBrowserOverlay == null) return;
        this.wallpaperTargetPkg = pkg;
        this.wallpaperTargetLabel = label;

        if (fileBrowserTitle != null) {
            fileBrowserTitle.setText("Choose Wallpaper: " + (label != null ? label : pkg));
        }

        // Generate blurred backdrop from active wallpaper
        Drawable bgDrawable = null;
        if (dockWallpaperA != null && dockWallpaperA.getVisibility() == View.VISIBLE && dockWallpaperA.getAlpha() > 0.3f) {
            bgDrawable = dockWallpaperA.getDrawable();
        } else if (dockWallpaperB != null && dockWallpaperB.getVisibility() == View.VISIBLE && dockWallpaperB.getAlpha() > 0.3f) {
            bgDrawable = dockWallpaperB.getDrawable();
        }
        if (bgDrawable == null && findViewById(R.id.root) != null) {
            bgDrawable = findViewById(R.id.root).getBackground();
        }
        if (bgDrawable != null && fileBrowserBlurredBackdrop != null) {
            try {
                Bitmap blurred = HeroArt.from(bgDrawable);
                if (blurred != null) {
                    fileBrowserBlurredBackdrop.setImageBitmap(blurred);
                }
            } catch (Throwable ignored) {}
        }

        if (fileBrowserAdapter != null) {
            fileBrowserAdapter.setHasCustomWallpaper(hasCustomDockWallpaper(pkg));
            fileBrowserAdapter.resetToRoot();
            fileBrowserAdapter.reload();
        }

        fileBrowserOverlay.setVisibility(View.VISIBLE);
        if (animations && !reducedMotion) {
            fileBrowserOverlay.setAlpha(0f);
            fileBrowserOverlay.animate().alpha(1f).setDuration(220).withEndAction(() -> {
                if (fileBrowserAdapter != null) {
                    fileBrowserAdapter.focusPosition(0);
                }
            }).start();
        } else {
            fileBrowserOverlay.setAlpha(1f);
            if (fileBrowserAdapter != null) {
                fileBrowserAdapter.focusPosition(0);
            }
        }
    }

    public void hideFileBrowser() {
        if (fileBrowserOverlay == null || fileBrowserOverlay.getVisibility() != View.VISIBLE) return;
        SoundManager.getInstance(this).playBack();
        if (animations && !reducedMotion) {
            fileBrowserOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                fileBrowserOverlay.setVisibility(View.GONE);
                if (fileBrowserBlurredBackdrop != null) {
                    fileBrowserBlurredBackdrop.setImageDrawable(null);
                }
                focusTile(Math.max(0, Math.min(lastFocused, adapter != null ? adapter.getItemCount() - 1 : 0)));
            }).start();
        } else {
            fileBrowserOverlay.setVisibility(View.GONE);
            if (fileBrowserBlurredBackdrop != null) {
                fileBrowserBlurredBackdrop.setImageDrawable(null);
            }
            focusTile(Math.max(0, Math.min(lastFocused, adapter != null ? adapter.getItemCount() - 1 : 0)));
        }
    }

    private void handleFileBrowserBack() {
        if (fileBrowserAdapter != null && fileBrowserAdapter.canNavigateUp()) {
            SoundManager.getInstance(this).playBack();
            fileBrowserAdapter.navigateUp();
        } else {
            hideFileBrowser();
        }
    }

    private File getCustomDockWallpapersDir() {
        File dir = new File(getFilesDir(), "dock_wallpapers");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File getCustomDockWallpaperFile(String pkg) {
        if (pkg == null) return null;
        File file = new File(getCustomDockWallpapersDir(), pkg + ".custom");
        return file.exists() ? file : null;
    }

    private boolean hasCustomDockWallpaper(String pkg) {
        File f = getCustomDockWallpaperFile(pkg);
        return f != null && f.exists();
    }

    private void clearCustomDockWallpapers() {
        File dir = new File(getFilesDir(), "dock_wallpapers");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    try { f.delete(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private void restoreDefaultDockWallpaper(String pkg, String label) {
        if (pkg == null) return;
        File f = new File(getCustomDockWallpapersDir(), pkg + ".custom");
        if (f.exists()) {
            f.delete();
        }
        Toast.makeText(this, "Default wallpaper restored for " + (label != null ? label : pkg), Toast.LENGTH_SHORT).show();
        if (pkg.equals(currentWallpaperPkg)) {
            updateDockWallpaper(pkg, lastFocused, true);
        }
    }

    private void applyCustomDockWallpaper(String pkg, String label, File srcImageFile) {
        if (pkg == null || srcImageFile == null || !srcImageFile.exists()) return;
        try {
            File dest = new File(getCustomDockWallpapersDir(), pkg + ".custom");
            copyFile(srcImageFile, dest);
            Toast.makeText(this, "Wallpaper updated for " + (label != null ? label : pkg), Toast.LENGTH_SHORT).show();
            if (pkg.equals(currentWallpaperPkg)) {
                updateDockWallpaper(pkg, lastFocused, true);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to set wallpaper: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private Bitmap decodeWallpaperFile(File file) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int origW = opts.outWidth;
            int origH = opts.outHeight;
            if (origW <= 0 || origH <= 0) return null;

            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int sampleSize = 1;
            while ((origW / (sampleSize * 2)) >= screenW && (origH / (sampleSize * 2)) >= screenH) {
                sampleSize *= 2;
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private void setSpecificWallpaperBitmap(Bitmap bitmap, int direction) {
        if (dockWallpaperA == null || dockWallpaperB == null || bitmap == null) return;

        final ImageView incoming = wallpaperShowingA ? dockWallpaperB : dockWallpaperA;
        final ImageView outgoing = wallpaperShowingA ? dockWallpaperA : dockWallpaperB;
        wallpaperShowingA = !wallpaperShowingA;

        incoming.animate().cancel();
        outgoing.animate().cancel();

        incoming.setImageBitmap(bitmap);
        incoming.setVisibility(View.VISIBLE);

        if (animations && !reducedMotion && outgoing.getVisibility() == View.VISIBLE && outgoing.getAlpha() > 0.05f) {
            incoming.setAlpha(0f);
            incoming.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            outgoing.animate()
                    .alpha(0f)
                    .setDuration(350)
                    .withEndAction(() -> {
                        outgoing.setAlpha(0f);
                        outgoing.setScaleX(1.0f);
                        outgoing.setScaleY(1.0f);
                        outgoing.setTranslationX(0f);
                        outgoing.setTranslationY(0f);
                        outgoing.setImageDrawable(null);
                    })
                    .start();
        } else {
            incoming.setAlpha(1f);
            outgoing.setAlpha(0f);
            outgoing.setScaleX(1.0f);
            outgoing.setScaleY(1.0f);
            outgoing.setTranslationX(0f);
            outgoing.setTranslationY(0f);
            outgoing.setImageDrawable(null);
        }
        if (wallpaperScrim != null) {
            wallpaperScrim.setVisibility(View.VISIBLE);
            wallpaperScrim.setAlpha(1f);
        }

        startKenBurnsEffect(incoming, direction);
    }

    /**
     * Moves the carried tile one step. Left and Right wrap across rows, like a list; Up and Down move a whole row,
     * and Down into a short last row lands on its last tile. Nothing moves past either end or up into the top bar.
     */
    private void moveBy(int keyCode) {
        int from = adapter.positionOf(movingKey);
        int n = adapter.movableCount();
        if (from < 0 || from >= n) {
            drop();
            return;
        }
        int to;
        // rtl_app_grid (Hebrew) mirrors the grid: position 0 is on the right, so Left moves towards the end.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) to = rtlAppGrid ? from + 1 : from - 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) to = rtlAppGrid ? from - 1 : from + 1;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) to = from - COLUMNS;
        else if (from + COLUMNS < n) to = from + COLUMNS;
        else to = from / COLUMNS < (n - 1) / COLUMNS ? n - 1 : from;
        if (to < 0 || to >= n || to == from) return;
        keepScroll(() -> adapter.move(from, to));
        refocusAfterLayout(to);
    }

    /**
     * Applies an adapter change without the grid jumping. After a move the layout manager anchors on the focused
     * tile, keeping it at its old place on screen and shifting every other row; pinning the first visible row at its
     * current offset as the next layout's anchor keeps the scroll where it was. onTileFocused still scrolls
     * afterwards if the moved tile itself ends up off screen.
     */
    private void keepScroll(Runnable change) {
        LinearLayoutManager lm = (LinearLayoutManager) grid.getLayoutManager();
        int first = lm.findFirstVisibleItemPosition();
        View firstView = first != RecyclerView.NO_POSITION ? lm.findViewByPosition(first) : null;
        int offset = firstView != null ? lm.getDecoratedTop(firstView) - grid.getPaddingTop() : 0;
        change.run();
        if (firstView != null) lm.scrollToPositionWithOffset(first, offset);
    }

    /**
     * Once the grid has applied the change, put the glass (and focus, if the move dropped it) on the tile at pos.
     * The grid has a fixed size, so it applies adapter changes on the next animation frame rather than in a layout
     * pass; until then it cannot find the tile by position. Wait frame by frame (at most 10) for that. No focus
     * change fires for a moved tile, so focusTile's already-focused path calls onTileFocused itself.
     */
    private void refocusAfterLayout(int pos) {
        grid.postOnAnimation(new Runnable() {
            int frames;

            @Override
            public void run() {
                if ((grid.hasPendingAdapterUpdates() || grid.isLayoutRequested()) && ++frames < 10) {
                    grid.postOnAnimation(this);
                    return;
                }
                focusTile(pos);
                dimOthers();   // views that came into view or were rebound during the move
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (fileBrowserOverlay != null && fileBrowserOverlay.getVisibility() == View.VISIBLE) {
            handleFileBrowserBack();
            return;
        }
        if (allAppsModalOverlay != null && allAppsModalOverlay.getVisibility() == View.VISIBLE) {
            hideAllAppsModal();
            return;
        }
        if (dockRemoving) {
            endDockRemove();
            return;
        }
        SoundManager.getInstance(this).playBack();
        View currentFocus = getCurrentFocus();
        if (currentFocus != null && (currentFocus.getId() == R.id.btnInputs
                || currentFocus.getId() == R.id.btnWifi
                || currentFocus.getId() == R.id.btnSettings
                || currentFocus.getId() == R.id.weatherPill
                || currentFocus.getId() == R.id.btnReorder
                || currentFocus.getId() == R.id.btnLibraryTop)) {
            focusTile(dockReturnPosition());
            return;
        }
        // Home screen: Back goes nowhere.
    }

    private void silenceSystemSoundEffects() {
        try {
            android.provider.Settings.System.putInt(getContentResolver(),
                    android.provider.Settings.System.SOUND_EFFECTS_ENABLED, 0);
        } catch (Throwable ignored) {}
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.unloadSoundEffects();
            }
        } catch (Throwable ignored) {}
    }

    private void hideDock() {
        if (dockHidden) return;
        dockHidden = true;
        if (grid != null) {
            if (animations && !reducedMotion) {
                float density = getResources().getDisplayMetrics().density;
                grid.animate().cancel();
                grid.animate()
                        .alpha(0f)
                        .translationY(30f * density)
                        .setDuration(240)
                        .setInterpolator(new AccelerateInterpolator(1.5f))
                        .start();
            } else {
                grid.setAlpha(0f);
            }
        }
        hideGlass();
    }

    private void showDock() {
        if (!dockHidden) return;
        dockHidden = false;
        if (grid != null) {
            if (animations && !reducedMotion) {
                grid.animate().cancel();
                grid.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(280)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .start();
            } else {
                grid.setAlpha(1f);
                grid.setTranslationY(0f);
            }
        }
    }

    private void showTopBarHero(String title) {
        if (hero == null || heroName == null) return;
        hideDock();
        hero.animate().cancel();
        heroName.setText(title);
        if (heroDots != null) heroDots.setVisibility(View.GONE);
        hideAppStats();

        if (ambient != null) {
            ambient.animate().cancel();
            ambient.animate().alpha(0f).setDuration(200).start();
        }
        if (heroArt != null) {
            heroArt.animate().cancel();
            heroArt.animate().alpha(0f).setDuration(200).start();
        }

        // Only transition wallpaper when first entering the top bar, preventing wallpaper glitches between pills
        if (!"top_bar".equals(currentWallpaperPkg)) {
            currentWallpaperPkg = "top_bar";
            setSpecificWallpaper(R.drawable.wallpaper_top_bar, 0);
        }

        if (animations && !reducedMotion) {
            float density = getResources().getDisplayMetrics().density;
            hero.setTranslationY(24f * density);
            hero.setAlpha(0f);
            hero.setVisibility(View.VISIBLE);
            hero.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .start();
        } else {
            hero.setTranslationY(0f);
            hero.setAlpha(1f);
            hero.setVisibility(View.VISIBLE);
        }
    }

    private void setPillHighlighted(View view, boolean highlighted) {
        if (view == null) return;
        view.setSelected(highlighted);
        boolean active = highlighted || view.isFocused();
        int tintColor = active ? 0xFF111111 : 0xFFFFFFFF;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextColor(tintColor);
            tv.setCompoundDrawableTintList(ColorStateList.valueOf(tintColor));
        }
        if (view == weatherPill) {
            if (weatherIcon != null) weatherIcon.setColorFilter(tintColor);
            if (weatherTemp != null) weatherTemp.setTextColor(tintColor);
            if (weatherDot != null) weatherDot.setTextColor(tintColor);
            if (weatherCity != null) weatherCity.setTextColor(tintColor);
        }
        if (advancedEffects) {
            view.animate()
                    .scaleX(active ? 1.04f : 1f)
                    .scaleY(active ? 1.04f : 1f)
                    .setDuration(160).start();
        }
        if (active) {
            if (view == weatherPill) {
                showTopBarHero(getString(R.string.weather));
            } else if (view == btnInputs) {
                showTopBarHero(getString(R.string.inputs));
            } else if (view == btnWifi) {
                showTopBarHero(getString(R.string.wifi));
            } else if (view == btnSettings) {
                showTopBarHero(getString(R.string.settings));
            } else if (view == btnReorder) {
                showTopBarHero(getString(R.string.reorder));
            } else if (view == btnLibraryTop) {
                showTopBarHero(getString(R.string.app_library));
            }
        }
    }

    private void disableViewSoundEffects(View v) {
        if (v == null) return;
        v.setSoundEffectsEnabled(false);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                disableViewSoundEffects(vg.getChildAt(i));
            }
        }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onLibraryRequested() {
        showAllAppsModal();
    }

    @Override
    public void onTileFocused(int position, View card) {
        showDock();
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Focus restored before the first layout (cold start): the tile has no size yet, and the glass
        // would be sized to 2*pad. Run again once the tile is laid out.
        if (card.getWidth() == 0) {
            card.post(() -> {
                if (card.isFocused() && card.getWidth() > 0) onTileFocused(position, card);
            });
            return;
        }

        float pad = 6f * density;        // matches GlassView's glow inset
        float scale = adapter.focusScale();

        if (dockRow && position < adapter.getItemCount()) {
            // Horizontal PS5 carousel scrolling:
            // Slot 1 (X_focus) is the fixed anchor for any focused app from index 1 onwards.
            // Position 0 aligns at slot 0 (X = 0).
            showHero(position);

            card.post(() -> {
                if (!card.isFocused()) return;

                float slotStep = (54f + 12f) * density;
                int targetX = (position == 0) ? 0 : Math.round(slotStep);

                int[] cardLoc = new int[2];
                card.getLocationInWindow(cardLoc);
                int[] gridLoc = new int[2];
                grid.getLocationInWindow(gridLoc);
                int[] rootLoc = new int[2];
                root.getLocationInWindow(rootLoc);

                int currentX = cardLoc[0] - gridLoc[0];
                int dx = (position <= 1 && grid.computeHorizontalScrollOffset() == 0) ? 0 : (currentX - targetX);

                Rect r = tileRect;
                r.set(0, 0, card.getWidth(), card.getHeight());
                root.offsetDescendantRectToMyCoords(card, r);

                int cardW = Math.round(92 * density);
                int cardH = Math.round(92 * density);
                float glassW = cardW + 2 * pad;
                float glassH = cardH + 2 * pad;

                ViewGroup.LayoutParams lp = glass.getLayoutParams();
                if (lp.width != (int) glassW || lp.height != (int) glassH) {
                    lp.width = (int) glassW;
                    lp.height = (int) glassH;
                    glass.setLayoutParams(lp);
                    glass.setRadius(18f * density);
                }

                int accent = dockRemoving ? 0xFFE53935 : (cinematic && !festive ? adapter.ambientColorAt(position) : 0);
                if (accent != glassAccent) {
                    glassAccent = accent;
                    glass.setAccent(accent);
                }

                float targetGlassX = (r.left - dx) - pad;
                float targetGlassY = r.top - pad;

                springX.cancel();
                springY.cancel();
                glass.animate().cancel();

                if (!glassShown) {
                    glass.setTranslationX(targetGlassX);
                    glass.setTranslationY(targetGlassY);
                    glass.setAlpha(1f);
                    glassShown = true;
                } else {
                    if (animations && !reducedMotion) {
                        glass.animate()
                                .translationX(targetGlassX)
                                .translationY(targetGlassY)
                                .setDuration(260)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                                .start();
                    } else {
                        glass.setTranslationX(targetGlassX);
                        glass.setTranslationY(targetGlassY);
                    }
                }

                if (Math.abs(dx) > (int) (2 * density)) {
                    grid.stopScroll();
                    if (!animations || reducedMotion) {
                        grid.scrollBy(dx, 0);
                    } else {
                        grid.smoothScrollBy(dx, 0, new android.view.animation.DecelerateInterpolator(1.8f), 280);
                    }
                }
            });
        } else {
            // Below the dock (or in a plain grid) the glow follows the tile without the big name; letting it
            // fade out left the grid on plain black.
            Rect r = tileRect;
            r.set(0, 0, card.getWidth(), card.getHeight());
            root.offsetDescendantRectToMyCoords(card, r);
            float w = card.getWidth() * scale + 2 * pad;
            float h = card.getHeight() * scale + 2 * pad;
            float x = r.exactCenterX() - w / 2f;
            float y = r.exactCenterY() - h / 2f - (advancedEffects ? 4f * density : 0f);

            ViewGroup.LayoutParams lp = glass.getLayoutParams();
            if (lp.width != (int) w || lp.height != (int) h || card.getWidth() != glassCardW) {
                lp.width = (int) w;
                lp.height = (int) h;
                glass.setLayoutParams(lp);
                // GlassView draws its slab inset by pad, i.e. exactly over the zoomed tile: same corners as the tile.
                glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * scale);
            }
            glassCardW = card.getWidth();
            glassTargetX = x;
            glassTargetY = y;
            // Cinematic shelf: the highlight glows in the dock app's colour, plain white in the grid.
            int accent = dockRemoving ? 0xFFE53935 : (cinematic && !festive && position < adapter.getItemCount() ? adapter.ambientColorAt(position) : 0);
            if (accent != glassAccent) {
                glassAccent = accent;
                glass.setAccent(accent);
            }
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            if (!glassShown) {
                glass.setAlpha(1f);
                glassShown = true;
            }

            showGlowOnly(position);
            // Scroll just enough so the whole glass (not only the tile) is visible.
            View item = (View) card.getParent();
            revealRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, revealRect, reducedMotion);
        }
    }

    /** Refits the glass to the focused holder once the grid layout and tile dimensions settle. */
    private void refitGlass() {
        if (isDestroyed() || adapter.appCount() == 0 || libraryFocusPending) return;
        int pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1));
        if (dockRow && pos < adapter.getItemCount()) return;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        View card = vh == null ? null : vh.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() <= 0 || !card.isFocused()) return;
        onTileFocused(pos, card);
    }

    // ---- bottom-left title (PS5 style: stuck to bottom-left with status dots above) --------------

    private void showHero(int position) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        boolean changed = !label.contentEquals(heroName.getText());
        if (cinematic) showHeroArt(position);
        // Dynamic wallpaper: transition to the focused dock app's wallpaper artwork.
        if (dockRow) updateDockWallpaper(adapter.packageAt(position), position);
        if (dockRow) updateAppStats(adapter.packageAt(position), changed);

        hero.animate().cancel();
        ambient.animate().cancel();
        fillHero(position, label);

        if (animations && !reducedMotion && changed) {
            float density = getResources().getDisplayMetrics().density;
            hero.setTranslationY(24f * density);
            hero.setAlpha(0f);
            hero.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .start();
        } else {
            hero.setTranslationY(0f);
            hero.setAlpha(1f);
        }

        if (ambientGlow) {
            setAmbient(color);
            ambient.setAlpha(1f);
        }
        heroShown = true;
    }

    /** The shelf's text for this dock app: its name and the status dots. */
    private void fillHero(int position, String label) {
        heroName.setText(label);
        updateHeroDots(position);
    }

    // ---- cinematic shelf -------------------------------------------------------------------------

    /** The parts that never change: large title and status dots. */
    private void setUpCinematicShelf() {
        heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 54f);
        heroName.setLetterSpacing(-0.02f);
        heroMeta.setVisibility(View.GONE);
        heroDots.setVisibility(View.VISIBLE);
        heroArt.setVisibility(View.GONE);
        View scrim = findViewById(R.id.heroScrim);
        if (scrim != null) scrim.setVisibility(View.GONE);
    }

    /**
     * Darkens the side the shelf's text is on and the lower part of the screen, so the name, the dock and
     * the grid stay readable over the artwork. One small bitmap stretched over the screen, like the glow.
     */
    private Drawable scrimDrawable() {
        int w = 480, h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        boolean rtl = getResources().getBoolean(R.bool.rtl_menu_bar);
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(rtl ? w : 0, 0, rtl ? 0 : w, 0,
                new int[]{0xD1060406, 0x59060406, 0x00060406}, new float[]{0f, 0.42f, 0.7f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x00060406, 0x00060406, 0xDB060406, 0xFF060406}, new float[]{0f, 0.28f, 0.58f, 0.76f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        BitmapDrawable scrim = new BitmapDrawable(getResources(), bmp);
        scrim.setGravity(Gravity.FILL);
        return scrim;
    }

    /** Swaps the backdrop to this app's artwork: fade out, swap, fade in, the way the shelf's name changes. */
    private void showHeroArt(int position) {
        String key = adapter.keyAt(position);
        if (key == null) return;
        heroArt.animate().cancel();
        if (key.equals(heroArtKey)) {
            fadeHeroArt(420);
            return;
        }
        Bitmap art = heroArtCache.get(key);
        if (art == null) {
            art = HeroArt.from(adapter.artworkAt(position));
            if (art != null) heroArtCache.put(key, art);
        }
        heroArtKey = key;
        final Bitmap next = art;
        if (!animations || reducedMotion || heroArt.getAlpha() == 0f) {
            heroArt.setImageBitmap(next);
            fadeHeroArt(600);
            return;
        }
        heroArt.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            heroArt.setImageBitmap(next);
            heroArt.animate().alpha(HERO_ART_ALPHA).setDuration(420).start();
        }).start();
    }

    private void fadeHeroArt(int ms) {
        if (!animations) heroArt.setAlpha(HERO_ART_ALPHA);
        else heroArt.animate().alpha(HERO_ART_ALPHA).setDuration(reducedMotion ? Math.min(ms, 160) : ms).start();
    }

    /** "Last opened yesterday, 9:40 PM", the time in white; nothing (but the same height) when it was never seen. */
    private void updateHeroMeta(int position) {
        long when = LastOpened.get(this, adapter.packageAt(position));
        if (when <= 0) {
            heroMeta.setVisibility(View.INVISIBLE);
            return;
        }
        String rel = LastOpened.describe(this, when);
        String text = getString(R.string.last_opened, rel);
        SpannableString line = new SpannableString(text);
        int at = text.indexOf(rel);
        if (at >= 0) line.setSpan(new ForegroundColorSpan(0xFFFFFFFF), at, at + rel.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        heroMeta.setText(line);
        heroMeta.setVisibility(View.VISIBLE);
    }

    /** One dot per dock app, the focused one drawn long and white. Hebrew docks start on the right, so do the dots. */
    private void updateHeroDots(int position) {
        int count = dockRow ? adapter.getItemCount() : Math.min(adapter.dockFilled(), COLUMNS);
        heroDots.setVisibility(count > 1 ? View.VISIBLE : View.INVISIBLE);
        if (heroDots.getChildCount() != count) {
            heroDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(this);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(3 * density);
                dot.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (6 * density), (int) (6 * density));
                if (i > 0) lp.leftMargin = (int) (6 * density);
                heroDots.addView(dot, lp);
            }
        }
        for (int i = 0; i < count; i++) {
            View dot = heroDots.getChildAt(rtlAppGrid ? count - 1 - i : i);
            boolean on = i == position;
            ((GradientDrawable) dot.getBackground()).setColor(on ? 0xFFFFFFFF : 0x4DFFFFFF);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            int w = (int) ((on ? 20 : 6) * density);
            if (lp.width != w) {
                lp.width = w;
                dot.setLayoutParams(lp);
            }
        }
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture. It is painted once in white and tinted with the app's colour,
     * so changing apps neither repaints it on the CPU (about 11 ms) nor uploads it again.
     */
    private void setAmbient(int color) {
        if (color == ambientColor && ambient.getBackground() != null) return;
        ambientColor = color;
        BitmapDrawable glow = (BitmapDrawable) ambient.getBackground();
        if (glow == null) {
            int w = 480;
            int h = 270;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (cinematic) {
                // Higher and stronger, from the corner away from the shelf's text: the app's colour floods
                // the top of the screen over its artwork, and the scrim keeps the text side dark.
                boolean rtl = getResources().getBoolean(R.bool.rtl_menu_bar);
                paint.setShader(new RadialGradient(
                        w * (rtl ? 0.20f : 0.80f), h * 0.06f, w * 0.78f,
                        new int[]{0xA6FFFFFF, 0x47FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
            } else {
                paint.setShader(new RadialGradient(
                        w * 0.32f, h * 0.42f, w * 0.62f,
                        new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, 0, w, h, paint);
            glow = new BitmapDrawable(getResources(), bmp);
            glow.setGravity(Gravity.FILL);
            ambient.setBackground(glow);
        }
        // The default tint mode, SRC_IN, keeps the mask's alpha and takes the colour from the tint.
        glow.setTint(0xFF000000 | color);
    }

    private void transitionAmbient(int color) {
        // A rapid focus change continues from the colour actually displayed on screen.
        stopAmbientAnimation();
        ambient.animate().cancel();
        if (color == ambientColor) {
            ambient.animate().alpha(1f).setDuration(180).start();
            return;
        }
        ambient.animate().alpha(1f).setDuration(180).start();
        ambientAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), ambientColor, color);
        ambientAnimator.setDuration(360);
        ambientAnimator.addUpdateListener(animation -> setAmbient((int) animation.getAnimatedValue()));
        ambientAnimator.start();
    }

    private void stopAmbientAnimation() {
        if (ambientAnimator != null) {
            ambientAnimator.cancel();
            ambientAnimator = null;
        }
    }

    /**
     * Grid tiles (every tile with dock_row false): the glow behind the grid takes the focused tile's colour; the big
     * name above the dock does not show (every grid tile has its name underneath).
     */
    private void showGlowOnly(int position) {
        hideDockWallpaper();  // focus moved out of the dock row
        hideAppStats();
        int color = adapter.ambientColorAt(position);
        stopAmbientAnimation();
        hero.animate().cancel();
        ambient.animate().cancel();
        hero.setAlpha(0f);
        heroArt.animate().cancel();
        heroArt.setAlpha(0f);
        heroShown = false;
        if (!ambientGlow) return;
        if (!animations || reducedMotion) {
            setAmbient(color);
            ambient.setAlpha(1f);
            return;
        }
        if (color == ambientColor && ambient.getBackground() != null) {
            ambient.animate().alpha(1f).setDuration(300).start();
            return;
        }
        // The same dip-and-swap as the dock's top shelf.
        ambient.animate().alpha(0.35f).setDuration(ambient.getAlpha() > 0.35f ? 200 : 0).withEndAction(() -> {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(500).start();
        }).start();
    }

    private void hideHero() {
        if (!heroShown) return;
        hideDockWallpaper();
        hideAppStats();
        stopAmbientAnimation();
        hero.animate().cancel();
        ambient.animate().cancel();
        heroArt.animate().cancel();
        heroShown = false;
        if (animations) {
            hero.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
            if (ambientGlow) ambient.animate().alpha(0f).setDuration(reducedMotion ? 140 : 300).start();
            heroArt.animate().alpha(0f).setDuration(reducedMotion ? 140 : 300).start();
        } else {
            hero.setAlpha(0f);
            ambient.setAlpha(0f);
            heroArt.setAlpha(0f);
        }
    }

    // ---- focused dock app wallpapers ---------------------------------------------------------

    /**
     * Cross-fades to the cinematic show wallpaper attached to the focused dock app with a smooth Ken Burns animation.
     */
    private void updateDockWallpaper(String pkg, int position) {
        updateDockWallpaper(pkg, position, false);
    }

    private void updateDockWallpaper(String pkg, int position, boolean force) {
        if (pkg == null) return;
        if (!force && pkg.equals(currentWallpaperPkg)) return;
        currentWallpaperPkg = pkg;

        File customWpFile = getCustomDockWallpaperFile(pkg);
        if (customWpFile != null && customWpFile.exists()) {
            Bitmap bmp = decodeWallpaperFile(customWpFile);
            if (bmp != null) {
                setSpecificWallpaperBitmap(bmp, position);
                return;
            }
        }

        Integer resId = DOCK_WALLPAPERS.get(pkg);
        if (resId == null) {
            // Dynamic auto-detection: no Java edits or AI needed!
            // 1. Try full package name (e.g. wallpaper_com_spotify_tv_android.jpg)
            String fullClean = pkg.replace('.', '_').toLowerCase();
            int id = getResources().getIdentifier("wallpaper_" + fullClean, "drawable", getPackageName());
            if (id != 0) {
                resId = id;
            } else {
                // 2. Try brand/app name segments (e.g. wallpaper_spotify.jpg, wallpaper_vlc.jpg, wallpaper_kodi.jpg)
                String[] segments = pkg.split("\\.");
                for (int s = segments.length - 1; s >= 0; s--) {
                    String seg = segments[s].toLowerCase();
                    if ("com".equals(seg) || "org".equals(seg) || "net".equals(seg) || "tv".equals(seg) || "android".equals(seg)) continue;
                    id = getResources().getIdentifier("wallpaper_" + seg, "drawable", getPackageName());
                    if (id != 0) {
                        resId = id;
                        break;
                    }
                }
            }
            if (resId != null) {
                DOCK_WALLPAPERS.put(pkg, resId);
            }
        }
        if (resId == null) {
            hideDockWallpaper();
            return;
        }

        setSpecificWallpaper(resId, position);
    }

    private void setSpecificWallpaper(int resId, int direction) {
        if (dockWallpaperA == null || dockWallpaperB == null) return;

        final ImageView incoming = wallpaperShowingA ? dockWallpaperB : dockWallpaperA;
        final ImageView outgoing = wallpaperShowingA ? dockWallpaperA : dockWallpaperB;
        wallpaperShowingA = !wallpaperShowingA;

        incoming.animate().cancel();
        outgoing.animate().cancel();

        incoming.setImageResource(resId);
        incoming.setVisibility(View.VISIBLE);

        if (animations && !reducedMotion && outgoing.getVisibility() == View.VISIBLE && outgoing.getAlpha() > 0.05f) {
            incoming.setAlpha(0f);
            incoming.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            outgoing.animate()
                    .alpha(0f)
                    .setDuration(350)
                    .withEndAction(() -> {
                        outgoing.setAlpha(0f);
                        outgoing.setScaleX(1.0f);
                        outgoing.setScaleY(1.0f);
                        outgoing.setTranslationX(0f);
                        outgoing.setTranslationY(0f);
                        outgoing.setImageDrawable(null);
                    })
                    .start();
        } else {
            incoming.setAlpha(1f);
            outgoing.setAlpha(0f);
            outgoing.setScaleX(1.0f);
            outgoing.setScaleY(1.0f);
            outgoing.setTranslationX(0f);
            outgoing.setTranslationY(0f);
            outgoing.setImageDrawable(null);
        }
        if (wallpaperScrim != null) {
            wallpaperScrim.setVisibility(View.VISIBLE);
            wallpaperScrim.setAlpha(1f);
        }

        startKenBurnsEffect(incoming, direction);
    }

    // ---- Ken Burns cinematic wallpaper animation ------------------------------------------------

    private void startKenBurnsEffect(ImageView iv, int direction) {
        if (iv == null) return;

        // Cancel previous animator on this ImageView
        if (iv == dockWallpaperA && kbAnimatorA != null) {
            kbAnimatorA.cancel();
            kbAnimatorA = null;
        } else if (iv == dockWallpaperB && kbAnimatorB != null) {
            kbAnimatorB.cancel();
            kbAnimatorB = null;
        }

        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        float panXDist = 0.02f * width;
        float panYDist = 0.02f * height;

        final float targetX;
        final float targetY;
        switch (Math.abs(direction) % 4) {
            case 0:
                targetX = -panXDist;
                targetY = -panYDist;
                break;
            case 1:
                targetX = panXDist;
                targetY = panYDist;
                break;
            case 2:
                targetX = panXDist;
                targetY = -panYDist;
                break;
            case 3:
            default:
                targetX = -panXDist;
                targetY = panYDist;
                break;
        }

        final float scaleFrom = 1.02f;
        final float scaleTo = 1.10f;

        iv.setScaleX(scaleFrom);
        iv.setScaleY(scaleFrom);
        iv.setTranslationX(0f);
        iv.setTranslationY(0f);

        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(22_000L);
        va.setInterpolator(new AccelerateDecelerateInterpolator());
        va.setRepeatMode(ValueAnimator.REVERSE);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.addUpdateListener(anim -> {
            float f = (float) anim.getAnimatedValue();
            iv.setScaleX(scaleFrom + (scaleTo - scaleFrom) * f);
            iv.setScaleY(scaleFrom + (scaleTo - scaleFrom) * f);
            iv.setTranslationX(targetX * f);
            iv.setTranslationY(targetY * f);
        });

        if (iv == dockWallpaperA) {
            kbAnimatorA = va;
        } else {
            kbAnimatorB = va;
        }
        va.start();
    }

    private void stopKenBurnsEffect() {
        if (kbAnimatorA != null) {
            kbAnimatorA.cancel();
            kbAnimatorA = null;
        }
        if (kbAnimatorB != null) {
            kbAnimatorB.cancel();
            kbAnimatorB = null;
        }
        if (dockWallpaperA != null) {
            dockWallpaperA.setScaleX(1.0f);
            dockWallpaperA.setScaleY(1.0f);
            dockWallpaperA.setTranslationX(0f);
            dockWallpaperA.setTranslationY(0f);
        }
        if (dockWallpaperB != null) {
            dockWallpaperB.setScaleX(1.0f);
            dockWallpaperB.setScaleY(1.0f);
            dockWallpaperB.setTranslationX(0f);
            dockWallpaperB.setTranslationY(0f);
        }
    }

    /**
     * Smoothly fades out the dock wallpapers when focus leaves the dock into the app grid.
     */
    private void hideDockWallpaper() {
        if (currentWallpaperPkg.isEmpty()) return;
        currentWallpaperPkg = "";
        stopKenBurnsEffect();
        if (dockWallpaperA != null) dockWallpaperA.animate().cancel();
        if (dockWallpaperB != null) dockWallpaperB.animate().cancel();
        if (wallpaperScrim != null) wallpaperScrim.animate().cancel();

        if (animations && !reducedMotion) {
            if (dockWallpaperA != null) dockWallpaperA.animate().alpha(0f).setDuration(300).start();
            if (dockWallpaperB != null) dockWallpaperB.animate().alpha(0f).setDuration(300).start();
            if (wallpaperScrim != null) wallpaperScrim.animate().alpha(0f).setDuration(300).start();
        } else {
            if (dockWallpaperA != null) dockWallpaperA.setAlpha(0f);
            if (dockWallpaperB != null) dockWallpaperB.setAlpha(0f);
            if (wallpaperScrim != null) wallpaperScrim.setAlpha(0f);
        }
    }

    // ── App statistics on bottom right ────────────────────────────────────────

    private void updateAppStats(String pkg, boolean changed) {
        if (appStats == null) return;
        currentStatsPkg = pkg != null ? pkg : "";
        if (currentStatsPkg.isEmpty() || currentStatsPkg.startsWith("__")) {
            hideAppStats();
            return;
        }

        AppStats cached = appStatsCache.get(currentStatsPkg);
        if (cached != null) {
            showOrUpdateAppStatsView(cached, changed);
        } else {
            final String targetPkg = currentStatsPkg;
            statsExecutor.execute(() -> {
                AppStats s = computeAppStats(targetPkg);
                handler.post(() -> {
                    appStatsCache.put(targetPkg, s);
                    if (targetPkg.equals(currentStatsPkg)) {
                        showOrUpdateAppStatsView(s, changed);
                    }
                });
            });
        }
    }

    private void showOrUpdateAppStatsView(AppStats s, boolean changed) {
        if (appStats == null) return;
        appStats.setVisibility(View.VISIBLE);
        appStats.animate().cancel();
        applyAppStats(s);

        if (animations && !reducedMotion && changed) {
            float density = getResources().getDisplayMetrics().density;
            appStats.setTranslationY(24f * density);
            appStats.setAlpha(0f);
            appStats.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .start();
        } else {
            appStats.setTranslationY(0f);
            appStats.setAlpha(1f);
        }
    }

    private void hideAppStats() {
        currentStatsPkg = "";
        if (appStats == null) return;
        appStats.animate().cancel();
        if (animations && !reducedMotion) {
            appStats.animate().alpha(0f).setDuration(160).start();
        } else {
            appStats.setAlpha(0f);
        }
    }

    private void applyAppStats(AppStats s) {
        if (statWeeklyTime != null) statWeeklyTime.setText(s.weeklyTime);
        if (statStorage != null) statStorage.setText(s.storage);
    }

    private AppStats computeAppStats(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return new AppStats("0 hr", "0 hr", "0 MB");
        }

        long weeklyMillis = 0;
        long totalMillis = 0;
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                long now = System.currentTimeMillis();
                long oneWeekAgo = now - 7L * 24 * 3600 * 1000L;
                long totalBegin = now - 365L * 24 * 3600 * 1000L * 3L;

                Map<String, UsageStats> weeklyMap = usm.queryAndAggregateUsageStats(oneWeekAgo, now);
                if (weeklyMap != null && weeklyMap.containsKey(pkg)) {
                    UsageStats s = weeklyMap.get(pkg);
                    if (s != null) weeklyMillis = s.getTotalTimeInForeground();
                }

                Map<String, UsageStats> totalMap = usm.queryAndAggregateUsageStats(totalBegin, now);
                if (totalMap != null && totalMap.containsKey(pkg)) {
                    UsageStats s = totalMap.get(pkg);
                    if (s != null) totalMillis = s.getTotalTimeInForeground();
                }

                if (totalMillis < weeklyMillis) {
                    totalMillis = weeklyMillis;
                }
            }
        } catch (Throwable ignored) {}

        long storageBytes = 0;
        try {
            StorageStatsManager ssm = (StorageStatsManager) getSystemService(Context.STORAGE_STATS_SERVICE);
            if (ssm != null) {
                StorageStats ss = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle());
                storageBytes = ss.getAppBytes() + ss.getDataBytes() + ss.getCacheBytes();
            }
        } catch (Throwable t) {
            // Fallback: estimate from application APK source dirs
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                if (ai.sourceDir != null) storageBytes += new File(ai.sourceDir).length();
                if (ai.splitSourceDirs != null) {
                    for (String split : ai.splitSourceDirs) {
                        storageBytes += new File(split).length();
                    }
                }
            } catch (Throwable ignored) {}
        }

        String totalTimeStr = formatHours(totalMillis);
        String weeklyTimeStr = formatHours(weeklyMillis);
        String storageStr = formatStorage(storageBytes);

        return new AppStats(totalTimeStr, weeklyTimeStr, storageStr);
    }

    private String formatHours(long millis) {
        if (millis <= 0) return "0 hr";
        double hours = millis / (1000.0 * 3600.0);
        if (hours < 0.1) return "< 0.1 hr";
        if (hours >= 100.0) return String.format(Locale.US, "%.0f hr", hours);
        return String.format(Locale.US, "%.1f hr", hours);
    }

    private String formatStorage(long bytes) {
        if (bytes <= 0) return "0 MB";
        long mb = Math.round(bytes / (1024.0 * 1024.0));
        if (mb == 0 && bytes > 0) return "< 1 MB";
        return String.format(Locale.US, "%,d MB", mb);
    }

    private void prewarmAppStats() {
        if (adapter == null) return;
        final List<String> pkgs = new ArrayList<>();
        int count = Math.min(COLUMNS, adapter.appCount());
        for (int i = 0; i < count; i++) {
            String p = adapter.packageAt(i);
            if (p != null && !p.isEmpty()) pkgs.add(p);
        }
        statsExecutor.execute(() -> {
            for (String p : pkgs) {
                if (!appStatsCache.containsKey(p)) {
                    AppStats s = computeAppStats(p);
                    appStatsCache.put(p, s);
                }
            }
            handler.post(() -> {
                if (!currentStatsPkg.isEmpty()) {
                    AppStats s = appStatsCache.get(currentStatsPkg);
                    if (s != null) applyAppStats(s);
                }
            });
        });
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().cancel();
        if (animations) glass.animate().alpha(0f).setDuration(160).start();
        else glass.setAlpha(0f);
    }

    // ---- optional system stats (config.xml show_stats) --------------------------------------------

    /** Background reader. Started only while Home is resumed and show_stats is on. */
    private HandlerThread statsThread;
    /** The current run's handler; a run whose handler is no longer this one stops (volatile: read off-thread). */
    private volatile Handler statsBg;

    private void startStats() {
        if (statsThread != null) return;
        stats.setVisibility(View.VISIBLE);
        cpuTotalPrev = -1;
        idlePrev = -1;
        procStatDead = false;
        statsThread = new HandlerThread("stats");
        statsThread.start();
        // Each start gets its own handler, held by its task: stopStats on the main thread can clear the field
        // at any moment without the reader tripping over it.
        Handler bg = new Handler(statsThread.getLooper());
        statsBg = bg;
        bg.post(new Runnable() {
            @Override
            public void run() {
                if (statsBg != bg) return;
                String line = readStatsLine();
                handler.post(() -> {
                    if (showStats && stats != null && statsBg == bg) stats.setText(line);
                });
                if (statsBg == bg) bg.postDelayed(this, STATS_MS);
            }
        });
    }

    private void stopStats() {
        if (statsThread == null) return;
        statsBg.removeCallbacksAndMessages(null);
        statsThread.quit();
        statsThread = null;
        statsBg = null;
        // Paused (the quick panel or an app on top): keep the last line on screen. Taking it away moved the grid up
        // under a glass highlight that stayed put.
        if (!showStats) stats.setVisibility(View.GONE);
    }

    /** One quiet line, e.g. "CPU 8%  ·  Memory 430 MB free  ·  Storage 3.1 GB free". Runs off the main thread. */
    private static void appendStat(StringBuilder line, String part) {
        if (line.length() > 0) line.append("  ·  ");
        line.append(part);
    }

    private String readStatsLine() {
        int cpu = cpuPercent();
        if (cpu < 0) {
            try {
                Thread.sleep(200);             // second sample, so the first line already has a CPU figure
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
            cpu = cpuPercent();
        }
        // Android 8 and later hide /proc/stat from apps on many TVs: then the line leaves CPU out rather than
        // showing a dash that never fills in.
        StringBuilder line = new StringBuilder();
        if (cpu >= 0) line.append(getString(R.string.stats_cpu, cpu));
        long mem = memAvailableMb();
        if (mem >= 0) appendStat(line, getString(R.string.stats_memory, mem));
        String free = formatGb(Environment.getDataDirectory().getFreeSpace());
        if (!free.isEmpty()) appendStat(line, getString(R.string.stats_storage, free));
        return line.toString();
    }

    // -1: need another sample. -2: this source cannot be read (Android 9 hides /proc/stat from apps).
    private static final int CPU_UNREADABLE = -2;
    private long cpuTotalPrev = -1, cpuIdlePrev = -1;
    private boolean procStatDead;
    private long idlePrev = -1, wallPrev = -1;

    private int cpuPercent() {
        if (!procStatDead) {
            int fromStat = cpuFromProcStat();
            if (fromStat != CPU_UNREADABLE) return fromStat;
            procStatDead = true;
            idlePrev = -1;
        }
        return cpuFromIdle();
    }

    private int cpuFromProcStat() {
        String s = readFirstLine("/proc/stat");
        if (s == null || !s.startsWith("cpu")) return CPU_UNREADABLE;
        String[] t = s.trim().split("\\s+");
        if (t.length < 5) return CPU_UNREADABLE;
        try {
            long user = Long.parseLong(t[1]), nice = Long.parseLong(t[2]), sys = Long.parseLong(t[3]);
            long idle = Long.parseLong(t[4]);
            long iowait = t.length > 5 ? Long.parseLong(t[5]) : 0;
            long irq = t.length > 6 ? Long.parseLong(t[6]) : 0;
            long soft = t.length > 7 ? Long.parseLong(t[7]) : 0;
            long total = user + nice + sys + idle + iowait + irq + soft;
            long idleAll = idle + iowait;
            if (cpuTotalPrev < 0) {
                cpuTotalPrev = total;
                cpuIdlePrev = idleAll;
                return -1;
            }
            long dt = total - cpuTotalPrev, di = idleAll - cpuIdlePrev;
            cpuTotalPrev = total;
            cpuIdlePrev = idleAll;
            if (dt <= 0) return -1;
            int busy = (int) Math.round((dt - di) * 100.0 / dt);
            return Math.max(0, Math.min(100, busy));
        } catch (NumberFormatException e) {
            return CPU_UNREADABLE;
        }
    }

    /** Busy percent from cpuidle time (microseconds) across the cores. -1 until the second sample. */
    private int cpuFromIdle() {
        long idle = 0;
        int cores = 0;
        for (int i = 0; i < 8; i++) {
            boolean any = false;
            for (int state = 0; state < 8; state++) {
                String t = readFirstLine("/sys/devices/system/cpu/cpu" + i
                        + "/cpuidle/state" + state + "/time");
                if (t == null) break;
                try {
                    idle += Long.parseLong(t.trim());
                    any = true;
                } catch (NumberFormatException e) {
                    break;
                }
            }
            if (!any) break;
            cores++;
        }
        if (cores == 0) return -1;
        long wall = SystemClock.elapsedRealtime() * 1000L;
        if (idlePrev < 0) {
            idlePrev = idle;
            wallPrev = wall;
            return -1;
        }
        long di = idle - idlePrev;
        long dw = (wall - wallPrev) * cores;
        idlePrev = idle;
        wallPrev = wall;
        if (dw <= 0) return -1;
        int busy = (int) Math.round((dw - di) * 100.0 / dw);
        return Math.max(0, Math.min(100, busy));
    }

    private long memAvailableMb() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    String[] t = line.split("\\s+");
                    return t.length >= 2 ? Long.parseLong(t[1]) / 1024 : -1;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return -1;
    }

    private static String readFirstLine(String path) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path))) {
            return r.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private static String formatGb(long bytes) {
        if (bytes <= 0) return "";
        double gb = bytes / (1024.0 * 1024 * 1024);
        return gb >= 10 ? String.format(Locale.US, "%.0f GB", gb)
                : String.format(Locale.US, "%.1f GB", gb);
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.appCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            boolean forward = rtlAppGrid ? keyCode == KeyEvent.KEYCODE_DPAD_LEFT : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (dockRow) {
                int lastPos = adapter.getItemCount() - 1;
                if (forward) {
                    if (position >= lastPos) {
                        if (weatherPill != null && weatherPill.getVisibility() == View.VISIBLE) {
                            weatherPill.requestFocus();
                            return true;
                        }
                    } else {
                        focusTile(position + 1);
                        return true;
                    }
                } else {
                    if (position == 0) {
                        return true;
                    } else {
                        focusTile(position - 1);
                        return true;
                    }
                }
            }
            if (forward && position == adapter.focusablePosition(count - 1) && adapter.isLibraryAt(count)) {
                focusLibrary();
                return true;
            }
            int rowCount = position < COLUMNS ? Math.min(count, adapter.dockFilled()) : count;
            return GridNavigation.atHorizontalEdge(position, rowCount, COLUMNS,
                    rtlAppGrid, keyCode == KeyEvent.KEYCODE_DPAD_LEFT);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (dockRow || position < COLUMNS) {
                // Dock row: moving UP focuses weather icon first!
                if (weatherPill != null && weatherPill.getVisibility() == View.VISIBLE) {
                    weatherPill.requestFocus();
                    return true;
                }
                View topTarget = btnInputs != null && btnInputs.getVisibility() == View.VISIBLE ? btnInputs : firstTopButton();
                if (topTarget != null) {
                    topTarget.requestFocus();
                    return true;
                }
                return true;
            }
            target = position - COLUMNS;
        } else {
            if (dockRow) return true;
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) {
                if (adapter.isLibraryAt(count)) focusLibrary();
                return true;
            }
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        // The default focus search clicks for Left/Right; Up/Down are handled here, so click for them too.
        if (SoundManager.getInstance(this).isEnabled()) {
            grid.playSoundEffect(keyCode == KeyEvent.KEYCODE_DPAD_UP
                    ? SoundEffectConstants.NAVIGATION_UP : SoundEffectConstants.NAVIGATION_DOWN);
        }
        return true;
    }

    @Override public void onLibraryFocused() {
        libraryFocusPending = false;
        hideGlass();
        hideHero();
    }

    /** The first top-bar button after Inputs that is shown: the network pill, Reorder, then Settings (always there). */
    private View firstTopButton() {
        if (btnWifi.getVisibility() == View.VISIBLE) return btnWifi;
        if (btnReorder.getVisibility() == View.VISIBLE) return btnReorder;
        return findViewById(R.id.btnSettings);
    }

    @Override public void onLibraryUp() {
        if (adapter.appCount() == 0) { firstTopButton().requestFocus(); return; }
        int lastRow = ((adapter.appCount() - 1) / COLUMNS) * COLUMNS;
        focusTile(Math.min(lastRow + lastFocused % COLUMNS, adapter.appCount() - 1));
    }

    private void focusLibrary() {
        cancelPendingFocusScroll();
        int position = adapter.appCount();
        if (!adapter.isLibraryAt(position)) return;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            View button = holder.itemView.findViewById(R.id.btnLibrary);
            Rect visible = new Rect();
            if (button.getGlobalVisibleRect(visible) && visible.height() >= button.getHeight()) {
                if (button.requestFocus()) libraryFocusPending = false;
                return;
            }
        }
        if (!animations) {
            grid.scrollToPosition(position);
            grid.post(() -> {
                if (appLibrary != null || !hasWindowFocus()) return;
                RecyclerView.ViewHolder footer = grid.findViewHolderForAdapterPosition(adapter.appCount());
                if (footer != null && adapter.isLibraryAt(adapter.appCount())
                        && footer.itemView.findViewById(R.id.btnLibrary).requestFocus()) libraryFocusPending = false;
            });
            return;
        }
        pendingFocusScroll = new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                if (pendingFocusScroll != this) return;
                pendingFocusScroll = null;
                RecyclerView.ViewHolder footer = rv.findViewHolderForAdapterPosition(position);
                if (footer != null && footer.itemView.findViewById(R.id.btnLibrary).requestFocus())
                    libraryFocusPending = false;
            }
        };
        grid.addOnScrollListener(pendingFocusScroll);
        grid.smoothScrollToPosition(position);
    }

    private void cancelPendingFocusScroll() {
        if (pendingFocusScroll != null) {
            grid.removeOnScrollListener(pendingFocusScroll);
            pendingFocusScroll = null;
            grid.stopScroll();
        }
    }

    private int dockReturnPosition() {
        if (dockRow && adapter != null) {
            return Math.max(0, Math.min(lastFocused, adapter.getItemCount() - 1));
        }
        return lastFocused % COLUMNS;
    }

    private void focusTile(int position) {
        libraryFocusPending = false;
        cancelPendingFocusScroll();
        if (adapter.appCount() == 0) {
            if (adapter.isLibraryAt(0)) focusLibrary();
            else firstTopButton().requestFocus();
            return;
        }
        final int pos = adapter.focusablePosition(Math.max(0, Math.min(position, adapter.appCount() - 1)));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        if (!animations) {
            grid.scrollToPosition(pos);
            grid.post(() -> {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                if (h != null) {
                    View card = h.itemView.findViewById(R.id.card);
                    if (card != null) {
                        if (card.isFocused()) onTileFocused(pos, card);
                        else card.requestFocus();
                    }
                }
            });
            return;
        }
        pendingFocusScroll = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                if (pendingFocusScroll != this) return;
                pendingFocusScroll = null;
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) {
                    if (target.isFocused()) onTileFocused(pos, target);
                    else target.requestFocus();
                }
            }
        };
        grid.addOnScrollListener(pendingFocusScroll);
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
            if (h != null) {
                cancelPendingFocusScroll();
                View target = h.itemView.findViewById(R.id.card);
                if (target != null) {
                    if (target.isFocused()) onTileFocused(pos, target);
                    else target.requestFocus();
                }
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    /** Some TVs (Coocaa OS) set the network time without ACTION_TIME_CHANGED, so keep checking until it is valid. */
    private final Runnable clockCheck = this::updateClockVisibility;

    private void updateClockVisibility() {
        boolean valid = System.currentTimeMillis() >= TIME_VALID_AFTER_MS;
        clockBlock.setVisibility(valid ? View.VISIBLE : View.INVISIBLE);
        handler.removeCallbacks(clockCheck);
        if (valid) {
            updateGreeting();
        } else {
            handler.postDelayed(clockCheck, 1_000L);
        }
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        String festiveGreeting = festive ? festival.greetingText() : "";
        if (!festiveGreeting.isEmpty()) {
            greeting.setText(festiveGreeting);
            greeting.setTextColor(liftTowardsWhite(festival.accentColor(), 0.25f));
        } else {
            greeting.setText(greetingName.isEmpty() ? getString(res)
                    : getString(R.string.greeting_with_name, getString(res), greetingName));
            greeting.setTextColor(greetingColor);
        }
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    // ---- race-weekend panel (a theme's race_calendar) -----------------------------------------------

    private static final int RACE_RED = 0xFFFF3B2F;

    /**
     * Shows or hides the race-weekend panel for this minute. Text only, set once a minute with the clock:
     * nothing animates, so the panel costs the GPU nothing between ticks.
     */
    private void updateRace() {
        if (raceCalendar.isEmpty()) return;
        RaceWeekend.State s = RaceWeekend.at(raceCalendar, java.time.Instant.now());
        if (s == null) {
            racePanel.setVisibility(View.GONE);
            return;
        }
        RaceWeekend.Race race = s.race;
        raceKicker.setText(race.place.isEmpty()
                ? getString(R.string.race_kicker_no_place, race.round, race.shortName())
                : getString(R.string.race_kicker, race.round, race.shortName(), race.place));

        if (s.live != null) {
            SpannableString live = new SpannableString("\u25CF  " + getString(R.string.race_live, s.live.title()));
            live.setSpan(new android.text.style.ForegroundColorSpan(RACE_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            raceHeadline.setText(live);
        } else if (s.next != null) {
            String in = RaceWeekend.countdown(s.untilNext);
            raceHeadline.setText(s.next.isRace() ? getString(R.string.race_lights_out, in)
                    : getString(R.string.race_next, s.next.title(), in));
        } else {
            raceHeadline.setText(R.string.race_finished);
        }

        // FP1 FRI 11:00 PM   FP2 SAT 2:30 AM ...: done sessions fade, the live one is red, the next one bright.
        java.time.format.DateTimeFormatter when = java.time.format.DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.is24HourFormat(this) ? "EEE HH:mm" : "EEE h:mm a", Locale.getDefault())
                .withZone(java.time.ZoneId.systemDefault());
        android.text.SpannableStringBuilder row = new android.text.SpannableStringBuilder();
        for (int i = 0; i < race.sessions.size(); i++) {
            RaceWeekend.Session session = race.sessions.get(i);
            if (i > 0) row.append("     ");
            int start = row.length();
            row.append(session.shortName()).append("  ").append(when.format(session.start).toUpperCase(Locale.getDefault()));
            int color;
            switch (s.statuses.get(i)) {
                case DONE: color = 0x47FFFFFF; break;
                case LIVE: color = RACE_RED; break;
                case NEXT: color = 0xFFFFFFFF; break;
                default: color = 0x99FFFFFF;
            }
            row.setSpan(new android.text.style.ForegroundColorSpan(color), start, row.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        raceSessions.setText(row);
        racePanel.setVisibility(View.VISIBLE);
    }

    private void refreshPillsAfterNextDraw() {
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                // Posted from inside the frame's traversal, so it runs after that frame is drawn. A post straight
                // from onResume would run first: the frame is only scheduled after onResume returns.
                handler.post(() -> {
                    if (isDestroyed()) return;
                    updateWifiLabel();
                    updateInputsButton();
                });
                return true;
            }
        });
        root.invalidate();   // a resume that changes nothing on screen still draws once, so the refresh runs
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        // The diya's flame: 0 is out (offline), 1 to 4 follow the Wi-Fi signal, Ethernet burns steady at 4.
        int flame = 0;
        boolean steady = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
                flame = 4;
                steady = true;
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                flame = info != null ? Math.max(1, Math.min(4, WifiManager.calculateSignalLevel(info.getRssi(), 5))) : 3;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        boolean vpnDown = !vpn && online && !vpnApp.isEmpty();
        if (vpn || vpnDown) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            String badge = getString(vpn ? R.string.vpn : R.string.vpn_off);
            VpnBadge span = new VpnBadge(vpn ? getDrawable(R.drawable.ic_lock) : null, density);
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The total paddings
            // include the Wi-Fi icon.
            TextPaint paint = btnWifi.getPaint();
            float room = btnWifi.getMaxWidth() - btnWifi.getTotalPaddingStart() - btnWifi.getTotalPaddingEnd()
                    - span.getSize(paint, badge, 0, badge.length(), null) - paint.measureText(" ") - 2 * density;
            String name = TextUtils.ellipsize(text, paint, Math.max(0, room), TextUtils.TruncateAt.END).toString();
            // A long badge ("VPN отключён") can leave room for a stub like "MT…", which says nothing and, after
            // rounding, can push the badge itself out of the pill. Show the badge alone then.
            // The space stays even then: a line holding only the span gets no font metrics and collapses.
            text = name.length() < text.length() && name.length() < 5 ? "" : name;
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(span, text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!TextUtils.equals(btnWifi.getText(), label)) btnWifi.setText(label);
        } else if (!TextUtils.equals(btnWifi.getText(), text)) {
            btnWifi.setText(text);
        }
        if (wifiIconOnly) {
            btnWifi.setContentDescription(btnWifi.getText());
            btnWifi.setText(null);
        }
        if (festive && diya != null) {
            if (online && flame == 0) flame = 3;   // online some other way (cellular, USB tethering)
            diya.setSteady(steady);
            diya.setFlame(flame);
        } else {
            if (icon != wifiIcon) {
                wifiIcon = icon;
                btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            }
        }
    }

    /** config.xml icon_only_buttons. */
    private boolean iconOnlyButtons;
    /** icon_only_buttons without wifi_label_width: the Wi-Fi pill shows no network name. */
    private boolean wifiIconOnly;
    /** The app library button in the top bar (library_button). */
    private View btnLibraryTop;
    /** The open library came from that button: closing it returns there instead of scrolling to the grid's end. */
    private boolean libraryFromTopBar;

    /** Turns a labelled top pill into the round TopButtonIcon shape: no text, the icon centred by even padding. */
    private void makeIconOnly(TextView pill) {
        pill.setContentDescription(pill.getText());
        pill.setText(null);
        pill.setMaxWidth(Integer.MAX_VALUE);
        int pad = Math.round(10 * density);
        pill.setPaddingRelative(pad, 0, pad, 0);
        pill.setCompoundDrawablePadding(0);
        pill.getLayoutParams().width = Math.round(42 * density);
    }

    // ---- festive skin ------------------------------------------------------------------------------

    /**
     * Swaps the pill icons for the festival's: a bell for Inputs that swings when focused, a diya for the
     * network whose flame follows the connection, and a marigold for Settings that turns when focused.
     */
    private void applyFestiveSkin(TextView inputs, TextView settings) {
        bell = new BellDrawable(density);
        marigold = new MarigoldDrawable(density);
        diya = new DiyaDrawable(density);
        bell.setReducedMotion(reducedMotion);
        marigold.setReducedMotion(reducedMotion);
        diya.setReducedMotion(reducedMotion);
        setFestiveIcon(inputs, bell);
        setFestiveIcon(btnWifi, diya);
        setFestiveIcon(settings, marigold);
        addFocusAction(inputs, bell::ring);
        addFocusAction(settings, marigold::turn);
    }

    private static void setFestiveIcon(TextView pill, Drawable icon) {
        // The pills tint their icons to follow focus; the festive ones keep their own colours.
        pill.setCompoundDrawableTintList(null);
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
    }

    /** Runs {@code action} when the view gains focus, keeping any focus listener it already had. */
    private static void addFocusAction(View view, Runnable action) {
        View.OnFocusChangeListener previous = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            if (previous != null) previous.onFocusChange(v, focused);
            if (focused) action.run();
        });
    }

    private void playPetalShower() {
        if (isFinishing() || isDestroyed()) return;
        PetalShowerView shower = new PetalShowerView(this);
        root.addView(shower, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shower.play(60, 2200, () -> root.removeView(shower));
    }

    private static int liftTowardsWhite(int argb, float amount) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : systemIntent(Settings.ACTION_WIFI_SETTINGS));
    }


    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged.
     */
    private Intent systemIntent(String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(getPackageManager()) != null) return i;
        return new Intent(standardAction);   // nothing found: let open() show the can't-open toast
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                // Inputs hidden in Settings > Inputs are left out of the menu, so they don't count here either.
                if (info.isPassthroughInput() && !info.isHidden(this)) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9): a
     *  service, or an activity on sets whose panel is a dialog (BYINTEK U4's QuickSourceActivity). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            if (getPackageManager().resolveActivity(i, 0) != null) {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Throwable e) {
            try {
                String pkg = i.getPackage();
                if (pkg != null) {
                    Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (fallback == null) fallback = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (fallback != null) {
                        startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return;
                    }
                }
            } catch (Throwable ignored) {}
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        if (openPopup != null) openPopup.dismiss();
        View anchor = findViewById(R.id.btnInputs);
        setPillHighlighted(anchor, true);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = glassPopup(panel, width);
        popup.setOnDismissListener(() -> {
            if (panel.getTag() == null && (popup.getContentView() == null || popup.getContentView().getTag() == null)) {
                SoundManager.getInstance(MainActivity.this).playBack();
            }
            if (openPopup == popup) openPopup = null;
            anchor.setSelected(false);
            setPillHighlighted(anchor, false);
            focusTile(dockReturnPosition());
        });

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        boolean tunerIsAnalog = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the
            // port it sits on, so the port and the device would each get a row (Vestel MT7332: "HDMI 1"
            // and "HDMI Apple TV"). The device takes its port's row instead, and a bare port is listed
            // only when nothing on it speaks CEC.
            Set<String> inputIds = new HashSet<>();
            for (TvInputInfo info : inputs) inputIds.add(info.getId());
            Map<String, List<TvInputInfo>> cecDevices = new HashMap<>();
            for (TvInputInfo info : inputs) {
                String parent = info.getParentId();
                if (parent == null || !inputIds.contains(parent)) continue;
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                List<TvInputInfo> onPort = cecDevices.get(parent);
                if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
                onPort.add(info);
            }
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    // Among tuners of the same kind, skip an analog input when another is present.
                    // MediaTek names them AnalogInputService and TunerInputService; alphabetical order
                    // would pick the analog one, and Live TV then opens a dead ATV source.
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    boolean analog = info.getId().toLowerCase(Locale.ROOT).contains("analog");
                    boolean better = tunerId == null
                            || (hw && !tunerIsHw)
                            || (hw == tunerIsHw && tunerIsAnalog && !analog)
                            || (hw == tunerIsHw && analog == tunerIsAnalog
                                    && info.getId().compareTo(tunerId) < 0);
                    if (better) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                        tunerIsAnalog = analog;
                    }
                }
                // Inputs hidden in the TV's Settings > Inputs stay out of the menu too.
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                // A CEC device's row is added with its port, below.
                if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                CharSequence custom = info.loadCustomLabel(this);
                boolean named = custom != null && custom.length() > 0 && !custom.toString().equals(label);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                List<TvInputInfo> devices = cecDevices.get(info.getId());
                if (devices != null) {
                    // "Apple TV · HDMI 1": the device's own name (or the one given to it or to its port in
                    // Settings > Inputs), then the port. Opening the device's input also makes it the CEC
                    // active source, which wakes it.
                    for (TvInputInfo device : devices) {
                        CharSequence name = device.loadCustomLabel(this);
                        if (name == null || name.length() == 0) name = named ? custom : device.loadLabel(this);
                        String deviceLabel = name == null || name.length() == 0 ? label : name + " \u00B7 " + label;
                        final String deviceId = device.getId();
                        int state = hdmiStateBogus ? -1 : tim.getInputState(deviceId);
                        addInputRow(rows, popup, R.drawable.ic_input, deviceLabel, state, () -> openInput(deviceId));
                    }
                    continue;
                }
                // The name given in Settings > Inputs (e.g. "MacBook Pro") goes first, the port after it.
                if (named) label = custom + " \u00B7 " + label;
                final String id = info.getId();
                // Analog AV jacks have no hotplug detection, and some TVs report them as always connected
                // (TCL Smart TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
                int state = !hdmi || hdmiStateBogus ? -1 : tim.getInputState(id);
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            // Tune that input only when the Live TV package itself handles the URI.
            // The unrestricted input player also resolves it (that is how HDMI opens), but on the
            // AIWA AI PONT SA that player stays black, and the Live TV package does not handle the
            // URI at all, which used to toast "Not available on this TV". Keep the leanback activity
            // then: it is the same path as the Live TV tile. Do not drop the package restriction.
            Intent tuned = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId),
                            "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
            if (tuned.resolveActivity(getPackageManager()) != null) liveTv = tuned;
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> {
                SoundManager.getInstance(MainActivity.this).playEnter();
                open(liveTvIntent);
            });
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, Math.min(panel.getMeasuredWidth(), (int) (360 * density))));

        int alignment = getResources().getBoolean(R.bool.rtl_menu_bar) ? Gravity.END : Gravity.START;
        dropIn(popup, panel, anchor, alignment);
        rows.getChildAt(0).requestFocus();
    }

    private PopupWindow glassPopup(View panel, int width) {
        FrameLayout container = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                int code = event.getKeyCode();
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
                            || code == KeyEvent.KEYCODE_TV_INPUT || code == KeyEvent.KEYCODE_TV || code == 178 || code == 170
                            || code == KeyEvent.KEYCODE_SETTINGS || code == KeyEvent.KEYCODE_MENU
                            || code == 83 || code == 4077 || code == 4020 || code == 4056) {
                        setTag(Boolean.TRUE);
                        SoundManager.getInstance(MainActivity.this).playBack();
                        if (openPopup != null) {
                            openPopup.dismiss();
                            openPopup = null;
                        }
                        return true;
                    }
                }
                SoundManager.getInstance(MainActivity.this).handleKeyEvent(event);
                return super.dispatchKeyEvent(event);
            }
        };
        container.addView(panel);
        final PopupWindow popup = new PopupWindow(container, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (container.getTag() == null) {
                SoundManager.getInstance(MainActivity.this).playBack();
            }
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        View root = findViewById(R.id.root);
        row.setOnClickListener(v -> {
            if (popup.getContentView() != null) popup.getContentView().setTag(Boolean.TRUE);
            popup.dismiss();
            if (root != null) root.post(action::run);
            else action.run();
        });
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) {
                if (popup.getContentView() != null) popup.getContentView().setTag(Boolean.TRUE);
                popup.dismiss();
                if (root != null) root.post(action::run);
                else action.run();
            }
            return true;
        });
        rows.addView(row);
    }

    // ---- launcher settings and module cards -------------------------------------------------------

    private void showWifiMenu() {
        if (openPopup != null) openPopup.dismiss();
        View anchor = findViewById(R.id.btnWifi);
        setPillHighlighted(anchor, true);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (260 * density));
        popup.setOnDismissListener(() -> {
            if (panel.getTag() == null && (popup.getContentView() == null || popup.getContentView().getTag() == null)) {
                SoundManager.getInstance(MainActivity.this).playBack();
            }
            if (openPopup == popup) openPopup = null;
            anchor.setSelected(false);
            setPillHighlighted(anchor, false);
            focusTile(dockReturnPosition());
        });

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        String connectedSsid = null;
        if (wm != null) {
            try {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getSSID() != null) {
                    connectedSsid = info.getSSID().replace("\"", "");
                    if ("<unknown ssid>".equalsIgnoreCase(connectedSsid)) connectedSsid = null;
                }
            } catch (Throwable ignored) {}
        }

        List<ScanResult> scanList = new ArrayList<>();
        if (wm != null) {
            try {
                List<ScanResult> raw = wm.getScanResults();
                if (raw != null) {
                    Set<String> seen = new HashSet<>();
                    for (ScanResult sr : raw) {
                        if (sr.SSID != null && !sr.SSID.isEmpty() && !seen.contains(sr.SSID)) {
                            seen.add(sr.SSID);
                            scanList.add(sr);
                        }
                    }
                    Collections.sort(scanList, (a, b) -> b.level - a.level);
                }
            } catch (Throwable ignored) {}
        }

        if (connectedSsid != null) {
            String label = "✓  " + connectedSsid + " (Connected)";
            addSettingsMenuRow(rows, popup, label, () -> open(systemIntent(Settings.ACTION_WIFI_SETTINGS)));
        }

        int count = 0;
        for (ScanResult sr : scanList) {
            if (connectedSsid != null && sr.SSID.equals(connectedSsid)) continue;
            String signal = sr.level > -60 ? "●●●" : (sr.level > -75 ? "●●○" : "●○○");
            String label = sr.SSID + "  (" + signal + ")";
            addSettingsMenuRow(rows, popup, label, () -> open(systemIntent(Settings.ACTION_WIFI_SETTINGS)));
            count++;
            if (count >= 6) break;
        }

        if (connectedSsid == null && scanList.isEmpty()) {
            addSettingsMenuRow(rows, popup, "Wi-Fi (Disconnected)", () -> open(systemIntent(Settings.ACTION_WIFI_SETTINGS)));
        }

        addSettingsMenuRow(rows, popup, getString(R.string.wifi) + " Settings", () -> open(systemIntent(Settings.ACTION_WIFI_SETTINGS)));

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (260 * density), panel.getMeasuredWidth()));
        dropIn(popup, panel, anchor, getResources().getBoolean(R.bool.rtl_menu_bar)
                ? Gravity.START : Gravity.END);
        if (rows.getChildCount() > 0) {
            rows.getChildAt(0).requestFocus();
        }
    }

    /** Show all launcher and TV settings directly in one clean glass popup. */
    private void showSettingsMenu() {
        if (openPopup != null) openPopup.dismiss();
        View anchor = findViewById(R.id.btnSettings);
        setPillHighlighted(anchor, true);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (320 * density));

        // Backdrop blur/dim scrim
        View heroScrim = findViewById(R.id.heroScrim);
        if (heroScrim != null) {
            heroScrim.setVisibility(View.VISIBLE);
            heroScrim.animate().alpha(0.75f).setDuration(240).start();
        }
        popup.setOnDismissListener(() -> {
            if (panel.getTag() == null && (popup.getContentView() == null || popup.getContentView().getTag() == null)) {
                SoundManager.getInstance(MainActivity.this).playBack();
            }
            if (openPopup == popup) openPopup = null;
            if (heroScrim != null) {
                heroScrim.animate().alpha(0f).setDuration(240).withEndAction(() -> heroScrim.setVisibility(View.GONE)).start();
            }
            anchor.setSelected(false);
            setPillHighlighted(anchor, false);
            if (!reordering && !dockEditing
                    && !"opening_modal".equals(panel.getTag())
                    && (allAppsModalOverlay == null || allAppsModalOverlay.getVisibility() != View.VISIBLE)
                    && (fileBrowserOverlay == null || fileBrowserOverlay.getVisibility() != View.VISIBLE)) {
                focusTile(dockReturnPosition());
            }
        });

        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);

        // Sound toggle row: "Sound: On" / "Sound: Off"
        SoundManager soundManager = SoundManager.getInstance(this);
        addSettingsToggleRow(rows, "Sound: " + (soundManager.isEnabled() ? getString(R.string.on) : getString(R.string.off)), () -> {
            boolean next = !soundManager.isEnabled();
            soundManager.setEnabled(next);
            return "Sound: " + (next ? getString(R.string.on) : getString(R.string.off));
        });

        // Screensaver choice: "solat" (Countdown Solat) vs "clock" (Clocks)
        String currentScreensaverType = choices.getString("screensaver_type", "solat");
        final boolean[] isSolatScreensaver = {"solat".equals(currentScreensaverType)};
        addSettingsToggleRow(rows, "Screensaver: " + (isSolatScreensaver[0] ? "Countdown Solat" : "Clocks"), () -> {
            isSolatScreensaver[0] = !isSolatScreensaver[0];
            String nextType = isSolatScreensaver[0] ? "solat" : "clock";
            choices.edit().putString("screensaver_type", nextType).apply();
            tryConfigureScreensaverComponent();
            return "Screensaver: " + (isSolatScreensaver[0] ? "Countdown Solat" : "Clocks");
        });

        // Default launcher setting
        if (isDefaultLauncher()) {
            addSettingsMenuRow(rows, popup, R.string.default_launcher_active, this::requestDefaultLauncher);
        } else {
            addSettingsMenuRow(rows, popup, R.string.set_default_launcher, this::showSetDefaultLauncherDialog);
        }

        // Add app to dock
        if (dockRow) {
            addSettingsMenuRow(rows, popup, R.string.dock_add, () -> {
                panel.setTag("opening_modal");
                showAddAppsOnDockModal();
            });
        }

        // Change wallpaper apps dock
        if (dockRow) {
            addSettingsMenuRow(rows, popup, R.string.dock_wallpaper_change, () -> {
                panel.setTag("opening_modal");
                showChangeWallpaperDockAppsPicker();
            });
        }

        // Remove app from dock
        if (dockRow) {
            addSettingsMenuRow(rows, popup, R.string.dock_remove, this::startDockRemove);
        }

        // Reorder apps
        addSettingsMenuRow(rows, popup, R.string.reorder, this::toggleReorder);

        // TV system settings
        addSettingsMenuRow(rows, popup, R.string.tv_settings, () -> open(systemIntent(Settings.ACTION_SETTINGS)));

        // Reset theme launcher (reset choices)
        addSettingsMenuRow(rows, popup, R.string.follow_theme, () -> {
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY)
                    .remove("animations").remove("advanced_effects")
                    .remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove("screensaver_type")
                    .remove("sound_effects_enabled")
                    .remove("dock_apps").apply();
            SoundManager.getInstance(this).setEnabled(true);
            try {
                File orderFile = Order.file(this);
                if (orderFile != null && orderFile.exists()) orderFile.delete();
            } catch (Throwable ignored) {}
            clearCustomDockWallpapers();
            recreate();
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (320 * density), panel.getMeasuredWidth()));
        dropIn(popup, panel, anchor, getResources().getBoolean(R.bool.rtl_menu_bar)
                ? Gravity.START : Gravity.END);
        if (rows.getChildCount() > 0) {
            rows.getChildAt(0).requestFocus();
        }
    }

    private void showLauncherSettingsModal() {
        showSettingsMenu();
    }

    public static boolean isDefaultLauncher(Context context) {
        if (context == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return context.getPackageName().equals(resolveInfo.activityInfo.packageName);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean isDefaultLauncher() {
        return isDefaultLauncher(this);
    }

    public void requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    try {
                        startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME));
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (Throwable ignored) {}

        try {
            Intent chooser = new Intent(Intent.ACTION_MAIN);
            chooser.addCategory(Intent.CATEGORY_HOME);
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(chooser, getString(R.string.set_default_launcher)));
        } catch (Throwable ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored2) {}
        }
    }

    public void trySilentSetDefaultLauncher() {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"cmd", "package", "set-home-activity", "--user", "0", getPackageName() + "/.MainActivity"});
            p.waitFor();
        } catch (Throwable ignored) {}
    }

    public void tryConfigureScreensaverComponent() {
        try {
            android.provider.Settings.Secure.putString(getContentResolver(), "screensaver_components", getPackageName() + "/.ClockDream");
            android.provider.Settings.Secure.putInt(getContentResolver(), "screensaver_enabled", 1);
        } catch (Throwable ignored) {}
    }

    private void checkAndPromptDefaultLauncher() {
        trySilentSetDefaultLauncher();
        tryConfigureScreensaverComponent();
        if (isDefaultLauncher()) return;
        SharedPreferences prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        boolean promptDismissed = prefs.getBoolean("default_launcher_prompt_dismissed", false);
        if (promptDismissed) return;

        handler.postDelayed(this::showSetDefaultLauncherDialog, 600);
    }

    public void showSetDefaultLauncherDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (isDefaultLauncher()) return;
        if (defaultLauncherDialog != null && defaultLauncherDialog.isShowing()) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_force_default_launcher, root, false);
        TextView btnLater = panel.findViewById(R.id.btnLater);
        TextView btnSetDefault = panel.findViewById(R.id.btnSetDefault);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (480 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.65f);
        }

        View.OnClickListener onSetDefault = v -> {
            SoundManager.getInstance(MainActivity.this).playEnter();
            if (defaultLauncherDialog != null) {
                defaultLauncherDialog.dismiss();
                defaultLauncherDialog = null;
            }
            trySilentSetDefaultLauncher();
            requestDefaultLauncher();
        };

        View.OnClickListener onLater = v -> {
            SoundManager.getInstance(MainActivity.this).playBack();
            SharedPreferences prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean("default_launcher_prompt_dismissed", true).apply();
            if (defaultLauncherDialog != null) {
                defaultLauncherDialog.dismiss();
                defaultLauncherDialog = null;
            }
        };

        btnSetDefault.setOnClickListener(onSetDefault);
        btnLater.setOnClickListener(onLater);

        View.OnKeyListener keyListener = (v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (v.getId() == R.id.btnSetDefault) onSetDefault.onClick(v);
                    else onLater.onClick(v);
                }
                return true;
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                btnLater.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                btnSetDefault.requestFocus();
                return true;
            }
            return false;
        };
        btnSetDefault.setOnKeyListener(keyListener);
        btnLater.setOnKeyListener(keyListener);

        dialog.setOnCancelListener(d -> {
            SharedPreferences prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean("default_launcher_prompt_dismissed", true).apply();
            defaultLauncherDialog = null;
        });

        dialog.setOnDismissListener(d -> {
            if (defaultLauncherDialog == dialog) defaultLauncherDialog = null;
        });

        defaultLauncherDialog = dialog;
        dialog.show();
        btnSetDefault.post(btnSetDefault::requestFocus);
    }

    private void syncTimezone(String tzId) {
        if (tzId == null || tzId.isEmpty()) return;
        TextClock clock = findViewById(R.id.clock);
        TextClock date = findViewById(R.id.date);
        if (clock != null) clock.setTimeZone(tzId);
        if (date != null) date.setTimeZone(tzId);
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) am.setTimeZone(tzId);
        } catch (Throwable ignored) {}
    }

    private void updateWeatherUi(String city, int temp, int code) {
        TextView tvCity = findViewById(R.id.weatherCity);
        TextView tvTemp = findViewById(R.id.weatherTemp);
        ImageView ivIcon = findViewById(R.id.weatherIcon);

        if (tvCity != null) tvCity.setText(city);
        if (tvTemp != null) tvTemp.setText(temp + "°C");
        if (ivIcon != null) {
            int iconRes;
            if (code == 0) iconRes = R.drawable.ic_weather_sunny;
            else if (code >= 1 && code <= 3) iconRes = R.drawable.ic_weather_partly_cloudy;
            else if (code >= 45 && code <= 48) iconRes = R.drawable.ic_weather_cloudy;
            else if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) iconRes = R.drawable.ic_weather_rain;
            else if (code >= 95) iconRes = R.drawable.ic_weather_thunder;
            else iconRes = R.drawable.ic_weather_partly_cloudy;
            ivIcon.setImageResource(iconRes);
        }
        if (weatherPill != null) {
            setPillHighlighted(weatherPill, weatherPill.isFocused() || (openPopup != null && weatherPill.isSelected()));
        }
    }

    private void fetchWeatherAsync(boolean force) {
        SharedPreferences sp = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        long lastUpdate = sp.getLong("weather_last_ts", 0L);
        long now = System.currentTimeMillis();

        // Always show cached values first
        updateWeatherUi(
                sp.getString("weather_city", "Bekasi"),
                sp.getInt("weather_temp", 28),
                sp.getInt("weather_code", 2)
        );

        if (!force && (now - lastUpdate < 30 * 60 * 1000L)) {
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String city = sp.getString("weather_city", "Bekasi");
                double lat = -6.2348;
                double lon = 106.9896;

                // 1. IP Geolocation via ipwho.is
                try {
                    URL ipUrl = new URL("https://ipwho.is/");
                    HttpURLConnection ipConn = (HttpURLConnection) ipUrl.openConnection();
                    ipConn.setConnectTimeout(4000);
                    ipConn.setReadTimeout(4000);
                    if (ipConn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(ipConn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        JSONObject json = new JSONObject(sb.toString());
                        if (json.optBoolean("success", false)) {
                            city = json.optString("city", city);
                            lat = json.optDouble("latitude", lat);
                            lon = json.optDouble("longitude", lon);
                            JSONObject tzObj = json.optJSONObject("timezone");
                            if (tzObj != null) {
                                String tzId = tzObj.optString("id", null);
                                if (tzId != null && !tzId.isEmpty()) {
                                    sp.edit().putString("weather_timezone", tzId).apply();
                                    runOnUiThread(() -> syncTimezone(tzId));
                                }
                            }
                        }
                    }
                    ipConn.disconnect();
                } catch (Throwable ignored) {}

                // 2. Weather via Open-Meteo
                URL weatherUrl = new URL(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true",
                        lat, lon));
                HttpURLConnection wConn = (HttpURLConnection) weatherUrl.openConnection();
                wConn.setConnectTimeout(4000);
                wConn.setReadTimeout(4000);
                if (wConn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(wConn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject json = new JSONObject(sb.toString());
                    JSONObject cur = json.optJSONObject("current_weather");
                    if (cur != null) {
                        double t = cur.optDouble("temperature", 28.0);
                        int tempInt = (int) Math.round(t);
                        int code = cur.optInt("weathercode", 2);

                        sp.edit()
                                .putString("weather_city", city)
                                .putInt("weather_temp", tempInt)
                                .putInt("weather_code", code)
                                .putLong("weather_last_ts", System.currentTimeMillis())
                                .apply();

                        final String finalCity = city;
                        runOnUiThread(() -> updateWeatherUi(finalCity, tempInt, code));
                    }
                }
                wConn.disconnect();
            } catch (Throwable ignored) {}
        });
    }

    private void showWeatherModal() {
        if (openPopup != null) openPopup.dismiss();
        View anchor = findViewById(R.id.weatherPill);
        if (anchor == null) return;
        setPillHighlighted(anchor, true);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = glassPopup(panel, (int) (280 * density));

        View heroScrim = findViewById(R.id.heroScrim);
        if (heroScrim != null) {
            heroScrim.setVisibility(View.VISIBLE);
            heroScrim.animate().alpha(0.75f).setDuration(240).start();
        }
        popup.setOnDismissListener(() -> {
            if (panel.getTag() == null && (popup.getContentView() == null || popup.getContentView().getTag() == null)) {
                SoundManager.getInstance(MainActivity.this).playBack();
            }
            if (openPopup == popup) openPopup = null;
            if (heroScrim != null) {
                heroScrim.animate().alpha(0f).setDuration(240).withEndAction(() -> heroScrim.setVisibility(View.GONE)).start();
            }
            anchor.setSelected(false);
            setPillHighlighted(anchor, false);
            focusTile(dockReturnPosition());
        });

        SharedPreferences sp = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        String city = sp.getString("weather_city", "Bekasi");
        int temp = sp.getInt("weather_temp", 28);
        int code = sp.getInt("weather_code", 2);

        String condition;
        if (code == 0) condition = "Clear Sky";
        else if (code <= 3) condition = "Partly Cloudy";
        else if (code <= 48) condition = "Overcast / Fog";
        else if (code <= 82) condition = "Rain / Showers";
        else if (code >= 95) condition = "Thunderstorm";
        else condition = "Partly Cloudy";

        addSettingsMenuRow(rows, popup, city + " • " + temp + "°C", () -> {});
        addSettingsMenuRow(rows, popup, "Status: " + condition, () -> {});
        addSettingsMenuRow(rows, popup, R.string.refresh_weather, () -> {
            fetchWeatherAsync(true);
            Toast.makeText(this, "Refreshing weather...", Toast.LENGTH_SHORT).show();
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max((int) (280 * density), panel.getMeasuredWidth()));
        dropIn(popup, panel, anchor, Gravity.START);
        if (rows.getChildCount() > 0) {
            rows.getChildAt(0).requestFocus();
        }
    }

    private void addSettingsToggleRow(LinearLayout rows, String title, java.util.function.Supplier<String> onToggle) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        TextView tvTitle = row.findViewById(R.id.title);
        tvTitle.setText(title);
        Runnable toggle = () -> {
            boolean wasEnabled = SoundManager.getInstance(MainActivity.this).isEnabled();
            SoundManager.getInstance(MainActivity.this).playEnter();
            String newTitle = onToggle.get();
            if (newTitle != null) tvTitle.setText(newTitle);
            if (!wasEnabled && SoundManager.getInstance(MainActivity.this).isEnabled()) {
                SoundManager.getInstance(MainActivity.this).playEnter();
            }
        };
        row.setOnClickListener(v -> toggle.run());
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            if (pressed[0]) {
                pressed[0] = false;
                toggle.run();
            }
            return true;
        });
        rows.addView(row);
    }

    private void addSettingsMenuRow(LinearLayout rows, PopupWindow popup, int title, Runnable action) {
        addSettingsMenuRow(rows, popup, getString(title), action);
    }

    private void addSettingsMenuRow(LinearLayout rows, PopupWindow popup, String title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        View root = findViewById(R.id.root);
        row.setOnClickListener(v -> {
            if (popup.getContentView() != null) popup.getContentView().setTag(Boolean.TRUE);
            popup.dismiss();
            if (root != null) root.post(action::run);
            else action.run();
        });
        // The OK-up after opening the menu must not activate its first row.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) {
                if (popup.getContentView() != null) popup.getContentView().setTag(Boolean.TRUE);
                popup.dismiss();
                if (root != null) root.post(action::run);
                else action.run();
            }
            return true;
        });
        rows.addView(row);
    }

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()
                || reordering) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
        prewarmAppStats();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        setPillHighlighted(anchor, true);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
            anchor.setSelected(false);
            setPillHighlighted(anchor, false);
            focusTile(dockReturnPosition());
        });
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }
}

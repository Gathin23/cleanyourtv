package com.gathin.geogenius;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

/**
 * GeoGenius v2 TV UI — Minimalist 55% Center Stage + Icon-First Circular Orbs +
 * Vertical Liquid Score Tube:
 * - Layer 1 (homeLayer):
 *     - Top Bar: Brand Title + 1-Click Daily Challenge Pill + Region Selector Pill + 2 Skill Tiers (🌱 Novice, 🏆 Expert) + 🔊 Toggle
 *     - Center Stage: 4 x 2 Grid of 8 Circular Mode Orbs:
 *         Row 1: Flag ➔ Name, Name ➔ Flag, Spell It, Capitals
 *         Row 2: Wonders, Snap Match, Spot the Lie, World Atlas
 * - Layer 2 (gameLayer):
 *     - Compact 55% Center Stage + Right-Flank Vertical Liquid Score Tube (ScoreTubeView)
 *     - Sub-stages: Multiple Choice (Flag->Name, Capitals, Wonders, Spot the Lie, Topic Quiz),
 *                   Flag Matcher (Name->Flag), Word Builder (Spell It), Snap Match (Left-to-Right),
 *                   and World Atlas Deck Browser.
 * - Layer 3 (resultLayer):
 *     - Translucent frosted-glass celebration card with ★ ★ ★, High Score badge, and circular ↻ / ✕ buttons
 * - Layer 4 (regionModalLayer):
 *     - Frosted popup to select World Region (All World, Americas, Europe, Africa, Asia, Oceania).
 */
final class GeoPanel extends FrameLayout {

    private static final int STATE_HOME = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_ATLAS = 2;
    private static final int STATE_RESULT = 3;

    private final boolean dream;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GeoSoundEngine soundEngine;

    private int uiState = STATE_HOME;
    private int skillTier = 1; // 1 = Novice, 2 = Expert
    private int regionMask = GeoData.REGION_ALL_MASK;
    private int activeMode = GeoStore.MODE_FLAG_FINDER;
    private boolean capitalReverseMode = false;

    // Layers
    private FrameLayout homeLayer;
    private FrameLayout gameLayer;
    private FrameLayout resultLayer;
    private FrameLayout regionModalLayer;
    private FrameLayout atlasTopicModalLayer;

    // Home Layer Views
    private TextView dailyBannerBtn;
    private TextView regionFilterBtn;
    private final TextView[] tierButtons = new TextView[2];
    private GeoCircleButtonView homeSoundBtn;
    private final GeoModeOrbView[] modeOrbs = new GeoModeOrbView[8];
    private View lastHomeFocus;

    // Region Modal Views
    private final TextView[] regionPills = new TextView[6];

    // Atlas Topic Modal Views
    private final TextView[] atlasTopicPills = new TextView[7];

    // Game Layer Views
    private LinearLayout centerStageCard;
    private LinearLayout hudRow;
    private ProgressDotsView progressDotsView;
    private TextView capitalDirToggleBtn;
    private GeoCircleButtonView gameSoundBtn;
    private GeoCircleButtonView gameCloseBtn;
    private ScoreTubeView scoreTubeView;
    private TextView feedbackFactBanner;
    private TextView nextQuestionBtn;
    private boolean pendingAdvance = false;
    private final Runnable advanceRunnable = this::advanceToNextQuestionNow;

    // Sub-stage containers inside Center Stage
    private LinearLayout stageMultipleChoice;
    private FlagCanvasView mcHeroFlag;
    private WonderCanvasView mcHeroWonder;
    private TextView mcPromptTitle;
    private TextView mcPromptSub;
    private final TextView[] mcChoiceBtns = new TextView[4];

    private LinearLayout stageSpotTheLie;
    private FlagCanvasView spotLieHeroFlag;
    private TextView spotLieCountryTitle;
    private TextView spotLiePromptSub;
    private final TextView[] spotLieBtns = new TextView[4];

    private LinearLayout stageFlagMatcher;
    private TextView fmCountryTitle;
    private final FlagCanvasView[] fmFlagCards = new FlagCanvasView[4];

    private LinearLayout stageWordBuilder;
    private FlagCanvasView wbHeroFlag;
    private TextView wbAttemptDots;
    private final TextView[] wbSlots = new TextView[10];
    private final TextView[] wbLetterOrbs = new TextView[8];
    private GeoCircleButtonView wbUndoBtn;

    private LinearLayout stageSnapMatch;
    private final TextView[] snapLeftBtns = new TextView[4];
    private SnapConnectorOverlayView snapConnectorView;
    private final FlagCanvasView[] snapRightFlags = new FlagCanvasView[4];

    // World Atlas Deck Browser Stage
    private LinearLayout stageAtlas;
    private TextView atlasTopicBtn;
    private TextView atlasCardRank;
    private FlagCanvasView atlasCardFlag;
    private TextView atlasCardTitle;
    private TextView atlasCardMetric;
    private TextView atlasCardSub;
    private TextView atlasCardDetail;
    private GeoCircleButtonView atlasPrevBtn;
    private GeoCircleButtonView atlasNextBtn;
    private int atlasCurrentTopic = 0;
    private int atlasCurrentCardIdx = 0;

    // Result Overlay Views
    private TextView resStarsText;
    private TextView resNewBestBadge;
    private TextView resScoreText;
    private TextView resBestSubText;
    private GeoCircleButtonView resReplayBtn;
    private GeoCircleButtonView resCloseBtn;

    // Active Round State
    private final List<GeoData.Country> roundCountries = new ArrayList<>();
    private final List<GeoData.Wonder> roundWonders = new ArrayList<>();
    private GeoData.OddQuestion currentOddQuestion;
    private int questionIndex = 0;
    private int currentScore = 0;
    private int currentStreak = 0;
    private int maxRoundScore = 100;
    private boolean inputLocked = false;
    private String currentSpokenPrompt = "";

    // Multiple Choice Answer Index
    private int correctChoiceIdx = 0;

    // Word Builder State
    private String wbTargetWord = "";
    private final boolean[] wbFixedMask = new boolean[12];
    private final char[] wbUserChars = new char[12];
    private final int[] wbSourceOrbForSlot = new int[12];
    private final char[] wbOrbChars = new char[8];
    private final boolean[] wbOrbUsed = new boolean[8];
    private int wbOrbCount = 7;
    private int wbAttemptsLeft = 3;

    // Snap Match State
    private int snapBoardIndex = 0;
    private final List<GeoData.Country> snapLeftList = new ArrayList<>();
    private final List<GeoData.Country> snapRightList = new ArrayList<>();
    private final boolean[] snapLeftMatched = new boolean[4];
    private final boolean[] snapRightMatched = new boolean[4];
    private final boolean[] snapLeftHadMiss = new boolean[4];
    private int snapSelectedLeft = -1;
    private int snapMatchesOnBoard = 0;

    GeoPanel(Context context, boolean dream) {
        super(context);
        this.dream = dream;
        this.soundEngine = new GeoSoundEngine(context);
        this.skillTier = GeoStore.getTier(context);
        this.regionMask = GeoStore.getRegionMask(context);

        setClipChildren(false);
        setClipToPadding(false);

        addView(new GeoBackdropView(context), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        homeLayer = buildHomeLayer(context);
        gameLayer = buildGameLayer(context);
        resultLayer = buildResultLayer(context);
        regionModalLayer = buildRegionModal(context);
        atlasTopicModalLayer = buildAtlasTopicModal(context);

        addView(homeLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(gameLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(resultLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(regionModalLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(atlasTopicModalLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        showHomeScreen();
    }

    boolean handleBackPress() {
        if (atlasTopicModalLayer != null && atlasTopicModalLayer.getVisibility() == View.VISIBLE) {
            closeAtlasTopicModal();
            return true;
        }
        if (regionModalLayer != null && regionModalLayer.getVisibility() == View.VISIBLE) {
            closeRegionModal();
            return true;
        }
        if (pendingAdvance) {
            advanceToNextQuestionNow();
            return true;
        }
        if (uiState == STATE_RESULT || uiState == STATE_ATLAS) {
            showHomeScreen();
            return true;
        }
        if (uiState == STATE_PLAYING) {
            if (activeMode == GeoStore.MODE_WORD_BUILDER && !inputLocked) {
                if (undoLastWordBuilderLetter()) return true;
            } else if (activeMode == GeoStore.MODE_SNAP_MATCH && snapSelectedLeft >= 0 && !inputLocked) {
                int prevLeft = snapSelectedLeft;
                snapSelectedLeft = -1;
                refreshSnapMatchStyles();
                snapLeftBtns[prevLeft].requestFocus();
                return true;
            }
            showHomeScreen();
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = event.getKeyCode();
            if (pendingAdvance) {
                if (kc == KeyEvent.KEYCODE_DPAD_CENTER || kc == KeyEvent.KEYCODE_ENTER ||
                    kc == KeyEvent.KEYCODE_DPAD_RIGHT || kc == KeyEvent.KEYCODE_BUTTON_A) {
                    advanceToNextQuestionNow();
                    return true;
                }
            }
            if (uiState == STATE_ATLAS && (atlasTopicModalLayer == null || atlasTopicModalLayer.getVisibility() != View.VISIBLE)) {
                if (kc == KeyEvent.KEYCODE_DPAD_LEFT) {
                    navigateAtlasCard(-1);
                    return true;
                } else if (kc == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    navigateAtlasCard(1);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    void start() {
        refreshHomeStats();
        if (!dream && uiState == STATE_HOME) {
            post(() -> {
                if (lastHomeFocus != null) lastHomeFocus.requestFocus();
                else if (modeOrbs[0] != null) modeOrbs[0].requestFocus();
            });
        }
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    void destroy() {
        stop();
        soundEngine.shutdown();
    }

    // -------------------------------------------------------------------------
    // 1. HOME SCREEN (Top Bar + 4x2 Grid of 8 Circular Orbs)
    // -------------------------------------------------------------------------
    private FrameLayout buildHomeLayer(Context context) {
        FrameLayout layer = new FrameLayout(context);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        layer.setPadding(dp(36), dp(18), dp(36), dp(18));

        LinearLayout centerCol = new LinearLayout(context);
        centerCol.setOrientation(VERTICAL);
        centerCol.setGravity(Gravity.CENTER_HORIZONTAL);
        centerCol.setClipChildren(false);
        centerCol.setClipToPadding(false);

        // Top Bar: GeoGenius + Daily Challenge + Region Dropdown + 2 Tiers (Novice/Expert) + Sound
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);
        topBar.setPadding(0, 0, 0, dp(14));

        TextView brand = makeText(24, 0xFFF8FAFC, true);
        brand.setText("🌍 GeoGenius");
        brand.setShadowLayer(4f * getResources().getDisplayMetrics().density, 0, 1.5f, 0xEE000000);
        topBar.addView(brand);

        addSpacer(topBar, 14, 1);

        dailyBannerBtn = makePillButton(GeoData.getDailyChallengeHeadline(), v -> {
            lastHomeFocus = v;
            startMode(GeoStore.MODE_DAILY);
        });
        dailyBannerBtn.setId(View.generateViewId());
        topBar.addView(dailyBannerBtn);

        addSpacer(topBar, 12, 1);

        regionFilterBtn = makePillButton(getRegionButtonLabel(), v -> {
            soundEngine.playPop();
            openRegionModal();
        });
        regionFilterBtn.setId(View.generateViewId());
        topBar.addView(regionFilterBtn);

        addSpacer(topBar, 12, 1);

        // 2 Skill Tiers: Novice vs Expert
        String[] tierLabels = {"🌱 Novice", "🏆 Expert"};
        for (int i = 0; i < 2; i++) {
            final int t = i + 1;
            TextView tb = makeText(12, 0xFFF8FAFC, true);
            tb.setId(View.generateViewId());
            tb.setText(tierLabels[i]);
            tb.setGravity(Gravity.CENTER);
            tb.setPadding(dp(11), dp(6), dp(11), dp(6));
            tb.setFocusable(true);
            tb.setFocusableInTouchMode(true);
            tb.setOnFocusChangeListener((v, hasFocus) -> styleTierPill(tb, t == skillTier, hasFocus));
            tb.setOnClickListener(v -> {
                skillTier = t;
                GeoStore.setTier(context, skillTier);
                soundEngine.playPop();
                refreshHomeStats();
            });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.setMarginEnd(dp(6));
            topBar.addView(tb, tlp);
            tierButtons[i] = tb;
        }

        addSpacer(topBar, 6, 1);

        homeSoundBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_SOUND, v -> {
            boolean next = !GeoStore.isSoundEnabled(context);
            GeoStore.setSoundEnabled(context, next);
            homeSoundBtn.setMutedState(!next);
            gameSoundBtn.setMutedState(!next);
            if (next) soundEngine.playPop();
        });
        homeSoundBtn.setId(View.generateViewId());
        homeSoundBtn.setMutedState(!GeoStore.isSoundEnabled(context));
        topBar.addView(homeSoundBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        centerCol.addView(topBar, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // 4 x 2 Grid of 8 Circular Glass Mode Orbs
        String[] modeTitles = {
            "Flag ➔ Name", "Name ➔ Flag", "Spell It", "Capitals",
            "Wonders", "Snap Match", "Spot the Lie", "World Atlas"
        };
        int[] modeColors = {
            0xFF34D399, // Emerald
            0xFF38BDF8, // Sky Cyan
            0xFFFBBF24, // Amber Gold
            0xFFA78BFA, // Violet
            0xFFF472B6, // Pink-Coral
            0xFF2DD4BF, // Teal
            0xFFFB923C, // Warm Orange (Spot the Lie)
            0xFF60A5FA  // Royal Blue (World Atlas)
        };

        LinearLayout gridWrap = new LinearLayout(context);
        gridWrap.setOrientation(VERTICAL);
        gridWrap.setGravity(Gravity.CENTER);
        gridWrap.setClipChildren(false);
        gridWrap.setClipToPadding(false);
        gridWrap.setPadding(dp(8), dp(4), dp(8), dp(6));

        for (int row = 0; row < 2; row++) {
            LinearLayout rLayout = new LinearLayout(context);
            rLayout.setOrientation(HORIZONTAL);
            rLayout.setGravity(Gravity.CENTER);
            rLayout.setClipChildren(false);
            rLayout.setClipToPadding(false);
            if (row == 0) rLayout.setPadding(0, 0, 0, dp(12));

            for (int col = 0; col < 4; col++) {
                final int mIdx = row * 4 + col;
                GeoModeOrbView orb = new GeoModeOrbView(context, mIdx, modeTitles[mIdx], modeColors[mIdx]);
                orb.setId(View.generateViewId());
                orb.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) lastHomeFocus = v;
                });
                orb.setOnClickListener(v -> {
                    lastHomeFocus = v;
                    soundEngine.playPop();
                    startMode(mIdx);
                });
                LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(122), dp(94));
                if (col < 3) olp.setMarginEnd(dp(18));
                rLayout.addView(orb, olp);
                modeOrbs[mIdx] = orb;
            }
            gridWrap.addView(rLayout, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        centerCol.addView(gridWrap, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Explicit D-pad Focus Network for Remote Control
        dailyBannerBtn.setNextFocusLeftId(dailyBannerBtn.getId());
        dailyBannerBtn.setNextFocusRightId(regionFilterBtn.getId());
        dailyBannerBtn.setNextFocusDownId(modeOrbs[0].getId());

        regionFilterBtn.setNextFocusLeftId(dailyBannerBtn.getId());
        regionFilterBtn.setNextFocusRightId(tierButtons[0].getId());
        regionFilterBtn.setNextFocusDownId(modeOrbs[1].getId());

        tierButtons[0].setNextFocusLeftId(regionFilterBtn.getId());
        tierButtons[0].setNextFocusRightId(tierButtons[1].getId());
        tierButtons[0].setNextFocusDownId(modeOrbs[2].getId());

        tierButtons[1].setNextFocusLeftId(tierButtons[0].getId());
        tierButtons[1].setNextFocusRightId(homeSoundBtn.getId());
        tierButtons[1].setNextFocusDownId(modeOrbs[3].getId());

        homeSoundBtn.setNextFocusLeftId(tierButtons[1].getId());
        homeSoundBtn.setNextFocusRightId(homeSoundBtn.getId());
        homeSoundBtn.setNextFocusDownId(modeOrbs[3].getId());

        // Row 0 Orbs (0..3)
        for (int c = 0; c < 4; c++) {
            GeoModeOrbView orb = modeOrbs[c];
            orb.setNextFocusUpId(c == 0 ? dailyBannerBtn.getId() : (c == 1 ? regionFilterBtn.getId() : (c == 2 ? tierButtons[0].getId() : tierButtons[1].getId())));
            orb.setNextFocusDownId(modeOrbs[c + 4].getId());
            orb.setNextFocusLeftId(c > 0 ? modeOrbs[c - 1].getId() : orb.getId());
            orb.setNextFocusRightId(c < 3 ? modeOrbs[c + 1].getId() : orb.getId());
        }

        // Row 1 Orbs (4..7)
        for (int c = 0; c < 4; c++) {
            GeoModeOrbView orb = modeOrbs[c + 4];
            orb.setNextFocusUpId(modeOrbs[c].getId());
            orb.setNextFocusDownId(orb.getId());
            orb.setNextFocusLeftId(c > 0 ? modeOrbs[c + 3].getId() : orb.getId());
            orb.setNextFocusRightId(c < 3 ? modeOrbs[c + 5].getId() : orb.getId());
        }

        LayoutParams clp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        layer.addView(centerCol, clp);
        return layer;
    }

    private String getRegionButtonLabel() {
        if ((regionMask & GeoData.REGION_ALL_MASK) == GeoData.REGION_ALL_MASK) {
            return "🌍 Region: All ▾";
        }
        for (int i = 0; i < 5; i++) {
            if (regionMask == (1 << i)) {
                return GeoData.CONTINENT_ICONS[i] + " " + GeoData.CONTINENT_NAMES[i] + " ▾";
            }
        }
        return "🌍 Filtered ▾";
    }

    // -------------------------------------------------------------------------
    // REGION SELECTOR POPUP MODAL
    // -------------------------------------------------------------------------
    private FrameLayout buildRegionModal(Context context) {
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x99040814);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(22), dp(28), dp(20));
        card.setBackground(makeStageGlassCard());

        TextView title = makeText(18, 0xFFF8FAFC, true);
        title.setText("Select Quiz Region");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(14));
        card.addView(title);

        String[] labels = {
            "🌍 All World (200 Countries)",
            "🌎 Americas (35 Countries)",
            "🌍 Europe (45 Countries)",
            "🌍 Africa (54 Countries)",
            "🌏 Asia (48 Countries)",
            "🏝️ Oceania (18 Countries)"
        };
        int[] masks = {
            GeoData.REGION_ALL_MASK,
            1 << GeoData.CONT_AMERICAS,
            1 << GeoData.CONT_EUROPE,
            1 << GeoData.CONT_AFRICA,
            1 << GeoData.CONT_ASIA,
            1 << GeoData.CONT_OCEANIA
        };

        for (int i = 0; i < 6; i++) {
            final int maskVal = masks[i];
            TextView pill = makeText(13, 0xFFE2E8F0, true);
            pill.setId(View.generateViewId());
            pill.setText(labels[i]);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(18), dp(8), dp(18), dp(8));
            pill.setFocusable(true);
            pill.setFocusableInTouchMode(true);
            pill.setOnClickListener(v -> {
                regionMask = maskVal;
                GeoStore.setRegionMask(getContext(), regionMask);
                soundEngine.playPop();
                closeRegionModal();
            });
            pill.setOnFocusChangeListener((v, hasFocus) -> styleRegionPill(pill, regionMask == maskVal, hasFocus));
            styleRegionPill(pill, regionMask == maskVal, false);

            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(270), dp(38));
            plp.bottomMargin = dp(8);
            card.addView(pill, plp);
            regionPills[i] = pill;
        }

        GeoCircleButtonView closeBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_CLOSE, v -> closeRegionModal());
        closeBtn.setId(View.generateViewId());
        card.addView(closeBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        for (int i = 0; i < 6; i++) {
            if (i > 0) regionPills[i].setNextFocusUpId(regionPills[i - 1].getId());
            else regionPills[i].setNextFocusUpId(regionPills[0].getId());

            if (i < 5) regionPills[i].setNextFocusDownId(regionPills[i + 1].getId());
            else regionPills[i].setNextFocusDownId(closeBtn.getId());
        }
        closeBtn.setNextFocusUpId(regionPills[5].getId());
        closeBtn.setNextFocusDownId(closeBtn.getId());

        overlay.addView(card, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return overlay;
    }

    private void openRegionModal() {
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        regionModalLayer.setVisibility(View.VISIBLE);
        regionModalLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        for (int i = 0; i < 6; i++) {
            int maskVal = (i == 0) ? GeoData.REGION_ALL_MASK : (1 << (i - 1));
            styleRegionPill(regionPills[i], regionMask == maskVal, false);
        }
        regionPills[0].requestFocus();
    }

    private void closeRegionModal() {
        regionModalLayer.setVisibility(View.GONE);
        regionModalLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        regionFilterBtn.setText(getRegionButtonLabel());
        refreshHomeStats();
        if (regionFilterBtn != null) regionFilterBtn.requestFocus();
    }

    private void styleRegionPill(TextView pill, boolean selected, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(12));
        if (focused) {
            gd.setColor(0xEE1E3A5F);
            gd.setStroke(dp(2), 0xFFFBBF24);
            pill.setTextColor(0xFFFDE047);
        } else if (selected) {
            gd.setColor(0xDD065F46);
            gd.setStroke(dp(1.5f), 0xFF34D399);
            pill.setTextColor(0xFFECFDF5);
        } else {
            gd.setColor(0x990F172A);
            gd.setStroke(dp(1), 0x4464748B);
            pill.setTextColor(0xFFCBD5E1);
        }
        pill.setBackground(gd);
    }

    // -------------------------------------------------------------------------
    // WORLD ATLAS TOPIC SELECTOR MODAL
    // -------------------------------------------------------------------------
    private FrameLayout buildAtlasTopicModal(Context context) {
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x99040814);
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(20), dp(28), dp(18));
        card.setBackground(makeStageGlassCard());

        TextView title = makeText(18, 0xFFF8FAFC, true);
        title.setText("Select World Atlas Deck");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));
        card.addView(title);

        for (int i = 0; i < 7; i++) {
            final int topicIdx = i;
            TextView pill = makeText(13, 0xFFE2E8F0, true);
            pill.setId(View.generateViewId());
            pill.setText(GeoData.ATLAS_TOPIC_TITLES[i]);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(18), dp(8), dp(18), dp(8));
            pill.setFocusable(true);
            pill.setFocusableInTouchMode(true);
            pill.setOnClickListener(v -> {
                atlasCurrentTopic = topicIdx;
                atlasCurrentCardIdx = 0;
                soundEngine.playPop();
                closeAtlasTopicModal();
                refreshAtlasCard();
            });
            pill.setOnFocusChangeListener((v, hasFocus) -> styleAtlasTopicPill(pill, atlasCurrentTopic == topicIdx, hasFocus));
            styleAtlasTopicPill(pill, atlasCurrentTopic == topicIdx, false);

            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(290), dp(36));
            plp.bottomMargin = dp(6);
            card.addView(pill, plp);
            atlasTopicPills[i] = pill;
        }

        GeoCircleButtonView closeBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_CLOSE, v -> closeAtlasTopicModal());
        closeBtn.setId(View.generateViewId());
        card.addView(closeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        for (int i = 0; i < 7; i++) {
            if (i > 0) atlasTopicPills[i].setNextFocusUpId(atlasTopicPills[i - 1].getId());
            else atlasTopicPills[i].setNextFocusUpId(atlasTopicPills[0].getId());

            if (i < 6) atlasTopicPills[i].setNextFocusDownId(atlasTopicPills[i + 1].getId());
            else atlasTopicPills[i].setNextFocusDownId(closeBtn.getId());
        }
        closeBtn.setNextFocusUpId(atlasTopicPills[6].getId());
        closeBtn.setNextFocusDownId(closeBtn.getId());

        overlay.addView(card, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return overlay;
    }

    private void openAtlasTopicModal() {
        soundEngine.playPop();
        gameLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        atlasTopicModalLayer.setVisibility(View.VISIBLE);
        atlasTopicModalLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        for (int i = 0; i < 7; i++) {
            styleAtlasTopicPill(atlasTopicPills[i], atlasCurrentTopic == i, false);
        }
        atlasTopicPills[atlasCurrentTopic].requestFocus();
    }

    private void closeAtlasTopicModal() {
        atlasTopicModalLayer.setVisibility(View.GONE);
        atlasTopicModalLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        gameLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (atlasTopicBtn != null) {
            atlasTopicBtn.requestFocus();
        }
    }

    private void styleAtlasTopicPill(TextView pill, boolean selected, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(12));
        if (focused) {
            gd.setColor(0xEE1E3A5F);
            gd.setStroke(dp(2), 0xFFFBBF24);
            pill.setTextColor(0xFFFDE047);
        } else if (selected) {
            gd.setColor(0xDD065F46);
            gd.setStroke(dp(1.5f), 0xFF34D399);
            pill.setTextColor(0xFFECFDF5);
        } else {
            gd.setColor(0x990F172A);
            gd.setStroke(dp(1), 0x4464748B);
            pill.setTextColor(0xFFCBD5E1);
        }
        pill.setBackground(gd);
    }

    // -------------------------------------------------------------------------
    // 2. COMPACT 55% CENTER GAME STAGE + RIGHT-FLANK LIQUID SCORE TUBE
    // -------------------------------------------------------------------------
    private FrameLayout buildGameLayer(Context context) {
        FrameLayout layer = new FrameLayout(context);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);

        LinearLayout mainStageRow = new LinearLayout(context);
        mainStageRow.setOrientation(HORIZONTAL);
        mainStageRow.setGravity(Gravity.CENTER);
        mainStageRow.setClipChildren(false);
        mainStageRow.setClipToPadding(false);

        centerStageCard = new LinearLayout(context);
        centerStageCard.setOrientation(VERTICAL);
        centerStageCard.setGravity(Gravity.CENTER_HORIZONTAL);
        centerStageCard.setClipChildren(false);
        centerStageCard.setClipToPadding(false);
        centerStageCard.setPadding(dp(24), dp(16), dp(24), dp(18));
        centerStageCard.setBackground(makeStageGlassCard());

        hudRow = new LinearLayout(context);
        hudRow.setOrientation(HORIZONTAL);
        hudRow.setGravity(Gravity.CENTER_VERTICAL);
        hudRow.setClipChildren(false);
        hudRow.setClipToPadding(false);
        hudRow.setPadding(0, 0, 0, dp(10));

        capitalDirToggleBtn = makePillButton("🏳️ ➔ 🏛️", v -> {
            capitalReverseMode = !capitalReverseMode;
            capitalDirToggleBtn.setText(capitalReverseMode ? "🏛️ ➔ 🏳️" : "🏳️ ➔ 🏛️");
            soundEngine.playPop();
            bindCurrentQuestion();
        });
        capitalDirToggleBtn.setId(View.generateViewId());
        hudRow.addView(capitalDirToggleBtn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        progressDotsView = new ProgressDotsView(context);
        hudRow.addView(progressDotsView, new LinearLayout.LayoutParams(0, dp(24), 1f));

        gameSoundBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_SOUND, v -> soundEngine.speak(currentSpokenPrompt));
        gameSoundBtn.setId(View.generateViewId());
        hudRow.addView(gameSoundBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        addSpacer(hudRow, 8, 1);

        gameCloseBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_CLOSE, v -> showHomeScreen());
        gameCloseBtn.setId(View.generateViewId());
        hudRow.addView(gameCloseBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        centerStageCard.addView(hudRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        stageMultipleChoice = buildMultipleChoiceSubStage(context);
        stageSpotTheLie = buildSpotTheLieSubStage(context);
        stageFlagMatcher = buildFlagMatcherSubStage(context);
        stageWordBuilder = buildWordBuilderSubStage(context);
        stageSnapMatch = buildSnapMatchSubStage(context);
        stageAtlas = buildAtlasSubStage(context);

        centerStageCard.addView(stageMultipleChoice, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        centerStageCard.addView(stageSpotTheLie, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        centerStageCard.addView(stageFlagMatcher, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        centerStageCard.addView(stageWordBuilder, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        centerStageCard.addView(stageSnapMatch, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        centerStageCard.addView(stageAtlas, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setClipChildren(false);
        bottomRow.setClipToPadding(false);
        bottomRow.setPadding(dp(6), dp(8), dp(6), 0);

        feedbackFactBanner = makeText(13, 0xFFFDE047, false);
        feedbackFactBanner.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        feedbackFactBanner.setSingleLine(false);
        feedbackFactBanner.setMaxLines(3);
        feedbackFactBanner.setEllipsize(TextUtils.TruncateAt.END);
        bottomRow.addView(feedbackFactBanner, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        nextQuestionBtn = makePillButton("Next ➔", v -> advanceToNextQuestionNow());
        nextQuestionBtn.setId(View.generateViewId());
        nextQuestionBtn.setVisibility(View.GONE);
        bottomRow.addView(nextQuestionBtn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        centerStageCard.addView(bottomRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mainStageRow.addView(centerStageCard, new LinearLayout.LayoutParams(dp(580), LayoutParams.WRAP_CONTENT));

        addSpacer(mainStageRow, 16, 1);

        scoreTubeView = new ScoreTubeView(context);
        mainStageRow.addView(scoreTubeView, new LinearLayout.LayoutParams(dp(54), dp(290)));

        layer.addView(mainStageRow, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return layer;
    }

    private LinearLayout buildMultipleChoiceSubStage(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        mcHeroFlag = new FlagCanvasView(context, false);
        box.addView(mcHeroFlag, new LinearLayout.LayoutParams(dp(196), dp(120)));

        mcHeroWonder = new WonderCanvasView(context);
        box.addView(mcHeroWonder, new LinearLayout.LayoutParams(dp(220), dp(112)));

        mcPromptTitle = makeText(22, 0xFFF8FAFC, true);
        mcPromptTitle.setGravity(Gravity.CENTER);
        mcPromptTitle.setPadding(0, dp(6), 0, dp(2));
        box.addView(mcPromptTitle);

        mcPromptSub = makeText(13, 0xFF93C5FD, false);
        mcPromptSub.setGravity(Gravity.CENTER);
        mcPromptSub.setPadding(dp(12), 0, dp(12), dp(10));
        box.addView(mcPromptSub);

        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            if (r == 0) row.setPadding(0, 0, 0, dp(8));

            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                TextView btn = makeText(14, 0xFFF8FAFC, true);
                btn.setId(View.generateViewId());
                btn.setGravity(Gravity.CENTER);
                btn.setSingleLine(false);
                btn.setMaxLines(2);
                btn.setEllipsize(TextUtils.TruncateAt.END);
                btn.setPadding(dp(12), dp(6), dp(12), dp(6));
                btn.setFocusable(true);
                btn.setFocusableInTouchMode(true);
                styleChoicePill(btn, FlagCanvasView.STAMP_NONE, false);
                btn.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!inputLocked) styleChoicePill(btn, FlagCanvasView.STAMP_NONE, hasFocus);
                });
                btn.setOnClickListener(v -> onMultipleChoiceSelected(idx));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(236), dp(48));
                if (c == 0) lp.setMarginEnd(dp(12));
                row.addView(btn, lp);
                mcChoiceBtns[idx] = btn;
            }
            box.addView(row, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        return box;
    }

    private LinearLayout buildSpotTheLieSubStage(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(6));

        spotLieHeroFlag = new FlagCanvasView(context, false);
        headerRow.addView(spotLieHeroFlag, new LinearLayout.LayoutParams(dp(54), dp(34)));

        addSpacer(headerRow, 10, 1);

        spotLieCountryTitle = makeText(20, 0xFFF8FAFC, true);
        headerRow.addView(spotLieCountryTitle);

        addSpacer(headerRow, 10, 1);

        TextView badge = makeText(11, 0xFFFDE047, true);
        badge.setText("🔎 SPOT THE LIE");
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(8));
        bgd.setColor(0x44D97706);
        bgd.setStroke(dp(1), 0xFFF59E0B);
        badge.setBackground(bgd);
        headerRow.addView(badge);

        box.addView(headerRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        spotLiePromptSub = makeText(12, 0xFF93C5FD, false);
        spotLiePromptSub.setText("Which statement is FALSE?");
        spotLiePromptSub.setPadding(0, 0, 0, dp(8));
        box.addView(spotLiePromptSub);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView btn = makeText(13, 0xFFF8FAFC, false);
            btn.setId(View.generateViewId());
            btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            btn.setSingleLine(false);
            btn.setMaxLines(2);
            btn.setEllipsize(TextUtils.TruncateAt.END);
            btn.setPadding(dp(14), dp(7), dp(14), dp(7));
            btn.setFocusable(true);
            btn.setFocusableInTouchMode(true);
            styleChoicePill(btn, FlagCanvasView.STAMP_NONE, false);
            btn.setOnFocusChangeListener((v, hasFocus) -> {
                if (!inputLocked) styleChoicePill(btn, FlagCanvasView.STAMP_NONE, hasFocus);
            });
            btn.setOnClickListener(v -> onSpotTheLieSelected(idx));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(520), LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            box.addView(btn, lp);
            spotLieBtns[i] = btn;
        }

        for (int i = 0; i < 4; i++) {
            if (i > 0) spotLieBtns[i].setNextFocusUpId(spotLieBtns[i - 1].getId());
            if (i < 3) spotLieBtns[i].setNextFocusDownId(spotLieBtns[i + 1].getId());
        }

        return box;
    }

    private LinearLayout buildFlagMatcherSubStage(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        fmCountryTitle = makeText(28, 0xFFF8FAFC, true);
        fmCountryTitle.setGravity(Gravity.CENTER);
        fmCountryTitle.setPadding(0, dp(8), 0, dp(18));
        box.addView(fmCountryTitle);

        LinearLayout flagsRow = new LinearLayout(context);
        flagsRow.setOrientation(HORIZONTAL);
        flagsRow.setGravity(Gravity.CENTER);
        flagsRow.setClipChildren(false);
        flagsRow.setClipToPadding(false);
        flagsRow.setPadding(dp(4), dp(6), dp(4), dp(10));

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            FlagCanvasView fc = new FlagCanvasView(context, true);
            fc.setId(View.generateViewId());
            fc.setOnClickListener(v -> onFlagCardSelected(idx));
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(122), dp(82));
            if (i < 3) flp.setMarginEnd(dp(10));
            flagsRow.addView(fc, flp);
            fmFlagCards[i] = fc;
        }
        box.addView(flagsRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return box;
    }

    private LinearLayout buildWordBuilderSubStage(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        wbHeroFlag = new FlagCanvasView(context, false);
        box.addView(wbHeroFlag, new LinearLayout.LayoutParams(dp(156), dp(96)));

        wbAttemptDots = makeText(12, 0xFF34D399, true);
        wbAttemptDots.setGravity(Gravity.CENTER);
        wbAttemptDots.setPadding(0, dp(4), 0, dp(8));
        box.addView(wbAttemptDots);

        LinearLayout slotsRow = new LinearLayout(context);
        slotsRow.setOrientation(HORIZONTAL);
        slotsRow.setGravity(Gravity.CENTER);
        slotsRow.setPadding(0, 0, 0, dp(14));

        for (int i = 0; i < 10; i++) {
            TextView sl = makeText(18, 0xFFF8FAFC, true);
            sl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(38), dp(42));
            slp.setMarginEnd(dp(5));
            slotsRow.addView(sl, slp);
            wbSlots[i] = sl;
        }
        box.addView(slotsRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout orbsRow = new LinearLayout(context);
        orbsRow.setOrientation(HORIZONTAL);
        orbsRow.setGravity(Gravity.CENTER_VERTICAL);
        orbsRow.setClipChildren(false);
        orbsRow.setClipToPadding(false);
        orbsRow.setPadding(dp(4), dp(4), dp(4), dp(4));

        for (int i = 0; i < 8; i++) {
            final int orbIdx = i;
            TextView lo = makeText(17, 0xFFF8FAFC, true);
            lo.setId(View.generateViewId());
            lo.setGravity(Gravity.CENTER);
            lo.setFocusable(true);
            lo.setFocusableInTouchMode(true);
            lo.setOnFocusChangeListener((v, hasFocus) -> styleLetterOrb(lo, wbOrbUsed[orbIdx], hasFocus));
            lo.setOnClickListener(v -> onWordBuilderOrbClicked(orbIdx));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(44), dp(44));
            olp.setMarginEnd(dp(8));
            orbsRow.addView(lo, olp);
            wbLetterOrbs[i] = lo;
        }

        wbUndoBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_BACKSPACE, v -> undoLastWordBuilderLetter());
        wbUndoBtn.setId(View.generateViewId());
        orbsRow.addView(wbUndoBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        box.addView(orbsRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return box;
    }

    private LinearLayout buildSnapMatchSubStage(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(dp(8), dp(4), dp(8), dp(4));

        LinearLayout leftCol = new LinearLayout(context);
        leftCol.setOrientation(VERTICAL);
        leftCol.setGravity(Gravity.CENTER);
        leftCol.setClipChildren(false);
        leftCol.setClipToPadding(false);

        for (int i = 0; i < 4; i++) {
            final int lIdx = i;
            TextView lb = makeText(14, 0xFFF8FAFC, true);
            lb.setId(View.generateViewId());
            lb.setGravity(Gravity.CENTER);
            lb.setSingleLine(true);
            lb.setPadding(dp(12), dp(6), dp(12), dp(6));
            lb.setFocusable(true);
            lb.setFocusableInTouchMode(true);
            lb.setOnFocusChangeListener((v, hasFocus) -> refreshSnapMatchStyles());
            lb.setOnClickListener(v -> onSnapLeftClicked(lIdx));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(170), dp(44));
            if (i < 3) llp.bottomMargin = dp(8);
            leftCol.addView(lb, llp);
            snapLeftBtns[i] = lb;
        }

        snapConnectorView = new SnapConnectorOverlayView(context);

        LinearLayout rightCol = new LinearLayout(context);
        rightCol.setOrientation(VERTICAL);
        rightCol.setGravity(Gravity.CENTER);
        rightCol.setClipChildren(false);
        rightCol.setClipToPadding(false);

        for (int i = 0; i < 4; i++) {
            final int rIdx = i;
            FlagCanvasView rf = new FlagCanvasView(context, true);
            rf.setId(View.generateViewId());
            rf.setOnClickListener(v -> onSnapRightClicked(rIdx));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(96), dp(44));
            if (i < 3) rlp.bottomMargin = dp(8);
            rightCol.addView(rf, rlp);
            snapRightFlags[i] = rf;
        }

        box.addView(leftCol, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        box.addView(snapConnectorView, new LinearLayout.LayoutParams(dp(96), dp(200)));
        box.addView(rightCol, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return box;
    }

    // World Atlas Deck Browser Sub-Stage
    private LinearLayout buildAtlasSubStage(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        // Topic Switcher Pill Button
        atlasTopicBtn = makePillButton("🏔️ Top 10 Mountains ▾", v -> openAtlasTopicModal());
        atlasTopicBtn.setId(View.generateViewId());
        box.addView(atlasTopicBtn);

        addSpacer(box, 1, 10);

        // Atlas Card Row with Left and Right Navigation Chevrons
        LinearLayout cardNavRow = new LinearLayout(context);
        cardNavRow.setOrientation(HORIZONTAL);
        cardNavRow.setGravity(Gravity.CENTER_VERTICAL);
        cardNavRow.setClipChildren(false);
        cardNavRow.setClipToPadding(false);

        atlasPrevBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_CHEVRON_LEFT, v -> navigateAtlasCard(-1));
        atlasPrevBtn.setId(View.generateViewId());
        cardNavRow.addView(atlasPrevBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        addSpacer(cardNavRow, 12, 1);

        // Main Record Postcard
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(12), dp(18), dp(12));
        card.setBackground(makeStageGlassCard());

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        atlasCardRank = makeText(14, 0xFFFDE047, true);
        atlasCardRank.setText("#1");
        headerRow.addView(atlasCardRank);

        addSpacer(headerRow, 10, 1);

        atlasCardFlag = new FlagCanvasView(context, false);
        headerRow.addView(atlasCardFlag, new LinearLayout.LayoutParams(dp(54), dp(34)));

        addSpacer(headerRow, 12, 1);

        atlasCardTitle = makeText(20, 0xFFF8FAFC, true);
        atlasCardTitle.setSingleLine(true);
        headerRow.addView(atlasCardTitle);

        card.addView(headerRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        atlasCardMetric = makeText(16, 0xFF34D399, true);
        atlasCardMetric.setPadding(0, dp(4), 0, dp(2));
        card.addView(atlasCardMetric);

        atlasCardSub = makeText(12, 0xFF93C5FD, false);
        card.addView(atlasCardSub);

        atlasCardDetail = makeText(12, 0xFFCBD5E1, false);
        atlasCardDetail.setGravity(Gravity.CENTER);
        atlasCardDetail.setPadding(dp(8), dp(6), dp(8), 0);
        card.addView(atlasCardDetail);

        cardNavRow.addView(card, new LinearLayout.LayoutParams(dp(380), dp(146)));

        addSpacer(cardNavRow, 12, 1);

        atlasNextBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_CHEVRON_RIGHT, v -> navigateAtlasCard(1));
        atlasNextBtn.setId(View.generateViewId());
        cardNavRow.addView(atlasNextBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        box.addView(cardNavRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        atlasTopicBtn.setNextFocusUpId(atlasTopicBtn.getId());
        atlasTopicBtn.setNextFocusDownId(atlasNextBtn.getId());

        atlasPrevBtn.setNextFocusLeftId(atlasPrevBtn.getId());
        atlasPrevBtn.setNextFocusRightId(atlasNextBtn.getId());
        atlasPrevBtn.setNextFocusUpId(atlasTopicBtn.getId());
        atlasPrevBtn.setNextFocusDownId(atlasPrevBtn.getId());

        atlasNextBtn.setNextFocusLeftId(atlasPrevBtn.getId());
        atlasNextBtn.setNextFocusRightId(atlasNextBtn.getId());
        atlasNextBtn.setNextFocusUpId(atlasTopicBtn.getId());
        atlasNextBtn.setNextFocusDownId(atlasNextBtn.getId());

        return box;
    }

    private void navigateAtlasCard(int delta) {
        GeoData.AtlasEntry[] deck = GeoData.ATLAS_DECKS[atlasCurrentTopic];
        atlasCurrentCardIdx = (atlasCurrentCardIdx + delta + deck.length) % deck.length;
        soundEngine.playPop();
        refreshAtlasCard();
    }

    private void refreshAtlasCard() {
        atlasTopicBtn.setText(GeoData.ATLAS_TOPIC_TITLES[atlasCurrentTopic] + " ▾");
        GeoData.AtlasEntry[] deck = GeoData.ATLAS_DECKS[atlasCurrentTopic];
        GeoData.AtlasEntry entry = deck[atlasCurrentCardIdx % deck.length];

        atlasCardRank.setText("#" + entry.rank);
        atlasCardTitle.setText(entry.title);
        atlasCardMetric.setText(entry.metric);
        atlasCardSub.setText(entry.subtitle);
        atlasCardDetail.setText(entry.detailFact);

        GeoData.Country c = GeoData.findByName(entry.countryName);
        if (c != null) {
            atlasCardFlag.setVisibility(View.VISIBLE);
            atlasCardFlag.bind(c);
        } else {
            atlasCardFlag.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // 3. END-OF-ROUND CELEBRATION POPUP
    // -------------------------------------------------------------------------
    private FrameLayout buildResultLayer(Context context) {
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x88040814);
        overlay.setClickable(true);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setPadding(dp(32), dp(26), dp(32), dp(24));
        card.setBackground(makeStageGlassCard());

        resStarsText = makeText(30, 0xFFFBBF24, true);
        resStarsText.setGravity(Gravity.CENTER);
        card.addView(resStarsText);

        resNewBestBadge = makeText(13, 0xFF34D399, true);
        resNewBestBadge.setGravity(Gravity.CENTER);
        resNewBestBadge.setPadding(0, dp(4), 0, dp(6));
        card.addView(resNewBestBadge);

        resScoreText = makeText(34, 0xFFF8FAFC, true);
        resScoreText.setGravity(Gravity.CENTER);
        card.addView(resScoreText);

        resBestSubText = makeText(13, 0xFF94A3B8, false);
        resBestSubText.setGravity(Gravity.CENTER);
        resBestSubText.setPadding(0, dp(4), 0, dp(18));
        card.addView(resBestSubText);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);

        resReplayBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_REFRESH, v -> {
            soundEngine.playPop();
            startMode(activeMode);
        });
        resReplayBtn.setId(View.generateViewId());

        resCloseBtn = new GeoCircleButtonView(context, GeoCircleButtonView.TYPE_CLOSE, v -> {
            soundEngine.playPop();
            showHomeScreen();
        });
        resCloseBtn.setId(View.generateViewId());

        resReplayBtn.setNextFocusLeftId(resReplayBtn.getId());
        resReplayBtn.setNextFocusRightId(resCloseBtn.getId());
        resCloseBtn.setNextFocusLeftId(resReplayBtn.getId());
        resCloseBtn.setNextFocusRightId(resCloseBtn.getId());

        actions.addView(resReplayBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        addSpacer(actions, 22, 1);
        actions.addView(resCloseBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        card.addView(actions);

        overlay.addView(card, new LayoutParams(dp(380), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return overlay;
    }

    // -------------------------------------------------------------------------
    // 4. GAMEPLAY ENGINE & ROUND FLOW
    // -------------------------------------------------------------------------
    private void showHomeScreen() {
        handler.removeCallbacksAndMessages(null);
        uiState = STATE_HOME;
        inputLocked = false;
        pendingAdvance = false;
        if (nextQuestionBtn != null) nextQuestionBtn.setVisibility(View.GONE);

        homeLayer.setVisibility(View.VISIBLE);
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        gameLayer.setVisibility(View.GONE);
        gameLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        resultLayer.setVisibility(View.GONE);
        resultLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (regionModalLayer != null) regionModalLayer.setVisibility(View.GONE);
        if (atlasTopicModalLayer != null) atlasTopicModalLayer.setVisibility(View.GONE);

        refreshHomeStats();
        if (lastHomeFocus != null) {
            lastHomeFocus.requestFocus();
        } else if (modeOrbs[0] != null) {
            modeOrbs[0].requestFocus();
        }
    }

    private void refreshHomeStats() {
        Context ctx = getContext();
        for (int i = 0; i < 2; i++) {
            if (tierButtons[i] != null) {
                styleTierPill(tierButtons[i], (i + 1) == skillTier, tierButtons[i].isFocused());
            }
        }
        for (int m = 0; m < 8; m++) {
            if (modeOrbs[m] != null) {
                int stars = GeoStore.getStarsForMode(ctx, m, skillTier);
                int best = GeoStore.getBestScore(ctx, m, skillTier);
                modeOrbs[m].updateProgress(stars, best);
            }
        }
    }

    private void startMode(int mode) {
        handler.removeCallbacksAndMessages(null);
        this.activeMode = mode;
        this.questionIndex = 0;
        this.currentScore = 0;
        this.currentStreak = 0;
        this.inputLocked = false;
        this.pendingAdvance = false;
        if (nextQuestionBtn != null) nextQuestionBtn.setVisibility(View.GONE);
        if (regionModalLayer != null) regionModalLayer.setVisibility(View.GONE);
        if (atlasTopicModalLayer != null) atlasTopicModalLayer.setVisibility(View.GONE);

        homeLayer.setVisibility(View.INVISIBLE);
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        resultLayer.setVisibility(View.GONE);
        resultLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        gameLayer.setVisibility(View.VISIBLE);
        gameLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        if (mode == GeoStore.MODE_ATLAS) {
            uiState = STATE_ATLAS;
            hudRow.setVisibility(View.GONE);
            scoreTubeView.setVisibility(View.GONE);
            stageMultipleChoice.setVisibility(View.GONE);
            stageSpotTheLie.setVisibility(View.GONE);
            stageFlagMatcher.setVisibility(View.GONE);
            stageWordBuilder.setVisibility(View.GONE);
            stageSnapMatch.setVisibility(View.GONE);
            stageAtlas.setVisibility(View.VISIBLE);
            feedbackFactBanner.setText("Press ‹ or › on remote to flip cards • Click topic to select a deck");
            atlasCurrentCardIdx = 0;
            refreshAtlasCard();
            atlasTopicBtn.requestFocus();
            return;
        }

        uiState = STATE_PLAYING;
        hudRow.setVisibility(View.VISIBLE);
        scoreTubeView.setVisibility(View.VISIBLE);
        stageAtlas.setVisibility(View.GONE);

        Random rng = (mode == GeoStore.MODE_DAILY)
                ? new Random(LocalDate.now().toEpochDay() * 31L + skillTier)
                : new Random();

        roundCountries.clear();
        roundWonders.clear();

        if (mode == GeoStore.MODE_WONDERS) {
            roundWonders.addAll(GeoData.pickWonderSet(regionMask, 10, rng));
            for (GeoData.Wonder w : roundWonders) {
                roundCountries.add(GeoData.findByName(w.countryName));
            }
            maxRoundScore = 100;
            progressDotsView.reset(10);
        } else if (mode == GeoStore.MODE_SNAP_MATCH) {
            roundCountries.addAll(GeoData.pickQuestionSet(skillTier, regionMask, 12, rng));
            maxRoundScore = 120;
            snapBoardIndex = 0;
            progressDotsView.reset(12);
        } else {
            roundCountries.addAll(GeoData.pickQuestionSet(skillTier, regionMask, 10, rng));
            maxRoundScore = (mode == GeoStore.MODE_WORD_BUILDER) ? 200 : 100;
            progressDotsView.reset(10);
        }

        int savedBest = GeoStore.getBestScore(getContext(), mode, skillTier);
        scoreTubeView.configure(maxRoundScore, savedBest, 0);

        capitalDirToggleBtn.setVisibility(mode == GeoStore.MODE_CAPITALS ? View.VISIBLE : View.GONE);
        feedbackFactBanner.setText("");

        if (mode == GeoStore.MODE_SNAP_MATCH) {
            setupSnapMatchBoard();
        } else {
            bindCurrentQuestion();
        }
    }

    private void bindCurrentQuestion() {
        inputLocked = false;
        pendingAdvance = false;
        handler.removeCallbacks(advanceRunnable);
        if (nextQuestionBtn != null) nextQuestionBtn.setVisibility(View.GONE);
        feedbackFactBanner.setText("");
        progressDotsView.setCurrentQuestion(questionIndex, currentStreak);

        int effectiveMode = activeMode;
        if (activeMode == GeoStore.MODE_DAILY) {
            int[] cycle = {
                GeoStore.MODE_FLAG_FINDER,
                GeoStore.MODE_FLAG_MATCHER,
                GeoStore.MODE_CAPITALS,
                GeoStore.MODE_WONDERS,
                GeoStore.MODE_ODD_ONE_OUT,
                GeoStore.MODE_WORD_BUILDER
            };
            effectiveMode = cycle[questionIndex % cycle.length];
        }

        stageMultipleChoice.setVisibility(View.GONE);
        stageSpotTheLie.setVisibility(View.GONE);
        stageFlagMatcher.setVisibility(View.GONE);
        stageWordBuilder.setVisibility(View.GONE);
        stageSnapMatch.setVisibility(View.GONE);
        stageAtlas.setVisibility(View.GONE);

        Random rng = new Random(questionIndex * 97L + System.nanoTime());
        List<GeoData.Country> pool = GeoData.getPoolForTierAndRegions(skillTier, regionMask);

        if (effectiveMode == GeoStore.MODE_FLAG_MATCHER) {
            // Mode 1: Country Name -> 4 Flag Cards
            stageFlagMatcher.setVisibility(View.VISIBLE);
            GeoData.Country target = roundCountries.get(questionIndex % roundCountries.size());
            fmCountryTitle.setText(target.name);
            currentSpokenPrompt = "Find the flag of " + target.name;

            List<GeoData.Country> choices = GeoData.buildSmartDistractors(target, pool, skillTier, 4, rng);
            correctChoiceIdx = choices.indexOf(target);
            for (int i = 0; i < 4; i++) {
                fmFlagCards[i].bind(choices.get(i));
            }
            fmFlagCards[0].requestFocus();
            if (skillTier == 1) soundEngine.speak(currentSpokenPrompt);

        } else if (effectiveMode == GeoStore.MODE_WORD_BUILDER) {
            // Mode 2: Word Builder (Spell It)
            stageWordBuilder.setVisibility(View.VISIBLE);
            GeoData.Country target = roundCountries.get(questionIndex % roundCountries.size());
            setupWordBuilderQuestion(target, rng);

        } else if (effectiveMode == GeoStore.MODE_ODD_ONE_OUT) {
            // Mode 6: Spot the Lie (Dedicated Vertical 4-Card Stage)
            stageSpotTheLie.setVisibility(View.VISIBLE);
            currentOddQuestion = GeoData.generateOddQuestion(pool, questionIndex, false, false, rng);
            correctChoiceIdx = currentOddQuestion.wrongIndex;

            if (currentOddQuestion.heroCountry != null) {
                spotLieHeroFlag.setVisibility(View.VISIBLE);
                spotLieHeroFlag.bind(currentOddQuestion.heroCountry);
            } else {
                spotLieHeroFlag.setVisibility(View.GONE);
            }

            spotLieCountryTitle.setText(currentOddQuestion.title);
            spotLiePromptSub.setText(currentOddQuestion.subtitle);
            currentSpokenPrompt = currentOddQuestion.title + ". " + currentOddQuestion.subtitle;

            for (int i = 0; i < 4; i++) {
                spotLieBtns[i].setText(currentOddQuestion.options[i]);
                styleChoicePill(spotLieBtns[i], FlagCanvasView.STAMP_NONE, false);
            }
            spotLieBtns[0].requestFocus();
            styleChoicePill(spotLieBtns[0], FlagCanvasView.STAMP_NONE, true);
            if (skillTier == 1) soundEngine.speak(currentSpokenPrompt);

        } else {
            // Multiple Choice (Flag->Name, Capitals, Wonders)
            stageMultipleChoice.setVisibility(View.VISIBLE);

            if (effectiveMode == GeoStore.MODE_WONDERS) {
                GeoData.Wonder w = roundWonders.isEmpty()
                        ? GeoData.WONDERS[questionIndex % GeoData.WONDERS.length]
                        : roundWonders.get(questionIndex % roundWonders.size());
                GeoData.Country target = GeoData.findByName(w.countryName);

                mcHeroFlag.setVisibility(View.GONE);
                mcHeroWonder.setVisibility(View.VISIBLE);
                mcHeroWonder.setArtType(w.artType);
                mcPromptTitle.setText(w.name);
                mcPromptSub.setText(w.clue);
                currentSpokenPrompt = w.name + ". " + w.clue;

                List<GeoData.Country> choices = GeoData.buildSmartDistractors(target, pool, skillTier, 4, rng);
                correctChoiceIdx = choices.indexOf(target);
                for (int i = 0; i < 4; i++) {
                    setMultipleChoiceOption(i, choices.get(i).name);
                }
            } else if (effectiveMode == GeoStore.MODE_CAPITALS) {
                GeoData.Country target = roundCountries.get(questionIndex % roundCountries.size());
                mcHeroWonder.setVisibility(View.GONE);
                mcHeroFlag.setVisibility(View.VISIBLE);
                mcHeroFlag.bind(target);

                List<GeoData.Country> choices = GeoData.buildSmartDistractors(target, pool, skillTier, 4, rng);
                correctChoiceIdx = choices.indexOf(target);

                if (!capitalReverseMode) {
                    mcPromptTitle.setText(target.name + "  ➔  🏛️ ?");
                    mcPromptSub.setText(GeoData.CONTINENT_NAMES[target.continent]);
                    currentSpokenPrompt = "What is the capital of " + target.name + "?";
                    for (int i = 0; i < 4; i++) {
                        setMultipleChoiceOption(i, choices.get(i).capital);
                    }
                } else {
                    mcHeroFlag.setVisibility(View.GONE);
                    mcPromptTitle.setText("🏛️ " + target.capital + "  ➔  🏳️ ?");
                    mcPromptSub.setText(GeoData.CONTINENT_NAMES[target.continent]);
                    currentSpokenPrompt = target.capital + " is the capital of which country?";
                    for (int i = 0; i < 4; i++) {
                        setMultipleChoiceOption(i, choices.get(i).name);
                    }
                }
            } else {
                // Mode 0: Flag -> Country Name
                GeoData.Country target = roundCountries.get(questionIndex % roundCountries.size());
                mcHeroWonder.setVisibility(View.GONE);
                mcHeroFlag.setVisibility(View.VISIBLE);
                mcHeroFlag.bind(target);
                mcPromptTitle.setText(GeoData.CONTINENT_NAMES[target.continent]);
                mcPromptSub.setVisibility(View.GONE);
                currentSpokenPrompt = "Which country's flag is this?";

                List<GeoData.Country> choices = GeoData.buildSmartDistractors(target, pool, skillTier, 4, rng);
                correctChoiceIdx = choices.indexOf(target);
                for (int i = 0; i < 4; i++) {
                    setMultipleChoiceOption(i, choices.get(i).name);
                }
            }
            if (effectiveMode == GeoStore.MODE_CAPITALS || effectiveMode == GeoStore.MODE_WONDERS) {
                mcPromptSub.setVisibility(View.VISIBLE);
            }
            mcChoiceBtns[0].requestFocus();
            styleChoicePill(mcChoiceBtns[0], FlagCanvasView.STAMP_NONE, true);
            if (skillTier == 1) soundEngine.speak(currentSpokenPrompt);
        }
    }

    private void setMultipleChoiceOption(int idx, String text) {
        mcChoiceBtns[idx].setText(text);
        if (text.length() > 18) {
            mcChoiceBtns[idx].setTextSize(12.5f);
        } else if (text.length() > 13) {
            mcChoiceBtns[idx].setTextSize(13.5f);
        } else {
            mcChoiceBtns[idx].setTextSize(14.5f);
        }
        styleChoicePill(mcChoiceBtns[idx], FlagCanvasView.STAMP_NONE, false);
    }

    private void onMultipleChoiceSelected(int idx) {
        if (inputLocked) return;
        inputLocked = true;

        boolean correct = (idx == correctChoiceIdx);

        GeoData.Country target = roundCountries.isEmpty()
                ? GeoData.COUNTRIES[0]
                : roundCountries.get(questionIndex % roundCountries.size());

        if (correct) {
            currentStreak++;
            currentScore += 10;
            styleChoicePill(mcChoiceBtns[idx], FlagCanvasView.STAMP_CORRECT, true);
            mcChoiceBtns[idx].setText("✓  " + mcChoiceBtns[idx].getText());
            scoreTubeView.setScoreAnimated(currentScore);
            progressDotsView.markResult(questionIndex, true, currentStreak);
            soundEngine.playCorrect(currentStreak);
            feedbackFactBanner.setText("✨ " + target.funFact);
        } else {
            currentStreak = 0;
            styleChoicePill(mcChoiceBtns[idx], FlagCanvasView.STAMP_WRONG, true);
            mcChoiceBtns[idx].setText("✕  " + mcChoiceBtns[idx].getText());
            styleChoicePill(mcChoiceBtns[correctChoiceIdx], FlagCanvasView.STAMP_CORRECT, false);
            mcChoiceBtns[correctChoiceIdx].setText("✓  " + mcChoiceBtns[correctChoiceIdx].getText());
            progressDotsView.markResult(questionIndex, false, 0);
            soundEngine.playWrong();
            feedbackFactBanner.setText("💡 Correct: " + mcChoiceBtns[correctChoiceIdx].getText());
        }

        scheduleQuestionAdvance();
    }

    private void onSpotTheLieSelected(int idx) {
        if (inputLocked) return;
        inputLocked = true;

        boolean correct = (idx == correctChoiceIdx);
        if (correct) {
            currentStreak++;
            currentScore += 10;
            styleChoicePill(spotLieBtns[idx], FlagCanvasView.STAMP_CORRECT, true);
            spotLieBtns[idx].setText("✓  " + spotLieBtns[idx].getText());
            scoreTubeView.setScoreAnimated(currentScore);
            progressDotsView.markResult(questionIndex, true, currentStreak);
            soundEngine.playCorrect(currentStreak);
            feedbackFactBanner.setText("✨ Correct! " + currentOddQuestion.explanation);
        } else {
            currentStreak = 0;
            styleChoicePill(spotLieBtns[idx], FlagCanvasView.STAMP_WRONG, true);
            spotLieBtns[idx].setText("✕  " + spotLieBtns[idx].getText());
            styleChoicePill(spotLieBtns[correctChoiceIdx], FlagCanvasView.STAMP_CORRECT, false);
            spotLieBtns[correctChoiceIdx].setText("✓  " + spotLieBtns[correctChoiceIdx].getText());
            progressDotsView.markResult(questionIndex, false, 0);
            soundEngine.playWrong();
            feedbackFactBanner.setText("💡 The Lie: " + currentOddQuestion.explanation);
        }

        scheduleQuestionAdvance();
    }

    private void onFlagCardSelected(int idx) {
        if (inputLocked) return;
        inputLocked = true;

        boolean correct = (idx == correctChoiceIdx);
        GeoData.Country target = roundCountries.get(questionIndex % roundCountries.size());

        if (correct) {
            currentStreak++;
            currentScore += 10;
            fmFlagCards[idx].setStampState(FlagCanvasView.STAMP_CORRECT);
            scoreTubeView.setScoreAnimated(currentScore);
            progressDotsView.markResult(questionIndex, true, currentStreak);
            soundEngine.playCorrect(currentStreak);
            feedbackFactBanner.setText("✨ " + target.funFact);
        } else {
            currentStreak = 0;
            fmFlagCards[idx].setStampState(FlagCanvasView.STAMP_WRONG);
            fmFlagCards[correctChoiceIdx].setStampState(FlagCanvasView.STAMP_CORRECT);
            progressDotsView.markResult(questionIndex, false, 0);
            soundEngine.playWrong();
            feedbackFactBanner.setText("💡 Look at the green ✓ flag for " + target.name + "!");
        }

        scheduleQuestionAdvance();
    }

    private void scheduleQuestionAdvance() {
        pendingAdvance = true;
        if (nextQuestionBtn != null) {
            nextQuestionBtn.setVisibility(View.VISIBLE);
            nextQuestionBtn.requestFocus();
        }
        handler.removeCallbacks(advanceRunnable);
        handler.postDelayed(advanceRunnable, 4500);
    }

    private void advanceToNextQuestionNow() {
        if (!pendingAdvance && uiState != STATE_PLAYING) return;
        pendingAdvance = false;
        handler.removeCallbacks(advanceRunnable);
        if (nextQuestionBtn != null) {
            nextQuestionBtn.setVisibility(View.GONE);
        }
        advanceToNextQuestionOrEnd();
    }

    // -------------------------------------------------------------------------
    // WORD BUILDER (SPELL IT) LOGIC + REMOTE AUTO-ADVANCE FOCUS
    // -------------------------------------------------------------------------
    private void setupWordBuilderQuestion(GeoData.Country target, Random rng) {
        wbHeroFlag.bind(target);
        wbTargetWord = target.name.replaceAll("[^A-Z]", "");
        int len = wbTargetWord.length();
        wbAttemptsLeft = 3;
        updateWordBuilderAttemptDots();
        currentSpokenPrompt = "Spell the country for this flag";

        for (int i = 0; i < 12; i++) {
            wbFixedMask[i] = false;
            wbUserChars[i] = 0;
            wbSourceOrbForSlot[i] = -1;
        }

        int blanksCount = len;
        if (len > 5) {
            for (int i = 0; i < len; i++) {
                if (i == 0 || (i % 2 == 0 && blanksCount > 5)) {
                    wbFixedMask[i] = true;
                    wbUserChars[i] = wbTargetWord.charAt(i);
                    blanksCount--;
                }
            }
        }

        List<Character> bag = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            if (!wbFixedMask[i]) bag.add(wbTargetWord.charAt(i));
        }
        String alphabet = "AEIOURSTLNCDPMBGHK";
        wbOrbCount = Math.min(8, Math.max(6, bag.size() + 2));
        while (bag.size() < wbOrbCount) {
            bag.add(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        Collections.shuffle(bag, rng);

        for (int i = 0; i < 8; i++) {
            if (i < wbOrbCount) {
                wbOrbChars[i] = bag.get(i);
                wbOrbUsed[i] = false;
                wbLetterOrbs[i].setVisibility(View.VISIBLE);
                wbLetterOrbs[i].setText(String.valueOf(wbOrbChars[i]));
                wbLetterOrbs[i].setFocusable(true);
                wbLetterOrbs[i].setFocusableInTouchMode(true);
                styleLetterOrb(wbLetterOrbs[i], false, false);
            } else {
                wbLetterOrbs[i].setVisibility(View.GONE);
                wbLetterOrbs[i].setFocusable(false);
            }
        }

        refreshWordBuilderSlots(0);
        wbLetterOrbs[0].requestFocus();
    }

    private void updateWordBuilderAttemptDots() {
        String dots = (wbAttemptsLeft >= 1 ? "● " : "○ ")
                + (wbAttemptsLeft >= 2 ? "● " : "○ ")
                + (wbAttemptsLeft >= 3 ? "●" : "○");
        int pts = wbAttemptsLeft == 3 ? 20 : (wbAttemptsLeft == 2 ? 10 : 0);
        wbAttemptDots.setText(dots + "   +" + pts + " pts");
    }

    private void refreshWordBuilderSlots(int highlightState) {
        int len = wbTargetWord.length();
        for (int i = 0; i < 10; i++) {
            if (i < len) {
                wbSlots[i].setVisibility(View.VISIBLE);
                char ch = wbUserChars[i];
                wbSlots[i].setText(ch != 0 ? String.valueOf(ch) : "_");
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(dp(8));
                if (highlightState == 1) {
                    gd.setColor(0xCC059669);
                    gd.setStroke(dp(2), 0xFF34D399);
                } else if (highlightState == -1) {
                    gd.setColor(0xCCDC2626);
                    gd.setStroke(dp(2), 0xFFF87171);
                } else if (highlightState == 2) {
                    gd.setColor(0xCC0284C7);
                    gd.setStroke(dp(2), 0xFFFDE047);
                } else if (wbFixedMask[i]) {
                    gd.setColor(0x661E293B);
                    gd.setStroke(dp(1), 0x5594A3B8);
                } else {
                    gd.setColor(ch != 0 ? 0xAA1E3A5F : 0x550F172A);
                    gd.setStroke(dp(1), ch != 0 ? 0xFFFBBF24 : 0x6694A3B8);
                }
                wbSlots[i].setBackground(gd);
            } else {
                wbSlots[i].setVisibility(View.GONE);
            }
        }
    }

    private void onWordBuilderOrbClicked(int orbIdx) {
        if (inputLocked || wbOrbUsed[orbIdx]) return;
        int len = wbTargetWord.length();
        int nextSlot = -1;
        for (int i = 0; i < len; i++) {
            if (!wbFixedMask[i] && wbUserChars[i] == 0) {
                nextSlot = i;
                break;
            }
        }
        if (nextSlot < 0) return;

        soundEngine.playPop();
        wbOrbUsed[orbIdx] = true;
        wbUserChars[nextSlot] = wbOrbChars[orbIdx];
        wbSourceOrbForSlot[nextSlot] = orbIdx;
        wbLetterOrbs[orbIdx].setFocusable(false); // Unfocusable so D-pad left/right skips it!
        styleLetterOrb(wbLetterOrbs[orbIdx], true, false);
        refreshWordBuilderSlots(0);

        // Auto-advance focus to the nearest remaining unused letter orb!
        boolean found = false;
        for (int i = orbIdx + 1; i < wbOrbCount; i++) {
            if (!wbOrbUsed[i]) {
                wbLetterOrbs[i].requestFocus();
                found = true;
                break;
            }
        }
        if (!found) {
            for (int i = orbIdx - 1; i >= 0; i--) {
                if (!wbOrbUsed[i]) {
                    wbLetterOrbs[i].requestFocus();
                    found = true;
                    break;
                }
            }
        }
        if (!found && wbUndoBtn != null) {
            wbUndoBtn.requestFocus();
        }

        // Check if all slots filled -> Auto-Evaluate!
        boolean allFilled = true;
        for (int i = 0; i < len; i++) {
            if (wbUserChars[i] == 0) {
                allFilled = false;
                break;
            }
        }
        if (allFilled) {
            evaluateWordBuilderAttempt();
        }
    }

    private boolean undoLastWordBuilderLetter() {
        int len = wbTargetWord.length();
        for (int i = len - 1; i >= 0; i--) {
            if (!wbFixedMask[i] && wbUserChars[i] != 0) {
                int orbIdx = wbSourceOrbForSlot[i];
                wbUserChars[i] = 0;
                wbSourceOrbForSlot[i] = -1;
                if (orbIdx >= 0 && orbIdx < 8) {
                    wbOrbUsed[orbIdx] = false;
                    wbLetterOrbs[orbIdx].setFocusable(true);
                    wbLetterOrbs[orbIdx].setFocusableInTouchMode(true);
                    styleLetterOrb(wbLetterOrbs[orbIdx], false, false);
                    wbLetterOrbs[orbIdx].requestFocus();
                }
                soundEngine.playPop();
                refreshWordBuilderSlots(0);
                return true;
            }
        }
        return false;
    }

    private void evaluateWordBuilderAttempt() {
        int len = wbTargetWord.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(wbUserChars[i]);
        boolean match = sb.toString().equalsIgnoreCase(wbTargetWord);

        if (match) {
            inputLocked = true;
            int pts = (wbAttemptsLeft == 3) ? 20 : 10;
            currentStreak++;
            currentScore += pts;
            refreshWordBuilderSlots(1);
            scoreTubeView.setScoreAnimated(currentScore);
            progressDotsView.markResult(questionIndex, true, currentStreak);
            soundEngine.playCorrect(currentStreak);
            soundEngine.speak(wbTargetWord);
            feedbackFactBanner.setText("✓  " + wbTargetWord + "  (+" + pts + " pts!)");
            scheduleQuestionAdvance();
        } else {
            wbAttemptsLeft--;
            updateWordBuilderAttemptDots();
            soundEngine.playWrong();
            refreshWordBuilderSlots(-1);

            if (wbAttemptsLeft >= 2) {
                inputLocked = true;
                feedbackFactBanner.setText("Try again for +10 pts!");
                handler.postDelayed(() -> {
                    for (int i = 0; i < len; i++) {
                        if (!wbFixedMask[i]) {
                            wbUserChars[i] = 0;
                            wbSourceOrbForSlot[i] = -1;
                        }
                    }
                    for (int i = 0; i < wbOrbCount; i++) {
                        wbOrbUsed[i] = false;
                        wbLetterOrbs[i].setFocusable(true);
                        wbLetterOrbs[i].setFocusableInTouchMode(true);
                        styleLetterOrb(wbLetterOrbs[i], false, wbLetterOrbs[i].isFocused());
                    }
                    refreshWordBuilderSlots(0);
                    wbLetterOrbs[0].requestFocus();
                    inputLocked = false;
                }, 650);
            } else {
                inputLocked = true;
                currentStreak = 0;
                progressDotsView.markResult(questionIndex, false, 0);
                for (int i = 0; i < len; i++) {
                    wbUserChars[i] = wbTargetWord.charAt(i);
                }
                refreshWordBuilderSlots(2);
                soundEngine.speak(wbTargetWord);
                feedbackFactBanner.setText("📖 Learn the spelling:  " + wbTargetWord);
                scheduleQuestionAdvance();
            }
        }
    }

    // -------------------------------------------------------------------------
    // SNAP MATCH LOGIC (Left-to-Right Matching)
    // -------------------------------------------------------------------------
    private void setupSnapMatchBoard() {
        inputLocked = false;
        snapSelectedLeft = -1;
        snapMatchesOnBoard = 0;
        snapConnectorView.reset();

        stageMultipleChoice.setVisibility(View.GONE);
        stageSpotTheLie.setVisibility(View.GONE);
        stageFlagMatcher.setVisibility(View.GONE);
        stageWordBuilder.setVisibility(View.GONE);
        stageSnapMatch.setVisibility(View.VISIBLE);

        snapLeftList.clear();
        snapRightList.clear();
        int base = (snapBoardIndex * 4) % Math.max(4, roundCountries.size());
        for (int i = 0; i < 4; i++) {
            GeoData.Country c = roundCountries.get((base + i) % roundCountries.size());
            snapLeftList.add(c);
            snapRightList.add(c);
            snapLeftMatched[i] = false;
            snapRightMatched[i] = false;
            snapLeftHadMiss[i] = false;
        }
        Collections.shuffle(snapRightList, new Random(System.nanoTime()));

        for (int i = 0; i < 4; i++) {
            snapLeftBtns[i].setText(snapLeftList.get(i).name);
            snapRightFlags[i].bind(snapRightList.get(i));
        }
        refreshSnapMatchStyles();
        feedbackFactBanner.setText("Board " + (snapBoardIndex + 1) + " / 3  ·  Select Country on Left ➔ Match Flag on Right");
        snapLeftBtns[0].requestFocus();
    }

    private void onSnapLeftClicked(int leftIdx) {
        if (inputLocked || snapLeftMatched[leftIdx]) return;
        snapSelectedLeft = leftIdx;
        soundEngine.playPop();
        refreshSnapMatchStyles();

        for (int r = 0; r < 4; r++) {
            if (!snapRightMatched[r]) {
                snapRightFlags[r].requestFocus();
                break;
            }
        }
    }

    private void onSnapRightClicked(int rightIdx) {
        if (inputLocked || snapRightMatched[rightIdx]) return;
        if (snapSelectedLeft < 0) {
            snapLeftBtns[0].requestFocus();
            return;
        }

        int leftIdx = snapSelectedLeft;
        GeoData.Country leftC = snapLeftList.get(leftIdx);
        GeoData.Country rightC = snapRightList.get(rightIdx);

        if (leftC.code.equals(rightC.code)) {
            snapLeftMatched[leftIdx] = true;
            snapRightMatched[rightIdx] = true;
            snapSelectedLeft = -1;
            snapMatchesOnBoard++;

            int pts = snapLeftHadMiss[leftIdx] ? 5 : 10;
            currentStreak++;
            currentScore += pts;
            int dotIdx = snapBoardIndex * 4 + (snapMatchesOnBoard - 1);
            progressDotsView.markResult(dotIdx, true, currentStreak);
            scoreTubeView.setScoreAnimated(currentScore);
            snapConnectorView.setMatch(leftIdx, rightIdx);
            snapRightFlags[rightIdx].setStampState(FlagCanvasView.STAMP_CORRECT);
            soundEngine.playCorrect(currentStreak);
            feedbackFactBanner.setText("✓  " + leftC.name + " — " + leftC.funFact);
            refreshSnapMatchStyles();

            if (snapMatchesOnBoard >= 4) {
                inputLocked = true;
                handler.postDelayed(() -> {
                    snapBoardIndex++;
                    if (snapBoardIndex < 3) {
                        setupSnapMatchBoard();
                    } else {
                        showEndOfRoundModal();
                    }
                }, 1100);
            } else {
                for (int l = 0; l < 4; l++) {
                    if (!snapLeftMatched[l]) {
                        snapLeftBtns[l].requestFocus();
                        break;
                    }
                }
            }
        } else {
            snapLeftHadMiss[leftIdx] = true;
            currentStreak = 0;
            soundEngine.playWrong();
            snapRightFlags[rightIdx].setStampState(FlagCanvasView.STAMP_WRONG);
            snapRightFlags[rightIdx].animate().translationX(dp(8)).setDuration(60).withEndAction(() ->
                    snapRightFlags[rightIdx].animate().translationX(0).setDuration(60).start()
            ).start();
            handler.postDelayed(() -> {
                if (!snapRightMatched[rightIdx]) {
                    snapRightFlags[rightIdx].setStampState(FlagCanvasView.STAMP_NONE);
                }
                int prevL = snapSelectedLeft;
                snapSelectedLeft = -1;
                refreshSnapMatchStyles();
                if (prevL >= 0) snapLeftBtns[prevL].requestFocus();
            }, 450);
        }
    }

    private void refreshSnapMatchStyles() {
        for (int i = 0; i < 4; i++) {
            TextView lb = snapLeftBtns[i];
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dp(10));
            if (snapLeftMatched[i]) {
                gd.setColor(0x66064E3B);
                gd.setStroke(dp(2), 0xFF10B981);
                lb.setTextColor(0xFF6EE7B7);
            } else if (snapSelectedLeft == i) {
                gd.setColor(0xCC1E3A5F);
                gd.setStroke(dp(2), 0xFFFBBF24);
                lb.setTextColor(0xFFFDE047);
            } else if (lb.isFocused()) {
                gd.setColor(0xCC1E293B);
                gd.setStroke(dp(2), 0xFF38BDF8);
                lb.setTextColor(Color.WHITE);
            } else {
                gd.setColor(0x880F172A);
                gd.setStroke(dp(1), 0x4494A3B8);
                lb.setTextColor(0xFFE2E8F0);
            }
            lb.setBackground(gd);
        }
    }

    private void advanceToNextQuestionOrEnd() {
        pendingAdvance = false;
        handler.removeCallbacks(advanceRunnable);
        if (nextQuestionBtn != null) nextQuestionBtn.setVisibility(View.GONE);
        questionIndex++;
        if (questionIndex < 10) {
            bindCurrentQuestion();
        } else {
            showEndOfRoundModal();
        }
    }

    private void showEndOfRoundModal() {
        uiState = STATE_RESULT;
        gameLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        resultLayer.setVisibility(View.VISIBLE);
        resultLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        int prevBest = GeoStore.getBestScore(getContext(), activeMode, skillTier);
        boolean isNewBest = GeoStore.recordScore(getContext(), activeMode, skillTier, currentScore, maxRoundScore);
        int newBest = GeoStore.getBestScore(getContext(), activeMode, skillTier);
        int stars = GeoStore.getStarsForMode(getContext(), activeMode, skillTier);

        String starStr = (stars >= 1 ? "★ " : "☆ ") + (stars >= 2 ? "★ " : "☆ ") + (stars >= 3 ? "★" : "☆");
        resStarsText.setText(starStr);
        resNewBestBadge.setText(isNewBest ? "🏆 NEW HIGH SCORE!" : "GREAT JOB!");
        resNewBestBadge.setTextColor(isNewBest ? 0xFFFDE047 : 0xFF34D399);
        resScoreText.setText(currentScore + " / " + maxRoundScore);
        resBestSubText.setText("Best to Beat: " + Math.max(prevBest, newBest));

        if (isNewBest) {
            soundEngine.playHighScoreFanfare();
        } else {
            soundEngine.playCorrect(3);
        }
        resReplayBtn.requestFocus();
    }

    // -------------------------------------------------------------------------
    // 5. STYLING HELPERS
    // -------------------------------------------------------------------------
    private GradientDrawable makeStageGlassCard() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xD80D182E);
        gd.setCornerRadius(dp(22));
        gd.setStroke(dp(1), 0x4494A3B8);
        return gd;
    }

    private void styleChoicePill(TextView btn, int stampState, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(12));
        if (stampState == FlagCanvasView.STAMP_CORRECT) {
            gd.setColor(0xDD065F46);
            gd.setStroke(dp(2), 0xFF34D399);
            btn.setTextColor(0xFFECFDF5);
        } else if (stampState == FlagCanvasView.STAMP_WRONG) {
            gd.setColor(0xDD7F1D1D);
            gd.setStroke(dp(2), 0xFFF87171);
            btn.setTextColor(0xFFFEF2F2);
        } else if (focused) {
            gd.setColor(0xDD1E293B);
            gd.setStroke(dp(2), 0xFFFBBF24);
            btn.setTextColor(0xFFFDE047);
        } else {
            gd.setColor(0x990F172A);
            gd.setStroke(dp(1), 0x4494A3B8);
            btn.setTextColor(0xFFF1F5F9);
        }
        btn.setBackground(gd);
    }

    private void styleLetterOrb(TextView orb, boolean used, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        if (used) {
            gd.setColor(0x330F172A);
            gd.setStroke(dp(1), 0x2264748B);
            orb.setTextColor(0x4464748B);
        } else if (focused) {
            gd.setColor(0xEE1E3A5F);
            gd.setStroke(dp(2), 0xFFFBBF24);
            orb.setTextColor(0xFFFDE047);
        } else {
            gd.setColor(0xCC0F1E36);
            gd.setStroke(dp(1), 0x8838BDF8);
            orb.setTextColor(0xFFF8FAFC);
        }
        orb.setBackground(gd);
    }

    private void styleTierPill(TextView btn, boolean active, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(999));
        if (focused) {
            gd.setColor(0xDD1E3A5F);
            gd.setStroke(dp(2), 0xFFFBBF24);
            btn.setTextColor(0xFFFDE047);
        } else if (active) {
            gd.setColor(0xCC065F46);
            gd.setStroke(dp(1), 0xFF34D399);
            btn.setTextColor(0xFFA7F3D0);
        } else {
            gd.setColor(0x770F172A);
            gd.setStroke(dp(1), 0x4464748B);
            btn.setTextColor(0xFF94A3B8);
        }
        btn.setBackground(gd);
    }

    private TextView makePillButton(String text, OnClickListener onClick) {
        TextView btn = makeText(12, 0xFFF8FAFC, true);
        btn.setText(text);
        btn.setPadding(dp(14), dp(6), dp(14), dp(6));
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);
        styleTierPill(btn, false, false);
        btn.setOnFocusChangeListener((v, hasFocus) -> styleTierPill(btn, false, hasFocus));
        btn.setOnClickListener(onClick);
        return btn;
    }

    private TextView makeText(int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(false);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private void addSpacer(LinearLayout parent, int wDp, int hDp) {
        View v = new View(getContext());
        parent.addView(v, new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
    }

    public void refreshState() {
        if (uiState == STATE_HOME) {
            refreshHomeStats();
        }
    }

    public void releaseResources() {
        handler.removeCallbacksAndMessages(null);
        if (soundEngine != null) {
            soundEngine.shutdown();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

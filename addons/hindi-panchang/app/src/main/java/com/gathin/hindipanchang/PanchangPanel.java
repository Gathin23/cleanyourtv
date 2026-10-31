package com.gathin.hindipanchang;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hindi Panchang TV UI (Version 5):
 * - 12 Real AI-Generated Seasonal & Festival Artwork Backdrops (bg_month_0 .. bg_month_11)
 * - Real AI-Generated Sacred Bhojpatra / Manuscript Backdrop for सुविचार (bg_suvichar)
 * - 3D Pearlescent Lunar Moon-Orbs floating in the center with left/right ‹ › chevrons
 * - Three Circular Icon Dock Orbs underneath the calendar: "सुविचार", "पंचांग", "व्रत/पर्व"
 * - Uniform Circular Icon-Only Buttons (✕ Close in red/white, ↻ Refresh in green/yellow — zero text)
 * - iOS-style Translucent Frosted Glass Popups for पंचांग and व्रत/पर्व with the calendar visible behind
 */
final class PanchangPanel extends FrameLayout {

    private static final int HORIZONTAL = LinearLayout.HORIZONTAL;
    private static final int VERTICAL = LinearLayout.VERTICAL;

    private static final int MODAL_NONE = 0;
    private static final int MODAL_WISDOM = 1;
    private static final int MODAL_PANCHANG = 2;
    private static final int MODAL_VRAT = 3;

    private static final int TEXT_IVORY = 0xFFFFFBF2;
    private static final int TEXT_SOFT = 0xFFEAE2D0;
    private static final int TEXT_DIM = 0xFFC2B59E;
    private static final int GOLD_WARM = 0xFFFDE68A;
    private static final int GOLD_BRIGHT = 0xFFFBBF24;
    private static final int ACCENT_AMBER = 0xFFF59E0B;
    private static final int GREEN_GOOD = 0xFF34D399;
    private static final int RED_RAHU = 0xFFF87171;

    interface HostCallbacks {
        void onPickCityRequested();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean dream;
    private HostCallbacks callbacks;

    private LocationStore.Place place;
    private int monthOffset = 0;
    private LocalDate selectedDate;
    private int wisdomDayOfYear;
    private int simMinutes = 810;
    private boolean isLiveSim = true;
    private int activeModal = MODAL_NONE;
    private int focusedLimbIndex = 0;
    private View lastHomeFocus;

    // Layers
    private SacredBackdropView backdropView;
    private FrameLayout homeLayer;
    private FrameLayout wisdomFullScreenLayer;
    private FrameLayout modalOverlayLayer;

    // Main screen views
    private TextView topGregorianDate;
    private TextView topHinduSubtitle;
    private TextView btnCityPin;
    private TextView calMonthLabel;
    private CircleIconButtonView btnLeftChevron;
    private CircleIconButtonView btnRightChevron;
    private final LunarDayCellView[] moonCells = new LunarDayCellView[42];
    private LinearLayout weekRow6;
    private final IconTileView[] squareTiles = new IconTileView[3];

    // Full-Screen Sacred Manuscript (सुविचार)
    private TextView wisSource;
    private TextView wisSanskrit;
    private TextView wisHindi;
    private TextView wisEnglish;
    private CircleIconButtonView wisShuffleBtn;
    private CircleIconButtonView wisCloseBtn;

    // Modal 2: Panchang & Sun Path + Interactive 6-Limb Insight Bar (Translucent Glass)
    private LinearLayout panchangCard;
    private TextView panDateTitle;
    private TextView panSunMoonBadges;
    private CircleIconButtonView panCloseBtn;
    private SolarArcView solarArcView;
    private final LinearLayout[] limbPills = new LinearLayout[6];
    private final TextView[] limbRowLabels = new TextView[6];
    private final TextView[] limbRowMain = new TextView[6];
    private final TextView[] limbRowSub = new TextView[6];
    private TextView limbInsightBar;

    // Modal 3: Vrat & Festival + Interactive 4-Pill Upcoming Strip (Translucent Glass)
    private LinearLayout vratCard;
    private TextView vratDateHeader;
    private TextView vratMainTitle;
    private TextView vratRuleBody;
    private TextView vratMoonBadge;
    private final LinearLayout[] upcomingPills = new LinearLayout[4];
    private final TextView[] upcomingTitles = new TextView[4];
    private final TextView[] upcomingDates = new TextView[4];
    private final List<LocalDate> upcomingFestDates = new ArrayList<>();
    private CircleIconButtonView vratCloseBtn;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            ZonedDateTime now = ZonedDateTime.now(place.zoneId());
            if (isLiveSim) {
                simMinutes = now.getHour() * 60 + now.getMinute();
                if (activeModal == MODAL_PANCHANG) refreshActiveModal();
            }
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L);
        }
    };

    PanchangPanel(Context context, boolean dream) {
        super(context);
        this.dream = dream;
        this.place = LocationStore.load(context);
        ZonedDateTime now = ZonedDateTime.now(place.zoneId());
        this.selectedDate = now.toLocalDate();
        this.wisdomDayOfYear = selectedDate.getDayOfYear();
        this.simMinutes = now.getHour() * 60 + now.getMinute();

        setClipChildren(false);
        setClipToPadding(false);

        backdropView = new SacredBackdropView(context, false);
        addView(backdropView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        homeLayer = buildMinimalHomeLayer(context);
        addView(homeLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (!dream) {
            wisdomFullScreenLayer = buildFullScreenWisdomLayer(context);
            wisdomFullScreenLayer.setVisibility(View.GONE);
            addView(wisdomFullScreenLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            modalOverlayLayer = buildModalOverlayLayer(context);
            modalOverlayLayer.setVisibility(View.GONE);
            addView(modalOverlayLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    void setHostCallbacks(HostCallbacks cb) {
        this.callbacks = cb;
    }

    void updatePlace(LocationStore.Place newPlace) {
        this.place = newPlace;
        rebuildCalendarAndHeader();
    }

    boolean handleBackPress() {
        if (activeModal != MODAL_NONE) {
            closeModal();
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // 1. MINIMALIST HOME LAYER (Floating Moon Orbs + Side Chevrons + 3 Circular Dock Orbs)
    // -------------------------------------------------------------------------
    private FrameLayout buildMinimalHomeLayer(Context context) {
        FrameLayout layer = new FrameLayout(context);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        layer.setPadding(dp(36), dp(20), dp(36), dp(18));

        // Top-Right Corner: Minimal Map Pin Pill
        if (!dream) {
            btnCityPin = makePillButton("📍 " + place.name, v -> {
                if (callbacks != null) callbacks.onPickCityRequested();
            });
            LayoutParams trLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
            layer.addView(btnCityPin, trLp);
        }

        // Center Column: Floating Header + [‹ Calendar ›] + 3 Circular Dock Icons
        LinearLayout centerCol = new LinearLayout(context);
        centerCol.setOrientation(VERTICAL);
        centerCol.setGravity(Gravity.CENTER_HORIZONTAL);
        centerCol.setClipChildren(false);
        centerCol.setClipToPadding(false);

        topGregorianDate = makeText(26, TEXT_IVORY, true);
        topGregorianDate.setGravity(Gravity.CENTER);
        topGregorianDate.setShadowLayer(4f * getResources().getDisplayMetrics().density, 0, 1.5f, 0xEE000000);
        centerCol.addView(topGregorianDate, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        topHinduSubtitle = makeText(15, GOLD_WARM, false);
        topHinduSubtitle.setGravity(Gravity.CENTER);
        topHinduSubtitle.setShadowLayer(3f * getResources().getDisplayMetrics().density, 0, 1.2f, 0xEE000000);
        topHinduSubtitle.setPadding(0, dp(3), 0, dp(8));
        centerCol.addView(topHinduSubtitle, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        calMonthLabel = makeText(14, TEXT_SOFT, true);
        calMonthLabel.setGravity(Gravity.CENTER);
        calMonthLabel.setShadowLayer(3f * getResources().getDisplayMetrics().density, 0, 1f, 0xEE000000);
        calMonthLabel.setPadding(0, 0, 0, dp(6));
        centerCol.addView(calMonthLabel, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Horizontal Row: [Left Chevron ‹] + [7-Column Moon Orb Grid] + [Right Chevron ›]
        LinearLayout calWithFlanks = new LinearLayout(context);
        calWithFlanks.setOrientation(HORIZONTAL);
        calWithFlanks.setGravity(Gravity.CENTER_VERTICAL);
        calWithFlanks.setClipChildren(false);
        calWithFlanks.setClipToPadding(false);

        if (!dream) {
            btnLeftChevron = new CircleIconButtonView(context, CircleIconButtonView.TYPE_CHEVRON_LEFT, v -> shiftMonth(-1));
            btnLeftChevron.setId(View.generateViewId());
            btnLeftChevron.setNextFocusLeftId(btnLeftChevron.getId());
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            leftLp.setMarginEnd(dp(14));
            calWithFlanks.addView(btnLeftChevron, leftLp);
        }

        LinearLayout gridCol = new LinearLayout(context);
        gridCol.setOrientation(VERTICAL);
        gridCol.setGravity(Gravity.CENTER_HORIZONTAL);
        gridCol.setClipChildren(false);
        gridCol.setClipToPadding(false);

        String[] shortWk = {"रवि", "सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि"};
        LinearLayout wkRow = new LinearLayout(context);
        wkRow.setOrientation(HORIZONTAL);
        wkRow.setGravity(Gravity.CENTER);
        wkRow.setPadding(0, 0, 0, dp(2));
        for (int i = 0; i < 7; i++) {
            TextView w = makeText(11, (i == 0 || i == 6) ? GOLD_BRIGHT : TEXT_DIM, true);
            w.setGravity(Gravity.CENTER);
            w.setShadowLayer(2.5f * getResources().getDisplayMetrics().density, 0, 1f, 0xDD000000);
            w.setText(shortWk[i]);
            wkRow.addView(w, new LinearLayout.LayoutParams(dp(60), dp(18)));
        }
        gridCol.addView(wkRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        for (int r = 0; r < 6; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            if (r == 5) weekRow6 = row;
            for (int c = 0; c < 7; c++) {
                final int idx = r * 7 + c;
                LunarDayCellView orb = new LunarDayCellView(context);
                orb.setId(View.generateViewId());
                orb.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus && orb.getDate() != null) {
                        lastHomeFocus = v;
                        selectDate(orb.getDate());
                    }
                });
                orb.setOnClickListener(v -> {
                    if (orb.getDate() != null) {
                        lastHomeFocus = v;
                        selectDate(orb.getDate());
                        openModal(MODAL_PANCHANG);
                    }
                });
                row.addView(orb, new LinearLayout.LayoutParams(dp(60), dp(46)));
                moonCells[idx] = orb;
            }
            gridCol.addView(row, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        calWithFlanks.addView(gridCol, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        if (!dream) {
            btnRightChevron = new CircleIconButtonView(context, CircleIconButtonView.TYPE_CHEVRON_RIGHT, v -> shiftMonth(1));
            btnRightChevron.setId(View.generateViewId());
            btnRightChevron.setNextFocusRightId(btnRightChevron.getId());
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            rightLp.setMarginStart(dp(14));
            calWithFlanks.addView(btnRightChevron, rightLp);
        }

        centerCol.addView(calWithFlanks, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Bottom Row: 3 Circular Icon Orbs with Short 1-Word Titles Underneath ("सुविचार", "पंचांग", "व्रत/पर्व")
        if (!dream) {
            LinearLayout tilesRow = new LinearLayout(context);
            tilesRow.setOrientation(HORIZONTAL);
            tilesRow.setGravity(Gravity.CENTER);
            tilesRow.setClipChildren(false);
            tilesRow.setClipToPadding(false);
            tilesRow.setPadding(dp(24), dp(10), dp(24), dp(6));

            String[] tileNames = {"सुविचार", "पंचांग", "व्रत/पर्व"};
            int[] icons = {IconTileView.ICON_WISDOM, IconTileView.ICON_PANCHANG, IconTileView.ICON_VRAT};
            int[] modals = {MODAL_WISDOM, MODAL_PANCHANG, MODAL_VRAT};

            for (int i = 0; i < 3; i++) {
                final int targetModal = modals[i];
                IconTileView tile = new IconTileView(context, icons[i], tileNames[i]);
                tile.setId(View.generateViewId());
                tile.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) lastHomeFocus = v;
                });
                tile.setOnClickListener(v -> {
                    lastHomeFocus = v;
                    if (targetModal == MODAL_WISDOM) {
                        wisdomDayOfYear = selectedDate.getDayOfYear();
                    }
                    openModal(targetModal);
                });
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(dp(82), dp(76));
                if (i < 2) tLp.setMarginEnd(dp(32));
                tilesRow.addView(tile, tLp);
                squareTiles[i] = tile;
            }

            centerCol.addView(tilesRow, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }

        LayoutParams centerLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        layer.addView(centerCol, centerLp);

        return layer;
    }

    // -------------------------------------------------------------------------
    // 2. FULL-SCREEN SACRED BHOJPATRA MANUSCRIPT LAYER (सुविचार)
    // -------------------------------------------------------------------------
    private FrameLayout buildFullScreenWisdomLayer(Context context) {
        FrameLayout screen = new FrameLayout(context);
        screen.setClickable(true);

        // Real AI-Generated Sacred Bhojpatra / Ancient Manuscript Artwork Background
        SacredBackdropView suvicharBg = new SacredBackdropView(context, true);
        screen.addView(suvicharBg, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        // Comfortably inset inside the Bhojpatra inner filigree rectangle border
        page.setPadding(dp(138), dp(70), dp(138), dp(68));

        TextView emblem = makeText(14, 0xFFD8A858, true);
        emblem.setText("☸   सुविचार   ·   श्रीमद्भगवद्गीता एवं सद्ग्रंथ अमृत   ☸");
        emblem.setGravity(Gravity.CENTER);
        emblem.setShadowLayer(4f * getResources().getDisplayMetrics().density, 0, 1.5f, 0xE6221004);
        page.addView(emblem);

        wisSource = makeText(12, 0xFFC8A06A, false);
        wisSource.setGravity(Gravity.CENTER);
        wisSource.setShadowLayer(3f * getResources().getDisplayMetrics().density, 0, 1f, 0xE6221004);
        wisSource.setPadding(0, dp(4), 0, dp(12));
        page.addView(wisSource);

        // Original Sanskrit Shloka / Doha (Warm Illuminated Gold)
        wisSanskrit = makeText(21, 0xFFFFE599, true);
        wisSanskrit.setGravity(Gravity.CENTER);
        wisSanskrit.setLineSpacing(dp(5), 1.0f);
        wisSanskrit.setShadowLayer(5f * getResources().getDisplayMetrics().density, 0, 1.8f, 0xF01E0E04);
        wisSanskrit.setPadding(dp(20), dp(2), dp(20), dp(12));
        page.addView(wisSanskrit, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Clean Ornamental Divider Line
        TextView divider = makeText(13, 0xFFCFA258, false);
        divider.setText("──────────   ✦   ──────────");
        divider.setGravity(Gravity.CENTER);
        divider.setShadowLayer(3f * getResources().getDisplayMetrics().density, 0, 1f, 0xE6221004);
        divider.setPadding(0, 0, 0, dp(10));
        page.addView(divider);

        // Hindi भावार्थ (Warm Chandan / Parchment Cream — never stark digital white)
        wisHindi = makeText(16, 0xFFF2DEC0, false);
        wisHindi.setGravity(Gravity.CENTER);
        wisHindi.setLineSpacing(dp(4), 1.0f);
        wisHindi.setShadowLayer(4f * getResources().getDisplayMetrics().density, 0, 1.5f, 0xF01E0E04);
        wisHindi.setPadding(dp(24), 0, dp(24), dp(10));
        page.addView(wisHindi, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // English Translation (Warm Muted Sepia / Sand)
        wisEnglish = makeText(13, 0xFFD4B894, false);
        wisEnglish.setGravity(Gravity.CENTER);
        wisEnglish.setLineSpacing(dp(3), 1.0f);
        wisEnglish.setShadowLayer(4f * getResources().getDisplayMetrics().density, 0, 1.5f, 0xF01E0E04);
        wisEnglish.setPadding(dp(24), 0, dp(24), dp(14));
        page.addView(wisEnglish, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Bottom Actions: Two Minimal Circular Icon-Only Buttons [↻] and [✕]
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        actions.setPadding(dp(8), dp(2), dp(8), dp(2));

        wisShuffleBtn = new CircleIconButtonView(context, CircleIconButtonView.TYPE_REFRESH, v -> {
            Wisdom365.Thought next = Wisdom365.randomOtherThan(wisdomDayOfYear);
            wisdomDayOfYear = next.dayOfYear;
            refreshActiveModal();
        });
        wisShuffleBtn.setId(View.generateViewId());

        wisCloseBtn = new CircleIconButtonView(context, CircleIconButtonView.TYPE_CLOSE, v -> closeModal());
        wisCloseBtn.setId(View.generateViewId());

        wisShuffleBtn.setNextFocusLeftId(wisShuffleBtn.getId());
        wisShuffleBtn.setNextFocusRightId(wisCloseBtn.getId());
        wisShuffleBtn.setNextFocusUpId(wisShuffleBtn.getId());
        wisShuffleBtn.setNextFocusDownId(wisShuffleBtn.getId());

        wisCloseBtn.setNextFocusLeftId(wisShuffleBtn.getId());
        wisCloseBtn.setNextFocusRightId(wisCloseBtn.getId());
        wisCloseBtn.setNextFocusUpId(wisCloseBtn.getId());
        wisCloseBtn.setNextFocusDownId(wisCloseBtn.getId());

        actions.addView(wisShuffleBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        addSpacer(actions, 20, 1);
        actions.addView(wisCloseBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        page.addView(actions);

        screen.addView(page, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        return screen;
    }

    // -------------------------------------------------------------------------
    // 3. TRANSLUCENT FROSTED GLASS MODAL OVERLAY (पंचांग & व्रत/पर्व)
    // -------------------------------------------------------------------------
    private FrameLayout buildModalOverlayLayer(Context context) {
        FrameLayout overlay = new FrameLayout(context);
        // Subtle 52% dim so the Moon Orbs and Month Artwork remain softly visible behind the glass popup!
        overlay.setBackgroundColor(0x85050406);
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.setClickable(true);

        panchangCard = buildPanchangSunPathModal(context);
        vratCard = buildVratMinimalModal(context);

        overlay.addView(panchangCard, new LayoutParams(dp(810), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        overlay.addView(vratCard, new LayoutParams(dp(660), LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        return overlay;
    }

    private GradientDrawable makeFrostedGlassCard() {
        GradientDrawable glass = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC1E1822, 0xD8120F17}
        );
        glass.setCornerRadius(dp(22));
        glass.setStroke(dp(1), 0x55FFFBEB);
        return glass;
    }

    // MODAL 2: Translucent Glass Panchang & Sun Path Card + Interactive 6-Limb Insight Bar
    private LinearLayout buildPanchangSunPathModal(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setPadding(dp(24), dp(18), dp(24), dp(18));
        card.setBackground(makeFrostedGlassCard());

        // Top Bar: Date on Left, Sunrise / Sunset / Moonrise Icon Badges + Circular ✕ on Right
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setClipChildren(false);
        topRow.setClipToPadding(false);
        topRow.setPadding(0, 0, 0, dp(10));

        panDateTitle = makeText(16, TEXT_IVORY, true);
        topRow.addView(panDateTitle, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        panSunMoonBadges = makeText(13, GOLD_WARM, true);
        topRow.addView(panSunMoonBadges);

        addSpacer(topRow, 14, 1);
        panCloseBtn = new CircleIconButtonView(context, CircleIconButtonView.TYPE_CLOSE, v -> closeModal());
        panCloseBtn.setId(View.generateViewId());
        topRow.addView(panCloseBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
        card.addView(topRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Middle Rectangle: Compact Solar Arc with D-Pad Left/Right Time Slider
        LinearLayout arcBox = new LinearLayout(context);
        arcBox.setOrientation(VERTICAL);
        GradientDrawable arcBg = new GradientDrawable();
        arcBg.setColor(0x990A080C);
        arcBg.setCornerRadius(dp(14));
        arcBg.setStroke(dp(1), 0x33FFFFFF);
        arcBox.setBackground(arcBg);

        solarArcView = new SolarArcView(context);
        solarArcView.setId(View.generateViewId());
        solarArcView.setOnTimeSimListener((mins, live) -> {
            simMinutes = mins;
            isLiveSim = live;
            updateLimbInsightText();
        });
        arcBox.addView(solarArcView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(148)));
        card.addView(arcBox, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Single Horizontal Row of 6 Focusable Glass Limb Pills
        LinearLayout row6 = new LinearLayout(context);
        row6.setOrientation(HORIZONTAL);
        row6.setPadding(0, dp(12), 0, dp(8));

        for (int i = 0; i < 6; i++) {
            final int limbIdx = i;
            LinearLayout pill = new LinearLayout(context);
            pill.setId(View.generateViewId());
            pill.setOrientation(VERTICAL);
            pill.setGravity(Gravity.CENTER_HORIZONTAL);
            pill.setPadding(dp(6), dp(8), dp(6), dp(8));
            pill.setFocusable(true);
            pill.setFocusableInTouchMode(true);
            styleGlassPill(pill, i == 5, false);

            pill.setOnFocusChangeListener((v, hasFocus) -> {
                styleGlassPill(pill, limbIdx == 5, hasFocus);
                if (hasFocus) {
                    focusedLimbIndex = limbIdx;
                    updateLimbInsightText();
                }
            });

            TextView lbl = makeText(10, TEXT_DIM, false);
            lbl.setSingleLine(true);
            pill.addView(lbl);
            limbRowLabels[i] = lbl;

            TextView val = makeText(13, i == 5 ? GREEN_GOOD : TEXT_IVORY, true);
            val.setSingleLine(true);
            val.setEllipsize(TextUtils.TruncateAt.END);
            val.setPadding(0, dp(2), 0, dp(1));
            pill.addView(val);
            limbRowMain[i] = val;

            TextView sub = makeText(10, i == 5 ? RED_RAHU : GOLD_WARM, false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            pill.addView(sub);
            limbRowSub[i] = sub;

            limbPills[i] = pill;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            if (i < 5) lp.setMarginEnd(dp(6));
            row6.addView(pill, lp);
        }

        // Strict focus wiring inside Panchang Modal
        solarArcView.setNextFocusUpId(panCloseBtn.getId());
        solarArcView.setNextFocusDownId(limbPills[0].getId());
        panCloseBtn.setNextFocusDownId(solarArcView.getId());
        panCloseBtn.setNextFocusUpId(panCloseBtn.getId());
        panCloseBtn.setNextFocusLeftId(panCloseBtn.getId());
        panCloseBtn.setNextFocusRightId(panCloseBtn.getId());

        for (int i = 0; i < 6; i++) {
            limbPills[i].setNextFocusUpId(solarArcView.getId());
            limbPills[i].setNextFocusDownId(panCloseBtn.getId());
            limbPills[i].setNextFocusLeftId(limbPills[Math.max(0, i - 1)].getId());
            limbPills[i].setNextFocusRightId(limbPills[Math.min(5, i + 1)].getId());
        }

        card.addView(row6, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // 1-Line Vedic Insight Bar under the 6 Pills
        limbInsightBar = makeText(12, GOLD_WARM, false);
        limbInsightBar.setGravity(Gravity.CENTER);
        limbInsightBar.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(0x880B090F);
        inBg.setCornerRadius(dp(10));
        inBg.setStroke(dp(1), 0x33FFFFFF);
        limbInsightBar.setBackground(inBg);
        card.addView(limbInsightBar, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        return card;
    }

    private void styleGlassPill(LinearLayout pill, boolean greenAccent, boolean focused) {
        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(focused ? 0xCC332214 : 0x771A1622);
        pBg.setCornerRadius(dp(12));
        pBg.setStroke(dp(focused ? 2 : 1), focused ? ACCENT_AMBER : (greenAccent ? 0x8810B981 : 0x38FFFFFF));
        pill.setBackground(pBg);
    }

    // MODAL 3: Translucent Frosted Glass Vrat/Parva Popup + Circular Red ✕ Button
    private LinearLayout buildVratMinimalModal(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setPadding(dp(26), dp(22), dp(26), dp(18));
        card.setBackground(makeFrostedGlassCard());

        vratDateHeader = makeText(13, GOLD_WARM, false);
        vratDateHeader.setGravity(Gravity.CENTER);
        card.addView(vratDateHeader);

        vratMainTitle = makeText(20, TEXT_IVORY, true);
        vratMainTitle.setGravity(Gravity.CENTER);
        vratMainTitle.setPadding(0, dp(8), 0, dp(6));
        card.addView(vratMainTitle);

        vratRuleBody = makeText(14, TEXT_SOFT, false);
        vratRuleBody.setGravity(Gravity.CENTER);
        vratRuleBody.setLineSpacing(dp(4), 1.0f);
        vratRuleBody.setPadding(dp(8), dp(2), dp(8), dp(8));
        card.addView(vratRuleBody);

        vratMoonBadge = makeText(13, GOLD_BRIGHT, true);
        vratMoonBadge.setGravity(Gravity.CENTER);
        vratMoonBadge.setPadding(0, 0, 0, dp(12));
        card.addView(vratMoonBadge);

        // Clean Upcoming Section Title (no instructional text clutter)
        TextView upHeader = makeText(11, TEXT_DIM, true);
        upHeader.setText("आगामी व्रत एवं पर्व");
        upHeader.setGravity(Gravity.CENTER);
        upHeader.setPadding(0, 0, 0, dp(6));
        card.addView(upHeader);

        LinearLayout upRow = new LinearLayout(context);
        upRow.setOrientation(HORIZONTAL);
        upRow.setPadding(0, 0, 0, dp(14));

        for (int i = 0; i < 4; i++) {
            final int uIdx = i;
            LinearLayout uPill = new LinearLayout(context);
            uPill.setId(View.generateViewId());
            uPill.setOrientation(VERTICAL);
            uPill.setGravity(Gravity.CENTER);
            uPill.setPadding(dp(8), dp(8), dp(8), dp(8));
            uPill.setFocusable(true);
            uPill.setFocusableInTouchMode(true);
            styleGlassPill(uPill, false, false);

            uPill.setOnFocusChangeListener((v, hasFocus) -> styleGlassPill(uPill, false, hasFocus));
            uPill.setOnClickListener(v -> {
                if (uIdx < upcomingFestDates.size()) {
                    jumpToDate(upcomingFestDates.get(uIdx));
                    refreshActiveModal();
                }
            });

            TextView ut = makeText(12, TEXT_IVORY, true);
            ut.setSingleLine(true);
            ut.setEllipsize(TextUtils.TruncateAt.END);
            ut.setGravity(Gravity.CENTER);
            uPill.addView(ut);
            upcomingTitles[i] = ut;

            TextView ud = makeText(11, GOLD_WARM, false);
            ud.setSingleLine(true);
            ud.setGravity(Gravity.CENTER);
            ud.setPadding(0, dp(2), 0, 0);
            uPill.addView(ud);
            upcomingDates[i] = ud;

            upcomingPills[i] = uPill;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            if (i < 3) lp.setMarginEnd(dp(8));
            upRow.addView(uPill, lp);
        }
        card.addView(upRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Small Circular Red ✕ Button at bottom center
        vratCloseBtn = new CircleIconButtonView(context, CircleIconButtonView.TYPE_CLOSE, v -> closeModal());
        vratCloseBtn.setId(View.generateViewId());
        card.addView(vratCloseBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        // Strict focus wiring inside Vrat Modal
        for (int i = 0; i < 4; i++) {
            upcomingPills[i].setNextFocusUpId(upcomingPills[i].getId());
            upcomingPills[i].setNextFocusDownId(vratCloseBtn.getId());
            upcomingPills[i].setNextFocusLeftId(upcomingPills[Math.max(0, i - 1)].getId());
            upcomingPills[i].setNextFocusRightId(upcomingPills[Math.min(3, i + 1)].getId());
        }
        vratCloseBtn.setNextFocusUpId(upcomingPills[0].getId());
        vratCloseBtn.setNextFocusDownId(vratCloseBtn.getId());
        vratCloseBtn.setNextFocusLeftId(upcomingPills[0].getId());
        vratCloseBtn.setNextFocusRightId(upcomingPills[3].getId());

        return card;
    }

    // -------------------------------------------------------------------------
    // 4. STATE, FOCUS LOCKING & RENDERING
    // -------------------------------------------------------------------------
    void start() {
        handler.removeCallbacks(tick);
        rebuildCalendarAndHeader();
        if (!dream) {
            post(this::focusSelectedMoonOrb);
        }
        tick.run();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    void shiftMonth(int delta) {
        if (activeModal != MODAL_NONE) return;
        boolean keepLeftFocus = btnLeftChevron != null && btnLeftChevron.hasFocus();
        boolean keepRightFocus = btnRightChevron != null && btnRightChevron.hasFocus();

        monthOffset += delta;
        LocalDate today = LocalDate.now(place.zoneId());
        LocalDate targetMonthFirst = today.withDayOfMonth(1).plusMonths(monthOffset);
        int dom = Math.min(selectedDate.getDayOfMonth(), targetMonthFirst.lengthOfMonth());
        selectedDate = (monthOffset == 0) ? today : targetMonthFirst.withDayOfMonth(dom);
        rebuildCalendarAndHeader();

        if (keepLeftFocus) {
            btnLeftChevron.requestFocus();
        } else if (keepRightFocus) {
            btnRightChevron.requestFocus();
        } else {
            focusSelectedMoonOrb();
        }
    }

    void jumpToToday() {
        monthOffset = 0;
        ZonedDateTime now = ZonedDateTime.now(place.zoneId());
        selectedDate = now.toLocalDate();
        simMinutes = now.getHour() * 60 + now.getMinute();
        isLiveSim = true;
        rebuildCalendarAndHeader();
        focusSelectedMoonOrb();
    }

    private void jumpToDate(LocalDate target) {
        LocalDate todayFirst = LocalDate.now(place.zoneId()).withDayOfMonth(1);
        LocalDate targetFirst = target.withDayOfMonth(1);
        monthOffset = (targetFirst.getYear() - todayFirst.getYear()) * 12
                + (targetFirst.getMonthValue() - todayFirst.getMonthValue());
        selectedDate = target;
        rebuildCalendarAndHeader();
    }

    private void selectDate(LocalDate d) {
        selectedDate = d;
        for (LunarDayCellView orb : moonCells) {
            if (orb != null && orb.getDate() != null) {
                orb.setSelectedDay(d.equals(orb.getDate()));
            }
        }
        updateHeaderAndTileBadges();
    }

    private void focusSelectedMoonOrb() {
        for (LunarDayCellView orb : moonCells) {
            if (orb != null && selectedDate.equals(orb.getDate())) {
                orb.requestFocus();
                return;
            }
        }
    }

    private void openModal(int modal) {
        this.activeModal = modal;
        // Block D-pad focus from ever leaking to homeLayer!
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        if (modal == MODAL_WISDOM) {
            homeLayer.setVisibility(View.INVISIBLE);
            modalOverlayLayer.setVisibility(View.GONE);
            wisdomFullScreenLayer.setVisibility(View.VISIBLE);
            refreshActiveModal();
            wisShuffleBtn.requestFocus();
        } else {
            // Keep homeLayer VISIBLE behind the translucent frosted glass popup!
            homeLayer.setVisibility(View.VISIBLE);
            wisdomFullScreenLayer.setVisibility(View.GONE);
            modalOverlayLayer.setVisibility(View.VISIBLE);
            panchangCard.setVisibility(modal == MODAL_PANCHANG ? View.VISIBLE : View.GONE);
            vratCard.setVisibility(modal == MODAL_VRAT ? View.VISIBLE : View.GONE);
            refreshActiveModal();
            if (modal == MODAL_PANCHANG) {
                solarArcView.requestFocus();
            } else if (modal == MODAL_VRAT) {
                upcomingPills[0].requestFocus();
            }
        }
    }

    private void closeModal() {
        this.activeModal = MODAL_NONE;
        wisdomFullScreenLayer.setVisibility(View.GONE);
        modalOverlayLayer.setVisibility(View.GONE);

        homeLayer.setVisibility(View.VISIBLE);
        homeLayer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (lastHomeFocus != null) {
            lastHomeFocus.requestFocus();
        } else {
            focusSelectedMoonOrb();
        }
    }

    private void rebuildCalendarAndHeader() {
        if (btnCityPin != null) btnCityPin.setText("📍 " + place.name);

        LocalDate today = LocalDate.now(place.zoneId());
        LocalDate firstOfMonth = today.withDayOfMonth(1).plusMonths(monthOffset);
        int length = firstOfMonth.lengthOfMonth();
        int lead = firstOfMonth.getDayOfWeek().getValue() % 7;

        PanchangEngine.DayPanchang firstDp = PanchangEngine.calculate(firstOfMonth, place);
        PanchangEngine.DayPanchang midDp = PanchangEngine.calculate(firstOfMonth.withDayOfMonth(15), place);
        PanchangEngine.DayPanchang lastDp = PanchangEngine.calculate(firstOfMonth.withDayOfMonth(length), place);

        backdropView.setHindiMonth(midDp.monthIndex);

        String gregMonth = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).format(firstOfMonth);
        String hindiSpan = firstDp.monthIndex == lastDp.monthIndex
                ? firstDp.monthHi
                : (firstDp.monthHi + " – " + lastDp.monthHi);
        calMonthLabel.setText(gregMonth + "   ·   " + hindiSpan);

        boolean needRow6 = (lead + length) > 35;
        if (weekRow6 != null) weekRow6.setVisibility(needRow6 ? View.VISIBLE : View.GONE);

        int firstActiveId = View.NO_ID;
        int lastActiveId = View.NO_ID;

        for (int i = 0; i < 42; i++) {
            LunarDayCellView orb = moonCells[i];
            int dayNum = i - lead + 1;
            if (dayNum < 1 || dayNum > length) {
                orb.bind(null, null, false, false);
                continue;
            }
            if (firstActiveId == View.NO_ID) firstActiveId = orb.getId();
            lastActiveId = orb.getId();

            LocalDate d = firstOfMonth.plusDays(dayNum - 1);
            PanchangEngine.DayPanchang dp = PanchangEngine.calculate(d, place);
            orb.bind(d, dp, d.equals(today), d.equals(selectedDate));

            if (!dream) {
                int col = i % 7;
                if (col == 0 || dayNum == 1) {
                    orb.setNextFocusLeftId(btnLeftChevron.getId());
                } else {
                    orb.setNextFocusLeftId(View.NO_ID);
                }
                if (col == 6 || dayNum == length) {
                    orb.setNextFocusRightId(btnRightChevron.getId());
                } else {
                    orb.setNextFocusRightId(View.NO_ID);
                }
            }
        }

        if (!dream && btnLeftChevron != null && btnRightChevron != null) {
            btnLeftChevron.setNextFocusRightId(firstActiveId);
            btnRightChevron.setNextFocusLeftId(lastActiveId);
        }

        updateHeaderAndTileBadges();
        if (activeModal != MODAL_NONE) refreshActiveModal();
    }

    private void updateHeaderAndTileBadges() {
        PanchangEngine.DayPanchang dp = PanchangEngine.calculate(selectedDate, place);
        backdropView.setHindiMonth(dp.monthIndex);

        String gregLine = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH).format(selectedDate);
        topGregorianDate.setText(gregLine);

        String festSuffix = !dp.festivalsHi.isEmpty() ? ("   •   ✨ " + dp.festivalsHi.get(0)) : "";
        topHinduSubtitle.setText(
                "विक्रम संवत " + PanchangEngine.toDevanagariDigits(dp.vikramSamvat)
                + "   ·   " + dp.monthHi + " " + dp.pakshaHi + " " + dp.tithiHi
                + " (" + dp.tithiEn + ")"
                + "   ·   " + dp.varaHi
                + festSuffix
        );

        if (!dream && squareTiles[2] != null) {
            squareTiles[2].setBadgeDot(!dp.festivalsHi.isEmpty() || dp.hasVratBanner);
        }
    }

    private void refreshActiveModal() {
        if (activeModal == MODAL_WISDOM) {
            Wisdom365.Thought t = Wisdom365.forDayOfYear(wisdomDayOfYear);
            wisSource.setText(t.sourceHi + "   •   " + t.sourceEn);
            wisSanskrit.setText(t.verse);
            wisHindi.setText(t.meaningHi);
            wisEnglish.setText(t.meaningEn);
        } else if (activeModal == MODAL_PANCHANG) {
            PanchangEngine.DayPanchang dp = PanchangEngine.calculate(selectedDate, place);
            String dStr = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(selectedDate);
            panDateTitle.setText(dStr + "  ·  " + dp.monthHi + " " + dp.pakshaHi + " " + dp.tithiHi);
            panSunMoonBadges.setText("🌅 " + dp.sunriseFormatted + "    🌇 " + dp.sunsetFormatted + "    🌙 " + dp.moonriseFormatted);

            solarArcView.bind(dp, simMinutes, isLiveSim, LocationStore.LANG_BILINGUAL);

            limbRowLabels[0].setText("◐ तिथि · Tithi");
            limbRowMain[0].setText(dp.tithiHi + " (" + dp.tithiEn + ")");
            limbRowSub[0].setText(dp.tithiEndFormatted + " तक");

            limbRowLabels[1].setText("☉ वार · Vara");
            limbRowMain[1].setText(dp.varaHi);
            limbRowSub[1].setText(dp.varaEn);

            limbRowLabels[2].setText("✦ नक्षत्र · Nakshatra");
            limbRowMain[2].setText(dp.nakshatraHi);
            limbRowSub[2].setText(dp.nakshatraEndFormatted + " तक");

            limbRowLabels[3].setText("∞ योग · Yoga");
            limbRowMain[3].setText(dp.yogaHi);
            limbRowSub[3].setText(dp.yogaEndFormatted + " तक");

            limbRowLabels[4].setText("◈ करण · Karana");
            limbRowMain[4].setText(dp.karanaHi);
            limbRowSub[4].setText(dp.karanaEndFormatted + " तक");

            limbRowLabels[5].setText("🟢 शुभ / 🔴 राहु");
            limbRowMain[5].setText(dp.abhijitFormatted);
            limbRowSub[5].setText("राहु: " + dp.rahuKaalFormatted);

            updateLimbInsightText();
        } else if (activeModal == MODAL_VRAT) {
            PanchangEngine.DayPanchang dp = PanchangEngine.calculate(selectedDate, place);
            String dStr = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).format(selectedDate);
            vratDateHeader.setText("🪔   " + dStr + "   ·   " + dp.monthHi + " " + dp.pakshaHi + " " + dp.tithiHi);

            if (!dp.festivalsHi.isEmpty()) {
                vratMainTitle.setText(dp.festivalsHi.get(0) + "  (" + dp.festivalsEn.get(0) + ")");
                vratRuleBody.setText(dp.hasVratBanner ? (dp.vratRuleHi + "\n" + dp.vratRuleEn)
                        : ("पूजा शुभ मुहूर्त (Abhijit): " + dp.abhijitFormatted + "   ·   सायं संध्या आरती: " + dp.godhuliMuhuratFormatted));
            } else if (dp.hasVratBanner) {
                vratMainTitle.setText(dp.vratTitleHi + "  (" + dp.vratTitleEn + ")");
                vratRuleBody.setText(dp.vratRuleHi + "\n" + dp.vratRuleEn);
            } else {
                vratMainTitle.setText("आज कोई प्रमुख व्रत या पर्व नहीं है");
                vratRuleBody.setText("नित्य प्रातः संध्या: " + dp.pratahSandhyaFormatted + "   ·   सायं गोधूलि आरती: " + dp.godhuliMuhuratFormatted);
            }

            String moonLine;
            if (dp.moonriseMin >= dp.sunriseMin && dp.moonriseMin < dp.sunsetMin) {
                // Shukla Paksha: Moon rises in daylight and is visible in the evening until Moonset
                moonLine = "🌙 सायं चन्द्र दर्शन (अस्त: " + dp.moonsetFormatted + " · उदय: " + dp.moonriseFormatted + ")";
            } else {
                moonLine = "🌙 रात्रि चन्द्रोदय: " + dp.moonriseFormatted;
            }
            vratMoonBadge.setText(moonLine + "     ·     🌅 सूर्योदय: " + dp.sunriseFormatted);

            upcomingFestDates.clear();
            List<PanchangEngine.DayPanchang> upDps = new ArrayList<>();
            for (int step = 1; step <= 65 && upcomingFestDates.size() < 4; step++) {
                LocalDate cand = selectedDate.plusDays(step);
                PanchangEngine.DayPanchang cdp = PanchangEngine.calculate(cand, place);
                if (!cdp.festivalsHi.isEmpty()) {
                    upcomingFestDates.add(cand);
                    upDps.add(cdp);
                }
            }
            for (int i = 0; i < 4; i++) {
                if (i < upcomingFestDates.size()) {
                    LocalDate fd = upcomingFestDates.get(i);
                    PanchangEngine.DayPanchang fdp = upDps.get(i);
                    long days = ChronoUnit.DAYS.between(selectedDate, fd);
                    String fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).format(fd);
                    upcomingTitles[i].setText(fdp.festivalsHi.get(0));
                    upcomingDates[i].setText(fmt + " · " + days + "d");
                } else {
                    upcomingTitles[i].setText("—");
                    upcomingDates[i].setText("");
                }
            }
        }
    }

    private void updateLimbInsightText() {
        if (limbInsightBar == null) return;
        PanchangEngine.DayPanchang dp = PanchangEngine.calculate(selectedDate, place);
        String msg;
        switch (focusedLimbIndex) {
            case 0:
                msg = "◐ तिथि विचार: " + dp.pakshaHi + " " + dp.tithiHi + " (" + dp.tithiEndFormatted + " तक, आगे " + dp.nextTithiHi + ") — चन्द्र कला " + dp.moonIlluminationPercent + "% प्रकाशित।";
                break;
            case 1:
                msg = "☉ वार एवं राशि: " + dp.varaHi + " (" + dp.varaEn + ")   •   चन्द्र राशि: " + dp.moonRashiHi + "   •   सूर्य राशि: " + dp.sunRashiHi + "।";
                break;
            case 2:
                String nakAlert = dp.hasGandmool ? " (गण्डमूल नक्षत्र)" : (dp.hasPanchak ? " (पंचक नक्षत्र)" : " (शुभ नक्षत्र)");
                msg = "✦ नक्षत्र विचार: " + dp.nakshatraHi + " (" + dp.nakshatraEn + ") " + dp.nakshatraEndFormatted + " तक" + nakAlert + "।";
                break;
            case 3:
                msg = "∞ योग विचार: " + dp.yogaHi + " (" + dp.yogaEn + ") योग " + dp.yogaEndFormatted + " तक सक्रिय रहेगा।";
                break;
            case 4:
                msg = "◈ करण एवं भद्रा: " + dp.karanaHi + " (" + dp.karanaEndFormatted + " तक)   •   " + dp.bhadraNoteHi + "।";
                break;
            case 5:
            default:
                msg = "🟢 ब्रह्म मुहूर्त: " + dp.brahmaMuhuratFormatted + "  ·  अभिजीत: " + dp.abhijitFormatted + "  ·  गोधूलि: " + dp.godhuliMuhuratFormatted + "  |  🔴 राहुकाल: " + dp.rahuKaalFormatted;
                break;
        }
        limbInsightBar.setText(msg);
    }

    // -------------------------------------------------------------------------
    // 5. UI HELPERS
    // -------------------------------------------------------------------------
    private TextView makeText(int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(false);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView makePillButton(String text, OnClickListener onClick) {
        TextView btn = makeText(12, TEXT_SOFT, true);
        btn.setText(text);
        btn.setPadding(dp(14), dp(6), dp(14), dp(6));
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);
        stylePill(btn, false);
        btn.setOnFocusChangeListener((v, hasFocus) -> stylePill(btn, hasFocus));
        btn.setOnClickListener(onClick);
        return btn;
    }

    private void stylePill(TextView btn, boolean focused) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(focused ? 0xCC332214 : 0xAA16100C);
        gd.setCornerRadius(dp(999));
        gd.setStroke(dp(focused ? 2 : 1), focused ? GOLD_BRIGHT : 0x66785634);
        btn.setBackground(gd);
        btn.setTextColor(focused ? GOLD_WARM : TEXT_IVORY);
    }

    private void addSpacer(LinearLayout parent, int wDp, int hDp) {
        View v = new View(getContext());
        parent.addView(v, new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

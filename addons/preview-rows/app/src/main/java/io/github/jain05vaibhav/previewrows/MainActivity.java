package io.github.jain05vaibhav.previewrows;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.jain05vaibhav.previewrows.PreviewModels.CardItem;
import io.github.jain05vaibhav.previewrows.PreviewModels.ChannelRow;
import io.github.jain05vaibhav.previewrows.PreviewModels.FocusTarget;
import io.github.jain05vaibhav.previewrows.PreviewModels.IntentChoice;
import io.github.jain05vaibhav.previewrows.PreviewModels.PosterSource;
import io.github.jain05vaibhav.previewrows.TvPreviewRepository.LoadResult;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays other apps' preview channels (TvContract.PreviewPrograms) as rows of still cards.
 * Reads only when shown or refreshed; never polls, never runs a background service, and never
 * autoplays video.
 */
public final class MainActivity extends Activity {

    private static final int REQ_READ_TV_LISTINGS = 1;
    private static final int POSTER_CACHE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BOUND_POSTERS = 24;
    private static final int MAX_CARDS_PER_ROW = 30;

    private static final class CardPosterSlot {
        final View card;
        final HorizontalScrollView rowScroll;
        final ImageView posterView;
        final ImageView fallbackArt;
        final String uri;
        boolean bound;
        long lastUsedSeq;

        CardPosterSlot(View card, HorizontalScrollView rowScroll, ImageView posterView,
                       ImageView fallbackArt, String uri) {
            this.card = card;
            this.rowScroll = rowScroll;
            this.posterView = posterView;
            this.fallbackArt = fallbackArt;
            this.uri = uri;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> posterCache = new LruCache<String, Bitmap>(POSTER_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final Map<String, Drawable> appArtCache = new HashMap<>();
    private final AtomicInteger loadGeneration = new AtomicInteger(0);
    private final List<CardPosterSlot> posterSlots = new ArrayList<>();
    private final List<ImageView> fallbackArtViews = new ArrayList<>();
    private final List<Future<?>> posterTasks = new ArrayList<>();
    private final Set<String> inFlightPosterUris = new HashSet<>();

    private volatile boolean active;
    private Future<?> activeQueryTask;

    private TextView statusView;
    private TextView refreshButton;
    private ScrollView verticalScroll;
    private LinearLayout contentRows;
    private boolean askedPermissionOnce;
    private boolean hasLoadedOnce;
    private boolean suppressFocusTracking;
    private long useSeq;
    private long focusedChannelId = -1L;
    private long focusedProgramId = -1L;
    private boolean refreshButtonHadFocus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D1117);
        root.setPadding(px(48), px(32), px(48), px(24));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, px(16));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setTextColor(0xFF8B949E);
        statusView.setPadding(0, px(4), 0, 0);
        titleBox.addView(statusView);

        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        refreshButton = buildActionButton(getString(R.string.refresh), v -> refresh());
        refreshButton.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !suppressFocusTracking && hasLoadedOnce) {
                refreshButtonHadFocus = true;
            }
        });
        header.addView(refreshButton);
        root.addView(header);

        verticalScroll = new ScrollView(this);
        verticalScroll.setVerticalScrollBarEnabled(false);
        verticalScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldX, oldY) ->
                updateVisiblePosters(loadGeneration.get()));
        contentRows = new LinearLayout(this);
        contentRows.setOrientation(LinearLayout.VERTICAL);
        verticalScroll.addView(contentRows, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(verticalScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        if (checkSelfPermission(TvPreviewRepository.PERMISSION_READ_TV_LISTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionPrompt();
            if (!askedPermissionOnce) {
                askedPermissionOnce = true;
                requestPermissions(
                        new String[]{TvPreviewRepository.PERMISSION_READ_TV_LISTINGS},
                        REQ_READ_TV_LISTINGS);
            }
            return;
        }
        refresh();
    }

    @Override
    protected void onPause() {
        if (!suppressFocusTracking && hasLoadedOnce && refreshButton != null && refreshButton.isFocused()) {
            refreshButtonHadFocus = true;
        }
        active = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        active = false;
        loadGeneration.incrementAndGet();
        cancelPendingWork();
        releaseAllPosterBitmaps();
        posterCache.evictAll();
        appArtCache.clear();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_READ_TV_LISTINGS) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (active) {
                refresh();
            }
        } else {
            statusView.setText(R.string.permission_denied_status);
            showPermissionPrompt();
        }
    }

    @Override
    protected void onDestroy() {
        active = false;
        loadGeneration.incrementAndGet();
        cancelPendingWork();
        releaseAllPosterBitmaps();
        posterCache.evictAll();
        appArtCache.clear();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void cancelPendingWork() {
        mainHandler.removeCallbacksAndMessages(null);
        if (activeQueryTask != null) {
            activeQueryTask.cancel(false);
            activeQueryTask = null;
        }
        for (Future<?> f : posterTasks) {
            f.cancel(false);
        }
        posterTasks.clear();
        inFlightPosterUris.clear();
    }

    private void releaseAllPosterBitmaps() {
        for (CardPosterSlot slot : posterSlots) {
            unbindSlot(slot);
        }
        posterSlots.clear();
        for (ImageView fallback : fallbackArtViews) {
            fallback.setImageDrawable(null);
        }
        fallbackArtViews.clear();
    }

    private void unbindSlot(CardPosterSlot slot) {
        if (slot.bound) {
            slot.posterView.setImageDrawable(null);
            slot.posterView.setVisibility(View.GONE);
            slot.fallbackArt.setVisibility(View.VISIBLE);
            slot.bound = false;
        }
    }

    private void refresh() {
        if (!active) return;
        if (checkSelfPermission(TvPreviewRepository.PERMISSION_READ_TV_LISTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionPrompt();
            return;
        }
        final int generation = loadGeneration.incrementAndGet();
        cancelPendingWork();
        statusView.setText(R.string.loading);
        activeQueryTask = executor.submit(() -> {
            if (!active || generation != loadGeneration.get()) {
                return;
            }
            LoadResult result = TvPreviewRepository.load(getApplicationContext());
            if (!active || generation != loadGeneration.get()) {
                return;
            }
            mainHandler.post(() -> {
                if (!active || isFinishing() || isDestroyed() || generation != loadGeneration.get()) {
                    return;
                }
                renderResult(result, generation);
            });
        });
    }

    private void renderResult(LoadResult result, int generation) {
        final boolean restoreRefresh = hasLoadedOnce && refreshButtonHadFocus;
        final long wantedChannel = hasLoadedOnce ? focusedChannelId : -1L;
        final long wantedProgram = hasLoadedOnce ? focusedProgramId : -1L;

        suppressFocusTracking = true;
        try {
            releaseAllPosterBitmaps();
            contentRows.removeAllViews();

            switch (result.status) {
                case PERMISSION_DENIED:
                    statusView.setText(R.string.permission_denied_status);
                    showPermissionPrompt();
                    return;
                case ERROR:
                    statusView.setText(R.string.error_status);
                    showMessageCard(getString(R.string.error_status), getString(R.string.empty_body));
                    return;
                case EMPTY:
                    statusView.setText(R.string.empty_title);
                    showMessageCard(getString(R.string.empty_title), getString(R.string.empty_body));
                    return;
                case OK:
                    break;
            }

            List<ChannelRow> displayedRows = PreviewLogic.capRows(result.rows, MAX_CARDS_PER_ROW);
            statusView.setText(getResources().getQuantityString(
                    R.plurals.summary_counts, displayedRows.size(), displayedRows.size(), result.totalItems));

            List<List<View>> cardGrid = new ArrayList<>(displayedRows.size());
            for (ChannelRow row : displayedRows) {
                cardGrid.add(addChannelRow(row, generation));
            }

            View focusTargetView = null;
            if (restoreRefresh) {
                focusTargetView = refreshButton;
                refreshButtonHadFocus = true;
            } else {
                FocusTarget target = PreviewLogic.pickFocusTarget(
                        displayedRows, wantedChannel, wantedProgram, MAX_CARDS_PER_ROW);
                if (target != null && target.rowIndex < cardGrid.size()) {
                    List<View> rowViews = cardGrid.get(target.rowIndex);
                    if (target.itemIndex < rowViews.size()) {
                        focusTargetView = rowViews.get(target.itemIndex);
                        ChannelRow chosenRow = displayedRows.get(target.rowIndex);
                        CardItem chosenItem = chosenRow.items.get(target.itemIndex);
                        focusedChannelId = chosenRow.channelId;
                        focusedProgramId = chosenItem.id;
                        refreshButtonHadFocus = false;
                    }
                }
            }

            hasLoadedOnce = true;
            if (focusTargetView != null) {
                final View target = focusTargetView;
                target.requestFocus();
                verticalScroll.post(() -> {
                    if (!active || isFinishing() || isDestroyed() || generation != loadGeneration.get()) {
                        return;
                    }
                    if (target != refreshButton && target.isAttachedToWindow()) {
                        target.requestRectangleOnScreen(
                                new Rect(0, 0, target.getWidth(), target.getHeight()), true);
                    }
                    updateVisiblePosters(generation);
                });
            } else {
                refreshButtonHadFocus = true;
                refreshButton.requestFocus();
            }
        } finally {
            suppressFocusTracking = false;
        }
    }

    private List<View> addChannelRow(ChannelRow row, int generation) {
        TextView header = new TextView(this);
        header.setText(getString(R.string.channel_header, row.headerLabel, row.items.size()));
        header.setTextSize(17);
        header.setTextColor(0xFFE6EDF3);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(px(4), px(12), px(4), px(8));
        contentRows.addView(header);

        HorizontalScrollView rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipToPadding(false);
        rowScroll.setPadding(px(4), px(4), px(24), px(12));
        rowScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldX, oldY) ->
                updateVisiblePosters(generation));

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        rowScroll.addView(strip);

        List<View> cards = new ArrayList<>(row.items.size());
        for (int i = 0; i < row.items.size(); i++) {
            CardItem item = row.items.get(i);
            View card = buildStillCard(row.channelId, item, row.appLabel, rowScroll, generation);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    px(216), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < row.items.size() - 1) {
                lp.rightMargin = px(14);
            }
            strip.addView(card, lp);
            cards.add(card);
        }
        contentRows.addView(rowScroll);
        return cards;
    }

    private View buildStillCard(long channelId, CardItem item, String appLabel,
                                HorizontalScrollView rowScroll, int generation) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_background);
        card.setFocusable(true);
        card.setFocusableInTouchMode(false);
        card.setClickable(true);
        card.setPadding(px(6), px(6), px(6), px(10));

        RelativeLayout mediaBox = new RelativeLayout(this);
        mediaBox.setBackgroundColor(0xFF10151C);

        ImageView fallbackArt = new ImageView(this);
        fallbackArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable appArt = loadAppArt(item.packageName);
        if (appArt != null) {
            fallbackArt.setImageDrawable(appArt);
        }
        int artPad = px(18);
        fallbackArt.setPadding(artPad, artPad, artPad, artPad);
        fallbackArtViews.add(fallbackArt);
        mediaBox.addView(fallbackArt, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView posterImage = new ImageView(this);
        posterImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterImage.setVisibility(View.GONE);
        mediaBox.addView(posterImage, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String badgeText = !appLabel.isEmpty() ? appLabel : item.packageName;
        if (!badgeText.isEmpty()) {
            TextView badge = new TextView(this);
            badge.setText(badgeText);
            badge.setTextSize(10);
            badge.setTextColor(0xFFC9D1D9);
            badge.setBackgroundColor(0xB30D1117);
            badge.setSingleLine(true);
            badge.setEllipsize(TextUtils.TruncateAt.END);
            badge.setPadding(px(6), px(2), px(6), px(2));
            RelativeLayout.LayoutParams badgeLp = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            badgeLp.addRule(RelativeLayout.ALIGN_PARENT_START);
            badgeLp.setMargins(px(6), 0, px(6), px(6));
            mediaBox.addView(badge, badgeLp);
        }

        card.addView(mediaBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(114)));

        if (item.poster.kind == PosterSource.Kind.LOCAL_URI) {
            posterSlots.add(new CardPosterSlot(
                    card, rowScroll, posterImage, fallbackArt, item.poster.uri));
        }

        TextView titleView = new TextView(this);
        titleView.setText(item.title);
        titleView.setTextSize(14);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPadding(px(6), px(8), px(6), 0);
        card.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(0xFF8B949E);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setPadding(px(6), px(3), px(6), 0);
        if (item.subtitle.isEmpty()) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(item.subtitle);
        }
        card.addView(subtitleView);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.04f : 1.0f;
            v.setScaleX(scale);
            v.setScaleY(scale);
            if (hasFocus) {
                if (!suppressFocusTracking) {
                    focusedChannelId = channelId;
                    focusedProgramId = item.id;
                    refreshButtonHadFocus = false;
                    v.requestRectangleOnScreen(new Rect(0, 0, v.getWidth(), v.getHeight()), false);
                }
                updateVisiblePosters(generation);
            }
        });
        card.setOnClickListener(v -> {
            focusedChannelId = channelId;
            focusedProgramId = item.id;
            refreshButtonHadFocus = false;
            launchCard(item);
        });

        return card;
    }

    private void updateVisiblePosters(int generation) {
        if (!active || isFinishing() || isDestroyed() || generation != loadGeneration.get()) {
            return;
        }
        int prefetchMargin = px(240);
        int releaseMargin = px(540);
        int vTop = verticalScroll.getScrollY();
        int vBottom = vTop + verticalScroll.getHeight();
        if (vBottom <= vTop) {
            return;
        }

        for (CardPosterSlot slot : posterSlots) {
            if (slot.bound && !isSlotInMargin(slot, vTop, vBottom, releaseMargin)) {
                unbindSlot(slot);
            }
        }

        for (CardPosterSlot slot : posterSlots) {
            if (!isSlotInMargin(slot, vTop, vBottom, prefetchMargin)) {
                continue;
            }
            Bitmap cached = posterCache.get(slot.uri);
            if (cached != null && !cached.isRecycled()) {
                bindSlot(slot, cached);
            } else if (!slot.bound && !inFlightPosterUris.contains(slot.uri)) {
                requestPosterDecode(slot.uri, generation);
            }
        }
    }

    private boolean isSlotInMargin(CardPosterSlot slot, int vTop, int vBottom, int margin) {
        int rowTop = slot.rowScroll.getTop();
        int rowBottom = slot.rowScroll.getBottom();
        if (rowBottom < vTop - margin || rowTop > vBottom + margin) {
            return false;
        }
        int hLeft = slot.rowScroll.getScrollX();
        int hRight = hLeft + slot.rowScroll.getWidth();
        int cardLeft = slot.card.getLeft();
        int cardRight = slot.card.getRight();
        return cardRight >= hLeft - margin && cardLeft <= hRight + margin;
    }

    private void bindSlot(CardPosterSlot slot, Bitmap bitmap) {
        if (!slot.bound) {
            enforceBoundPosterCap();
            slot.posterView.setImageBitmap(bitmap);
            slot.posterView.setVisibility(View.VISIBLE);
            slot.fallbackArt.setVisibility(View.GONE);
            slot.bound = true;
        }
        slot.lastUsedSeq = ++useSeq;
    }

    private void enforceBoundPosterCap() {
        int boundCount = 0;
        CardPosterSlot oldest = null;
        for (CardPosterSlot slot : posterSlots) {
            if (!slot.bound) continue;
            boundCount++;
            if (oldest == null || slot.lastUsedSeq < oldest.lastUsedSeq) {
                oldest = slot;
            }
        }
        if (boundCount >= MAX_BOUND_POSTERS && oldest != null) {
            unbindSlot(oldest);
        }
    }

    private void requestPosterDecode(String uri, int generation) {
        inFlightPosterUris.add(uri);
        final int targetW = px(204);
        final int targetH = px(114);
        Future<?> task = executor.submit(() -> {
            Bitmap result = null;
            boolean newlyDecoded = false;
            try {
                if (!active || generation != loadGeneration.get()) {
                    return;
                }
                Bitmap cached = posterCache.get(uri);
                if (cached != null && !cached.isRecycled()) {
                    result = cached;
                } else {
                    result = TvPreviewRepository.loadLocalBitmap(
                            getContentResolver(), uri, targetW, targetH);
                    newlyDecoded = result != null;
                }
            } finally {
                final Bitmap bitmapToBind = result;
                final boolean shouldCache = newlyDecoded;
                mainHandler.post(() -> {
                    if (!active || isFinishing() || isDestroyed()
                            || generation != loadGeneration.get()) {
                        if (shouldCache && bitmapToBind != null && !bitmapToBind.isRecycled()) {
                            bitmapToBind.recycle();
                        }
                        return;
                    }
                    inFlightPosterUris.remove(uri);
                    if (bitmapToBind == null || bitmapToBind.isRecycled()) {
                        return;
                    }
                    if (shouldCache) {
                        posterCache.put(uri, bitmapToBind);
                    }
                    int vTop = verticalScroll.getScrollY();
                    int vBottom = vTop + verticalScroll.getHeight();
                    int margin = px(240);
                    for (CardPosterSlot slot : posterSlots) {
                        if (slot.uri.equals(uri) && isSlotInMargin(slot, vTop, vBottom, margin)) {
                            bindSlot(slot, bitmapToBind);
                        }
                    }
                });
            }
        });
        posterTasks.add(task);
    }

    private Drawable loadAppArt(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        if (appArtCache.containsKey(packageName)) {
            return appArtCache.get(packageName);
        }
        PackageManager pm = getPackageManager();
        Drawable art = null;
        try {
            art = pm.getApplicationBanner(packageName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if (art == null) {
            try {
                art = pm.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        appArtCache.put(packageName, art);
        return art;
    }

    private void launchCard(CardItem item) {
        IntentChoice choice = item.launch;
        if (choice.kind == IntentChoice.Kind.NONE) {
            statusView.setText(R.string.cant_open);
            return;
        }
        if (choice.kind == IntentChoice.Kind.INTENT_URI || choice.kind == IntentChoice.Kind.CHANNEL_URI) {
            if (tryStartUri(choice.uri, choice.packageName)) {
                return;
            }
        }
        if (!choice.packageName.isEmpty() && tryStartPackage(choice.packageName)) {
            return;
        }
        statusView.setText(R.string.cant_open);
    }

    private boolean tryStartUri(String rawUri, String publisherPkg) {
        try {
            Intent intent = Intent.parseUri(rawUri, Intent.URI_INTENT_SCHEME);
            intent.setSelector(null);
            intent.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if (intent.getAction() == null) {
                intent.setAction(Intent.ACTION_VIEW);
            }
            ResolveInfo resolved = getPackageManager().resolveActivity(intent, 0);
            String resolvedPkg = (resolved != null && resolved.activityInfo != null)
                    ? resolved.activityInfo.packageName : "";
            if (!PreviewLogic.canLaunch(resolvedPkg, publisherPkg, getPackageName())) {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (URISyntaxException | ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private boolean tryStartPackage(String packageName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(packageName);
        }
        if (intent == null) return false;
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void showPermissionPrompt() {
        releaseAllPosterBitmaps();
        contentRows.removeAllViews();
        statusView.setText(R.string.permission_needed_title);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF161B22);
        box.setPadding(px(24), px(20), px(24), px(20));

        TextView heading = new TextView(this);
        heading.setText(R.string.permission_needed_title);
        heading.setTextSize(20);
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(heading);

        TextView body = new TextView(this);
        body.setText(R.string.permission_needed_body);
        body.setTextSize(15);
        body.setTextColor(0xFFC9D1D9);
        body.setPadding(0, px(10), 0, px(18));
        box.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView allowBtn = buildActionButton(getString(R.string.allow_permission), v ->
                requestPermissions(
                        new String[]{TvPreviewRepository.PERMISSION_READ_TV_LISTINGS},
                        REQ_READ_TV_LISTINGS));
        LinearLayout.LayoutParams allowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        allowLp.rightMargin = px(12);
        actions.addView(allowBtn, allowLp);

        TextView settingsBtn = buildActionButton(getString(R.string.open_settings), v -> openAppSettings());
        actions.addView(settingsBtn);

        box.addView(actions);
        contentRows.addView(box);
        allowBtn.requestFocus();
    }

    private void showMessageCard(String headingText, String bodyText) {
        releaseAllPosterBitmaps();
        contentRows.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF161B22);
        box.setPadding(px(24), px(20), px(24), px(20));

        TextView heading = new TextView(this);
        heading.setText(headingText);
        heading.setTextSize(20);
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(heading);

        TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextSize(15);
        body.setTextColor(0xFFC9D1D9);
        body.setPadding(0, px(10), 0, 0);
        box.addView(body);

        contentRows.addView(box);
        refreshButton.requestFocus();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            statusView.setText(R.string.cant_open);
        }
    }

    private TextView buildActionButton(String label, View.OnClickListener onClick) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundResource(R.drawable.button_background);
        btn.setPadding(px(16), px(8), px(16), px(8));
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(false);
        btn.setClickable(true);
        btn.setOnClickListener(onClick);
        return btn;
    }

    private int px(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

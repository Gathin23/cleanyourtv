package com.gathin.tvhome;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

/** A remote-navigable, full-width NEXUS feature hero backed only by bundled artwork and text. */
public final class YouTubeDiscoverHero extends FrameLayout {
    private static final long ROTATE_MS = 10_000L;
    private static final long TEXT_FADE_MS = 220L;

    private static final String[] CATEGORIES = {
            "NEXUS HOME",
            "YOUR ENTERTAINMENT",
            "SIMPLE CONTROL"
    };

    private static final String[] TITLES = {
            "Your TV. Reimagined.",
            "All your apps, one home.",
            "Everything within reach."
    };

    private static final String[] DESCRIPTIONS = {
            "A clean, cinematic space for everything on your TV.",
            "Jump straight into the apps you love.",
            "Apps, inputs, Wi-Fi and settings \u2014 ready when you need them."
    };

    private static final String NAVIGATION_HINT =
            "\u25c0  \u25b6  EXPLORE     \u2022     OK  OPEN APPS     \u2022     WORKS OFFLINE";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ImageView featuredImage;
    private final TextView categoryText;
    private final TextView titleText;
    private final TextView channelText;
    private final TextView hintText;
    private final LinearLayout details;

    private RecyclerView appGrid;
    private ValueAnimator spacerAnimator;
    private int activeIndex;
    private boolean attached;
    private AnimatorSet focusAnimator;
    private float lastHeroProgress = Float.NaN;
    private boolean lastAppSectionFocused;

    private final ViewTreeObserver.OnGlobalFocusChangeListener globalFocusListener =
            (oldFocus, newFocus) -> syncWithAppGrid(true);

    private final View.OnLayoutChangeListener gridLayoutListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    positionDetailsAboveGrid();

    private final RecyclerView.OnScrollListener gridScrollListener = new RecyclerView.OnScrollListener() {
        @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            syncWithAppGrid(false);
        }
    };

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            // Rotate only while the hero is visible and the viewer is not browsing it with the remote.
            if (getVisibility() == View.VISIBLE && getAlpha() > 0.05f && !hasFocus()) {
                moveSelection(1);
            }
            handler.postDelayed(this, ROTATE_MS);
        }
    };

    public YouTubeDiscoverHero(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setNextFocusDownId(R.id.grid);
        // Preserve the original full-bleed stage and subtle focus-pop behavior.
        setClipChildren(false);
        setClipToPadding(false);
        setClipToOutline(false);
        setBackgroundColor(Color.TRANSPARENT);
        setForeground(null);
        setOnClickListener(view -> focusFirstDockApp());

        featuredImage = new ImageView(context);
        featuredImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        featuredImage.setBackgroundColor(0xFF101318);
        featuredImage.setClipToOutline(false);
        featuredImage.setContentDescription(null);
        Bitmap artwork = decodeBundledArtwork();
        if (artwork != null) {
            featuredImage.setImageBitmap(artwork);
        }
        addView(featuredImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Keep the original bottom and side scrims for readable text over the bundled artwork.
        View bottomScrim = new View(context);
        GradientDrawable bottomGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x12000000, 0x70000000});
        bottomScrim.setBackground(bottomGradient);
        addView(bottomScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View sideScrim = new View(context);
        GradientDrawable sideGradient = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x40000000, 0x18000000, 0x00000000});
        sideScrim.setBackground(sideGradient);
        addView(sideScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.BOTTOM | Gravity.START);
        details.setPadding(dp(26), dp(12), dp(24), dp(19));
        FrameLayout.LayoutParams detailsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        addView(details, detailsParams);
        details.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        positionDetailsAboveGrid());

        categoryText = makeText(context, 11, 0xFFE6E8EC, "sans-serif-medium");
        categoryText.setLetterSpacing(0.10f);
        categoryText.setSingleLine(true);
        categoryText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(categoryText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        titleText = makeText(context, 29, Color.WHITE, "sans-serif");
        titleText.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        titleText.setMaxLines(2);
        titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleText.setShadowLayer(dp(7), 0, dp(3), 0xF5000000);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(5);
        details.addView(titleText, titleParams);

        channelText = makeText(context, 13, 0xE0FFFFFF, "sans-serif");
        channelText.setSingleLine(true);
        channelText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        channelParams.topMargin = dp(4);
        details.addView(channelText, channelParams);

        hintText = makeText(context, 10, 0xBFFFFFFF, "sans-serif-medium");
        hintText.setSingleLine(true);
        hintText.setText(NAVIGATION_HINT);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(8);
        details.addView(hintText, hintParams);

        showFeature(false);
    }

    private Bitmap decodeBundledArtwork() {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.nexus_home, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int targetWidth = Math.max(getResources().getDisplayMetrics().widthPixels, dp(1000));
        int targetHeight = Math.max(getResources().getDisplayMetrics().heightPixels / 2, dp(300));
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidth
                && bounds.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        return BitmapFactory.decodeResource(getResources(), R.drawable.nexus_home, options);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void positionDetailsAboveGrid() {
        if (appGrid == null || getHeight() <= 0) return;

        int[] heroLocation = new int[2];
        int[] gridLocation = new int[2];
        getLocationOnScreen(heroLocation);
        appGrid.getLocationOnScreen(gridLocation);

        int gridTop = gridLocation[1] - heroLocation[1];
        int detailHeight = details.getMeasuredHeight();
        if (gridTop <= 0 || detailHeight <= 0) return;

        int desiredTop = Math.max(dp(112), gridTop - detailHeight - dp(16));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) details.getLayoutParams();
        if (params.topMargin != desiredTop || params.gravity != (Gravity.TOP | Gravity.START)) {
            params.gravity = Gravity.TOP | Gravity.START;
            params.topMargin = desiredTop;
            details.setLayoutParams(params);
        }
    }

    private TextView makeText(Context context, int sizeSp, int color, String family) {
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(color);
        text.setTypeface(android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL));
        text.setIncludeFontPadding(false);
        return text;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        // Permit the subtle focus pop to extend beyond the hero's own bounds.
        if (getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) getParent();
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
        }
        updateFocusAppearance(false);
        ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) observer.addOnGlobalFocusChangeListener(globalFocusListener);
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, ROTATE_MS);
        post(() -> {
            if (!attached) return;
            RecyclerView found = getRootView().findViewById(R.id.grid);
            if (found != null && found != appGrid) {
                if (appGrid != null) {
                    appGrid.removeOnScrollListener(gridScrollListener);
                    appGrid.removeOnLayoutChangeListener(gridLayoutListener);
                }
                appGrid = found;
                appGrid.addOnScrollListener(gridScrollListener);
                appGrid.addOnLayoutChangeListener(gridLayoutListener);
            }
            syncWithAppGrid(false);
            if (appGrid != null) appGrid.post(this::positionDetailsAboveGrid);
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        if (spacerAnimator != null) {
            spacerAnimator.cancel();
            spacerAnimator = null;
        }
        attached = false;
        ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnGlobalFocusChangeListener(globalFocusListener);
        handler.removeCallbacks(rotate);
        if (appGrid != null) {
            appGrid.removeOnScrollListener(gridScrollListener);
            appGrid.removeOnLayoutChangeListener(gridLayoutListener);
            appGrid = null;
        }
        if (focusAnimator != null) focusAnimator.cancel();
        super.onDetachedFromWindow();
    }

    /** Hide the hero as soon as focus enters the app dock. */
    private void syncWithAppGrid(boolean animateFocusChange) {
        if (appGrid == null) return;
        boolean appSectionFocused = appGrid.hasFocus() && !hasFocus();
        boolean focusChanged = appSectionFocused != lastAppSectionFocused;
        boolean shouldAnimate = animateFocusChange && focusChanged;
        lastAppSectionFocused = appSectionFocused;

        if (focusChanged) {
            animateSpacer(appSectionFocused);
            details.animate().cancel();
            details.animate().alpha(appSectionFocused ? 0f : 1f)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            details.setImportantForAccessibility(appSectionFocused
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }

        float progress = appSectionFocused ? 1f : 0f;
        if (!Float.isNaN(lastHeroProgress) && Math.abs(progress - lastHeroProgress) < 0.001f) return;
        lastHeroProgress = progress;

        float targetAlpha = 1f - progress;
        float targetY = -Math.round(dp(24) * progress);
        boolean fullyHidden = progress >= 0.995f;
        if (shouldAnimate) {
            setVisibility(View.VISIBLE);
            animate().cancel();
            animate().alpha(targetAlpha).translationY(targetY)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (fullyHidden && appGrid != null && appGrid.hasFocus() && !hasFocus()) {
                            setVisibility(View.INVISIBLE);
                        }
                    }).start();
        } else {
            animate().cancel();
            setVisibility(fullyHidden ? View.INVISIBLE : View.VISIBLE);
            setAlpha(targetAlpha);
            setTranslationY(targetY);
        }
        setImportantForAccessibility(fullyHidden
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void animateSpacer(boolean appSectionFocused) {
        View spacer = getRootView().findViewById(R.id.youtubeHeroSpacer);
        if (spacer == null || spacer.getLayoutParams() == null) return;

        int targetHeight = dp(appSectionFocused ? 96 : 192);
        int startHeight = spacer.getLayoutParams().height;
        if (startHeight <= 0 || startHeight == targetHeight) return;
        if (spacerAnimator != null) spacerAnimator.cancel();

        spacerAnimator = ValueAnimator.ofInt(startHeight, targetHeight);
        spacerAnimator.setDuration(420);
        spacerAnimator.setInterpolator(new DecelerateInterpolator());
        spacerAnimator.addUpdateListener(animation -> {
            if (spacer.getParent() == null) return;
            ViewGroup.LayoutParams params = spacer.getLayoutParams();
            params.height = (Integer) animation.getAnimatedValue();
            spacer.setLayoutParams(params);
        });
        spacerAnimator.start();
    }

    private void showFeature(boolean animate) {
        activeIndex = Math.max(0, Math.min(activeIndex, CATEGORIES.length - 1));
        categoryText.setText(CATEGORIES[activeIndex]);
        titleText.setText(TITLES[activeIndex]);
        channelText.setText(DESCRIPTIONS[activeIndex]);
        hintText.setText(NAVIGATION_HINT);
        setContentDescription(CATEGORIES[activeIndex] + ". " + TITLES[activeIndex] + ". "
                + DESCRIPTIONS[activeIndex] + ". Use left and right to explore. Press OK to open apps.");
        if (animate) {
            details.animate().cancel();
            details.setAlpha(0.55f);
            details.animate().alpha(1f).setDuration(TEXT_FADE_MS)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        post(this::positionDetailsAboveGrid);
    }

    private void moveSelection(int delta) {
        if (delta == 0) return;
        activeIndex = (activeIndex + delta + CATEGORIES.length) % CATEGORIES.length;
        showFeature(true);
    }

    /** Focus the actual first focusable dock slot rather than relying on RecyclerView's geometric guess. */
    private boolean focusFirstDockApp() {
        RecyclerView grid = appGrid;
        if (grid == null) {
            View candidate = getRootView().findViewById(R.id.grid);
            if (candidate instanceof RecyclerView) {
                grid = (RecyclerView) candidate;
                appGrid = grid;
                grid.removeOnScrollListener(gridScrollListener);
                grid.addOnScrollListener(gridScrollListener);
            }
        }
        if (grid == null) return false;

        RecyclerView.Adapter<?> rawAdapter = grid.getAdapter();
        if (rawAdapter == null || rawAdapter.getItemCount() == 0) return false;
        int target = 0;
        if (rawAdapter instanceof AppAdapter) {
            AppAdapter apps = (AppAdapter) rawAdapter;
            if (apps.appCount() == 0) return false;
            target = apps.focusablePosition(0);
            target = Math.max(0, Math.min(target, apps.appCount() - 1));
        }

        final RecyclerView destinationGrid = grid;
        final int destination = target;
        destinationGrid.stopScroll();
        destinationGrid.scrollToPosition(destination);
        destinationGrid.post(() -> requestDockCardFocus(destinationGrid, destination, 0));
        return true;
    }

    private void requestDockCardFocus(RecyclerView grid, int position, int attempts) {
        if (!attached || appGrid != grid || grid.getAdapter() == null) return;
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        if (holder == null) {
            if (attempts < 8) {
                grid.postOnAnimation(() -> requestDockCardFocus(grid, position, attempts + 1));
            }
            return;
        }
        View card = holder.itemView.findViewById(R.id.card);
        if (card == null) card = holder.itemView;
        if (card.isFocusable()) card.requestFocus();
        else holder.itemView.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && focusFirstDockApp()) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveSelection(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        updateFocusAppearance(true);
        if (gainFocus) {
            setContentDescription(CATEGORIES[activeIndex] + ". " + TITLES[activeIndex]
                    + ". Use left and right to explore. Press OK to open apps.");
        }
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        updateFocusAppearance(true);
    }

    private void updateFocusAppearance(boolean animate) {
        boolean emphasized = hasFocus() || isHovered();
        float scale = emphasized ? 1.025f : 1f;
        if (focusAnimator != null) focusAnimator.cancel();

        if (animate) {
            focusAnimator = new AnimatorSet();
            focusAnimator.playTogether(
                    ObjectAnimator.ofFloat(featuredImage, "scaleX", scale),
                    ObjectAnimator.ofFloat(featuredImage, "scaleY", scale));
            focusAnimator.setDuration(emphasized ? 180 : 150);
            focusAnimator.setInterpolator(new DecelerateInterpolator());
            focusAnimator.start();
        } else {
            featuredImage.setScaleX(scale);
            featuredImage.setScaleY(scale);
        }
    }
}
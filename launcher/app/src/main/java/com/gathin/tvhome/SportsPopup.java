package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Frosted glass popup displaying prioritized sports fixtures with category filters
 * and 1-click launch to installed broadcasters (JioCinema, Hotstar, Sony LIV, JioTV+, Apple TV, YouTube).
 * Supports deterministic D-pad traversal directly returning to parent filter section on UP arrow.
 */
public final class SportsPopup {
    private final Activity activity;
    private final View anchor;
    private final float density;
    private final SportsService service;
    private PopupWindow popup;
    private LinearLayout fixturesContainer;
    private TextView statusMsg;
    private String currentFilter = "ALL";
    private List<SportsService.Fixture> allFixtures = new ArrayList<>();
    private final TextView[] filterViews = new TextView[5];

    public SportsPopup(Activity activity, View anchor, float density) {
        this.activity = activity;
        this.anchor = anchor;
        this.density = density;
        this.service = new SportsService(activity);
    }

    public void show() {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View panel = inflater.inflate(R.layout.popup_sports, null, false);
        fixturesContainer = panel.findViewById(R.id.fixturesContainer);
        statusMsg = panel.findViewById(R.id.sportsStatusMsg);

        filterViews[0] = panel.findViewById(R.id.filterAll);
        filterViews[1] = panel.findViewById(R.id.filterSoccer);
        filterViews[2] = panel.findViewById(R.id.filterTennis);
        filterViews[3] = panel.findViewById(R.id.filterF1);
        filterViews[4] = panel.findViewById(R.id.filterCricket);

        String[] filters = {"ALL", "SOCCER", "TENNIS", "F1", "CRICKET"};
        for (int i = 0; i < filterViews.length; i++) {
            final String f = filters[i];
            final View chip = filterViews[i];
            chip.setOnClickListener(v -> {
                currentFilter = f;
                updateFilterSelection();
                renderFixtures();
            });
            chip.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (fixturesContainer != null && fixturesContainer.getChildCount() > 0) {
                        fixturesContainer.getChildAt(0).requestFocus();
                        return true;
                    }
                }
                return false;
            });
        }
        updateFilterSelection();

        View btnRefresh = panel.findViewById(R.id.btnRefreshSports);
        btnRefresh.setOnClickListener(v -> load(true));

        int width = (int) (480 * density);
        popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);

        // Position under anchor
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int rootHeight = activity.getWindow().getDecorView().getHeight();
        int room = rootHeight - (loc[1] + anchor.getHeight()) - (int) (30 * density);
        popup.setHeight(Math.min((int) (460 * density), Math.max(room, (int) (200 * density))));

        panel.setAlpha(0f);
        panel.setTranslationY(-20f);
        popup.showAsDropDown(anchor, -(width - anchor.getWidth()), (int) (6 * density), Gravity.NO_GRAVITY);
        panel.animate().alpha(1f).translationY(0f).setDuration(160).start();

        load(false);
    }

    private View getActiveFilterView() {
        switch (currentFilter) {
            case "SOCCER":
                return filterViews[1] != null ? filterViews[1] : filterViews[0];
            case "TENNIS":
                return filterViews[2] != null ? filterViews[2] : filterViews[0];
            case "F1":
                return filterViews[3] != null ? filterViews[3] : filterViews[0];
            case "CRICKET":
                return filterViews[4] != null ? filterViews[4] : filterViews[0];
            default:
                return filterViews[0]; // ALL
        }
    }

    private void updateFilterSelection() {
        String[] filters = {"ALL", "SOCCER", "TENNIS", "F1", "CRICKET"};
        for (int i = 0; i < filterViews.length; i++) {
            boolean selected = filters[i].equals(currentFilter);
            filterViews[i].setSelected(selected);
            filterViews[i].setAlpha(selected ? 1.0f : 0.65f);
        }
    }

    private void load(boolean forceRefresh) {
        statusMsg.setVisibility(View.VISIBLE);
        statusMsg.setText(R.string.sports_loading);
        fixturesContainer.removeAllViews();

        service.loadFixtures(forceRefresh, new SportsService.Listener() {
            @Override
            public void onFixturesLoaded(List<SportsService.Fixture> fixtures) {
                if (popup == null || !popup.isShowing()) return;
                allFixtures = fixtures;
                statusMsg.setVisibility(View.GONE);
                renderFixtures();
            }

            @Override
            public void onError(String message) {
                if (popup == null || !popup.isShowing()) return;
                statusMsg.setVisibility(View.VISIBLE);
                statusMsg.setText(R.string.sports_error);
            }
        });
    }

    private void renderFixtures() {
        fixturesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        PackageManager pm = activity.getPackageManager();

        List<SportsService.Fixture> filtered = new ArrayList<>();
        for (SportsService.Fixture f : allFixtures) {
            if ("ALL".equals(currentFilter) || f.sport.equalsIgnoreCase(currentFilter)) {
                filtered.add(f);
            }
        }

        if (filtered.isEmpty()) {
            statusMsg.setVisibility(View.VISIBLE);
            statusMsg.setText(R.string.sports_empty);
            View active = getActiveFilterView();
            if (active != null) active.requestFocus();
            return;
        }

        statusMsg.setVisibility(View.GONE);
        for (int i = 0; i < filtered.size(); i++) {
            final int index = i;
            SportsService.Fixture f = filtered.get(i);
            View row = inflater.inflate(R.layout.item_sports_fixture, fixturesContainer, false);
            TextView compView = row.findViewById(R.id.fixtureCompetition);
            TextView statusView = row.findViewById(R.id.fixtureStatus);
            TextView titleView = row.findViewById(R.id.fixtureTitle);
            TextView appView = row.findViewById(R.id.fixtureApp);

            compView.setText(f.competition);
            titleView.setText(f.title);
            statusView.setText(f.statusText);

            if (f.isLive) {
                statusView.setTextColor(Color.parseColor("#FFFF4444")); // Red for LIVE
            }

            appView.setText(f.appLabel != null ? f.appLabel : "Watch");

            row.setOnClickListener(v -> {
                launchFixture(f, pm);
                if (popup != null && popup.isShowing()) {
                    popup.dismiss();
                }
            });

            // For the first row in the list: UP keypress always returns to the active parent filter tab!
            if (index == 0) {
                row.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        View target = getActiveFilterView();
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    }
                    return false;
                });
            }

            fixturesContainer.addView(row);
        }

        if (fixturesContainer.getChildCount() > 0) {
            fixturesContainer.getChildAt(0).requestFocus();
        }
    }

    private void launchFixture(SportsService.Fixture fixture, PackageManager pm) {
        // Try launching target broadcast app if installed
        if (fixture.targetPackage != null && !fixture.targetPackage.isEmpty()) {
            try {
                Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(fixture.targetPackage);
                if (launchIntent == null) {
                    launchIntent = pm.getLaunchIntentForPackage(fixture.targetPackage);
                }
                if (launchIntent != null) {
                    activity.startActivity(launchIntent);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // Secondary fallback: if JioCinema was targeted but not installed, try JioTV+
        if ("com.jio.media.ondemand".equals(fixture.targetPackage)) {
            try {
                Intent jioTv = pm.getLeanbackLaunchIntentForPackage("com.jio.media.jiotvplus");
                if (jioTv == null) jioTv = pm.getLaunchIntentForPackage("com.jio.media.jiotvplus");
                if (jioTv != null) {
                    activity.startActivity(jioTv);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback: Launch YouTube with search query for highlights / live stream
        try {
            Uri ytUri = Uri.parse("vnd.youtube://results?search_query=" + Uri.encode(fixture.searchQuery));
            Intent ytIntent = new Intent(Intent.ACTION_VIEW, ytUri);
            ytIntent.setPackage("com.google.android.youtube.tv");
            activity.startActivity(ytIntent);
            return;
        } catch (Exception ignored) {
        }

        // Generic web intent fallback
        try {
            Intent webYt = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(fixture.searchQuery)));
            activity.startActivity(webYt);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}

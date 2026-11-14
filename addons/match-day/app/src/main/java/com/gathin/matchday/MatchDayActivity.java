package com.gathin.matchday;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MatchDayActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(16, 19, 25);
    private static final int PANEL = Color.rgb(29, 34, 43);
    private static final int PANEL_FOCUS = Color.rgb(42, 51, 62);
    private static final int ACCENT = Color.rgb(72, 215, 160);
    private static final int TEXT = Color.rgb(246, 247, 249);
    private static final int MUTED = Color.rgb(157, 165, 177);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService coordinatorExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService apiExecutor = Executors.newFixedThreadPool(Competition.values().length);
    private final ExecutorService crestExecutor = Executors.newFixedThreadPool(3);
    private final MatchRepository repository = new MatchRepository();
    private CrestLoader crestLoader;

    private ZoneId zone;
    private LocalDate selectedDate;
    private MatchLoadState activeLoad;
    private MatchLoadState manualLoad;
    private int crestSerial;
    private int crestsPending;
    private int crestsFailed;
    private boolean settingsOpen;
    private boolean resumed;

    private LinearLayout root;
    private LinearLayout matchList;
    private ScrollView matchScroll;
    private TextView selectedDateText;
    private TextView previousDateButton;
    private TextView nextDateButton;
    private TextView todayButton;
    private TextView refreshButton;
    private final List<TextView> dateButtons = new ArrayList<>();
    private TextView footerText;
    private TextView footerDetail;
    private TextView emptyText;
    private final EnumMap<Competition, MatchRepository.Result> currentResults =
            new EnumMap<>(Competition.class);
    private String baseFooter = "Fixtures and scores · Powered by SportScore";
    private String baseFooterDetail = "";
    private boolean manualRefreshInFlight;
    private static final String PROVIDER_KEY = "data_provider";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        zone = ZoneId.systemDefault();
        selectedDate = LocalDate.now(zone);
        crestLoader = new CrestLoader(this);
        showMainScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (!settingsOpen) requestMatches(false, false);
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        coordinatorExecutor.shutdownNow();
        apiExecutor.shutdownNow();
        crestExecutor.shutdownNow();
        super.onDestroy();
    }

    private void showMainScreen() {
        settingsOpen = false;
        dateButtons.clear();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(42), dp(22), dp(42), dp(18));
        root.setBackgroundColor(BACKGROUND);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("MATCH DAY", 27, TEXT, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        DataProvider provider = selectedProvider();
        TextView sourceTag = text(provider == DataProvider.SPORT_SCORE
                ? "FOOTBALL SCORES  ·  SPORTSCORE" : "FOOTBALL SCORES  ·  DELAYED", 14, MUTED, true);
        sourceTag.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(sourceTag, new LinearLayout.LayoutParams(0, dp(52), 1));
        refreshButton = actionButton("Refresh", () -> requestMatches(true, false));
        top.addView(refreshButton, buttonParams(dp(114), dp(48)));
        TextView settings = actionButton("Settings", this::showSettingsScreen);
        LinearLayout.LayoutParams settingsParams = buttonParams(dp(126), dp(48));
        settingsParams.leftMargin = dp(10);
        top.addView(settings, settingsParams);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout dates = new LinearLayout(this);
        dates.setGravity(Gravity.CENTER_VERTICAL);
        dates.setPadding(0, dp(7), 0, dp(4));
        previousDateButton = actionButton("‹", () -> moveDate(-1));
        previousDateButton.setTextSize(28);
        dates.addView(previousDateButton, buttonParams(dp(48), dp(52)));
        for (int offset = -1; offset <= 1; offset++) {
            final int dayOffset = offset;
            TextView dayButton = actionButton("", () -> chooseDate(selectedDate.plusDays(dayOffset)));
            LinearLayout.LayoutParams dayParams = buttonParams(dp(166), dp(52));
            dayParams.leftMargin = dp(8);
            dates.addView(dayButton, dayParams);
            dateButtons.add(dayButton);
        }
        nextDateButton = actionButton("›", () -> moveDate(1));
        nextDateButton.setTextSize(28);
        LinearLayout.LayoutParams nextParams = buttonParams(dp(48), dp(52));
        nextParams.leftMargin = dp(8);
        dates.addView(nextDateButton, nextParams);
        todayButton = actionButton("Today", () -> chooseDate(LocalDate.now(zone)));
        LinearLayout.LayoutParams todayParams = buttonParams(dp(102), dp(52));
        todayParams.leftMargin = dp(14);
        dates.addView(todayButton, todayParams);
        root.addView(dates, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(67)));

        LinearLayout league = new LinearLayout(this);
        league.setGravity(Gravity.CENTER_VERTICAL);
        selectedDateText = text(dateHeading(selectedDate), 24, TEXT, true);
        league.addView(selectedDateText, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView competition = text("ALL COMPETITIONS", 12, MUTED, true);
        competition.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        league.addView(competition, new LinearLayout.LayoutParams(dp(240), dp(42)));
        root.addView(league, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));

        matchScroll = new ScrollView(this);
        matchScroll.setFillViewport(true);
        matchScroll.setClipToPadding(false);
        matchScroll.setPadding(0, dp(5), 0, dp(5));
        matchList = new LinearLayout(this);
        matchList.setOrientation(LinearLayout.VERTICAL);
        matchScroll.addView(matchList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(matchScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        footerText = text(defaultFooter(), 13, MUTED, false);
        footerText.setGravity(Gravity.CENTER_VERTICAL);
        footerDetail = text("", 12, MUTED, false);
        footerDetail.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(footerText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));
        root.addView(footerDetail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        setContentView(root);

        updateDateControls();
        if (!currentResults.isEmpty()) {
            displayResults(currentResults, false, activeLoad != null && !activeLoad.isComplete());
        }
        else showEmptyMessage("Loading saved schedules…");
    }

    private void showSettingsScreen() {
        settingsOpen = true;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(54), dp(36), dp(54), dp(28));
        root.setBackgroundColor(BACKGROUND);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = actionButton("‹  Back", this::returnToMatches);
        header.addView(back, buttonParams(dp(140), dp(52)));
        TextView title = text("Match Day settings", 28, TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        titleParams.leftMargin = dp(24);
        header.addView(title, titleParams);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        DataProvider provider = selectedProvider();
        TextView description = text(provider == DataProvider.SPORT_SCORE
                        ? "Choose a fixture source. SportScore is ready to use without an API key."
                        : "Enter your football-data.org API key to load fixtures and delayed scores for the included competitions.",
                18, MUTED, false);
        description.setGravity(Gravity.CENTER_VERTICAL);
        description.setMaxLines(2);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70));
        descParams.topMargin = dp(34);
        root.addView(description, descParams);

        LinearLayout providerRow = new LinearLayout(this);
        providerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sportScore = actionButton(provider == DataProvider.SPORT_SCORE
                ? "✓  SportScore · Recommended" : "SportScore · Recommended", () -> selectProvider(DataProvider.SPORT_SCORE));
        providerRow.addView(sportScore, buttonParams(dp(310), dp(54)));
        TextView footballData = actionButton(provider == DataProvider.FOOTBALL_DATA
                ? "✓  football-data.org" : "football-data.org", () -> selectProvider(DataProvider.FOOTBALL_DATA));
        LinearLayout.LayoutParams footballParams = buttonParams(dp(280), dp(54));
        footballParams.leftMargin = dp(14);
        providerRow.addView(footballData, footballParams);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        providerParams.topMargin = dp(10);
        root.addView(providerRow, providerParams);

        EditText keyField = null;
        if (provider == DataProvider.FOOTBALL_DATA) {
            keyField = new EditText(this);
            keyField.setSingleLine(true);
            keyField.setTextSize(20);
            keyField.setTextColor(TEXT);
            keyField.setHintTextColor(MUTED);
            keyField.setHint("API key");
            keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            keyField.setPadding(dp(18), 0, dp(18), 0);
            keyField.setBackground(background(PANEL, dp(10), Color.TRANSPARENT));
            keyField.setText(getPreferences(Context.MODE_PRIVATE).getString("api_key", ""));
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(dp(700), dp(62));
            fieldParams.topMargin = dp(10);
            root.addView(keyField, fieldParams);

            TextView privacy = text("The key stays in this app's private storage. Requests go only to football-data.org; no account details or analytics are sent.",
                    15, MUTED, false);
            privacy.setMaxLines(2);
            LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(dp(780), dp(52));
            privacyParams.topMargin = dp(14);
            root.addView(privacy, privacyParams);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (provider == DataProvider.FOOTBALL_DATA) {
            EditText savedKeyField = keyField;
            TextView save = actionButton("Save key", () -> {
                String value = savedKeyField.getText().toString().trim();
                getPreferences(Context.MODE_PRIVATE).edit().putString("api_key", value).apply();
                Toast.makeText(this, "API key saved on this TV", Toast.LENGTH_SHORT).show();
                returnToMatches();
            });
            actions.addView(save, buttonParams(dp(150), dp(54)));
            TextView clear = actionButton("Clear key", () -> {
                savedKeyField.setText("");
                getPreferences(Context.MODE_PRIVATE).edit().remove("api_key").apply();
            });
            LinearLayout.LayoutParams clearParams = buttonParams(dp(150), dp(54));
            clearParams.leftMargin = dp(12);
            actions.addView(clear, clearParams);
        } else {
            TextView done = actionButton("Done", this::returnToMatches);
            actions.addView(done, buttonParams(dp(150), dp(54)));
        }
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        actionsParams.topMargin = dp(22);
        root.addView(actions, actionsParams);

        TextView source = text("Included: Premier League, Champions League, La Liga, Serie A, Bundesliga and Ligue 1. Free scores are delayed; refresh is limited to one batch per minute.",
                14, MUTED, false);
        source.setMaxLines(2);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(dp(800), dp(48));
        sourceParams.topMargin = dp(20);
        root.addView(source, sourceParams);
        setContentView(root);
        if (keyField != null) keyField.requestFocus(); else sportScore.requestFocus();
    }

    private void selectProvider(DataProvider provider) {
        getPreferences(Context.MODE_PRIVATE).edit().putString(PROVIDER_KEY, provider.preferenceValue).apply();
        showSettingsScreen();
    }

    private DataProvider selectedProvider() {
        return DataProvider.fromPreference(getPreferences(Context.MODE_PRIVATE).getString(
                PROVIDER_KEY, DataProvider.SPORT_SCORE.preferenceValue));
    }

    private void returnToMatches() {
        showMainScreen();
        if (resumed) requestMatches(false, false);
    }

    private void chooseDate(LocalDate date) {
        if (date.equals(selectedDate)) return;
        selectedDate = date;
        currentResults.clear();
        crestSerial++;
        crestsPending = 0;
        crestsFailed = 0;
        baseFooter = defaultFooter();
        baseFooterDetail = "";
        updateDateControls();
        if (matchScroll != null) matchScroll.scrollTo(0, 0);
        showEmptyMessage("Loading schedules…");
        updateFooter();
        requestMatches(false, true);
    }

    private void moveDate(int days) {
        chooseDate(selectedDate.plusDays(days));
    }

    private void requestMatches(boolean manualRefresh, boolean resetScroll) {
        if (manualRefresh && manualRefreshInFlight) return;
        final LocalDate requestedDate = selectedDate;
        final MatchLoadState load = new MatchLoadState(requestedDate);
        activeLoad = load;
        if (resetScroll && matchScroll != null) matchScroll.scrollTo(0, 0);
        if (manualRefresh) {
            manualRefreshInFlight = true;
            manualLoad = load;
            if (refreshButton != null) refreshButton.setText("Refreshing…");
        }
        final String key = getPreferences(Context.MODE_PRIVATE).getString("api_key", "");
        final DataProvider provider = selectedProvider();
        if (currentResults.isEmpty() || matchList == null) {
            showEmptyMessage(manualRefresh ? "Refreshing league schedules…" : "Loading schedules…");
        }
        if (footerText != null && currentResults.isEmpty()) {
            footerText.setText(manualRefresh ? "Refreshing schedules…" : "Loading league schedules…");
        }
        coordinatorExecutor.execute(() -> loadSchedules(load, key, provider, manualRefresh));
    }

    private void loadSchedules(MatchLoadState load, String key, DataProvider provider, boolean manualRefresh) {
        EnumMap<Competition, MatchRepository.Result> cached = new EnumMap<>(Competition.class);
        List<Competition> fetch = new ArrayList<>();
        for (Competition competition : Competition.values()) {
            MatchRepository.Result saved = repository.readCached(this, load.date, zone, competition, provider);
            cached.put(competition, saved);
            if (manualRefresh || saved == null) {
                fetch.add(competition);
            } else {
                postResult(load, competition, saved);
            }
        }

        if (fetch.isEmpty()) return;
        if (provider == DataProvider.FOOTBALL_DATA && (key == null || key.trim().isEmpty())) {
            for (Competition competition : fetch) {
                postResult(load, competition,
                        repository.refresh(this, key, load.date, zone, competition, provider));
            }
            return;
        }
        if (!repository.tryStartNetworkBatch(this)) {
            long remaining = repository.networkCooldownRemaining(this);
            String message = "Wait " + Math.max(1L, (remaining + 999L) / 1000L)
                    + "s before the next refresh.";
            for (Competition competition : fetch) {
                MatchRepository.Result saved = cached.get(competition);
                postResult(load, competition, saved == null
                        ? MatchRepository.Result.failure(message) : saved.withError(message));
            }
            return;
        }
        for (Competition competition : fetch) {
            apiExecutor.execute(() -> postResult(load, competition,
                    repository.refresh(this, key, load.date, zone, competition, provider)));
        }
    }

    private void postResult(MatchLoadState load, Competition competition,
                            MatchRepository.Result result) {
        main.post(() -> {
            boolean visible = load == activeLoad;
            boolean accepted = load.accept(selectedDate, competition, result);
            if (load == manualLoad && load.isComplete()) {
                manualLoad = null;
                manualRefreshInFlight = false;
                if (refreshButton != null) refreshButton.setText("Refresh");
            }
            if (!accepted || !visible || isFinishing() || settingsOpen) return;
            currentResults.put(competition, result);
            updateCompetitionResult(competition, result, !load.isComplete());
        });
    }

    private void displayResults(Map<Competition, MatchRepository.Result> results,
                               boolean resetScroll, boolean pending) {
        if (matchList == null) return;
        int scrollY = resetScroll || matchScroll == null ? 0 : matchScroll.getScrollY();
        matchList.removeAllViews();
        crestSerial++;
        crestsPending = 0;
        crestsFailed = 0;
        int matchCount = 0;
        for (Competition competition : Competition.values()) {
            MatchRepository.Result result = results.get(competition);
            if (result == null || result.matches.isEmpty()) continue;
            matchCount += result.matches.size();
            LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sectionParams.bottomMargin = dp(16);
            View card = competitionCard(competition, result.matches);
            card.setTag(competition);
            matchList.addView(card, sectionParams);
        }
        if (matchCount == 0) {
            showEmptyMessage(pending ? "Loading league schedules…" : emptyMessage(results));
        } else emptyText = null;
        if (matchScroll != null) matchScroll.post(() -> matchScroll.scrollTo(0, scrollY));
        baseFooter = footerFor(results);
        baseFooterDetail = footerDetailFor(results);
        updateFooter();
    }

    private void updateCompetitionResult(Competition competition, MatchRepository.Result result,
                                         boolean pending) {
        if (matchList == null) return;
        for (int i = matchList.getChildCount() - 1; i >= 0; i--) {
            View child = matchList.getChildAt(i);
            if (child.getTag() == competition) matchList.removeViewAt(i);
        }
        if (emptyText != null) {
            matchList.removeView(emptyText);
            emptyText = null;
        }
        if (!result.matches.isEmpty()) {
            View card = competitionCard(competition, result.matches);
            card.setTag(competition);
            int index = 0;
            while (index < matchList.getChildCount()) {
                Object tag = matchList.getChildAt(index).getTag();
                if (tag instanceof Competition && ((Competition) tag).ordinal() > competition.ordinal()) break;
                index++;
            }
            LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sectionParams.bottomMargin = dp(16);
            matchList.addView(card, index, sectionParams);
        }
        int matchCount = 0;
        for (MatchRepository.Result value : currentResults.values()) matchCount += value.matches.size();
        if (matchCount == 0) {
            showEmptyMessage(pending ? "Loading league schedules…" : emptyMessage(currentResults));
        }
        baseFooter = footerFor(currentResults);
        baseFooterDetail = footerDetailFor(currentResults);
        updateFooter();
    }

    private String emptyMessage(Map<Competition, MatchRepository.Result> results) {
        return hasErrors(results)
                ? (selectedProvider() == DataProvider.FOOTBALL_DATA
                    ? "No saved fixtures are available. Check the API key or choose Refresh."
                    : "No saved fixtures are available. Choose Refresh to try SportScore again.")
                : "No matches on this day.";
    }

    private View competitionCard(Competition competition, List<Match> matches) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(background(PANEL, dp(16), Color.TRANSPARENT));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView name = text(competition.displayName, 21, TEXT, true);
        header.addView(name, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView area = text(competition.areaName, 13, MUTED, true);
        area.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(area, new LinearLayout.LayoutParams(dp(160), dp(52)));
        card.addView(header);

        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(49, 57, 66));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.leftMargin = dp(24);
                dividerParams.rightMargin = dp(24);
                card.addView(divider, dividerParams);
            }
            card.addView(matchRow(matches.get(i)), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(98)));
        }
        return card;
    }

    private View matchRow(Match match) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(8), dp(24), dp(8));

        row.addView(teamCell(match.home, false), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        boolean showScore = match.showScore();
        String primary = showScore
                ? match.homeScore + "  –  " + match.awayScore
                : (match.kickoff == null ? "TIME TBC" : kickoffTime(match.kickoff));
        TextView mainValue = text(primary, 23, TEXT, true);
        mainValue.setGravity(Gravity.CENTER);
        center.addView(mainValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        String secondary = showScore || (!"TIMED".equals(match.status) && !"SCHEDULED".equals(match.status))
                ? statusLabel(match.status) : "";
        if (!secondary.isEmpty()) {
            TextView state = text(secondary, 11,
                    ("IN_PLAY".equals(match.status) || "PAUSED".equals(match.status)) ? ACCENT : MUTED, true);
            state.setGravity(Gravity.CENTER);
            center.addView(state, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        }
        row.addView(center, new LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(teamCell(match.away, true), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return row;
    }

    private View teamCell(Match.Team team, boolean crestFirst) {
        LinearLayout cell = new LinearLayout(this);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout crestFrame = new FrameLayout(this);
        crestFrame.setBackground(background(Color.rgb(43, 49, 59), dp(22), Color.TRANSPARENT));
        ImageView crest = new ImageView(this);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER);
        crestFrame.addView(crest, imageParams);

        TextView name = text(team.displayName(), 19, TEXT, false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL | (crestFirst ? Gravity.LEFT : Gravity.RIGHT));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (crestFirst) {
            cell.addView(crestFrame, new LinearLayout.LayoutParams(dp(46), dp(46)));
            nameParams.leftMargin = dp(14);
            cell.addView(name, nameParams);
        } else {
            nameParams.rightMargin = dp(14);
            cell.addView(name, nameParams);
            cell.addView(crestFrame, new LinearLayout.LayoutParams(dp(46), dp(46)));
        }
        requestCrest(team, crest, crestFrame);
        return cell;
    }

    private void requestCrest(Match.Team team, ImageView target, View placeholder) {
        final int generation = crestSerial;
        crestsPending++;
        if (team.id < 0 || team.crestUrl == null || team.crestUrl.isEmpty()) {
            crestsPending--;
            crestsFailed++;
            updateFooter();
            return;
        }
        crestExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = crestLoader.load(team);
            } catch (Exception ignored) { }
            final Bitmap loaded = bitmap;
            main.post(() -> {
                if (generation != crestSerial) return;
                crestsPending--;
                if (loaded == null) {
                    crestsFailed++;
                } else {
                    target.setImageDrawable(new BitmapDrawable(getResources(), loaded));
                    target.setVisibility(View.VISIBLE);
                    placeholder.setBackground(background(Color.TRANSPARENT, dp(18), Color.TRANSPARENT));
                }
                updateFooter();
            });
        });
        updateFooter();
    }

    private void showEmptyMessage(String message) {
        if (matchList == null) return;
        matchList.removeAllViews();
        emptyText = text(message, 20, MUTED, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(30), dp(20), dp(30), dp(20));
        matchList.addView(emptyText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void updateFooter() {
        if (footerText == null || footerDetail == null) return;
        footerText.setText(baseFooter);
        boolean sportScore = selectedProvider() == DataProvider.SPORT_SCORE;
        footerText.setClickable(sportScore);
        footerText.setFocusable(sportScore);
        footerText.setOnClickListener(sportScore ? view -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://sportscore.com/"))) : null);
        if (crestsPending > 0) {
            footerDetail.setText("Loading team crests · " + crestsPending + " remaining");
        } else if (crestsFailed > 0) {
            footerDetail.setText("" + crestsFailed + " team crest(s) could not load · select Refresh to retry");
        } else {
            footerDetail.setText(baseFooterDetail);
        }
    }

    private boolean hasErrors(Map<Competition, MatchRepository.Result> results) {
        for (MatchRepository.Result result : results.values()) {
            if (result.error != null) return true;
        }
        return false;
    }

    private String footerFor(Map<Competition, MatchRepository.Result> results) {
        int failures = 0;
        String firstError = null;
        boolean savedScheduleAvailable = false;
        boolean errorsMatch = true;
        for (MatchRepository.Result result : results.values()) {
            if (result.updatedAt > 0) savedScheduleAvailable = true;
            if (result.error == null) continue;
            failures++;
            if (firstError == null) firstError = result.error;
            else if (!firstError.equals(result.error)) errorsMatch = false;
        }
        if (failures == 0) return defaultFooter();
        if (failures == 1) return "Schedule update failed · " + firstError;
        if (errorsMatch) return firstError + (savedScheduleAvailable ? " · saved schedules kept" : "");
        if (savedScheduleAvailable) return "Some league schedules could not update · saved results were kept";
        return firstError == null ? "League schedules could not load" : firstError;
    }

    private String footerDetailFor(Map<Competition, MatchRepository.Result> results) {
        long latestUpdate = 0;
        boolean allSaved = true;
        for (MatchRepository.Result result : results.values()) {
            latestUpdate = Math.max(latestUpdate, result.updatedAt);
            if (result.updatedAt > 0 && !result.fromCache) allSaved = false;
        }
        if (latestUpdate == 0) return "";
        return (allSaved ? "Saved schedules · " : "Schedules updated · ") + updateTime(latestUpdate);
    }

    private String defaultFooter() {
        return selectedProvider() == DataProvider.SPORT_SCORE
                ? "Fixtures and scores · Powered by SportScore"
                : "Scores may be delayed · football-data.org";
    }

    private void updateDateControls() {
        if (selectedDateText != null) selectedDateText.setText(dateHeading(selectedDate));
        for (int i = 0; i < dateButtons.size(); i++) {
            int offset = i - 1;
            TextView button = dateButtons.get(i);
            boolean selected = offset == 0;
            LocalDate day = selectedDate.plusDays(offset);
            button.setText(day.format(DateTimeFormatter.ofPattern("EEE  ·  d MMM", Locale.getDefault())));
            button.setTag(selected);
            button.setTextColor(selected ? ACCENT : TEXT);
            setButtonSurface(button, selected, button.hasFocus());
        }
        if (todayButton != null) {
            boolean selected = selectedDate.equals(LocalDate.now(zone));
            todayButton.setTag(selected);
            todayButton.setTextColor(selected ? ACCENT : TEXT);
            setButtonSurface(todayButton, selected, todayButton.hasFocus());
        }
    }

    private void setButtonSurface(TextView button, boolean selected, boolean focused) {
        int fill = selected ? Color.rgb(27, 55, 47) : (focused ? PANEL_FOCUS : PANEL);
        button.setBackground(background(fill, dp(9), focused ? ACCENT : (selected ? ACCENT : Color.TRANSPARENT)));
    }

    private TextView actionButton(String label, Runnable click) {
        TextView button = text(label, 15, TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setClickable(true);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setTag(Boolean.FALSE);
        button.setBackground(background(PANEL, dp(9), Color.TRANSPARENT));
        button.setOnFocusChangeListener((view, focused) -> {
            view.setScaleX(focused ? 1.04f : 1f);
            view.setScaleY(focused ? 1.04f : 1f);
            setButtonSurface(button, Boolean.TRUE.equals(button.getTag()), focused);
        });
        button.setOnClickListener(view -> click.run());
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private GradientDrawable background(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(2), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams buttonParams(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String dateHeading(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()));
    }

    private String kickoffTime(Instant instant) {
        return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(zone).format(instant);
    }

    private String updateTime(long milliseconds) {
        return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(zone)
                .format(Instant.ofEpochMilli(milliseconds));
    }

    private String statusLabel(String status) {
        switch (status) {
            case "IN_PLAY": return "DELAYED SCORE";
            case "PAUSED": return "HALF TIME · DELAYED";
            case "FINISHED": return "FULL TIME";
            case "POSTPONED": return "POSTPONED";
            case "CANCELLED": return "CANCELLED";
            case "SUSPENDED": return "SUSPENDED";
            case "AWARDED": return "AWARDED";
            case "TIMED":
            case "SCHEDULED": return "KICK-OFF";
            default: return "SCHEDULE TBC";
        }
    }
}

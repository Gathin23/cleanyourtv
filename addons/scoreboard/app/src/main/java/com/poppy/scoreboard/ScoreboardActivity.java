package com.poppy.scoreboard;

import android.app.Activity;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreboardActivity extends Activity {

    static class AppScore {
        String packageName;
        String label;
        Drawable icon;
        int launchCount;
        long totalTimeMs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scoreboard);

        renderScoreboard();
    }

    private boolean isStreamingApp(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();

        // System & Utility Blocklist
        if (p.contains("setting") ||
            p.contains("installer") ||
            p.contains("systemui") ||
            p.contains("android.vending") ||
            p.contains("google.android.gms") ||
            p.contains("google.android.katniss") ||
            p.contains("google.android.tv.assistant") ||
            p.contains("remote.service") ||
            p.contains("dreamx") ||
            p.contains("mediashell") ||
            p.contains("launcher") ||
            p.contains("setupwraith") ||
            p.contains("tvhome") ||
            p.contains("scoreboard") ||
            p.contains("headphones") ||
            p.contains("weather") ||
            p.contains("palantir") ||
            p.contains("wireguard") ||
            p.contains("tcl.suspension") ||
            p.contains("tcl.bi") ||
            p.contains("tcl.guard") ||
            p.contains("tcl.usercenter") ||
            p.contains("tcl.messagebox") ||
            p.contains("tcl.partnercustomizer") ||
            p.contains("tvinput") ||
            p.contains("tivo.atom") ||
            p.contains("tivo.sageservice") ||
            p.contains("tivo.tvlaunchercustomization") ||
            p.contains("nes.") ||
            p.contains("sei.") ||
            p.contains("droidlogic") ||
            p.contains("talkback") ||
            p.contains("providers") ||
            p.startsWith("android")) {
            return false;
        }

        // Streaming & Media Whitelist
        return p.contains("netflix") ||
               p.contains("amazonvideo") ||
               p.contains("prime") ||
               p.contains("avod") ||
               p.contains("disney") ||
               p.contains("youtube") ||
               p.contains("plex") ||
               p.contains("peacock") ||
               p.contains("hulu") ||
               p.contains("wbd") ||
               p.contains("max") ||
               p.contains("hbo") ||
               p.contains("cbs") ||
               p.contains("paramount") ||
               p.contains("spotify") ||
               p.contains("twitch") ||
               p.contains("tubi") ||
               p.contains("pluto") ||
               p.contains("vudu") ||
               p.contains("apple.atve") ||
               p.contains("appletv") ||
               p.contains("airscreen") ||
               p.contains("espn") ||
               p.contains("crunchyroll") ||
               p.contains("starz") ||
               p.contains("discovery") ||
               p.contains("kodi") ||
               p.contains("vlc") ||
               p.contains("pandora") ||
               p.contains("deezer") ||
               p.contains("iheart") ||
               p.contains("tclchannel") ||
               p.contains("tivoplusplayer") ||
               p.equals("com.google.android.videos");
    }

    private void renderScoreboard() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = getPackageManager();

        Calendar cal = Calendar.getInstance();
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.MONTH, -6);
        long startTime = cal.getTimeInMillis();

        Map<String, Integer> eventCounts = new HashMap<>();

        if (usm != null) {
            UsageEvents events = usm.queryEvents(startTime, endTime);
            if (events != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    int eventType = event.getEventType();
                    if (eventType == UsageEvents.Event.ACTIVITY_RESUMED || eventType == 1) { // 1 = MOVE_TO_FOREGROUND / RESUMED
                        String pkg = event.getPackageName();
                        if (isStreamingApp(pkg)) {
                            eventCounts.put(pkg, eventCounts.getOrDefault(pkg, 0) + 1);
                        }
                    }
                }
            }
        }

        Map<String, AppScore> scoreMap = new HashMap<>();

        // Also query time in foreground
        List<UsageStats> statsList = usm != null ? usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime) : null;
        if (statsList != null) {
            for (UsageStats us : statsList) {
                String pkg = us.getPackageName();
                if (!isStreamingApp(pkg)) continue;

                int count = eventCounts.getOrDefault(pkg, 0);
                if (count <= 0 && us.getTotalTimeInForeground() <= 1000) continue;

                AppScore existing = scoreMap.get(pkg);
                if (existing == null) {
                    existing = new AppScore();
                    existing.packageName = pkg;
                    existing.launchCount = count;
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                        existing.label = String.valueOf(pm.getApplicationLabel(ai));
                        existing.icon = pm.getApplicationIcon(ai);
                    } catch (Exception e) {
                        existing.label = getFriendlyName(pkg);
                    }
                    scoreMap.put(pkg, existing);
                }
                existing.totalTimeMs += us.getTotalTimeInForeground();
            }
        }

        // Add any remaining from eventCounts
        for (Map.Entry<String, Integer> entry : eventCounts.entrySet()) {
            String pkg = entry.getKey();
            if (!scoreMap.containsKey(pkg)) {
                AppScore s = new AppScore();
                s.packageName = pkg;
                s.launchCount = entry.getValue();
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    s.label = String.valueOf(pm.getApplicationLabel(ai));
                    s.icon = pm.getApplicationIcon(ai);
                } catch (Exception e) {
                    s.label = getFriendlyName(pkg);
                }
                scoreMap.put(pkg, s);
            }
        }

        List<AppScore> scores = new ArrayList<>(scoreMap.values());
        Collections.sort(scores, (a, b) -> {
            if (b.launchCount != a.launchCount) {
                return Integer.compare(b.launchCount, a.launchCount);
            }
            return Long.compare(b.totalTimeMs, a.totalTimeMs);
        });

        LinearLayout container = findViewById(R.id.scoreboard_list);
        container.removeAllViews();

        int totalLaunches = 0;
        for (AppScore s : scores) {
            totalLaunches += s.launchCount;
        }

        TextView totalLabel = findViewById(R.id.total_launches_label);
        if (totalLabel != null) {
            totalLabel.setText("Streaming: " + totalLaunches + " total plays tracked");
        }

        int rank = 1;
        View firstRow = null;

        for (AppScore app : scores) {
            if (app.launchCount <= 0 && app.totalTimeMs <= 0) continue;

            final String targetPkg = app.packageName;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setClickable(true);
            row.setBackgroundResource(R.drawable.item_focus_bg);
            
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(64));
            rowLp.setMargins(0, 0, 0, dpToPx(10));
            row.setLayoutParams(rowLp);
            row.setPadding(dpToPx(16), 0, dpToPx(20), 0);

            // Rank Badge
            TextView rankView = new TextView(this);
            String medal = rank == 1 ? "🥇 #1" : rank == 2 ? "🥈 #2" : rank == 3 ? "🥉 #3" : "   #" + rank;
            rankView.setText(medal);
            rankView.setTextColor(rank == 1 ? Color.parseColor("#E5A93C") : rank == 2 ? Color.parseColor("#CBD5E1") : rank == 3 ? Color.parseColor("#CD7F32") : Color.parseColor("#94A3B8"));
            rankView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            rankView.setTypeface(null, android.graphics.Typeface.BOLD);
            rankView.setWidth(dpToPx(72));
            row.addView(rankView);

            // Icon
            ImageView iconView = new ImageView(this);
            if (app.icon != null) {
                iconView.setImageDrawable(app.icon);
            }
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
            iconLp.setMargins(dpToPx(8), 0, dpToPx(16), 0);
            iconView.setLayoutParams(iconLp);
            row.addView(iconView);

            // App Label
            TextView labelView = new TextView(this);
            String dispLabel = app.label != null && !app.label.isEmpty() ? app.label : getFriendlyName(app.packageName);
            labelView.setText(dispLabel);
            labelView.setTextColor(Color.WHITE);
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            labelView.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelView.setLayoutParams(labelLp);
            row.addView(labelView);

            // Launch Count Score Pill
            TextView countView = new TextView(this);
            countView.setText(app.launchCount + " plays");
            countView.setTextColor(Color.parseColor("#38BDF8"));
            countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            countView.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(countView);

            // Click & Key Listener to launch the app
            row.setOnClickListener(v -> launchTargetApp(targetPkg));

            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_UP && (
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                    launchTargetApp(targetPkg);
                    return true;
                }
                return false;
            });

            container.addView(row);

            if (firstRow == null) {
                firstRow = row;
            }
            rank++;
        }

        if (firstRow != null) {
            firstRow.requestFocus();
        }
    }

    private void launchTargetApp(String targetPkg) {
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(targetPkg);
            if (intent == null) {
                intent = pm.getLaunchIntentForPackage(targetPkg);
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getFriendlyName(String pkg) {
        if (pkg == null) return "";
        String p = pkg.toLowerCase();
        if (p.contains("netflix")) return "Netflix";
        if (p.contains("amazonvideo") || p.contains("prime")) return "Prime Video";
        if (p.contains("disney")) return "Disney+";
        if (p.contains("youtube.tvunplugged")) return "YouTube TV";
        if (p.contains("youtube.tvmusic")) return "YouTube Music";
        if (p.contains("youtube")) return "YouTube";
        if (p.contains("plex")) return "Plex";
        if (p.contains("peacock")) return "Peacock";
        if (p.contains("hulu")) return "Hulu";
        if (p.contains("wbd") || p.contains("max") || p.contains("hbo")) return "Max";
        if (p.contains("airscreen")) return "AirScreen";
        if (p.contains("cbs") || p.contains("paramount")) return "Paramount+";
        if (p.contains("spotify")) return "Spotify";
        if (p.contains("espn")) return "ESPN";
        if (p.contains("twitch")) return "Twitch";
        if (p.contains("tubi")) return "Tubi";
        if (p.contains("pluto")) return "Pluto TV";
        if (p.contains("vudu")) return "Fandango at Home (Vudu)";
        if (p.contains("tivoplusplayer")) return "TiVo+";
        if (p.contains("apple")) return "Apple TV";
        if (p.contains("videos")) return "Google TV";
        return pkg;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
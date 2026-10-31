package com.gathin.geogenius;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistent local storage for High Scores, Stars, Skill Tier (1=Novice, 2=Expert),
 * Multi-Select Region Filter Mask, and Sound/Voice preferences. Zero network permissions.
 */
final class GeoStore {

    private static final String PREFS = "geogenius_prefs_v2";

    static final int MODE_FLAG_FINDER = 0;   // Flag -> Country
    static final int MODE_FLAG_MATCHER = 1;  // Country -> Flag
    static final int MODE_WORD_BUILDER = 2;  // Spell It
    static final int MODE_CAPITALS = 3;      // Capitals Trainer
    static final int MODE_WONDERS = 4;       // Landmark Explorer
    static final int MODE_SNAP_MATCH = 5;    // Left-to-Right Snap Match
    static final int MODE_ODD_ONE_OUT = 6;   // Odd Attribute Out & Spot the Lie
    static final int MODE_ATLAS = 7;         // World Atlas Explorer & Topic Quiz
    static final int MODE_DAILY = 8;         // Daily 10-Q Challenge

    static int getTier(Context ctx) {
        int t = prefs(ctx).getInt("skill_tier", 1);
        return (t == 2) ? 2 : 1; // 1 = Novice, 2 = Expert
    }

    static void setTier(Context ctx, int tier) {
        prefs(ctx).edit().putInt("skill_tier", tier == 2 ? 2 : 1).apply();
    }

    static int getRegionMask(Context ctx) {
        int m = prefs(ctx).getInt("region_mask", GeoData.REGION_ALL_MASK);
        return (m & GeoData.REGION_ALL_MASK) == 0 ? GeoData.REGION_ALL_MASK : (m & GeoData.REGION_ALL_MASK);
    }

    static void setRegionMask(Context ctx, int mask) {
        int clean = (mask & GeoData.REGION_ALL_MASK) == 0 ? GeoData.REGION_ALL_MASK : (mask & GeoData.REGION_ALL_MASK);
        prefs(ctx).edit().putInt("region_mask", clean).apply();
    }

    static boolean isSoundEnabled(Context ctx) {
        return prefs(ctx).getBoolean("sound_enabled", true);
    }

    static void setSoundEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("sound_enabled", enabled).apply();
    }

    static int getBestScore(Context ctx, int mode, int tier) {
        return prefs(ctx).getInt("best_" + mode + "_" + tier, 0);
    }

    static boolean recordScore(Context ctx, int mode, int tier, int score, int maxScore) {
        SharedPreferences sp = prefs(ctx);
        String key = "best_" + mode + "_" + tier;
        int prevBest = sp.getInt(key, 0);
        boolean isNewBest = score > prevBest && score > 0;
        if (isNewBest) {
            sp.edit().putInt(key, score).apply();
        }
        return isNewBest;
    }

    static int getStarsForMode(Context ctx, int mode, int tier) {
        int best = getBestScore(ctx, mode, tier);
        int max = (mode == MODE_WORD_BUILDER) ? 200 : (mode == MODE_SNAP_MATCH ? 120 : 100);
        if (best <= 0) return 0;
        int pct = (best * 100) / max;
        if (pct >= 90) return 3;
        if (pct >= 60) return 2;
        return 1;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

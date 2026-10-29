package io.github.maxhayim.tvhub.data

import android.content.Context

/** Plain settings; nothing secret lives here (source settings are in the encrypted sources table). */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    var mergeDuplicates: Boolean
        get() = p.getBoolean("merge", true)
        set(v) = p.edit().putBoolean("merge", v).apply()

    /** Days of guide kept after a refresh. */
    var guideDays: Int
        get() = p.getInt("guide_days", 2)
        set(v) = p.edit().putInt("guide_days", v.coerceIn(1, 7)).apply()

    /** Refresh when the app opens and the last refresh is older than this; 0 never refreshes on its own. */
    var refreshHours: Int
        get() = p.getInt("refresh_hours", 12)
        set(v) = p.edit().putInt("refresh_hours", v).apply()

    var animations: Boolean
        get() = p.getBoolean("animations", true)
        set(v) = p.edit().putBoolean("animations", v).apply()

    var lastRefresh: Long
        get() = p.getLong("last_refresh", 0)
        set(v) = p.edit().putLong("last_refresh", v).apply()
}

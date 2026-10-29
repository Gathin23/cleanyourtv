package io.github.maxhayim.tvhub.source

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.maxhayim.tvhub.model.AppShortcut

/**
 * Installed live-TV apps, offered as shortcuts that open the app. None of these publishes its
 * channel list or guide to other apps, so the hub never shows their channels as its own.
 * Package visibility: the known packages are declared in the manifest's <queries>, and the
 * "other apps" list uses the LEANBACK_LAUNCHER intent query; no QUERY_ALL_PACKAGES.
 */
object Apps {
    /** Package to display name. Order is the order shown. Keep in sync with <queries> in the manifest. */
    val KNOWN = linkedMapOf(
        "tv.pluto.android" to "Pluto TV",
        "com.tubitv" to "Tubi",
        "com.plexapp.android" to "Plex",
        "org.xbmc.kodi" to "Kodi",
        "com.getchannels.dvr.app" to "Channels",
        "com.silicondust.view" to "HDHomeRun",
        "com.google.android.youtube.tvunplugged" to "YouTube TV",
        "com.sling" to "Sling TV",
        "com.philo.philo.google" to "Philo",
        "com.haystack.android" to "Haystack News",
        "com.google.android.tv" to "Live Channels",
        "ar.tvplayer.tv" to "TiviMate",
    )

    fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun isInstalled(context: Context, pkg: String) = launchIntent(context, pkg) != null

    fun label(context: Context, pkg: String): String = KNOWN[pkg] ?: try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { pkg }

    /** Known live-TV apps that are installed. */
    fun detected(context: Context): List<AppShortcut> = KNOWN.keys.filter { isInstalled(context, it) }.map { AppShortcut(it, label(context, it)) }

    /** Every app with a TV launcher entry, for picking an app the known list does not cover. */
    fun allTvApps(context: Context): List<AppShortcut> {
        val pm = context.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }
            .filter { it != context.packageName }.distinct()
            .map { AppShortcut(it, label(context, it)) }.sortedBy { it.label.lowercase() }
    }
}

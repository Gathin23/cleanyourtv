package io.github.maxhayim.tvhub.ui

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.maxhayim.tvhub.BuildConfig
import io.github.maxhayim.tvhub.ui.Ui.dp

class SettingsScreen(host: MainActivity) : Screen(host) {
    private val list = Ui.vertical(ctx, 8)

    override val view: View = ScrollView(ctx).apply {
        clipChildren = false; clipToPadding = false
        isVerticalScrollBarEnabled = false
        val col = Ui.vertical(ctx, 18)
        col.addView(Ui.text(ctx, "Settings", 30f, bold = true))
        col.addView(list)
        addView(col, Ui.lp())
        build()
    }

    private fun build() {
        val p = repo.prefs
        list.removeAllViews()
        row("Merge duplicate channels", if (p.mergeDuplicates) "On: channels with the same tvg-id in several sources show once, and the player falls back between their streams" else "Off: every source keeps its own copy") {
            repo.setMerge(!p.mergeDuplicates); build()
        }
        row("Guide length", "${p.guideDays} day${if (p.guideDays == 1) "" else "s"} of listings. Longer guides use more storage and take longer to read") {
            p.guideDays = when (p.guideDays) { 1 -> 2; 2 -> 3; 3 -> 7; else -> 1 }; build()
            Toast.makeText(ctx, "Takes effect at the next refresh", Toast.LENGTH_SHORT).show()
        }
        row("Refresh when the app opens", if (p.refreshHours == 0) "Never on its own; use Refresh" else "If the last refresh is older than ${p.refreshHours} hours. Nothing runs in the background") {
            p.refreshHours = when (p.refreshHours) { 6 -> 12; 12 -> 24; 24 -> 0; else -> 6 }; build()
        }
        row("Animations", if (p.animations) "On" else "Off: no zoom on focus and no scrolling motion, for slow TVs") {
            p.animations = !p.animations; build()
        }
        row("Clear recently watched", "Empties the Recently watched row") {
            repo.clearRecents(); Toast.makeText(ctx, "Cleared", Toast.LENGTH_SHORT).show()
        }
        row("Run setup again", "The first-launch steps for adding sources") {
            ctx.startActivity(Intent(ctx, OnboardingActivity::class.java))
        }
        row("About and licences", "TV Hub ${BuildConfig.VERSION_NAME}, MIT licence") { about() }
    }

    private fun row(title: String, sub: String, onClick: () -> Unit) {
        val r = Ui.vertical(ctx, 4).apply {
            isFocusable = true; isClickable = true
            background = Ui.focusable(ctx)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener {
                val idx = list.indexOfChild(this)
                onClick()
                list.getChildAt(idx)?.requestFocus()
            }
        }
        r.addView(Ui.text(ctx, title, 20f, bold = true))
        r.addView(Ui.text(ctx, sub, 15f, Ui.DIM, lines = 2))
        list.addView(r)
    }

    private fun about() {
        val text = ctx.assets.open("licenses.txt").bufferedReader().use { it.readText() }
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("TV Hub ${BuildConfig.VERSION_NAME}")
            .setView(ScrollView(ctx).apply {
                isFocusable = true
                addView(TextView(ctx).apply { this.text = text; setTextColor(Ui.TEXT); textSize = 14f; setPadding(dp(28), dp(8), dp(28), dp(8)) })
            })
            .setPositiveButton("Close", null).show()
    }

    override fun focusFirst(): Boolean = list.getChildAt(0)?.requestFocus() ?: false
}

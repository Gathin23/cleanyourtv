package io.github.maxhayim.tvhub.ui

import android.app.AlertDialog
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.model.Programme
import io.github.maxhayim.tvhub.ui.Ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The programme grid with a row of categories above it. */
class GuideScreen(host: MainActivity) : Screen(host) {
    private val grid = GuideView(ctx)
    private val chips = Ui.horizontal(ctx, 10)
    private val empty = Ui.text(ctx, "", 18f, Ui.DIM, lines = 4)
    private var channels: List<Channel> = emptyList()
    private var favs: Set<String> = emptySet()
    private var category = "All"
    private var shown: List<Channel> = emptyList()

    override val view: View = Ui.vertical(ctx, 14).apply {
        addView(Ui.text(ctx, "Guide", 30f, bold = true))
        addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(dp(4), dp(4), dp(4), dp(4)); addView(chips) })
        val frame = FrameLayout(ctx)
        frame.addView(grid, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        frame.addView(empty, FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        addView(frame, Ui.lp(Ui.MATCH, 0, 1f))
    }

    init {
        grid.onNeed = { first, last ->
            val slice = shown.subList(first, last + 1).toList()
            val (from, to) = grid.timeRange
            host.scope.launch {
                val data = withContext(Dispatchers.IO) { repo.programmes(slice, from, to) }
                if (slice.firstOrNull()?.key == shown.getOrNull(first)?.key) grid.putProgrammes(first, last, data)
            }
        }
        grid.onSelect = { ch, p ->
            if (p == null || p.isOn(System.currentTimeMillis())) play(ch) else details(ch, p)
        }
        grid.onInfo = { ch, p -> details(ch, p) }
        grid.onLong = { ch ->
            val on = repo.toggleFavorite(ch.key)
            Toast.makeText(ctx, if (on) "Added ${ch.name} to Favorites" else "Removed ${ch.name} from Favorites", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onShow() {
        watch(combine(repo.channels, repo.favorites) { c, f -> c to f }) { (c, f) ->
            val changed = c !== channels || (category == "Favorites" && f != favs)
            channels = c; favs = f; grid.favorites = f
            if (changed) { buildChips(); applyCategory() }
        }
        watch(repo.guideVersion) { applyCategory() }
        // Keeps the now line moving and the grid starting at the current half hour.
        launch { while (true) { delay(60_000); grid.invalidate() } }
    }

    private fun buildChips() {
        chips.removeAllViews()
        val names = listOf("All", "Favorites") + channels.mapNotNull { it.group }.distinct()
        if (category !in names) category = "All"
        names.forEach { n ->
            chips.addView(Ui.text(ctx, n, 16f, if (n == category) Ui.TEXT else Ui.DIM, bold = true).apply {
                isFocusable = true; isClickable = true; isSelected = n == category
                background = Ui.focusable(ctx, Ui.SURFACE, 20, Ui.SURFACE_2)
                setPadding(dp(18), dp(8), dp(18), dp(8))
                setOnClickListener { select(n); grid.requestFocus() }
                setOnFocusChangeListener { _, has -> if (has) select(n) }
                nextFocusDownId = View.NO_ID
            })
        }
    }

    private fun select(n: String) {
        if (n == category) return
        category = n
        for (i in 0 until chips.childCount) (chips.getChildAt(i) as TextView).let {
            val on = it.text == n
            it.isSelected = on; it.setTextColor(if (on) Ui.TEXT else Ui.DIM)
        }
        applyCategory()
    }

    private fun applyCategory() {
        shown = when (category) {
            "All" -> channels
            "Favorites" -> channels.filter { it.key in favs }
            else -> channels.filter { it.group == category }
        }
        grid.setData(shown, repo.prefs.guideDays)
        empty.text = when {
            shown.isNotEmpty() -> ""
            channels.isEmpty() -> "No channels yet. Add a playlist, Dispatcharr or a tuner in Sources, and an XMLTV guide for programme listings."
            category == "Favorites" -> "No favourites yet. Hold OK on a channel to add it."
            else -> "No channels in $category."
        }
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (shown.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    private fun play(ch: Channel) {
        host.play(shown, shown.indexOfFirst { it.key == ch.key }.coerceAtLeast(0))
    }

    private fun details(ch: Channel, p: Programme?) {
        host.scope.launch {
            val desc = p?.let { withContext(Dispatchers.IO) { repo.description(it) } }
            val body = Ui.vertical(ctx, 10).apply { setPadding(dp(28), dp(12), dp(28), dp(8)) }
            body.addView(Ui.text(ctx, listOfNotNull(ch.number, ch.name).joinToString("  "), 16f, Ui.DIM))
            if (p != null) {
                body.addView(Ui.text(ctx, "${Ui.dayTime(ctx, p.start)} – ${Ui.time(ctx, p.stop)}", 16f, Ui.DIM))
                p.subtitle?.let { body.addView(Ui.text(ctx, it, 18f, bold = true, lines = 2)) }
                p.category?.let { body.addView(Ui.text(ctx, it, 14f, Ui.ACCENT)) }
                body.addView(Ui.text(ctx, desc ?: "No description in the guide.", 17f, lines = 0))
            } else {
                body.addView(Ui.text(ctx, if (ch.epgIds.isEmpty()) "This channel has no guide information." else "Nothing is listed for this time.", 17f, lines = 0))
            }
            body.addView(Ui.text(ctx, "From " + ch.sourceNames.joinToString(", "), 14f, Ui.DIM))
            AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(p?.title?.ifEmpty { null } ?: ch.name)
                .setView(ScrollView(ctx).apply { addView(body) })
                .setPositiveButton("Watch now") { _, _ -> play(ch) }
                .setNeutralButton(if (ch.key in favs) "Remove from Favorites" else "Add to Favorites") { _, _ -> repo.toggleFavorite(ch.key) }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    override fun focusFirst(): Boolean = if (shown.isNotEmpty()) grid.requestFocus() else chips.getChildAt(0)?.requestFocus() ?: false
}

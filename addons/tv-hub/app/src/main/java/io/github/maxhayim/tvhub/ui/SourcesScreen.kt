package io.github.maxhayim.tvhub.ui

import android.app.AlertDialog
import android.view.View
import android.widget.ScrollView
import io.github.maxhayim.tvhub.model.SourceConfig
import io.github.maxhayim.tvhub.model.SourceStatus
import io.github.maxhayim.tvhub.model.SourceType
import io.github.maxhayim.tvhub.ui.Ui.dp

/** Every configured source with its last result, and the way to add, edit, refresh or remove one. */
class SourcesScreen(host: MainActivity) : Screen(host) {
    private val actions = Ui.horizontal(ctx, 16)
    private val list = Ui.vertical(ctx, 10)

    override val view: View = ScrollView(ctx).apply {
        clipChildren = false; clipToPadding = false
        isVerticalScrollBarEnabled = false
        val col = Ui.vertical(ctx, 18)
        col.addView(Ui.text(ctx, "Sources", 30f, bold = true))
        col.addView(Ui.text(ctx, "Every source is optional. Channels from playlists, Dispatcharr and tuners play here; apps open on their own.", 16f, Ui.DIM, lines = 2))
        actions.addView(Ui.button(ctx, "Add a source") { addDialog() })
        actions.addView(Ui.button(ctx, "Refresh all") { repo.refreshAll() })
        col.addView(actions)
        col.addView(list)
        addView(col, Ui.lp())
    }

    override fun onShow() {
        watch(repo.sources) { build(it) }
        watch(repo.refresh) { if (!it.running) repo.load() }
    }

    private fun build(sources: List<Pair<SourceConfig, SourceStatus>>) {
        val hadFocus = list.findFocus() != null
        list.removeAllViews()
        if (sources.isEmpty()) list.addView(Ui.text(ctx, "No sources yet.", 18f, Ui.DIM))
        sources.forEach { (s, st) ->
            val row = Ui.vertical(ctx, 4).apply {
                isFocusable = true; isClickable = true
                background = Ui.focusable(ctx)
                setPadding(dp(20), dp(14), dp(20), dp(14))
                setOnClickListener { actionsFor(s) }
            }
            row.addView(Ui.text(ctx, s.name + if (!s.enabled) "  (off)" else "", 20f, if (s.enabled) Ui.TEXT else Ui.DIM, bold = true))
            row.addView(Ui.text(ctx, typeLabel(s.type), 14f, Ui.DIM))
            val line = when {
                !s.enabled -> "Turned off"
                s.type == SourceType.APPS -> "${s.packages.size} app${if (s.packages.size == 1) "" else "s"}, opened as shortcuts"
                st.error != null -> st.error
                st.lastRefresh == 0L -> "Not read yet"
                s.type == SourceType.XMLTV -> "${plural(st.channelCount, "guide channel")} · read ${Ui.dayTime(ctx, st.lastRefresh)}"
                else -> plural(st.channelCount, "channel") + (if (st.skipped > 0) " · ${plural(st.skipped, "entry", "entries")} skipped (not http/https, or encrypted)" else "") + " · read ${Ui.dayTime(ctx, st.lastRefresh)}"
            }
            row.addView(Ui.text(ctx, line, 15f, if (st.error != null && s.enabled) Ui.ERROR else Ui.DIM, lines = 2))
            list.addView(row)
        }
        if (hadFocus) list.getChildAt(0)?.requestFocus()
    }

    private fun actionsFor(s: SourceConfig) {
        val items = arrayOf(
            if (s.type == SourceType.APPS) "Choose apps" else "Edit",
            if (s.enabled) "Turn off" else "Turn on",
            "Refresh all sources now",
            "Remove",
        )
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(s.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> SourceEditorActivity.start(ctx, s.type, s.id)
                    1 -> repo.saveSource(s.copy(enabled = !s.enabled))
                    2 -> repo.refreshAll()
                    3 -> AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Remove ${s.name}?")
                        .setMessage("Its channels leave the guide. Favourites of channels no other source has are kept in case you add it back.")
                        .setPositiveButton("Remove") { _, _ -> repo.deleteSource(s.id) }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun addDialog() {
        val types = SourceType.entries
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Add a source")
            .setItems(types.map { typeLabel(it) }.toTypedArray()) { _, i -> SourceEditorActivity.start(ctx, types[i], 0) }
            .show()
    }

    override fun focusFirst(): Boolean = actions.getChildAt(0)?.requestFocus() ?: false

    companion object {
        fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

        fun typeLabel(t: SourceType) = when (t) {
            SourceType.M3U -> "M3U playlist"
            SourceType.XMLTV -> "XMLTV guide"
            SourceType.DISPATCHARR -> "Dispatcharr"
            SourceType.HDHOMERUN -> "HDHomeRun tuner (antenna or cable)"
            SourceType.APPS -> "Streaming apps (shortcuts)"
        }
    }
}

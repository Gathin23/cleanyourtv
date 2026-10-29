package io.github.maxhayim.tvhub.ui

import android.content.Intent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.Toast
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.source.Apps
import io.github.maxhayim.tvhub.ui.Ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** Quick ways in: the guide, recent and favourite channels, and the configured apps. */
class HomeScreen(host: MainActivity) : Screen(host) {
    private val clock = Ui.text(ctx, "", 20f, Ui.DIM)
    private val summary = Ui.text(ctx, "", 16f, Ui.DIM, lines = 2)
    private val actions = Ui.horizontal(ctx, 16)
    private val rows = Ui.vertical(ctx, 28)
    private val body = Ui.vertical(ctx, 24)

    override val view: View = ScrollView(ctx).apply {
        clipChildren = false; clipToPadding = false
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        val head = Ui.horizontal(ctx).apply {
            addView(Ui.text(ctx, "Live TV", 34f, bold = true), Ui.lp(0, Ui.WRAP, 1f))
            addView(clock)
        }
        body.addView(head)
        body.addView(summary)
        body.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(dp(4), dp(6), dp(4), dp(6)); addView(actions) })
        body.addView(rows)
        addView(body, Ui.lp())
    }

    override fun onShow() {
        watch(combine(repo.channels, repo.favorites, repo.sources, repo.guideVersion) { c, f, s, _ -> Triple(c, f, s) }) { (channels, favs, sources) ->
            launch { build(channels, favs, sources.isNotEmpty()) }
        }
        launch {
            while (true) { clock.text = Ui.dayTime(ctx, System.currentTimeMillis()); delay(15_000) }
        }
    }

    private suspend fun build(channels: List<Channel>, favs: Set<String>, hasSources: Boolean) {
        val hadFocus = view.findFocus() != null
        val recents = withContext(Dispatchers.IO) { repo.recents() }
        val favList = channels.filter { it.key in favs }
        val apps = repo.appShortcuts()
        val nn = withContext(Dispatchers.IO) { repo.nowNext((recents + favList).distinctBy { it.key }) }

        summary.text = when {
            !hasSources -> "No sources yet. Add an M3U playlist, Dispatcharr, an HDHomeRun tuner or your streaming apps."
            channels.isEmpty() -> "Your sources have no channels yet. Check Sources for errors, or refresh."
            else -> "${channels.size} channels" + (if (apps.isNotEmpty()) " · ${apps.size} app${if (apps.size == 1) "" else "s"}" else "")
        }

        actions.removeAllViews()
        if (channels.isNotEmpty()) {
            actions.addView(Ui.button(ctx, "Open the guide") { host.go(MainActivity.Page.GUIDE) })
            actions.addView(Ui.button(ctx, "All channels") { host.go(MainActivity.Page.LIVE) })
            recents.firstOrNull()?.let { last -> actions.addView(Ui.button(ctx, "Resume ${last.name}") { host.play(channels, channels.indexOfFirst { it.key == last.key }.coerceAtLeast(0)) }) }
        }
        if (!hasSources || channels.isEmpty()) {
            actions.addView(Ui.button(ctx, "Set up sources") { ctx.startActivity(Intent(ctx, OnboardingActivity::class.java)) })
        }
        actions.addView(Ui.button(ctx, "Refresh") { repo.refreshAll(); Toast.makeText(ctx, "Refreshing…", Toast.LENGTH_SHORT).show() })

        rows.removeAllViews()
        if (recents.isNotEmpty()) rows.addView(channelRow("Recently watched", recents, nn))
        if (favList.isNotEmpty()) rows.addView(channelRow("Favorites", favList, nn))
        else if (channels.isNotEmpty()) rows.addView(Ui.text(ctx, "Hold OK on a channel to add it to Favorites.", 16f, Ui.DIM))
        if (apps.isNotEmpty()) rows.addView(appRow(apps))
        if (hadFocus) focusFirst()
    }

    private fun channelRow(title: String, list: List<Channel>, nn: NowNext): View {
        val box = Ui.vertical(ctx, 12)
        box.addView(Ui.text(ctx, title, 20f, bold = true))
        val strip = Ui.horizontal(ctx, 20).apply { clipChildren = false; setPadding(dp(6), dp(8), dp(6), dp(8)) }
        list.take(20).forEachIndexed { i, ch ->
            strip.addView(ChannelCard(ctx).apply {
                bindChannel(ch, nn[ch.key]?.first)
                setOnClickListener { host.play(list, i) }
                setOnLongClickListener {
                    val on = repo.toggleFavorite(ch.key)
                    Toast.makeText(ctx, if (on) "Added ${ch.name} to Favorites" else "Removed ${ch.name} from Favorites", Toast.LENGTH_SHORT).show()
                    true
                }
            })
        }
        box.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; addView(strip) })
        return box
    }

    private fun appRow(apps: List<io.github.maxhayim.tvhub.model.AppShortcut>): View {
        val box = Ui.vertical(ctx, 12)
        box.addView(Ui.text(ctx, "Apps", 20f, bold = true))
        box.addView(Ui.text(ctx, "These open the app itself. Their channels are not part of this guide.", 14f, Ui.DIM))
        val strip = Ui.horizontal(ctx, 20).apply { clipChildren = false; setPadding(dp(6), dp(8), dp(6), dp(8)) }
        val pm = ctx.packageManager
        apps.forEach { a ->
            val icon = runCatching { pm.getApplicationBanner(a.pkg) ?: pm.getApplicationIcon(a.pkg) }.getOrNull()
            strip.addView(ChannelCard(ctx).apply {
                bindApp(a.label, icon)
                setOnClickListener {
                    val i = Apps.launchIntent(ctx, a.pkg)
                    if (i != null) ctx.startActivity(i) else Toast.makeText(ctx, "${a.label} is not installed any more", Toast.LENGTH_SHORT).show()
                }
            })
        }
        box.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; addView(strip) })
        return box
    }

    override fun focusFirst(): Boolean = actions.getChildAt(0)?.requestFocus() ?: false
}


package io.github.maxhayim.tvhub.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.maxhayim.tvhub.R
import io.github.maxhayim.tvhub.TvHubApp
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.player.PlayerActivity
import io.github.maxhayim.tvhub.ui.Ui.dp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The hub's window: a navigation rail on the left and one page beside it. Moving focus along the
 * rail switches the page; Right enters it; Back returns to the rail, and Back on the rail leaves.
 */
class MainActivity : Activity() {
    val scope = MainScope()
    val repo get() = (application as TvHubApp).repo

    enum class Page(val label: String) { HOME("Home"), LIVE("Live TV"), GUIDE("Guide"), FAVORITES("Favorites"), SOURCES("Sources"), SETTINGS("Settings") }

    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private val navItems = LinkedHashMap<Page, TextView>()
    private val screens = HashMap<Page, Screen>()
    private var current: Page? = null
    /**
     * Between leaving this window and getting it back, Android may hand focus to the first rail
     * item (when the view that had it was rebuilt meanwhile). That must not switch the page.
     */
    private var restoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { setBackgroundColor(Ui.BG) }

        val rail = Ui.vertical(this, 6).apply {
            setPadding(dp(20), dp(28), dp(12), dp(20))
            setBackgroundColor(0xFF0F1216.toInt())
        }
        rail.addView(Ui.text(this, getString(R.string.app_name), 24f, bold = true).apply { setPadding(dp(12), 0, 0, dp(18)) })
        Page.entries.forEach { p ->
            val item = Ui.text(this, p.label, 19f, Ui.DIM, bold = true).apply {
                isFocusable = true; isClickable = true
                background = Ui.focusable(this@MainActivity, 0x00000000, 22, Ui.SURFACE)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setOnFocusChangeListener { _, has -> if (has && !restoring) show(p); refreshNavColors() }
                setOnClickListener { focusContent() }
                // Right always enters the page at its first control, not whatever sits nearest.
                setOnKeyListener { _, code, e -> code == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN && focusContent() }
                id = View.generateViewId()
            }
            navItems[p] = item
            rail.addView(item, Ui.lp(Ui.MATCH, Ui.WRAP))
        }
        // The rail ends at both ends instead of wandering into the page.
        navItems.values.first().let { it.nextFocusUpId = it.id }
        navItems.values.last().let { it.nextFocusDownId = it.id }
        rail.addView(View(this), Ui.lp(Ui.MATCH, 0, 1f))
        status = Ui.text(this, "", 13f, Ui.DIM, lines = 3).apply { setPadding(dp(12), 0, dp(4), 0) }
        rail.addView(status, Ui.lp(Ui.MATCH, Ui.WRAP))
        root.addView(rail, LinearLayout.LayoutParams(dp(210), Ui.MATCH))

        content = FrameLayout(this).apply { setPadding(dp(24), dp(28), dp(32), dp(20)) }
        root.addView(content, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        setContentView(root)

        scope.launch {
            repo.refresh.collect { st ->
                status.text = st.message
                status.setTextColor(if (!st.running && st.errors > 0) Ui.ERROR else Ui.DIM)
            }
        }

        val start = intent.getStringExtra(EXTRA_PAGE)?.let { runCatching { Page.valueOf(it) }.getOrNull() } ?: Page.HOME
        navItems.getValue(start).requestFocus()
        show(start)

        if (!repo.prefs.onboarded) startActivity(Intent(this, OnboardingActivity::class.java))
        else repo.refreshIfStale()
    }

    override fun onResume() {
        super.onResume()
        current?.let { screens[it]?.onShow() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !restoring) return
        content.post {
            restoring = false
            val f = currentFocus
            val cur = current ?: return@post
            if (f == null || (navItems.values.any { it === f } && f !== navItems[cur])) navItems.getValue(cur).requestFocus()
        }
    }

    override fun onPause() {
        restoring = true
        current?.let { screens[it]?.onHide() }
        super.onPause()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    fun show(p: Page) {
        if (current == p) return
        current?.let { screens[it]?.onHide() }
        current = p
        val s = screens.getOrPut(p) {
            when (p) {
                Page.HOME -> HomeScreen(this)
                Page.LIVE -> ChannelsScreen(this, favoritesOnly = false)
                Page.GUIDE -> GuideScreen(this)
                Page.FAVORITES -> ChannelsScreen(this, favoritesOnly = true)
                Page.SOURCES -> SourcesScreen(this)
                Page.SETTINGS -> SettingsScreen(this)
            }
        }
        content.removeAllViews()
        // A margin inside the page where focused controls can grow (scaleOnFocus) without being cut.
        s.view.setPadding(dp(8), s.view.paddingTop, dp(8), s.view.paddingBottom)
        (s.view as? android.view.ViewGroup)?.clipToPadding = false
        content.addView(s.view, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        // The rail item to the left of the page is the current one, so Left from the page returns to it.
        s.view.nextFocusLeftId = navItems.getValue(p).id
        navItems.values.forEach { it.nextFocusRightId = View.NO_ID }
        s.onShow()
        refreshNavColors()
    }

    private fun refreshNavColors() {
        navItems.forEach { (p, v) ->
            v.isSelected = p == current && !v.isFocused
            v.setTextColor(if (p == current || v.isFocused) Ui.TEXT else Ui.DIM)
        }
    }

    fun focusContent(): Boolean = current?.let { screens[it]?.focusFirst() } ?: false

    fun go(p: Page) { navItems.getValue(p).requestFocus(); show(p); content.post { focusContent() } }

    fun play(list: List<Channel>, index: Int) {
        if (list.isEmpty()) return
        PlayerActivity.start(this, list, index)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val focus = currentFocus
            if (focus != null && navItems.values.none { it === focus }) {
                current?.let { navItems.getValue(it).requestFocus() }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}

/** Gravity helper for one-off centred text. */
internal fun TextView.centered(): TextView = apply { gravity = Gravity.CENTER }

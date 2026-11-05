package io.github.maxhayim.tvhub.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.parse.XmltvParser
import io.github.maxhayim.tvhub.ui.Ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live TV: categories on the left, the channels of the chosen one on the right with what is on
 * now and next, and a search box. Favorites is the same page limited to favourite channels.
 */
class ChannelsScreen(host: MainActivity, private val favoritesOnly: Boolean) : Screen(host) {

    /** A category: a label and the channels in it. */
    private data class Cat(val label: String, val filter: (Channel) -> Boolean)

    private var channels: List<Channel> = emptyList()
    private var favs: Set<String> = emptySet()
    private var cats: List<Cat> = emptyList()
    private var catIndex = 0
    private var query = ""
    private var nnJob: Job? = null

    private val title = Ui.text(ctx, if (favoritesOnly) "Favorites" else "Live TV", 30f, bold = true)
    private val empty = Ui.text(ctx, "", 18f, Ui.DIM, lines = 4)
    private val adapter = ChannelAdapter({ list, i -> host.play(list, i) }, ::toggleFav)
    private val list = RecyclerView(ctx).apply {
        layoutManager = LinearLayoutManager(ctx)
        this.adapter = this@ChannelsScreen.adapter
        itemAnimator = null
        clipToPadding = false
        setPadding(dp(4), dp(4), dp(4), dp(24))
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }
    private val catAdapter = CatAdapter()
    private val catList = RecyclerView(ctx).apply {
        layoutManager = LinearLayoutManager(ctx)
        adapter = catAdapter
        itemAnimator = null
        setPadding(0, dp(4), 0, dp(24)); clipToPadding = false
    }
    private val search = EditText(ctx).apply {
        hint = "Search channels"
        setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); textSize = 18f
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        background = Ui.focusable(ctx, Ui.SURFACE, 24)
        setPadding(dp(20), dp(10), dp(20), dp(10))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); apply() }
        })
        setOnEditorActionListener { _, _, _ -> list.post { list.getChildAt(0)?.requestFocus() }; true }
    }

    override val view: View = Ui.vertical(ctx, 16).apply {
        val head = Ui.horizontal(ctx, 24)
        head.addView(title, Ui.lp(0, Ui.WRAP, 1f))
        head.addView(search, Ui.lp(dp(360), Ui.WRAP))
        addView(head)
        val bodyRow = Ui.horizontal(ctx, 20)
        if (!favoritesOnly) bodyRow.addView(catList, Ui.lp(dp(230), Ui.MATCH))
        val right = android.widget.FrameLayout(ctx)
        right.addView(list, android.widget.FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        right.addView(empty, android.widget.FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP))
        bodyRow.addView(right, Ui.lp(0, Ui.MATCH, 1f))
        addView(bodyRow, Ui.lp(Ui.MATCH, 0, 1f))
    }

    override fun onShow() {
        watch(combine(repo.channels, repo.favorites) { c, f -> c to f }) { (c, f) ->
            channels = c; favs = f; adapter.favorites = f
            rebuildCats(); apply()
        }
        watch(repo.guideVersion) { loadNowNext() }
        launch { while (true) { delay(60_000); loadNowNext() } }
    }

    private fun rebuildCats() {
        val out = ArrayList<Cat>()
        out += Cat("All channels") { true }
        if (!favoritesOnly) {
            out += Cat("Favorites") { it.key in favs }
            channels.mapNotNull { it.group }.distinct().forEach { g -> out += Cat(g) { it.group == g } }
            val sources = channels.flatMap { it.sourceNames }.distinct()
            if (sources.size > 1) sources.forEach { s -> out += Cat("From $s") { s in it.sourceNames } }
        }
        cats = out
        if (catIndex >= cats.size) catIndex = 0
        catAdapter.notifyDataSetChanged()
    }

    private fun apply() {
        val cat = cats.getOrNull(catIndex)
        val q = XmltvParser.normalise(query)
        val shown = channels.filter { ch ->
            (!favoritesOnly || ch.key in favs) && (cat?.filter?.invoke(ch) ?: true) &&
                (q.isEmpty() || XmltvParser.normalise(ch.name).contains(q) || ch.number?.startsWith(query.trim()) == true)
        }
        adapter.submit(shown)
        empty.text = when {
            shown.isNotEmpty() -> ""
            channels.isEmpty() -> "No channels yet. Add a source in Sources."
            favoritesOnly && favs.isEmpty() -> "No favourites yet. Hold OK on a channel in Live TV or the Guide to add it."
            query.isNotEmpty() -> "No channel matches \"$query\"."
            else -> "No channels here."
        }
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        loadNowNext()
    }

    private fun loadNowNext() {
        nnJob?.cancel()
        val items = adapter.items
        nnJob = host.scope.launch {
            val nn = withContext(Dispatchers.IO) { repo.nowNext(items.take(3000)) }
            adapter.nowNext = nn
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    private fun toggleFav(ch: Channel) {
        val on = repo.toggleFavorite(ch.key)
        Toast.makeText(ctx, if (on) "Added ${ch.name} to Favorites" else "Removed ${ch.name} from Favorites", Toast.LENGTH_SHORT).show()
    }

    override fun focusFirst(): Boolean {
        if (adapter.itemCount > 0) {
            val v = list.layoutManager?.findViewByPosition(0) ?: list.getChildAt(0)
            if (v != null) return v.requestFocus()
        }
        return if (!favoritesOnly && catList.childCount > 0) catList.getChildAt(0).requestFocus() else search.requestFocus()
    }

    private inner class CatAdapter : RecyclerView.Adapter<CatAdapter.H>() {
        inner class H(val v: TextView) : RecyclerView.ViewHolder(v)
        override fun getItemCount() = cats.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = H(Ui.text(ctx, "", 17f, Ui.DIM, bold = true).apply {
            isFocusable = true; isClickable = true
            background = Ui.focusable(ctx, 0x00000000, 20, Ui.SURFACE)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP).apply { bottomMargin = dp(4) }
        })
        override fun onBindViewHolder(h: H, position: Int) {
            val c = cats[position]
            val count = channels.count(c.filter)
            h.v.text = "${c.label}  ${if (count > 0) count else ""}".trim()
            h.v.isSelected = position == catIndex
            h.v.setTextColor(if (position == catIndex) Ui.TEXT else Ui.DIM)
            h.v.setOnFocusChangeListener { _, has ->
                val pos = h.bindingAdapterPosition
                if (has && catIndex != pos && pos >= 0) {
                    val old = catIndex
                    catIndex = pos
                    apply()
                    list.post { catAdapter.notifyItemChanged(old); catAdapter.notifyItemChanged(pos) }
                }
            }
            h.v.setOnClickListener { list.getChildAt(0)?.requestFocus() }
            h.v.setOnKeyListener { _, code, e ->
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN && adapter.itemCount > 0) { list.getChildAt(0)?.requestFocus(); true } else false
            }
        }
    }
}

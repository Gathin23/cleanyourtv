package io.github.maxhayim.tvhub.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.maxhayim.tvhub.TvHubApp
import io.github.maxhayim.tvhub.model.SourceConfig
import io.github.maxhayim.tvhub.model.SourceType
import io.github.maxhayim.tvhub.parse.M3uParser
import io.github.maxhayim.tvhub.source.Apps
import io.github.maxhayim.tvhub.source.HdHomeRun
import io.github.maxhayim.tvhub.source.Providers
import io.github.maxhayim.tvhub.ui.Ui.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Adds or edits one source. "Check" reads it once without saving, so typos show up before they reach the guide. */
class SourceEditorActivity : Activity() {
    private val scope = MainScope()
    private val repo get() = (application as TvHubApp).repo
    private lateinit var type: SourceType
    private var existing: SourceConfig? = null

    private lateinit var form: LinearLayout
    private lateinit var result: android.widget.TextView
    private val name by lazy { field("Name", "") }
    private val url by lazy { field("", "", InputType.TYPE_TEXT_VARIATION_URI) }
    private val epg by lazy { field("Guide (XMLTV) address, optional", "", InputType.TYPE_TEXT_VARIATION_URI) }
    private val profile by lazy { field("Output profile, optional (empty for all channels)", "") }
    private val headerEpg by lazy { check("Also use the guide the playlist names in its header (x-tvg-url)") }
    private val transcode by lazy { check("Ask the tuner for H.264 (HDHomeRun EXTEND models only). Turn on if channels play sound but no picture") }
    private val appChecks = LinkedHashMap<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = SourceType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: SourceType.M3U.name)
        val id = intent.getLongExtra(EXTRA_ID, 0)
        existing = repo.sources.value.firstOrNull { it.first.id == id }?.first

        form = Ui.vertical(this, 14).apply { setPadding(dp(64), dp(40), dp(64), dp(40)) }
        result = Ui.text(this, "", 16f, Ui.DIM, lines = 4)
        form.addView(Ui.text(this, (if (existing == null) "Add " else "Edit ") + SourcesScreen.typeLabel(type), 28f, bold = true))
        when (type) {
            SourceType.M3U -> {
                hint("The address of an M3U or M3U8 playlist you are allowed to use. Many IPTV services give one; it is stored encrypted on this TV.")
                labelled("Playlist address", url, "http://192.168.1.10:8000/playlist.m3u")
                labelled("Name", name, "My playlist"); add(epg); add(headerEpg)
            }
            SourceType.XMLTV -> {
                hint("An XMLTV guide (.xml or .xml.gz). Its programmes are matched to channels by tvg-id, or by name for channels without one.")
                labelled("Guide address", url, "http://192.168.1.10:8000/guide.xml")
                labelled("Name", name, "My guide")
            }
            SourceType.DISPATCHARR -> {
                hint("Your Dispatcharr server's address, as you open it in a browser. TV Hub reads its /output/m3u and /output/epg. If you get \"refused access\", allow this TV's network under Dispatcharr's M3U/EPG network settings.")
                labelled("Server address", url, "http://192.168.1.10:9191")
                labelled("Name", name, "Dispatcharr"); add(profile)
            }
            SourceType.HDHOMERUN -> {
                hint("HDHomeRun tuners are found on the local network. The guide is not read from SiliconDust's online service; add an XMLTV guide for listings.")
                val found = Ui.vertical(this, 8)
                add(Ui.button(this, "Scan the network") { scan(found) })
                add(found)
                labelled("Tuner address", url, "192.168.1.50")
                labelled("Name", name, "Antenna"); add(transcode)
            }
            SourceType.APPS -> {
                hint("Apps you pick appear as shortcuts on Home. They open the app; TV Hub cannot read their channels or guides, so they are never shown as channels.")
                val picked = existing?.packages?.toSet()
                Apps.detected(this).forEach { appCheck(it.pkg, it.label, picked?.contains(it.pkg) ?: true) }
                if (appChecks.isEmpty()) hint("None of the live-TV apps TV Hub knows is installed.")
                val more = Ui.vertical(this, 8)
                add(Ui.button(this, "Show every TV app") {
                    Apps.allTvApps(this).filter { it.pkg !in appChecks }.forEach { appCheck(it.pkg, it.label, picked?.contains(it.pkg) == true, more) }
                })
                add(more)
                name.setText("Streaming apps")
            }
        }
        existing?.let { e ->
            name.setText(e.name); url.setText(e.url); epg.setText(e.epgUrl); profile.setText(e.profile)
            headerEpg.isChecked = e.useHeaderEpg; transcode.isChecked = e.transcode
        } ?: run { headerEpg.isChecked = true }

        val buttons = Ui.horizontal(this, 16)
        buttons.addView(Ui.button(this, "Save") { save() })
        if (type != SourceType.APPS) buttons.addView(Ui.button(this, "Check") { test() })
        buttons.addView(Ui.button(this, "Cancel") { finish() })
        form.addView(buttons, Ui.lp(Ui.WRAP, Ui.WRAP).apply { topMargin = dp(10) })
        form.addView(result)

        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(form) })
        (if (type == SourceType.APPS) appChecks.values.firstOrNull() else url)?.requestFocus()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun field(hint: String, value: String, variation: Int = 0) = EditText(this).apply {
        this.hint = hint; setText(value); setHintTextColor(0xFF6B7380.toInt()); setTextColor(Ui.TEXT); textSize = 18f
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or variation
        imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_FULLSCREEN
        background = Ui.focusable(this@SourceEditorActivity, Ui.SURFACE, 10)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        // An old "Check" result or error no longer applies once the text changes.
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { if (::result.isInitialized) result.text = "" }
        })
    }

    private fun check(label: String) = CheckBox(this).apply {
        text = label; setTextColor(Ui.TEXT); textSize = 17f
        background = Ui.focusable(this@SourceEditorActivity, 0x00000000, 10)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        buttonTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT)
    }

    private fun appCheck(pkg: String, label: String, on: Boolean, into: LinearLayout = form) {
        val c = check(label).apply { isChecked = on }
        appChecks[pkg] = c
        into.addView(c, if (into === form) form.childCount else into.childCount)
    }

    private fun add(v: View) = form.addView(v, Ui.lp(Ui.MATCH, Ui.WRAP))
    private fun hint(s: String) = form.addView(Ui.text(this, s, 16f, Ui.DIM, lines = 0))
    private fun labelled(label: String, f: EditText, example: String) {
        form.addView(Ui.text(this, label, 15f, Ui.DIM))
        f.hint = example
        add(f)
    }

    private fun scan(into: LinearLayout) {
        into.removeAllViews()
        into.addView(Ui.text(this, "Scanning…", 16f, Ui.DIM))
        scope.launch {
            val devices = withContext(Dispatchers.IO) { runCatching { HdHomeRun.discover() }.getOrDefault(emptyList()) }
            into.removeAllViews()
            if (devices.isEmpty()) into.addView(Ui.text(this@SourceEditorActivity, "No tuner answered. Check it is on the same network, or type its address below.", 16f, Ui.ERROR, lines = 3))
            devices.forEach { d ->
                into.addView(Ui.button(this@SourceEditorActivity, "${d.name} ${d.model} · ${d.host} · ${d.tuners} tuners") {
                    url.setText(d.host)
                    if (name.text.isBlank()) name.setText(d.name)
                    result.text = "Selected ${d.host}. Save to add it."
                })
            }
            into.getChildAt(0)?.requestFocus()
        }
    }

    private fun build(): SourceConfig? {
        val u = url.text.toString().trim()
        val n = name.text.toString().trim().ifEmpty { defaultName() }
        val e = epg.text.toString().trim()
        when (type) {
            SourceType.M3U, SourceType.XMLTV, SourceType.DISPATCHARR -> if (!M3uParser.isHttp(u)) return fail("Enter an address starting with http:// or https://")
            SourceType.HDHOMERUN -> if (u.isEmpty() || u.contains(' ')) return fail("Enter the tuner's address, or scan for it")
            SourceType.APPS -> Unit
        }
        if (type == SourceType.M3U && e.isNotEmpty() && !M3uParser.isHttp(e)) return fail("The guide address must start with http:// or https://")
        return (existing ?: SourceConfig(type = type, name = n)).copy(
            name = n, url = u, epgUrl = e, profile = profile.text.toString().trim(),
            useHeaderEpg = headerEpg.isChecked, transcode = transcode.isChecked,
            packages = appChecks.filterValues { it.isChecked }.keys.toList(),
        )
    }

    private fun defaultName() = when (type) {
        SourceType.M3U -> "Playlist"; SourceType.XMLTV -> "Guide"; SourceType.DISPATCHARR -> "Dispatcharr"
        SourceType.HDHOMERUN -> "Antenna"; SourceType.APPS -> "Streaming apps"
    }

    private fun fail(msg: String): SourceConfig? { result.setTextColor(Ui.ERROR); result.text = msg; return null }

    private fun save() {
        val s = build() ?: return
        repo.saveSource(s)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_TYPE, type.name))
        finish()
    }

    private fun test() {
        val s = build() ?: return
        result.setTextColor(Ui.DIM); result.text = "Checking…"
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    if (type == SourceType.XMLTV) {
                        io.github.maxhayim.tvhub.data.Http.open(s.url, 256L * 1024 * 1024).use { input ->
                            val head = ByteArray(4096); val n = input.read(head)
                            val t = String(head, 0, maxOf(n, 0))
                            if (t.contains("<tv")) "Found an XMLTV guide. Save to read it." else throw java.io.IOException("That address did not return an XMLTV guide")
                        }
                    } else {
                        val r = Providers.forType(type).load(s)
                        "Found ${SourcesScreen.plural(r.channels.size, "channel")}" + (if (r.skipped > 0) ", ${r.skipped} skipped" else "") +
                            (if (r.epgUrls.isNotEmpty()) " and a guide address" else "") + ". Save to add them."
                    }
                } catch (e: Exception) { "!" + (io.github.maxhayim.tvhub.data.Http.friendly(e).message ?: "Could not read it") }
            }
            result.setTextColor(if (msg.startsWith("!")) Ui.ERROR else Ui.OK)
            result.text = msg.removePrefix("!")
        }
    }

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_ID = "id"
        fun intent(c: Context, type: SourceType, id: Long) = Intent(c, SourceEditorActivity::class.java).putExtra(EXTRA_TYPE, type.name).putExtra(EXTRA_ID, id)
        fun start(c: Context, type: SourceType, id: Long) = c.startActivity(intent(c, type, id))
    }
}

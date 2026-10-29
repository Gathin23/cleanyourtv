package io.github.maxhayim.tvhub.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import io.github.maxhayim.tvhub.TvHubApp
import io.github.maxhayim.tvhub.model.SourceType
import io.github.maxhayim.tvhub.ui.Ui.dp

/** First launch: pick any sources to add now, or skip. Every step is optional and can be done later in Sources. */
class OnboardingActivity : Activity() {
    private val repo get() = (application as TvHubApp).repo
    private val buttons = LinkedHashMap<SourceType, TextView>()
    private val labels = linkedMapOf(
        SourceType.M3U to "Add an M3U playlist",
        SourceType.XMLTV to "Add an XMLTV guide",
        SourceType.DISPATCHARR to "Connect Dispatcharr",
        SourceType.HDHOMERUN to "Find an HDHomeRun tuner",
        SourceType.APPS to "Choose streaming apps",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.vertical(this, 16).apply { setPadding(dp(96), dp(56), dp(96), dp(40)) }
        col.addView(Ui.text(this, "Welcome to TV Hub", 34f, bold = true))
        col.addView(Ui.text(this, "One guide for your live TV. Add the sources you have; each one is optional, and you can add or change them later under Sources. Only use playlists and services you are allowed to watch.", 18f, Ui.DIM, lines = 0))
        val list = Ui.vertical(this, 10)
        labels.forEach { (t, l) ->
            val b = Ui.button(this, l) { startActivityForResult(SourceEditorActivity.intent(this, t, 0), 1) }
            b.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            buttons[t] = b
            list.addView(b, Ui.lp(dp(520), Ui.WRAP))
        }
        col.addView(list)
        val done = Ui.button(this, "Skip for now") { finishSetup() }
        col.addView(done, Ui.lp(Ui.WRAP, Ui.WRAP).apply { topMargin = dp(12) })
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(col) })
        buttons.values.first().requestFocus()
        doneButton = done
    }

    private var doneButton: TextView? = null

    @Deprecated("Activity result API needs androidx.activity; the platform call is enough here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val t = data?.getStringExtra(SourceEditorActivity.EXTRA_TYPE)?.let { SourceType.valueOf(it) } ?: return
        buttons[t]?.let { it.text = "✓  " + labels[t]; it.setTextColor(Ui.OK) }
        doneButton?.text = "Done"
        doneButton?.requestFocus()
    }

    @Deprecated("Back is handled here so skipping setup still marks it done")
    override fun onBackPressed() { finishSetup() }

    private fun finishSetup() {
        repo.prefs.onboarded = true
        repo.load()
        finish()
    }
}

package io.github.maxhayim.tvhub.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.maxhayim.tvhub.TvHubApp
import java.text.DateFormat
import java.util.Date

/** The small view kit every screen is built from: colours, type sizes and the focus look. */
object Ui {
    const val BG = 0xFF0B0D10.toInt()
    const val SURFACE = 0xFF161A20.toInt()
    const val SURFACE_2 = 0xFF222832.toInt()
    const val TEXT = 0xFFF2F4F7.toInt()
    const val DIM = 0xFF9AA4B2.toInt()
    const val ACCENT = 0xFF4FA3E0.toInt()
    const val LIVE = 0xFFE5484D.toInt()
    const val ERROR = 0xFFFF7B72.toInt()
    const val OK = 0xFF5BC782.toInt()

    fun Context.dp(v: Number): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    fun View.dp(v: Number): Int = context.dp(v)

    fun animations(c: Context) = (c.applicationContext as TvHubApp).repo.prefs.animations

    fun rounded(c: Context, color: Int, radiusDp: Int = 12, strokeColor: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = c.dp(radiusDp).toFloat()
        if (strokeDp > 0) setStroke(c.dp(strokeDp), strokeColor)
    }

    /** Focused: lighter surface with a white ring. Clear focus is the main thing a 10-foot UI needs. */
    fun focusable(c: Context, normal: Int = SURFACE, radiusDp: Int = 12, selected: Int = SURFACE_2): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(c, SURFACE_2, radiusDp, Color.WHITE, 3))
        addState(intArrayOf(android.R.attr.state_selected), rounded(c, selected, radiusDp, ACCENT, 2))
        addState(intArrayOf(), rounded(c, normal, radiusDp))
    }

    /** Grows the focused view a little; off with Settings > Animations. */
    fun scaleOnFocus(v: View, scale: Float = 1.04f) {
        val prev = v.onFocusChangeListener
        v.setOnFocusChangeListener { view, has ->
            prev?.onFocusChange(view, has)
            if (!animations(view.context)) { view.scaleX = 1f; view.scaleY = 1f; return@setOnFocusChangeListener }
            val s = if (has) scale else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }
    }

    fun text(c: Context, s: CharSequence = "", sizeSp: Float = 18f, color: Int = TEXT, bold: Boolean = false, lines: Int = 1) = TextView(c).apply {
        text = s; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
        includeFontPadding = false
    }

    fun button(c: Context, label: String, onClick: () -> Unit) = text(c, label, 18f, bold = true).apply {
        isFocusable = true; isClickable = true
        background = focusable(c, SURFACE_2, 24)
        gravity = Gravity.CENTER
        setPadding(c.dp(24), c.dp(12), c.dp(24), c.dp(12))
        minWidth = c.dp(120)
        setOnClickListener { onClick() }
        scaleOnFocus(this, 1.05f)
    }

    // Children grow on focus (scaleOnFocus), so containers never clip them.
    fun vertical(c: Context, gapDp: Int = 0) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        if (gapDp > 0) { dividerDrawable = spacer(c, gapDp); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }
    }
    fun horizontal(c: Context, gapDp: Int = 0) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false; clipToPadding = false
        if (gapDp > 0) { dividerDrawable = spacer(c, gapDp); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }
    }
    private fun spacer(c: Context, d: Int) = GradientDrawable().apply { setSize(c.dp(d), c.dp(d)); setColor(Color.TRANSPARENT) }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun time(c: Context, t: Long): String = android.text.format.DateFormat.getTimeFormat(c).format(Date(t))
    fun dayTime(c: Context, t: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t)) + " " + time(c, t)
}

package io.github.maxhayim.tvhub.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import io.github.maxhayim.tvhub.TvHubApp
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.model.Programme
import io.github.maxhayim.tvhub.ui.Ui.dp
import kotlin.math.max
import kotlin.math.min

/**
 * The programme grid, drawn on one canvas: a few thousand channels cost no more than the rows on
 * screen. Up and Down move between channels, Left and Right between programmes; Left from the
 * first programme leaves the grid. The cursor keeps a time, so moving up and down stays at
 * roughly the same hour, as on a TV's own guide.
 */
class GuideView(c: Context) : View(c) {
    var onNeed: (first: Int, last: Int) -> Unit = { _, _ -> }
    var onSelect: (Channel, Programme?) -> Unit = { _, _ -> }
    var onLong: (Channel) -> Unit = {}
    var onInfo: (Channel, Programme?) -> Unit = { _, _ -> }

    private var channels: List<Channel> = emptyList()
    private val programmes = HashMap<String, List<Programme>>()
    private val loaded = HashSet<Int>()
    var favorites: Set<String> = emptySet()
        set(v) { field = v; invalidate() }

    private val headerH = dp(40).toFloat()
    private val rowH = dp(64).toFloat()
    private val chanW = dp(270).toFloat()
    private val slotW = dp(240).toFloat()
    private val slotMs = 30 * 60_000L
    private val pxPerMs get() = slotW / slotMs
    private val gap = dp(3).toFloat()

    private var timeMin = floorSlot(System.currentTimeMillis())
    private var timeMax = timeMin + 2 * 86_400_000L
    private var viewStart = timeMin
    private var focusRow = 0
    private var focusTime = System.currentTimeMillis()
    private var topRow = 0
    private var drawTop = 0f
    private var anim: ValueAnimator? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); color = 0xFFFFFFFF.toInt() }
    private val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(17f); color = Ui.TEXT; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val smallP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(13f); color = Ui.DIM }
    private val numP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(15f); color = Ui.DIM }
    private val rect = RectF()
    private val radius = dp(8).toFloat()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun sp(v: Float) = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    fun setData(list: List<Channel>, days: Int) {
        val keepKey = channels.getOrNull(focusRow)?.key
        channels = list
        programmes.clear(); loaded.clear()
        timeMin = floorSlot(System.currentTimeMillis())
        timeMax = System.currentTimeMillis() + days * 86_400_000L
        viewStart = max(viewStart, timeMin)
        focusTime = max(focusTime, System.currentTimeMillis())
        focusRow = list.indexOfFirst { it.key == keepKey }.takeIf { it >= 0 } ?: 0
        topRow = min(topRow, max(0, list.size - visibleRows()))
        if (focusRow !in topRow until topRow + visibleRows()) topRow = max(0, focusRow - 1)
        drawTop = topRow.toFloat()
        requestRows(); invalidate()
    }

    fun channelCount() = channels.size
    val timeRange get() = timeMin to timeMax

    /** Programmes for rows [first, last], by channel key, as asked for through [onNeed]. */
    fun putProgrammes(first: Int, last: Int, data: Map<String, List<Programme>>) {
        for (r in first..min(last, channels.size - 1)) {
            loaded += r
            val ch = channels[r]
            programmes[ch.key] = data[ch.key].orEmpty()
        }
        invalidate()
    }

    private fun visibleRows() = max(1, ((height - headerH) / rowH).toInt())

    private fun requestRows() {
        if (channels.isEmpty() || height == 0) return
        val first = max(0, topRow - 10)
        val last = min(channels.size - 1, topRow + visibleRows() + 20)
        if ((first..last).any { it !in loaded }) {
            // Mark now, so a burst of key presses asks once.
            (first..last).forEach { loaded += it }
            onNeed(first, last)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); requestRows() }

    private fun rowProgrammes(r: Int): List<Programme>? = channels.getOrNull(r)?.let { programmes[it.key] }

    /** The programme under the cursor, or null in a gap or on a row without guide data. */
    private fun focused(): Programme? = rowProgrammes(focusRow)?.firstOrNull { focusTime >= it.start && focusTime < it.stop }

    private fun hasGuide(r: Int) = channels.getOrNull(r)?.epgIds?.isNotEmpty() == true && rowProgrammes(r)?.isNotEmpty() == true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (channels.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { if (focusRow == 0) return false; moveRow(focusRow - 1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (focusRow < channels.size - 1) moveRow(focusRow + 1); return true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { moveRow(max(0, focusRow - visibleRows())); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { moveRow(min(channels.size - 1, focusRow + visibleRows())); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!hasGuide(focusRow)) return false
                val p = focused()
                val target = (p?.start ?: floorSlot(focusTime)) - 1
                if (target < timeMin) return false
                focusTime = rowProgrammes(focusRow)?.firstOrNull { target >= it.start && target < it.stop }?.let { max(it.start, timeMin) } ?: floorSlot(target)
                afterMove(); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!hasGuide(focusRow)) return true
                val target = focused()?.stop ?: (floorSlot(focusTime) + slotMs)
                if (target >= timeMax) return true
                focusTime = target
                afterMove(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { event.startTracking(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> { onInfo(channels[focusRow], focused()); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode) && channels.isNotEmpty()) { onLong(channels[focusRow]); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode) && channels.isNotEmpty()) {
            if (event.isTracking && !event.isCanceled) onSelect(channels[focusRow], focused())
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isOk(k: Int) = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun moveRow(r: Int) {
        focusRow = r
        val vis = visibleRows()
        val newTop = when {
            r < topRow -> r
            r >= topRow + vis -> r - vis + 1
            else -> topRow
        }
        if (newTop != topRow) {
            topRow = newTop
            anim?.cancel()
            if (Ui.animations(context)) {
                anim = ValueAnimator.ofFloat(drawTop, newTop.toFloat()).setDuration(110).apply {
                    addUpdateListener { drawTop = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else drawTop = newTop.toFloat()
            requestRows()
        }
        afterMove()
    }

    private fun afterMove() {
        val p = focused()
        val start = max(p?.start ?: focusTime, timeMin)
        val span = ((width - chanW) / pxPerMs).toLong()
        if (start < viewStart || start > viewStart + span - slotMs) viewStart = max(timeMin, floorSlot(start))
        invalidate()
        announce()
    }

    private fun announce() {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!am.isEnabled) return
        val ch = channels.getOrNull(focusRow) ?: return
        val p = focused()
        val text = listOfNotNull(ch.number, ch.name, p?.let { "${it.title}, ${Ui.time(context, it.start)} to ${Ui.time(context, it.stop)}" } ?: "no guide information").joinToString(", ")
        contentDescription = text
        announceForAccessibility(text)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val now = System.currentTimeMillis()
        val viewEnd = viewStart + ((w - chanW) / pxPerMs).toLong()
        val focusedView = hasFocus()

        // Time header.
        val dayLabel = if (android.text.format.DateUtils.isToday(viewStart)) "Today" else android.text.format.DateFormat.format("EEE d MMM", viewStart).toString()
        canvas.drawText(dayLabel, dp(4).toFloat(), headerH - dp(14), smallP)
        var t = viewStart
        while (t < viewEnd) {
            val x = chanW + (t - viewStart) * pxPerMs
            canvas.drawText(Ui.time(context, t), x + dp(8), headerH - dp(14), smallP)
            fill.color = 0x33FFFFFF; canvas.drawRect(x, headerH - dp(8), x + dp(1), headerH, fill)
            t += slotMs
        }

        val first = drawTop.toInt()
        val last = min(channels.size - 1, first + visibleRows() + 1)
        canvas.save()
        canvas.clipRect(0f, headerH, w, height.toFloat())
        for (r in first..last) {
            if (r < 0) continue
            val y = headerH + (r - drawTop) * rowH
            val ch = channels[r]
            val isFocusRow = focusedView && r == focusRow

            // Channel cell.
            rect.set(0f, y + gap, chanW - gap * 2, y + rowH - gap)
            fill.color = if (r == focusRow) Ui.SURFACE_2 else Ui.SURFACE
            canvas.drawRoundRect(rect, radius, radius, fill)
            val logo = (context.applicationContext as TvHubApp).logos.bitmap(ch.logo, dp(36)) { invalidate() }
            var tx = dp(12).toFloat()
            if (ch.number != null) { canvas.drawText(ch.number, tx, y + rowH / 2 + dp(5), numP); tx += dp(40) }
            if (logo != null) {
                val lh = dp(28).toFloat(); val lw = min(dp(44).toFloat(), logo.width * lh / logo.height)
                rect.set(tx, y + (rowH - lh) / 2, tx + lw, y + (rowH + lh) / 2)
                canvas.drawBitmap(logo, null, rect, null)
                tx += lw + dp(10)
            }
            val star = if (ch.key in favorites) "★ " else ""
            canvas.drawText(TextUtils.ellipsize(star + ch.name, titleP, chanW - tx - dp(16), TextUtils.TruncateAt.END).toString(), tx, y + rowH / 2 + dp(6), titleP)

            // Programmes.
            canvas.save()
            canvas.clipRect(chanW, y, w, y + rowH)
            val list = programmes[ch.key]
            when {
                ch.epgIds.isEmpty() || (list != null && list.isEmpty()) -> {
                    cell(canvas, chanW, w, y, if (ch.epgIds.isEmpty()) "No guide information" else "No listings in this guide", null, isFocusRow, false)
                }
                list == null -> cell(canvas, chanW, w, y, "…", null, false, false)
                else -> {
                    for (p in list) {
                        if (p.stop <= viewStart || p.start >= viewEnd) continue
                        val x0 = chanW + (max(p.start, viewStart) - viewStart) * pxPerMs
                        val x1 = chanW + (min(p.stop, viewEnd) - viewStart) * pxPerMs
                        val f = isFocusRow && focusTime >= p.start && focusTime < p.stop
                        cell(canvas, x0, x1, y, p.title.ifEmpty { "Untitled" }, "${Ui.time(context, p.start)} – ${Ui.time(context, p.stop)}", f, p.isOn(now))
                    }
                    if (isFocusRow && focused() == null) {
                        val s = floorSlot(focusTime)
                        val x0 = chanW + (s - viewStart) * pxPerMs
                        cell(canvas, x0, x0 + slotW, y, "No listing", null, true, false)
                    }
                }
            }
            canvas.restore()
        }
        canvas.restore()

        // Now line.
        if (now in viewStart until viewEnd) {
            val x = chanW + (now - viewStart) * pxPerMs
            fill.color = Ui.LIVE
            canvas.drawRect(x - dp(1), headerH - dp(6), x + dp(1), height.toFloat(), fill)
            canvas.drawCircle(x, headerH - dp(6), dp(4).toFloat(), fill)
        }
    }

    private fun cell(canvas: Canvas, x0: Float, x1: Float, y: Float, title: String, sub: String?, focused: Boolean, onAir: Boolean) {
        rect.set(x0 + gap, y + gap, x1 - gap, y + rowH - gap)
        if (rect.width() <= 0) return
        fill.color = when { focused -> Ui.SURFACE_2; onAir -> 0xFF1B2633.toInt(); else -> Ui.SURFACE }
        canvas.drawRoundRect(rect, radius, radius, fill)
        if (focused) canvas.drawRoundRect(rect, radius, radius, ring)
        val avail = rect.width() - dp(24)
        if (avail < dp(12)) return
        val tx = rect.left + dp(12)
        if (sub == null) {
            canvas.drawText(TextUtils.ellipsize(title, titleP, avail, TextUtils.TruncateAt.END).toString(), tx, y + rowH / 2 + dp(6), if (title.startsWith("No ") || title == "…") smallP else titleP)
        } else {
            canvas.drawText(TextUtils.ellipsize(title, titleP, avail, TextUtils.TruncateAt.END).toString(), tx, y + rowH / 2 - dp(2), titleP)
            canvas.drawText(TextUtils.ellipsize(sub, smallP, avail, TextUtils.TruncateAt.END).toString(), tx, y + rowH / 2 + dp(17), smallP)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) afterMove() else invalidate()
    }

    private fun floorSlot(t: Long): Long {
        val off = java.util.TimeZone.getDefault().getOffset(t)
        return ((t + off) / slotMs) * slotMs - off
    }
}

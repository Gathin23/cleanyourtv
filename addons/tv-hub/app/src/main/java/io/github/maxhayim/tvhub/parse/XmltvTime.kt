package io.github.maxhayim.tvhub.parse

/** XMLTV times: "YYYYMMDDhhmmss +zzzz", where seconds, minutes and the offset may be left out (then UTC). */
object XmltvTime {
    fun parse(s: String?): Long? {
        if (s == null) return null
        val t = s.trim()
        var i = 0
        fun num(len: Int): Int? {
            if (i + len > t.length) return null
            var v = 0
            for (k in 0 until len) {
                val c = t[i + k]
                if (c !in '0'..'9') return null
                v = v * 10 + (c - '0')
            }
            i += len
            return v
        }
        val year = num(4) ?: return null
        val month = num(2) ?: return null
        val day = num(2) ?: return null
        val hour = num(2) ?: 0
        val minute = if (i < t.length && t[i].isDigit()) num(2) ?: 0 else 0
        val second = if (i < t.length && t[i].isDigit()) num(2) ?: 0 else 0
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
        while (i < t.length && t[i] == ' ') i++
        var offsetSec = 0
        if (i < t.length && (t[i] == '+' || t[i] == '-')) {
            val sign = if (t[i] == '-') -1 else 1
            i++
            val oh = num(2) ?: return null
            if (i < t.length && t[i] == ':') i++
            val om = num(2) ?: 0
            offsetSec = sign * (oh * 3600 + om * 60)
        }
        val days = daysFromCivil(year, month, day)
        return ((days * 86400L) + hour * 3600 + minute * 60 + second - offsetSec) * 1000L
    }

    /** Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's algorithm). */
    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }
}

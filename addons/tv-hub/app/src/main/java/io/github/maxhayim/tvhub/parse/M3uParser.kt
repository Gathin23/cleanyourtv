package io.github.maxhayim.tvhub.parse

import io.github.maxhayim.tvhub.model.RawChannel
import java.io.BufferedReader

/**
 * Reads M3U and M3U8 playlists (the extended IPTV form with #EXTINF attributes, and plain lists
 * of URLs). Only http and https streams are kept; anything else is counted as skipped, so the
 * Sources screen can say how many entries this player cannot open.
 */
object M3uParser {
    const val MAX_CHANNELS = 50_000
    private const val MAX_LINE = 16 * 1024

    data class Result(val channels: List<RawChannel>, val epgUrls: List<String>, val skipped: Int, val isPlaylist: Boolean)

    fun parse(reader: BufferedReader, sourceId: Long, sourceName: String): Result {
        val out = ArrayList<RawChannel>()
        val epg = LinkedHashSet<String>()
        var skipped = 0
        var sawHeader = false
        var info: Map<String, String> = emptyMap()
        var title: String? = null
        var group: String? = null
        var ua: String? = null
        var ref: String? = null
        var first = true
        while (true) {
            var line = reader.readLine() ?: break
            if (first) { line = line.removePrefix("\uFEFF"); first = false }
            if (line.length > MAX_LINE) { skipped++; continue }
            line = line.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U") -> {
                    sawHeader = true
                    val a = attributes(line.removePrefix("#EXTM3U"))
                    listOfNotNull(a["x-tvg-url"], a["url-tvg"]).forEach { v ->
                        v.split(',').map { it.trim() }.filter { isHttp(it) }.forEach { epg += it }
                    }
                }
                line.startsWith("#EXTINF:") -> {
                    val body = line.removePrefix("#EXTINF:")
                    val comma = titleComma(body)
                    info = attributes(if (comma >= 0) body.substring(0, comma) else body)
                    title = if (comma >= 0) body.substring(comma + 1).trim() else null
                }
                line.startsWith("#EXTGRP:") -> group = line.removePrefix("#EXTGRP:").trim()
                line.startsWith("#EXTVLCOPT:") -> {
                    val opt = line.removePrefix("#EXTVLCOPT:")
                    when {
                        opt.startsWith("http-user-agent=") -> ua = opt.substringAfter('=').trim()
                        opt.startsWith("http-referrer=") -> ref = opt.substringAfter('=').trim()
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    if (!isHttp(line) || out.size >= MAX_CHANNELS) {
                        skipped++
                    } else {
                        val name = title?.takeIf { it.isNotEmpty() } ?: info["tvg-name"]?.takeIf { it.isNotBlank() } ?: nameFromUrl(line)
                        out += RawChannel(
                            sourceId = sourceId,
                            sourceName = sourceName,
                            name = name,
                            number = (info["tvg-chno"] ?: info["channel-number"] ?: info["tvg-num"])?.trim()?.takeIf { it.isNotEmpty() },
                            tvgId = info["tvg-id"]?.trim()?.takeIf { it.isNotEmpty() },
                            tvgName = info["tvg-name"]?.trim()?.takeIf { it.isNotEmpty() },
                            logo = info["tvg-logo"]?.trim()?.takeIf { isHttp(it) },
                            group = (info["group-title"] ?: group)?.trim()?.takeIf { it.isNotEmpty() },
                            url = line,
                            userAgent = ua ?: info["http-user-agent"],
                            referrer = ref ?: info["http-referrer"],
                        )
                    }
                    info = emptyMap(); title = null; group = null; ua = null; ref = null
                }
            }
        }
        return Result(out, epg.toList(), skipped, sawHeader || out.isNotEmpty())
    }

    fun isHttp(s: String): Boolean {
        val l = s.lowercase()
        return (l.startsWith("http://") || l.startsWith("https://")) && s.length > 8 && s.none { it == ' ' || it.code < 0x20 }
    }

    /** The comma that starts the title: the first one outside a quoted attribute value. */
    internal fun titleComma(s: String): Int {
        var quoted = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return -1
    }

    /** key="value" pairs; keys are lower-cased, the first occurrence of a key wins. */
    internal fun attributes(s: String): Map<String, String> {
        val m = HashMap<String, String>()
        var i = 0
        val n = s.length
        while (i < n) {
            while (i < n && !(s[i].isLetterOrDigit())) i++
            val ks = i
            while (i < n && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == '_')) i++
            val key = s.substring(ks, i).lowercase()
            if (i < n && s[i] == '=' && i + 1 < n && s[i + 1] == '"') {
                val vs = i + 2
                val ve = s.indexOf('"', vs)
                if (ve < 0) break
                if (key.isNotEmpty() && key !in m) m[key] = s.substring(vs, ve)
                i = ve + 1
            } else if (i == ks) {
                i++
            }
        }
        return m
    }

    private fun nameFromUrl(url: String): String {
        val path = url.substringAfter("://").substringBefore('?').trimEnd('/')
        val last = path.substringAfterLast('/')
        return if (last.isNotEmpty() && last != path) last else path.substringBefore('/')
    }
}

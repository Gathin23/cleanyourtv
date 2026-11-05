package io.github.maxhayim.tvhub.parse

import io.github.maxhayim.tvhub.model.Programme
import org.xmlpull.v1.XmlPullParser

/**
 * Streams an XMLTV guide without holding it in memory. Guides list their channels before their
 * programmes, so a channel element can add its id to the wanted set (matched by display name)
 * before the programmes for it arrive.
 */
class XmltvParser(
    /** XMLTV ids some channel already names through its tvg-id. */
    wantedIds: Set<String>,
    /** Normalised names of channels without a tvg-id, for matching by display name. */
    private val namesWithoutId: Set<String>,
    private val windowStart: Long,
    private val windowEnd: Long,
) {
    private val wanted = HashSet(wantedIds)

    /** Display-name matches found: normalised name to XMLTV id. */
    val nameMatches = HashMap<String, String>()
    var channelCount = 0; private set
    var programmeCount = 0; private set

    fun parse(p: XmlPullParser, onProgramme: (Programme) -> Unit) {
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> readChannel(p)
                    "programme" -> readProgramme(p)?.let { programmeCount++; onProgramme(it) }
                }
            }
            ev = p.next()
        }
    }

    private fun readChannel(p: XmlPullParser) {
        val id = p.getAttributeValue(null, "id")?.trim().orEmpty()
        val depth = p.depth
        channelCount++
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT || (ev == XmlPullParser.END_TAG && p.depth == depth)) return
            if (ev == XmlPullParser.START_TAG && p.name == "display-name" && id.isNotEmpty()) {
                val n = normalise(p.nextText())
                if (n.isNotEmpty() && n in namesWithoutId && n !in nameMatches) {
                    nameMatches[n] = id
                    wanted += id
                }
            }
        }
    }

    private fun readProgramme(p: XmlPullParser): Programme? {
        val ch = p.getAttributeValue(null, "channel")?.trim().orEmpty()
        val start = XmltvTime.parse(p.getAttributeValue(null, "start"))
        val stop = XmltvTime.parse(p.getAttributeValue(null, "stop"))
        val keep = ch in wanted && start != null && stop != null && stop > start && stop > windowStart && start < windowEnd
        val depth = p.depth
        var title: String? = null
        var sub: String? = null
        var desc: String? = null
        var cat: String? = null
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT || (ev == XmlPullParser.END_TAG && p.depth == depth)) break
            if (keep && ev == XmlPullParser.START_TAG && p.depth == depth + 1) {
                when (p.name) {
                    "title" -> p.nextText().trim().let { if (title == null && it.isNotEmpty()) title = it }
                    "sub-title" -> p.nextText().trim().let { if (sub == null && it.isNotEmpty()) sub = it }
                    "desc" -> p.nextText().trim().let { if (desc == null && it.isNotEmpty()) desc = it.take(2000) }
                    "category" -> p.nextText().trim().let { if (cat == null && it.isNotEmpty()) cat = it }
                }
            }
        }
        if (!keep) return null
        return Programme(ch, start!!, stop!!, title ?: "", sub, desc, cat)
    }

    companion object {
        /** Lower case, letters and digits only: "BBC One HD" and "bbc-one-hd" match. */
        fun normalise(s: String): String = buildString { s.lowercase().forEach { if (it.isLetterOrDigit()) append(it) } }
    }
}

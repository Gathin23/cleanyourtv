package io.github.maxhayim.tvhub.parse

import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.model.RawChannel
import io.github.maxhayim.tvhub.model.StreamRef

/**
 * Turns the streams of every source into the channel list. Two streams are the same channel only
 * when they carry the same tvg-id, the one identifier playlists share reliably; names alone are
 * never merged. With merging off, every source keeps its own copy.
 */
object ChannelMerger {

    fun merge(raw: List<RawChannel>, mergeDuplicates: Boolean, epgByName: Map<String, String> = emptyMap()): List<Channel> {
        val byKey = LinkedHashMap<String, Builder>()
        val seen = HashMap<String, Int>()
        for (r in raw) {
            val tvg = r.tvgId?.trim()?.takeIf { it.isNotEmpty() }
            val base = if (mergeDuplicates && tvg != null) "tvg:" + tvg.lowercase()
            else "src:${r.sourceId}:" + (tvg?.lowercase() ?: XmltvParser.normalise(r.name))
            // Two entries of one source with the same key (the same channel listed twice in a group
            // each) stay separate channels, numbered by order so the key is stable across refreshes.
            val key = if (base.startsWith("tvg:") && byKey.containsKey(base)) {
                base
            } else {
                val n = seen.merge(base, 1, Int::plus)!!
                if (n == 1) base else "$base#$n"
            }
            val b = byKey.getOrPut(key) { Builder(key, r) }
            val s = StreamRef(r.url, r.userAgent, r.referrer, r.sourceName)
            if (b.streams.none { it.url == s.url }) b.streams += s
            if (b.logo == null) b.logo = r.logo
            if (b.number == null) b.number = r.number
            if (b.group == null) b.group = r.group
            if (tvg != null) b.epgIds += tvg
            else {
                val byName = epgByName[XmltvParser.normalise(r.tvgName ?: r.name)]
                if (byName != null) b.epgIds += byName
            }
        }
        return byKey.values.map { it.build() }.sortedWith(ORDER)
    }

    /** Channels with a number first, by number ("4.1" before "4.10" before "12"), then by name. */
    val ORDER: Comparator<Channel> = Comparator { a, b ->
        val an = a.number; val bn = b.number
        when {
            an != null && bn != null -> compareNumbers(an, bn).takeIf { it != 0 } ?: a.name.compareTo(b.name, true)
            an != null -> -1
            bn != null -> 1
            else -> a.name.compareTo(b.name, true)
        }
    }

    internal fun compareNumbers(a: String, b: String): Int {
        val pa = a.split('.', '-'); val pb = b.split('.', '-')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i); val y = pb.getOrNull(i)
            if (x == null) return -1
            if (y == null) return 1
            val xi = x.toLongOrNull(); val yi = y.toLongOrNull()
            val c = if (xi != null && yi != null) xi.compareTo(yi) else x.compareTo(y, true)
            if (c != 0) return c
        }
        return 0
    }

    private class Builder(val key: String, first: RawChannel) {
        val name = first.name
        var number = first.number
        var logo = first.logo
        var group = first.group
        val epgIds = LinkedHashSet<String>()
        val streams = ArrayList<StreamRef>()
        fun build() = Channel(key, name, number, logo, group, epgIds.toList(), streams.toList())
    }
}

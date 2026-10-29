package io.github.maxhayim.tvhub.source

import io.github.maxhayim.tvhub.data.Http
import io.github.maxhayim.tvhub.model.RawChannel
import io.github.maxhayim.tvhub.model.SourceConfig
import io.github.maxhayim.tvhub.model.SourceType
import io.github.maxhayim.tvhub.parse.M3uParser
import java.io.IOException
import java.net.URLEncoder

/** What one source gives a refresh: its streams and the guides it names. */
data class ProviderResult(val channels: List<RawChannel>, val epgUrls: List<String>, val skipped: Int = 0)

/** Blocking; the refresh calls it off the main thread. Throws an IOException with a message for the owner. */
fun interface Provider {
    fun load(cfg: SourceConfig): ProviderResult
}

object Providers {
    private const val PLAYLIST_MAX = 64L * 1024 * 1024

    fun forType(t: SourceType): Provider = when (t) {
        SourceType.M3U -> Provider(::m3u)
        SourceType.XMLTV -> Provider { ProviderResult(emptyList(), listOf(it.url)) }
        SourceType.DISPATCHARR -> Provider(::dispatcharr)
        SourceType.HDHOMERUN -> Provider { HdHomeRun.load(it) }
        SourceType.APPS -> Provider { ProviderResult(emptyList(), emptyList()) }
    }

    private fun m3u(cfg: SourceConfig): ProviderResult {
        val r = readPlaylist(cfg.url, cfg)
        val epg = buildList {
            if (cfg.epgUrl.isNotBlank()) add(cfg.epgUrl.trim())
            if (cfg.useHeaderEpg) addAll(r.epgUrls)
        }.distinct()
        return ProviderResult(r.channels, epg, r.skipped)
    }

    /**
     * Dispatcharr's documented outputs: /output/m3u and /output/epg, optionally followed by an
     * output profile name. Its "M3U/EPG" network setting decides which addresses may read them,
     * so a 403 here usually means the TV's network is not allowed yet.
     */
    private fun dispatcharr(cfg: SourceConfig): ProviderResult {
        val (m3u, epg) = dispatcharrUrls(cfg.url, cfg.profile)
        val r = readPlaylist(m3u, cfg)
        return ProviderResult(r.channels, listOf(epg), r.skipped)
    }

    fun dispatcharrUrls(base: String, profile: String): Pair<String, String> {
        val b = base.trim().trimEnd('/')
        val p = profile.trim().let { if (it.isEmpty()) "" else "/" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "$b/output/m3u$p" to "$b/output/epg$p"
    }

    private fun readPlaylist(url: String, cfg: SourceConfig): M3uParser.Result {
        if (!M3uParser.isHttp(url.trim())) throw IOException("The address must start with http:// or https://")
        val r = try {
            Http.open(url.trim(), PLAYLIST_MAX).bufferedReader(Charsets.UTF_8).use { M3uParser.parse(it, cfg.id, cfg.name) }
        } catch (e: Exception) { throw Http.friendly(e) }
        if (!r.isPlaylist) throw IOException("That address did not return an M3U playlist")
        return r
    }
}

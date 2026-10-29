package io.github.maxhayim.tvhub.model

/** Kinds of source the hub can read. APPS is a list of installed apps, never channels. */
enum class SourceType { M3U, XMLTV, DISPATCHARR, HDHOMERUN, APPS }

/**
 * One configured source. Everything but id and type is stored encrypted, since playlist URLs
 * often carry an account name and password in their query string.
 */
data class SourceConfig(
    val id: Long = 0,
    val type: SourceType,
    val name: String,
    /** M3U or XMLTV URL, Dispatcharr base URL, or the HDHomeRun address. */
    val url: String = "",
    /** XMLTV URL used with an M3U source; empty for none. */
    val epgUrl: String = "",
    /** Dispatcharr output profile; empty for all channels. */
    val profile: String = "",
    /** Also read the guide named in the playlist's #EXTM3U header (x-tvg-url). */
    val useHeaderEpg: Boolean = true,
    /** HDHomeRun EXTEND only: ask the tuner for H.264 instead of broadcast MPEG-2. */
    val transcode: Boolean = false,
    /** APPS: package names the owner picked. */
    val packages: List<String> = emptyList(),
    val enabled: Boolean = true,
)

/** Result of the last refresh of a source, shown on the Sources screen. */
data class SourceStatus(val lastRefresh: Long = 0, val channelCount: Int = 0, val skipped: Int = 0, val error: String? = null)

/** One playable stream as a playlist or tuner lists it. */
data class RawChannel(
    val sourceId: Long,
    val sourceName: String,
    val name: String,
    val number: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val url: String,
    val userAgent: String? = null,
    val referrer: String? = null,
)

data class StreamRef(val url: String, val userAgent: String?, val referrer: String?, val sourceName: String)

/**
 * A channel as the guide shows it. With duplicates merged, one channel can hold the streams of
 * several sources that share a tvg-id; the player falls back to the next one if a stream fails.
 */
data class Channel(
    /** Stable across refreshes; favourites and recents are stored by it. */
    val key: String,
    val name: String,
    val number: String?,
    val logo: String?,
    val group: String?,
    /** XMLTV channel ids whose programmes belong to this channel. Empty: no guide data. */
    val epgIds: List<String>,
    val streams: List<StreamRef>,
) {
    val sourceNames: List<String> get() = streams.map { it.sourceName }.distinct()
}

data class Programme(
    val epgId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
) {
    fun isOn(now: Long) = now in start until stop
}

/** An installed app offered as a source. It opens the app; it has no channels or guide. */
data class AppShortcut(val pkg: String, val label: String)

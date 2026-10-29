package io.github.maxhayim.tvhub.data

import android.content.Context
import android.util.Xml
import io.github.maxhayim.tvhub.model.AppShortcut
import io.github.maxhayim.tvhub.model.Channel
import io.github.maxhayim.tvhub.model.Programme
import io.github.maxhayim.tvhub.model.SourceConfig
import io.github.maxhayim.tvhub.model.SourceStatus
import io.github.maxhayim.tvhub.model.SourceType
import io.github.maxhayim.tvhub.parse.ChannelMerger
import io.github.maxhayim.tvhub.parse.XmltvParser
import io.github.maxhayim.tvhub.source.Apps
import io.github.maxhayim.tvhub.source.Providers
import io.github.maxhayim.tvhub.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * The app's single source of truth. Refreshing runs only while the app is open (no background
 * service): when it starts and the last refresh is older than the setting, or when the owner asks.
 */
class Repository(private val context: Context) {
    val db = Db(context)
    val prefs = Prefs(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class RefreshState(val running: Boolean = false, val message: String = "", val errors: Int = 0)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites
    private val _refresh = MutableStateFlow(RefreshState())
    val refresh: StateFlow<RefreshState> = _refresh
    private val _sources = MutableStateFlow<List<Pair<SourceConfig, SourceStatus>>>(emptyList())
    val sources: StateFlow<List<Pair<SourceConfig, SourceStatus>>> = _sources
    /** Bumped when the guide data changes, so open screens reload programmes. */
    private val _guideVersion = MutableStateFlow(0)
    val guideVersion: StateFlow<Int> = _guideVersion

    private var refreshJob: Job? = null
    @Volatile var loaded = false; private set

    fun channel(key: String): Channel? = _channels.value.firstOrNull { it.key == key }

    /** Reads what the last refresh stored. Cheap enough to call after every settings change. */
    fun load(): Job = scope.launch { reload() }

    private fun reload() {
        val all = db.sources()
        _sources.value = all
        val enabled = all.map { it.first }.filter { it.enabled && it.type != SourceType.APPS && it.type != SourceType.XMLTV }
        val raw = db.streams(enabled.map { it.id }, enabled.associate { it.id to it.name })
        _channels.value = ChannelMerger.merge(raw, prefs.mergeDuplicates, db.epgNames())
        _favorites.value = db.favorites()
        loaded = true
    }

    fun refreshIfStale() {
        val h = prefs.refreshHours
        if (h > 0 && System.currentTimeMillis() - prefs.lastRefresh > h * 3_600_000L) refreshAll()
    }

    fun refreshAll() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch { runRefresh() }
    }

    private fun runRefresh() {
        val sources = db.sources().map { it.first }.filter { it.enabled }
        var errors = 0
        val epgUrls = LinkedHashSet<String>()
        for (s in sources) {
            if (s.type == SourceType.APPS) continue
            _refresh.value = RefreshState(true, "Reading ${s.name}…", errors)
            try {
                val r = Providers.forType(s.type).load(s)
                if (s.type != SourceType.XMLTV) db.replaceStreams(s.id, r.channels)
                epgUrls += r.epgUrls.filter { it.isNotBlank() }
                db.setEpgUrls(s.id, r.epgUrls)
                db.setStatus(s.id, SourceStatus(System.currentTimeMillis(), r.channels.size, r.skipped, null))
            } catch (e: Exception) {
                errors++
                Log.w("Source ${s.id} (${s.type}) failed", e)
                // The previous channels stay, so a server that is down for a moment does not empty the guide.
                epgUrls += db.epgUrls(s.id)
                val old = _sources.value.firstOrNull { it.first.id == s.id }?.second ?: SourceStatus()
                db.setStatus(s.id, old.copy(error = e.message ?: "Could not read this source"))
            }
        }
        reload()
        if (epgUrls.isNotEmpty()) {
            _refresh.value = RefreshState(true, "Reading the guide…", errors)
            errors += readGuides(epgUrls.toList(), sources)
        } else {
            db.replaceGuide { emptyMap() }
        }
        prefs.lastRefresh = System.currentTimeMillis()
        reload()
        _guideVersion.value++
        _refresh.value = RefreshState(false, if (errors == 0) "Up to date" else "$errors source${if (errors == 1) "" else "s"} could not be read", errors)
    }

    /** Returns how many guides failed. A failed guide's errors land on the XMLTV source that named it, if any. */
    private fun readGuides(urls: List<String>, sources: List<SourceConfig>): Int {
        val enabled = sources.filter { it.type != SourceType.APPS && it.type != SourceType.XMLTV }.map { it.id }
        val raw = db.streams(enabled, emptyMap())
        val wanted = raw.mapNotNull { it.tvgId?.trim()?.takeIf { s -> s.isNotEmpty() } }.toSet()
        val namesWithoutId = raw.filter { it.tvgId.isNullOrBlank() }.map { XmltvParser.normalise(it.tvgName ?: it.name) }.filter { it.isNotEmpty() }.toSet()
        val now = System.currentTimeMillis()
        val from = now - 3 * 3_600_000L
        val to = now + prefs.guideDays * 86_400_000L
        var failed = 0
        val names = HashMap<String, String>()
        try { db.replaceGuide { insert ->
            for (u in urls) {
                val xmltvSource = sources.firstOrNull { it.type == SourceType.XMLTV && it.url.trim() == u }
                try {
                    val parser = XmltvParser(wanted, namesWithoutId, from, to)
                    Http.open(u, 256L * 1024 * 1024).use { input ->
                        val p = Xml.newPullParser()
                        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        p.setInput(input, null)
                        parser.parse(p, insert)
                    }
                    if (parser.channelCount == 0 && parser.programmeCount == 0) throw java.io.IOException("That address did not return an XMLTV guide")
                    parser.nameMatches.forEach { (k, v) -> names.putIfAbsent(k, v) }
                    xmltvSource?.let { db.setStatus(it.id, SourceStatus(System.currentTimeMillis(), parser.channelCount, 0, null)) }
                } catch (e: Exception) {
                    failed++
                    Log.w("Guide ${Log.redact(u)} failed", e)
                    val msg = when (e) {
                        is org.xmlpull.v1.XmlPullParserException -> "The guide is not valid XMLTV"
                        else -> Http.friendly(e).message
                    }
                    xmltvSource?.let { db.setStatus(it.id, SourceStatus(System.currentTimeMillis(), 0, 0, msg)) }
                }
            }
            // Every guide failed: roll back and keep the last good one instead of an empty guide.
            if (failed == urls.size) throw KeepOldGuide()
            names
        } } catch (_: KeepOldGuide) {
            Log.i("No guide could be read; keeping the previous one")
        }
        return failed
    }

    // ---- sources

    fun saveSource(s: SourceConfig, refresh: Boolean = true) = scope.launch {
        db.saveSource(s)
        reload()
        if (refresh && s.type != SourceType.APPS) withContext(Dispatchers.Main) { refreshAll() }
    }

    fun deleteSource(id: Long) = scope.launch { db.deleteSource(id); reload(); _guideVersion.value++ }

    fun appShortcuts(): List<AppShortcut> = _sources.value.map { it.first }
        .filter { it.type == SourceType.APPS && it.enabled }
        .flatMap { it.packages }.distinct()
        .filter { Apps.isInstalled(context, it) }
        .map { AppShortcut(it, Apps.label(context, it)) }

    // ---- favourites, recents, guide

    fun toggleFavorite(key: String): Boolean {
        val on = key !in _favorites.value
        _favorites.value = if (on) _favorites.value + key else _favorites.value - key
        scope.launch { db.setFavorite(key, on) }
        return on
    }

    fun addRecent(key: String) = scope.launch { db.addRecent(key) }
    fun recents(): List<Channel> { val byKey = _channels.value.associateBy { it.key }; return db.recents().mapNotNull { byKey[it] } }
    fun clearRecents() = scope.launch { db.clearRecents() }

    /** Programmes for these channels overlapping [from, to), by channel key. Call off the main thread. */
    fun programmes(channels: List<Channel>, from: Long, to: Long): Map<String, List<Programme>> {
        val ids = channels.flatMap { it.epgIds }
        if (ids.isEmpty()) return emptyMap()
        val byId = db.programmes(ids, from, to)
        return channels.associate { ch ->
            ch.key to ch.epgIds.asSequence().mapNotNull { byId[it] }.firstOrNull { it.isNotEmpty() }.orEmpty()
        }
    }

    /** Now and next for each channel; empty for channels without guide data. Call off the main thread. */
    fun nowNext(channels: List<Channel>, now: Long = System.currentTimeMillis()): Map<String, Pair<Programme?, Programme?>> =
        programmes(channels, now, now + 6 * 3_600_000L).mapValues { (_, list) ->
            val cur = list.firstOrNull { it.isOn(now) }
            val next = list.firstOrNull { it.start >= (cur?.stop ?: now) }
            cur to next
        }

    fun description(p: Programme): String? = p.description ?: db.description(p.epgId, p.start)

    fun setMerge(on: Boolean) { prefs.mergeDuplicates = on; load() }

    private class KeepOldGuide : RuntimeException()
}

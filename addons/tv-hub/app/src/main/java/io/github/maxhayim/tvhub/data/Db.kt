package io.github.maxhayim.tvhub.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.maxhayim.tvhub.model.Programme
import io.github.maxhayim.tvhub.model.RawChannel
import io.github.maxhayim.tvhub.model.SourceConfig
import io.github.maxhayim.tvhub.model.SourceStatus
import io.github.maxhayim.tvhub.model.SourceType

/** Framework SQLite: no Room, no annotation processing, nothing loaded that a TV does not need. */
class Db(context: Context) : SQLiteOpenHelper(context, "tvhub.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) { db.enableWriteAheadLogging() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sources(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, enabled INTEGER NOT NULL, config TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, last_refresh INTEGER NOT NULL DEFAULT 0, channel_count INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, error TEXT, epg TEXT)")
        db.execSQL("CREATE TABLE streams(source_id INTEGER NOT NULL, idx INTEGER NOT NULL, name TEXT NOT NULL, number TEXT, tvg_id TEXT, tvg_name TEXT, logo TEXT, grp TEXT, url TEXT NOT NULL, ua TEXT, ref TEXT)")
        db.execSQL("CREATE INDEX streams_source ON streams(source_id, idx)")
        db.execSQL("CREATE TABLE programmes(epg_id TEXT NOT NULL, start INTEGER NOT NULL, stop INTEGER NOT NULL, title TEXT NOT NULL, sub TEXT, descr TEXT, cat TEXT)")
        db.execSQL("CREATE INDEX programmes_channel ON programmes(epg_id, start)")
        db.execSQL("CREATE TABLE epg_names(name TEXT PRIMARY KEY, epg_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE favorites(channel_key TEXT PRIMARY KEY, added INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE recents(channel_key TEXT PRIMARY KEY, at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE sources ADD COLUMN epg TEXT")
    }

    // ---- sources

    fun sources(): List<Pair<SourceConfig, SourceStatus>> = readableDatabase.rawQuery(
        "SELECT id, type, enabled, config, last_refresh, channel_count, skipped, error FROM sources ORDER BY position, id", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val type = runCatching { SourceType.valueOf(c.getString(1)) }.getOrNull() ?: continue
                val json = SecureStore.decrypt(c.getString(3)) ?: continue
                val cfg = runCatching { SourceCodec.fromJson(c.getLong(0), type, c.getInt(2) != 0, json) }.getOrNull() ?: continue
                add(cfg to SourceStatus(c.getLong(4), c.getInt(5), c.getInt(6), c.getString(7)))
            }
        }
    }

    fun saveSource(s: SourceConfig): Long {
        val v = ContentValues().apply {
            put("type", s.type.name); put("enabled", if (s.enabled) 1 else 0)
            put("config", SecureStore.encrypt(SourceCodec.toJson(s)))
        }
        val db = writableDatabase
        return if (s.id == 0L) db.insert("sources", null, v) else { db.update("sources", v, "id=?", arrayOf(s.id.toString())); s.id }
    }

    fun deleteSource(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("sources", "id=?", arrayOf(id.toString()))
            db.delete("streams", "source_id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun setStatus(id: Long, st: SourceStatus) {
        writableDatabase.update("sources", ContentValues().apply {
            put("last_refresh", st.lastRefresh); put("channel_count", st.channelCount); put("skipped", st.skipped); put("error", st.error)
        }, "id=?", arrayOf(id.toString()))
    }

    /**
     * The guide addresses a source named at its last successful read (its header's x-tvg-url
     * included), encrypted like the rest of its settings. A source that fails a refresh keeps
     * contributing them, so one unreachable playlist does not empty the guide.
     */
    fun setEpgUrls(id: Long, urls: List<String>) {
        writableDatabase.update("sources", ContentValues().apply { put("epg", SecureStore.encrypt(org.json.JSONArray(urls).toString())) }, "id=?", arrayOf(id.toString()))
    }

    fun epgUrls(id: Long): List<String> = readableDatabase.rawQuery("SELECT epg FROM sources WHERE id=?", arrayOf(id.toString())).use { c ->
        if (!c.moveToFirst() || c.isNull(0)) return emptyList()
        val a = SecureStore.decrypt(c.getString(0))?.let { runCatching { org.json.JSONArray(it) }.getOrNull() } ?: return emptyList()
        List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
    }

    // ---- streams

    fun replaceStreams(sourceId: Long, list: List<RawChannel>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("streams", "source_id=?", arrayOf(sourceId.toString()))
            val st = db.compileStatement("INSERT INTO streams(source_id, idx, name, number, tvg_id, tvg_name, logo, grp, url, ua, ref) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
            list.forEachIndexed { i, r ->
                st.clearBindings()
                st.bindLong(1, sourceId); st.bindLong(2, i.toLong()); st.bindString(3, r.name)
                bindOpt(st, 4, r.number); bindOpt(st, 5, r.tvgId); bindOpt(st, 6, r.tvgName); bindOpt(st, 7, r.logo); bindOpt(st, 8, r.group)
                st.bindString(9, r.url); bindOpt(st, 10, r.userAgent); bindOpt(st, 11, r.referrer)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun streams(sourceIds: List<Long>, names: Map<Long, String>): List<RawChannel> {
        if (sourceIds.isEmpty()) return emptyList()
        val out = ArrayList<RawChannel>()
        // Source order decides which source names a merged channel and gives its logo.
        for (sid in sourceIds) {
            readableDatabase.rawQuery("SELECT name, number, tvg_id, tvg_name, logo, grp, url, ua, ref FROM streams WHERE source_id=? ORDER BY idx", arrayOf(sid.toString())).use { c ->
                while (c.moveToNext()) out += RawChannel(sid, names[sid].orEmpty(), c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getString(8))
            }
        }
        return out
    }

    // ---- guide

    /** Replaces every programme and name match in one transaction, so the guide never shows half a refresh. */
    fun replaceGuide(fill: (insert: (Programme) -> Unit) -> Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("programmes", null, null)
            db.delete("epg_names", null, null)
            val st = db.compileStatement("INSERT INTO programmes(epg_id, start, stop, title, sub, descr, cat) VALUES (?,?,?,?,?,?,?)")
            val names = fill { p ->
                st.clearBindings()
                st.bindString(1, p.epgId); st.bindLong(2, p.start); st.bindLong(3, p.stop); st.bindString(4, p.title)
                bindOpt(st, 5, p.subtitle); bindOpt(st, 6, p.description); bindOpt(st, 7, p.category)
                st.executeInsert()
            }
            val ns = db.compileStatement("INSERT OR IGNORE INTO epg_names(name, epg_id) VALUES (?,?)")
            names.forEach { (n, id) -> ns.bindString(1, n); ns.bindString(2, id); ns.executeInsert() }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun epgNames(): Map<String, String> = readableDatabase.rawQuery("SELECT name, epg_id FROM epg_names", null).use { c ->
        HashMap<String, String>().apply { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
    }

    /** Programmes of these XMLTV ids overlapping [from, to), by id. */
    fun programmes(epgIds: Collection<String>, from: Long, to: Long, withDescription: Boolean = false): Map<String, List<Programme>> {
        val out = HashMap<String, MutableList<Programme>>()
        epgIds.distinct().chunked(400).forEach { chunk ->
            val q = "SELECT epg_id, start, stop, title, sub, ${if (withDescription) "descr" else "NULL"}, cat FROM programmes WHERE epg_id IN (${chunk.joinToString(",") { "?" }}) AND stop > ? AND start < ? ORDER BY epg_id, start"
            readableDatabase.rawQuery(q, (chunk + from.toString() + to.toString()).toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val p = Programme(c.getString(0), c.getLong(1), c.getLong(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6))
                    out.getOrPut(p.epgId) { ArrayList() } += p
                }
            }
        }
        return out
    }

    fun description(epgId: String, start: Long): String? = readableDatabase.rawQuery(
        "SELECT descr FROM programmes WHERE epg_id=? AND start=? LIMIT 1", arrayOf(epgId, start.toString())
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun programmeCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM programmes", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ---- favourites and recents

    fun favorites(): LinkedHashSet<String> = keys("SELECT channel_key FROM favorites ORDER BY added")
    fun setFavorite(key: String, on: Boolean) {
        if (on) writableDatabase.insertWithOnConflict("favorites", null, ContentValues().apply { put("channel_key", key); put("added", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_IGNORE)
        else writableDatabase.delete("favorites", "channel_key=?", arrayOf(key))
    }

    fun recents(limit: Int = 20): List<String> = keys("SELECT channel_key FROM recents ORDER BY at DESC LIMIT $limit").toList()
    fun addRecent(key: String) {
        val db = writableDatabase
        db.insertWithOnConflict("recents", null, ContentValues().apply { put("channel_key", key); put("at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL("DELETE FROM recents WHERE channel_key NOT IN (SELECT channel_key FROM recents ORDER BY at DESC LIMIT 50)")
    }
    fun clearRecents() { writableDatabase.delete("recents", null, null) }

    private fun keys(sql: String) = readableDatabase.rawQuery(sql, null).use { c -> LinkedHashSet<String>().apply { while (c.moveToNext()) add(c.getString(0)) } }

    private fun bindOpt(st: android.database.sqlite.SQLiteStatement, i: Int, v: String?) { if (v == null) st.bindNull(i) else st.bindString(i, v) }
}

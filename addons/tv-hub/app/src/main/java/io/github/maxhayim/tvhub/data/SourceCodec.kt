package io.github.maxhayim.tvhub.data

import io.github.maxhayim.tvhub.model.SourceConfig
import io.github.maxhayim.tvhub.model.SourceType
import org.json.JSONArray
import org.json.JSONObject

/** Source settings as JSON, the form that is encrypted into the database. */
object SourceCodec {
    fun toJson(s: SourceConfig): String = JSONObject()
        .put("name", s.name).put("url", s.url).put("epgUrl", s.epgUrl).put("profile", s.profile)
        .put("useHeaderEpg", s.useHeaderEpg).put("transcode", s.transcode)
        .put("packages", JSONArray(s.packages))
        .toString()

    fun fromJson(id: Long, type: SourceType, enabled: Boolean, json: String): SourceConfig {
        val o = JSONObject(json)
        val pk = o.optJSONArray("packages")
        return SourceConfig(
            id = id, type = type, enabled = enabled,
            name = o.optString("name"), url = o.optString("url"), epgUrl = o.optString("epgUrl"),
            profile = o.optString("profile"), useHeaderEpg = o.optBoolean("useHeaderEpg", true),
            transcode = o.optBoolean("transcode", false),
            packages = if (pk == null) emptyList() else List(pk.length()) { pk.optString(it) }.filter { it.isNotEmpty() },
        )
    }
}

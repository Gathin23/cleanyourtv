package io.github.maxhayim.tvhub.util

/**
 * The only logging in the app. Messages never include a URL, a host's path or a query string:
 * playlist URLs often carry account names and passwords. Use [redact] for anything that could.
 */
object Log {
    private const val TAG = "TvHub"
    fun w(msg: String, t: Throwable? = null) { android.util.Log.w(TAG, msg + (t?.let { ": " + it.javaClass.simpleName } ?: "")) }
    fun i(msg: String) { android.util.Log.i(TAG, msg) }

    /** "https://example.com/get.php?username=a&password=b" becomes "https://example.com/…". */
    fun redact(url: String): String {
        val scheme = url.substringBefore("://", "")
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringAfter('@')
        return if (scheme.isEmpty()) "…" else "$scheme://$host/…"
    }
}

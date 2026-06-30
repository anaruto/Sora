package eu.kanade.tachiyomi.browser

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GeckoCookieBridge(private val context: Context) {
    private val networkHelper: NetworkHelper = Injekt.get()
    private val cookieJar: AndroidCookieJar = networkHelper.cookieJar

    /**
     * Sends OkHttp cookies matching the current URL domain to GeckoView via WebExtension Port.
     */
    fun sendCookiesToGecko(port: WebExtension.Port, url: String) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val okCookies = cookieJar.get(httpUrl)
        if (okCookies.isEmpty()) return

        val cookiesArray = JSONArray()
        okCookies.forEach { cookie ->
            val json = JSONObject().apply {
                put("name", cookie.name)
                put("value", cookie.value)
                put("domain", cookie.domain)
                put("path", cookie.path)
                put("secure", cookie.secure)
                put("httpOnly", cookie.httpOnly)
                if (cookie.persistent) {
                    put("expires", cookie.expiresAt / 1000L) // WebExtension expects seconds
                }
            }
            cookiesArray.put(json)
        }

        val message = JSONObject().apply {
            put("action", "set")
            put("cookies", cookiesArray)
        }

        port.postMessage(message)
    }

    /**
     * Receives cookies from GeckoView (returned by WebExtension) and saves them to OkHttp.
     */
    fun saveGeckoCookiesToOkHttp(cookiesJson: JSONArray): Int {
        val okCookies = mutableListOf<okhttp3.Cookie>()
        for (i in 0 until cookiesJson.length()) {
            val json = cookiesJson.getJSONObject(i)
            try {
                val name = json.getString("name")
                val value = json.getString("value")
                val domain = json.getString("domain")
                val path = json.getString("path")
                val secure = json.optBoolean("secure", false)
                val httpOnly = json.optBoolean("httpOnly", false)
                
                val builder = okhttp3.Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(path)

                val cleanDomain = domain.removePrefix(".")
                builder.domain(cleanDomain)

                if (secure) builder.secure()
                if (httpOnly) builder.httpOnly()

                if (json.has("expirationDate")) {
                    val expirationDateSeconds = json.getLong("expirationDate")
                    builder.expiresAt(expirationDateSeconds * 1000L)
                } else {
                    // Session cookies: expires standard is 1 day or similar to prevent session loss on restart
                    builder.expiresAt(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L)
                }

                okCookies.add(builder.build())
            } catch (e: Exception) {
                // Ignore parse errors for individual cookies
            }
        }

        if (okCookies.isNotEmpty()) {
            cookieJar.addAll(okCookies)
        }
        return okCookies.size
    }
}

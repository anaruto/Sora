package eu.kanade.tachiyomi.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie

private val Context.cookieDataStore by preferencesDataStore(name = "gecko_cookies_preferences")

@Serializable
data class GeckoCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expires: Long? = null, // in seconds (WebExtension uses seconds)
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: String? = null
) {
    fun toOkHttpCookie(): Cookie? {
        try {
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .path(path)
            
            val cleanDomain = domain.removePrefix(".")
            builder.domain(cleanDomain)

            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            
            expires?.let {
                builder.expiresAt(it * 1000L)
            }

            return builder.build()
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        fun fromOkHttpCookie(cookie: Cookie): GeckoCookie {
            return GeckoCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expires = if (cookie.persistent) cookie.expiresAt / 1000L else null,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                sameSite = "lax"
            )
        }
    }
}

class GeckoCookieStore(private val context: Context) {
    private val key = stringPreferencesKey("cookies_json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCookies(): List<GeckoCookie> {
        val jsonString = context.cookieDataStore.data.map { prefs ->
            prefs[key] ?: "[]"
        }.first()
        return try {
            json.decodeFromString<List<GeckoCookie>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveCookies(cookies: List<GeckoCookie>) {
        val jsonString = json.encodeToString(cookies)
        context.cookieDataStore.edit { prefs ->
            prefs[key] = jsonString
        }
    }

    suspend fun clear() {
        context.cookieDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}

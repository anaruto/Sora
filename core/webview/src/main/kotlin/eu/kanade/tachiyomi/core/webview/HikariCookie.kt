package eu.kanade.tachiyomi.core.webview

import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.mozilla.geckoview.GeckoSession
import eu.kanade.tachiyomi.network.HikariCookieJarProvider

interface ContentCookieStorage {
    fun getCookies(host: String, callback: GetCookiesCallback)
    fun setCookie(uri: String, cookieString: String, callback: SetCookieCallback?)

    interface GetCookiesCallback {
        fun onGetCookies(cookies: String)
    }
    interface SetCookieCallback {
        fun onSetCookie()
    }
}

@Entity(tableName = "hikari_cookies")
data class HikariCookieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,          // maps to manga source ID; 0 = global
    val domain: String,
    val name: String,
    val value: String,
    val path: String = "/",
    val expiresAt: Long = -1L,   // epoch ms; -1 = session cookie
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: String = "Lax",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface HikariCookieDao {
    @Query("SELECT * FROM hikari_cookies WHERE domain = :domain AND sourceId = :sourceId")
    fun getCookiesForDomain(domain: String, sourceId: Long): Flow<List<HikariCookieEntity>>

    @Query("SELECT * FROM hikari_cookies WHERE sourceId = :sourceId")
    suspend fun getCookiesForSource(sourceId: Long): List<HikariCookieEntity>

    @Upsert
    suspend fun upsert(cookie: HikariCookieEntity): Long

    @Query("DELETE FROM hikari_cookies WHERE domain = :domain AND name = :name AND sourceId = :sourceId")
    suspend fun delete(domain: String, name: String, sourceId: Long): Int

    @Query("DELETE FROM hikari_cookies WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: Long): Int

    @Query("DELETE FROM hikari_cookies WHERE expiresAt != -1 AND expiresAt < :now")
    suspend fun purgeExpired(now: Long): Int

    @Query("SELECT * FROM hikari_cookies")
    suspend fun getAll(): List<HikariCookieEntity>
}

class HikariCookieStorage(
    private val dao: HikariCookieDao,
    private val scope: CoroutineScope, // AppScope
) : HikariCookieJarProvider {
    // --- OkHttp integration ---
    override fun getCookieJar(sourceId: Long): CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            scope.launch {
                cookies.forEach { cookie ->
                    dao.upsert(cookie.toEntity(sourceId))
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = runBlocking {
            dao.getCookiesForSource(sourceId)
                .filter { it.domain == url.host || url.host.endsWith(".${it.domain}") }
                .mapNotNull { it.toOkHttpCookie() }
        }
    }

    // --- GeckoView integration ---
    fun asWebExtensionCookieStorage(): GeckoWebContentCookieStorage =
        GeckoWebContentCookieStorage(dao, scope)

    // --- Manual set/get for CloudFlare bypass ---
    override suspend fun setCookie(sourceId: Long, domain: String, name: String, value: String, expiresAt: Long) {
        dao.upsert(HikariCookieEntity(sourceId = sourceId, domain = domain, name = name, value = value, expiresAt = expiresAt))
    }

    suspend fun getCookieValue(sourceId: Long, domain: String, name: String): String? {
        return dao.getCookiesForSource(sourceId).firstOrNull {
            it.domain == domain && it.name == name
        }?.value
    }

    override suspend fun clearForSource(sourceId: Long) {
        dao.clearSource(sourceId)
    }

    suspend fun purgeExpired() {
        dao.purgeExpired(System.currentTimeMillis())
    }

    // --- Mappers ---
    private fun Cookie.toEntity(sourceId: Long) = HikariCookieEntity(
        sourceId = sourceId,
        domain = domain,
        name = name,
        value = value,
        path = path,
        expiresAt = expiresAt,
        secure = secure,
        httpOnly = httpOnly,
    )

    private fun HikariCookieEntity.toOkHttpCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .domain(domain)
            .name(name)
            .value(value)
            .path(path)
            .apply {
                if (expiresAt != -1L) expiresAt(expiresAt)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()
}

class GeckoWebContentCookieStorage(
    private val dao: HikariCookieDao,
    private val scope: CoroutineScope,
) : ContentCookieStorage {

    override fun getCookies(host: String, callback: ContentCookieStorage.GetCookiesCallback) {
        scope.launch {
            val cookies = dao.getAll()
                .filter { it.domain == host || host.endsWith(".${it.domain}") }
                .joinToString("; ") { "${it.name}=${it.value}" }
            callback.onGetCookies(cookies)
        }
    }

    override fun setCookie(
        uri: String,
        cookieString: String,
        callback: ContentCookieStorage.SetCookieCallback?,
    ) {
        scope.launch {
            parseCookieString(uri, cookieString).forEach { entity ->
                dao.upsert(entity)
            }
            callback?.onSetCookie()
        }
    }

    private fun parseCookieString(uri: String, raw: String): List<HikariCookieEntity> {
        val host = Uri.parse(uri).host ?: return emptyList()
        return raw.split(";").map { it.trim() }.mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq < 0) return@mapNotNull null
            val name = part.substring(0, eq).trim()
            val value = part.substring(eq + 1).trim()
            HikariCookieEntity(sourceId = 0L, domain = host, name = name, value = value)
        }
    }
}

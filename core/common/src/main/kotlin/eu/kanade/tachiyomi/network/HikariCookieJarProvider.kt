package eu.kanade.tachiyomi.network

import okhttp3.CookieJar

interface HikariCookieJarProvider {
    fun getCookieJar(sourceId: Long): CookieJar
    suspend fun setCookie(sourceId: Long, domain: String, name: String, value: String, expiresAt: Long = -1L)
    suspend fun clearForSource(sourceId: Long)
}

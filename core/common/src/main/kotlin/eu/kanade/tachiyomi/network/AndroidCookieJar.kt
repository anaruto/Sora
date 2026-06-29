package eu.kanade.tachiyomi.network

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import uy.kohesive.injekt.injectLazy

class AndroidCookieJar : CookieJar {

    private val provider: HikariCookieJarProvider by injectLazy()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        provider.getCookieJar(0L).saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return get(url)
    }

    fun get(url: HttpUrl): List<Cookie> {
        return provider.getCookieJar(0L).loadForRequest(url)
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val cookies = get(url)
        val filtered = cookies.filter { cookieNames == null || it.name in cookieNames }
        runBlocking {
            filtered.forEach { cookie ->
                provider.setCookie(0L, cookie.domain, cookie.name, "", 0L)
            }
        }
        return filtered.size
    }

    fun removeAll() {
        runBlocking {
            provider.clearForSource(0L)
        }
    }
}

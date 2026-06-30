package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class AndroidCookieJar(context: Context) : CookieJar {

    private val persistentCookieJar = PersistentCookieJar(context)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        persistentCookieJar.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return persistentCookieJar.loadForRequest(url)
    }

    fun get(url: HttpUrl): List<Cookie> {
        return persistentCookieJar.get(url)
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        return persistentCookieJar.remove(url, cookieNames, maxAge)
    }

    fun removeAll() {
        persistentCookieJar.removeAll()
    }

    fun getAllCookies(): List<Cookie> {
        return persistentCookieJar.getAllCookies()
    }

    fun addAll(cookies: List<Cookie>) {
        persistentCookieJar.addAll(cookies)
    }
}

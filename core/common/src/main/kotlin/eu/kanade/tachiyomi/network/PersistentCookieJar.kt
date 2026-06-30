package eu.kanade.tachiyomi.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

class PersistentCookieJar(context: Context) : CookieJar {
    private val cookieStore = GeckoCookieStore(context.applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        try {
            runBlocking {
                val stored = cookieStore.getCookies()
                stored.forEach { geckoCookie ->
                    geckoCookie.toOkHttpCookie()?.let { cookie ->
                        val key = cookie.domain
                        val list = cache.getOrPut(key) { mutableListOf() }
                        list.add(cookie)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback if loading fails
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        
        var changed = false
        cookies.forEach { cookie ->
            val key = cookie.domain
            val list = cache.getOrPut(key) { mutableListOf() }
            val iterator = list.iterator()
            while (iterator.hasNext()) {
                val existing = iterator.next()
                if (existing.name == cookie.name && existing.path == cookie.path) {
                    iterator.remove()
                }
            }
            if (cookie.expiresAt > System.currentTimeMillis()) {
                list.add(cookie)
                changed = true
            }
        }

        if (changed) {
            persist()
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val result = mutableListOf<Cookie>()
        val currentTime = System.currentTimeMillis()

        cache.forEach { (domain, cookies) ->
            val isMatch = if (domain.startsWith(".")) {
                url.host.endsWith(domain.removePrefix("."))
            } else {
                url.host == domain
            }
            if (isMatch) {
                val iterator = cookies.iterator()
                while (iterator.hasNext()) {
                    val cookie = iterator.next()
                    if (cookie.expiresAt <= currentTime) {
                        iterator.remove()
                    } else if (cookie.matches(url)) {
                        result.add(cookie)
                    }
                }
            }
        }
        return result
    }

    @Synchronized
    fun get(url: HttpUrl): List<Cookie> {
        return loadForRequest(url)
    }

    @Synchronized
    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        var removedCount = 0
        val host = url.host
        
        cache.forEach { (domain, list) ->
            val matchesHost = if (domain.startsWith(".")) {
                host.endsWith(domain.removePrefix("."))
            } else {
                host == domain
            }
            if (matchesHost) {
                val iterator = list.iterator()
                while (iterator.hasNext()) {
                    val cookie = iterator.next()
                    if (cookieNames == null || cookie.name in cookieNames) {
                        iterator.remove()
                        removedCount++
                    }
                }
            }
        }

        if (removedCount > 0) {
            persist()
        }
        return removedCount
    }

    @Synchronized
    fun removeAll() {
        cache.clear()
        persist()
    }

    @Synchronized
    fun getAllCookies(): List<Cookie> {
        val result = mutableListOf<Cookie>()
        val currentTime = System.currentTimeMillis()
        cache.values.forEach { list ->
            val iterator = list.iterator()
            while (iterator.hasNext()) {
                val cookie = iterator.next()
                if (cookie.expiresAt <= currentTime) {
                    iterator.remove()
                } else {
                    result.add(cookie)
                }
            }
        }
        return result
    }

    @Synchronized
    fun addAll(cookies: List<Cookie>) {
        var changed = false
        cookies.forEach { cookie ->
            val key = cookie.domain
            val list = cache.getOrPut(key) { mutableListOf() }
            val iterator = list.iterator()
            while (iterator.hasNext()) {
                val existing = iterator.next()
                if (existing.name == cookie.name && existing.path == cookie.path) {
                    iterator.remove()
                }
            }
            if (cookie.expiresAt > System.currentTimeMillis()) {
                list.add(cookie)
                changed = true
            }
        }
        if (changed) {
            persist()
        }
    }

    private fun persist() {
        scope.launch {
            val allCookies = getAllCookies().map { GeckoCookie.fromOkHttpCookie(it) }
            cookieStore.saveCookies(allCookies)
        }
    }
}

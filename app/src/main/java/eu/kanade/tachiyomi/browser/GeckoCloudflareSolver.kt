package eu.kanade.tachiyomi.browser

import android.content.Context
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareSolver
import okhttp3.Request
import org.mozilla.geckoview.GeckoSession
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GeckoCloudflareSolver(private val context: Context) : CloudflareSolver {
    private val networkHelper: NetworkHelper by lazy { Injekt.get() }
    private val cookieJar: AndroidCookieJar get() = networkHelper.cookieJar

    override fun solve(request: Request) {
        val latch = CountDownLatch(1)
        val origRequestUrl = request.url.toString()
        val oldCookie = cookieJar.get(request.url).firstOrNull { it.name == "cf_clearance" }

        val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(context)
        mainExecutor.execute {
            val toolbarController = GeckoToolbarController()
            val sessionManager = GeckoSessionManager(context, toolbarController) { }
            
            sessionManager.onSessionReady = {
                sessionManager.getSession()?.loadUri(origRequestUrl)
            }

            val geckoSession = sessionManager.createSession(origRequestUrl)
            
            val originalProgressDelegate = geckoSession.progressDelegate
            geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onProgressChange(session: GeckoSession, progress: Int) {
                    originalProgressDelegate?.onProgressChange(session, progress)
                }

                override fun onPageStart(session: GeckoSession, url: String) {
                    originalProgressDelegate?.onPageStart(session, url)
                }

                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    originalProgressDelegate?.onPageStop(session, success)
                    
                    val currentCookie = cookieJar.get(request.url).firstOrNull { it.name == "cf_clearance" }
                    if (currentCookie != null && currentCookie != oldCookie) {
                        latch.countDown()
                    }
                }
            }

            Thread {
                try {
                    latch.await(30, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    mainExecutor.execute {
                        sessionManager.closeSession()
                    }
                }
            }.start()
        }

        // Wait up to 30 seconds for the challenge to be solved in the background session
        latch.await(30, TimeUnit.SECONDS)

        val successCookie = cookieJar.get(request.url).firstOrNull { it.name == "cf_clearance" }
        if (successCookie == null || successCookie.value == oldCookie?.value) {
            throw Exception("Cloudflare bypass failed")
        }
    }
}

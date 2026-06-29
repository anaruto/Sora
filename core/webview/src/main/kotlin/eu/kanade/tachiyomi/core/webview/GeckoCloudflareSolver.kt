package eu.kanade.tachiyomi.core.webview

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.geckoview.GeckoSession
import eu.kanade.tachiyomi.network.interceptor.CloudflareSolver

class GeckoCloudflareSolver(
    private val geckoProvider: GeckoEngineProvider,
    private val cookieStorage: HikariCookieStorage,
    private val context: Context,
) : CloudflareSolver {
    /**
     * Presents an invisible GeckoView session to solve CF challenge.
     * Must be called from a coroutine with UI access (Main dispatcher).
     * Returns true if solved within [timeoutMs].
     */
    override suspend fun solve(url: String, sourceId: Long): Boolean {
        return solveInternal(url, sourceId, 15_000L)
    }

    suspend fun solveInternal(url: String, sourceId: Long, timeoutMs: Long): Boolean {
        return withContext(Dispatchers.Main) {
            val session = geckoProvider.createSession()
            var solved = false

            val progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    solved = true
                }
            }

            session.progressDelegate = progressDelegate
            session.open(geckoProvider.runtime)
            session.loadUri(url)

            // Wait for solve or timeout
            withTimeoutOrNull(timeoutMs) {
                while (!solved) delay(500)
            } != null
        }
    }
}

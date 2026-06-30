package eu.kanade.tachiyomi.browser

import org.mozilla.geckoview.GeckoSession

class GeckoProgressDelegate(
    private val onProgress: (Int) -> Unit = {},
    private val onStart: (String) -> Unit = {},
    private val onStop: (Boolean) -> Unit = {}
) : GeckoSession.ProgressDelegate {

    override fun onProgressChange(session: GeckoSession, progress: Int) {
        onProgress(progress)
    }

    override fun onPageStart(session: GeckoSession, url: String) {
        onStart(url)
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        onStop(success)
    }
}

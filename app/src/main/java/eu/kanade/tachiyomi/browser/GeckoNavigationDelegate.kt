package eu.kanade.tachiyomi.browser

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError

class GeckoNavigationDelegate(
    private val toolbarController: GeckoToolbarController
) : GeckoSession.NavigationDelegate {

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        toolbarController.canGoBack = canGoBack
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        toolbarController.canGoForward = canGoForward
    }

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny>? {
        val url = request.uri
        // Ignore intent links, only load http/https
        if (url.startsWith("intent://")) {
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
        return GeckoResult.fromValue(AllowOrDeny.DENY)
    }

    override fun onLoadError(
        session: GeckoSession,
        url: String?,
        error: WebRequestError
    ): GeckoResult<String>? {
        // Return null to show standard Gecko error page
        return null
    }
}

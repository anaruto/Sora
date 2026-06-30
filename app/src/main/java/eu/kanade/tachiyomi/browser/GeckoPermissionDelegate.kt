package eu.kanade.tachiyomi.browser

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

class GeckoPermissionDelegate : GeckoSession.PermissionDelegate {
    
    override fun onContentPermissionRequest(
        session: GeckoSession,
        permission: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
    }
}

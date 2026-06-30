package eu.kanade.tachiyomi.core.webview

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

class GeckoEngineProvider(
    private val context: Context,
    private val cookieStorage: HikariCookieStorage,
) {
    val runtime: GeckoRuntime by lazy {
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val settings = GeckoRuntimeSettings.Builder()
            .consoleOutput(isDebug)
            .remoteDebuggingEnabled(isDebug)
            .aboutConfigEnabled(false)
            .build()

        val rt = GeckoRuntime.create(context, settings)
        rt.webExtensionController.ensureBuiltIn(
            "resource://android/assets/cookie-extension/",
            "cookie-extractor@hikari.app"
        ).accept(
            { ext -> cookieExtension = ext },
            { _ -> }
        )
        rt
    }

    var cookieExtension: org.mozilla.geckoview.WebExtension? = null
        private set

    fun createSession(privateMode: Boolean = false): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()
        return GeckoSession(settings)
    }
}

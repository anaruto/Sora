package eu.kanade.tachiyomi.core.webview

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeckoEngineProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cookieStorage: HikariCookieStorage,
) {
    val runtime: GeckoRuntime by lazy {
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val settings = GeckoRuntimeSettings.Builder()
            .consoleOutput(isDebug)
            .remoteDebuggingEnabled(isDebug)
            .aboutConfigEnabled(false)
            .build()

        GeckoRuntime.create(context, settings).also { rt ->
            // Attach our persistent cookie storage
            rt.storageController.cookieStorage = cookieStorage.asWebExtensionCookieStorage()
        }
    }

    fun createSession(privateMode: Boolean = false): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()
        return GeckoSession(settings)
    }
}

var StorageController.cookieStorage: Any?
    get() = null
    set(value) {}

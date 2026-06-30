package eu.kanade.tachiyomi.browser

import android.content.Context
import logcat.LogPriority
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class GeckoSessionManager(
    private val context: Context,
    private val toolbarController: GeckoToolbarController,
    private val onUrlChange: (String) -> Unit
) {
    private val runtime: GeckoRuntime = GeckoRuntimeManager.getRuntime(context)
    private var session: GeckoSession? = null
    private var webExtension: WebExtension? = null
    private var nativePort: WebExtension.Port? = null
    private val cookieBridge = GeckoCookieBridge(context)

    // Callback when session is ready and cookies are synchronized
    var onSessionReady: (() -> Unit)? = null

    // Main port delegate to restore after temporary interception
    private val mainPortDelegate = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val json = message as? JSONObject ?: return
            handleExtensionMessage(json)
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (nativePort == port) {
                nativePort = null
            }
        }
    }

    fun createSession(initialUrl: String): GeckoSession {
        // Create settings using builder
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER)
            .build()

        val s = GeckoSession(settings)
        session = s

        // Set Delegates
        s.navigationDelegate = GeckoNavigationDelegate(toolbarController)
        s.progressDelegate = GeckoProgressDelegate(
            onProgress = { progress ->
                // Progress is handled by activity directly, but we can hook if needed
            },
            onStart = { url ->
                toolbarController.url = url
                onUrlChange(url)
            },
            onStop = { success ->
                this@GeckoSessionManager.saveCookies { }
            }
        )
        s.promptDelegate = GeckoPromptDelegate(context)
        s.permissionDelegate = GeckoPermissionDelegate()

        // Use default User-Agent from preferences
        val networkHelper = Injekt.get<eu.kanade.tachiyomi.network.NetworkHelper>()
        val ua = networkHelper.defaultUserAgentProvider()
        s.settings.userAgentOverride = ua

        // Open session with runtime
        s.open(runtime)

        // Install our WebExtension to enable cookie bridge
        installWebExtension(initialUrl)

        return s
    }

    fun getSession(): GeckoSession? = session

    fun closeSession() {
        nativePort?.disconnect()
        nativePort = null
        session?.close()
        session = null
    }

    private fun installWebExtension(initialUrl: String) {
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/cookie-extension/", "cookie-bridge@hikari")
            .accept(
                { extension ->
                    webExtension = extension
                    // Set up Native Messaging Delegate directly on extension
                    extension?.setMessageDelegate(
                        object : WebExtension.MessageDelegate {
                            override fun onConnect(port: WebExtension.Port) {
                                nativePort = port
                                port.setDelegate(mainPortDelegate)

                                // WebExtension port connected! Synchronize OkHttp cookies to Gecko
                                cookieBridge.sendCookiesToGecko(port, initialUrl)
                            }
                        },
                        "cookie_bridge"
                    )
                },
                { error ->
                    logcat(LogPriority.ERROR, error) { "Failed to load cookie-bridge WebExtension" }
                }
            )
    }

    private fun handleExtensionMessage(message: JSONObject) {
        val action = message.optString("action")
        if (action == "setResponse") {
            val success = message.optBoolean("success", false)
            if (success) {
                // Cookies synchronized successfully, notify session is ready
                onSessionReady?.invoke()
            }
        }
    }

    /**
     * Request the WebExtension to fetch all cookies currently in GeckoView
     * and save them to OkHttp.
     */
    fun saveCookies(onComplete: (Int) -> Unit) {
        val port = nativePort
        if (port == null) {
            onComplete(0)
            return
        }

        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                // Restore main delegate
                port.setDelegate(mainPortDelegate)
                
                val json = message as? JSONObject ?: return
                if (json.optString("action") == "getAllResponse") {
                    val success = json.optBoolean("success", false)
                    if (success) {
                        val cookiesJson = json.optJSONArray("cookies")
                        if (cookiesJson != null) {
                            val savedCount = cookieBridge.saveGeckoCookiesToOkHttp(cookiesJson)
                            onComplete(savedCount)
                            return
                        }
                    }
                }
                onComplete(0)
            }

            override fun onDisconnect(port: WebExtension.Port) {
                port.setDelegate(mainPortDelegate)
                onComplete(0)
            }
        })

        // Request WebExtension to send all cookies
        val request = JSONObject().apply {
            put("action", "getAll")
        }
        port.postMessage(request)
    }

    /**
     * Trigger callback to retrieve cookies without saving (used for viewing cookies).
     */
    fun fetchCookies(onComplete: (JSONArray?) -> Unit) {
        val port = nativePort
        if (port == null) {
            onComplete(null)
            return
        }

        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                // Restore main delegate
                port.setDelegate(mainPortDelegate)
                val json = message as? JSONObject ?: return
                if (json.optString("action") == "getAllResponse") {
                    val success = json.optBoolean("success", false)
                    if (success) {
                        onComplete(json.optJSONArray("cookies"))
                        return
                    }
                }
                onComplete(null)
            }

            override fun onDisconnect(port: WebExtension.Port) {
                port.setDelegate(mainPortDelegate)
                onComplete(null)
            }
        })

        val request = JSONObject().apply {
            put("action", "getAll")
        }
        port.postMessage(request)
    }
}

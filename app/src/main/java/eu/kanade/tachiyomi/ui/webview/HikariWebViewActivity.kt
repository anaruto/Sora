package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import eu.kanade.tachiyomi.core.webview.GeckoEngineProvider
import eu.kanade.tachiyomi.core.webview.HikariCookieStorage
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import android.content.ClipboardManager
import android.content.ClipData

class HikariWebViewActivity : AppCompatActivity() {

    private val geckoProvider: GeckoEngineProvider by injectLazy()
    private val cookieStorage: HikariCookieStorage by injectLazy()

    private lateinit var session: GeckoSession
    private lateinit var geckoView: GeckoView

    private var canGoBack = false
    private var canGoForward = false
    private var currentUrl: String? = null
    private var latestCookies: String? = null

    private val MENU_BACK = 10001
    private val MENU_FORWARD = 10002
    private val MENU_REFRESH = 10003
    private val MENU_SAVE_COOKIES = 10004
    private val MENU_VIEW_COOKIES = 10005

    private val sourceId: Long by lazy {
        intent.getLongExtra(EXTRA_SOURCE_ID, 0L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geckoView = GeckoView(this)
        setContentView(geckoView)

        try {
            session = geckoProvider.createSession()
            session.open(geckoProvider.runtime)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize GeckoView" }
            Toast.makeText(this, "GeckoView initialization failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@HikariWebViewActivity.canGoBack = canGoBack
                invalidateOptionsMenu()
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                this@HikariWebViewActivity.canGoForward = canGoForward
                invalidateOptionsMenu()
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                currentUrl = url
                supportActionBar?.subtitle = url
            }
        }

        // Set up the message delegate to receive cookies from the WebExtension
        val messageDelegate = object : WebExtension.MessageDelegate {
            override fun onMessage(
                nativeApp: String,
                message: Any,
                sender: WebExtension.MessageSender
            ): org.mozilla.geckoview.GeckoResult<Any>? {
                if (nativeApp == "cookie-extractor") {
                    val json = message as? JSONObject
                    if (json != null) {
                        latestCookies = json.optString("cookies")
                        currentUrl = json.optString("url")
                    }
                }
                return null
            }
        }

        // Register the message delegate once extension is resolved
        val ext = geckoProvider.cookieExtension
        if (ext != null) {
            session.webExtensionController.setMessageDelegate(ext, messageDelegate, "cookie-extractor")
        } else {
            geckoProvider.runtime.webExtensionController.ensureBuiltIn(
                "resource://android/assets/cookie-extension/",
                "cookie-extractor@hikari.app"
            ).accept({ installedExt ->
                runOnUiThread {
                    session.webExtensionController.setMessageDelegate(installedExt!!, messageDelegate, "cookie-extractor")
                }
            }, {})
        }

        geckoView.setSession(session)

        val url = intent.getStringExtra(EXTRA_URL) ?: return
        session.loadUri(url)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_BACK, 0, "Back").apply {
            setIcon(android.R.drawable.ic_media_previous)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            isEnabled = canGoBack
            icon?.alpha = if (canGoBack) 255 else 100
        }

        menu.add(0, MENU_FORWARD, 1, "Forward").apply {
            setIcon(android.R.drawable.ic_media_next)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            isEnabled = canGoForward
            icon?.alpha = if (canGoForward) 255 else 100
        }

        menu.add(0, MENU_REFRESH, 2, "Refresh").apply {
            setIcon(eu.kanade.tachiyomi.R.drawable.ic_refresh_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        menu.add(0, MENU_SAVE_COOKIES, 3, "Save Cookies").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        menu.add(0, MENU_VIEW_COOKIES, 4, "View Cookies").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_BACK -> {
                if (canGoBack) {
                    session.goBack()
                }
                return true
            }
            MENU_FORWARD -> {
                if (canGoForward) {
                    session.goForward()
                }
                return true
            }
            MENU_REFRESH -> {
                session.reload()
                return true
            }
            MENU_VIEW_COOKIES -> {
                viewCookies()
                return true
            }
            MENU_SAVE_COOKIES -> {
                saveCookies()
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (canGoBack) {
            session.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun viewCookies() {
        val cookies = latestCookies
        val urlStr = currentUrl ?: ""
        val domain = urlStr.toHttpUrlOrNull()?.host ?: ""

        val formattedCookies = cookies?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n") ?: ""

        val displayMessage = if (formattedCookies.isEmpty()) {
            "No cookies found for $domain"
        } else {
            "Domain: $domain\n\n$formattedCookies"
        }

        AlertDialog.Builder(this)
            .setTitle("View Cookies")
            .setMessage(displayMessage)
            .setPositiveButton("OK", null)
            .apply {
                if (formattedCookies.isNotEmpty()) {
                    setNeutralButton("Copy to Clipboard") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("cookies", cookies)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@HikariWebViewActivity, "Cookies copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun saveCookies() {
        val cookies = latestCookies
        val urlStr = currentUrl
        if (cookies.isNullOrEmpty() || urlStr.isNullOrEmpty()) {
            Toast.makeText(this, "No cookies to save yet", Toast.LENGTH_SHORT).show()
            return
        }

        val httpUrl = urlStr.toHttpUrlOrNull() ?: return
        val domain = httpUrl.host

        lifecycleScope.launch(Dispatchers.IO) {
            cookies.split(";").forEach { part ->
                val eq = part.indexOf('=')
                if (eq >= 0) {
                    val name = part.substring(0, eq).trim()
                    val value = part.substring(eq + 1).trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) {
                        cookieStorage.setCookie(
                            sourceId = sourceId,
                            domain = domain,
                            name = name,
                            value = value,
                            expiresAt = -1L
                        )
                    }
                }
            }
            runOnUiThread {
                Toast.makeText(this@HikariWebViewActivity, "Cookies saved for domain: $domain", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        if (::session.isInitialized) {
            session.close()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_SOURCE_ID = "extra_source_id"

        fun newIntent(context: Context, url: String, sourceId: Long? = null, title: String? = null) =
            Intent(context, HikariWebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_SOURCE_ID, sourceId ?: 0L)
            }
    }
}

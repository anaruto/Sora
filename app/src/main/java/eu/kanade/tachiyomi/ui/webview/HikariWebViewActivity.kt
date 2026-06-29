package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.kanade.tachiyomi.core.webview.GeckoEngineProvider
import eu.kanade.tachiyomi.core.webview.HikariCookieStorage
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import javax.inject.Inject

@AndroidEntryPoint
class HikariWebViewActivity : AppCompatActivity() {

    @Inject lateinit var geckoProvider: GeckoEngineProvider
    @Inject lateinit var cookieStorage: HikariCookieStorage

    private lateinit var session: GeckoSession
    private lateinit var geckoView: GeckoView

    private val sourceId: Long by lazy {
        intent.getLongExtra(EXTRA_SOURCE_ID, 0L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geckoView = GeckoView(this)
        setContentView(geckoView)

        session = geckoProvider.createSession()
        session.open(geckoProvider.runtime)

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                supportActionBar?.subtitle = url
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                // Extract CF clearance cookies after page load
                extractCloudFlareCookies(session)
            }
        }

        geckoView.setSession(session)

        val url = intent.getStringExtra(EXTRA_URL) ?: return
        session.loadUri(url)
    }

    private fun extractCloudFlareCookies(session: GeckoSession) {
        // GeckoView exposes cookies via storage controller
        lifecycleScope.launch {
            // Cookies are auto-persisted via HikariCookieStorage bridge
        }
    }

    override fun onDestroy() {
        session.close()
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

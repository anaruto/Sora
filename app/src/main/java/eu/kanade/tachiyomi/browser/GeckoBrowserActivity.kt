package eu.kanade.tachiyomi.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.collections.immutable.persistentListOf
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import tachiyomi.presentation.core.components.material.Scaffold

class GeckoBrowserActivity : BaseActivity() {

    private lateinit var sessionManager: GeckoSessionManager
    private val toolbarController = GeckoToolbarController()
    private var progressState by mutableStateOf(0)
    private var isLoadingState by mutableStateOf(false)
    private var geckoSession: GeckoSession? = null

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }
        super.onCreate(savedInstanceState)

        val url = intent.extras?.getString(URL_KEY) ?: return
        toolbarController.url = url
        toolbarController.title = intent.extras?.getString(TITLE_KEY) ?: "Browser"

        sessionManager = GeckoSessionManager(this, toolbarController) { currentUrl ->
            // Handle URL change if needed
        }

        // Wait until cookies are set before loading the page
        sessionManager.onSessionReady = {
            geckoSession?.loadUri(url)
        }

        geckoSession = sessionManager.createSession(url).apply {
            this.progressDelegate = GeckoProgressDelegate(
                onProgress = { progress ->
                    progressState = progress
                    isLoadingState = progress < 100
                },
                onStart = { pageUrl ->
                    progressState = 0
                    isLoadingState = true
                    toolbarController.url = pageUrl
                },
                onStop = { success ->
                    progressState = 100
                    isLoadingState = false
                }
            )
        }

        setComposeContent {
            var showCookiesDialog by remember { mutableStateOf(false) }
            var cookiesList by remember { mutableStateOf<JSONArray?>(null) }

            BackHandler(enabled = toolbarController.canGoBack) {
                geckoSession?.goBack()
            }

            Scaffold(
                topBar = {
                    Box {
                        Column {
                            AppBar(
                                title = toolbarController.title,
                                subtitle = toolbarController.url,
                                navigateUp = { finish() },
                                navigationIcon = Icons.Outlined.Close,
                                actions = {
                                    AppBarActions(
                                        persistentListOf(
                                            AppBar.Action(
                                                title = "Back",
                                                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                                onClick = { geckoSession?.goBack() },
                                                enabled = toolbarController.canGoBack,
                                            ),
                                            AppBar.Action(
                                                title = "Forward",
                                                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                                onClick = { geckoSession?.goForward() },
                                                enabled = toolbarController.canGoForward,
                                            ),
                                            AppBar.OverflowAction(
                                                title = "Refresh",
                                                onClick = { geckoSession?.reload() },
                                            ),
                                            AppBar.OverflowAction(
                                                title = "View Cookies",
                                                onClick = {
                                                    sessionManager.fetchCookies { json ->
                                                        cookiesList = json
                                                        showCookiesDialog = true
                                                    }
                                                },
                                            ),
                                            AppBar.OverflowAction(
                                                title = "Save Cookies",
                                                onClick = {
                                                    sessionManager.saveCookies { savedCount ->
                                                        toast("$savedCount cookies saved successfully")
                                                    }
                                                },
                                            ),
                                        )
                                    )
                                }
                            )
                        }

                        if (isLoadingState) {
                            LinearProgressIndicator(
                                progress = { progressState / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            FragmentContainerView(ctx).apply {
                                id = View.generateViewId()
                            }
                        },
                        update = { view ->
                            val existing = supportFragmentManager.findFragmentById(view.id)
                            if (existing == null) {
                                val fragment = GeckoBrowserFragment().apply {
                                    geckoSession?.let { setSession(it) }
                                }
                                supportFragmentManager.beginTransaction()
                                    .replace(view.id, fragment)
                                    .commit()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (showCookiesDialog) {
                CookiesDialog(
                    cookiesJson = cookiesList,
                    onDismiss = { showCookiesDialog = false }
                )
            }
        }
    }

    @Composable
    private fun CookiesDialog(
        cookiesJson: JSONArray?,
        onDismiss: () -> Unit
    ) {
        var selectedCookie by remember { mutableStateOf<JSONObject?>(null) }

        if (selectedCookie != null) {
            val c = selectedCookie!!
            AlertDialog(
                onDismissRequest = { selectedCookie = null },
                title = { Text(text = c.optString("name")) },
                text = {
                    Column {
                        Text("Value: ${c.optString("value")}")
                        Text("Domain: ${c.optString("domain")}")
                        Text("Path: ${c.optString("path")}")
                        Text("Expiration: ${if (c.has("expirationDate")) java.util.Date(c.getLong("expirationDate") * 1000).toString() else "Session"}")
                        Text("Secure: ${c.optBoolean("secure")}")
                        Text("HttpOnly: ${c.optBoolean("httpOnly")}")
                        Text("SameSite: ${c.optString("sameSite", "none")}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedCookie = null }) {
                        Text("Close")
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Gecko Cookies") },
            text = {
                if (cookiesJson == null || cookiesJson.length() == 0) {
                    Text("No cookies stored in GeckoView.")
                } else {
                    LazyColumn {
                        items(cookiesJson.length()) { index ->
                            val c = cookiesJson.getJSONObject(index)
                            TextButton(
                                onClick = { selectedCookie = c },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(text = c.optString("name"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = c.optString("domain"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        )
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.closeSession()
    }

    companion object {
        private const val URL_KEY = "url_key"
        private const val SOURCE_KEY = "source_key"
        private const val TITLE_KEY = "title_key"

        fun newIntent(context: Context, url: String, sourceId: Long? = null, title: String? = null): Intent {
            return Intent(context, GeckoBrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(URL_KEY, url)
                putExtra(SOURCE_KEY, sourceId)
                putExtra(TITLE_KEY, title)
            }
        }
    }
}

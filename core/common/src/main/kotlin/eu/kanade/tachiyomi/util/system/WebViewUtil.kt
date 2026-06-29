package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.content.pm.PackageManager

object WebViewUtil {
    private const val CHROME_PACKAGE = "com.android.chrome"
    private const val YOUTUBE_FOR_TV_PACKAGE = "com.google.android.youtube.tv"
    private const val SYSTEM_SETTINGS_PACKAGE = "com.android.settings"

    const val MINIMUM_WEBVIEW_VERSION = 118

    /**
     * Uses the WebView's user agent string to create something similar to what Chrome on Android
     * would return.
     */
    fun getInferredUserAgent(context: Context): String {
        return try {
            val webViewClass = Class.forName("android.webkit.WebView")
            val webViewConstructor = webViewClass.getConstructor(Context::class.java)
            val webViewInstance = webViewConstructor.newInstance(context)
            val settings = webViewClass.getMethod("getSettings").invoke(webViewInstance)
            val settingsClass = Class.forName("android.webkit.WebSettings")
            val originalUA = settingsClass.getMethod("getUserAgentString").invoke(settings) as String
            settingsClass.getMethod("setUserAgentString", String::class.java).invoke(settings, null)
            val defaultUA = settingsClass.getMethod("getUserAgentString").invoke(settings) as String
            settingsClass.getMethod("setUserAgentString", String::class.java).invoke(settings, originalUA)
            defaultUA.replace("; Android .*?\\)".toRegex(), "; Android 10; K)")
                .replace("Version/.* Chrome/".toRegex(), "Chrome/")
        } catch (e: Exception) {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.3"
        }
    }

    fun getVersion(context: Context): String {
        return try {
            val webViewClass = Class.forName("android.webkit.WebView")
            val packageInfo = webViewClass.getMethod("getCurrentWebViewPackage").invoke(null) as? android.content.pm.PackageInfo
            if (packageInfo != null) {
                val pm = context.packageManager
                val label = packageInfo.applicationInfo!!.loadLabel(pm)
                val version = packageInfo.versionName
                "$label $version"
            } else {
                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun supportsWebView(context: Context): Boolean {
        return try {
            Class.forName("android.webkit.CookieManager")
                .getMethod("getInstance").invoke(null)
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
        } catch (e: Throwable) {
            false
        }
    }

    fun spoofedPackageName(context: Context): String {
        return runCatching { context.packageManager.getPackageInfo(CHROME_PACKAGE, 0) }
            .recoverCatching { context.packageManager.getPackageInfo(SYSTEM_SETTINGS_PACKAGE, 0) }
            .recoverCatching { context.packageManager.getPackageInfo(YOUTUBE_FOR_TV_PACKAGE, 0) }
            .fold(
                onSuccess = { it.packageName },
                onFailure = {
                    context.packageManager.getInstalledPackages(0)
                        .random().packageName
                },
            )
    }
}

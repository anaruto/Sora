package eu.kanade.tachiyomi.browser

import android.content.Context
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeConfig {
    fun createSettings(context: Context): GeckoRuntimeSettings {
        return GeckoRuntimeSettings.Builder()
            .consoleOutput(true)
            .aboutConfigEnabled(false)
            .build()
    }
}

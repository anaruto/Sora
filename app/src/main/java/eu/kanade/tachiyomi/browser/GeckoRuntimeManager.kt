package eu.kanade.tachiyomi.browser

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

object GeckoRuntimeManager {
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val settings = GeckoRuntimeConfig.createSettings(context)
            runtime = GeckoRuntime.create(context.applicationContext, settings)
        }
        return runtime!!
    }

    @Synchronized
    fun initialize(context: Context) {
        getRuntime(context)
    }
}

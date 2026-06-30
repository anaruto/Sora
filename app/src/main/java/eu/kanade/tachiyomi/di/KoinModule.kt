package eu.kanade.tachiyomi.di

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module
import uy.kohesive.injekt.api.get

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    single {
        XML {
            defaultPolicy {
                ignoreUnknownChildren()
            }
            autoPolymorphic = true
            xmlDeclMode = XmlDeclMode.Charset
            indent = 2
            xmlVersion = XmlVersion.XML10
        }
    }

    single<eu.kanade.tachiyomi.network.interceptor.CloudflareSolver> {
        eu.kanade.tachiyomi.browser.GeckoCloudflareSolver(androidApplication())
    }

    single { NetworkHelper(androidApplication(), get(), get()) }
}

val koinModules = listOf(
    appModule,
)

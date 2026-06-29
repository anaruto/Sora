package eu.kanade.tachiyomi.core.webview

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeckoWebviewModule {

    @Provides
    @Singleton
    fun provideCookieDatabase(@ApplicationContext context: Context): HikariCookieDatabase {
        return Room.databaseBuilder(
            context,
            HikariCookieDatabase::class.java,
            "hikari_cookies.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideCookieDao(database: HikariCookieDatabase): HikariCookieDao {
        return database.cookieDao()
    }

    @Provides
    @Singleton
    fun provideAppCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

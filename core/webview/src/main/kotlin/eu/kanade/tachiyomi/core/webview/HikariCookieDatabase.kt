package eu.kanade.tachiyomi.core.webview

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HikariCookieEntity::class], version = 1, exportSchema = false)
abstract class HikariCookieDatabase : RoomDatabase() {
    abstract fun cookieDao(): HikariCookieDao
}

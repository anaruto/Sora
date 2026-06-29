package eu.kanade.tachiyomi.network.interceptor

interface CloudflareSolver {
    suspend fun solve(url: String, sourceId: Long): Boolean
}

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Request

interface CloudflareSolver {
    fun solve(request: Request)
}

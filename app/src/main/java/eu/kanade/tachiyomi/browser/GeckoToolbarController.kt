package eu.kanade.tachiyomi.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class GeckoToolbarController {
    var title by mutableStateOf("Loading...")
    var url by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}

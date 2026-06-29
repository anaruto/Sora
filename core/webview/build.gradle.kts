plugins {
    alias(hikarix.plugins.android.library)
    alias(hikarix.plugins.spotless)
}

android {
    namespace = "eu.kanade.tachiyomi.core.webview"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.mozilla.geckoview)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)
}

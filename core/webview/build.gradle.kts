plugins {
    alias(hikarix.plugins.android.library)
    alias(hikarix.plugins.spotless)
    alias(libs.plugins.ksp)
}

android {
    namespace = "eu.kanade.tachiyomi.core.webview"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.mozilla.geckoview)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}

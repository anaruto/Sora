dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://maven.mozilla.org/maven2/")
    }
    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))
        }
        create("hikarix") {
            from(files("../hikari.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

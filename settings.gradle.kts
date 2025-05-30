// settings.gradle.kts (should be fine as is)
pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/kotlin/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/maven")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Convertaphile"
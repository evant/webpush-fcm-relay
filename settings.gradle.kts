pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    plugins {
        kotlin("jvm") version "1.9.23"
        kotlin("android") version "1.9.23"
        id("com.android.library") version "8.2.0"
        id("com.android.application") version "8.2.0"
        id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
        id("io.ktor.plugin") version "2.3.10"
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
}

rootProject.name = "webpush-fcm-relay-project"
include(":server")
project(":server").name = "webpush-fcm-relay"
include(":client-android")
include(":sample-app")
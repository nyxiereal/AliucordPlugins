@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/releases")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/releases")
        }
        maven {
            name = "aliucord"
            url = uri("https://maven.aliucord.com/snapshots")
        }
    }
}

rootProject.name = "aliucord-plugins"

rootDir
    .resolve("plugin")
    .listFiles { file ->
        file.isDirectory && file.resolve("build.gradle.kts").exists()
    }!!
    .forEach { include(":plugin:${it.name}") }
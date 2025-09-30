@file:Suppress("UnstableApiUsage")

import com.aliucord.gradle.AliucordExtension
import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

val repo = "nyxiereal/AliucordPlugins"

subprojects {
    val libs = rootProject.libs

    apply {
        plugin(libs.plugins.android.library.get().pluginId)
        plugin(libs.plugins.aliucord.get().pluginId)
        plugin(libs.plugins.kotlin.android.get().pluginId)
        plugin(libs.plugins.ktlint.get().pluginId)
    }

    configure<LibraryExtension> {
        namespace = "com.github.nyxiereal"

        compileSdk = 34

        defaultConfig {
            minSdk = 24
        }

        buildFeatures {
            renderScript = false
            shaders = false
            buildConfig = true
            resValues = false
            aidl = false
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    configure<AliucordExtension> {
        author("nyxiereal", 1242567443742986373L)

        updateUrl.set("https://raw.githubusercontent.com/$repo/builds/updater.json")
        buildUrl.set("https://raw.githubusercontent.com/$repo/builds/%s.zip")
    }

    configure<KtlintExtension> {
        version.set(libs.versions.ktlint)

        coloredOutput.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(true)
    }

    dependencies {
        val discord by configurations
        val compileOnly by configurations
        val implementation by configurations

        discord(libs.discord)
        compileOnly(libs.aliucord)
        // compileOnly("com.github.Aliucord:Aliucord:unspecified")
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        }
    }
}
// pls ignore
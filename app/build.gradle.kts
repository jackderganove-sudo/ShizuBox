import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.emil.shizubox"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.emil.shizubox"
        minSdk = 31
        targetSdk = 37
        versionCode = 14
        versionName = "0.11.1"
        // The downloader bundles Python/yt-dlp and FFmpeg per ABI; only 64-bit ARM keeps the APK small.
        ndk { abiFilters += "arm64-v8a" }
    }

    // youtubedl-android runs its bundled binaries from the extracted native library dir.
    packaging { jniLibs { useLegacyPackaging = true } }

    // Release key lives outside the repo (never commit it): ~/Android/keys/shizubox-release.properties
    val releaseProps = File(System.getProperty("user.home"), "Android/keys/shizubox-release.properties")
        .takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (releaseProps != null) {
            create("release") {
                storeFile = file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // R8 also in debug: the icon library alone would make the APK ~60 MB,
        // too much for installing over wireless adb.
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.material.kolor)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.haze)
    implementation(libs.haze.blur)

    // Install engine of Universal Installer (see installer/ package)
    implementation(libs.ackpine.core)
    implementation(libs.ackpine.ktx)
    implementation(libs.ackpine.splits)
    implementation(libs.ackpine.splits.ktx)
    implementation(libs.ackpine.shizuku)
    implementation(libs.ackpine.shizuku.ktx)

    // Video downloader: yt-dlp + FFmpeg, search/feed without account
    implementation(libs.ytdl.library)
    implementation(libs.ytdl.ffmpeg)
    implementation(libs.newpipe.extractor)
}

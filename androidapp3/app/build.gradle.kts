// Must be a real import: inside the Kotlin DSL, a bare `java.util.Properties`
// resolves `java` to Gradle's own `java` extension rather than the package.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.kakashi812.droidoss"
    compileSdk {
        // 37, not 36.1: androidx.core[-ktx] 1.19.0 refuses to compile against
        // anything older. compileSdk only controls which APIs are visible at
        // compile time -- targetSdk (36) and minSdk (26) are what affect
        // runtime behaviour and device support, and both stay put.
        version = release(37)
    }

    defaultConfig {
        val connectionPreview = providers.gradleProperty("connectionPreview").orNull == "true"
        applicationId = if (connectionPreview) "io.github.chowdhurysiam.kizuna.preview" else "io.github.chowdhurysiam.kizuna"
        manifestPlaceholders["appLabel"] = if (connectionPreview) "Kizuna Preview" else "@string/app_name"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.0.2"

        val releaseRepository = providers.gradleProperty("releaseRepository").orElse("Chowdhury-Siam/Kizuna").get()
        require(Regex("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+").matches(releaseRepository)) { "Invalid releaseRepository" }
        buildConfigField("String", "RELEASE_REPOSITORY", "\"$releaseRepository\"")
        buildConfigField("boolean", "UPDATE_ENABLED", (!connectionPreview).toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Read from local.properties, which is gitignored. The keystore and its
    // passwords must never reach the repository: anyone holding them can ship
    // an update that installs over a real droidOSS install.
    //
    // Losing the keystore is equally unrecoverable -- Android identifies an app
    // by its signature, so a replacement key means existing users must
    // uninstall before they can update. Back it up somewhere that is not this
    // machine.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    val keystorePath = (System.getenv("ANDROID_KEYSTORE_PATH") ?: keystoreProperties.getProperty("storeFile"))

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = (System.getenv("ANDROID_STORE_PASSWORD") ?: keystoreProperties.getProperty("storePassword"))
                keyAlias = (System.getenv("ANDROID_KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias"))
                keyPassword = (System.getenv("ANDROID_KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Falls back to an unsigned release build when no keystore is
            // configured, so a fresh clone still builds.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("test").resources.srcDir(rootProject.file("../tests/fixtures"))

    buildFeatures {
        compose = true
        // Generates BuildConfig.VERSION_NAME, shown in the home-screen footer so
        // the version on screen can never drift from the one in defaultConfig.
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    // QR codes for sharing a layout phone to phone: core draws them, the
    // embedded scanner reads them. Both Apache-2.0 and free of Play services.
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing key: keystore.properties on the PC (not in git), or the repo secrets on GitHub.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)
val hasReleaseKey = signingValue("storeFile", "NAPTAP_KEYSTORE_PATH") != null

android {
    namespace = "com.czo.tap2nap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.czo.tap2nap"
        minSdk = 28          // Android 9, needed for GLOBAL_ACTION_LOCK_SCREEN
        targetSdk = 35
        // Raise both for every release. versionCode always has to go up.
        versionCode = 15
        versionName = "1.14"

        // Where the app looks for updates.
        buildConfigField("String", "GITHUB_REPO", "\"DiegoCZO/Tap2Nap\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(signingValue("storeFile", "NAPTAP_KEYSTORE_PATH")!!)
                storePassword = signingValue("storePassword", "NAPTAP_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NAPTAP_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NAPTAP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Our key if we have it, otherwise the debug key.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
    }

    // Name the APK Tap2Nap-<version>.apk
    applicationVariants.all {
        val variant = this
        outputs.all {
            val suffix = if (variant.buildType.name == "release") "" else "-${variant.buildType.name}"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Tap2Nap-${variant.versionName}$suffix.apk"
        }
    }
}

dependencies {
    // Compose and Material 3 for the settings screen
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
}

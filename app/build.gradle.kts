import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing key. Read from keystore.properties (local PC, not committed to git)
// or from environment variables (GitHub Actions, filled from the repo secrets).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)
val hasReleaseKey = signingValue("storeFile", "NAPTAP_KEYSTORE_PATH") != null

android {
    namespace = "com.diego.dobletoque"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.diego.dobletoque"
        minSdk = 28          // GLOBAL_ACTION_LOCK_SCREEN requires Android 9
        targetSdk = 35
        // Bump both before every new release (versionCode must always go up).
        versionCode = 2
        versionName = "1.1"
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
            // Own key if available, otherwise the debug key (still installable).
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

    // APK name: Nap-Tap-1.1.apk (release) / Nap-Tap-1.1-debug.apk (debug)
    applicationVariants.all {
        val variant = this
        outputs.all {
            val suffix = if (variant.buildType.name == "release") "" else "-${variant.buildType.name}"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Nap-Tap-${variant.versionName}$suffix.apk"
        }
    }
}

// No dependencies: just the Android framework + Kotlin stdlib → very small APK.

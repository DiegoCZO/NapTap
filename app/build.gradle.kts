plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.diego.dobletoque"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.diego.dobletoque"
        minSdk = 28          // GLOBAL_ACTION_LOCK_SCREEN existe desde Android 9
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Firmado con la clave debug para poder instalarlo directamente (uso personal).
            signingConfig = signingConfigs.getByName("debug")
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
}

// Sin dependencias: solo el framework de Android + stdlib de Kotlin → APK muy liviano.

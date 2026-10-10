import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionPropsFile = file("../version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(versionPropsFile.inputStream())
}
val verCode = versionProps["versionCode"].toString().toInt()
val verName = versionProps["versionName"].toString()

android {
    namespace = "com.mdkdw1.mypdf"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mdkdw1.mypdf"
        minSdk = 24
        targetSdk = 34
        versionCode = verCode
        versionName = verName
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "../keystore.p12")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "mypdf"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android123"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // ✅ AndroidX 기반 최신 포크 (Maven Central)
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")
}

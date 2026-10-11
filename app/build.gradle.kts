import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionPropsFile = file("../version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(versionPropsFile.inputStream())
}
val verCode = versionProps["versionCode"].toString().toInt()
val verName = versionProps["versionName"].toString()

android {
    namespace = "com.mdkdw1.mypdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mdkdw1.mypdf"
        minSdk = 24
        targetSdk = 35
        versionCode = verCode
        versionName = verName
        multiDexEnabled = true
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

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/DEPENDENCIES"
        resources.excludes += "/META-INF/LICENSE"
        resources.excludes += "/META-INF/LICENSE.txt"
        resources.excludes += "/META-INF/NOTICE"
        resources.excludes += "/META-INF/NOTICE.txt"
        resources.excludes += "/META-INF/*.kotlin_module"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.multidex:multidex:2.0.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // PDF 뷰어
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")

    // ⭐ PdfBox-Android (Maven Central)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // 코루틴
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 문서 접근
    implementation("androidx.documentfile:documentfile:1.0.1")
}

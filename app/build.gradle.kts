import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Embedded Yandex Music OAuth token — read from local.properties (yandex.token).
val yandexToken: String = run {
    val p = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { p.load(it) }
    // Дефолтный токен вшит в исходники — чтобы воспроизведение работало в любой
    // сборке (своей/CI) и переживало переустановку приложения. local.properties
    // (yandex.token=...) или env YANDEX_TOKEN при наличии переопределяют его.
    (p.getProperty("yandex.token") ?: System.getenv("YANDEX_TOKEN")
        ?: "y0__wgBELHe8_MIGN74BiCQ5tv2FzC43vPzCLd5agbA1oC0b0HoY1KEg2NnBn3o").trim()
}

android {
    namespace = "com.moyavolna.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.moyavolna.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "YANDEX_TOKEN", "\"$yandexToken\"")
    }

    signingConfigs {
        create("release") {
            // Committed dev keystore so the release APK installs straight off
            // the build — NOT a production Play Store key.
            storeFile = file("wellness-release.jks")
            storePassword = "wellness"
            keyAlias = "wellness"
            keyPassword = "wellness"
        }
    }

    buildTypes {
        release {
            // Keep minify off: we use Gson reflection on Audius JSON models,
            // so shrinking would require keep rules. App is small enough.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.runtime:runtime")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Media3 playback (background + media session/notification)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

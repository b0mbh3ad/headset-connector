plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Адрес сервера, вшитый в APK: сборка с -Phc.server=https://name.alwaysdata.net
// даёт приложение, которому не нужно вводить адрес вручную.
val defaultServer: String = (project.findProperty("hc.server") as String?)?.trim().orEmpty()

// Репозиторий GitHub для автообновления: приложение берёт последний релиз и
// качает APK оттуда. Сборка с -Phc.update.repo=user/repo меняет его.
val updateRepo: String = (project.findProperty("hc.update.repo") as String?)?.trim() ?: "b0mbh3ad/headset-connector"

android {
    namespace = "com.headsetconnector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.headsetconnector"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.7"
        vectorDrawables { useSupportLibrary = true }
        // WebRTC тянет нативные библиотеки на все архитектуры. Шлемы и телефоны
        // у нас 64-битные ARM, поэтому остальные ABI только раздувают APK.
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "DEFAULT_SERVER", "\"$defaultServer\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    buildTypes {
        release {
            // R8: обфускация и удаление мёртвого кода. Один APK для шлема и телефона.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Живой звук: WebRTC (микрофон, эхоподавление, шумодав) + WebSocket-сигналинг.
    implementation("io.github.webrtc-sdk:android:125.6422.07")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

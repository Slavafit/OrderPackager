plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.orderpackager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.orderpackager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "v1.3"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlin.get()  // важно для Kotlin 2.1
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    // === Apache POI ===
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Если нужно работать с XML (xlsx) — рекомендуется добавить:
    implementation("org.apache.poi:poi-ooxml-lite:5.3.0")
}
// === FIX для ошибки debugRuntimeClasspathCopy ===
configurations.configureEach {
    if (name.contains("ClasspathCopy", ignoreCase = true)) {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

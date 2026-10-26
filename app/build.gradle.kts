import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localBuildConfigValue(name: String): String =
    localProperties.getProperty(name, "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "com.orderpackager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orderpackager"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "v1.19"

        buildConfigField("String", "SUPABASE_URL", "\"${localBuildConfigValue("SUPABASE_URL")}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${localBuildConfigValue("SUPABASE_PUBLISHABLE_KEY")}\""
        )
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            "\"${localBuildConfigValue("PRIVACY_POLICY_URL")}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE", "")
        if (storeFilePath.isNotBlank()) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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

    testImplementation("junit:junit:4.13.2")
}
// === FIX для ошибки debugRuntimeClasspathCopy ===
configurations.configureEach {
    if (name.contains("ClasspathCopy", ignoreCase = true)) {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

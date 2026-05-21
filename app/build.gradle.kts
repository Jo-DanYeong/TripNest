plugins {
    alias(libs.plugins.android.application)
}

import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.tripnest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tripnest"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val backendBaseUrl = (project.findProperty("BACKEND_BASE_URL") as String?)
            ?: localProperties.getProperty("BACKEND_BASE_URL")
            ?: "http://172.30.1.109:8080"
        val backendFallbackUrl = (project.findProperty("BACKEND_FALLBACK_URL") as String?)
            ?: localProperties.getProperty("BACKEND_FALLBACK_URL")
            ?: backendBaseUrl
        val kakaoNativeAppKey = (project.findProperty("KAKAO_NATIVE_APP_KEY") as String?)
            ?: localProperties.getProperty("KAKAO_NATIVE_APP_KEY")
            ?: ""

        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "BACKEND_FALLBACK_URL", "\"$backendFallbackUrl\"")
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    lint {
        disable += "PropertyEscape"
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.osmdroid.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

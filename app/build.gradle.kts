import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Per-machine settings kept out of git (see README): sdk.dir, the weather location and the TV address.
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull?.let { load(it.reader()) }
}

android {
    namespace = "com.chroniac.tvhome"
    compileSdk = 37
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.chroniac.tvhome"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        // Open-Meteo coordinates for the weather chip; left empty, the chip stays hidden.
        resValue("string", "weather_latitude", localProperties.getProperty("weather.latitude", ""))
        resValue("string", "weather_longitude", localProperties.getProperty("weather.longitude", ""))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Same key as earlier builds (~/.android/debug.keystore), so `adb install -r` upgrades in place.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
}

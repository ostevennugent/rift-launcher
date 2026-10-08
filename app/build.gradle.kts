plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Passed in by CI (-PgitSha=... -PbuildNumber=...). Local builds fall back to "dev" / 1.
val gitSha: String = (project.findProperty("gitSha") as String?) ?: "dev"
val buildNumber: Int = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "app.rift.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.rift.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    // One fixed key, so every build can be installed over the previous one.
    signingConfigs {
        create("rift") {
            storeFile = file("rift.keystore")
            storePassword = "riftlauncher"
            keyAlias = "rift"
            keyPassword = "riftlauncher"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("rift")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rift")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}

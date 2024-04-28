plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "me.tatarka.webpush.relay.fcm.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "me.tatarka.webpush.relay.fcm.app"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":client-android"))
    implementation(libs.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.compose)
    implementation(libs.firebase.messaging)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
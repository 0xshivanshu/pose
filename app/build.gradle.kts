plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.pose"
    // Reverted to API 35 (Stable) as we downgraded to stable androidx libraries
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.pose"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    packaging {
        jniLibs {
            // Fix for 16 KB page size compatibility with MediaPipe native libraries.
            // This forces native libraries to be extracted on the device,
            // avoiding the alignment issue in the APK on newer 16 KB devices.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // MediaPipe
    implementation(libs.mediapipe.tasks.vision)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Guava (for ListenableFuture in CameraX)
    implementation("com.google.guava:guava:33.0.0-android")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
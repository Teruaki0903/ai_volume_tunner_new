plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cm.test.ai_volume_tuner_back"
    compileSdk = 35

    defaultConfig {
        applicationId = "cm.test.ai_volume_tuner_back"
        minSdk = 26
        targetSdk = 32
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // AndroidX Libraries

    implementation("androidx.core:core-ktx:1.15.0") // 最新版を確認
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("com.google.android.material:material:1.12.0")

    // CameraX Libraries
    val camerax_version = "1.1.0" // 最新版を確認し、安定版を使用
    //noinspection GradleDependency
    implementation("androidx.camera:camera-camera2:$camerax_version")
    //noinspection GradleDependency
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:1.5.0-alpha03") // 安定版も検討

    // ML Kit Libraries
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:image-labeling-custom:17.0.3") // 最新版
    implementation("com.google.mlkit:face-detection:16.1.7") // 最新版

    // Testing Libraries
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

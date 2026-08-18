plugins {
    id("com.android.application")
}

android {
    namespace = "com.teegle.opened"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.teegle.opened"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1-alpha"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("androidx.window:window:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

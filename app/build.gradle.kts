plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.ezgerman"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ezgerman"
        minSdk = 24
        targetSdk = 34
        versionCode = 15
        versionName = "1.4.4"
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity:1.9.1")
    // Daily reminder notification (Feature 2) — periodic background work that
    // survives process death/reboot without any custom AlarmManager/receiver
    // plumbing; WorkManager's own library manifest already declares the
    // RECEIVE_BOOT_COMPLETED-based rescheduling it needs internally.
    implementation("androidx.work:work-runtime:2.9.1")
}

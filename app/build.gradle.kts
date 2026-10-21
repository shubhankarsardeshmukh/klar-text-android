plugins {
    id("com.android.application")
}

android {
    namespace = "com.klartext.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.klartext.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 21
        versionName = "1.4.10"
    }

    // A fixed debug key (committed on purpose) so every build, including the
    // ones made on GitHub, has the SAME fingerprint. Google sign-in only works
    // when that fingerprint is registered in Firebase.
    signingConfigs {
        getByName("debug") {
            storeFile = file("klartext-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    // Native "Sign in with Google" (Credential Manager) — bridged to the web
    // layer, which exchanges the Google ID token for a Firebase session.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}

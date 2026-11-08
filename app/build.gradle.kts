plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.lgtm.finger_lottery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.lgtm.finger_lottery"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                // WorkManager/Room are initialized by AndroidX Startup in the
                // Google Mobile Ads SDK. R8 optimization here breaks that
                // reflective database creation at application startup.
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.ump)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

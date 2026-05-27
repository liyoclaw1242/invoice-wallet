plugins {
    id("app.convention.android.application")
    id("app.convention.android.compose")
    id("app.convention.android.hilt")
    id("app.convention.kotlin.jvm.test")
}

android {
    namespace = "tw.invoicewallet.app"

    defaultConfig {
        applicationId = "tw.invoicewallet"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Instrumented smoke tests (run on a device/emulator).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

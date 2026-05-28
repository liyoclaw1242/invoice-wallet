plugins {
    id("app.convention.android.application")
    id("app.convention.android.compose")
    id("app.convention.android.hilt")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
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
    implementation(project(":feature:scan"))
    implementation(project(":feature:invoice-list"))
    implementation(project(":feature:invoice-detail"))
    implementation(project(":feature:lottery"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:pairing"))
    implementation(project(":data-source:mcp-server"))
    implementation(project(":data-source:relay-client"))
    // Carries the Hilt DatabaseModule (so Hilt can resolve the graph here) and
    // re-exports :core:model (Invoice) via `api`.
    implementation(project(":core:database"))
    // ScanRoute composes the scan SnackbarHost + counter in WalletTheme tokens.
    implementation(project(":core:design-system"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    // Shared test infrastructure (MainDispatcher rules, HiltTestRunner, fixtures).
    testImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:testing"))

    // Instrumented smoke tests (run on a device/emulator).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

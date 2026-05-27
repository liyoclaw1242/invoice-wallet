plugins {
    id("app.convention.android.library")
    id("app.convention.android.compose")
    id("app.convention.android.hilt")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tw.invoicewallet.feature.scan"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    // Domain models + the InvoiceRepository interface (the impl is wired via DI at app level).
    implementation(project(":core:model"))
    implementation(project(":core:database"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // The QR parser is pure Kotlin; it only needs date types from kotlinx-datetime.
    implementation(libs.kotlinx.datetime)

    // On-device QR + Chinese OCR (no network); .await() bridges ML Kit Tasks to coroutines.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.kotlinx.coroutines.play.services)

    // CameraX live preview + analysis for real-time QR scanning.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)

    // Fixtures (real e-invoice QR payloads) are loaded as JSON in the parser tests.
    testImplementation(libs.kotlinx.serialization.json)
    // Shared test infra (MainDispatcherExtension) for ViewModel tests.
    testImplementation(project(":core:testing"))

    // Instrumented tests (e.g. ML Kit recognition against a real photo on a device).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotest.assertions.core)
}

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

// zxing-cpp:android is built with a newer Kotlin; allow our 2.0.x compiler to read its
// metadata (stdlib stays forward-compatible at runtime).
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // Domain models + the InvoiceRepository interface (the impl is wired via DI at app level).
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:design-system"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // The QR parser is pure Kotlin; it only needs date types from kotlinx-datetime.
    implementation(libs.kotlinx.datetime)

    // On-device QR + Chinese OCR (no network); .await() bridges ML Kit Tasks to coroutines.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.kotlinx.coroutines.play.services)
    // Stronger QR decoder for hard/skewed thermal-print invoices (tryHarder/rotate/invert).
    implementation(libs.zxing.cpp.android)

    // CameraX live preview + analysis for real-time QR scanning.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)

    // Ktor client: look up a store name from its public business tax ID (the only network use).
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Fixtures (real e-invoice QR payloads) are loaded as JSON in the parser tests.
    testImplementation(libs.kotlinx.serialization.json)
    // Shared test infra (MainDispatcherExtension) for ViewModel tests.
    testImplementation(project(":core:testing"))

    // Ktor MockEngine drives the merchant-lookup parsing tests deterministically (no network).
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.content.negotiation)

    // Instrumented tests (e.g. ML Kit recognition against a real photo on a device).
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotest.assertions.core)
}

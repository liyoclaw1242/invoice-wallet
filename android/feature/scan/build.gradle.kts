plugins {
    id("app.convention.android.library")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tw.invoicewallet.feature.scan"
}

dependencies {
    // Domain models + the InvoiceRepository interface (the impl is wired via DI at app level).
    implementation(project(":core:model"))
    implementation(project(":core:database"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // The QR parser is pure Kotlin; it only needs date types from kotlinx-datetime.
    implementation(libs.kotlinx.datetime)

    // Fixtures (real e-invoice QR payloads) are loaded as JSON in the parser tests.
    testImplementation(libs.kotlinx.serialization.json)
    // Shared test infra (MainDispatcherExtension) for ViewModel tests.
    testImplementation(project(":core:testing"))
}

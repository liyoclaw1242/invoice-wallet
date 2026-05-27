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
    // The QR parser is pure Kotlin; it only needs date types from kotlinx-datetime.
    implementation(libs.kotlinx.datetime)

    // Fixtures (real e-invoice QR payloads) are loaded as JSON in the parser tests.
    testImplementation(libs.kotlinx.serialization.json)
}

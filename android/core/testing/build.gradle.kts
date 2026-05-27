plugins {
    id("app.convention.android.library")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.core.testing"
}

dependencies {
    // This module IS test infrastructure — its utilities live in main/ and are
    // exported to consumers' test source sets, so the test frameworks are `api`.
    api(libs.kotlinx.coroutines.test)
    api(libs.junit4)
    api(libs.junit.jupiter.api)
    api(libs.androidx.test.runner)
    api(libs.androidx.test.ext.junit)
    api(libs.hilt.android.testing)
}

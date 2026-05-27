plugins {
    id("app.convention.android.library")
    id("app.convention.android.compose")
    id("app.convention.android.hilt")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.feature.settings"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":feature:export"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)

    // Plaintext app preferences (theme / scan mode / onboarding flag).
    implementation(libs.androidx.datastore.preferences)
    // The carrier barcode is sensitive → Keystore-backed encrypted storage.
    implementation(libs.androidx.security.crypto)

    testImplementation(project(":core:testing"))
}

plugins {
    id("app.convention.android.library")
    id("app.convention.android.room")
    id("app.convention.android.hilt")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.core.database"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Exported Room schemas (written to $projectDir/schemas by the room convention)
    // are read by MigrationTestHelper from the androidTest assets.
    sourceSets {
        getByName("androidTest") {
            assets.srcDir(files("$projectDir/schemas"))
        }
    }
}

dependencies {
    api(project(":core:model"))

    // Encryption at rest: SQLCipher-backed Room + a Keystore-protected passphrase.
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.security.crypto)

    // Instrumented tests run on a device/emulator against a real Room database.
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotest.assertions.core)
}

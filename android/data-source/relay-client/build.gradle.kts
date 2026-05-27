plugins {
    id("app.convention.android.library")
    id("app.convention.android.hilt")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.datasource.relayclient"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":data-source:authz"))
    // Reuses the MCP dispatcher + tools so remote and local paths answer identically.
    implementation(project(":data-source:mcp-server"))

    implementation(libs.kotlinx.coroutines.core)

    // Outbound WebSocket to the relay (same Ktor/OkHttp stack used elsewhere).
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)

    // Keystore-backed storage for the device_secret + relay URL.
    implementation(libs.androidx.security.crypto)

    testImplementation(project(":core:testing"))
}

plugins {
    id("app.convention.android.library")
    id("app.convention.android.hilt")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.datasource.mcpserver"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":data-source:authz"))

    implementation(libs.kotlinx.coroutines.core)

    // In-app MCP server: CIO engine is pure-Kotlin and Android-friendly.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    testImplementation(project(":core:testing"))
    testImplementation(libs.ktor.server.test.host)
}

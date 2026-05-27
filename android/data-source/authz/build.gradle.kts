plugins {
    id("app.convention.android.library")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.datasource.authz"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
}

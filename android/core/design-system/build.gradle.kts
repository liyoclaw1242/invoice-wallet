plugins {
    id("app.convention.android.library")
    id("app.convention.android.compose")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.core.designsystem"
}

dependencies {
    testImplementation(project(":core:testing"))
}

plugins {
    id("app.convention.android.library")
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

android {
    namespace = "tw.invoicewallet.feature.lottery"
}

dependencies {
    implementation(project(":core:model"))
}

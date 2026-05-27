import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

// Pure-JVM module: the exporter is plain serialization logic with no Android
// dependencies, so it gets fast unit tests. SAF writing + UI live in :feature:settings.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // :core:model api-exports kotlinx-serialization-json + kotlinx-datetime.
    implementation(project(":core:model"))
}

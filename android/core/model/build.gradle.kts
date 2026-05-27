import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("app.convention.kotlin.jvm.test")
    id("app.convention.ktlint")
}

// Pure-JVM module (no Android deps) consumed by Android modules; target 17 so the
// produced class files are accepted by AGP's D8/R8. Java + Kotlin must agree.
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
    // Domain types appear in the public API (LocalDate / Instant / @Serializable) → api.
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.json)
}

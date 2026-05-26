import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Standard unit-test stack for any module: JUnit 5 (Jupiter) on the JUnit
 * Platform, plus MockK / Turbine / Kotest / coroutines-test / Robolectric.
 * Works on Android modules (via testOptions) and plain JVM modules (via Test tasks).
 */
class KotlinJvmTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when (val extension = extensions.findByName("android")) {
                is ApplicationExtension ->
                    extension.testOptions.unitTests.all { it.useJUnitPlatform() }
                is LibraryExtension ->
                    extension.testOptions.unitTests.all { it.useJUnitPlatform() }
                else ->
                    tasks.withType<Test>().configureEach { useJUnitPlatform() }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit-jupiter").get())
                add("testRuntimeOnly", libs.findLibrary("junit-jupiter-engine").get())
                add("testImplementation", libs.findLibrary("mockk").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                add("testImplementation", libs.findLibrary("kotest-assertions-core").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
            }
        }
    }
}

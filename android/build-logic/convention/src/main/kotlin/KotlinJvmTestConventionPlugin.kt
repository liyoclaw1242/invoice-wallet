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
            val isAndroid = when (val extension = extensions.findByName("android")) {
                is ApplicationExtension -> {
                    extension.testOptions.unitTests.apply {
                        // Robolectric needs real Android resources.
                        isIncludeAndroidResources = true
                        all { it.useJUnitPlatform() }
                    }
                    true
                }
                is LibraryExtension -> {
                    extension.testOptions.unitTests.apply {
                        isIncludeAndroidResources = true
                        all { it.useJUnitPlatform() }
                    }
                    true
                }
                else -> {
                    tasks.withType<Test>().configureEach { useJUnitPlatform() }
                    false
                }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit-jupiter").get())
                add("testRuntimeOnly", libs.findLibrary("junit-jupiter-engine").get())
                add("testImplementation", libs.findLibrary("mockk").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                add("testImplementation", libs.findLibrary("kotest-assertions-core").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())

                if (isAndroid) {
                    // Robolectric + Compose UI tests are JUnit4-based; the vintage engine
                    // runs them on the JUnit Platform next to the Jupiter tests.
                    add("testImplementation", libs.findLibrary("robolectric").get())
                    add("testImplementation", libs.findLibrary("androidx-test-ext-junit").get())
                    add("testRuntimeOnly", libs.findLibrary("junit-vintage-engine").get())
                }
            }
        }
    }
}

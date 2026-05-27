import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
            }
            // Unit tests run on the debug variant only (see app convention).
            extensions.configure<LibraryAndroidComponentsExtension> {
                beforeVariants(selector().withBuildType("release")) {
                    it.enableUnitTest = false
                }
            }
        }
    }
}

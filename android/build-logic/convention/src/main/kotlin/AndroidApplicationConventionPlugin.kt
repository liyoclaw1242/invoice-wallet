import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 35
            }
            // Unit tests run on the debug variant only; release unit tests are
            // redundant and break Compose tests (ui-test-manifest is debug-only).
            extensions.configure<ApplicationAndroidComponentsExtension> {
                beforeVariants(selector().withBuildType("release")) {
                    it.enableUnitTest = false
                }
            }
        }
    }
}

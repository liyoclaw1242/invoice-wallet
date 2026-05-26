import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Adds Compose to an Android module. Apply *after* the application/library
 * convention so the Android extension already exists.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            when (val extension = extensions.findByName("android")) {
                is ApplicationExtension -> configureAndroidCompose(extension)
                is LibraryExtension -> configureAndroidCompose(extension)
                else -> error(
                    "app.convention.android.compose requires an Android application or " +
                        "library plugin to be applied first.",
                )
            }
        }
    }
}

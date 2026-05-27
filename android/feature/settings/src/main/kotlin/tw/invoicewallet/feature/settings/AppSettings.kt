package tw.invoicewallet.feature.settings

/** How the app picks its colour scheme. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which input the scan screen opens by default. */
enum class DefaultScanMode { CAMERA, GALLERY }

/** User-controlled, non-sensitive app preferences (persisted in DataStore). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultScanMode: DefaultScanMode = DefaultScanMode.CAMERA,
    val onboardingCompleted: Boolean = false,
)

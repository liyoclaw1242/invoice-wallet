package tw.invoicewallet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import tw.invoicewallet.app.ui.theme.InvoiceWalletTheme
import tw.invoicewallet.feature.settings.ThemeMode
import tw.invoicewallet.feature.settings.onboarding.OnboardingScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsState()

            val darkTheme = when (settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }

            InvoiceWalletTheme(darkTheme = darkTheme) {
                val current = settings
                when {
                    // Settings still loading — keep the themed background, avoid flicker.
                    current == null -> Unit
                    !current.onboardingCompleted ->
                        OnboardingScreen(onFinish = { appViewModel.completeOnboarding() })
                    else -> InvoiceWalletApp(defaultScanMode = current.defaultScanMode)
                }
            }
        }
    }
}

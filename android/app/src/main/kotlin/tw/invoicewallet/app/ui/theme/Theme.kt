package tw.invoicewallet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = OnBrand,
    background = NeutralLight,
    onBackground = NeutralDark,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = OnBrand,
    background = NeutralDark,
    onBackground = NeutralLight,
)

@Composable
fun InvoiceWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

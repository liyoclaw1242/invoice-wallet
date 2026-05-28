package tw.invoicewallet.core.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tw.invoicewallet.core.designsystem.theme.WalletTheme

/**
 * Small all-caps section label — the Vocabulary "PREMIUM" / "ABOUT YOU" / "MAKE IT
 * YOURS" pattern above grouped settings rows. Localised input is up to the caller; we
 * just apply the micro-caps type + tertiary ink.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = WalletTheme.typography.microCaps,
        color = WalletTheme.colors.inkTertiary,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}
